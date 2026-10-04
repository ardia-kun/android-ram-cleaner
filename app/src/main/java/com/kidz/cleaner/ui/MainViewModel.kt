package com.kidz.cleaner.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kidz.cleaner.backend.AccessMode
import com.kidz.cleaner.backend.AppInfo
import com.kidz.cleaner.backend.CleanerEngine
import com.kidz.cleaner.backend.RootRunner
import com.kidz.cleaner.backend.ShellRunner
import com.kidz.cleaner.backend.ShizukuRunner
import com.kidz.cleaner.data.Aggressiveness
import com.kidz.cleaner.data.AutoMode
import com.kidz.cleaner.data.BackupStore
import com.kidz.cleaner.data.NightSchedule
import com.kidz.cleaner.data.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Status backend akses istimewa. */
data class AccessState(
    val mode: AccessMode = AccessMode.NONE,
    val shizukuRunning: Boolean = false,
    val shizukuPermission: Boolean = false,
    val rootAvailable: Boolean = false,
    val message: String = "Memeriksa akses…",
)

/** Semua state UI. */
data class UiState(
    val access: AccessState = AccessState(),
    val ramTotal: Long = -1,
    val ramAvail: Long = -1,
    val apps: List<AppInfo> = emptyList(),
    val removedApps: List<String> = emptyList(),
    val selected: Set<String> = emptySet(),
    val includeSystem: Boolean = false,
    val busy: Boolean = false,
    val autoEnabled: Boolean = false,
    val intervalMin: Int = 180,
    val autoRam: Boolean = true,
    val autoCache: Boolean = true,
    val message: String? = null,
    val query: String = "",
    // --- Pembersih RAM otomatis ---
    val ramAutoMode: AutoMode = AutoMode.OFF,
    val ramIntervalSec: Int = 300,
    val ramThresholdPct: Int = 80,
    val ramAggressive: Aggressiveness = Aggressiveness.MEDIUM,
    val monitorRunning: Boolean = false,
    // --- Jadwal malam ---
    val nightSchedule: NightSchedule = NightSchedule.OFF,
    val nightHour: Int = 2,
    val nightMinute: Int = 0,
    // --- Hemat baterai ---
    val restrictedApps: Set<String> = emptySet(),
    // --- Backup ---
    val lastBackupJson: String? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = SettingsStore(app)
    private var runner: ShellRunner = ShizukuRunner()
    private val engine get() = CleanerEngine(getApplication(), runner)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            settings.autoEnabled.collect { v -> _state.value = _state.value.copy(autoEnabled = v) }
        }
        viewModelScope.launch {
            settings.intervalMin.collect { v -> _state.value = _state.value.copy(intervalMin = v) }
        }
        viewModelScope.launch {
            settings.cleanRam.collect { v -> _state.value = _state.value.copy(autoRam = v) }
        }
        viewModelScope.launch {
            settings.cleanCache.collect { v -> _state.value = _state.value.copy(autoCache = v) }
        }
        viewModelScope.launch {
            settings.ramAutoMode.collect { v ->
                _state.value = _state.value.copy(
                    ramAutoMode = v,
                    monitorRunning = com.kidz.cleaner.work.RamMonitorService.isRunning,
                )
            }
        }
        viewModelScope.launch {
            settings.ramIntervalSec.collect { v -> _state.value = _state.value.copy(ramIntervalSec = v) }
        }
        viewModelScope.launch {
            settings.ramThresholdPct.collect { v -> _state.value = _state.value.copy(ramThresholdPct = v) }
        }
        viewModelScope.launch {
            settings.ramAggressive.collect { v -> _state.value = _state.value.copy(ramAggressive = v) }
        }
        viewModelScope.launch {
            settings.nightSchedule.collect { v -> _state.value = _state.value.copy(nightSchedule = v) }
        }
        viewModelScope.launch {
            settings.nightHour.collect { v -> _state.value = _state.value.copy(nightHour = v) }
        }
        viewModelScope.launch {
            settings.nightMinute.collect { v -> _state.value = _state.value.copy(nightMinute = v) }
        }
        refreshAccess()
    }

    /** Catat aktivitas ke Logcat (tidak ditampilkan di UI). */
    private fun log(line: String) {
        android.util.Log.d("RamCleaner", line)
    }

    // ------------------------------------------------------------- akses/backend

    fun refreshAccess() {
        viewModelScope.launch {
            val shizuku = ShizukuRunner()
            val root = RootRunner()
            val shRunning = withContext(Dispatchers.IO) { shizuku.hasBinder() }
            val shPerm = withContext(Dispatchers.IO) { shizuku.hasPermission() }
            val rootOk = withContext(Dispatchers.IO) { root.isReady() }

            val mode = when {
                rootOk -> AccessMode.ROOT
                shRunning && shPerm -> AccessMode.SHIZUKU
                else -> AccessMode.NONE
            }
            runner = when {
                rootOk -> root
                else -> shizuku
            }
            val msg = when {
                rootOk -> "Mode ROOT aktif — akses penuh."
                shRunning && shPerm -> "Mode Shizuku aktif — setara izin ADB (tanpa root)."
                shRunning && !shPerm -> "Shizuku berjalan. Izinkan akses untuk mulai."
                else -> "Shizuku belum berjalan. Nyalakan Shizuku dulu (lihat panduan)."
            }
            _state.value = _state.value.copy(
                access = AccessState(mode, shRunning, shPerm, rootOk, msg)
            )
            log("Akses: $msg")
            if (mode != AccessMode.NONE) {
                refreshData()
            }
        }
    }

    /** Minta izin Shizuku — panggil dari Activity (butuh request code). */
    fun requestShizukuPermission(requestCode: Int) {
        val sh = runner as? ShizukuRunner ?: ShizukuRunner().also { runner = it }
        try {
            sh.requestPermission(requestCode)
            log("Meminta izin Shizuku…")
        } catch (e: Throwable) {
            log("Gagal minta izin: ${e.message}")
        }
    }

    fun onPermissionResult(granted: Boolean) {
        log(if (granted) "Izin Shizuku diberikan ✔" else "Izin Shizuku ditolak")
        refreshAccess()
    }

    // -------------------------------------------------------------------- data

    fun refreshData() {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            val (total, avail) = withContext(Dispatchers.IO) { engine.ramInfo() }
            val apps = withContext(Dispatchers.IO) {
                engine.listApps(includeSystem = _state.value.includeSystem)
            }
            val removed = withContext(Dispatchers.IO) { engine.removedPackages() }
            _state.value = _state.value.copy(
                ramTotal = total, ramAvail = avail, apps = apps,
                removedApps = removed, busy = false,
            )
            log("${apps.size} aplikasi dimuat, ${removed.size} di-debloat. RAM ${fmt(avail)}/${fmt(total)}.")
        }
    }

    fun dismissMessage() {
        _state.value = _state.value.copy(message = null)
    }

    /** Filter pencarian daftar aplikasi (nama atau nama paket). */
    fun setQuery(q: String) {
        _state.value = _state.value.copy(query = q)
    }

    fun setIncludeSystem(v: Boolean) {
        _state.value = _state.value.copy(includeSystem = v, selected = emptySet())
        refreshData()
    }

    fun toggleSelect(pkg: String) {
        val s = _state.value.selected.toMutableSet()
        if (!s.add(pkg)) s.remove(pkg)
        _state.value = _state.value.copy(selected = s)
    }

    fun selectAll() {
        _state.value = _state.value.copy(selected = _state.value.apps.map { it.packageName }.toSet())
    }

    fun clearSelection() {
        _state.value = _state.value.copy(selected = emptySet())
    }

    // ------------------------------------------------------------------ aksi

    /** Bersihkan RAM (kill-all). Bila ada paket terpilih, force-stop paket itu. */
    fun cleanRam() {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            val forced = _state.value.selected.toList()
            log("Membersihkan RAM… (${forced.size} app dipilih untuk force-stop)")
            val rep = withContext(Dispatchers.IO) { engine.clearRam(forced) }
            val (total, avail) = withContext(Dispatchers.IO) { engine.ramInfo() }
            _state.value = _state.value.copy(busy = false, ramTotal = total, ramAvail = avail)
            if (rep.freedBytes >= 0) log("RAM dibebaskan: ${fmt(rep.freedBytes)} ✔")
            else log("RAM dibebaskan ✔ (perkiraan tidak tersedia)")
            refreshData()
        }
    }

    /**
     * Tombol utama: bersihkan RAM + cache semua aplikasi sekaligus.
     *
     * Pada Android < 13, bagian cache otomatis dilewati oleh engine (demi keamanan),
     * sehingga RAM tetap dibersihkan.
     */
    fun cleanEverything() {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            log("Bersihkan semuanya: RAM + cache…")

            // 1) RAM (kill-all + force-stop app terpilih)
            val forced = _state.value.selected.toList()
            val ramRep = withContext(Dispatchers.IO) { engine.clearRam(forced) }

            // 2) Cache semua app user
            val targets = _state.value.apps.map { it.packageName }
            val cacheRep = if (targets.isEmpty()) null
            else withContext(Dispatchers.IO) { engine.clearCache(targets) }

            val (total, avail) = withContext(Dispatchers.IO) { engine.ramInfo() }
            _state.value = _state.value.copy(busy = false, ramTotal = total, ramAvail = avail)

            if (ramRep.freedBytes >= 0) log("RAM dibebaskan: ${fmt(ramRep.freedBytes)}")
            cacheRep?.let {
                log(it.message)
                if (it.freedBytes >= 0) log("Ruang cache dibebaskan: ${fmt(it.freedBytes)}")
            }
            refreshData()
        }
    }

    /** Deep clean: RAM + trim cache sistem + cache semua app user. */
    fun deepClean() {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, message = null)
            val apps = _state.value.apps.map { it.packageName }
            val rep = withContext(Dispatchers.IO) { engine.deepClean(apps) }
            val (total, avail) = withContext(Dispatchers.IO) { engine.ramInfo() }
            _state.value = _state.value.copy(
                busy = false, ramTotal = total, ramAvail = avail,
                message = if (rep.freedBytes > 0)
                    "Deep clean: ${fmt(rep.freedBytes)} dibebaskan"
                else "Deep clean selesai",
            )
            log(rep.message)
            refreshData()
        }
    }

    /** Bersihkan cache paket terpilih (Android 13+) atau semua app user. */
    fun clearCache(selectedOnly: Boolean) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            val targets = if (selectedOnly) _state.value.selected.toList()
            else _state.value.apps.map { it.packageName }
            if (targets.isEmpty()) {
                _state.value = _state.value.copy(busy = false)
                log("Tidak ada paket untuk dibersihkan.")
                return@launch
            }
            log("Membersihkan cache ${targets.size} paket…")
            val rep = withContext(Dispatchers.IO) {
                engine.clearCache(targets) { p -> /* progress */ }
            }
            _state.value = _state.value.copy(busy = false)
            log(rep.message)
            if (rep.freedBytes >= 0) log("Ruang dibebaskan: ${fmt(rep.freedBytes)} ✔")
            rep.skipped.take(5).forEach { (p, m) -> log("  dilewati $p: $m") }
            refreshData()
        }
    }

    // ------------------------------------------------------- debloat & freeze

    /** Bekukan paket terpilih (data tetap aman). */
    fun freezeSelected() = runBatch("Bekukan", _state.value.selected.toList()) { engine.freeze(it) }

    /** Aktifkan kembali paket terpilih. */
    fun unfreezeSelected() = runBatch("Aktifkan", _state.value.selected.toList()) { engine.unfreeze(it) }

    /** Debloat (hapus untuk user) paket terpilih — APK tetap bisa dipulihkan. */
    fun debloatSelected() = runBatch("Debloat", _state.value.selected.toList()) { engine.debloat(it) }

    /** Kembalikan paket yang sudah di-debloat. */
    fun restorePackages(pkgs: List<String>) = runBatch("Pulihkan", pkgs) { engine.restore(it) }

    /** Jalankan aksi ke banyak paket, lalu tampilkan ringkasannya. */
    private fun runBatch(
        label: String,
        pkgs: List<String>,
        action: (String) -> Pair<Boolean, String>,
    ) {
        if (pkgs.isEmpty()) {
            _state.value = _state.value.copy(message = "Tidak ada aplikasi terpilih.")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, message = null)
            val ok = ArrayList<String>()
            val fail = ArrayList<String>()
            for (p in pkgs) {
                val (good, _) = withContext(Dispatchers.IO) { action(p) }
                if (good) ok += p else fail += p
            }
            _state.value = _state.value.copy(
                busy = false,
                selected = emptySet(),
                message = "$label: ${ok.size} berhasil" +
                    if (fail.isNotEmpty()) ", ${fail.size} gagal/dilindungi" else "",
            )
            log("$label → ${ok.size} ok, ${fail.size} gagal")
            refreshData()
        }
    }

    // ------------------------------------------------------- auto-clean setting

    fun setAutoEnabled(v: Boolean) {
        viewModelScope.launch {
            settings.setAutoEnabled(v)
            if (v) {
                com.kidz.cleaner.work.Scheduler.schedule(getApplication(), _state.value.intervalMin)
                log("Auto-clean AKTIF tiap ${_state.value.intervalMin} menit.")
            } else {
                com.kidz.cleaner.work.Scheduler.cancel(getApplication())
                log("Auto-clean dimatikan.")
            }
        }
    }

    fun setInterval(min: Int) {
        viewModelScope.launch {
            settings.setInterval(min)
            if (_state.value.autoEnabled) {
                com.kidz.cleaner.work.Scheduler.schedule(getApplication(), min)
            }
            log("Interval auto-clean: $min menit.")
        }
    }

    fun setAutoRam(v: Boolean) = viewModelScope.launch { settings.setCleanRam(v) }
    fun setAutoCache(v: Boolean) = viewModelScope.launch { settings.setCleanCache(v) }

    // --------------------------------------------- pembersih RAM otomatis

    /** Ubah mode (OFF / INTERVAL / THRESHOLD) & jalankan/hentikan service. */
    fun setRamAutoMode(m: AutoMode) {
        viewModelScope.launch {
            settings.setRamAutoMode(m)
            val ctx = getApplication<Application>()
            if (m == AutoMode.OFF) {
                com.kidz.cleaner.work.RamMonitorService.stop(ctx)
                log("Pembersih RAM otomatis: OFF")
            } else {
                com.kidz.cleaner.work.RamMonitorService.start(ctx)
                log("Pembersih RAM otomatis: $m")
            }
            _state.value = _state.value.copy(
                ramAutoMode = m,
                monitorRunning = com.kidz.cleaner.work.RamMonitorService.isRunning,
                message = when (m) {
                    AutoMode.OFF -> "Pembersih RAM otomatis dimatikan"
                    AutoMode.INTERVAL -> "Otomatis tiap ${fmtInterval(_state.value.ramIntervalSec)}"
                    AutoMode.THRESHOLD -> "Otomatis saat RAM ≥ ${_state.value.ramThresholdPct}%"
                },
            )
        }
    }

    fun setRamIntervalSec(sec: Int) = viewModelScope.launch {
        settings.setRamIntervalSec(sec)
        restartMonitorIfNeeded()
    }

    fun setRamThresholdPct(pct: Int) = viewModelScope.launch {
        settings.setRamThresholdPct(pct)
        restartMonitorIfNeeded()
    }

    fun setRamAggressive(a: Aggressiveness) = viewModelScope.launch {
        settings.setRamAggressive(a)
        restartMonitorIfNeeded()
    }

    /** Service membaca preferensi tiap putaran, jadi cukup di-restart agar langsung pakai nilai baru. */
    private fun restartMonitorIfNeeded() {
        val ctx = getApplication<Application>()
        if (_state.value.ramAutoMode != AutoMode.OFF) {
            com.kidz.cleaner.work.RamMonitorService.start(ctx)
        }
    }

    private fun fmtInterval(sec: Int): String =
        if (sec < 60) "${sec}s" else "${sec / 60} mnt"

    // ------------------------------------------------------------- jadwal malam

    fun setNightSchedule(s: NightSchedule) {
        viewModelScope.launch {
            settings.setNightSchedule(s)
            val ctx = getApplication<Application>()
            if (s == NightSchedule.OFF && _state.value.ramAutoMode == AutoMode.OFF) {
                com.kidz.cleaner.work.RamMonitorService.stop(ctx)
            } else {
                com.kidz.cleaner.work.RamMonitorService.start(ctx)
            }
            _state.value = _state.value.copy(
                nightSchedule = s,
                message = when (s) {
                    NightSchedule.OFF -> "Jadwal malam dimatikan"
                    NightSchedule.SCREEN_OFF -> "Bersihkan tiap layar mati"
                    NightSchedule.SCHEDULED ->
                        "Bersihkan tiap hari ${fmtJam(_state.value.nightHour, _state.value.nightMinute)}"
                },
            )
        }
    }

    fun setNightTime(hour: Int, minute: Int) = viewModelScope.launch {
        settings.setNightTime(hour, minute)
        restartMonitorIfNeeded()
    }

    // ------------------------------------------------------------ hemat baterai

    /** Batasi latar belakang app terpilih (hemat baterai). */
    fun batteryRestrictSelected() {
        val pkgs = _state.value.selected.toList()
        if (pkgs.isEmpty()) return
        runBatch("Hemat baterai", pkgs) { p -> engine.batteryRestrict(p) }
    }

    /** Buka batasan latar belakang app terpilih. */
    fun batteryUnrestrictSelected() {
        val pkgs = _state.value.selected.toList()
        if (pkgs.isEmpty()) return
        runBatch("Buka batasan", pkgs) { p -> engine.batteryUnrestrict(p) }
    }

    /** Muat ulang daftar app yang dibatasi latar belakangnya. */
    fun refreshRestricted() {
        viewModelScope.launch {
            val set = withContext(Dispatchers.IO) {
                runCatching { engine.batteryRestrictedPackages() }.getOrDefault(emptySet())
            }
            _state.value = _state.value.copy(restrictedApps = set)
        }
    }

    // --------------------------------------------------------- backup & restore

    /** Ekspor daftar freeze/debloat/hemat-baterai ke JSON. */
    fun exportBackup(): String? {
        val json = runCatching {
            BackupStore.toJson(BackupStore.snapshot(engine))
        }.getOrNull()
        if (json != null) {
            _state.value = _state.value.copy(
                lastBackupJson = json,
                message = "Backup dibuat (${json.length} byte)",
            )
        } else {
            _state.value = _state.value.copy(message = "Gagal membuat backup")
        }
        return json
    }

    /** Pulihkan keadaan dari JSON: bekukan & batasi lagi paket yang tercatat. */
    fun importBackup(json: String) {
        val data = BackupStore.fromJson(json)
        if (data == null) {
            _state.value = _state.value.copy(message = "Berkas backup tidak valid")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            val ok = ArrayList<String>()
            withContext(Dispatchers.IO) {
                for (p in data.frozen) {
                    if (engine.freeze(p).first) ok += p
                }
                for (p in data.restricted) {
                    if (engine.batteryRestrict(p).first) ok += p
                }
                for (p in data.removed) {
                    if (engine.debloat(p).first) ok += p
                }
            }
            _state.value = _state.value.copy(
                busy = false,
                message = "Backup dipulihkan: ${ok.size} tindakan diterapkan",
            )
            refreshData()
        }
    }

    private fun fmtJam(h: Int, m: Int): String =
        String.format("%02d:%02d", h, m)

    companion object {
        /** Format byte -> teks ramah (MB/GB). */
        fun fmt(bytes: Long): String {
            if (bytes < 0) return "—"
            val mb = bytes / 1024.0 / 1024.0
            return if (mb >= 1024) String.format("%.2f GB", mb / 1024) else String.format("%.0f MB", mb)
        }
    }
}

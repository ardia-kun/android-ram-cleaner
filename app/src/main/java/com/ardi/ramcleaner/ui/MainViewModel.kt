package com.ardi.ramcleaner.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ardi.ramcleaner.backend.AccessMode
import com.ardi.ramcleaner.backend.AppInfo
import com.ardi.ramcleaner.backend.CleanerEngine
import com.ardi.ramcleaner.backend.RootRunner
import com.ardi.ramcleaner.backend.ShellRunner
import com.ardi.ramcleaner.backend.ShizukuRunner
import com.ardi.ramcleaner.data.SettingsStore
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
    val selected: Set<String> = emptySet(),
    val includeSystem: Boolean = false,
    val busy: Boolean = false,
    val log: List<String> = emptyList(),
    val autoEnabled: Boolean = false,
    val intervalMin: Int = 180,
    val autoRam: Boolean = true,
    val autoCache: Boolean = true,
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
        refreshAccess()
    }

    private fun log(line: String) {
        _state.value = _state.value.copy(log = (_state.value.log + line).takeLast(200))
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
            _state.value = _state.value.copy(ramTotal = total, ramAvail = avail, apps = apps, busy = false)
            log("${apps.size} aplikasi dimuat. RAM tersedia ${fmt(avail)} / ${fmt(total)}.")
        }
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

    // ------------------------------------------------------- auto-clean setting

    fun setAutoEnabled(v: Boolean) {
        viewModelScope.launch {
            settings.setAutoEnabled(v)
            if (v) {
                com.ardi.ramcleaner.work.Scheduler.schedule(getApplication(), _state.value.intervalMin)
                log("Auto-clean AKTIF tiap ${_state.value.intervalMin} menit.")
            } else {
                com.ardi.ramcleaner.work.Scheduler.cancel(getApplication())
                log("Auto-clean dimatikan.")
            }
        }
    }

    fun setInterval(min: Int) {
        viewModelScope.launch {
            settings.setInterval(min)
            if (_state.value.autoEnabled) {
                com.ardi.ramcleaner.work.Scheduler.schedule(getApplication(), min)
            }
            log("Interval auto-clean: $min menit.")
        }
    }

    fun setAutoRam(v: Boolean) = viewModelScope.launch { settings.setCleanRam(v) }
    fun setAutoCache(v: Boolean) = viewModelScope.launch { settings.setCleanCache(v) }

    companion object {
        /** Format byte -> teks ramah (MB/GB). */
        fun fmt(bytes: Long): String {
            if (bytes < 0) return "—"
            val mb = bytes / 1024.0 / 1024.0
            return if (mb >= 1024) String.format("%.2f GB", mb / 1024) else String.format("%.0f MB", mb)
        }
    }
}

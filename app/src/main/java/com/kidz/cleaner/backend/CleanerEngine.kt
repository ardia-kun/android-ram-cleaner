package com.kidz.cleaner.backend

import android.content.Context
import android.content.pm.PackageManager
import com.kidz.cleaner.data.Aggressiveness
import java.util.concurrent.TimeUnit

/** Status satu aplikasi terkait debloat. */
enum class AppState { NORMAL, FROZEN, REMOVED }

/** Info satu aplikasi yang bisa dibersihkan. */
data class AppInfo(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    val cacheBytes: Long = -1,
    val state: AppState = AppState.NORMAL,
)

/** Ringkasan hasil pembersihan. */
data class CleanReport(
    val okPackages: List<String>,
    val skipped: List<Pair<String, String>>,
    val freedBytes: Long,
    val message: String,
)

/**
 * Mesin pembersih: RAM + cache.
 *
 * Semua perintah dijalankan lewat [ShellRunner] (Shizuku/root) sehingga berjalan
 * dengan identitas ADB shell — inilah kunci bisa membersihkan cache aplikasi lain.
 */
class CleanerEngine(
    private val context: Context,
    private val runner: ShellRunner,
) {

    private val pm: PackageManager get() = context.packageManager

    // ---------------------------------------------------------------- daftar app

    /**
     * Semua aplikasi terpasang, diambil dari **adb shell `pm list packages`**
     * (bukan API PackageManager), diurutkan berdasarkan nama.
     *
     * Daftar paket murni dari `pm list packages`, `-s` (sistem), dan `-d`
     * (disabled/dibekukan). Nama tampilan (label) tetap diambil dari
     * PackageManager agar mudah dibaca — kalau tidak tersedia, pakai nama paket.
     */
    fun listApps(includeSystem: Boolean): List<AppInfo> {
        val all = packageSet("pm list packages")
        val system = packageSet("pm list packages -s")
        val disabled = packageSet("pm list packages -d")

        val out = ArrayList<AppInfo>(all.size)
        for (pkg in all) {
            if (pkg == context.packageName) continue
            val isSystem = pkg in system
            if (!includeSystem && isSystem) continue
            out += AppInfo(
                packageName = pkg,
                label = labelOf(pkg),
                isSystem = isSystem,
                state = if (pkg in disabled) AppState.FROZEN else AppState.NORMAL,
            )
        }
        return out.sortedBy { it.label.lowercase() }
    }

    /** Nama tampilan paket (label), fallback ke nama paket bila tak ada. */
    private fun labelOf(pkg: String): String = runCatching {
        val ai = pm.getApplicationInfo(pkg, 0)
        pm.getApplicationLabel(ai).toString()
    }.getOrDefault(pkg)

    /** Ambil himpunan nama paket dari perintah `pm list packages ...`. */
    private fun packageSet(cmd: String): Set<String> =
        runner.exec(cmd).stdout.lines()
            .map { it.trim() }
            .filter { it.startsWith("package:") }
            .map { it.removePrefix("package:") }
            .toHashSet()

    // ------------------------------------------------------ debloat & freeze

    /**
     * Bekukan (freeze) paket: `pm disable-user --user 0`.
     *
     * App berhenti total (tidak ada proses/service), hilang dari launcher,
     * TAPI data & APK tetap ada — bisa di-unfreeze kapan saja.
     */
    fun freeze(pkg: String): Pair<Boolean, String> {
        if (Guard.isProtected(pkg)) {
            return false to "DILINDUNGI: ${Guard.reason(pkg)}"
        }
        val r = runner.exec("pm disable-user --user 0 $pkg")
        return if (r.combined.contains("new state: disabled", true) ||
            r.combined.contains("disabled-user", true) ||
            r.combined.contains("disabled", true)
        ) true to "dibekukan" else false to r.combined.take(140)
    }

    /** Aktifkan kembali paket yang dibekukan: `pm enable`. */
    fun unfreeze(pkg: String): Pair<Boolean, String> {
        val r = runner.exec("pm enable --user 0 $pkg")
        return if (r.combined.contains("new state: enabled", true) ||
            r.combined.contains("enabled", true)
        ) true to "diaktifkan" else false to r.combined.take(140)
    }

    /**
     * Debloat: hapus paket untuk user saat ini: `pm uninstall --user 0`.
     *
     * APK tetap ada di partisi /system — bisa dikembalikan lewat [restore] tanpa
     * download ulang. Data user dihapus (beda dengan freeze yang data-nya utuh).
     */
    fun debloat(pkg: String): Pair<Boolean, String> {
        if (Guard.isProtected(pkg)) {
            return false to "DILINDUNGI: ${Guard.reason(pkg)}"
        }
        val r = runner.exec("pm uninstall --user 0 $pkg")
        return if (r.combined.contains("success", true)) true to "dihapus"
        else false to r.combined.take(140)
    }

    /** Kembalikan paket yang sudah di-debloat: `cmd package install-existing`. */
    fun restore(pkg: String): Pair<Boolean, String> {
        val r = runner.exec("cmd package install-existing --user 0 $pkg")
        return if (r.combined.contains("installed", true)) true to "dikembalikan"
        else false to r.combined.take(140)
    }

    /**
     * Daftar paket yang sudah di-debloat (ada di /system tapi tidak terpasang
     * untuk user ini). Diambil dari selisih `pm list packages -u` dan
     * `pm list packages` — sekali perintah, cepat.
     */
    fun removedPackages(): List<String> {
        val all = packageSet("pm list packages -u")
        val installed = packageSet("pm list packages")
        return (all - installed).sorted()
    }

    // ------------------------------------------------------------------- cache

    /**
     * Ukuran cache satu paket (byte) via `dumpsys diskstats`.
     * Kembalikan -1 bila tak tersedia.
     */
    fun cacheSize(pkg: String): Long {
        val r = runner.exec("dumpsys diskstats")
        val parsed = parseDiskStats(r.stdout) ?: return -1
        val idx = parsed.first.indexOf(pkg)
        if (idx < 0 || idx >= parsed.second.size) return -1
        return parsed.second[idx].toLongOrNull() ?: -1
    }

    /** Total cache semua paket terpilih (perkiraan). */
    fun totalCache(pkgs: List<String>): Long {
        val r = runner.exec("dumpsys diskstats")
        val parsed = parseDiskStats(r.stdout) ?: return -1
        val want = pkgs.toHashSet()
        var total = 0L
        var found = false
        parsed.first.forEachIndexed { i, n ->
            if (n in want && i < parsed.second.size) {
                parsed.second[i].toLongOrNull()?.let { total += it; found = true }
            }
        }
        return if (found) total else -1
    }

    /**
     * Parse keluaran `dumpsys diskstats`.
     *
     * Format (AOSP DiskStatsService), spasi sebagai pemisah, terkadang memakai
     * tanda kurung siku/koma tergantung versi:
     *   Package Names: com.a com.b ...
     *   Cache Sizes: 1234 5678 ...
     */
    private fun parseDiskStats(out: String): Pair<List<String>, List<String>>? {
        val lines = out.lines()
        fun grab(label: String): List<String>? =
            lines.firstOrNull { it.trim().startsWith(label) }
                ?.substringAfter(":")
                ?.replace("[", " ")
                ?.replace("]", " ")
                ?.replace(",", " ")
                ?.trim()
                ?.split(Regex("\\s+"))
                ?.filter { it.isNotBlank() }
        val names = grab("Package Names:") ?: return null
        val sizes = grab("Cache Sizes:") ?: return null
        return names to sizes
    }

    /**
     * Bersihkan cache banyak paket sekaligus.
     *
     * Di Android 13+ (SDK 33) memakai `pm clear --cache-only` yang AMAN
     * (hanya cache, data/login tetap). Di bawah itu kita TOLAK demi keamanan,
     * karena flag tersebut diabaikan dan justru menghapus SEMUA data.
     */
    fun clearCache(pkgs: List<String>, onProgress: (String) -> Unit = {}): CleanReport {
        val sdk = android.os.Build.VERSION.SDK_INT
        if (sdk < 33) {
            return CleanReport(
                emptyList(), pkgs.map { it to "butuh Android 13+" },
                freedBytes = -1,
                message = "Clear cache per-app butuh Android 13+ (SDK 33). " +
                    "Di versi lama flag --cache-only diabaikan sehingga akan menghapus " +
                    "SEMUA data app — diblokir demi keamanan. Pakai 'Bersihkan RAM' saja."
            )
        }
        val before = totalCache(pkgs)
        val ok = ArrayList<String>()
        val skip = ArrayList<Pair<String, String>>()
        for (p in pkgs) {
            onProgress(p)
            val r = runner.exec("pm clear --user 0 --cache-only $p")
            val out = r.combined.lowercase()
            if (out.contains("success")) ok += p else skip += p to r.combined.take(120)
        }
        val after = totalCache(pkgs)
        val freed = if (before >= 0 && after >= 0) (before - after).coerceAtLeast(0) else -1
        return CleanReport(ok, skip, freed, "Cache dibersihkan: ${ok.size}/${pkgs.size}")
    }

    // ------------------------------------------------------------- deep clean

    /**
     * Minta sistem membuang cache sampai tersedia ruang tertentu:
     * `cmd package trim-caches <size>` (Android 11+ / SDK 30).
     *
     * Ini pembersihan tingkat sistem (semua app sekaligus), aman, tidak
     * menghapus data. Dipakai untuk "Deep clean".
     */
    fun trimSystemCache(size: String = "128G"): Pair<Boolean, String> {
        val sdk = android.os.Build.VERSION.SDK_INT
        if (sdk < 30) return false to "trim-caches butuh Android 11+ (SDK 30)"
        val r = runner.exec("cmd package trim-caches $size")
        val out = r.combined
        return if (!out.contains("Error", true) && !out.contains("Exception", true))
            true to "trim selesai" else false to out.take(140)
    }

    /**
     * Deep clean: RAM (kill-all) + trim cache sistem + clear cache semua app user.
     *
     * Hasil digabung menjadi satu laporan. Bagian cache per-app otomatis
     * dilewati di Android < 13 (lihat [clearCache]).
     */
    fun deepClean(userApps: List<String>, onProgress: (String) -> Unit = {}): CleanReport {
        val ok = ArrayList<String>()
        val skip = ArrayList<Pair<String, String>>()

        // 1) RAM
        onProgress("RAM")
        val ram = clearRam()
        ok += ram.okPackages
        skip += ram.skipped

        // 2) Trim cache sistem
        onProgress("trim cache sistem")
        val (trimOk, trimMsg) = trimSystemCache()
        if (trimOk) ok += "trim-caches" else skip += "trim-caches" to trimMsg

        // 3) Clear cache tiap app user (Android 13+)
        var freed = if (ram.freedBytes >= 0) ram.freedBytes else 0L
        if (userApps.isNotEmpty()) {
            onProgress("cache aplikasi")
            val cache = clearCache(userApps)
            ok += cache.okPackages
            skip += cache.skipped
            if (cache.freedBytes > 0) freed += cache.freedBytes
        }

        val freedFinal = if (freed > 0) freed else -1
        return CleanReport(ok, skip, freedFinal, "Deep clean selesai: ${ok.size} tindakan berhasil")
    }

    // ------------------------------------------------------------ hemat baterai

    /**
     * Kunci app agar tidak berjalan di latar (hemat baterai) — TANPA membekukan.
     *
     * - `am set-standby-bucket <pkg> restricted` → app jarang dibangunkan sistem.
     * - `cmd appops set <pkg> RUN_IN_BACKGROUND ignore` → cegah jalan di latar.
     * - `cmd appops set <pkg> RUN_ANY_IN_BACKGROUND ignore` (bila didukung).
     *
     * App tetap bisa dibuka normal; hanya latar belakangnya yang dibatasi.
     */
    fun batteryRestrict(pkg: String): Pair<Boolean, String> {
        if (Guard.isProtected(pkg)) {
            return false to "DILINDUNGI: ${Guard.reason(pkg)}"
        }
        val sb = runner.exec("am set-standby-bucket $pkg restricted")
        val ao = runner.exec("cmd appops set $pkg RUN_IN_BACKGROUND ignore")
        runner.exec("cmd appops set $pkg RUN_ANY_IN_BACKGROUND ignore")
        val bad = (sb.combined + ao.combined)
        val ok = !bad.contains("Error", true) && !bad.contains("Exception", true) &&
            !bad.contains("Unknown", true)
        return if (ok) true to "dibatasi" else false to bad.take(140)
    }

    /** Buka kunci app: kembalikan bucket & appops ke normal. */
    fun batteryUnrestrict(pkg: String): Pair<Boolean, String> {
        runner.exec("am set-standby-bucket $pkg active")
        val r = runner.exec("cmd appops set $pkg RUN_IN_BACKGROUND allow")
        runner.exec("cmd appops set $pkg RUN_ANY_IN_BACKGROUND allow")
        val ok = !r.combined.contains("Error", true)
        return if (ok) true to "dibuka" else false to r.combined.take(140)
    }

    /** Bucket standby satu paket (active/working_set/frequent/rare/restricted). */
    fun standbyBucket(pkg: String): String {
        val r = runner.exec("am get-standby-bucket $pkg")
        return r.combined.trim().ifBlank { "?" }
    }

    /** Daftar paket yang sedang dibekukan (`pm list packages -d`). */
    fun frozenPackages(): List<String> = packageSet("pm list packages -d").sorted()

    /** Daftar paket yang sedang dibatasi latar belakangnya. */
    fun batteryRestrictedPackages(): Set<String> {
        val r = runner.exec("cmd appops query-op RUN_IN_BACKGROUND ignore")
        return r.stdout.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() && it.contains(".") }
            .toHashSet()
    }

    // --------------------------------------------------------------------- RAM

    /** Info RAM: total & tersedia (byte). */
    fun ramInfo(): Pair<Long, Long> {
        val r = runner.exec("cat /proc/meminfo")
        val total = Regex("MemTotal:\\s+(\\d+)").find(r.stdout)?.groupValues?.get(1)?.toLongOrNull() ?: -1
        val avail = Regex("MemAvailable:\\s+(\\d+)").find(r.stdout)?.groupValues?.get(1)?.toLongOrNull() ?: -1
        return (total * 1024) to (avail * 1024)
    }

    /**
     * Bebaskan RAM.
     *
     * 1) `am kill-all` — membunuh proses latar (aman, diizinkan shell, TIDAK
     *    mengganggu app foreground / service penting).
     * 2) Bila [forcePackages] diisi, `am force-stop` untuk paket itu (menghentikan
     *    app sampai dibuka lagi).
     */
    fun clearRam(forcePackages: List<String> = emptyList(), onProgress: (String) -> Unit = {}): CleanReport {
        val (_, availBefore) = ramInfo()
        val ok = ArrayList<String>()
        val skip = ArrayList<Pair<String, String>>()

        onProgress("am kill-all")
        runner.exec("am kill-all")

        for (p in forcePackages) {
            onProgress(p)
            val r = runner.exec("am force-stop $p")
            if (r.combined.contains("Error", true) || r.combined.contains("Exception", true)) {
                skip += p to r.combined.take(120)
            } else {
                ok += p
            }
        }
        // Beri waktu kernel membebaskan memori.
        runCatching { TimeUnit.MILLISECONDS.sleep(700) }
        val (_, availAfter) = ramInfo()
        val freed = if (availBefore >= 0 && availAfter >= 0)
            (availAfter - availBefore).coerceAtLeast(0) else -1
        return CleanReport(ok, skip, freed, "RAM dibebaskan")
    }

    /**
     * Bersihkan RAM sesuai tingkat keagresifan.
     *
     * - LIGHT: `am kill-all` saja.
     * - MEDIUM: kill-all + trim cache sistem.
     * - AGGRESSIVE: kill-all + trim + force-stop app user yang masih berjalan.
     */
    fun clearRamByLevel(level: Aggressiveness): CleanReport {
        val (_, before) = ramInfo()
        val ok = ArrayList<String>()
        val skip = ArrayList<Pair<String, String>>()

        runner.exec("am kill-all")
        ok += "am kill-all"

        if (level != Aggressiveness.LIGHT) {
            val (t, m) = trimSystemCache()
            if (t) ok += "trim-caches" else skip += "trim-caches" to m
        }

        if (level == Aggressiveness.AGGRESSIVE) {
            for (p in runningUserPackages()) {
                if (Guard.isProtected(p)) continue
                val r = runner.exec("am force-stop $p")
                if (r.combined.contains("Error", true) || r.combined.contains("Exception", true)) {
                    skip += p to "gagal"
                } else {
                    ok += p
                }
            }
        }

        runCatching { TimeUnit.MILLISECONDS.sleep(700) }
        val (_, after) = ramInfo()
        val freed = if (before >= 0 && after >= 0) (after - before).coerceAtLeast(0) else -1
        return CleanReport(ok, skip, freed, "RAM dibersihkan (${level.name.lowercase()})")
    }

    /** Paket user (non-sistem) yang sedang berjalan. */
    fun runningUserPackages(): List<String> {
        val r = runner.exec("ps -A -o NAME")
        val running = r.stdout.lines().map { it.trim() }.toHashSet()
        return listApps(includeSystem = false)
            .map { it.packageName }
            .filter { it in running }
    }

    /** Daftar paket yang sedang berjalan (untuk pembersihan RAM selektif). */
    fun runningPackages(): List<String> {
        val r = runner.exec("ps -A -o NAME")
        val running = r.stdout.lines().map { it.trim() }.toHashSet()
        return listApps(includeSystem = true)
            .filter { it.packageName in running }
            .map { it.packageName }
    }
}

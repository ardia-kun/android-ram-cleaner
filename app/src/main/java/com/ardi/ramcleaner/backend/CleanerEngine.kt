package com.ardi.ramcleaner.backend

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import java.util.concurrent.TimeUnit

/** Info satu aplikasi yang bisa dibersihkan. */
data class AppInfo(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    val cacheBytes: Long = -1,
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

    /** Semua aplikasi yang punya launcher / terpasang, diurutkan cache terbesar. */
    fun listApps(includeSystem: Boolean): List<AppInfo> {
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val out = ArrayList<AppInfo>(apps.size)
        for (ai in apps) {
            val isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            if (!includeSystem && isSystem) continue
            if (ai.packageName == context.packageName) continue
            out += AppInfo(
                packageName = ai.packageName,
                label = runCatching { pm.getApplicationLabel(ai).toString() }
                    .getOrDefault(ai.packageName),
                isSystem = isSystem,
            )
        }
        return out.sortedBy { it.label.lowercase() }
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

    /** Daftar paket yang sedang berjalan (untuk pembersihan RAM selektif). */
    fun runningPackages(): List<String> {
        val r = runner.exec("ps -A -o NAME")
        val running = r.stdout.lines().map { it.trim() }.toHashSet()
        return listApps(includeSystem = true)
            .filter { it.packageName in running }
            .map { it.packageName }
    }
}

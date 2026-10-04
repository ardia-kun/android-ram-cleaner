package com.kidz.cleaner.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kidz.cleaner.backend.CleanerEngine
import com.kidz.cleaner.backend.ShellRunner
import com.kidz.cleaner.data.SettingsStore
import kotlinx.coroutines.flow.first

/**
 * Worker yang dijalankan WorkManager secara berkala (tanpa root, tanpa app dibuka).
 *
 * Hanya berjalan bila backend (Shizuku/root) siap. Bila Shizuku belum berjalan
 * (mis. setelah reboot & belum di-start ulang), worker keluar dengan sukses
 * tanpa melakukan apa-apa agar tidak menumpuk retry.
 */
class CleanWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val settings = SettingsStore(applicationContext)
        val doRam = settings.cleanRam.first()
        val doCache = settings.cleanCache.first()

        val runner = ShellRunner.detect(preferRoot = false)
        if (!runner.isReady()) {
            // Backend belum siap — jangan dianggap gagal.
            return Result.success()
        }

        val engine = CleanerEngine(applicationContext, runner)

        var freedRam = -1L
        var freedCache = -1L

        if (doRam) {
            val rep = engine.clearRam()
            freedRam = rep.freedBytes
        }
        if (doCache) {
            val userApps = engine.listApps(includeSystem = false).map { it.packageName }
            if (userApps.isNotEmpty()) {
                val rep = engine.clearCache(userApps)
                freedCache = rep.freedBytes
            }
        }

        val (_, avail) = engine.ramInfo()
        settings.setLastRun(System.currentTimeMillis())
        settings.setLastFreed(avail)

        // Notifikasi ringkas hasil pembersihan otomatis.
        val parts = ArrayList<String>()
        if (freedRam > 0) parts += "RAM ${fmt(freedRam)}"
        if (freedCache > 0) parts += "cache ${fmt(freedCache)}"
        val text = if (parts.isEmpty()) {
            "Pembersihan otomatis selesai. Tersedia ${fmt(avail)}."
        } else {
            "Dibebaskan: ${parts.joinToString(", ")}. Tersedia ${fmt(avail)}."
        }
        Notifier.showResult(applicationContext, "Pembersihan otomatis", text)

        return Result.success()
    }

    /** Format byte → teks ramah. */
    private fun fmt(bytes: Long): String {
        if (bytes < 0) return "—"
        val mb = bytes / 1024.0 / 1024.0
        return if (mb >= 1024) String.format("%.2f GB", mb / 1024)
        else String.format("%.0f MB", mb)
    }

    companion object {
        const val UNIQUE_NAME = "auto_clean_periodic"
    }
}

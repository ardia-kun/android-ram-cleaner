package com.ardi.ramcleaner.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ardi.ramcleaner.backend.CleanerEngine
import com.ardi.ramcleaner.backend.ShellRunner
import com.ardi.ramcleaner.data.SettingsStore
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

        if (doRam) {
            engine.clearRam()
        }
        if (doCache) {
            val userApps = engine.listApps(includeSystem = false).map { it.packageName }
            if (userApps.isNotEmpty()) {
                engine.clearCache(userApps)
            }
        }

        val (_, avail) = engine.ramInfo()
        settings.setLastRun(System.currentTimeMillis())
        settings.setLastFreed(avail)
        return Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "auto_clean_periodic"
    }
}

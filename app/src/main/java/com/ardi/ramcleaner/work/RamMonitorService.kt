package com.ardi.ramcleaner.work

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.ardi.ramcleaner.R
import com.ardi.ramcleaner.backend.CleanerEngine
import com.ardi.ramcleaner.backend.ShellRunner
import com.ardi.ramcleaner.data.Aggressiveness
import com.ardi.ramcleaner.data.AutoMode
import com.ardi.ramcleaner.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Service latar yang memantau RAM dan membersihkannya otomatis.
 *
 * Dua mode:
 * - [AutoMode.INTERVAL]  → bersihkan setiap N detik.
 * - [AutoMode.THRESHOLD] → bersihkan ketika pemakaian RAM ≥ X%.
 *
 * Tingkat keagresifan mengatur seberapa kuat pembersihannya
 * (lihat [Aggressiveness]).
 *
 * Memakai foreground service (bukan WorkManager) karena WorkManager minimum
 * 15 menit, sedangkan fitur ini butuh interval beberapa menit.
 */
class RamMonitorService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loop: Job? = null
    private lateinit var settings: SettingsStore

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(applicationContext)
        ensureChannel()
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForegroundCompat(getString(R.string.monitor_title), "Memantau RAM…")

        if (loop == null || loop?.isActive != true) {
            loop = scope.launch { runLoop() }
        }
        return START_STICKY
    }

    private suspend fun runLoop() {
        var lastCleanAt = 0L
        val minGapMs = 60_000L   // jeda minimum antar pembersihan (cegah terlalu sering)

        while (scope.isActive) {
            val mode = settings.ramAutoMode.first()
            val intervalSec = settings.ramIntervalSec.first()
            val threshold = settings.ramThresholdPct.first()
            val level = settings.ramAggressive.first()

            if (mode == AutoMode.OFF) break

            val runner = ShellRunner.detect(preferRoot = false)
            if (!runner.isReady()) {
                // Backend belum siap (Shizuku belum jalan) — coba lagi nanti.
                updateNotification("Menunggu Shizuku…")
                delay(30_000)
                continue
            }
            val engine = CleanerEngine(applicationContext, runner)

            when (mode) {
                AutoMode.INTERVAL -> {
                    delay(intervalSec.coerceAtLeast(30) * 1000L)
                    doClean(engine, level)
                    lastCleanAt = System.currentTimeMillis()
                }
                AutoMode.THRESHOLD -> {
                    val (total, avail) = engine.ramInfo()
                    if (total > 0 && avail >= 0) {
                        val usedPct = (((total - avail).toFloat() / total.toFloat()) * 100).toInt()
                        updateNotification("RAM terpakai $usedPct% (ambang $threshold%)")
                        val now = System.currentTimeMillis()
                        if (usedPct >= threshold && now - lastCleanAt > minGapMs) {
                            doClean(engine, level)
                            lastCleanAt = System.currentTimeMillis()
                        }
                    }
                    delay(30_000)
                }
                AutoMode.OFF -> break
            }
        }
    }

    private suspend fun doClean(engine: CleanerEngine, level: Aggressiveness) {
        val rep = engine.clearRamByLevel(level)
        val (total, avail) = engine.ramInfo()
        settings.setLastRun(System.currentTimeMillis())
        settings.setLastFreed(avail)

        val freed = if (rep.freedBytes > 0) " (${fmt(rep.freedBytes)} dibebaskan)" else ""
        val text = "Tersedia ${fmt(avail)} dari ${fmt(total)}$freed"
        updateNotification(text)

        // Beri tahu pengguna (opsional, tidak mengganggu).
        Notifier.showResult(applicationContext, "RAM dibersihkan otomatis", text)
    }

    private fun fmt(bytes: Long): String {
        if (bytes < 0) return "—"
        val mb = bytes / 1024.0 / 1024.0
        return if (mb >= 1024) String.format("%.2f GB", mb / 1024) else String.format("%.0f MB", mb)
    }

    // ------------------------------------------------------------- notifikasi

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = getSystemService(NotificationManager::class.java) ?: return
        if (mgr.getNotificationChannel(CHANNEL_MONITOR) != null) return
        mgr.createNotificationChannel(
            NotificationChannel(
                CHANNEL_MONITOR,
                "Pemantau RAM",
                NotificationManager.IMPORTANCE_MIN,
            ).apply {
                description = "Notifikasi tetap saat pembersihan RAM otomatis aktif"
                setShowBadge(false)
            }
        )
    }

    private fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_MONITOR)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentTitle(getString(R.string.monitor_title))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()

    private fun startForegroundCompat(title: String, text: String) {
        val n = buildNotification(text)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        runCatching {
            ServiceCompat.startForeground(this, NOTIF_ID, n, type)
        }
    }

    private fun updateNotification(text: String) {
        runCatching {
            val mgr = getSystemService(NotificationManager::class.java) ?: return
            mgr.notify(NOTIF_ID, buildNotification(text))
        }
    }

    override fun onDestroy() {
        isRunning = false
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.ardi.ramcleaner.START_MONITOR"
        const val ACTION_STOP = "com.ardi.ramcleaner.STOP_MONITOR"
        private const val CHANNEL_MONITOR = "ram_monitor"
        private const val NOTIF_ID = 2001

        /** True bila service sedang berjalan (dipakai UI). */
        @Volatile
        var isRunning: Boolean = false
            private set

        fun start(context: Context) {
            val i = Intent(context, RamMonitorService::class.java).setAction(ACTION_START)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(i)
                } else {
                    context.startService(i)
                }
            }
        }

        fun stop(context: Context) {
            val i = Intent(context, RamMonitorService::class.java).setAction(ACTION_STOP)
            runCatching { context.startService(i) }
            runCatching { context.stopService(Intent(context, RamMonitorService::class.java)) }
        }
    }
}

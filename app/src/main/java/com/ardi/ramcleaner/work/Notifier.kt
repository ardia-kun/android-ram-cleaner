package com.ardi.ramcleaner.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** Notifikasi hasil pembersihan otomatis. */
object Notifier {

    private const val CHANNEL_ID = "clean_result"
    private const val NOTIF_ID = 1001

    /** Buat channel notifikasi (panggil sekali saat app start). */
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        if (mgr.getNotificationChannel(CHANNEL_ID) != null) return
        val ch = NotificationChannel(
            CHANNEL_ID,
            "Hasil pembersihan otomatis",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Memberi tahu berapa RAM/cache yang dibebaskan"
            setShowBadge(false)
        }
        mgr.createNotificationChannel(ch)
    }

    /** Tampilkan notifikasi "auto-clean selesai". */
    fun showResult(context: Context, title: String, text: String) {
        ensureChannel(context)

        // Android 13+: butuh izin notifikasi.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIF_ID, n)
        }
    }
}

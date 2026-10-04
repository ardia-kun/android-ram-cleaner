package com.ardi.ramcleaner.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ardi.ramcleaner.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Menjadwalkan ulang pembersihan otomatis setelah HP dinyalakan. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val settings = SettingsStore(context.applicationContext)
                if (settings.autoEnabled.first()) {
                    Scheduler.schedule(context.applicationContext, settings.intervalMin.first())
                }
                // Lanjutkan pemantau RAM otomatis bila sebelumnya aktif.
                if (settings.ramAutoMode.first() != com.ardi.ramcleaner.data.AutoMode.OFF) {
                    RamMonitorService.start(context.applicationContext)
                }
            } finally {
                pending.finish()
            }
        }
    }
}

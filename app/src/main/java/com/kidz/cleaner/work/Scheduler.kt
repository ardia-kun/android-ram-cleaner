package com.kidz.cleaner.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** Utilitas menjadwalkan / membatalkan pembersihan otomatis. */
object Scheduler {

    fun schedule(context: Context, intervalMinutes: Int) {
        val interval = intervalMinutes.coerceAtLeast(15).toLong()
        val request = PeriodicWorkRequestBuilder<CleanWorker>(interval, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(false)
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            CleanWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(CleanWorker.UNIQUE_NAME)
    }
}

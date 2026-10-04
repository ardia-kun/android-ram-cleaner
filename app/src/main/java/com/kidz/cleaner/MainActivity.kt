package com.kidz.cleaner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.kidz.cleaner.ui.MainScreen
import com.kidz.cleaner.ui.MainViewModel
import com.kidz.cleaner.ui.theme.RamCleanerTheme
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    private val permissionListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == SHIZUKU_CODE) {
                vm.onPermissionResult(
                    grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED
                )
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching { Shizuku.addRequestPermissionResultListener(permissionListener) }

        // Siapkan channel notifikasi & minta izin (Android 13+).
        com.kidz.cleaner.work.Notifier.ensureChannel(this)
        requestNotificationPermission()

        setContent {
            RamCleanerTheme {
                val state by vm.state.collectAsState()
                MainScreen(
                    state = state,
                    onRefreshAccess = vm::refreshAccess,
                    onRequestShizuku = { vm.requestShizukuPermission(SHIZUKU_CODE) },
                    onCleanEverything = vm::cleanEverything,
                    onCleanCacheSelected = { vm.clearCache(selectedOnly = true) },
                    onToggleSelect = vm::toggleSelect,
                    onSelectAll = vm::selectAll,
                    onClearSelection = vm::clearSelection,
                    onIncludeSystem = vm::setIncludeSystem,
                    onAutoEnabled = vm::setAutoEnabled,
                    onInterval = vm::setInterval,
                    onAutoRam = vm::setAutoRam,
                    onAutoCache = vm::setAutoCache,
                    onFreeze = vm::freezeSelected,
                    onUnfreeze = vm::unfreezeSelected,
                    onDebloat = vm::debloatSelected,
                    onRestore = vm::restorePackages,
                    onDismissMessage = vm::dismissMessage,
                    onQuery = vm::setQuery,
                    onDeepClean = vm::deepClean,
                    onRamMode = vm::setRamAutoMode,
                    onRamInterval = vm::setRamIntervalSec,
                    onRamThreshold = vm::setRamThresholdPct,
                    onRamLevel = vm::setRamAggressive,
                )
            }
        }
    }

    override fun onDestroy() {
        runCatching { Shizuku.removeRequestPermissionResultListener(permissionListener) }
        super.onDestroy()
    }

    /** Minta izin POST_NOTIFICATIONS di Android 13+ (untuk notif hasil auto-clean). */
    private fun requestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return
        val perm = android.Manifest.permission.POST_NOTIFICATIONS
        if (checkSelfPermission(perm) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(perm), NOTIF_CODE)
        }
    }

    companion object {
        private const val SHIZUKU_CODE = 1001
        private const val NOTIF_CODE = 1002
    }
}

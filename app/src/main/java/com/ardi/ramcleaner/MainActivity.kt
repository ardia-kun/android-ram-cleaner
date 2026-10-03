package com.ardi.ramcleaner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.ardi.ramcleaner.ui.MainScreen
import com.ardi.ramcleaner.ui.MainViewModel
import com.ardi.ramcleaner.ui.theme.RamCleanerTheme
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
                )
            }
        }
    }

    override fun onDestroy() {
        runCatching { Shizuku.removeRequestPermissionResultListener(permissionListener) }
        super.onDestroy()
    }

    companion object {
        private const val SHIZUKU_CODE = 1001
    }
}

package com.ardi.ramcleaner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ardi.ramcleaner.backend.AccessMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    state: UiState,
    onRefreshAccess: () -> Unit,
    onRequestShizuku: () -> Unit,
    onCleanRam: () -> Unit,
    onCleanCacheSelected: () -> Unit,
    onCleanCacheAll: () -> Unit,
    onToggleSelect: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onIncludeSystem: (Boolean) -> Unit,
    onAutoEnabled: (Boolean) -> Unit,
    onInterval: (Int) -> Unit,
    onAutoRam: (Boolean) -> Unit,
    onAutoCache: (Boolean) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(title = {
                Column {
                    Text("RAM & Cache Cleaner", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(
                        "Mode: ${modeLabel(state.access.mode)}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            })
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AccessCard(state, onRefreshAccess, onRequestShizuku)
            RamCard(state, onCleanRam)
            CacheCard(state, onCleanCacheSelected, onCleanCacheAll, onIncludeSystem,
                onSelectAll, onClearSelection)
            AutoCard(state, onAutoEnabled, onInterval, onAutoRam, onAutoCache)
            LogCard(state)
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun modeLabel(mode: AccessMode): String = when (mode) {
    AccessMode.ROOT -> "ROOT"
    AccessMode.SHIZUKU -> "Shizuku (tanpa root)"
    AccessMode.NONE -> "belum aktif"
}

@Composable
private fun AccessCard(state: UiState, onRefresh: () -> Unit, onRequest: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Status Akses", fontWeight = FontWeight.SemiBold)
            Text(state.access.message, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.access.shizukuRunning && !state.access.shizukuPermission &&
                    state.access.mode == AccessMode.NONE
                ) {
                    Button(onClick = onRequest) { Text("Izinkan Shizuku") }
                }
                OutlinedButton(onClick = onRefresh) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Muat ulang")
                }
            }
            if (state.access.mode == AccessMode.NONE) {
                Text(
                    "Panduan: install aplikasi Shizuku → aktifkan lewat Wireless Debugging → " +
                        "buka app ini → tekan “Izinkan Shizuku”.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
private fun RamCard(state: UiState, onCleanRam: () -> Unit) {
    Card {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Memory, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Memori (RAM)", fontWeight = FontWeight.SemiBold)
            }
            val total = state.ramTotal
            val avail = state.ramAvail
            if (total > 0 && avail >= 0) {
                val used = (total - avail).coerceAtLeast(0)
                val frac = (used.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = frac,
                    modifier = Modifier.fillMaxWidth().height(10.dp),
                )
                Text(
                    "Terpakai ${MainViewModel.fmt(used)} dari ${MainViewModel.fmt(total)} " +
                        "· tersedia ${MainViewModel.fmt(avail)}",
                    fontSize = 13.sp,
                )
            } else {
                Text("Info RAM tidak tersedia.", fontSize = 13.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onCleanRam, enabled = !state.busy && state.access.mode != AccessMode.NONE) {
                    Icon(Icons.Filled.CleaningServices, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Bersihkan RAM")
                }
                if (state.selected.isNotEmpty()) {
                    Text(
                        "${state.selected.size} app terpilih akan di-force-stop",
                        fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                }
            }
        }
    }
}

@Composable
private fun CacheCard(
    state: UiState,
    onCleanSelected: () -> Unit,
    onCleanAll: () -> Unit,
    onIncludeSystem: (Boolean) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
) {
    Card {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.DeleteSweep, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Cache Aplikasi", fontWeight = FontWeight.SemiBold)
            }
            Text(
                if (state.access.mode == AccessMode.NONE)
                    "Butuh Shizuku/root untuk membersihkan cache app lain."
                else "Membersihkan cache TIDAK menghapus login/data (Android 13+).",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onCleanSelected,
                    enabled = !state.busy && state.selected.isNotEmpty() &&
                        state.access.mode != AccessMode.NONE,
                ) { Text("Cache terpilih (${state.selected.size})") }
                OutlinedButton(
                    onClick = onCleanAll,
                    enabled = !state.busy && state.apps.isNotEmpty() &&
                        state.access.mode != AccessMode.NONE,
                ) { Text("Semua cache") }
            }
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = state.includeSystem, onCheckedChange = onIncludeSystem)
                Text("Tampilkan app sistem", fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onSelectAll) { Text("Pilih semua") }
                TextButton(onClick = onClearSelection) { Text("Kosongkan") }
            }

            if (state.busy) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            Column {
                state.apps.forEach { app ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = app.packageName in state.selected,
                            onCheckedChange = { onToggleSelect(app.packageName) },
                        )
                        Column(Modifier.weight(1f)) {
                            Text(app.label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(
                                app.packageName + if (app.isSystem) "  · sistem" else "",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AutoCard(
    state: UiState,
    onAutoEnabled: (Boolean) -> Unit,
    onInterval: (Int) -> Unit,
    onAutoRam: (Boolean) -> Unit,
    onAutoCache: (Boolean) -> Unit,
) {
    val intervals = listOf(30, 60, 180, 360, 720)
    Card {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Bersihkan Otomatis", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Switch(checked = state.autoEnabled, onCheckedChange = onAutoEnabled)
            }
            Text(
                "Berjalan di latar lewat WorkManager. Butuh Shizuku aktif (di-start tiap reboot).",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            Text("Interval", fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                intervals.forEach { m ->
                    FilterChip(
                        selected = state.intervalMin == m,
                        onClick = { onInterval(m) },
                        label = { Text(if (m < 60) "${m}m" else "${m / 60}j") },
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = state.autoRam, onCheckedChange = onAutoRam)
                Text("Bersihkan RAM", fontSize = 13.sp)
                Spacer(Modifier.width(12.dp))
                Checkbox(checked = state.autoCache, onCheckedChange = onAutoCache)
                Text("Bersihkan cache", fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun LogCard(state: UiState) {
    Card {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Log", fontWeight = FontWeight.SemiBold)
            if (state.log.isEmpty()) {
                Text("Belum ada aktivitas.", fontSize = 12.sp)
            } else {
                state.log.takeLast(40).reversed().forEach {
                    Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
                }
            }
        }
    }
}

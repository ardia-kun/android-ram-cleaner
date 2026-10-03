package com.ardi.ramcleaner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DeleteSweep
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
            TopAppBar(
                title = { Text("Pembersih RAM & Cache", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onRefreshAccess) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Muat ulang")
                    }
                },
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            StatusRow(state, onRequestShizuku)
            RamCard(state, onCleanRam)
            CacheCard(state, onCleanCacheSelected, onCleanCacheAll, onIncludeSystem,
                onSelectAll, onClearSelection, onToggleSelect)
            AutoCard(state, onAutoEnabled, onInterval, onAutoRam, onAutoCache)
            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun modeLabel(mode: AccessMode): String = when (mode) {
    AccessMode.ROOT -> "Mode ROOT aktif"
    AccessMode.SHIZUKU -> "Mode Shizuku aktif"
    AccessMode.NONE -> "Belum aktif"
}

@Composable
private fun StatusRow(state: UiState, onRequest: () -> Unit) {
    val active = state.access.mode != AccessMode.NONE
    val dot = when {
        active -> Color(0xFF10B981)
        state.access.shizukuRunning -> Color(0xFFF59E0B)
        else -> Color(0xFFEF4444)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(modeLabel(state.access.mode), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(
                state.access.message,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }
        if (!active && state.access.shizukuRunning) {
            Button(onClick = onRequest) { Text("Izinkan") }
        }
    }
}

@Composable
private fun RamCard(state: UiState, onCleanRam: () -> Unit) {
    Card {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val total = state.ramTotal
            val avail = state.ramAvail
            val hasRam = total > 0 && avail >= 0
            val used = if (hasRam) (total - avail).coerceAtLeast(0) else 0
            val frac = if (hasRam) (used.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f

            Text(
                if (hasRam) "${(frac * 100).toInt()}%" else "—",
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                if (hasRam)
                    "${MainViewModel.fmt(used)} / ${MainViewModel.fmt(total)} terpakai"
                else "Info RAM tidak tersedia",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            LinearProgressIndicator(
                progress = frac,
                modifier = Modifier.fillMaxWidth().height(12.dp).clip(CircleShape),
            )
            if (hasRam) {
                Text(
                    "Tersedia ${MainViewModel.fmt(avail)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            Spacer(Modifier.height(2.dp))
            Button(
                onClick = onCleanRam,
                enabled = !state.busy && state.access.mode != AccessMode.NONE,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (state.busy) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.CleaningServices, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Bersihkan RAM", fontSize = 16.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CacheCard(
    state: UiState,
    onCleanSelected: () -> Unit,
    onCleanAll: () -> Unit,
    onIncludeSystem: (Boolean) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onToggleSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val enabled = !state.busy && state.access.mode != AccessMode.NONE

    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.DeleteSweep, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Cache Aplikasi", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
            Text(
                "Bebaskan ruang tanpa menghapus data atau login.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            Button(
                onClick = onCleanAll,
                enabled = enabled && state.apps.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text("Bersihkan Semua Cache") }

            if (state.selected.isNotEmpty()) {
                OutlinedButton(
                    onClick = onCleanSelected,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) { Text("Bersihkan Terpilih (${state.selected.size})") }
            }

            TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
                Text(if (expanded) "Tutup daftar aplikasi" else "Pilih aplikasi…")
            }

            if (expanded) {
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = state.includeSystem, onCheckedChange = onIncludeSystem)
                    Text("App sistem", fontSize = 13.sp)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onSelectAll) { Text("Pilih semua") }
                    TextButton(onClick = onClearSelection) { Text("Kosongkan") }
                }
                state.apps.forEach { app ->
                    Row(
                        Modifier.fillMaxWidth(),
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
    Card(colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Bersihkan Otomatis", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text(
                        "Berjalan sendiri di latar belakang.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }
                Switch(checked = state.autoEnabled, onCheckedChange = onAutoEnabled)
            }
            if (state.autoEnabled) {
                Text("Seberapa sering?", fontSize = 13.sp)
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
                    Text("RAM", fontSize = 13.sp)
                    Spacer(Modifier.width(12.dp))
                    Checkbox(checked = state.autoCache, onCheckedChange = onAutoCache)
                    Text("Cache", fontSize = 13.sp)
                }
            }
        }
    }
}

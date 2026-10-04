package com.kidz.cleaner.ui

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
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kidz.cleaner.backend.AccessMode
import com.kidz.cleaner.backend.AppState
import com.kidz.cleaner.data.Aggressiveness
import com.kidz.cleaner.data.AutoMode
import com.kidz.cleaner.data.NightSchedule

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    state: UiState,
    onRefreshAccess: () -> Unit,
    onRequestShizuku: () -> Unit,
    onCleanEverything: () -> Unit,
    onCleanCacheSelected: () -> Unit,
    onToggleSelect: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onIncludeSystem: (Boolean) -> Unit,
    onAutoEnabled: (Boolean) -> Unit,
    onInterval: (Int) -> Unit,
    onAutoRam: (Boolean) -> Unit,
    onAutoCache: (Boolean) -> Unit,
    onFreeze: () -> Unit,
    onUnfreeze: () -> Unit,
    onDebloat: () -> Unit,
    onRestore: (List<String>) -> Unit,
    onDismissMessage: () -> Unit,
    onQuery: (String) -> Unit,
    onDeepClean: () -> Unit,
    onRamMode: (AutoMode) -> Unit,
    onRamInterval: (Int) -> Unit,
    onRamThreshold: (Int) -> Unit,
    onRamLevel: (Aggressiveness) -> Unit,
    onNightSchedule: (NightSchedule) -> Unit,
    onNightTime: (Int, Int) -> Unit,
    onBatteryRestrict: () -> Unit,
    onBatteryUnrestrict: () -> Unit,
    onExport: () -> Unit,
    onImport: (String) -> Unit,
) {
    var tab by remember { mutableStateOf(0) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            onDismissMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Pembersih", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onRefreshAccess) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Muat ulang")
                    }
                },
            )
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 },
                    text = { Text("Bersihkan") })
                Tab(selected = tab == 1, onClick = { tab = 1 },
                    text = { Text("Bekukan / Debloat") })
            }
            if (tab == 0) {
                CleanTab(state, onCleanEverything, onDeepClean, onCleanCacheSelected,
                    onToggleSelect, onSelectAll, onClearSelection, onIncludeSystem,
                    onRequestShizuku, onAutoEnabled, onInterval, onAutoRam, onAutoCache,
                    onQuery, onRamMode, onRamInterval, onRamThreshold, onRamLevel,
                    onNightSchedule, onNightTime)
            } else {
                FreezeTab(state, onToggleSelect, onSelectAll, onClearSelection,
                    onIncludeSystem, onRequestShizuku, onFreeze, onUnfreeze, onDebloat,
                    onRestore, onQuery, onBatteryRestrict, onBatteryUnrestrict,
                    onExport, onImport)
            }
        }
    }
}

// =============================================================== Tab 1: Bersihkan

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CleanTab(
    state: UiState,
    onCleanEverything: () -> Unit,
    onDeepClean: () -> Unit,
    onCleanCacheSelected: () -> Unit,
    onToggleSelect: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onIncludeSystem: (Boolean) -> Unit,
    onRequestShizuku: () -> Unit,
    onAutoEnabled: (Boolean) -> Unit,
    onInterval: (Int) -> Unit,
    onAutoRam: (Boolean) -> Unit,
    onAutoCache: (Boolean) -> Unit,
    onQuery: (String) -> Unit,
    onRamMode: (AutoMode) -> Unit,
    onRamInterval: (Int) -> Unit,
    onRamThreshold: (Int) -> Unit,
    onRamLevel: (Aggressiveness) -> Unit,
    onNightSchedule: (NightSchedule) -> Unit,
    onNightTime: (Int, Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RamGauge(state)
        BigCleanButton(state, onCleanEverything)
        OutlinedButton(
            onClick = onDeepClean,
            enabled = !state.busy && state.access.mode != AccessMode.NONE,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Deep clean (RAM + trim + cache)") }
        AccessLine(state, onRequestShizuku)
        RamAutoSection(state, onRamMode, onRamInterval, onRamThreshold, onRamLevel)
        NightSection(state, onNightSchedule, onNightTime)
        AutoSection(state, onAutoEnabled, onInterval, onAutoRam, onAutoCache)
        AppPicker(state, onCleanCacheSelected, onToggleSelect, onSelectAll,
            onClearSelection, onIncludeSystem, onQuery)
        Spacer(Modifier.height(16.dp))
    }
}

/** Bagian "Bersihkan RAM otomatis": mode, interval/ambang, keagresifan. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RamAutoSection(
    state: UiState,
    onMode: (AutoMode) -> Unit,
    onInterval: (Int) -> Unit,
    onThreshold: (Int) -> Unit,
    onLevel: (Aggressiveness) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Bersihkan RAM otomatis", fontWeight = FontWeight.SemiBold)
                    val sub = when (state.ramAutoMode) {
                        AutoMode.OFF -> "Mati"
                        AutoMode.INTERVAL -> "Tiap ${fmtInterval(state.ramIntervalSec)} · ${levelLabel(state.ramAggressive)}"
                        AutoMode.THRESHOLD -> "Saat RAM ≥ ${state.ramThresholdPct}% · ${levelLabel(state.ramAggressive)}"
                    }
                    Text(sub, fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                }
                TextButton(onClick = { open = !open }) { Text(if (open) "Tutup" else "Atur") }
            }

            if (open) {
                // Mode
                Text("Mode", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = state.ramAutoMode == AutoMode.OFF,
                        onClick = { onMode(AutoMode.OFF) }, label = { Text("Mati") })
                    FilterChip(selected = state.ramAutoMode == AutoMode.INTERVAL,
                        onClick = { onMode(AutoMode.INTERVAL) }, label = { Text("Interval") })
                    FilterChip(selected = state.ramAutoMode == AutoMode.THRESHOLD,
                        onClick = { onMode(AutoMode.THRESHOLD) }, label = { Text("Ambang %") })
                }

                // Interval (menit)
                if (state.ramAutoMode == AutoMode.INTERVAL) {
                    Text("Setiap berapa menit", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(60, 120, 300, 600, 1800).forEach { sec ->
                            FilterChip(selected = state.ramIntervalSec == sec,
                                onClick = { onInterval(sec) },
                                label = { Text(fmtInterval(sec)) })
                        }
                    }
                }

                // Ambang %
                if (state.ramAutoMode == AutoMode.THRESHOLD) {
                    Text("Bersihkan saat pemakaian RAM mencapai", fontSize = 13.sp,
                        fontWeight = FontWeight.Medium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(60, 70, 80, 90).forEach { p ->
                            FilterChip(selected = state.ramThresholdPct == p,
                                onClick = { onThreshold(p) }, label = { Text("$p%") })
                        }
                    }
                }

                // Keagresifan
                Text("Tingkat keagresifan", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Aggressiveness.entries.forEach { a ->
                        FilterChip(selected = state.ramAggressive == a,
                            onClick = { onLevel(a) }, label = { Text(levelLabel(a)) })
                    }
                }
                Text(levelDesc(state.ramAggressive), fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
        }
    }
}

private fun fmtInterval(sec: Int): String =
    if (sec < 60) "${sec}s" else "${sec / 60} mnt"

private fun levelLabel(a: Aggressiveness): String = when (a) {
    Aggressiveness.LIGHT -> "Ringan"
    Aggressiveness.MEDIUM -> "Sedang"
    Aggressiveness.AGGRESSIVE -> "Agresif"
}

private fun levelDesc(a: Aggressiveness): String = when (a) {
    Aggressiveness.LIGHT -> "Hanya membunuh proses latar (paling hemat baterai)"
    Aggressiveness.MEDIUM -> "Bunuh proses latar + buang cache sistem"
    Aggressiveness.AGGRESSIVE -> "Bunuh proses latar + trim + tutup paksa app yang berjalan"
}

/** Bagian "Jadwal malam": bersihkan saat layar mati / pada jam tertentu. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NightSection(
    state: UiState,
    onSchedule: (NightSchedule) -> Unit,
    onTime: (Int, Int) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Jadwal malam", fontWeight = FontWeight.SemiBold)
                    val sub = when (state.nightSchedule) {
                        NightSchedule.OFF -> "Mati"
                        NightSchedule.SCREEN_OFF -> "Setiap layar dimatikan"
                        NightSchedule.SCHEDULED ->
                            "Tiap hari ${fmtJam(state.nightHour, state.nightMinute)}"
                    }
                    Text(sub, fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                }
                TextButton(onClick = { open = !open }) { Text(if (open) "Tutup" else "Atur") }
            }
            if (open) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = state.nightSchedule == NightSchedule.OFF,
                        onClick = { onSchedule(NightSchedule.OFF) }, label = { Text("Mati") })
                    FilterChip(selected = state.nightSchedule == NightSchedule.SCREEN_OFF,
                        onClick = { onSchedule(NightSchedule.SCREEN_OFF) }, label = { Text("Layar mati") })
                    FilterChip(selected = state.nightSchedule == NightSchedule.SCHEDULED,
                        onClick = { onSchedule(NightSchedule.SCHEDULED) }, label = { Text("Jam") })
                }
                if (state.nightSchedule == NightSchedule.SCHEDULED) {
                    Text("Pilih jam", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0, 1, 2, 3, 4).forEach { h ->
                            FilterChip(selected = state.nightHour == h,
                                onClick = { onTime(h, 0) },
                                label = { Text(fmtJam(h, 0)) })
                        }
                    }
                }
            }
        }
    }
}

/** Baris backup: ekspor / impor JSON. */
@Composable
private fun BackupRow(
    state: UiState,
    enabled: Boolean,
    onExport: () -> Unit,
    onImport: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Backup & pulihkan", fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                modifier = Modifier.weight(1f))
            TextButton(onClick = { open = !open }) { Text(if (open) "Tutup" else "Buka") }
        }
        if (open) {
            Text("Simpan daftar app yang dibekukan / di-debloat / dibatasi ke teks JSON, " +
                "lalu tempel kembali untuk memulihkan (mis. setelah ganti HP).",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onExport, enabled = enabled, modifier = Modifier.weight(1f)) {
                    Text("Buat backup")
                }
            }
            if (state.lastBackupJson != null) {
                Text("Hasil backup (salin simpan):", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                OutlinedTextField(
                    value = state.lastBackupJson,
                    onValueChange = {},
                    readOnly = true,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text("Pulihkan dari JSON:", fontSize = 12.sp, fontWeight = FontWeight.Medium)
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Tempel isi backup di sini") },
                maxLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                onClick = { if (text.isNotBlank()) onImport(text) },
                enabled = enabled && text.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Pulihkan") }
        }
    }
}

private fun fmtJam(h: Int, m: Int): String = String.format("%02d:%02d", h, m)

/** Lingkaran besar berisi persentase RAM terpakai. */
@Composable
private fun RamGauge(state: UiState) {
    val total = state.ramTotal
    val avail = state.ramAvail
    val hasRam = total > 0 && avail >= 0
    val used = if (hasRam) (total - avail).coerceAtLeast(0) else 0
    val frac = if (hasRam) (used.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
    val pct = (frac * 100).toInt()

    Box(
        modifier = Modifier
            .size(190.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (hasRam) "$pct%" else "—",
                fontSize = 52.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "RAM terpakai",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }
    }
    Text(
        if (hasRam)
            "${MainViewModel.fmt(used)} / ${MainViewModel.fmt(total)} · tersedia ${MainViewModel.fmt(avail)}"
        else "Info RAM tidak tersedia",
        fontSize = 13.sp,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
    )
}

/** Satu tombol besar: bersihkan RAM + cache sekaligus. */
@Composable
private fun BigCleanButton(state: UiState, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = !state.busy && state.access.mode != AccessMode.NONE,
        modifier = Modifier.fillMaxWidth().height(64.dp),
    ) {
        if (state.busy) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text("Membersihkan…", fontSize = 17.sp)
        } else {
            Text("Bersihkan Sekarang", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Satu baris status akses. */
@Composable
private fun AccessLine(state: UiState, onRequest: () -> Unit) {
    val active = state.access.mode != AccessMode.NONE
    val dot = when {
        active -> Color(0xFF10B981)
        state.access.shizukuRunning -> Color(0xFFF59E0B)
        else -> Color(0xFFEF4444)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(8.dp))
        Text(
            when (state.access.mode) {
                AccessMode.ROOT -> "Mode root aktif"
                AccessMode.SHIZUKU -> "Mode Shizuku aktif"
                AccessMode.NONE ->
                    if (state.access.shizukuRunning) "Shizuku siap — perlu izin"
                    else "Shizuku belum aktif"
            },
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
        )
        if (!active && state.access.shizukuRunning) {
            Spacer(Modifier.width(10.dp))
            TextButton(onClick = onRequest) { Text("Izinkan") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AutoSection(
    state: UiState,
    onAutoEnabled: (Boolean) -> Unit,
    onInterval: (Int) -> Unit,
    onAutoRam: (Boolean) -> Unit,
    onAutoCache: (Boolean) -> Unit,
) {
    val intervals = listOf(30, 60, 180, 360, 720)
    Card(colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Otomatis", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Switch(checked = state.autoEnabled, onCheckedChange = onAutoEnabled)
            }
            if (state.autoEnabled) {
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
                    Spacer(Modifier.width(16.dp))
                    Checkbox(checked = state.autoCache, onCheckedChange = onAutoCache)
                    Text("Cache", fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun AppPicker(
    state: UiState,
    onCleanSelected: () -> Unit,
    onToggleSelect: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onIncludeSystem: (Boolean) -> Unit,
    onQuery: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        TextButton(onClick = { expanded = !expanded }) {
            Icon(Icons.Filled.DeleteSweep, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(if (expanded) "Tutup daftar" else "Pilih aplikasi…")
        }
        if (expanded) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SearchField(state.query, onQuery)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = state.includeSystem, onCheckedChange = onIncludeSystem)
                        Text("App sistem", fontSize = 13.sp)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onSelectAll) { Text("Semua") }
                        TextButton(onClick = onClearSelection) { Text("Kosong") }
                    }
                    if (state.selected.isNotEmpty()) {
                        OutlinedButton(
                            onClick = onCleanSelected,
                            enabled = !state.busy && state.access.mode != AccessMode.NONE,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Bersihkan cache terpilih (${state.selected.size})") }
                    }
                    HorizontalDivider()
                    filtered(state).forEach { app ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = app.packageName in state.selected,
                                onCheckedChange = { onToggleSelect(app.packageName) },
                            )
                            Column(Modifier.weight(1f)) {
                                Text(app.label, fontSize = 14.sp)
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
}

/** Kotak pencarian aplikasi. */
@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        singleLine = true,
        label = { Text("Cari aplikasi…") },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Terapkan filter pencarian pada daftar aplikasi. */
private fun filtered(state: UiState): List<com.kidz.cleaner.backend.AppInfo> {
    val q = state.query.trim().lowercase()
    if (q.isEmpty()) return state.apps
    return state.apps.filter {
        it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q)
    }
}

// ====================================================== Tab 2: Bekukan / Debloat

@Composable
private fun FreezeTab(
    state: UiState,
    onToggleSelect: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onIncludeSystem: (Boolean) -> Unit,
    onRequestShizuku: () -> Unit,
    onFreeze: () -> Unit,
    onUnfreeze: () -> Unit,
    onDebloat: () -> Unit,
    onRestore: (List<String>) -> Unit,
    onQuery: (String) -> Unit,
    onBatteryRestrict: () -> Unit,
    onBatteryUnrestrict: () -> Unit,
    onExport: () -> Unit,
    onImport: (String) -> Unit,
) {
    val enabled = !state.busy && state.access.mode != AccessMode.NONE
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AccessLine(state, onRequestShizuku)

        Text(
            "Bekukan = app berhenti total tapi data tetap aman (bisa diaktifkan lagi).\n" +
                "Debloat = hapus untuk user ini; APK tetap di sistem dan bisa dipulihkan.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )

        SearchField(state.query, onQuery)

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = state.includeSystem, onCheckedChange = onIncludeSystem)
            Text("Tampilkan app sistem", fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onSelectAll) { Text("Semua") }
            TextButton(onClick = onClearSelection) { Text("Kosong") }
        }

        if (state.selected.isNotEmpty()) {
            Text("${state.selected.size} aplikasi dipilih", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onFreeze, enabled = enabled) {
                    Icon(Icons.Filled.Lock, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Bekukan")
                }
                OutlinedButton(onClick = onUnfreeze, enabled = enabled) {
                    Icon(Icons.Filled.LockOpen, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Aktifkan")
                }
            }
            OutlinedButton(onClick = onDebloat, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.DeleteForever, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Debloat (hapus untuk user)")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onBatteryRestrict, enabled = enabled,
                    modifier = Modifier.weight(1f)) {
                    Text("Hemat baterai")
                }
                OutlinedButton(onClick = onBatteryUnrestrict, enabled = enabled,
                    modifier = Modifier.weight(1f)) {
                    Text("Buka batasan")
                }
            }
            if (state.restrictedApps.isNotEmpty()) {
                Text("${state.restrictedApps.size} app dibatasi latar belakangnya",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
        }

        // ---------------------------------------------------------- backup
        HorizontalDivider()
        BackupRow(state, enabled, onExport, onImport)

        HorizontalDivider()

        filtered(state).forEach { app ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = app.packageName in state.selected,
                    onCheckedChange = { onToggleSelect(app.packageName) },
                )
                Column(Modifier.weight(1f)) {
                    Text(app.label, fontSize = 14.sp)
                    Text(app.packageName, fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
                if (app.state == AppState.FROZEN) {
                    Text("dibekukan", fontSize = 11.sp, color = Color(0xFFF59E0B),
                        fontWeight = FontWeight.SemiBold)
                }
            }
        }

        if (state.removedApps.isNotEmpty()) {
            HorizontalDivider()
            Text("Dipulihkan (${state.removedApps.size})", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text("Paket yang sudah di-debloat. Tekan untuk memulihkan.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            state.removedApps.forEach { pkg ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(pkg, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = { onRestore(listOf(pkg)) }) {
                        Icon(Icons.Filled.Restore, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Pulihkan")
                    }
                }
            }
            OutlinedButton(
                onClick = { onRestore(state.removedApps) },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Pulihkan semua") }
        }

        Spacer(Modifier.height(16.dp))
    }
}

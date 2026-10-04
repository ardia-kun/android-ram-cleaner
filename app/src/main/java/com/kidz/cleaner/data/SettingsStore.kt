package com.kidz.cleaner.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/** Preferensi aplikasi (auto-clean, mode akses, dll). */
class SettingsStore(private val context: Context) {

    private object Keys {
        val AUTO_ENABLED = booleanPreferencesKey("auto_enabled")
        val INTERVAL_MIN = intPreferencesKey("interval_min")
        val CLEAN_RAM = booleanPreferencesKey("clean_ram")
        val CLEAN_CACHE = booleanPreferencesKey("clean_cache")
        val LAST_RUN = longPreferencesKey("last_run")
        val LAST_FREED = longPreferencesKey("last_freed")

        // --- Pembersih RAM otomatis (service) ---
        val RAM_AUTO_MODE = stringPreferencesKey("ram_auto_mode")        // OFF/INTERVAL/THRESHOLD
        val RAM_INTERVAL_SEC = intPreferencesKey("ram_interval_sec")     // detik
        val RAM_THRESHOLD_PCT = intPreferencesKey("ram_threshold_pct")   // %
        val RAM_AGGRESSIVE = stringPreferencesKey("ram_aggressive")      // LIGHT/MEDIUM/AGGRESSIVE

        // --- Jadwal malam ---
        val NIGHT_SCHEDULE = stringPreferencesKey("night_schedule")      // OFF/SCREEN_OFF/SCHEDULED
        val NIGHT_HOUR = intPreferencesKey("night_hour")                 // 0..23
        val NIGHT_MINUTE = intPreferencesKey("night_minute")             // 0..59
        val NIGHT_LAST_DAY = longPreferencesKey("night_last_day")        // epoch day terakhir dijalankan
    }

    val autoEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.AUTO_ENABLED] ?: false }
    val intervalMin: Flow<Int> = context.dataStore.data.map { it[Keys.INTERVAL_MIN] ?: 180 }
    val cleanRam: Flow<Boolean> = context.dataStore.data.map { it[Keys.CLEAN_RAM] ?: true }
    val cleanCache: Flow<Boolean> = context.dataStore.data.map { it[Keys.CLEAN_CACHE] ?: true }
    val lastRun: Flow<Long> = context.dataStore.data.map { it[Keys.LAST_RUN] ?: 0L }
    val lastFreed: Flow<Long> = context.dataStore.data.map { it[Keys.LAST_FREED] ?: -1L }

    val ramAutoMode: Flow<AutoMode> =
        context.dataStore.data.map { AutoMode.from(it[Keys.RAM_AUTO_MODE]) }
    val ramIntervalSec: Flow<Int> = context.dataStore.data.map { it[Keys.RAM_INTERVAL_SEC] ?: 300 }
    val ramThresholdPct: Flow<Int> = context.dataStore.data.map { it[Keys.RAM_THRESHOLD_PCT] ?: 80 }
    val ramAggressive: Flow<Aggressiveness> =
        context.dataStore.data.map { Aggressiveness.from(it[Keys.RAM_AGGRESSIVE]) }

    val nightSchedule: Flow<NightSchedule> =
        context.dataStore.data.map { NightSchedule.from(it[Keys.NIGHT_SCHEDULE]) }
    val nightHour: Flow<Int> = context.dataStore.data.map { it[Keys.NIGHT_HOUR] ?: 2 }
    val nightMinute: Flow<Int> = context.dataStore.data.map { it[Keys.NIGHT_MINUTE] ?: 0 }
    val nightLastDay: Flow<Long> = context.dataStore.data.map { it[Keys.NIGHT_LAST_DAY] ?: -1L }

    suspend fun setAutoEnabled(v: Boolean) = context.dataStore.edit { it[Keys.AUTO_ENABLED] = v }
    suspend fun setInterval(min: Int) = context.dataStore.edit { it[Keys.INTERVAL_MIN] = min }
    suspend fun setCleanRam(v: Boolean) = context.dataStore.edit { it[Keys.CLEAN_RAM] = v }
    suspend fun setCleanCache(v: Boolean) = context.dataStore.edit { it[Keys.CLEAN_CACHE] = v }
    suspend fun setLastRun(t: Long) = context.dataStore.edit { it[Keys.LAST_RUN] = t }
    suspend fun setLastFreed(b: Long) = context.dataStore.edit { it[Keys.LAST_FREED] = b }

    suspend fun setRamAutoMode(m: AutoMode) =
        context.dataStore.edit { it[Keys.RAM_AUTO_MODE] = m.name }
    suspend fun setRamIntervalSec(s: Int) =
        context.dataStore.edit { it[Keys.RAM_INTERVAL_SEC] = s }
    suspend fun setRamThresholdPct(p: Int) =
        context.dataStore.edit { it[Keys.RAM_THRESHOLD_PCT] = p }
    suspend fun setRamAggressive(a: Aggressiveness) =
        context.dataStore.edit { it[Keys.RAM_AGGRESSIVE] = a.name }

    suspend fun setNightSchedule(s: NightSchedule) =
        context.dataStore.edit { it[Keys.NIGHT_SCHEDULE] = s.name }
    suspend fun setNightTime(hour: Int, minute: Int) = context.dataStore.edit {
        it[Keys.NIGHT_HOUR] = hour
        it[Keys.NIGHT_MINUTE] = minute
    }
    suspend fun setNightLastDay(day: Long) = context.dataStore.edit { it[Keys.NIGHT_LAST_DAY] = day }
}

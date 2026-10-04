package com.kidz.cleaner.data

/** Mode pembersihan RAM otomatis. */
enum class AutoMode {
    /** Mati. */
    OFF,

    /** Bersihkan setiap beberapa menit sekali. */
    INTERVAL,

    /** Bersihkan ketika pemakaian RAM menyentuh persentase tertentu. */
    THRESHOLD;

    companion object {
        fun from(s: String?): AutoMode = entries.firstOrNull { it.name == s } ?: OFF
    }
}

/**
 * Tingkat keagresifan pembersihan RAM.
 *
 * - [LIGHT]      hanya `am kill-all` (paling aman & hemat baterai).
 * - [MEDIUM]     `am kill-all` + trim cache sistem.
 * - [AGGRESSIVE] kill-all + trim + force-stop app yang masih berjalan di latar
 *                (paling kuat, tapi app bisa tertutup sampai dibuka lagi).
 */
enum class Aggressiveness {
    LIGHT, MEDIUM, AGGRESSIVE;

    companion object {
        fun from(s: String?): Aggressiveness =
            entries.firstOrNull { it.name == s } ?: MEDIUM
    }
}

/**
 * Pembersihan terjadwal pada jam tertentu.
 *
 * Bila [SCHEDULED] dipilih, service memeriksa setiap menit; saat jam cocok
 * (dan belum dijalankan hari ini) RAM dibersihkan otomatis.
 */
enum class NightSchedule {
    OFF,

    /** Bersihkan setiap kali layar dimatikan. */
    SCREEN_OFF,

    /** Bersihkan pada jam tertentu (lihat [SettingsStore.nightHour]). */
    SCHEDULED;

    companion object {
        fun from(s: String?): NightSchedule =
            entries.firstOrNull { it.name == s } ?: OFF
    }
}

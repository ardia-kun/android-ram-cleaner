package com.kidz.cleaner.backend

/**
 * Daftar paket yang TIDAK BOLEH dibekukan/di-debloat.
 *
 * Membekukan atau menghapus paket di sini bisa membuat HP bootloop, kehilangan
 * sinyal, atau tidak bisa dibuka (launcher hilang). Ini pengaman utama fitur
 * debloat — sama seperti peringatan "critical" di Canta.
 */
object Guard {

    /** Paket inti sistem yang selalu diblokir. */
    private val CORE = setOf(
        // Framework & UI inti
        "android",
        "com.android.systemui",
        "com.android.settings",
        "com.android.shell",
        "com.android.keychain",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",

        // Telepon & SMS (kalau dibekukan: tidak bisa telepon/SMS)
        "com.android.phone",
        "com.android.server.telecom",
        "com.android.dialer",
        "com.google.android.dialer",
        "com.android.providers.telephony",
        "com.android.mms",
        "com.android.messaging",

        // Provider penting
        "com.android.providers.settings",
        "com.android.providers.contacts",
        "com.android.providers.downloads",
        "com.android.providers.media",
        "com.android.providers.media.module",
        "com.android.providers.calendar",
        "com.android.providers.userdictionary",

        // Launcher (kalau hilang: tidak ada home screen)
        "com.android.launcher",
        "com.android.launcher3",
        "com.google.android.apps.nexuslauncher",
        "com.sec.android.app.launcher",
        "com.miui.home",
        "com.oppo.launcher",
        "com.coloros.launcher",
        "com.vivo.launcher",
        "com.huawei.android.launcher",
        "com.bbk.launcher2",
        "com.android.launcher2",

        // Konektivitas (kalau dibekukan: sinyal/WiFi hilang)
        "com.android.bluetooth",
        "com.android.wifi",
        "com.android.networkstack",
        "com.android.networkstack.tethering",
        "com.android.server.telecom",
        "com.android.phone",
        "com.android.cellbroadcastreceiver",

        // Keamanan & update
        "com.android.se",
        "com.android.keychain",
        "com.google.android.gms",
        "com.google.android.gsf",

        // Input & kamera dasar (jaga-jaga)
        "com.android.inputmethod.latin",
    )

    /** Tambahan per-vendor yang juga berisiko. */
    private val VENDOR = setOf(
        "com.samsung.android.app.telephonyui",
        "com.samsung.android.incallui",
        "com.samsung.android.messaging",
        "com.miui.securitycenter",
        "com.miui.securityadd",
        "com.oplus.safecenter",
        "com.coloros.safecenter",
        "com.vivo.permissionmanager",
        "com.huawei.systemmanager",
    )

    /** Prefiks yang menandakan paket kritis (dicek juga). */
    private val PROTECTED_PREFIXES = listOf(
        "com.android.providers.",
        "com.android.internal.",
    )

    /** True bila paket ini harus dilindungi (tidak boleh dibekukan/di-debloat). */
    fun isProtected(pkg: String): Boolean {
        if (pkg in CORE || pkg in VENDOR) return true
        if (PROTECTED_PREFIXES.any { pkg.startsWith(it) }) return true
        return false
    }

    /** Alasan singkat bila dilindungi (untuk ditampilkan ke pengguna). */
    fun reason(pkg: String): String? = when {
        pkg in CORE || PROTECTED_PREFIXES.any { pkg.startsWith(it) } ->
            "paket inti sistem — berisiko bootloop"
        pkg in VENDOR -> "komponen penting vendor"
        else -> null
    }
}

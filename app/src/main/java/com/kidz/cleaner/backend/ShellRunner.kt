package com.kidz.cleaner.backend

/**
 * Hasil eksekusi sebuah perintah shell.
 */
data class ShellResult(
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
) {
    val ok: Boolean get() = exitCode == 0
    val combined: String get() = (stdout + "\n" + stderr).trim()
}

/**
 * Mode akses istimewa.
 *
 * - [SHIZUKU] menjalankan perintah sebagai ADB shell (uid 2000) — tanpa root.
 * - [ROOT]    menjalankan perintah sebagai superuser (uid 0) — perlu Magisk/dll.
 * - [NONE]    tidak ada akses istimewa; hanya operasi terbatas.
 */
enum class AccessMode { SHIZUKU, ROOT, NONE }

/**
 * Abstraksi "cara menjalankan perintah shell istimewa".
 *
 * Implementasi nyata ada di [RootRunner] dan [ShizukuRunner]. Dengan abstraksi ini
 * seluruh logika pembersihan tidak peduli sedang memakai Shizuku atau root.
 */
interface ShellRunner {
    val mode: AccessMode

    /** Benar bila backend siap dipakai (Shizuku berjalan & diizinkan / su tersedia). */
    fun isReady(): Boolean

    /**
     * Jalankan perintah dan kembalikan hasilnya.
     * Implementasi harus blocking-safe: dipanggil dari thread IO, bukan main thread.
     */
    fun exec(command: String): ShellResult

    /** Perintah multi-baris; dibungkus `sh -c`. */
    fun exec(vararg commands: String): ShellResult =
        exec(commands.joinToString(" ; "))

    companion object {
        /**
         * Pilih backend terbaik yang tersedia.
         * Prioritas: root bila user memilih & su ada, lalu Shizuku, lalu NONE.
         */
        fun detect(preferRoot: Boolean): ShellRunner {
            val root = RootRunner()
            val shizuku = ShizukuRunner()
            return when {
                preferRoot && root.isReady() -> root
                shizuku.isReady() -> shizuku
                root.isReady() -> root
                else -> shizuku // tetap kembalikan Shizuku agar bisa meminta izin
            }
        }
    }
}

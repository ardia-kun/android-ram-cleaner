package com.kidz.cleaner.backend

import java.util.concurrent.TimeUnit

/**
 * Menjalankan perintah sebagai root via `su -c`.
 *
 * Aman dipakai walau HP tidak di-root: [isReady] mengembalikan false bila `su`
 * tidak ada, dan [exec] mengembalikan exit code 127 tanpa melempar exception.
 */
class RootRunner : ShellRunner {

    override val mode = AccessMode.ROOT

    /** Cek sekali apakah binary `su` benar-benar bisa dieksekusi (dan izin diberikan). */
    override fun isReady(): Boolean = try {
        val p = ProcessBuilder("su", "-c", "id").redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        val done = p.waitFor(6, TimeUnit.SECONDS)
        if (!done) { p.destroy(); false } else out.contains("uid=0")
    } catch (_: Exception) {
        false
    }

    override fun exec(command: String): ShellResult = try {
        val p = ProcessBuilder("su", "-c", command).start()
        val stdout = p.inputStream.bufferedReader().use { it.readText() }
        val stderr = p.errorStream.bufferedReader().use { it.readText() }
        val finished = p.waitFor(60, TimeUnit.SECONDS)
        if (!finished) {
            p.destroy()
            ShellResult("", "timeout", 124)
        } else {
            ShellResult(stdout, stderr, p.exitValue())
        }
    } catch (e: Exception) {
        ShellResult("", e.message ?: "root tidak tersedia", 127)
    }
}

package com.kidz.cleaner.backend

import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import com.kidz.cleaner.BuildConfig
import com.kidz.cleaner.ICleanerService
import rikka.shizuku.Shizuku
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Menjalankan perintah shell lewat Shizuku (tanpa root) memakai UserService.
 *
 * Alur: app -> Shizuku server -> UserService (proses milik kita, tapi identitas shell/root)
 * -> menjalankan `sh -c "<perintah>"`.
 */
class ShizukuRunner : ShellRunner {

    override val mode = AccessMode.SHIZUKU

    @Volatile
    private var service: ICleanerService? = null

    /** Shizuku berjalan? (binder hidup) */
    fun hasBinder(): Boolean = try {
        Shizuku.pingBinder()
    } catch (_: Throwable) {
        false
    }

    /** Izin Shizuku sudah diberikan? */
    fun hasPermission(): Boolean = try {
        !Shizuku.isPreV11() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    override fun isReady(): Boolean = hasBinder() && hasPermission()

    /** Minta izin Shizuku (dialog muncul dari app Shizuku). */
    fun requestPermission(requestCode: Int) {
        try {
            Shizuku.requestPermission(requestCode)
        } catch (_: Throwable) {
        }
    }

    private val userServiceArgs: Shizuku.UserServiceArgs by lazy {
        Shizuku.UserServiceArgs(
            ComponentName(BuildConfig.APPLICATION_ID, CleanerUserService::class.java.name)
        )
            .daemon(false)
            .processNameSuffix("cleaner")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)
    }

    /** Pastikan UserService ter-bind; kembalikan interface-nya atau null. */
    private fun ensureService(timeoutMs: Long = 10_000): ICleanerService? {
        service?.let { return it }
        if (!hasBinder() || !hasPermission()) return null

        val latch = CountDownLatch(1)
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                service = ICleanerService.Stub.asInterface(binder)
                latch.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                service = null
            }
        }
        return try {
            Shizuku.bindUserService(userServiceArgs, conn)
            latch.await(timeoutMs, TimeUnit.MILLISECONDS)
            service
        } catch (_: Throwable) {
            null
        }
    }

    override fun exec(command: String): ShellResult {
        val svc = ensureService()
            ?: return ShellResult("", "Shizuku belum siap atau izin belum diberikan", 126)
        return try {
            val out = svc.exec(command) ?: ""
            // UserService menggabungkan stderr ke stdout; deteksi error umum.
            val exit = if (out.startsWith("[ERROR]") || out.contains("[TIMEOUT]")) 1 else 0
            ShellResult(out, "", exit)
        } catch (e: Throwable) {
            ShellResult("", e.message ?: "shizuku error", 1)
        }
    }
}

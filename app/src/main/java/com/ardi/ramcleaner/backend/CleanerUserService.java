package com.ardi.ramcleaner.backend;

import android.content.Context;
import android.os.RemoteException;

import androidx.annotation.Keep;

import com.ardi.ramcleaner.ICleanerService;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/**
 * UserService yang dijalankan Shizuku dengan identitas shell/root.
 *
 * <p>Karena proses ini dijalankan oleh Shizuku (bukan oleh app kita), perintah di
 * dalamnya otomatis berjalan sebagai ADB shell (uid 2000) atau root (uid 0) —
 * persis seperti menjalankan {@code adb shell}. Inilah sebabnya app kita bisa
 * membersihkan cache aplikasi lain tanpa root.
 */
public class CleanerUserService extends ICleanerService.Stub {

    /** Konstruktor tanpa argumen — wajib ada. */
    public CleanerUserService() {
    }

    /**
     * Konstruktor dengan Context — tersedia sejak Shizuku API v13.
     * Harus diberi anotasi {@link Keep} agar tidak dihapus ProGuard.
     */
    @Keep
    public CleanerUserService(Context context) {
    }

    @Override
    public void destroy() throws RemoteException {
        System.exit(0);
    }

    @Override
    public String exec(String command) throws RemoteException {
        try {
            ProcessBuilder pb = new ProcessBuilder("sh", "-c", command);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String out = readAll(p.getInputStream());
            boolean done = p.waitFor(60, TimeUnit.SECONDS);
            if (!done) {
                p.destroy();
                return out + "\n[TIMEOUT]";
            }
            return out;
        } catch (Exception e) {
            return "[ERROR] " + e;
        }
    }

    private static String readAll(InputStream is) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) != -1) {
            bos.write(buf, 0, n);
        }
        return bos.toString("UTF-8");
    }
}

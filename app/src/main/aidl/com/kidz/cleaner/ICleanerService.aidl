// AIDL untuk UserService Shizuku.
// UserService = kode kita yang dijalankan Shizuku dengan identitas shell (uid 2000)
// atau root (uid 0) — inilah cara resmi Shizuku API v13 untuk menjalankan perintah.
package com.kidz.cleaner;

interface ICleanerService {

    // Method wajib (didefinisikan Shizuku server) — jangan diubah.
    void destroy() = 16777114;

    // Menjalankan perintah shell, mengembalikan gabungan stdout+stderr.
    String exec(String command) = 1;
}

# RAM & Cache Cleaner — Android (tanpa root)

Aplikasi Android untuk **membersihkan RAM dan cache aplikasi lain secara otomatis**
menggunakan **Shizuku** (tanpa root) atau **root** bila tersedia.

Dibangun dengan **Kotlin + Jetpack Compose (Material 3)**, di-build menjadi APK lewat
**GitHub Actions**.

---

## ⚠️ Kenyataan penting (baca dulu)

Sejak **Android 6.0**, izin `CLEAR_APP_CACHE` dan `DELETE_CACHE_FILES` berubah menjadi
`signature|privileged`. Artinya **aplikasi biasa tidak bisa** menghapus cache aplikasi
lain secara otomatis — ini batasan sistem operasi, bukan kekurangan aplikasi ini.

Solusinya ada dua:

| Cara | Bisa bersihkan cache app lain? | Perlu root? |
|---|---|---|
| **Shizuku** | ✅ Ya (setara izin `adb shell`) | ❌ Tidak |
| **Root** | ✅ Ya (akses penuh) | ✅ Ya |

Aplikasi ini **mendukung keduanya** dan otomatis memilih yang tersedia
(root diprioritaskan bila ada, lalu Shizuku).

---

## Fitur

- 🧠 **Bersihkan RAM** — `am kill-all` (membunuh proses latar dengan aman) dan
  `am force-stop` untuk aplikasi yang Anda pilih.
- 🗑️ **Bersihkan cache massal** — `pm clear --cache-only` untuk semua/banyak aplikasi
  sekaligus, **tanpa** menghapus login & data.
- ⏰ **Otomatis & berkala** — dijadwalkan dengan WorkManager (pilihan interval
  30 mnt – 12 jam), berjalan di latar tanpa membuka app.
- 📊 **Monitor RAM** — total, terpakai, tersedia (real-time dari `/proc/meminfo`).
- ☑️ **Pilih aplikasi sendiri** — daftar app dengan pencarian, filter app sistem,
  pilih semua / kosongkan.
- 📝 **Log aktivitas** — semua aksi tercatat di layar.

---

## Keamanan (penting!)

- **Clear cache pakai `pm clear --cache-only`** → hanya cache, **data & login tetap utuh**.
- **Dibatasi ke Android 13+ (SDK 33).** Di Android 12 ke bawah flag `--cache-only`
  **diabaikan diam-diam** oleh sistem, sehingga `pm clear` akan **menghapus SEMUA data**
  aplikasi. Karena itu fitur ini **diblokir** di perangkat lama (aplikasi akan memberi tahu).
- **RAM** memakai `am kill-all` (aman, tidak mengganggu app yang sedang dibuka).
  `am force-stop` hanya dijalankan untuk aplikasi yang Anda centang.
- Aplikasi ini **tidak** mengirim data ke mana pun. Tidak ada iklan, tidak ada analytics.

---

## Arsitektur

```
app/src/main/
├── aidl/com/ardi/ramcleaner/ICleanerService.aidl   # kontrak UserService (Shizuku)
├── java/com/ardi/ramcleaner/
│   ├── backend/
│   │   ├── ShellRunner.kt        # interface + AccessMode + auto-detect
│   │   ├── ShizukuRunner.kt      # jalankan shell via Shizuku (UserService)
│   │   ├── RootRunner.kt         # jalankan shell via `su -c`
│   │   ├── CleanerUserService.java # kode yang dijalankan dengan identitas shell/root
│   │   └── CleanerEngine.kt      # logika: list app, ukur & bersihkan cache, RAM
│   ├── data/SettingsStore.kt     # preferensi (DataStore)
│   ├── work/                     # CleanWorker, Scheduler, BootReceiver
│   ├── ui/                       # MainViewModel, MainScreen (Compose), Theme
│   └── MainActivity.kt
└── AndroidManifest.xml
```

**Kenapa pakai UserService?** Di Shizuku API v13, `Shizuku.newProcess()` sudah
**private** (tidak bisa dipakai lagi). Cara resmi adalah **UserService**: Shizuku
menjalankan kode kita di proses terpisah dengan identitas shell/root. Perintah shell
di dalamnya otomatis berjalan seperti `adb shell` — inilah kunci pembersihan tanpa root.

---

## Build APK (via GitHub Actions — tanpa install apa pun di komputer)

1. Push repo ini ke GitHub (lihat bawah).
2. Buka tab **Actions** → workflow **Build APK** berjalan otomatis.
3. Setelah selesai, unduh **artifact** `app-debug` (dan `app-release`).

Atau jalankan manual: **Actions → Build APK → Run workflow**.

### Push ke GitHub

```bash
cd android-ram-cleaner
git init -b main
git add -A
git commit -m "RAM & Cache Cleaner: Shizuku + root"
git remote add origin git@github.com:<USERNAME>/android-ram-cleaner.git
git push -u origin main
```

### Build lokal (opsional)

Butuh JDK 17 + Android SDK:

```bash
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/android-sdk
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

---

## Cara pakai

### 1. Siapkan Shizuku (tanpa root)

1. Install **Shizuku** dari Play Store / [shizuku.rikka.app](https://shizuku.rikka.app/download/).
2. Aktifkan **Wireless debugging** (Pengaturan → Opsi developer).
3. Buka Shizuku → **Start** → pilih *Wireless debugging* → ikuti langkahnya.
   (Di Android 11+ bisa langsung dari HP, tanpa komputer.)
4. Buka aplikasi ini → tekan **Izinkan Shizuku** → setujui.

> Shizuku harus di-start ulang setiap HP reboot (kecuali pakai Sui/root).

### 2. Bersihkan

- **Bersihkan RAM** — tekan tombol, proses latar dimatikan.
- **Cache** — centang aplikasi lalu **Cache terpilih**, atau **Semua cache**.
- **Otomatis** — nyalakan *Bersihkan Otomatis*, pilih interval.

### 3. (Opsional) Mode root

Bila HP sudah di-root (Magisk), aplikasi otomatis memakai mode **ROOT** — tanpa
perlu Shizuku, dan auto-clean tetap berjalan setelah reboot.

---

## Troubleshooting

| Masalah | Solusi |
|---|---|
| "Shizuku belum berjalan" | Buka app Shizuku dan Start (via Wireless debugging). |
| Tombol cache tidak aktif | Butuh Shizuku/root. Cek kartu *Status Akses*. |
| "Butuh Android 13+" | Clear cache per-app tidak didukung di Android ≤12 (demi keamanan). |
| Auto-clean tidak jalan | Shizuku harus aktif; setelah reboot perlu Start Shizuku lagi (kecuali root). |
| Build gagal di Actions | Cek log; pastikan `platforms;android-34` terpasang (sudah ada di workflow). |

---

## Lisensi

MIT — bebas dipakai dan dimodifikasi.

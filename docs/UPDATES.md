# Distribusi, signing dan updater

Repository resmi: https://github.com/IkhwanN-kyp/PianoFlow . Aset APK resmi berasal dari GitHub Releases repository ini. Kanal Stable mengecualikan prerelease; Beta menerima stable dan prerelease, lalu memilih SemVer tertinggi. Tag tanpa APK/update.json diabaikan. Downgrade ditolak meskipun pengguna berpindah kanal.

## Identitas yang dipertahankan

Package: `id.arus.piano`. Sertifikat SHA-256:

```
eda0f798078f5eb48b7237301a2e4261544639d333625ab6c4b265f55862636d
```

Ini sertifikat yang menandatangani APK Arus Piano 0.1.0 yang dikirim sebelumnya. Private key dipertahankan, dipindahkan ke PKCS12 privat dengan kata sandi acak, dan disimpan terenkripsi sebagai GitHub Actions Secrets. Nama subjek sertifikat lama tetap Android Debug karena mengganti sertifikat akan memutus identitas update; **APK release baru tidak debuggable**. Distribusi ini menggunakan sideload resmi, bukan Google Play App Signing.

Secrets: `PIANOFLOW_KEYSTORE_BASE64`, `PIANOFLOW_STORE_PASSWORD`, `PIANOFLOW_KEY_PASSWORD`, `PIANOFLOW_KEY_ALIAS`. Tidak ada secret dalam commit/ZIP/APK. Backup privat lokal dikelola di luar repository. Jangan mengekspor secret lewat logs atau mengganti key untuk setiap versi. Kehilangan kunci memutus kemampuan update kompatibel.

## Rantai verifikasi

1. API publik `/repos/IkhwanN-kyp/PianoFlow/releases?per_page=100`, HTTPS dan host GitHub/CDN yang diizinkan.
2. Pilih versi sesuai kanal dan SemVer; cocokkan `update.json`, package ID, nama versi, ukuran dan pin sertifikat.
3. Unduh APK ke `files/updates/update.part`. Cek panjang tepat dan SHA-256. Tidak memakai direktori publik yang bisa diganti aplikasi lain.
4. Verifikasi signature APK menggunakan Android apksig, bukan sekadar membaca sertifikat. Cocokkan satu signer dengan pin dan signature aplikasi yang sedang terpasang.
5. Parse manifest APK; package/version harus cocok dengan rilis dan lebih baru dari aplikasi terpasang. Pemeriksaan diulang sebelum membuat sesi installer.
6. PackageInstaller FULL_INSTALL dengan package yang sama; Android memverifikasi lagi dan meminta konfirmasi pengguna. Tidak ada uninstall/clear-data pada jalur update.

Checksum dari server saja tidak membuktikan identitas pembuat; pin sertifikat dan verifikasi kriptografis menambahkan pemeriksaan itu. Pemilik private key tetap memiliki kendali rilis. Sistem installer bisa menolak karena ruang disk, kebijakan perangkat atau izin OEM.

Pemeriksaan otomatis paling sering setiap 24 jam saat aplikasi dibuka; kegagalan jaringan tidak memblokir antrean impor atau latihan. Settings > Software Update memberikan error/progres manual. Audio/MIDI tidak diunggah. Pembatalan layar update menghentikan pekerjaan download; unduhan tidak dipasang tanpa tindakan pengguna.

## Data dan migrasi

Aplikasi mempertahankan application ID, class preference `MainActivity`, direktori privat, salinan lagu, nama lagu, model/bank impor serta checkpoint. Referensi URI dari Arus Piano lama dibaca lalu dimigrasi ke salinan lokal setelah file berhasil diakses. Tidak menganggap URI yang sudah dicabut sebagai file yang masih bisa diakses.

Update normal mempertahankan data. Uninstall, clear data, reset tablet atau memasang fork berkunci lain bukan jalur update kompatibel. Tidak ada klaim backup cloud; `allowBackup=false` menjaga data tetap lokal. Uji marker data lama tersedia pada VERIFICATION.md.

## GitHub Actions

Workflow `.github/workflows/android.yml`:

- Build dan lint; pemeriksaan hash vendor dan pemeriksaan file secret.
- Core tests dan emulator Android 30 tanpa Google Play Services, memakai x86_64 untuk hosted Linux. ARM64 diuji lokal.
- Hanya tag `v*` yang mengakses signing Secrets dan melakukan publikasi. Actions dipin ke commit resmi.
- Kunci didekode ke direktori sementara runner dengan permission terbatas, lalu dihapus pada langkah always.
- Source ZIP dan corresponding native sources dilampirkan bersama APK, model opsional, metadata updater dan checksum. Publikasi dilakukan dari draft lengkap.

Tag rilis yang sudah dipublikasikan tidak boleh dipakai ulang untuk binary berbeda; naikkan SemVer. Bila job gagal saat upload draft, perbaiki masalah lalu tangani draft secara eksplisit sebelum mencoba kembali. Jangan menghapus rilis publik yang sudah digunakan updater tanpa alasan yang jelas.

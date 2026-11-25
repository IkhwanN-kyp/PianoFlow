# Verifikasi PianoFlow 0.2.0-beta.3

Diperiksa 26 November 2026 (Asia/Jakarta). Hasil kompilasi, emulator dan hardware dipisahkan.

## Kredit dan halaman Tentang beta.3

Build ARM64 dan lint berhasil. Emulator ARM64 lulus **185 pemeriksaan runtime**, termasuk versi/kode versi yang berasal dari BuildConfig, kredit ASNkit, dukungan sukarela, atribusi bukan-fork, serta render landscape dalam bahasa Indonesia/tema gelap dan English/tema terang. Core 43 assertion + 300 mutasi dan 110 pemeriksaan tambahan tetap lolos; enam regresi release tools dan 23 hash vendor/source lolos. Halaman Tentang memuat tautan GitHub/Trakteer dan pembaca teks lisensi offline. Pengujian perangkat fisik tetap belum dilakukan.

## Distribusi dan perbaikan penyimpanan

Pipeline beta.1 sudah berhasil membangun, menguji, menandatangani dan memublikasikan APK: https://github.com/IkhwanN-kyp/PianoFlow/actions/runs/45603260090 . Beta.2 mempertahankan ID/nama/track kosong saat ekspor sehingga penetapan tangan tidak bergeser pada impor baru; regresi metadata dan track ditambahkan. File beta.1 tetap dapat dibaca.

## Bukti yang sudah ada

- JDK 17, Gradle 8.10.2, AGP 8.7.3, SDK/build-tools 35, NDK 27.2.12479018 dan CMake 3.22.1; build native ARM64 serta Android lint berhasil.
- Core: **43 assertion + 300 mutasi MIDI rusak**, ditambah **110 pemeriksaan** export MIDI, tempo/meter, MusicXML/XXE, grid partitur, GM, MIDI CC dan SemVer.
- Emulator Android 11/API 30 ARM64, landscape 1400 × 920 (rasio 2800 × 1840), tanpa Google Play Services: **171 pemeriksaan runtime** lolos pada tahap integrasi, kemudian **177 pemeriksaan** setelah menambahkan tes keamanan updater.
- Termasuk fitur latihan lama, audio FluidSynth/AudioTrack, 128 preset GM yang benar-benar menghasilkan sampel audio, amplitudo velocity, sustain down/up, WAV decode/resample dan deteksi akor C–E–G melalui Basic Pitch.
- Decoder MP3, WAV, M4A dan FLAC diuji dengan akor sintetis asli, bukan lagu komersial. M4A dibuat dengan AAC encoder Android; MP3/FLAC berasal dari fixture asli yang sama.
- MusicXML dengan grand staff/meter berhasil diparse pada parser Android; DTD eksternal ditolak. PDF diekspor, dibuka dengan PdfRenderer dan diverifikasi berisi staff/not.
- Ketiga tampilan dirender; palette light dan terjemahan English diperiksa.
- APK baru dipasang di atas Arus Piano 0.1.0 tanpa uninstall; marker privat `preserved-v0.1.0` tetap terbaca. Sertifikat tetap sama.
- Hash 23 binary/asset/model/font/bank dan corresponding native sources dipin serta diperiksa. Kunci signing dipindahkan ke PKCS12 privat berkata sandi acak dan GitHub Actions Secrets; tidak dimasukkan ke source.

Tambahan enam tes updater memakai APK bertanda tangan asli dengan versi lebih tinggi, checksum keliru, metadata versi keliru, APK versi sama/downgrade, APK dengan signer lain, serta APK yang payload-nya dirusak meskipun checksum metadata disesuaikan. APK resmi diterima; kelima kasus penolakan lolos. APK fixture hanya dipakai di emulator, tidak dipublikasikan sebagai versi produk.

## Hasil distribusi resmi

- Beta.2 berhasil dibangun, diuji, ditandatangani dan dipublikasikan otomatis: https://github.com/IkhwanN-kyp/PianoFlow/actions/runs/45605672941 . Kedua job test dan release berstatus success. Rilis: https://github.com/IkhwanN-kyp/PianoFlow/releases/tag/v0.2.0-beta.2 .
- SHA-256 APK resmi: `9e3dabae06f0b3a2f0ce5bfcb8b2fbe18563be249d04f9433bd681ed160d275e`; 27.154.617 byte; package `id.arus.piano`; versionCode `2002002`; APK Signature Scheme v2 valid, sertifikat sama dan non-debuggable.
- Aplikasi beta.1 resmi berhasil membaca beta.2 melalui GitHub Releases API dan menampilkan versi terpasang/terbaru, ukuran 25,90 MiB, kanal Beta serta changelog yang benar. Uji end-to-end selesai 26 November 2026: unduhan, verifikasi, izin unknown-source, dialog konfirmasi Android dan instalasi beta.1 → beta.2 berhasil. PackageManager menunjukkan versionName `0.2.0-beta.2` dan versionCode `2002002` setelah instalasi tanpa uninstall. Perpustakaan masih menampilkan `converted.mid` dan lagu impor sebelumnya. Tab Konversi juga tetap menampilkan `Basic Pitch · model siap` setelah update.
- Alur UI konversi diuji secara manual: impor model resmi dari Downloads, pilih WAV asli, jalankan Basic Pitch, buka editor not, hapus satu not estimasi, dan Masuk latihan. Hasil terkoreksi benar-benar muncul di latihan/perpustakaan.
- Catatan rilis diperbaiki dan ditambah regresi parser batas bagian changelog. Ini perubahan tooling/metadata; tidak mengubah APK yang sudah diterbitkan.

## Belum dibuktikan oleh emulator

Tidak ada Huawei TGR-W09, Yamaha P-145, kabel USB/OTG atau keyboard lampu fisik yang terhubung. Belum terbukti: kompatibilitas firmware HarmonyOS 4.2, input/output USB end-to-end, descriptor/driver, hot-unplug pada perangkat tersebut, MIDI output ke speaker piano, latensi akustik, kualitas timbre pada speaker tablet, multitouch fisik sepuluh jari dan sesi berjam-jam.

Tes MIDI sintetis memanggil listener produksi; itu bukan transfer USB fisik. Render PCM dan inferensi akor uji bukan jaminan kualitas transkripsi seluruh lagu. Keberhasilan installer pada Android emulator juga tidak menghapus kebutuhan uji izin/installer OEM Huawei. Lihat docs/LIMITATIONS.md.

## Uji penerimaan perangkat pengguna

1. Pasang menimpa APK Arus Piano lama; periksa data/lagu sebelumnya dan checkpoint.
2. Dalam mode pesawat, coba Grand Piano 1, velocity, sustain dan beberapa instrumen. Coba SF2 kustom yang izinnya jelas.
3. Sambungkan P-145 dengan kabel data USB 2.0 + OTG dan catu daya piano sendiri. Coba Android MIDI, kemudian raw USB bila perlu. Periksa note-on/off, pedal dan cabut-pasang.
4. Coba wait chord, ritme, tempo 50/100/150%, tangan, loop, pause serta partitur/hybrid.
5. Impor modul Basic Pitch resmi dan rekaman solo buatan sendiri; koreksi hasil, ekspor MIDI dan masuk latihan. Coba MusicXML sederhana serta PDF.
6. Periksa Settings > Software Update, kanal Stable/Beta, unduhan, verifikasi, izin unknown-source dan konfirmasi installer. Jangan uninstall untuk melakukan update.

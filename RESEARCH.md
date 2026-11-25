# Referensi teknis dan lisensi

Diperiksa 25 November 2026. Bukti dokumen dibedakan dari pengujian perangkat.

## Yamaha P-145

- [MIDI Reference P-145/P-143 resmi Yamaha](https://data.yamaha.com/files/download/other_assets/4/2144534/p145_en_mr_a0.pdf) menjelaskan komunikasi MIDI melalui sambungan USB, pesan/channel dan penerimaan/transmisi. Ini menjadi dasar penggunaan USB MIDI, bukan bukti bahwa aplikasi ini sudah terhubung ke piano fisik.
- [Owner’s Manual resmi Yamaha](https://my.yamaha.com/files/download/other_assets/3/2144473/P-145_owners_manual_En_C0.pdf), bagian terminal USB TO HOST, menjelaskan pertukaran MIDI/audio dan syarat kabel. Revisi manual dapat berbeda antar wilayah. MVP ini memakai pesan MIDI; tidak mengimplementasikan USB audio piano.
- [Smart Device Connection Manual for Android resmi Yamaha](https://data.yamaha.com/en/download/files/2109203/), Connection A, menggambarkan USB TO HOST Type-B ke Android melalui kabel A–B dan adaptor USB Type-C. Kabel kurang dari 3 m dan ketentuan USB 2.0 perlu diikuti.
- [Spesifikasi resmi P-145 Yamaha Jepang](https://jp.yamaha.com/products/musical_instruments/pianos/p_series/p-145/specs.html) mencantumkan USB TO HOST untuk MIDI/audio. Produk ini tidak memiliki sistem tuts bercahaya; output lampu tidak diklaim untuk P-145.
- Manual Android umum adalah dasar rancangan sambungan, bukan daftar sertifikasi P-145 dengan TGR-W09. Tidak menganggap dukungan aplikasi Smart Pianist pada platform tertentu sebagai bukti universal untuk semua aplikasi MIDI.

## Huawei dan Android

- [Spesifikasi Huawei Indonesia MatePad 11.5 S](https://consumer.huawei.com/id/tablets/matepad-11-5-s/specs/): HarmonyOS 4.2, 2800 × 1840, multitouch hingga 10 titik, USB Type-C dan dukungan OTG. [Halaman Huawei UK](https://consumer.huawei.com/uk/tablets/matepad-11-5-s/specs/) menamai OTG secara eksplisit dan menyebut batas suplai balik 1 A/5 V. Halaman spesifikasi umum tidak mengesahkan kombinasi aplikasi/firmware TGR-W09 dengan P-145.
- [Panduan Huawei tentang instalasi APK](https://consumer.huawei.com/ca/support/content/en-us00448892/) mencantumkan HarmonyOS 4.2 dalam versi yang berlaku. Ini mendukung pemilihan jalur APK untuk HarmonyOS 4.2, tetapi bukan jaminan seluruh Android API tersedia. Tidak memperluas klaim ini ke HarmonyOS NEXT.
- [AOSP MIDI](https://source.android.com/docs/core/audio/midi): dukungan MIDI dapat diaktifkan produsen sejak Android 6.0 dan bergantung pada implementasi kernel/USB. Oleh sebab itu APK bisa dipasang dan OTG bisa tersedia sementara Android MIDI service tetap tidak tersedia/tidak lengkap.
- [Android MidiManager](https://developer.android.com/reference/android/media/midi/MidiManager): jalur utama untuk enumerasi perangkat dan port. Arah port memakai perspektif perangkat: output port piano menjadi input aplikasi; input port perangkat menjadi tujuan output aplikasi.
- [Android USB Host](https://developer.android.com/develop/connectivity/usb/host): enumerasi perangkat, izin akses pengguna, klaim interface dan transfer endpoint. Inilah dasar jalur cadangan; tidak mengandalkan browser Web MIDI.

**Kesimpulan yang merupakan inferensi:** USB MIDI P-145 + USB OTG pada tablet membuat implementasi Android native masuk akal. Jalur USB host cadangan dapat membantu jika MIDI service tidak tersedia. Keduanya masih harus diuji pada hardware/firmware pengguna; tidak ada bukti empiris kompatibilitas fisik dari pekerjaan ini.

## Referensi visual MIDIVisualizer

- [Repository kosua20/MIDIVisualizer](https://github.com/kosua20/MIDIVisualizer) adalah visualizer C++/OpenGL desktop. README menjelaskan visualisasi piano roll, warna, garis, dan keyboard; juga menyatakan aplikasi referensi tidak menyediakan playback audio soundtrack.
- [Lisensi MIT repository](https://github.com/kosua20/MIDIVisualizer/blob/master/LICENSE), copyright Simon Rodriguez, mengizinkan penggunaan dengan kewajiban mempertahankan pemberitahuan jika menyalin kode atau bagian substansialnya. Pustaka pendukung memiliki cakupan lisensi masing-masing dan tidak otomatis seluruhnya dicakup lisensi akar.
- Arus Piano merupakan implementasi baru dalam Java/Android Canvas. Tidak ada kode, shader, ikon, gambar, font, logo, merek, atau library dari repository tersebut yang disalin atau dibundel. Inspirasi dibatasi pada ide umum not vertikal menuju keyboard. Warna, kontrol, layout, ikon, renderer, parser, audio dan mesin latihan dibuat sendiri. Karena tidak mengadopsi implementasi C++/OpenGL desktop, proyek ini bukan port MIDIVisualizer.
- Source Arus Piano diberikan dengan lisensi MIT pada `LICENSE`. Gradle Wrapper memiliki lisensi Apache 2.0; lihat `THIRD_PARTY_NOTICES.md`. Alat build Android/JDK/Gradle tidak dimasukkan dalam ZIP source atau APK pengguna.

Tidak ada lagu komersial dibundel. Demo adalah pola tangga nada/bas yang dihasilkan oleh kode. Pengguna memilih file lokalnya sendiri.

## Tambahan PianoFlow 0.2

- FluidSynth 2.6.3 memakai paket Android24 resmi: https://github.com/FluidSynth/fluidsynth/releases/tag/v2.6.3 . Binary, header, versi dependensi dan corresponding sources dipin bersama; lihat third_party/provenance.json dan sumber workflow Android upstream.
- Spotify Basic Pitch: https://github.com/spotify/basic-pitch . Model ICASSP 2022 TFLite berlisensi Apache-2.0 menjadi modul opsional. Inference Android memakai CPU; input 22.050 Hz dan window model 43.844 sampel. Postprocessing Android sendiri tidak diklaim identik dengan Python upstream.
- Bank redistributable: https://github.com/arbruijn/TimGM6mb (GPL-2.0-only). Font Bravura: https://github.com/steinbergmedia/bravura (SIL OFL 1.1). Salinan lisensi dan source terkait disertakan.
- GitHub Releases API: https://docs.github.com/en/rest/releases/releases . Installer Android memakai PackageInstaller dan signature verifier apksig; tidak bergantung pada Play Services.

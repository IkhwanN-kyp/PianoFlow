# Build dan pengembangan

## Lingkungan

JDK 17; Gradle Wrapper 8.10.2 (checksum dipin); AGP 8.7.3; SDK 35/build-tools 35.0.0; NDK 27.2.12479018; CMake 3.22.1. Atur `JAVA_HOME` dan `ANDROID_HOME`, atau `sdk.dir` pada `local.properties`. Android Studio dapat membuka root project ini.

```sh
sdkmanager 'platforms;android-35' 'build-tools;35.0.0' 'ndk;27.2.12479018' 'cmake;3.22.1'
python3 scripts/verify_vendor.py
./test-core.sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. Default ARM64. `-PtargetAbi=x86_64` digunakan untuk emulator CI, bukan APK rilis tablet. Dependensi Maven perlu internet pada build pertama; sesudah cache siap dapat memakai `--offline`.

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w id.arus.piano.test/id.arus.piano.SmokeInstrumentation
```

APK test khusus pengembangan, jangan dipasang untuk penggunaan normal. Model, provider fixture dan generator AAC test tidak ada di APK aplikasi utama. Core tests tidak memerlukan Android atau framework test pihak ketiga. Tes runtime memakai Java Instrumentation, codec Android dan library produksi secara langsung.

## Struktur

- `MainActivity`, `Trainer`, `PianoView`: kontrol latihan dan rendering. `ScoreRenderer`/`ScoreLayout`/`ScorePdf` memakai clock yang sama.
- `MidiSong`, `MidiWriter`, `MusicXml`: parsing/export dan tempo/meter berbasis quarter beats.
- `MidiHub`, `MidiStream`: Android MIDI, raw USB fallback, running status, realtime note/control dispatch.
- `OfflineSynth`, `FluidNative`, `app/src/main/cpp/audio.cpp`: render SF2 melalui FluidSynth dinamis ke AudioTrack; live MIDI tidak menunggu repaint UI untuk audio.
- `AudioDecoder`, `Transcriber`, `ConverterActivity`: decode streaming, model TFLite opsional, monophonic fallback, preview/editor/export/practice.
- `LibraryStore`: salinan MIDI content-addressed privat. Checkpoint per lagu disimpan dalam preferences. Package/class preference lama dipertahankan untuk migrasi Arus Piano.
- `UpdateClient`, `ReleaseVersion`, `SoftwareUpdateActivity`, `InstallResultReceiver`: verifikasi rilis, unduhan privat dan installer.

## Native dependencies dan lisensi

Binary FluidSynth Android diambil utuh dari rilis resmi 2.6.3. File `.so` ARM64/x86_64 dipin SHA-256. Corresponding source FluidSynth, libsndfile dan dependensinya beserta submodule header-only ada di `third_party/sources/`; versi dan URL dicatat di provenance. Ini shared libraries, bukan port FluidSynth yang disamarkan.

Untuk mengubah library native, ekstrak source sesuai versi, isi folder `gcem` dan `signalsmith-audio-basics` dari arsip commit yang disertakan, lalu ikuti `.azure/azure-pipelines-android.yml` dan `.azure/cmake-android.yml` upstream. Keduanya mendokumentasikan NDK, versi dependensi dan perintah CMake. Perbarui `.so`, header, lisensi dan provenance bersama-sama. Rebuild aplikasi dengan Gradle. Source aplikasi tidak melarang penggantian/relinking library LGPL untuk penggunaan pribadi.

SF2 TimGM6mb merupakan bentuk editable dari bank; file dan lisensinya disertakan. Bank/model/font tidak boleh diganti dengan aset tanpa izin. Jangan memasukkan lagu komersial ke fixture. Lihat THIRD_PARTY_NOTICES.

## Versi dan rilis

`VERSION` memuat SemVer. `scripts/version_code.py` dan konfigurasi Gradle memakai formula monotonic: major×100000000 + minor×1000000 + patch×10000 + tahap (alpha=1000, beta=2000, rc=3000, stable=9000) + iterasi. Minor/patch maksimum 99, iterasi maksimum 999; komponen major dibatasi plafon Android.

Ubah VERSION dan CHANGELOG, jalankan tes, commit lalu tag `vMAJOR.MINOR.PATCH[-beta.N]`. Workflow tag memakai version dari tag, membangun release non-debuggable, menandatangani dengan GitHub Secrets, memeriksa sertifikat/package/version, menyiapkan SHA-256/update.json/source/model, mengunggah sebagai draft lalu memublikasikannya setelah seluruh aset tersedia. Branch/PR hanya mendapat build/test tanpa signing secrets.

Fork: buat kunci sendiri dan ubah `UpdateClient.CERT`, `BuildConfig.UPDATE_REPOSITORY`, serta pin pada `prepare_release.py`. Fork tidak dapat memperbarui APK resmi dengan kunci berbeda. Kunci resmi tidak ada dalam source.

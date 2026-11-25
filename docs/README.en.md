# PianoFlow — English overview

Native Android piano practice, continuing Arus Piano with the same application ID (`id.arus.piano`) and signing certificate. Free of ads, subscriptions, mandatory accounts and commercial song-import quotas. Release 0.2.0-beta.5 is intended for personal testing.

Download the ARM64 APK from [official Releases](https://github.com/IkhwanN-kyp/PianoFlow/releases). Install over the old app to retain its data; do not uninstall. For future versions use Settings > Software Update, select Stable/Beta, and choose Download & Install Update. APK SHA-256, cryptographic signature, certificate, package ID and increasing version are checked before the Android system installer. Google Play Services are not used.

Practice retains falling notes, 88-key multitouch, real-time MIDI, chord wait mode, tempo, pause, loops, left/right/track filtering and scoring. Connect a powered Yamaha P-145 USB TO HOST with a USB 2.0 data cable and USB-C OTG adapter. Select Android MIDI input, or raw USB MIDI 1 fallback if needed. The P-145 does not have illuminated keys. Generic output guidance does not support proprietary vendor lighting protocols.

FluidSynth 2.6.3 uses the redistributable TimGM6mb SF2 bank. Acoustic Grand Piano is default; 128 GM programs, SoundFont import, velocity, sustain, reverb, polyphony and tablet/MIDI output selection are functional. Additional SoundFonts remain optional.

Convert MP3/WAV/M4A/FLAC offline with Android codecs and the optional Spotify Basic Pitch TFLite module, downloadable separately from Releases. Import the official model before choosing polyphonic transcription. A model-free monophonic melody method is also available. Preview, edit individual notes, export MIDI or open the result directly in practice. Transcription is an estimate and does not separate stems or guarantee exact notes.

Sheet, falling-note and hybrid views share the practice clock. Grand staff, treble/bass clefs, meter, tempo and highlights are rendered. Uncompressed partwise MusicXML import and native PDF export are available. Notation uses display-only sixteenth quantization and is not professional engraving; compressed MXL, advanced repeats, ties and ornaments remain outside the current implementation.

Dark/light themes and Indonesian/English UI are in Settings. Songs and checkpoints are local; audio is never uploaded. Network access is used only by the updater. The physical Huawei TGR-W09/P-145 combination and acoustic latency have not been tested. Read [limitations](LIMITATIONS.md), [build instructions](DEVELOPMENT.md), [update/signing design](UPDATES.md), and [verification](../VERIFICATION.md).

## Free play and keyboard labels

Tap **Free play** in the upper toolbar and play screen keys, a computer keyboard or MIDI piano. Bars rise from the keyboard, grow while held, then detach on release. No song playback or scoring runs in this mode. **Stop & clear** silences notes and clears trails; **Back to song** preserves the paused song position and score. MIDI velocity controls trail intensity; sustain affects sound, while trail length follows physical key duration.

**Key labels** offers C D E F G A B, 1–7, Do Re Mi Fa Sol La Si or no labels. These settings persist across launches and apply to practice too. Number 1 / Do always means C, with sharps on black keys and small octave anchors (C4 is middle C). This does not transpose the piano.

**Settings > Help & guide** includes an offline topic guide in both languages; **About** contains installed version/code, optional support, upstream credits and license texts.

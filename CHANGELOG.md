# Changelog

## [0.2.0-beta.3] - 2026-11-26

- Add About with installed version and version code, ASNkit / @radarswingid credit and optional Trakteer support.
- Add Indonesian/English credits, upstream GitHub links and an offline viewer for original bundled licenses.
- Clarify independent development and MIDIVisualizer visual inspiration; retain existing features, package ID, signing identity and user data.

## [0.2.0-beta.2] - 2026-11-25

- Preserve MIDI track IDs, empty tracks and track names when saving songs, preventing hand-role mappings from shifting after reimport.
- Avoid duplicate tempo/meter events during repeated MIDI export.
- Add regression checks for stable track identity and per-track learning settings.
- Retain the same package ID, signing key and all beta.1 features; existing files remain readable.

## [0.2.0-beta.1] - 2026-11-25

- Rename Arus Piano to PianoFlow while retaining package ID and signing identity.
- Add FluidSynth SF2 audio, a redistributable complete GM bank, 128 instruments, velocity, sustain, reverb and polyphony controls.
- Add tablet/MIDI sound routing, direct MIDI audio dispatch and MIDI program/control playback.
- Add optional offline Basic Pitch transcription, MP3/WAV/M4A/FLAC decoding, audio/MIDI preview, event correction, MIDI export and direct practice loading.
- Add synchronized grand staff, meter/tempo display, hybrid view, uncompressed partwise MusicXML import and native PDF export.
- Add dark/light themes, Indonesian/English UI, local song library and per-song checkpoints.
- Add public GitHub distribution, automatic/manual Stable/Beta update checks, download progress, SHA-256 and cryptographic APK signature verification, and system installation without Google Play Services.
- Preserve previous falling notes, 88-key multitouch keyboard, USB MIDI API/fallback, chord wait mode, tempo, loops, hand selection and scoring.
- Beta limitations: no physical MatePad/P-145 validation or measured latency; transcription and notation remain approximate. See docs/LIMITATIONS.md.

## [0.1.0] - 2026-11-25

Original Arus Piano native Android MVP with MIDI import, falling notes, 88-key multitouch, Android MIDI/raw USB host, wait-for-note, tempo, pause, loops, hand/track filtering, scoring and procedural offline audio.

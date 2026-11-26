# Changelog

## [0.2.0-beta.6] - 2026-11-26

- Add track/channel parts with I play, Automatic backing and Mute roles, per-part hand/level, global backing mix and saved per-song choices.
- Add isolated offline part audition, automatic piano/backing suggestions from GM programs, and shared-channel/program-change information.
- Play backing during practice with the trainer clock: wait, tempo, pause, seek and loops remain synchronized; only learner parts are scored and displayed.
- Preserve controller/program source tracks through MIDI library roundtrips; use file programs for playback independently from live keyboard sound selection.
- Handle simultaneous learner unisons and overlapping playback note releases without requiring duplicate key presses or cutting held notes.
- Include an original five-note piano/guitar MIDI fixture, update the bilingual guide and retain previous features, signing identity and user data.

## [0.2.0-beta.5] - 2026-11-26

- Add Free play with live rising note bars for multitouch, computer keyboard and MIDI input; bars follow physical key duration and MIDI velocity.
- Preserve song position and score when switching modes; add Stop & clear, bounded animation history, input-source overlap handling and lifecycle/disconnect cleanup.
- Add persistent 88-key letter, fixed-C number, fixed-Do solfege and hidden label choices, including chromatic sharp labels and octave anchors.
- Replace the help dialog with a bilingual offline guide grouped by task, and reorganize About with clearer information and component credits.
- Dispatch free-play screen-key MIDI output immediately; retain prior training, audio, conversion, notation, updater, signing identity and data.

## [0.2.0-beta.4] - 2026-11-26

- Correct the support section to use PianoFlow consistently in Indonesian and English; keep @radarswingid and the optional Trakteer link.
- Preserve all application features, signing identity, package ID and user data.

## [0.2.0-beta.3] - 2026-11-26

- Add About with installed version and version code, PianoFlow / @radarswingid credit and optional Trakteer support.
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

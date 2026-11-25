# Third-party notices and corresponding source

Application Java/C++ bridge, original icon and generated scale exercise: MIT, see LICENSE. Separate data/library licenses remain in force; application MIT does not replace them. No commercial song, recording, logo or MIDIVisualizer asset/code is bundled.

| Component | Version / source | License / distribution |
|---|---|---|
| FluidSynth | 2.6.3 official Android24 build | LGPL-2.1; shared library, matching source archive included |
| libsndfile | 1.2.2 | LGPL-2.1; shared library, matching source included |
| Oboe | 1.10.0 | Apache-2.0; shared library, source included |
| libogg | 1.3.6 | BSD; source/license included |
| libvorbis | 1.3.7 | BSD; source/license included |
| FLAC C library | 1.4.3 | BSD; source/license included (FLAC++/CLI are not linked) |
| Opus | 1.5.2 | BSD; source/license included |
| gcem | commit in source provenance | Apache-2.0; header-only corresponding source included |
| signalsmith-audio-basics | commit in source provenance | MIT; corresponding source included |
| Android NDK libc++ | NDK 27.2.12479018 | LLVM/Apache with exceptions; NOTICE included |
| TimGM6mb SF2 | Tim Brechbill / David Bolton | GPL-2.0-only; unmodified editable SF2 and full notices included |
| Bravura music font | Steinberg reference SMuFL font | SIL OFL 1.1; unmodified font and OFL included |
| Spotify Basic Pitch model | official icassp_2022 nmp.tflite | Apache-2.0; optional model distributed separately, license included |
| TensorFlow Lite | 2.16.1 | Apache-2.0; CPU runtime Maven dependency; upstream source github.com/tensorflow/tensorflow/tree/v2.16.1 |
| Android apksig | 8.7.3 | Apache-2.0; Android build tools project, upstream android.googlesource.com/platform/tools/apksig |
| Gradle Wrapper | 8.10.2 | Apache-2.0; original headers and embedded/extracted license retained |

Original copyright/license texts are under `app/src/main/assets/licenses/` and also in the release native-source archive. SHA-256 and upstream URLs for binaries, SF2, model and font are in `third_party/provenance.json`. Matching archives and source URLs/hashes are in `third_party/sources/provenance.json`, including pinned submodule commits. The LGPL libraries are dynamically linked; users can modify/relink these libraries using the provided application and corresponding source. No reverse-engineering restriction is imposed for debugging such modifications.

TimGM6mb source and provenance: https://github.com/arbruijn/TimGM6mb . Its COPYING records the authors' sample-origin/license statements. Rendered codec test audio is an original C-major chord from that bank, provided under GPL-2.0-only; see tests/fixtures/README.md. It is not a third-party song.

Visual concept reference only: https://github.com/kosua20/MIDIVisualizer (MIT, Simon Rodriguez). This project implements its own Android renderer and does not copy that project's code/assets or claim endorsement by Spotify, Steinberg, Yamaha, Huawei, FluidSynth, MIDIVisualizer or Synthesia.

SDK, NDK tools, JDK and Gradle distributions are development tools downloaded separately. Signing materials are never part of the source distribution.

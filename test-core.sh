#!/bin/sh
set -eu
cd "$(dirname "$0")"
TASK_CLASSES="${TMPDIR:-/tmp}/arus-core-$$"
mkdir -p "$TASK_CLASSES"
trap 'rm -rf "$TASK_CLASSES"' EXIT
TASK_JAVAC=javac
TASK_JAVA=java
if [ -n "${JAVA_HOME:-}" ]; then TASK_JAVAC="$JAVA_HOME/bin/javac"; TASK_JAVA="$JAVA_HOME/bin/java"; fi
"$TASK_JAVAC" -encoding UTF-8 -d "$TASK_CLASSES" app/src/main/java/id/arus/piano/MidiSong.java app/src/main/java/id/arus/piano/Trainer.java app/src/main/java/id/arus/piano/MidiStream.java app/src/main/java/id/arus/piano/MidiWriter.java app/src/main/java/id/arus/piano/MusicXml.java app/src/main/java/id/arus/piano/ScoreLayout.java app/src/main/java/id/arus/piano/ReleaseVersion.java app/src/main/java/id/arus/piano/GeneralMidi.java app/src/main/java/id/arus/piano/FreePlay.java app/src/main/java/id/arus/piano/NoteLabels.java tests/CoreTests.java tests/ExtendedTests.java tests/LiveTests.java
"$TASK_JAVA" -cp "$TASK_CLASSES" id.arus.piano.CoreTests

"$TASK_JAVA" -cp "$TASK_CLASSES" id.arus.piano.ExtendedTests samples/latihan-asli.mid

"$TASK_JAVA" -cp "$TASK_CLASSES" id.arus.piano.LiveTests

package id.arus.piano;

import java.io.*;
import java.util.*;

/** Real MIDI roundtrips and event traces against the shared practice clock. */
public final class ArrangementTests {
  static int checks;

  static void ok(boolean value, String label) {
    checks++;
    if (!value) throw new AssertionError(label);
  }

  static final class Trace implements SongPlayback.Sink {
    List<String> events = new ArrayList<>();

    public void send(int ch, int kind, int a, int b) {
      events.add(ch + ":" + kind + ":" + a + ":" + b);
    }

    boolean has(int ch, int kind, int a, int b) {
      return events.contains(ch + ":" + kind + ":" + a + ":" + b);
    }

    long ons() {
      return events.stream().filter(e -> e.contains(":144:")).count();
    }

    void clear() {
      events.clear();
    }
  }

  public static MidiSong duet() {
    Map<Integer, String> tracks = new TreeMap<>();
    tracks.put(0, "Conductor");
    tracks.put(1, "Piano left");
    tracks.put(2, "Piano right");
    tracks.put(3, "Guitar");
    return new MidiSong(
        Arrays.asList(
            new MidiSong.Note(48, 80, 0, 1, 1, 2),
            new MidiSong.Note(60, 100, 0, 2, 1, 1.5),
            new MidiSong.Note(67, 100, 1, 3, 1, 2),
            new MidiSong.Note(69, 90, 1, 3, 2.5, 3),
            new MidiSong.Note(62, 90, 0, 2, 3, 3.5)),
        tracks,
        0,
        Collections.singletonList(new MidiSong.Tempo(0, 0, 500000)),
        Collections.singletonList(new MidiSong.Meter(0, 4, 4)),
        Arrays.asList(
            new MidiSong.Message(0, 0xc0, 0, 0, 1),
            new MidiSong.Message(0, 0xc1, 24, 0, 3),
            new MidiSong.Message(1.1, 0xb1, 64, 127, 3),
            new MidiSong.Message(2, 0xb1, 64, 0, 3)));
  }

  public static void main(String[] args) throws Exception {
    MidiSong song = duet();
    Trainer t = new Trainer();
    t.load(song);
    ok(t.arrangement.parts.size() == 3, "empty conductor is not an instrument part");
    ok(
        t.arrangement.get(48 + 1).mode == Arrangement.BACKING,
        "GM guitar defaults to backing when piano exists");
    ok(
        t.arrangement.get(16).mode == Arrangement.PLAY
            && t.arrangement.get(32).mode == Arrangement.PLAY,
        "two piano staves remain separate playable parts");
    ok(
        t.arrangement.get(16).sharedChannel && t.arrangement.get(32).sharedChannel,
        "shared-channel state is flagged");
    ok(t.arrangement.get(49).program == 24, "guitar program identified by channel");
    ok(
        t.selected(song.notes.get(0)) && !t.selected(song.notes.get(2)),
        "backing excluded from scoring");
    ok(
        t.visible(song.notes.get(0)) && !t.visible(song.notes.get(2)),
        "practice visuals exclude backing");
    t.listen = true;
    ok(t.visible(song.notes.get(2)), "Listen visuals include backing");
    t.listen = false;
    Trace trace = new Trace();
    SongPlayback player = new SongPlayback(t, trace);
    t.playing = true;
    t.time = 0;
    player.sync(t.time, t.playing, t.listen);
    trace.clear();
    t.update(1.5);
    ok(t.time == 1, "wait gates only piano onset");
    player.sync(t.time, true, false);
    ok(trace.has(1, 0x90, 67, 70), "guitar sounds during practice at backing mix level");
    ok(
        !trace.has(0, 0x90, 48, 80) && !trace.has(0, 0x90, 60, 100),
        "learner piano is not auto-played");
    long count = trace.ons();
    t.update(5);
    player.sync(t.time, true, false);
    ok(t.time == 1 && trace.ons() == count, "waiting does not retrigger or advance accompaniment");
    t.press(48);
    t.press(60);
    ok(t.hits == 2 && t.wrong == 0, "only learner chord scored");
    t.update(.2);
    player.sync(t.time, true, false);
    ok(trace.has(1, 0xb0, 64, 127), "backing sustain follows song time");
    t.speed = .5;
    t.update(1);
    player.sync(t.time, true, false);
    ok(Math.abs(t.time - 1.7) < .00001, "tempo changes shared clock");
    t.update(1);
    player.sync(t.time, true, false);
    ok(
        trace.has(1, 0x80, 67, 0) && trace.has(1, 0xb0, 64, 0),
        "note off and sustain release scheduled");
    trace.clear();
    player.sync(t.time, false, false);
    ok(trace.has(1, 0xb0, 120, 0), "pause silences scheduled voices");
    trace.clear();
    player.sync(2.7, true, false);
    ok(
        trace.has(1, 0xc0, 24, 0) && trace.has(1, 0x90, 69, 63),
        "seek/resume rebuilds program and active notes");
    ok(trace.ons() == 1, "seek never sounds all historical notes");
    trace.clear();
    player.sync(1, true, false);
    ok(
        trace.has(1, 0x90, 67, 70) && !trace.has(1, 0x90, 69, 63),
        "loop rewind restarts correct accompaniment");
    player.stop();
    trace.clear();
    player.sync(1, true, true);
    ok(
        trace.has(0, 0x90, 48, 80) && trace.has(0, 0x90, 60, 100) && trace.has(1, 0x90, 67, 70),
        "Listen plays all non-muted parts");
    player.stop();
    t.arrangement.get(49).mode = Arrangement.MUTE;
    trace.clear();
    player.sync(1, true, true);
    ok(!trace.has(1, 0x90, 67, 100) && trace.ons() == 2, "Mute suppresses note playback in Listen");
    player.stop();
    t.arrangement.get(49).mode = Arrangement.BACKING;
    t.arrangement.backingVolume = 0;
    trace.clear();
    player.sync(1, true, false);
    ok(trace.ons() == 0, "zero backing level is silent");
    player.stop();
    t.arrangement.backingVolume = 50;
    t.arrangement.get(49).volume = 40;
    trace.clear();
    player.sync(1, true, false);
    ok(trace.has(1, 0x90, 67, 20), "part and mix volume combine");
    player.stop();
    t.arrangement.get(49).mode = Arrangement.MUTE;
    t.arrangement.get(49).volume = 100;
    player.solo(49);
    trace.clear();
    player.sync(1, true, true);
    ok(trace.ons() == 1 && trace.has(1, 0x90, 67, 100), "solo audition isolates even a muted part");
    player.stop();
    t.arrangement.get(16).mode = Arrangement.MUTE;
    t.arrangement.get(32).mode = Arrangement.MUTE;
    ok(!t.hasPracticeNotes(), "empty learner selection detected");
    // One SMF-0-style track with two channels still exposes two independent parts.
    MidiSong combined =
        MidiSong.of(
            Arrays.asList(
                new MidiSong.Note(60, 90, 0, 0, 0, 1), new MidiSong.Note(67, 90, 1, 0, 0, 1)));
    ok(
        new Arrangement(combined).parts.size() == 2,
        "format-0 multi-channel track splits into parts");
    // Preserve controller track ownership through repeated library export/import.
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    MidiWriter.write(song, bytes);
    MidiSong copy = MidiSong.read(new ByteArrayInputStream(bytes.toByteArray()));
    ok(copy.tracks.equals(song.tracks), "all original track names/ids retained");
    ok(
        copy.messages.size() == 4
            && copy.messages.get(1).track == 3
            && copy.messages.get(1).a == 24,
        "program source track retained through MIDI roundtrip");
    ok(new Arrangement(copy).get(49).program == 24, "reopened library song keeps guitar identity");
    bytes.reset();
    MidiWriter.write(copy, bytes);
    MidiSong again = MidiSong.read(new ByteArrayInputStream(bytes.toByteArray()));
    ok(again.messages.get(1).track == 3, "repeated export keeps message ownership");
    Trainer unison = new Trainer();
    unison.load(
        MidiSong.of(
            Arrays.asList(
                new MidiSong.Note(60, 90, 0, 0, 1, 2), new MidiSong.Note(60, 90, 0, 1, 1, 2))));
    unison.time = 1;
    unison.playing = true;
    unison.press(60);
    unison.update(.2);
    ok(
        unison.hits == 1 && unison.time > 1 && unison.judged[0] == 1 && unison.judged[1] == 1,
        "one key satisfies simultaneous unison notes on two learner tracks");
    unison.seek(1);
    unison.wait = false;
    unison.update(.3);
    ok(unison.misses == 1, "unison miss counted once");
    Trainer overlap = new Trainer();
    overlap.load(
        MidiSong.of(
            Arrays.asList(
                new MidiSong.Note(60, 90, 0, 0, 0, 2),
                new MidiSong.Note(60, 90, 0, 1, 1, 3),
                new MidiSong.Note(64, 90, 1, 2, 1, 1))));
    Trace overlapTrace = new Trace();
    SongPlayback overlapPlayer = new SongPlayback(overlap, overlapTrace);
    overlapPlayer.sync(0, true, true);
    overlapTrace.clear();
    overlapPlayer.sync(1, true, true);
    ok(
        overlapTrace.ons() == 0,
        "overlap does not duplicate attack and zero-duration notes do not hang");
    overlapPlayer.sync(2, true, true);
    ok(!overlapTrace.has(0, 0x80, 60, 0), "one part's note-off does not cut another held unison");
    overlapPlayer.sync(3, true, true);
    ok(overlapTrace.has(0, 0x80, 60, 0), "final overlapping note releases sound");
    Trainer guitarOnly = new Trainer();
    guitarOnly.load(
        new MidiSong(
            song.notes.subList(2, 3), song.tracks, 0, song.tempos, song.meters, song.messages));
    ok(
        guitarOnly.arrangement.get(49).mode == Arrangement.PLAY,
        "non-piano-only song remains learnable by default");
    Trace isolated = new Trace();
    SongPlayback isolatedPlayer = new SongPlayback(t, isolated);
    isolatedPlayer.solo(49);
    isolatedPlayer.sync(1, true, true);
    ok(
        !isolated.has(0, 0xc0, 0, 0),
        "muted/non-audible channel controller state is not sent to learner output");
    isolatedPlayer.stop();
    String signature = t.arrangement.learningSignature();
    t.arrangement.backingVolume = 80;
    t.arrangement.get(49).volume = 50;
    ok(
        signature.equals(t.arrangement.learningSignature()),
        "level changes do not invalidate learning score configuration");
    t.arrangement.get(49).hand = 1;
    ok(
        !signature.equals(t.arrangement.learningSignature()),
        "hand changes invalidate learning scoring without seeking to start");
    if (args.length > 0)
      try (OutputStream output = new FileOutputStream(args[0])) {
        MidiWriter.write(song, output);
      }
    System.out.println(
        "PASS arrangement: "
            + checks
            + " checks (parts, scoring, wait, tempo, controllers, seek, loops, audition, MIDI"
            + " roundtrip)");
  }
}

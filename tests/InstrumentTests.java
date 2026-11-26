package id.arus.piano;

import java.util.*;

/** Independent program overrides on identical source channels; source MIDI stays intact. */
public final class InstrumentTests {
  static int checks;

  static void ok(boolean v, String label) {
    checks++;
    if (!v) throw new AssertionError(label);
  }

  static final class Trace implements SongPlayback.Sink {
    final List<int[]> events = new ArrayList<>();

    public void send(int channel, int kind, int a, int b) {
      events.add(new int[] {channel, kind, a, b});
    }

    int program(int channel) {
      int result = -1;
      for (int[] e : events) if (e[0] == channel && e[1] == 0xc0) result = e[2];
      return result;
    }

    boolean has(int channel, int kind, int a, int b) {
      for (int[] e : events)
        if (e[0] == channel && e[1] == kind && e[2] == a && e[3] == b) return true;
      return false;
    }
  }

  static MidiSong song() {
    Map<Integer, String> names = new TreeMap<>();
    names.put(0, "Track 1");
    names.put(1, "Track 2");
    names.put(2, "Track 3");
    return new MidiSong(
        Arrays.asList(
            new MidiSong.Note(60, 90, 0, 0, 1, 2),
            new MidiSong.Note(60, 95, 0, 1, 1, 3),
            new MidiSong.Note(60, 100, 0, 2, 1, 4)),
        names,
        0,
        Collections.singletonList(new MidiSong.Tempo(0, 0, 500000)),
        Collections.singletonList(new MidiSong.Meter(0, 4, 4)),
        Arrays.asList(
            new MidiSong.Message(0, 0xc0, 0, 0),
            new MidiSong.Message(1.5, 0xc0, 80, 0),
            new MidiSong.Message(1.6, 0xb0, 0, 5),
            new MidiSong.Message(1.7, 0xb0, 64, 127),
            new MidiSong.Message(2.2, 0xb0, 64, 0)));
  }

  public static void main(String[] args) {
    Trainer trainer = new Trainer();
    trainer.load(song());
    ok(
        trainer.arrangement.parts.stream().allMatch(p -> p.instrument == -1 && p.program == 0),
        "old/unconfigured songs follow source MIDI by default");
    int[] programs = {0, 73, 24};
    for (int i = 0; i < 3; i++) trainer.arrangement.parts.get(i).instrument = programs[i];
    String signature = trainer.arrangement.learningSignature();
    Trace trace = new Trace();
    SongPlayback player = new SongPlayback(trainer, trace);
    player.sync(1, true, true);
    int[] routes = new int[3];
    for (int i = 0; i < 3; i++) {
      routes[i] = player.routeOf(trainer.song.notes.get(i));
      ok(trace.program(routes[i]) == programs[i], "independent program sent for part " + i);
      ok(
          trace.has(routes[i], 0x90, 60, 90 + i * 5),
          "same-pitch notes use their own instrument voice " + i);
    }
    ok(
        routes[0] != routes[1] && routes[1] != routes[2] && routes[0] != routes[2],
        "shared original channel does not collapse piano/flute/guitar");
    player.sync(1.8, true, true);
    for (int i = 0; i < 3; i++) {
      ok(
          trace.program(routes[i]) == programs[i],
          "file program/bank cannot overwrite fixed selection");
      ok(trace.has(routes[i], 0xb0, 64, 127), "source sustain reaches each isolated voice");
      ok(!trace.has(routes[i], 0xb0, 0, 5), "fixed GM selection ignores file bank changes");
    }
    player.sync(2, true, true);
    ok(
        trace.has(routes[0], 0x80, 60, 0) && !trace.has(routes[1], 0x80, 60, 0),
        "one instrument release does not stop another instrument on same pitch");
    player.sync(1, true, true);
    for (int i = 0; i < 3; i++)
      ok(trace.program(routes[i]) == programs[i], "loop rewind preserves fixed preset");
    player.stop();
    trainer.arrangement.parts.get(1).instrument = -1;
    player.sync(1.8, true, true);
    int flute = player.routeOf(trainer.song.notes.get(1));
    ok(trace.program(flute) == 80, "Follow MIDI restores dynamic source program");
    ok(
        trace.program(player.routeOf(trainer.song.notes.get(0))) == 0
            && trace.program(player.routeOf(trainer.song.notes.get(2))) == 24,
        "restoring one part to MIDI does not affect others");
    player.stop();
    trainer.arrangement.parts.get(1).instrument = 73;
    Trace external = new Trace();
    SongPlayback midi = new SongPlayback(trainer, external, true);
    midi.sync(1, true, true);
    Set<Integer> externalRoutes = new HashSet<>();
    for (int i = 0; i < 3; i++) {
      int route = midi.routeOf(trainer.song.notes.get(i));
      externalRoutes.add(route);
      ok(
          route >= 0 && route < 16 && route != 9 && external.program(route) == programs[i],
          "MIDI OUT remaps fixed parts to separate melodic channels");
    }
    ok(
        externalRoutes.size() == 3,
        "MIDI output does not collapse three source-channel-1 instruments");
    midi.stop();
    Arrangement copy = new Arrangement(trainer.song);
    copy.copyFrom(trainer.arrangement);
    ok(
        copy.parts.get(1).instrument == 73 && copy.parts.get(2).instrument == 24,
        "snapshot copies fixed instruments");
    trainer.arrangement.parts.get(1).instrument = 0;
    Trace samePreset = new Trace();
    SongPlayback separate = new SongPlayback(trainer, samePreset);
    separate.sync(1, true, true);
    ok(
        separate.routeOf(trainer.song.notes.get(0)) != separate.routeOf(trainer.song.notes.get(1)),
        "two fixed parts retain independent note/level voices even on the same preset");
    separate.stop();
    for (int program = 0; program < 128; program++) {
      trainer.arrangement.parts.get(1).instrument = program;
      Trace boundary = new Trace();
      SongPlayback single = new SongPlayback(trainer, boundary);
      single.solo(16);
      single.sync(1, true, true);
      ok(
          boundary.program(single.routeOf(trainer.song.notes.get(1))) == program,
          "all 128 GM program numbers route correctly");
      single.stop();
    }
    ok(
        signature.equals(trainer.arrangement.learningSignature()),
        "instrument changes preserve scoring configuration");
    ok(
        trainer.song.messages.get(0).a == 0 && trainer.song.notes.get(1).channel == 0,
        "original song program/channel are not rewritten");
    List<MidiSong.Note> many = new ArrayList<>();
    for (int i = 0; i < 210; i++) many.add(new MidiSong.Note(60, 90, i % 16, i, 1, 3));
    Trainer large = new Trainer();
    large.load(MidiSong.of(many));
    for (Arrangement.Part part : large.arrangement.parts) part.instrument = part.track / 16;
    boolean rejected = false;
    try {
      new SongPlayback(large, (c, k, a, b) -> {}).sync(1, true, true);
    } catch (IllegalStateException expected) {
      rejected = true;
    }
    ok(rejected, "route capacity is reported rather than silently mixing different presets");
    System.out.println(
        "PASS instrument overrides: "
            + checks
            + " checks (128 GM choices, shared channels, sustain, seek, independent note-off, MIDI"
            + " OUT, data preservation)");
  }
}

package id.arus.piano;

import java.util.*;

/** Event playback follows the trainer's song clock, including waits, speed and loop rewinds. */
public final class SongPlayback {
  public interface Sink {
    void send(int channel, int kind, int a, int b);
  }

  private static final class Event {
    final double time;
    final MidiSong.Note note;
    final MidiSong.Message message;
    final boolean on;

    Event(MidiSong.Note n, boolean on) {
      note = n;
      this.on = on;
      message = null;
      time = on ? n.start : n.end;
    }

    Event(MidiSong.Message m) {
      message = m;
      time = m.time;
      note = null;
      on = false;
    }

    int priority() {
      return message != null ? 1 : on ? 2 : 0;
    }
  }

  private final Trainer trainer;
  private final Sink sink;
  private final ArrayList<Event> events = new ArrayList<>();
  private final int[][] held;
  private final boolean[] used;
  private final int[] sourceChannel, fixedProgram;
  private final boolean externalMidi;
  private final Map<Integer, Integer> routes = new HashMap<>();
  private boolean running = false, listening;
  private int cursor, solo = -1, lastSolo = -1;
  private double last = Double.NaN;

  public SongPlayback(Trainer trainer, Sink sink) {
    this(trainer, sink, false);
  }

  public SongPlayback(Trainer trainer, Sink sink, boolean externalMidi) {
    this.externalMidi = externalMidi;
    int capacity = externalMidi ? 16 : 224;
    held = new int[capacity][128];
    used = new boolean[capacity];
    sourceChannel = new int[capacity];
    fixedProgram = new int[capacity];
    this.trainer = trainer;
    this.sink = sink;
    for (MidiSong.Note n : trainer.song.notes) {
      if (n.end <= n.start) continue;
      events.add(new Event(n, true));
      events.add(new Event(n, false));
    }
    for (MidiSong.Message m : trainer.song.messages) events.add(new Event(m));
    events.sort(Comparator.comparingDouble((Event e) -> e.time).thenComparingInt(Event::priority));
  }

  public void solo(int key) {
    solo = key;
  }

  private boolean audible(MidiSong.Note n) {
    return solo >= 0 ? Arrangement.key(n) == solo : trainer.audible(n, listening);
  }

  private int velocity(MidiSong.Note n) {
    if (solo >= 0) {
      Arrangement.Part p = trainer.arrangement.part(n);
      return Math.round(n.velocity * (p == null ? 1 : p.volume / 100f));
    }
    return trainer.arrangement.velocity(n);
  }

  private void controllersOff() {
    for (int ch = 0; ch < used.length; ch++) {
      if (used[ch]) {
        sink.send(ch, 0xb0, 64, 0);
        sink.send(ch, 0xb0, 120, 0);
      }
      Arrays.fill(held[ch], 0);
    }
  }

  public void stop() {
    if (running) controllersOff();
    running = false;
    last = Double.NaN;
  }

  private void on(MidiSong.Note n) {
    if (!audible(n) || velocity(n) <= 0) return;
    int channel = routeOf(n);
    if (held[channel][n.pitch]++ == 0) sink.send(channel, 0x90, n.pitch, velocity(n));
  }

  private void off(MidiSong.Note n) {
    if (!audible(n) || velocity(n) <= 0) return;
    int channel = routeOf(n);
    if (held[channel][n.pitch] > 0 && --held[channel][n.pitch] == 0)
      sink.send(channel, 0x80, n.pitch, 0);
  }

  private void reset(double time) {
    controllersOff();
    buildRoutes();
    for (int ch = 0; ch < used.length; ch++) {
      if (!used[ch]) continue;
      sink.send(ch, 0xb0, 121, 0);
      sink.send(ch, 0xb0, 0, 0);
      sink.send(ch, 0xb0, 32, 0);
      sink.send(ch, 0xc0, Math.max(0, fixedProgram[ch]), 0);
      sink.send(ch, 0xe0, 0, 64);
    }
    // Rebuild controllers without sounding every historical note on a seek/resume.
    cursor = 0;
    while (cursor < events.size() && events.get(cursor).time <= time + .000001) {
      Event e = events.get(cursor++);
      if (e.message != null) message(e.message);
    }
    for (MidiSong.Note n : trainer.song.notes) {
      if (n.start > time + .000001) break;
      if (n.end > time + .000001) on(n);
    }
  }

  private void message(MidiSong.Message m) {
    int kind = m.status & 240;
    for (int channel = 0; channel < used.length; channel++) {
      if (!used[channel] || sourceChannel[channel] != (m.status & 15)) continue;
      // Fixed GM presets are isolated from file Program Change and bank messages.
      if (fixedProgram[channel] >= 0 && (kind == 0xc0 || (kind == 0xb0 && (m.a == 0 || m.a == 32))))
        continue;
      sink.send(channel, kind, m.a, m.b);
    }
  }

  public int routeOf(MidiSong.Note note) {
    return routes.getOrDefault(Arrangement.key(note), note.channel);
  }

  private void buildRoutes() {
    Arrays.fill(used, false);
    Arrays.fill(fixedProgram, -1);
    routes.clear();
    Set<Integer> active = new TreeSet<>();
    for (MidiSong.Note note : trainer.song.notes)
      if (audible(note) && velocity(note) > 0) active.add(Arrangement.key(note));
    // Unmodified parts retain the original channel and file controller semantics.
    for (int key : active) {
      Arrangement.Part part = trainer.arrangement.get(key);
      if (part.instrument < 0) {
        routes.put(key, part.channel);
        used[part.channel] = true;
        sourceChannel[part.channel] = part.channel;
      }
    }
    Map<Integer, Integer> groups = new HashMap<>();
    for (int key : active) {
      Arrangement.Part part = trainer.arrangement.get(key);
      if (part.instrument < 0) continue;
      int group = part.key;
      Integer channel = groups.get(group);
      if (channel == null) {
        channel = externalMidi ? 1 : 16;
        while (channel < used.length && (used[channel] || (externalMidi && channel == 9)))
          channel++;
        if (channel >= used.length)
          throw new IllegalStateException(
              externalMidi
                  ? "Not enough melodic MIDI OUT channels. Use tablet audio or reduce active"
                      + " instrument groups."
                  : "Too many independent instrument groups for the 224-voice-route audio engine.");
        groups.put(group, channel);
        used[channel] = true;
        sourceChannel[channel] = part.channel;
        fixedProgram[channel] = part.instrument;
      }
      routes.put(key, channel);
    }
  }

  public void sync(double time, boolean playing, boolean listen) {
    if (!playing) {
      stop();
      return;
    }
    boolean reset = !running || listen != listening || solo != lastSolo || time < last - .000001;
    listening = listen;
    lastSolo = solo;
    if (reset) {
      running = true;
      reset(time);
    } else
      while (cursor < events.size() && events.get(cursor).time <= time + .000001) {
        Event e = events.get(cursor++);
        if (e.message != null) message(e.message);
        else if (e.on) on(e.note);
        else off(e.note);
      }
    last = time;
  }
}

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
  private final int[][] held = new int[16][128];
  private final boolean[] used = new boolean[16];
  private boolean running = false, listening;
  private int cursor, solo = -1, lastSolo = -1;
  private double last = Double.NaN;

  public SongPlayback(Trainer trainer, Sink sink) {
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
    for (int ch = 0; ch < 16; ch++) {
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
    if (held[n.channel][n.pitch]++ == 0) sink.send(n.channel, 0x90, n.pitch, velocity(n));
  }

  private void off(MidiSong.Note n) {
    if (!audible(n) || velocity(n) <= 0) return;
    if (held[n.channel][n.pitch] > 0 && --held[n.channel][n.pitch] == 0)
      sink.send(n.channel, 0x80, n.pitch, 0);
  }

  private void reset(double time) {
    controllersOff();
    Arrays.fill(used, false);
    for (MidiSong.Note note : trainer.song.notes)
      if (audible(note) && velocity(note) > 0) used[note.channel] = true;
    for (int ch = 0; ch < 16; ch++) {
      if (!used[ch]) continue;
      sink.send(ch, 0xb0, 121, 0);
      sink.send(ch, 0xb0, 0, 0);
      sink.send(ch, 0xb0, 32, 0);
      sink.send(ch, 0xc0, 0, 0);
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
    // MIDI program/pedal/bank/pitch state belongs to a channel, including conductor tracks.
    if (used[m.status & 15]) sink.send(m.status & 15, m.status & 240, m.a, m.b);
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

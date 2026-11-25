package id.arus.piano;

import android.os.*;
import java.util.*;

/** Exact note/control event scheduling for converter MIDI preview. UI thread only. */
final class SimplePlayback {
  private static final class E {
    double time;
    int status, a, b;

    E(double t, int s, int a, int b) {
      time = t;
      status = s;
      this.a = a;
      this.b = b;
    }
  }

  private final OfflineSynth synth;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final List<E> events = new ArrayList<>();
  private int cursor;
  private long start;
  private boolean playing;

  SimplePlayback(OfflineSynth synth) {
    this.synth = synth;
  }

  void play(MidiSong song) {
    stop();
    events.clear();
    for (MidiSong.Note n : song.notes) {
      events.add(new E(n.start, 0x90, n.pitch, n.velocity));
      events.add(new E(n.end, 0x80, n.pitch, 0));
    }
    for (MidiSong.Message m : song.messages) events.add(new E(m.time, m.status & 240, m.a, m.b));
    events.sort(Comparator.comparingDouble(e -> e.time));
    cursor = 0;
    start = SystemClock.elapsedRealtime();
    playing = true;
    handler.post(tick);
  }

  private final Runnable tick =
      new Runnable() {
        public void run() {
          if (!playing) return;
          double time = (SystemClock.elapsedRealtime() - start) / 1000.0;
          while (cursor < events.size() && events.get(cursor).time <= time) {
            E e = events.get(cursor++);
            synth.midi(16, e.status, e.a, e.b);
          }
          if (cursor < events.size()) handler.postDelayed(this, 5);
          else playing = false;
        }
      };

  void stop() {
    playing = false;
    handler.removeCallbacks(tick);
    synth.silence();
  }
}

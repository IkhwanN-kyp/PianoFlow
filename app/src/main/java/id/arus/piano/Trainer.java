package id.arus.piano;

import java.util.*;

/** Clock, chord gating and scoring. All calls belong to the UI thread. */
public final class Trainer {
  public MidiSong song = MidiSong.exercise();
  public double time = 0, speed = 1, loopA = 0, loopB = 0;
  public boolean playing = false, wait = true, listen = false, loop = false;
  public int hand = 0, hits = 0, misses = 0, wrong = 0;
  // Track role: 0=pitch split C4, 1=left, 2=right, 3=excluded.
  public final Map<Integer, Integer> roles = new HashMap<>();
  public byte[] judged = new byte[song.notes.size()];
  private int cursor = 0;

  public int role(MidiSong.Note n) {
    int r = roles.getOrDefault(n.track, 0);
    return r == 0 ? (n.pitch < 60 ? 1 : 2) : r;
  }

  public boolean selected(MidiSong.Note n) {
    int r = role(n);
    return n.pitch >= 21 && n.pitch <= 108 && r != 3 && (hand == 0 || hand == r);
  }

  public void load(MidiSong s) {
    song = s;
    roles.clear();
    loop = false;
    loopA = 0;
    loopB = s.duration;
    seek(0);
    playing = false;
  }

  public int lower(double t) {
    int l = 0, h = song.notes.size();
    while (l < h) {
      int m = (l + h) >>> 1;
      if (song.notes.get(m).start < t) l = m + 1;
      else h = m;
    }
    return l;
  }

  public void seek(double t) {
    time = Math.max(0, Math.min(t, song.duration));
    judged = new byte[song.notes.size()];
    cursor = lower(time - 0.001);
    hits = misses = wrong = 0;
    for (int i = 0; i < cursor; i++) judged[i] = 3;
  }

  public void update(double dt) {
    if (!playing) return;
    double target = time + Math.max(0, dt) * speed;
    if (!listen && wait) {
      while (cursor < judged.length && (judged[cursor] != 0 || !selected(song.notes.get(cursor))))
        cursor++;
      if (cursor < judged.length) {
        double onset = song.notes.get(cursor).start;
        if (onset <= target) target = Math.max(time, onset);
      }
    } else if (!listen) {
      while (cursor < judged.length && song.notes.get(cursor).start + .18 < target) {
        if (judged[cursor] == 0 && selected(song.notes.get(cursor))) {
          judged[cursor] = 2;
          misses++;
        }
        cursor++;
      }
    }
    if (loop && loopB > loopA + .05 && target >= loopB) {
      seek(loopA);
      return;
    }
    time = Math.min(target, song.duration + .4);
    if (time >= song.duration + .4) playing = false;
  }

  public boolean press(int pitch) {
    if (!playing || listen) return false;
    int best = -1;
    double distance = .181;
    for (int i = lower(time - .181); i < judged.length; i++) {
      MidiSong.Note n = song.notes.get(i);
      if (n.start > time + .18) break;
      if (n.pitch == pitch && judged[i] == 0 && selected(n)) {
        double d = Math.abs(n.start - time);
        if (d < distance) {
          distance = d;
          best = i;
        }
      }
    }
    if (best >= 0) {
      judged[best] = 1;
      hits++;
      return true;
    }
    wrong++;
    return false;
  }

  public int accuracy() {
    int total = hits + wrong + misses;
    return total == 0 ? 100 : Math.round(hits * 100f / total);
  }

  public int next() {
    for (int i = cursor; i < judged.length; i++)
      if (judged[i] == 0 && selected(song.notes.get(i))) return i;
    return -1;
  }
}

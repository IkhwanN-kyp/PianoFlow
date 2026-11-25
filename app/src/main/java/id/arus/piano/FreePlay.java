package id.arus.piano;

import java.util.*;

/** UI-thread live note trails. The caller supplies a monotonic clock in seconds. */
public final class FreePlay {
  public static final double WINDOW = 5.0;
  public static final int LIMIT = 512;

  public static final class Note {
    public final int pitch, velocity;
    public final double start;
    public double end = Double.POSITIVE_INFINITY;

    private Note(int pitch, int velocity, double start) {
      this.pitch = pitch;
      this.velocity = velocity;
      this.start = start;
    }

    public boolean held() {
      return Double.isInfinite(end);
    }

    public double age(double now) {
      return Math.max(0, now - start);
    }

    public double releaseAge(double now) {
      return held() ? 0 : Math.max(0, now - end);
    }
  }

  private final ArrayList<Note> notes = new ArrayList<>();
  private final List<Note> view = Collections.unmodifiableList(notes);
  private final Note[] held = new Note[128];

  public List<Note> notes() {
    return view;
  }

  public void press(int pitch, int velocity, double now) {
    if (pitch < 21 || pitch > 108 || velocity <= 0 || !Double.isFinite(now)) return;
    release(pitch, now);
    prune(now);
    if (notes.size() >= LIMIT) {
      // Prefer discarding an already released trail; preserve held keys when possible.
      int remove = 0;
      while (remove < notes.size() && notes.get(remove).held()) remove++;
      Note old = notes.remove(remove == notes.size() ? 0 : remove);
      if (held[old.pitch] == old) held[old.pitch] = null;
    }
    Note note = new Note(pitch, Math.min(127, velocity), now);
    notes.add(note);
    held[pitch] = note;
  }

  public void release(int pitch, double now) {
    if (pitch < 0 || pitch > 127 || !Double.isFinite(now)) return;
    Note note = held[pitch];
    if (note != null) {
      note.end = Math.max(note.start, now);
      held[pitch] = null;
    }
  }

  public void prune(double now) {
    notes.removeIf(n -> !n.held() && n.releaseAge(now) > WINDOW + .1);
  }

  public boolean moving(double now) {
    prune(now);
    return !notes.isEmpty();
  }

  public void clear() {
    notes.clear();
    Arrays.fill(held, null);
  }
}

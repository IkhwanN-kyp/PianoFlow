package id.arus.piano;

import java.io.*;
import java.util.*;

/** Strict SMF 0/1 parser, independent of Android. Times are song seconds. */
public final class MidiSong {
  public static final class Note {
    public final int pitch, velocity, channel, track;
    public final double start, end, beat, endBeat;

    public Note(int p, int v, int c, int t, double s, double e) {
      this(p, v, c, t, s, e, s * 2, e * 2);
    }

    public Note(int p, int v, int c, int t, double s, double e, double beat, double endBeat) {
      this.beat = beat;
      this.endBeat = endBeat;
      pitch = p;
      velocity = v;
      channel = c;
      track = t;
      start = s;
      end = e;
    }
  }

  public static final class Tempo {
    public final double time, beat;
    public final int micros;

    public Tempo(double time, double beat, int micros) {
      this.time = time;
      this.beat = beat;
      this.micros = micros;
    }
  }

  public static final class Meter {
    public final double beat;
    public final int numerator, denominator;

    public Meter(double beat, int numerator, int denominator) {
      this.beat = beat;
      this.numerator = numerator;
      this.denominator = denominator;
    }
  }

  public static final class Message {
    public final double time;
    public final int status, a, b, track;

    public Message(double time, int status, int a, int b) {
      this(time, status, a, b, -1);
    }

    public Message(double time, int status, int a, int b, int track) {
      this.track = track;
      this.time = time;
      this.status = status;
      this.a = a;
      this.b = b;
    }
  }

  public final List<Tempo> tempos;
  public final List<Meter> meters;
  public final List<Message> messages;
  public final List<Note> notes;
  public final Map<Integer, String> tracks;
  public final double duration;
  public final int percussionSkipped;

  private MidiSong(List<Note> n, Map<Integer, String> t, int skipped) {
    this(
        n,
        t,
        skipped,
        Collections.singletonList(new Tempo(0, 0, 500000)),
        Collections.singletonList(new Meter(0, 4, 4)),
        Collections.emptyList());
  }

  public MidiSong(
      List<Note> n,
      Map<Integer, String> t,
      int skipped,
      List<Tempo> tempos,
      List<Meter> meters,
      List<Message> messages) {
    this.tempos = Collections.unmodifiableList(new ArrayList<>(tempos));
    this.meters = Collections.unmodifiableList(new ArrayList<>(meters));
    this.messages = Collections.unmodifiableList(new ArrayList<>(messages));
    n = new ArrayList<>(n);
    n.sort(Comparator.comparingDouble(a -> a.start));
    notes = Collections.unmodifiableList(n);
    tracks = t;
    double end = 0;
    for (Note x : n) end = Math.max(end, x.end);
    for (Message m : messages) end = Math.max(end, m.time);
    duration = end;
    percussionSkipped = skipped;
  }

  public static MidiSong of(List<Note> notes) {
    Map<Integer, String> tracks = new TreeMap<>();
    for (Note n : notes) tracks.put(n.track, "Track " + (n.track + 1));
    return new MidiSong(notes, tracks, 0);
  }

  public double beatAt(double time) {
    Tempo last = tempos.get(0);
    for (Tempo t : tempos) {
      if (t.time > time) break;
      last = t;
    }
    return last.beat + (time - last.time) * 1000000.0 / last.micros;
  }

  public double secondsAt(double beat) {
    Tempo last = tempos.get(0);
    for (Tempo t : tempos) {
      if (t.beat > beat) break;
      last = t;
    }
    return last.time + (beat - last.beat) * last.micros / 1000000.0;
  }

  private static final class Event {
    long tick;
    int kind, a, b, ch, track, order;

    Event(long t, int k, int x, int y, int c, int tr, int o) {
      tick = t;
      kind = k;
      a = x;
      b = y;
      ch = c;
      track = tr;
      order = o;
    }
  }

  private static final class Reader {
    final byte[] d;
    int p, limit;

    Reader(byte[] d) {
      this.d = d;
      limit = d.length;
    }

    int u() throws IOException {
      if (p >= limit) throw new IOException("MIDI terpotong");
      return d[p++] & 255;
    }

    int u16() throws IOException {
      return (u() << 8) | u();
    }

    long u32() throws IOException {
      return ((long) u() << 24) | ((long) u() << 16) | (u() << 8) | u();
    }

    int vlq() throws IOException {
      int v = 0;
      for (int i = 0; i < 4; i++) {
        int b = u();
        v = (v << 7) | (b & 127);
        if (b < 128) return v;
      }
      throw new IOException("VLQ lebih dari 4 byte");
    }

    void skip(long n) throws IOException {
      if (n < 0 || n > limit - p) throw new IOException("Panjang chunk MIDI tidak valid");
      p += (int) n;
    }

    boolean tag(String s) throws IOException {
      for (int i = 0; i < 4; i++) if (u() != s.charAt(i)) return false;
      return true;
    }

    int data() throws IOException {
      int b = u();
      if (b > 127) throw new IOException("Data MIDI tidak valid");
      return b;
    }
  }

  public static MidiSong read(InputStream input) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] buf = new byte[8192];
    int len;
    // Resource guard, not an import count or duration quota.
    while ((len = input.read(buf)) != -1) {
      if (out.size() + len > 64 * 1024 * 1024)
        throw new IOException("Berkas melebihi batas memori aman 64 MiB");
      out.write(buf, 0, len);
    }
    Reader r = new Reader(out.toByteArray());
    if (!r.tag("MThd")) throw new IOException("Bukan Standard MIDI File");
    long hl = r.u32();
    if (hl < 6) throw new IOException("Header MIDI tidak valid");
    int format = r.u16(), nt = r.u16(), division = r.u16();
    r.skip(hl - 6);
    if (format > 1) throw new IOException("MIDI format 2 belum didukung; gunakan format 0/1");
    if (nt == 0 || (format == 0 && nt != 1) || division == 0)
      throw new IOException("Header MIDI tidak valid");
    double fixed = 0;
    if ((division & 0x8000) != 0) {
      int fps = 256 - (division >> 8), sub = division & 255;
      if (sub == 0 || (fps != 24 && fps != 25 && fps != 29 && fps != 30))
        throw new IOException("SMPTE tidak valid");
      fixed = 1.0 / ((fps == 29 ? 30000.0 / 1001 : fps) * sub);
    }
    List<Event> events = new ArrayList<>();
    Map<Integer, String> tracks = new TreeMap<>();
    int order = 0;
    for (int tr = 0; tr < nt; tr++) {
      if (!r.tag("MTrk")) throw new IOException("Chunk MTrk tidak ditemukan");
      long size = r.u32();
      if (size > r.limit - r.p) throw new IOException("Track terpotong");
      int fullLimit = r.limit, end = r.p + (int) size;
      r.limit = end;
      long tick = 0;
      int running = 0;
      tracks.put(tr, "Track " + (tr + 1));
      while (r.p < end) {
        if (events.size() >= 500000)
          throw new IOException("MIDI terlalu padat untuk batas memori aman (500.000 event)");
        tick += r.vlq();
        int s = r.u(), first = -1;
        if (s < 128) {
          if (running == 0) throw new IOException("Running status tanpa status");
          first = s;
          s = running;
        }
        if (s == 255) {
          running = 0;
          int type = r.u(), n = r.vlq();
          if (type == 0x51) {
            if (n != 3) throw new IOException("Tempo tidak valid");
            int tempo = (r.u() << 16) | (r.u() << 8) | r.u();
            if (tempo == 0) throw new IOException("Tempo nol");
            events.add(new Event(tick, 0x51, tempo, 0, 0, tr, order++));
          } else if (type == 0x58) {
            if (n != 4) throw new IOException("Time signature invalid");
            int numerator = r.u(), power = r.u();
            r.skip(2);
            if (numerator == 0 || power > 7) throw new IOException("Time signature invalid");
            events.add(new Event(tick, 0x58, numerator, 1 << power, 0, tr, order++));
          } else if (type == 3) {
            if (n > r.limit - r.p) throw new IOException("Nama track terpotong");
            tracks.put(tr, new String(r.d, r.p, n, "UTF-8"));
            r.skip(n);
          } else r.skip(n);
          if (type == 0x2f) {
            if (n != 0) throw new IOException("End-of-track tidak valid");
            break;
          }
        } else if (s == 0xf0 || s == 0xf7) {
          running = 0;
          r.skip(r.vlq());
        } else if (s >= 0x80 && s <= 0xef) {
          running = s;
          int a = first >= 0 ? first : r.data(),
              k = s & 240,
              b = (k == 0xc0 || k == 0xd0) ? 0 : r.data();
          events.add(new Event(tick, k, a, b, s & 15, tr, order++));
        } else throw new IOException("Status SMF tidak didukung");
      }
      events.add(new Event(tick, 0x2f, 0, 0, 0, tr, order++));
      r.p = end;
      r.limit = fullLimit;
    }
    events.sort(Comparator.comparingLong((Event e) -> e.tick).thenComparingInt(e -> e.order));
    Map<Integer, ArrayDeque<Event>> active = new HashMap<>();
    Map<Event, Double> starts = new IdentityHashMap<>();
    List<Note> notes = new ArrayList<>();
    List<Tempo> tempos = new ArrayList<>();
    tempos.add(new Tempo(0, 0, 500000));
    List<Meter> meters = new ArrayList<>();
    meters.add(new Meter(0, 4, 4));
    List<Message> messages = new ArrayList<>();
    long lastTick = 0;
    double time = 0;
    int tempo = 500000, skipped = 0;
    for (Event e : events) {
      time += (e.tick - lastTick) * (fixed > 0 ? fixed : tempo / (division * 1000000.0));
      lastTick = e.tick;
      double beat = fixed > 0 ? time * 2 : e.tick / (double) division;
      if (e.kind == 0x58) {
        meters.add(new Meter(beat, e.a, e.b));
        continue;
      }
      if (e.kind == 0xb0 || e.kind == 0xc0 || e.kind == 0xe0)
        messages.add(new Message(time, e.kind | e.ch, e.a, e.b, e.track));
      if (e.kind == 0x51) {
        if (fixed == 0) tempos.add(new Tempo(time, beat, e.a));
        tempo = e.a;
        continue;
      }
      if (e.kind == 0x2f) {
        for (ArrayDeque<Event> q : active.values())
          for (Iterator<Event> it = q.iterator(); it.hasNext(); ) {
            Event on = it.next();
            if (on.track == e.track) {
              double start = starts.remove(on);
              notes.add(
                  new Note(
                      on.a,
                      on.b,
                      on.ch,
                      on.track,
                      start,
                      Math.max(start + .001, time),
                      fixed > 0 ? start * 2 : on.tick / (double) division,
                      beat));
              it.remove();
            }
          }
        continue;
      }
      if (e.ch == 9 && (e.kind == 0x90 || e.kind == 0x80)) {
        if (e.kind == 0x90 && e.b > 0) skipped++;
        continue;
      }
      int key = (e.track << 12) | (e.ch << 8) | e.a;
      if (e.kind == 0x90 && e.b > 0) {
        active.computeIfAbsent(key, k -> new ArrayDeque<>()).add(e);
        starts.put(e, time);
      } else if (e.kind == 0x80 || (e.kind == 0x90 && e.b == 0)) {
        ArrayDeque<Event> q = active.get(key);
        if (q != null && !q.isEmpty()) {
          Event on = q.remove();
          double start = starts.remove(on);
          notes.add(
              new Note(
                  on.a,
                  on.b,
                  on.ch,
                  on.track,
                  start,
                  time,
                  fixed > 0 ? start * 2 : on.tick / (double) division,
                  beat));
        }
      }
    }
    for (ArrayDeque<Event> q : active.values())
      for (Event on : q) {
        double s = starts.get(on);
        notes.add(new Note(on.a, on.b, on.ch, on.track, s, Math.max(s + 0.1, time)));
      }
    if (notes.isEmpty()) throw new IOException("Tidak ada not melodis dalam MIDI");
    return new MidiSong(notes, tracks, skipped, tempos, meters, messages);
  }

  /** Original generated exercise; not a bundled song. */
  public static MidiSong exercise() {
    List<Note> n = new ArrayList<>();
    int[] scale = {60, 62, 64, 65, 67, 69, 71, 72, 71, 69, 67, 65, 64, 62, 60};
    for (int i = 0; i < scale.length; i++) {
      n.add(new Note(scale[i], 85, 0, 1, 2 + i * .6, 2 + i * .6 + .45));
      if (i % 2 == 0)
        n.add(new Note(48 + (i % 4 == 0 ? 0 : 7), 65, 1, 0, 2 + i * .6, 2 + i * .6 + .9));
    }
    Map<Integer, String> t = new TreeMap<>();
    t.put(0, "Latihan bas");
    t.put(1, "Latihan tangga nada");
    return new MidiSong(n, t, 0);
  }
}

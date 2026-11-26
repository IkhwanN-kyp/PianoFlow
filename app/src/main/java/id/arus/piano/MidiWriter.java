package id.arus.piano;

import java.io.*;
import java.util.*;

/** SMF 1 writer preserving seconds/beat tempo mapping, meter and control messages. */
public final class MidiWriter {
  private static final class Ev {
    long tick;
    byte[] data;

    Ev(long t, int... bytes) {
      tick = t;
      data = new byte[bytes.length];
      for (int i = 0; i < bytes.length; i++) data[i] = (byte) bytes[i];
    }
  }

  private static long tick(double beat) {
    return Math.max(0, Math.round(beat * 480));
  }

  private static void vlq(OutputStream out, long n) throws IOException {
    if (n > 0xfffffffL) throw new IOException("MIDI delta too large");
    int v = (int) n & 127;
    while ((n >>= 7) > 0) {
      v <<= 8;
      v |= ((int) n & 127) | 128;
    }
    while (true) {
      out.write(v & 255);
      if ((v & 128) == 0) break;
      v >>>= 8;
    }
  }

  private static void chunk(DataOutputStream out, List<Ev> events) throws IOException {
    events.sort(
        (a, b) -> {
          int c = Long.compare(a.tick, b.tick);
          if (c != 0) return c;
          return Integer.compare(
              (a.data[0] & 0xf0) == 0x80 ? 0 : 1, (b.data[0] & 0xf0) == 0x80 ? 0 : 1);
        });
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    long last = 0;
    for (Ev e : events) {
      vlq(bytes, e.tick - last);
      bytes.write(e.data);
      last = e.tick;
    }
    bytes.write(new byte[] {0, (byte) 255, 47, 0});
    out.writeBytes("MTrk");
    out.writeInt(bytes.size());
    bytes.writeTo(out);
  }

  public static void write(MidiSong song, OutputStream output) throws IOException {
    DataOutputStream out = new DataOutputStream(output);
    Map<Integer, List<Ev>> tracks = new TreeMap<>();
    for (MidiSong.Note n : song.notes) {
      List<Ev> list = tracks.computeIfAbsent(n.track, k -> new ArrayList<>());
      list.add(new Ev(tick(n.beat), 0x90 | n.channel, n.pitch, n.velocity));
      list.add(new Ev(Math.max(tick(n.beat) + 1, tick(n.endBeat)), 0x80 | n.channel, n.pitch, 0));
    }
    List<Ev> meta = new ArrayList<>();
    TreeMap<Double, MidiSong.Tempo> tempoMap = new TreeMap<>();
    for (MidiSong.Tempo t : song.tempos) tempoMap.put(t.beat, t);
    TreeMap<Double, MidiSong.Meter> meterMap = new TreeMap<>();
    for (MidiSong.Meter m : song.meters) meterMap.put(m.beat, m);
    for (MidiSong.Tempo t : tempoMap.values())
      meta.add(
          new Ev(tick(t.beat), 255, 81, 3, t.micros >> 16, (t.micros >> 8) & 255, t.micros & 255));
    for (MidiSong.Meter m : meterMap.values()) {
      int power = 0;
      while ((1 << power) < m.denominator) power++;
      meta.add(new Ev(tick(m.beat), 255, 88, 4, m.numerator, power, 24, 8));
    }
    for (MidiSong.Message m : song.messages) {
      List<Ev> list = tracks.computeIfAbsent(Math.max(0, m.track), k -> new ArrayList<>());
      if ((m.status & 240) == 0xc0) list.add(new Ev(tick(song.beatAt(m.time)), m.status, m.a));
      else list.add(new Ev(tick(song.beatAt(m.time)), m.status, m.a, m.b));
    }
    int highest = 0;
    for (int id : song.tracks.keySet()) highest = Math.max(highest, id);
    for (int id : tracks.keySet()) highest = Math.max(highest, id);
    if (highest >= 65535) throw new IOException("Too many MIDI tracks");
    tracks.computeIfAbsent(0, k -> new ArrayList<>()).addAll(0, meta);
    for (int id = 0; id <= highest; id++) {
      List<Ev> list = tracks.computeIfAbsent(id, k -> new ArrayList<>());
      String name = song.tracks.getOrDefault(id, "Track " + (id + 1));
      byte[] utf8 = name.getBytes("UTF-8");
      ByteArrayOutputStream event = new ByteArrayOutputStream();
      event.write(255);
      event.write(3);
      vlq(event, utf8.length);
      event.write(utf8);
      Ev named = new Ev(0);
      named.data = event.toByteArray();
      list.add(0, named);
    }
    out.writeBytes("MThd");
    out.writeInt(6);
    out.writeShort(1);
    out.writeShort(highest + 1);
    out.writeShort(480);
    for (List<Ev> list : tracks.values()) chunk(out, list);
    out.flush();
  }
}

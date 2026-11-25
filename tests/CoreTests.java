package id.arus.piano;

import java.io.*;
import java.util.*;

/** Dependency-free regression tests on production parser/training/stream classes. */
public final class CoreTests {
  static int assertions = 0;

  static void ok(boolean b, String message) {
    assertions++;
    if (!b) throw new AssertionError(message);
  }

  static void near(double x, double y, String m) {
    ok(Math.abs(x - y) < 1e-6, m + " (" + x + " / " + y + ")");
  }

  static void vlq(ByteArrayOutputStream b, int value) {
    int v = value & 127;
    while ((value >>= 7) > 0) {
      v <<= 8;
      v |= (value & 127) | 128;
    }
    while (true) {
      b.write(v & 255);
      if ((v & 128) != 0) v >>>= 8;
      else break;
    }
  }

  static final class Track {
    final ByteArrayOutputStream b = new ByteArrayOutputStream();

    Track ev(int ticks, int... bytes) {
      vlq(b, ticks);
      for (int x : bytes) b.write(x);
      return this;
    }

    byte[] bytes() {
      return b.toByteArray();
    }
  }

  static byte[] file(int format, int division, byte[]... tracks) throws Exception {
    ByteArrayOutputStream b = new ByteArrayOutputStream();
    DataOutputStream d = new DataOutputStream(b);
    d.writeBytes("MThd");
    d.writeInt(6);
    d.writeShort(format);
    d.writeShort(tracks.length);
    d.writeShort(division);
    for (byte[] t : tracks) {
      d.writeBytes("MTrk");
      d.writeInt(t.length);
      d.write(t);
    }
    return b.toByteArray();
  }

  static MidiSong parse(byte[] bytes) throws Exception {
    return MidiSong.read(new ByteArrayInputStream(bytes));
  }

  interface Throwing {
    void run() throws Exception;
  }

  static void rejects(Throwing fn, String m) throws Exception {
    boolean failed = false;
    try {
      fn.run();
    } catch (IOException e) {
      failed = true;
    }
    ok(failed, m);
  }

  static void parser() throws Exception {
    MidiSong s =
        parse(
            file(
                0,
                480,
                new Track().ev(0, 0x90, 60, 100).ev(480, 60, 0).ev(0, 0xff, 0x2f, 0).bytes()));
    ok(s.notes.size() == 1, "running status note-on velocity zero closes note");
    near(s.notes.get(0).end, .5, "default 120 BPM");
    Track tempo =
        new Track()
            .ev(0, 0xff, 0x51, 3, 7, 0xa1, 0x20)
            .ev(480, 0xff, 0x51, 3, 15, 0x42, 0x40)
            .ev(480, 0xff, 0x2f, 0);
    Track notes = new Track().ev(0, 0x90, 60, 100).ev(960, 0x80, 60, 0).ev(0, 0xff, 0x2f, 0);
    s = parse(file(1, 480, tempo.bytes(), notes.bytes()));
    near(s.duration, 1.5, "global tempo map across format 1 tracks");
    ok(s.notes.get(0).track == 1, "preserve track identity");
    s =
        parse(
            file(
                0,
                480,
                new Track()
                    .ev(0, 0x90, 60, 90)
                    .ev(240, 60, 80)
                    .ev(240, 0x80, 60, 0)
                    .ev(240, 60, 0)
                    .ev(0, 0xff, 0x2f, 0)
                    .bytes()));
    ok(s.notes.size() == 2, "overlapping same-pitch notes survive");
    near(s.notes.get(0).end, .5, "FIFO first off");
    near(s.notes.get(1).end, .75, "FIFO second off");
    s =
        parse(
            file(
                0,
                480,
                new Track()
                    .ev(0, 0x99, 36, 100)
                    .ev(240, 0x89, 36, 0)
                    .ev(0, 0x90, 108, 100)
                    .ev(480, 0x80, 108, 0)
                    .bytes()));
    ok(s.percussionSkipped == 1 && s.notes.size() == 1, "percussion excluded");
    s = parse(file(0, 0xe728, new Track().ev(0, 0x90, 21, 100).ev(1000, 0x80, 21, 0).bytes()));
    near(s.duration, 1, "SMPTE 25 fps / 40 subframes");
    s =
        parse(
            file(
                0,
                480,
                new Track()
                    .ev(0, 0xf0, 3, 1, 2, 0xf7)
                    .ev(0, 0x90, 64, 70)
                    .ev(480, 0xff, 0x2f, 0)
                    .bytes()));
    near(s.duration, .5, "unterminated note closes at track end");
    rejects(() -> parse(new byte[] {0, 1, 2}), "bad/truncated header rejected");
    rejects(() -> parse(file(2, 480, new Track().bytes())), "format 2 rejected clearly");
    rejects(() -> parse(file(0, 0, new Track().bytes())), "zero division rejected");
    rejects(
        () -> parse(file(0, 480, new Track().ev(0, 60, 100).bytes())),
        "invalid running status rejected");
    rejects(
        () -> parse(file(0, 480, new Track().ev(0, 0x90, 60).bytes())),
        "truncated channel message rejected");
    rejects(
        () -> parse(file(0, 480, new Track().ev(0, 0xff, 0x51, 3, 0, 0, 0).bytes())),
        "zero tempo rejected");
    byte[] valid = file(0, 480, notes.bytes());
    rejects(() -> parse(Arrays.copyOf(valid, valid.length - 2)), "truncated track rejected");
    // Deterministic mutation corpus: malformed input must terminate without unchecked exceptions.
    byte[] seed =
        file(
            0,
            480,
            new Track().ev(0, 0x90, 60, 100).ev(480, 0x80, 60, 0).ev(0, 0xff, 0x2f, 0).bytes());
    Random random = new Random(145);
    for (int i = 0; i < 300; i++) {
      byte[] b = seed.clone();
      int j = random.nextInt(b.length);
      b[j] = (byte) random.nextInt(256);
      try {
        parse(b);
      } catch (IOException expected) {
      }
    }
    ok(true, "300 parser mutation cases terminate");
  }

  static MidiSong chord() throws Exception {
    return parse(
        file(
            0,
            480,
            new Track()
                .ev(480, 0x90, 60, 100)
                .ev(0, 64, 100)
                .ev(480, 0x80, 60, 0)
                .ev(0, 64, 0)
                .ev(0, 0x90, 67, 100)
                .ev(480, 0x80, 67, 0)
                .bytes()));
  }

  static void training() throws Exception {
    Trainer t = new Trainer();
    t.load(chord());
    t.playing = true;
    t.update(3);
    near(t.time, .5, "wait clamps large clock step to earliest chord");
    ok(t.press(60), "first chord note credited");
    t.update(.5);
    near(t.time, .5, "incomplete chord keeps clock stopped");
    ok(!t.press(61) && t.wrong == 1, "wrong key cannot unblock chord");
    ok(t.press(64), "second chord note credited");
    t.update(.2);
    near(t.time, .7, "completed chord advances");
    ok(t.hits == 2, "score chord once per note");
    t.seek(0);
    ok(t.hits == 0 && t.wrong == 0, "seek resets score");
    t.wait = false;
    t.update(.8);
    ok(t.misses == 2, "rhythm misses recorded once");
    t.update(.01);
    ok(t.misses == 2, "misses not repeated");
    t.seek(0);
    t.wait = true;
    t.speed = .5;
    t.update(.4);
    near(t.time, .2, "tempo ratio");
    t.seek(0);
    t.hand = 1;
    t.update(1);
    near(t.time, .5, "empty hand selection does not block (speed .5)");
    ok(t.next() == -1, "right-only file has no left targets");
    t.hand = 0;
    t.roles.put(0, 1);
    t.seek(0);
    ok(
        t.selected(t.song.notes.get(0)) && t.role(t.song.notes.get(0)) == 1,
        "manual role overrides pitch split");
    t.roles.put(0, 3);
    ok(!t.selected(t.song.notes.get(0)), "track exclusion");
    t.roles.clear();
    t.listen = true;
    t.speed = 1;
    t.seek(0);
    t.update(.9);
    near(t.time, .9, "listen bypasses waiting");
    t.listen = false;
    t.wait = true;
    t.loop = true;
    t.loopA = 0;
    t.loopB = .6;
    t.seek(0);
    t.update(2);
    near(t.time, .5, "wait gate wins over loop boundary");
    t.press(60);
    t.press(64);
    t.update(.3);
    near(t.time, 0, "loop returns to A");
    ok(t.hits == 0, "loop resets per-pass score");
    t.loop = false;
    t.seek(.5);
    t.playing = true;
    ok(t.press(60), "seek to exact onset remains playable");
  }

  static void stream() {
    List<String> events = new ArrayList<>();
    MidiStream s = new MidiStream((c, p, v, on) -> events.add(c + ":" + p + ":" + v + ":" + on));
    s.feed(new byte[] {(byte) 0x92, 60}, 0, 2);
    s.feed(new byte[] {(byte) 0xf8, 100, 64, 80, 60, 0, (byte) 0x82, 64, 1}, 0, 9);
    ok(events.size() == 4, "fragmented running status and realtime");
    ok(events.get(0).equals("2:60:100:true"), "channel/velocity preserved");
    ok(events.get(2).equals("2:60:0:false"), "zero velocity becomes off");
    s.feed(new byte[] {(byte) 0xf0, 1, 2, (byte) 0xf7, 3, 4, (byte) 0x90, 67, 80}, 0, 9);
    ok(events.size() == 5, "sysex clears running status");
    ok(
        MidiStream.usbLength(8) == 3
            && MidiStream.usbLength(9) == 3
            && MidiStream.usbLength(12) == 2
            && MidiStream.usbLength(0) == 0,
        "USB event lengths");
  }

  public static void main(String[] args) throws Exception {
    parser();
    training();
    stream();
    System.out.println(
        "PASS: " + assertions + " assertions + 300 deterministic malformed MIDI mutations");
  }
}

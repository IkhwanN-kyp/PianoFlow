package id.arus.piano;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class ExtendedTests {
  static int checks = 0;

  static void ok(boolean value, String label) {
    checks++;
    if (!value) throw new AssertionError(label);
  }

  static void near(double a, double b, String label) {
    ok(Math.abs(a - b) < .003, label + " " + a + " != " + b);
  }

  static MidiSong roundtrip(MidiSong song) throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    MidiWriter.write(song, bytes);
    return MidiSong.read(new ByteArrayInputStream(bytes.toByteArray()));
  }

  static MidiSong xml(String body) throws Exception {
    return MusicXml.read(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
  }

  public static void main(String[] args) throws Exception {
    MidiSong original = MidiSong.exercise(), copy = roundtrip(original);
    ok(copy.notes.size() == 23, "MIDI export retains original notes");
    for (int i = 0; i < copy.notes.size(); i++) {
      near(copy.notes.get(i).start, original.notes.get(i).start, "roundtrip onset " + i);
      near(copy.notes.get(i).end, original.notes.get(i).end, "roundtrip duration " + i);
    }
    List<MidiSong.Tempo> tempos =
        Arrays.asList(new MidiSong.Tempo(0, 0, 500000), new MidiSong.Tempo(1, 2, 1000000));
    List<MidiSong.Meter> meters =
        Arrays.asList(new MidiSong.Meter(0, 4, 4), new MidiSong.Meter(4, 3, 4));
    List<MidiSong.Message> messages =
        Arrays.asList(
            new MidiSong.Message(0, 0xc0, 73, 0),
            new MidiSong.Message(.4, 0xb0, 64, 127),
            new MidiSong.Message(2, 0xb0, 64, 0));
    MidiSong timing = new MidiSong(new ArrayList<>(), new TreeMap<>(), 0, tempos, meters, messages);
    near(timing.beatAt(2), 3, "seconds to beat follows tempo changes");
    near(timing.secondsAt(3), 2, "beat to seconds follows tempo changes");
    MidiSong song =
        new MidiSong(
            Arrays.asList(new MidiSong.Note(60, 72, 0, 1, .5, 2, 1, 3)),
            Collections.singletonMap(1, "Piano"),
            0,
            tempos,
            meters,
            messages);
    copy = roundtrip(song);
    near(copy.notes.get(0).end, 2, "tempo preserved on export");
    ok(copy.messages.size() == 3, "program and sustain retained");
    ok(copy.messages.get(1).a == 64 && copy.messages.get(1).b == 127, "sustain down value");
    near(copy.duration, 2, "sustain release included in song duration");
    ScoreLayout grid = new ScoreLayout(song);
    ok(grid.at(0).numerator == 4 && grid.at(0).denominator == 4, "initial meter");
    ok(grid.at(4).number == 2 && grid.at(4).numerator == 3, "meter change starts new bar");
    near(grid.next(grid.at(4)).beat, 7, "3/4 bar length");
    near(ScoreLayout.quantize(1.12), 1, "display quantization");
    ok(
        ScoreLayout.diatonic(60) == 28 && ScoreLayout.diatonic(61) == 28,
        "diatonic staff positions plus accidentals");
    String doc =
        "<score-partwise version='4.0'><part-list><score-part"
            + " id='P1'><part-name>Piano</part-name></score-part></part-list><part id='P1'><measure"
            + " number='1'><attributes><divisions>2</divisions><time><beats>3</beats><beat-type>4</beat-type></time><staves>2</staves></attributes><direction><sound"
            + " tempo='120'/></direction><note><pitch><step>C</step><octave>4</octave></pitch><duration>2</duration><staff>1</staff></note><note><chord/><pitch><step>E</step><octave>4</octave></pitch><duration>2</duration><staff>1</staff></note><backup><duration>2</duration></backup><note><pitch><step>C</step><octave>3</octave></pitch><duration>6</duration><staff>2</staff></note></measure><measure"
            + " number='2'><direction><sound"
            + " tempo='60'/></direction><note><pitch><step>D</step><alter>1</alter><octave>4</octave></pitch><duration>2</duration><staff>1</staff></note></measure></part></score-partwise>";
    MidiSong imported = xml(doc);
    ok(imported.notes.size() == 4, "MusicXML chord and bass parsed");
    ok(imported.meters.get(imported.meters.size() - 1).numerator == 3, "MusicXML time signature");
    MidiSong.Note last = imported.notes.get(3);
    ok(last.pitch == 63, "MusicXML alter");
    near(last.start, 1.5, "MusicXML measure/backup timing");
    near(last.end, 2.5, "MusicXML tempo change");
    ok(imported.notes.get(0).start == imported.notes.get(1).start, "MusicXML chord simultaneous");
    boolean rejected = false;
    try {
      xml(
          "<!DOCTYPE score-partwise [<!ENTITY xxe SYSTEM"
              + " 'file:///etc/passwd'>]><score-partwise>&xxe;</score-partwise>");
    } catch (IOException e) {
      rejected = true;
    }
    ok(rejected, "MusicXML external entities and DTD rejected");
    String[] versions = {
      "1.0.0-alpha",
      "1.0.0-alpha.1",
      "1.0.0-alpha.beta",
      "1.0.0-beta",
      "1.0.0-beta.2",
      "1.0.0-beta.11",
      "1.0.0-rc.1",
      "1.0.0",
      "1.0.1"
    };
    for (int i = 1; i < versions.length; i++)
      ok(
          new ReleaseVersion(versions[i]).compareTo(new ReleaseVersion(versions[i - 1])) > 0,
          "SemVer " + versions[i]);
    ok(
        new ReleaseVersion("v1.2.3+build.2").compareTo(new ReleaseVersion("1.2.3+build.1")) == 0,
        "build metadata does not change precedence");
    for (String invalid : new String[] {"01.2.3", "1.2", "1.2.3-beta.01", "1.2.3-", "bad"}) {
      boolean failed = false;
      try {
        new ReleaseVersion(invalid);
      } catch (IllegalArgumentException e) {
        failed = true;
      }
      ok(failed, "invalid version rejected " + invalid);
    }
    ok(GeneralMidi.NAMES.length == 128, "128 GM programs");
    ok(
        GeneralMidi.NAMES[0].equals("Acoustic Grand Piano")
            && GeneralMidi.NAMES[73].equals("Flute"),
        "GM program mapping");
    List<String> stream = new ArrayList<>();
    MidiStream decoder =
        new MidiStream(
            new MidiStream.Listener() {
              public void note(int c, int p, int v, boolean on) {}

              public void message(int status, int a, int b) {
                stream.add(status + ":" + a + ":" + b);
              }
            });
    decoder.feed(new byte[] {(byte) 0xb2, 64, 127, 64, 0, (byte) 0xc2, 73, (byte) 0xf8, 4}, 0, 9);
    ok(
        stream.equals(Arrays.asList("178:64:127", "178:64:0", "194:73:0", "194:4:0")),
        "sustain/program running status and realtime preserved");
    if (args.length > 0) {
      try (InputStream in = new FileInputStream(args[0])) {
        ok(MidiSong.read(in).notes.size() == 23, "shipped MIDI fixture parses");
      }
    }
    System.out.println(
        "PASS extended: "
            + checks
            + " checks (MIDI export, tempo/meter, MusicXML/XXE, notation, GM, MIDI CC, SemVer)");
  }
}

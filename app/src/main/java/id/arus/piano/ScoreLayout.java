package id.arus.piano;

import java.util.*;

/** Meter-aware bar grid in quarter-note beats. Quantization is display-only. */
public final class ScoreLayout {
  public static final class Bar {
    public final double beat, length;
    public final int number, numerator, denominator;

    Bar(double b, double length, int n, int num, int den) {
      beat = b;
      this.length = length;
      number = n;
      numerator = num;
      denominator = den;
    }
  }

  private final MidiSong song;

  public ScoreLayout(MidiSong s) {
    song = s;
  }

  public Bar at(double beat) {
    double position = 0;
    int number = 1;
    MidiSong.Meter meter = new MidiSong.Meter(0, 4, 4);
    for (MidiSong.Meter m : song.meters) {
      if (m.beat > beat + .0001) break;
      if (m.beat > position) {
        number +=
            (int)
                Math.ceil(
                    (m.beat - position) / (meter.numerator * 4.0 / meter.denominator) - .000001);
        position = m.beat;
      }
      meter = m;
    }
    double length = meter.numerator * 4.0 / meter.denominator;
    int count = (int) Math.floor(Math.max(0, beat - position + .00001) / length);
    return new Bar(
        position + count * length, length, number + count, meter.numerator, meter.denominator);
  }

  public Bar next(Bar bar) {
    return at(bar.beat + bar.length + .00001);
  }

  public int count() {
    double max = 0;
    for (MidiSong.Note n : song.notes) max = Math.max(max, n.endBeat);
    return at(Math.max(0, max - .00001)).number;
  }

  public static double quantize(double beat) {
    return Math.round(beat * 4) / 4.0;
  }

  public static int diatonic(int midi) {
    int[] degree = {0, 0, 1, 1, 2, 3, 3, 4, 4, 5, 5, 6};
    return (midi / 12 - 1) * 7 + degree[midi % 12];
  }
}

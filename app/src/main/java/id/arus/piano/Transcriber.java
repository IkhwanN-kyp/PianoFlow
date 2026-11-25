package id.arus.piano;

import java.io.*;
import java.nio.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.tensorflow.lite.Interpreter;

/** Basic Pitch model inference offline. Model is an optional user-imported Apache-2.0 module. */
public final class Transcriber {
  private static final int WINDOW = 43844, HOP = 36164, KEPT = 142;

  public static MidiSong basicPitch(
      AudioDecoder.Decoded decoded,
      File model,
      AtomicBoolean cancel,
      AudioDecoder.Progress progress,
      float threshold)
      throws Exception {
    List<MidiSong.Note> notes = new ArrayList<>();
    double[] starts = new double[88], last = new double[88], sum = new double[88];
    int[] count = new int[88], quiet = new int[88];
    float[] prevOnset = new float[88];
    Arrays.fill(starts, -1);
    try (Interpreter interpreter =
            new Interpreter(model, new Interpreter.Options().setNumThreads(2));
        RandomAccessFile audio = new RandomAccessFile(decoded.file, "r")) {
      int[] shape = interpreter.getInputTensor(0).shape();
      int total = 1;
      for (int x : shape) total *= x;
      if (total != WINDOW)
        throw new IOException("Not a compatible Basic Pitch model (expected 43844 samples)");
      String[] signatures = interpreter.getSignatureKeys();
      if (signatures.length == 0) throw new IOException("Model has no serving signature");
      String signature = signatures[0], inputName = interpreter.getSignatureInputs(signature)[0];
      Set<String> outputs =
          new HashSet<>(Arrays.asList(interpreter.getSignatureOutputs(signature)));
      if (!outputs.contains("note") || !outputs.contains("onset"))
        throw new IOException("Model must provide note and onset tensors");
      ByteBuffer input = ByteBuffer.allocateDirect(WINDOW * 4).order(ByteOrder.nativeOrder());
      float[][][] frame = new float[1][172][88], onset = new float[1][172][88];
      Map<String, Object> out = new HashMap<>();
      out.put("note", frame);
      out.put("onset", onset);
      Map<String, Object> in = new HashMap<>();
      in.put(inputName, input);
      long windows = (decoded.samples + HOP - 1) / HOP;
      for (long w = 0; w < windows; w++) {
        if (cancel.get() || Thread.currentThread().isInterrupted())
          throw new InterruptedIOException("Cancelled");
        input.clear();
        long first = w * HOP - 3840;
        if (first >= 0) audio.seek(first * 4);
        else audio.seek(0);
        for (int i = 0; i < WINDOW; i++) {
          long sample = first + i;
          input.putFloat(sample < 0 || sample >= decoded.samples ? 0 : audio.readFloat());
        }
        input.rewind();
        interpreter.runSignature(in, out, signature);
        for (int j = 0; j < KEPT; j++) {
          // Anchor each kept window to its original sample offset; do not accumulate frame-rounding
          // drift.
          double t = (w * HOP + j * (HOP / (double) KEPT)) / 22050.0;
          if (t >= decoded.duration()) break;
          for (int p = 0; p < 88; p++) {
            float f = frame[0][j + 15][p], o = onset[0][j + 15][p];
            boolean newOnset = o >= .5f && prevOnset[p] < .5f;
            prevOnset[p] = o;
            if (starts[p] >= 0 && newOnset && t - starts[p] > .12) {
              add(notes, p + 21, starts[p], t, sum[p], count[p]);
              starts[p] = -1;
            }
            if (starts[p] < 0 && (newOnset || f >= Math.max(.5f, threshold))) {
              starts[p] = t;
              sum[p] = 0;
              count[p] = quiet[p] = 0;
              last[p] = t;
            }
            if (starts[p] >= 0) {
              if (f >= threshold) {
                last[p] = t;
                quiet[p] = 0;
                sum[p] += f;
                count[p]++;
              } else quiet[p]++;
              if (quiet[p] >= 5) {
                add(
                    notes,
                    p + 21,
                    starts[p],
                    last[p] + HOP / (double) KEPT / 22050,
                    sum[p],
                    count[p]);
                starts[p] = -1;
              }
            }
          }
        }
        progress.report((int) ((w + 1) * 100 / windows), "infer");
      }
      for (int p = 0; p < 88; p++)
        if (starts[p] >= 0)
          add(
              notes,
              p + 21,
              starts[p],
              Math.min(decoded.duration(), last[p] + .02),
              sum[p],
              count[p]);
    }
    if (notes.isEmpty())
      throw new IOException("No notes detected. Try a clearer solo instrument or lower threshold.");
    return MidiSong.of(notes);
  }

  private static void add(
      List<MidiSong.Note> notes, int pitch, double start, double end, double strength, int count)
      throws IOException {
    if (end - start >= .09 && count > 0) {
      notes.add(
          new MidiSong.Note(
              pitch,
              Math.max(25, Math.min(127, (int) Math.round(strength / count * 127))),
              0,
              0,
              start,
              end));
      if (notes.size() > 250000) throw new IOException("Result too dense for memory");
    }
  }

  /** Lightweight autocorrelation fallback for one melody at a time; not polyphonic AI. */
  public static MidiSong melody(
      AudioDecoder.Decoded decoded, AtomicBoolean cancel, AudioDecoder.Progress progress)
      throws Exception {
    List<MidiSong.Note> notes = new ArrayList<>();
    int window = 2048, hop = 441, previous = -1;
    double start = 0;
    float[] samples = new float[window];
    try (RandomAccessFile file = new RandomAccessFile(decoded.file, "r")) {
      for (long pos = 0; pos < decoded.samples; pos += hop) {
        if (cancel.get()) throw new InterruptedIOException("Cancelled");
        file.seek(pos * 4);
        double energy = 0;
        for (int i = 0; i < window; i++) {
          samples[i] = pos + i < decoded.samples ? file.readFloat() : 0;
          energy += samples[i] * samples[i];
        }
        int pitch = -1;
        if (energy / window > .00008) {
          double best = 0;
          int lagBest = 0;
          for (int lag = 22; lag <= 800; lag++) {
            double dot = 0, a = 0, b = 0;
            for (int j = 0; j < window - lag; j += 2) {
              dot += samples[j] * samples[j + lag];
              a += samples[j] * samples[j];
              b += samples[j + lag] * samples[j + lag];
            }
            double corr = dot / Math.sqrt(a * b + 1e-15);
            if (corr > best + .004) {
              best = corr;
              lagBest = lag;
            }
            if (best > .97 && lag > lagBest + 3) break;
          }
          if (best > .75 && lagBest > 0)
            pitch = (int) Math.round(69 + 12 * Math.log(22050.0 / lagBest / 440) / Math.log(2));
          if (pitch < 21 || pitch > 108) pitch = -1;
        }
        double t = pos / 22050.0;
        if (pitch != previous) {
          if (previous >= 0 && t - start >= .08)
            notes.add(new MidiSong.Note(previous, 85, 0, 0, start, t));
          previous = pitch;
          start = t;
        }
        progress.report((int) (pos * 100 / decoded.samples), "melody");
      }
    }
    if (previous >= 0 && decoded.duration() - start >= .08)
      notes.add(new MidiSong.Note(previous, 85, 0, 0, start, decoded.duration()));
    if (notes.isEmpty()) throw new IOException("No stable melody found");
    return MidiSong.of(notes);
  }
}

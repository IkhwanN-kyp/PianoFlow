package id.arus.piano;

import android.content.*;
import android.media.*;
import android.os.Build;
import java.io.*;
import java.util.*;

/** FluidSynth SF2 engine. Render thread uses native output rate and short AudioTrack blocks. */
public final class OfflineSynth implements AutoCloseable {
  private final Thread thread;
  private volatile boolean running = true, enabled = true;
  private long handle;
  private int program = 0;
  private boolean reverb = true;
  private int polyphony = 128;
  private AudioTrack audio;
  private volatile String error;
  public final int sampleRate;
  private final float[][] previous = new float[16][128];

  public OfflineSynth(Context context) {
    AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    int rate = 48000;
    try {
      rate = Integer.parseInt(am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE));
    } catch (Exception ignored) {
    }
    sampleRate = rate;
    handle = FluidNative.create(rate, polyphony);
    if (handle == 0) throw new IllegalStateException("FluidSynth init failed");
    File bank = new File(context.getFilesDir(), "TimGM6mb.sf2");
    try {
      if (!bank.isFile())
        try (InputStream in = context.getAssets().open("soundfonts/TimGM6mb.sf2");
            OutputStream out = new FileOutputStream(bank)) {
          byte[] b = new byte[16384];
          int n;
          while ((n = in.read(b)) != -1) out.write(b, 0, n);
        }
      if (FluidNative.load(handle, bank.getPath()) < 0)
        throw new IOException("Cannot load bundled SF2");
    } catch (IOException e) {
      FluidNative.destroy(handle);
      handle = 0;
      throw new IllegalStateException(e);
    }
    program(0);
    thread = new Thread(this::render, "PianoFlowAudio");
    thread.start();
  }

  public synchronized void midi(int channel, int kind, int a, int b) {
    if (handle != 0 && enabled) FluidNative.message(handle, (kind << 8) | (channel & 63), a, b);
  }

  public void key(int note, int velocity, boolean on) {
    midi(16, on ? 0x90 : 0x80, note, on ? velocity : 0);
  }

  public synchronized void scheduled(float[] levels) {
    scheduleChannel(0, levels);
  }

  private void scheduleChannel(int channel, float[] levels) {
    for (int n = 0; n < 128; n++) {
      if (previous[channel][n] > 0 && levels[n] <= 0) midi(32 + channel, 0x80, n, 0);
      if (previous[channel][n] <= 0 && levels[n] > 0)
        midi(32 + channel, 0x90, n, Math.max(1, Math.round(levels[n] * 127)));
      previous[channel][n] = levels[n];
    }
  }

  public synchronized void scheduledChannels(float[][] levels) {
    for (int ch = 0; ch < 16; ch++) scheduleChannel(ch, levels[ch]);
  }

  public synchronized void program(int p) {
    program = Math.max(0, Math.min(127, p));
    for (int ch = 0; ch < 48; ch++)
      if (ch % 16 != 9) FluidNative.message(handle, 0xc000 | ch, program, 0);
  }

  public synchronized void configure(int poly, boolean rev) {
    polyphony = poly;
    reverb = rev;
    if (handle != 0) FluidNative.configure(handle, poly, rev);
  }

  public synchronized void enable(boolean on) {
    silence();
    enabled = on;
  }

  public synchronized boolean importBank(File bank) {
    if (handle == 0) return false;
    silence();
    boolean ok = FluidNative.load(handle, bank.getPath()) >= 0;
    if (ok) {
      program(program);
      configure(polyphony, reverb);
    }
    return ok;
  }

  public synchronized void silence() {
    for (float[] ch : previous) Arrays.fill(ch, 0);
    if (handle != 0) {
      FluidNative.panic(handle);
      program(program);
    }
  }

  public String error() {
    return error;
  }

  // Deterministic production renderer probe for runtime tests, not an audio-quality measurement.
  public synchronized long probeEnergy(int velocity) {
    silence();
    FluidNative.message(handle, 0x9010, 60, velocity);
    short[] b = new short[2048];
    FluidNative.render(handle, b, 1024);
    long sum = 0;
    for (short x : b) sum += Math.abs((int) x);
    silence();
    return sum;
  }

  private void render() {
    android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
    int block = 128;
    try {
      int min =
          AudioTrack.getMinBufferSize(
              sampleRate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT);
      AudioTrack.Builder builder =
          new AudioTrack.Builder()
              .setAudioAttributes(
                  new AudioAttributes.Builder()
                      .setUsage(AudioAttributes.USAGE_MEDIA)
                      .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                      .build())
              .setAudioFormat(
                  new AudioFormat.Builder()
                      .setSampleRate(sampleRate)
                      .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                      .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                      .build())
              .setBufferSizeInBytes(Math.max(min, block * 4 * 3))
              .setTransferMode(AudioTrack.MODE_STREAM);
      if (Build.VERSION.SDK_INT >= 26)
        builder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY);
      audio = builder.build();
      audio.play();
      short[] b = new short[block * 2];
      while (running) {
        synchronized (this) {
          if (handle == 0) break;
          FluidNative.render(handle, b, block);
        }
        int n = audio.write(b, 0, b.length, AudioTrack.WRITE_BLOCKING);
        if (n < 0) throw new IllegalStateException("AudioTrack " + n);
      }
    } catch (RuntimeException e) {
      error = e.toString();
      android.util.Log.e("PianoFlowAudio", error, e);
    } finally {
      if (audio != null) {
        audio.release();
        audio = null;
      }
    }
  }

  @Override
  public void close() {
    running = false;
    try {
      thread.join(1500);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    synchronized (this) {
      if (handle != 0) {
        FluidNative.destroy(handle);
        handle = 0;
      }
    }
  }
}

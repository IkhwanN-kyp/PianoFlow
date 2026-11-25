package id.arus.piano;

import android.content.Context;
import android.media.*;
import android.net.Uri;
import java.io.*;
import java.nio.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Offline Android codec decoding, mono mixdown and streaming linear resampling to 22,050 Hz. */
public final class AudioDecoder {
  public interface Progress {
    void report(int percent, String stage);
  }

  public static final class Decoded {
    public final File file;
    public final long samples;

    Decoded(File f, long n) {
      file = f;
      samples = n;
    }

    public double duration() {
      return samples / 22050.0;
    }
  }

  public static Decoded decode(
      Context context, Uri uri, File target, AtomicBoolean cancel, Progress progress)
      throws Exception {
    MediaExtractor extractor = new MediaExtractor();
    MediaCodec codec = null;
    long samples = 0;
    boolean complete = false;
    try {
      extractor.setDataSource(context, uri, null);
      MediaFormat format = null;
      for (int i = 0; i < extractor.getTrackCount(); i++) {
        MediaFormat f = extractor.getTrackFormat(i);
        String mime = f.getString(MediaFormat.KEY_MIME);
        if (mime != null && mime.startsWith("audio/")) {
          format = f;
          extractor.selectTrack(i);
          break;
        }
      }
      if (format == null) throw new IOException("No audio track");
      int channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT),
          rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE),
          encoding = AudioFormat.ENCODING_PCM_16BIT;
      long duration =
          format.containsKey(MediaFormat.KEY_DURATION)
              ? format.getLong(MediaFormat.KEY_DURATION)
              : 0;
      codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));
      codec.configure(format, null, null, 0);
      codec.start();
      boolean inputEnd = false, outputEnd = false;
      MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
      long frame = 0;
      double next = 0;
      float previous = 0;
      try (DataOutputStream out =
          new DataOutputStream(new BufferedOutputStream(new FileOutputStream(target), 65536))) {
        while (!outputEnd) {
          if (cancel.get() || Thread.currentThread().isInterrupted())
            throw new InterruptedIOException("Cancelled");
          if (!inputEnd) {
            int idx = codec.dequeueInputBuffer(10000);
            if (idx >= 0) {
              ByteBuffer b = codec.getInputBuffer(idx);
              int size = extractor.readSampleData(b, 0);
              if (size < 0) {
                codec.queueInputBuffer(idx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                inputEnd = true;
              } else {
                codec.queueInputBuffer(idx, 0, size, extractor.getSampleTime(), 0);
                extractor.advance();
              }
            }
          }
          int idx = codec.dequeueOutputBuffer(info, 10000);
          if (idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            MediaFormat f = codec.getOutputFormat();
            channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            encoding =
                f.containsKey(MediaFormat.KEY_PCM_ENCODING)
                    ? f.getInteger(MediaFormat.KEY_PCM_ENCODING)
                    : AudioFormat.ENCODING_PCM_16BIT;
          }
          if (idx >= 0) {
            ByteBuffer b = codec.getOutputBuffer(idx).order(ByteOrder.LITTLE_ENDIAN);
            b.position(info.offset);
            b.limit(info.offset + info.size);
            int bytes = encoding == AudioFormat.ENCODING_PCM_FLOAT ? 4 : 2;
            if (encoding != AudioFormat.ENCODING_PCM_FLOAT
                && encoding != AudioFormat.ENCODING_PCM_16BIT)
              throw new IOException("Unsupported decoded PCM encoding: " + encoding);
            while (b.remaining() >= channels * bytes) {
              float value = 0;
              for (int c = 0; c < channels; c++)
                value +=
                    encoding == AudioFormat.ENCODING_PCM_FLOAT
                        ? b.getFloat()
                        : b.getShort() / 32768f;
              value = Math.max(-1, Math.min(1, value / channels));
              if (frame == 0) {
                out.writeFloat(value);
                samples++;
                next = rate / 22050.0;
              } else
                while (next <= frame) {
                  float sample = previous + (value - previous) * (float) (next - (frame - 1));
                  out.writeFloat(sample);
                  samples++;
                  next += rate / 22050.0;
                }
              previous = value;
              frame++;
            }
            codec.releaseOutputBuffer(idx, false);
            outputEnd = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
            if (duration > 0)
              progress.report(
                  (int) Math.min(100, info.presentationTimeUs * 100 / duration), "decode");
          }
        }
      }
      if (samples == 0) throw new IOException("Empty audio");
      complete = true;
      return new Decoded(target, samples);
    } finally {
      if (codec != null) {
        try {
          codec.stop();
        } catch (Exception ignored) {
        }
        codec.release();
      }
      extractor.release();
      if (!complete) target.delete();
    }
  }
}

package id.arus.piano;

import android.media.*;
import java.io.*;
import java.nio.*;

/** Test-only encoder: generates an original M4A fixture with Android's AAC encoder. */
final class AacFixture {
  static void encode(File wav, File target) throws Exception {
    MediaCodec codec = MediaCodec.createEncoderByType("audio/mp4a-latm");
    MediaMuxer muxer =
        new MediaMuxer(target.getPath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
    boolean started = false;
    try (RandomAccessFile input = new RandomAccessFile(wav, "r")) {
      input.seek(44);
      MediaFormat format = MediaFormat.createAudioFormat("audio/mp4a-latm", 22050, 1);
      format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
      format.setInteger(MediaFormat.KEY_BIT_RATE, 64000);
      codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
      codec.start();
      boolean inputEnd = false, outputEnd = false;
      int track = -1;
      long bytes = 0;
      MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
      while (!outputEnd) {
        if (!inputEnd) {
          int idx = codec.dequeueInputBuffer(10000);
          if (idx >= 0) {
            ByteBuffer b = codec.getInputBuffer(idx);
            b.clear();
            byte[] pcm = new byte[Math.min(b.remaining(), 8192)];
            int n = input.read(pcm);
            if (n < 0) {
              codec.queueInputBuffer(
                  idx, 0, 0, bytes / 2 * 1000000 / 22050, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
              inputEnd = true;
            } else {
              b.put(pcm, 0, n);
              codec.queueInputBuffer(idx, 0, n, bytes / 2 * 1000000 / 22050, 0);
              bytes += n;
            }
          }
        }
        int idx = codec.dequeueOutputBuffer(info, 10000);
        if (idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
          track = muxer.addTrack(codec.getOutputFormat());
          muxer.start();
          started = true;
        } else if (idx >= 0) {
          ByteBuffer b = codec.getOutputBuffer(idx);
          if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && info.size > 0) {
            b.position(info.offset);
            b.limit(info.offset + info.size);
            muxer.writeSampleData(track, b, info);
          }
          outputEnd = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
          codec.releaseOutputBuffer(idx, false);
        }
      }
    } finally {
      try {
        codec.stop();
      } catch (Exception ignored) {
      }
      codec.release();
      if (started) muxer.stop();
      muxer.release();
    }
  }
}

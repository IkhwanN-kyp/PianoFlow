package id.arus.piano;

final class FluidNative {
  static {
    System.loadLibrary("pianoflow");
  }

  static native long create(int sampleRate, int polyphony);

  static native int load(long handle, String path);

  static native void message(long handle, int kindAndChannel, int a, int b);

  static native void render(long handle, short[] pcm, int frames);

  static native void configure(long handle, int polyphony, boolean reverb);

  static native void panic(long handle);

  static native int programOnChannel(long handle, int channel);

  static native void destroy(long handle);
}

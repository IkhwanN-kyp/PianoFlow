package id.arus.piano;

/** Incremental MIDI 1 byte-stream decoder, handles running status and realtime interleaving. */
public final class MidiStream {
  public interface Listener {
    void note(int channel, int pitch, int velocity, boolean on);

    default void message(int status, int a, int b) {}
  }

  private int status = 0, first = -1, need = 0;
  private boolean sysex = false;
  private final Listener listener;

  public MidiStream(Listener l) {
    listener = l;
  }

  public void feed(byte[] data, int off, int count) {
    for (int i = off; i < off + count; i++) accept(data[i] & 255);
  }

  public void accept(int b) {
    if (b >= 0xf8) return;
    if (b >= 128) {
      first = -1;
      if (b >= 0xf0) {
        status = 0;
        need = 0;
        sysex = b == 0xf0;
        return;
      }
      sysex = false;
      status = b;
      int k = b & 240;
      need = (k == 0xc0 || k == 0xd0) ? 1 : 2;
      return;
    }
    if (sysex || status == 0) return;
    if (need == 1) {
      listener.message(status, b, 0);
      return;
    }
    if (first < 0) {
      first = b;
      return;
    }
    int k = status & 240;
    if (k == 0x80 || k == 0x90) listener.note(status & 15, first, b, k == 0x90 && b > 0);
    if (k != 0x80 && k != 0x90) listener.message(status, first, b);
    first = -1;
  }

  public static int usbLength(int cin) {
    int[] sizes = {0, 0, 2, 3, 3, 1, 2, 3, 3, 3, 3, 3, 2, 2, 3, 1};
    return sizes[cin & 15];
  }
}

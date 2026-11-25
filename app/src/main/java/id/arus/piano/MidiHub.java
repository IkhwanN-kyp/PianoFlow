package id.arus.piano;

import android.app.*;
import android.content.*;
import android.hardware.usb.*;
import android.media.midi.*;
import android.os.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Android MIDI preferred; explicitly chosen raw USB-MIDI 1 fallback, cable 0. */
public final class MidiHub implements AutoCloseable {
  public interface Listener {
    void note(int channel, int pitch, int velocity, boolean on);

    default void realtime(int channel, int pitch, int velocity, boolean on) {}

    default void message(int status, int a, int b) {}

    void status(String message);

    void disconnected();
  }

  private final Activity activity;
  private final Listener listener;
  private final Handler main = new Handler(Looper.getMainLooper());
  private final MidiManager midi;
  private final UsbManager usb;
  private MidiDevice inputDevice, outputDevice;
  private MidiOutputPort input;
  private MidiInputPort output;
  private final ExecutorService send = Executors.newSingleThreadExecutor();
  private volatile UsbDeviceConnection raw;
  private UsbInterface rawInterface;
  private UsbEndpoint rawIn, rawOut;
  private volatile boolean rawRunning;
  private final ConcurrentLinkedQueue<byte[]> rawQueue = new ConcurrentLinkedQueue<>();
  private String rawName, pendingName;
  private volatile int inputGeneration, outputGeneration;
  private volatile boolean closed;
  private final String permission = "id.arus.piano.USB_PERMISSION";
  private final MidiManager.DeviceCallback callback =
      new MidiManager.DeviceCallback() {
        @Override
        public void onDeviceRemoved(MidiDeviceInfo info) {
          if (inputDevice != null && inputDevice.getInfo().getId() == info.getId()) {
            closeInput();
            status("Input MIDI terlepas");
          }
          if (outputDevice != null && outputDevice.getInfo().getId() == info.getId()) {
            closeOutput();
            status("Output MIDI terlepas");
          }
        }
      };
  private final BroadcastReceiver receiver =
      new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
          UsbDevice d = i.getParcelableExtra(UsbManager.EXTRA_DEVICE);
          if (d == null) return;
          if (permission.equals(i.getAction()) && d.getDeviceName().equals(pendingName)) {
            pendingName = null;
            if (usb.hasPermission(d)) openRaw(d);
            else status("Izin USB ditolak. Ketuk MIDI untuk mencoba lagi.");
          }
          if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(i.getAction())
              && d.getDeviceName().equals(rawName)) {
            closeRaw();
            listener.disconnected();
            status("USB MIDI terlepas");
          }
        }
      };

  @android.annotation.SuppressLint(
      "UnspecifiedRegisterReceiverFlag") // API 33+ uses NOT_EXPORTED; older releases validate the
  // scoped PendingIntent and USB permission.
  public MidiHub(Activity a, Listener l) {
    activity = a;
    listener = l;
    midi = (MidiManager) a.getSystemService(Context.MIDI_SERVICE);
    usb = (UsbManager) a.getSystemService(Context.USB_SERVICE);
    IntentFilter f = new IntentFilter(permission);
    f.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
    if (Build.VERSION.SDK_INT >= 33) a.registerReceiver(receiver, f, Context.RECEIVER_NOT_EXPORTED);
    else a.registerReceiver(receiver, f);
    if (midi != null) midi.registerDeviceCallback(callback, main);
  }

  private void status(String s) {
    main.post(
        () -> {
          if (!closed) listener.status(s);
        });
  }

  public void menu() {
    new AlertDialog.Builder(activity)
        .setTitle(Ui.t(activity, "Sambungan MIDI"))
        .setItems(
            new String[] {
              "Input · Android MIDI API",
              Ui.t(activity, "Output · Note On/Off (audio / lampu generik)"),
              Ui.t(activity, "Cadangan · USB host MIDI 1"),
              Ui.t(activity, "Putuskan semua")
            },
            (d, w) -> {
              if (w == 0 || w == 1) chooseApi(w == 1);
              else if (w == 2) chooseRaw();
              else {
                closeInput();
                closeOutput();
                closeRaw();
                listener.disconnected();
                status("MIDI terputus");
              }
            })
        .show();
  }

  private void chooseApi(boolean out) {
    if (midi == null) {
      status("Layanan Android MIDI tidak tersedia. Coba cadangan USB host.");
      return;
    }
    List<MidiDeviceInfo> devices = new ArrayList<>();
    List<Integer> ports = new ArrayList<>();
    List<String> labels = new ArrayList<>();
    try {
      for (MidiDeviceInfo info : midi.getDevices())
        for (MidiDeviceInfo.PortInfo p : info.getPorts())
          if (p.getType()
              == (out ? MidiDeviceInfo.PortInfo.TYPE_INPUT : MidiDeviceInfo.PortInfo.TYPE_OUTPUT)) {
            devices.add(info);
            ports.add(p.getPortNumber());
            labels.add(
                info.getProperties().getString(MidiDeviceInfo.PROPERTY_NAME, "MIDI")
                    + " · "
                    + p.getPortNumber()
                    + " "
                    + p.getName());
          }
    } catch (RuntimeException e) {
      status("Android MIDI gagal: " + e.getMessage());
      return;
    }
    if (labels.isEmpty()) {
      status("Port tidak ditemukan. Sambungkan kabel data OTG, lalu coba USB host.");
      return;
    }
    new AlertDialog.Builder(activity)
        .setTitle(Ui.t(activity, out ? "Pilih tujuan output" : "Pilih input piano"))
        .setItems(
            labels.toArray(new String[0]),
            (d, w) -> {
              closeRaw();
              if (out) closeOutput();
              else closeInput();
              final int generation = out ? outputGeneration : inputGeneration;
              try {
                midi.openDevice(
                    devices.get(w),
                    device -> {
                      if (device == null) {
                        status("Perangkat MIDI tidak dapat dibuka");
                        return;
                      }
                      if (closed || generation != (out ? outputGeneration : inputGeneration)) {
                        quiet(device);
                        return;
                      }
                      if (out) {
                        outputDevice = device;
                        output = device.openInputPort(ports.get(w));
                        if (output == null) {
                          closeOutput();
                          status("Port output sedang digunakan");
                          return;
                        }
                      } else {
                        inputDevice = device;
                        input = device.openOutputPort(ports.get(w));
                        if (input == null) {
                          closeInput();
                          status("Port input tidak tersedia");
                          return;
                        }
                        MidiStream stream = stream(() -> !closed && generation == inputGeneration);
                        input.connect(
                            new MidiReceiver() {
                              @Override
                              public void onSend(byte[] b, int off, int count, long timestamp) {
                                stream.feed(b, off, count);
                              }
                            });
                      }
                      status((out ? "Output → " : "Input ← ") + labels.get(w));
                    },
                    main);
              } catch (RuntimeException e) {
                status("Tidak dapat membuka MIDI: " + e.getMessage());
              }
            })
        .setNegativeButton(Ui.t(activity, "Batal"), null)
        .show();
  }

  private void chooseRaw() {
    List<UsbDevice> ds = new ArrayList<>();
    List<String> labels = new ArrayList<>();
    if (usb != null)
      for (UsbDevice d : usb.getDeviceList().values())
        for (int j = 0; j < d.getInterfaceCount(); j++) {
          UsbInterface in = d.getInterface(j);
          if (in.getInterfaceClass() == 1
              && in.getInterfaceSubclass() == 3
              && in.getInterfaceProtocol() == 0) {
            ds.add(d);
            labels.add(
                (d.getProductName() == null ? "USB MIDI" : d.getProductName())
                    + " · "
                    + d.getVendorId()
                    + ":"
                    + d.getProductId());
            break;
          }
        }
    if (ds.isEmpty()) {
      status(
          "USB MIDI 1 tidak ditemukan. Periksa OTG dan kabel; piano perlu adaptor dayanya"
              + " sendiri.");
      return;
    }
    new AlertDialog.Builder(activity)
        .setTitle(Ui.t(activity, "USB host · tutup aplikasi MIDI lain"))
        .setItems(
            labels.toArray(new String[0]),
            (dialog, w) -> {
              closeInput();
              closeOutput();
              closeRaw();
              UsbDevice d = ds.get(w);
              if (usb.hasPermission(d)) openRaw(d);
              else {
                pendingName = d.getDeviceName();
                int flags = PendingIntent.FLAG_UPDATE_CURRENT;
                if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE;
                usb.requestPermission(
                    d,
                    PendingIntent.getBroadcast(
                        activity,
                        0,
                        new Intent(permission).setPackage(activity.getPackageName()),
                        flags));
              }
            })
        .setNegativeButton(Ui.t(activity, "Batal"), null)
        .show();
  }

  private void openRaw(UsbDevice d) {
    if (closed) return;
    closeRaw();
    try {
      for (int i = 0; i < d.getInterfaceCount(); i++) {
        UsbInterface in = d.getInterface(i);
        if (in.getInterfaceClass() != 1
            || in.getInterfaceSubclass() != 3
            || in.getInterfaceProtocol() != 0) continue;
        UsbEndpoint epIn = null, epOut = null;
        for (int j = 0; j < in.getEndpointCount(); j++) {
          UsbEndpoint ep = in.getEndpoint(j);
          if (ep.getType() != UsbConstants.USB_ENDPOINT_XFER_BULK) continue;
          if (ep.getDirection() == UsbConstants.USB_DIR_IN) epIn = ep;
          else epOut = ep;
        }
        if (epIn == null) continue;
        UsbDeviceConnection c = usb.openDevice(d);
        if (c == null) {
          status("USB gagal dibuka");
          return;
        }
        if (!c.claimInterface(in, true)) {
          c.close();
          continue;
        }
        raw = c;
        rawInterface = in;
        rawIn = epIn;
        rawOut = epOut;
        rawName = d.getDeviceName();
        rawRunning = true;
        final UsbEndpoint readEndpoint = epIn, writeEndpoint = epOut;
        MidiStream stream = stream(() -> raw == c);
        new Thread(
                () -> {
                  byte[] b = new byte[Math.max(64, readEndpoint.getMaxPacketSize())];
                  while (rawRunning && raw == c) {
                    byte[] packet;
                    while ((packet = rawQueue.poll()) != null && writeEndpoint != null)
                      c.bulkTransfer(writeEndpoint, packet, packet.length, 50);
                    int n = c.bulkTransfer(readEndpoint, b, b.length, 4);
                    for (int k = 0; k + 3 < n; k += 4) {
                      if (((b[k] >> 4) & 15) != 0) continue;
                      int length = MidiStream.usbLength(b[k] & 15);
                      stream.feed(b, k + 1, length);
                    }
                  }
                },
                "ArusUSB")
            .start();
        status(
            "USB host ← "
                + d.getProductName()
                + " · kabel 0"
                + (epOut == null ? " · input saja" : " · output tersedia"));
        return;
      }
      status("Tidak dapat mengklaim MIDI. Tutup aplikasi MIDI lain atau gunakan Android MIDI API.");
    } catch (RuntimeException e) {
      closeRaw();
      status("USB: " + e.getMessage());
    }
  }

  private MidiStream stream(java.util.function.BooleanSupplier valid) {
    return new MidiStream(
        new MidiStream.Listener() {
          public void note(int ch, int p, int v, boolean on) {
            if (!valid.getAsBoolean()) return;
            listener.realtime(ch, p, v, on);
            main.post(
                () -> {
                  if (valid.getAsBoolean()) listener.note(ch, p, v, on);
                });
          }

          public void message(int status, int a, int b) {
            if (valid.getAsBoolean()) listener.message(status, a, b);
          }
        });
  }

  public void noteOut(int channel, int pitch, int velocity, boolean on) {
    sendMessage((on ? 0x90 : 0x80) | (channel & 15), pitch, on ? velocity : 0);
  }

  public void sendMessage(int status, int a, int b) {
    int kind = status & 240, len = (kind == 0xc0 || kind == 0xd0) ? 2 : 3;
    byte[] bytes =
        len == 2
            ? new byte[] {(byte) status, (byte) a}
            : new byte[] {(byte) status, (byte) a, (byte) b};
    if (raw != null && rawOut != null) {
      rawQueue.offer(new byte[] {(byte) (kind >> 4), (byte) status, (byte) a, (byte) b});
      return;
    }
    MidiInputPort port = output;
    final int generation = outputGeneration;
    if (port != null && !send.isShutdown())
      send.execute(
          () -> {
            synchronized (MidiHub.this) {
              if (closed || output != port || generation != outputGeneration) return;
              try {
                port.send(bytes, 0, bytes.length);
              } catch (IOException e) {
                status("MIDI output: " + e.getMessage());
              }
            }
          });
  }

  public boolean hasOutput() {
    return output != null || (raw != null && rawOut != null);
  }

  public void panic() {
    for (int n = 21; n <= 108; n++) noteOut(0, n, 0, false);
  }

  private static void quiet(AutoCloseable c) {
    if (c != null)
      try {
        c.close();
      } catch (Exception ignored) {
      }
  }

  private void closeInput() {
    inputGeneration++;
    quiet(input);
    quiet(inputDevice);
    input = null;
    inputDevice = null;
    listener.disconnected();
  }

  private synchronized void closeOutput() {
    if (output != null) {
      try {
        for (int ch = 0; ch < 16; ch++)
          output.send(new byte[] {(byte) (0xb0 | ch), 64, 0, (byte) (0xb0 | ch), 123, 0}, 0, 6);
      } catch (IOException ignored) {
      }
    }
    outputGeneration++;
    quiet(output);
    quiet(outputDevice);
    output = null;
    outputDevice = null;
  }

  private void closeRaw() {
    pendingName = null;
    rawRunning = false;
    UsbDeviceConnection c = raw;
    if (c != null) {
      if (rawOut != null)
        for (int ch = 0; ch < 16; ch++)
          c.bulkTransfer(
              rawOut,
              new byte[] {11, (byte) (0xb0 | ch), 64, 0, 11, (byte) (0xb0 | ch), 123, 0},
              8,
              10);
      c.releaseInterface(rawInterface);
      c.close();
    }
    raw = null;
    rawName = null;
    rawIn = rawOut = null;
    rawQueue.clear();
    if (c != null) listener.disconnected();
  }

  @Override
  public void close() {
    closed = true;
    closeInput();
    closeOutput();
    closeRaw();
    if (midi != null) midi.unregisterDeviceCallback(callback);
    activity.unregisterReceiver(receiver);
    send.shutdownNow();
  }
}

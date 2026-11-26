package id.arus.piano;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

final class FeatureChecks {
  static void run(SmokeInstrumentation test, MainActivity activity) throws Exception {
    Context context = test.getTargetContext();
    File bank = new File(context.getFilesDir(), "TimGM6mb.sf2");
    test.ok(bank.length() > 5000000, "redistributable GM SF2 installed");
    File marker = new File(context.getFilesDir(), "upgrade-marker.txt");
    if (marker.isFile())
      test.ok(
          read(marker).equals("preserved-v0.1.0"),
          "old-version private data survives same-key APK update");
    long handle = FluidNative.create(22050, 128);
    test.ok(handle != 0, "native FluidSynth creates engine");
    try {
      test.ok(FluidNative.load(handle, bank.getPath()) >= 0, "FluidSynth loads SF2");
      FluidNative.configure(handle, 128, false);
      long soft = energy(handle, 0, 60, 25), loud = energy(handle, 0, 60, 120);
      test.ok(loud > soft * 2, "real velocity affects SF2 output amplitude");
      for (int p = 0; p < 128; p++) {
        long sum = energy(handle, p, 60, 90);
        test.ok(sum > 0, "GM preset produces sound: " + p);
      }
      FluidNative.panic(handle);
      FluidNative.message(handle, 0xc000, 73, 0);
      FluidNative.message(handle, 0xb000, 64, 127);
      FluidNative.message(handle, 0x9000, 69, 100);
      short[] pcm = new short[22050];
      FluidNative.render(handle, pcm, 11025);
      FluidNative.message(handle, 0x8000, 69, 0);
      FluidNative.render(handle, pcm, 11025);
      long held = sum(pcm);
      FluidNative.message(handle, 0xb000, 64, 0);
      for (int i = 0; i < 4; i++) FluidNative.render(handle, pcm, 11025);
      test.ok(held > sum(pcm) * 4, "sustain retains a released note and pedal-up releases it");
      File wav = new File(context.getCacheDir(), "native-chord.wav");
      writeChord(handle, wav);
      test.ok(
          wav.length() > 100000, "original piano audio fixture rendered by production FluidSynth");
      AtomicBoolean cancel = new AtomicBoolean();
      AudioDecoder.Decoded decoded =
          AudioDecoder.decode(
              context,
              Uri.fromFile(wav),
              new File(context.getCacheDir(), "test-decoded.pcm"),
              cancel,
              (p, s) -> {});
      test.ok(
          Math.abs(decoded.duration() - 3) < .03, "WAV decoder and resampler preserve duration");
      File model = new File(context.getCacheDir(), "basic-pitch-test.tflite");
      copy(test.getContext().getAssets().open("basic-pitch.tflite"), model);
      MidiSong transcription = Transcriber.basicPitch(decoded, model, cancel, (p, s) -> {}, .3f);
      Set<Integer> pitches = new HashSet<>();
      for (MidiSong.Note n : transcription.notes) pitches.add(n.pitch);
      test.ok(
          pitches.contains(60) && pitches.contains(64) && pitches.contains(67),
          "Basic Pitch detects real polyphonic C-major piano chord " + pitches);
      ByteArrayOutputStream midi = new ByteArrayOutputStream();
      MidiWriter.write(transcription, midi);
      MidiSong imported = MidiSong.read(new ByteArrayInputStream(midi.toByteArray()));
      test.ok(
          imported.notes.size() == transcription.notes.size(),
          "converted notes export and reimport as MIDI");
      File m4a = new File(context.getCacheDir(), "native-chord.m4a");
      if (!m4a.isFile() || m4a.length() == 0) AacFixture.encode(wav, m4a);
      for (String extension : new String[] {"mp3", "m4a", "flac"}) {
        File file = new File(context.getCacheDir(), "native-chord." + extension);
        if (!file.isFile() && !extension.equals("m4a"))
          copy(test.getContext().getAssets().open("native-chord." + extension), file);
        if (file.isFile()) {
          AudioDecoder.Decoded compressed =
              AudioDecoder.decode(
                  context,
                  Uri.fromFile(file),
                  new File(context.getCacheDir(), "test-" + extension + ".pcm"),
                  cancel,
                  (p, s) -> {});
          test.ok(Math.abs(compressed.duration() - 3) < .15, extension + " decoded offline");
          compressed.file.delete();
        }
      }
      decoded.file.delete();
    } finally {
      FluidNative.destroy(handle);
    }
    String xml =
        "<score-partwise><part-list><score-part"
            + " id='P1'><part-name>Piano</part-name></score-part></part-list><part id='P1'><measure"
            + " number='1'><attributes><divisions>1</divisions><time><beats>3</beats><beat-type>4</beat-type></time><staves>2</staves></attributes><direction><sound"
            + " tempo='90'/></direction><note><pitch><step>C</step><octave>4</octave></pitch><duration>1</duration><staff>1</staff></note><backup><duration>1</duration></backup><note><pitch><step>C</step><octave>3</octave></pitch><duration>3</duration><staff>2</staff></note></measure></part></score-partwise>";
    MidiSong parsed = MusicXml.read(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    test.ok(
        parsed.notes.size() == 2 && parsed.meters.get(parsed.meters.size() - 1).numerator == 3,
        "MusicXML works on actual Android parser");
    boolean blocked = false;
    try {
      MusicXml.read(
          new ByteArrayInputStream(
              "<!DOCTYPE x SYSTEM 'https://example.com'><score-partwise/>"
                  .getBytes(StandardCharsets.UTF_8)));
    } catch (IOException e) {
      blocked = true;
    }
    test.ok(blocked, "Android MusicXML rejects external DTD");
    Trainer snapshot = new Trainer();
    snapshot.load(MidiSong.exercise());
    File pdf = new File(context.getCacheDir(), "test-score.pdf");
    try (OutputStream out = new FileOutputStream(pdf)) {
      ScorePdf.write(context, snapshot, out);
    }
    ParcelFileDescriptor fd = ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY);
    PdfRenderer renderer = new PdfRenderer(fd);
    test.ok(renderer.getPageCount() > 0, "exported PDF is a valid renderable score");
    PdfRenderer.Page page = renderer.openPage(0);
    Bitmap image = Bitmap.createBitmap(842, 595, Bitmap.Config.ARGB_8888);
    page.render(image, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
    int ink = 0;
    for (int y = 40; y < 560; y += 4)
      for (int x = 25; x < 815; x += 4)
        if (image.getPixel(x, y) != android.graphics.Color.WHITE) ink++;
    test.ok(ink > 100, "PDF contains rendered grand staff and notes");
    page.close();
    renderer.close();
    image.recycle();
    test.runOnMainSync(
        () -> {
          try {
            PianoView piano = (PianoView) test.field(activity, "piano");
            Trainer trainer = (Trainer) test.field(activity, "trainer");
            trainer.load(MidiSong.exercise());
            trainer.playing = false;
            for (int mode = 0; mode < 3; mode++) {
              piano.displayMode = mode;
              Bitmap b =
                  Bitmap.createBitmap(piano.getWidth(), piano.getHeight(), Bitmap.Config.ARGB_8888);
              piano.draw(new Canvas(b));
              test.ok(b.getWidth() > 0, "learning view renders mode " + mode);
              b.recycle();
            }
            piano.displayMode = 0;
            context
                .getSharedPreferences("settings", 0)
                .edit()
                .putBoolean("light", true)
                .putString("language", "en")
                .commit();
            test.ok(
                Ui.bg(context) == 0xfff3f5f8 && Ui.t(context, "▶ Mulai").equals("▶ Start"),
                "light palette and English UI translations");
            context
                .getSharedPreferences("settings", 0)
                .edit()
                .putBoolean("light", false)
                .putString("language", "id")
                .commit();
            piano.invalidate();
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
    checkAbout(test, context);
    checkLive(test, activity);
    checkHelp(test, context);
    ArrangementChecks.run(test, activity);
    File future = new File(context.getFilesDir(), "update-fixture.apk");
    if (future.isFile()) verifyUpdate(test, context, future);
  }

  private static void checkLive(SmokeInstrumentation test, MainActivity activity) throws Exception {
    test.waitForIdleSync();
    PianoView piano = (PianoView) test.field(activity, "piano");
    Trainer trainer = (Trainer) test.field(activity, "trainer");
    MidiSong song = trainer.song;
    test.click("Bermain bebas");
    final double time = trainer.time;
    final int hits = trainer.hits, wrong = trainer.wrong;
    test.runOnMainSync(
        () -> {
          test.ok(piano.freePlay && !trainer.playing, "free play pauses song without replacing it");
          activity.touch(60, true);
          activity.touch(64, true);
          activity.note(1, 60, 111, true);
          test.ok(
              piano.live.notes().size() == 2 && piano.down[60] && piano.down[64],
              "free play captures touch chord without duplicate MIDI overlap");
          activity.touch(60, false);
          test.ok(
              piano.down[60] && piano.live.notes().get(0).held(),
              "same pitch remains held by another input source");
        });
    android.os.SystemClock.sleep(180);
    final int[] before = new int[1];
    test.runOnMainSync(() -> before[0] = liveTop(test, piano));
    android.os.SystemClock.sleep(250);
    test.runOnMainSync(
        () -> {
          int after = liveTop(test, piano);
          test.ok(
              before[0] >= 0 && after >= 0 && after < before[0],
              "rendered live bar physically moves upward while held");
          test.ok(
              trainer.time == time && trainer.hits == hits && trainer.wrong == wrong,
              "free play leaves song position and score unchanged");
          activity.note(1, 60, 0, false);
          activity.touch(64, false);
          test.ok(
              !piano.down[60] && !piano.down[64] && !piano.live.notes().get(0).held(),
              "live note off detaches trail from keyboard");
          activity.note(0, 67, 120, true);
          test.ok(piano.live.notes().get(2).velocity == 120, "MIDI velocity reaches rising trail");
          activity.disconnected();
          test.ok(
              !piano.down[67] && !piano.live.notes().get(2).held(),
              "USB disconnect releases live MIDI key");
          for (int mode = 0; mode < 4; mode++) {
            piano.labelMode = mode;
            android.graphics.Bitmap bitmap =
                android.graphics.Bitmap.createBitmap(
                    piano.getWidth(), piano.getHeight(), android.graphics.Bitmap.Config.ARGB_8888);
            piano.draw(new android.graphics.Canvas(bitmap));
            bitmap.recycle();
          }
          test.ok(true, "all keyboard label modes render in live mode");
        });
    test.click("Hentikan & bersihkan");
    test.runOnMainSync(
        () -> test.ok(piano.live.notes().isEmpty(), "stop and clear removes all live trails"));
    test.runOnMainSync(
        () -> {
          activity.note(0, 60, 90, true);
          test.callActivityOnPause(activity);
          test.ok(
              piano.live.notes().isEmpty() && !piano.down[60],
              "background clears live notes and keyboard state");
          activity.note(0, 64, 90, true);
          test.ok(!piano.down[64], "paused activity ignores incoming MIDI state");
          test.callActivityOnResume(activity);
        });
    test.click("Kembali ke lagu");
    test.runOnMainSync(
        () -> {
          test.ok(
              !piano.freePlay && trainer.song == song && trainer.time == time && !trainer.playing,
              "return to song preserves exact position, song and pause state");
          piano.labelMode = NoteLabels.LETTERS;
        });
  }

  private static int liveTop(SmokeInstrumentation test, PianoView piano) {
    try {
      android.graphics.RectF[] keys = (android.graphics.RectF[]) test.field(piano, "keys");
      int top = (int) (float) test.field(piano, "top"),
          bottom = (int) (float) test.field(piano, "bottom");
      android.graphics.Bitmap bitmap =
          android.graphics.Bitmap.createBitmap(
              piano.getWidth(), piano.getHeight(), android.graphics.Bitmap.Config.ARGB_8888);
      piano.draw(new android.graphics.Canvas(bitmap));
      int found = -1, x = Math.round(keys[60].centerX());
      for (int y = top; y < bottom - 4; y++) {
        int color = bitmap.getPixel(x, y),
            red = android.graphics.Color.red(color),
            green = android.graphics.Color.green(color),
            blue = android.graphics.Color.blue(color);
        if (green > 130 && green > red * 1.6 && Math.abs(blue - green) < 45) {
          found = y;
          break;
        }
      }
      bitmap.recycle();
      return found;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static void checkHelp(SmokeInstrumentation test, Context context) {
    for (boolean english : new boolean[] {false, true}) {
      context
          .getSharedPreferences("settings", 0)
          .edit()
          .putString("language", english ? "en" : "id")
          .putBoolean("light", english)
          .commit();
      HelpActivity help =
          (HelpActivity)
              test.startActivitySync(
                  new Intent(context, HelpActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      test.waitForIdleSync();
      test.runOnMainSync(
          () -> {
            Button topic =
                test.find(
                    help.getWindow().getDecorView(),
                    english ? "Free play & labels" : "Bermain bebas & label");
            test.ok(topic != null, "offline help has topic navigation in selected language");
            topic.performClick();
            String text = allText(help.getWindow().getDecorView());
            test.ok(
                text.contains(english ? "Rising note bars" : "Balok bergerak ke atas")
                    && text.contains("Do♯"),
                "free play guide explains direction and chromatic labels");
            test.find(
                    help.getWindow().getDecorView(),
                    english ? "MIDI / USB connection" : "Sambungan MIDI / USB")
                .performClick();
            test.ok(
                allText(help.getWindow().getDecorView()).contains("USB TO HOST"),
                "offline connection guide is actionable");
            help.finish();
          });
    }
    context
        .getSharedPreferences("settings", 0)
        .edit()
        .putString("language", "id")
        .putBoolean("light", false)
        .commit();
    test.waitForIdleSync();
  }

  private static void checkAbout(SmokeInstrumentation test, Context context) {
    for (boolean english : new boolean[] {false, true}) {
      context
          .getSharedPreferences("settings", 0)
          .edit()
          .putString("language", english ? "en" : "id")
          .putBoolean("light", english)
          .commit();
      Activity about =
          test.startActivitySync(
              new Intent(context, AboutActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      test.waitForIdleSync();
      test.runOnMainSync(
          () -> {
            String text = allText(about.getWindow().getDecorView());
            test.ok(
                text.contains(BuildConfig.VERSION_NAME)
                    && text.contains("" + BuildConfig.VERSION_CODE),
                "About displays installed version and code");
            test.ok(
                text.contains(
                        english
                            ? "Help PianoFlow keep growing"
                            : "Bantu PianoFlow terus berkembang")
                    && text.contains("@radarswingid")
                    && text.contains(english ? "entirely optional" : "bersifat sukarela"),
                "About contains author credit and optional support in selected language");
            test.ok(
                text.contains("MIDIVisualizer")
                    && text.contains(english ? "not a fork" : "bukan fork")
                    && text.contains("LGPL-2.1"),
                "About accurately attributes upstream components");
            android.view.View decor = about.getWindow().getDecorView();
            Bitmap bitmap =
                Bitmap.createBitmap(decor.getWidth(), decor.getHeight(), Bitmap.Config.ARGB_8888);
            decor.draw(new Canvas(bitmap));
            test.ok(bitmap.getWidth() > bitmap.getHeight(), "About renders in landscape");
            bitmap.recycle();
            about.finish();
          });
    }
    context
        .getSharedPreferences("settings", 0)
        .edit()
        .putString("language", "id")
        .putBoolean("light", false)
        .commit();
  }

  private static String allText(android.view.View view) {
    StringBuilder text = new StringBuilder();
    if (view instanceof TextView) text.append(((TextView) view).getText()).append('\n');
    if (view instanceof android.view.ViewGroup) {
      android.view.ViewGroup group = (android.view.ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) text.append(allText(group.getChildAt(i)));
    }
    return text.toString();
  }

  private static long energy(long handle, int program, int pitch, int velocity) {
    FluidNative.panic(handle);
    FluidNative.message(handle, 0xc000, program, 0);
    FluidNative.message(handle, 0x9000, pitch, velocity);
    short[] pcm = new short[8192];
    FluidNative.render(handle, pcm, 4096);
    return sum(pcm);
  }

  private static long sum(short[] pcm) {
    long sum = 0;
    for (short value : pcm) sum += Math.abs((int) value);
    return sum;
  }

  private static void le(DataOutputStream out, int value) throws IOException {
    out.writeInt(Integer.reverseBytes(value));
  }

  private static void writeChord(long handle, File file) throws IOException {
    int rate = 22050, frames = rate * 3;
    FluidNative.panic(handle);
    FluidNative.message(handle, 0xc000, 0, 0);
    try (DataOutputStream out =
        new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)))) {
      out.writeBytes("RIFF");
      le(out, 36 + frames * 2);
      out.writeBytes("WAVEfmt ");
      le(out, 16);
      out.writeShort(Short.reverseBytes((short) 1));
      out.writeShort(Short.reverseBytes((short) 1));
      le(out, rate);
      le(out, rate * 2);
      out.writeShort(Short.reverseBytes((short) 2));
      out.writeShort(Short.reverseBytes((short) 16));
      out.writeBytes("data");
      le(out, frames * 2);
      short[] pcm = new short[512];
      for (int i = 0; i < frames; i += 256) {
        if (i == 6656)
          for (int p : new int[] {60, 64, 67}) FluidNative.message(handle, 0x9000, p, 95);
        if (i == 33024)
          for (int p : new int[] {60, 64, 67}) FluidNative.message(handle, 0x8000, p, 0);
        int n = Math.min(256, frames - i);
        FluidNative.render(handle, pcm, n);
        for (int j = 0; j < n; j++)
          out.writeShort(Short.reverseBytes((short) ((pcm[j * 2] + pcm[j * 2 + 1]) / 2)));
      }
    }
  }

  private static String read(File file) throws IOException {
    try (InputStream input = new FileInputStream(file);
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      byte[] b = new byte[8192];
      int n;
      while ((n = input.read(b)) != -1) out.write(b, 0, n);
      return out.toString("UTF-8");
    }
  }

  private static void copy(InputStream input, File file) throws IOException {
    try (InputStream in = input;
        OutputStream out = new FileOutputStream(file)) {
      byte[] b = new byte[8192];
      int n;
      while ((n = in.read(b)) != -1) out.write(b, 0, n);
    }
  }

  private static void verifyUpdate(SmokeInstrumentation test, Context context, File apk)
      throws Exception {
    android.content.pm.PackageInfo info =
        context.getPackageManager().getPackageArchiveInfo(apk.getPath(), 0);
    UpdateClient.Release release = new UpdateClient.Release();
    release.version = info.versionName;
    release.code =
        android.os.Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
    release.sha = UpdateClient.sha256(apk);
    release.certificate = UpdateClient.CERT;
    UpdateClient.verify(context, apk, release);
    test.ok(true, "valid same-key higher-version APK passes cryptographic verification");
    String original = release.sha;
    release.sha = "0000000000000000000000000000000000000000000000000000000000000000";
    boolean rejected = false;
    try {
      UpdateClient.verify(context, apk, release);
    } catch (SecurityException e) {
      rejected = true;
    }
    test.ok(rejected, "tampered APK checksum rejected");
    release.sha = original;
    release.code--;
    rejected = false;
    try {
      UpdateClient.verify(context, apk, release);
    } catch (SecurityException e) {
      rejected = true;
    }
    test.ok(rejected, "APK version metadata mismatch rejected");
    UpdateClient.Release self = new UpdateClient.Release();
    self.version =
        context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
    self.code = UpdateClient.installedCode(context);
    File current = new File(context.getApplicationInfo().sourceDir);
    self.sha = UpdateClient.sha256(current);
    rejected = false;
    try {
      UpdateClient.verify(context, current, self);
    } catch (SecurityException e) {
      rejected = true;
    }
    test.ok(rejected, "same-version/downgrade APK rejected");
    for (String name : new String[] {"wrong-signer.apk", "bad-signature.apk"}) {
      File fixture = new File(context.getFilesDir(), name);
      if (fixture.isFile()) {
        release.code =
            android.os.Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
        release.sha = UpdateClient.sha256(fixture);
        boolean denied = false;
        try {
          UpdateClient.verify(context, fixture, release);
        } catch (SecurityException e) {
          denied = true;
        }
        test.ok(
            denied, "cryptographic verification rejects " + name + " even when checksum matches");
      }
    }
  }
}

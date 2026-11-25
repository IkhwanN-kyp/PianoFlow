package id.arus.piano;

import android.app.*;
import android.content.*;
import android.graphics.RectF;
import android.media.AudioTrack;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.lang.reflect.*;

/** No test frameworks or third-party libraries. Run with adb shell am instrument -w. */
public final class SmokeInstrumentation extends Instrumentation {
  int checks = 0;
  MainActivity a;
  Trainer t;
  PianoView piano;

  @Override
  public void runOnMainSync(Runnable action) {
    final Throwable[] error = new Throwable[1];
    super.runOnMainSync(
        () -> {
          try {
            action.run();
          } catch (Throwable e) {
            error[0] = e;
          }
        });
    if (error[0] != null) throw new AssertionError("Main-thread test failure", error[0]);
  }

  @Override
  public void onCreate(Bundle arguments) {
    super.onCreate(arguments);
    start();
  }

  void ok(boolean value, String label) {
    checks++;
    if (!value) throw new AssertionError(label);
  }

  Object field(Object o, String name) throws Exception {
    Field f = o.getClass().getDeclaredField(name);
    f.setAccessible(true);
    return f.get(o);
  }

  Button find(View v, String text) {
    if (v instanceof Button && ((Button) v).getText().toString().equals(text)) return (Button) v;
    if (v instanceof ViewGroup)
      for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) {
        Button b = find(((ViewGroup) v).getChildAt(i), text);
        if (b != null) return b;
      }
    return null;
  }

  void click(String label) {
    runOnMainSync(
        () -> {
          Button b = find(a.getWindow().getDecorView(), label);
          ok(b != null, "button " + label);
          b.performClick();
        });
    SystemClock.sleep(80);
  }

  @Override
  public void onStart() {
    Bundle result = new Bundle();
    try {
      a =
          (MainActivity)
              startActivitySync(
                  new Intent(getTargetContext(), MainActivity.class)
                      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      SystemClock.sleep(80);
      t = (Trainer) field(a, "trainer");
      piano = (PianoView) field(a, "piano");
      click("Demo");
      runOnMainSync(
          () -> {
            ok(piano.getWidth() > piano.getHeight(), "landscape canvas");
            ok(t.song.notes.size() == 23, "original exercise loaded");
          });
      AudioTrack audio = null;
      for (int i = 0; i < 30 && audio == null; i++) {
        Thread.sleep(100);
        audio = (AudioTrack) field(field(a, "synth"), "audio");
      }
      ok(
          audio != null && audio.getState() == AudioTrack.STATE_INITIALIZED,
          "offline AudioTrack initialized");
      runOnMainSync(
          () -> {
            t.seek(2);
            t.speed = .25;
            t.playing = true;
            piano.invalidate();
          });
      RectF[] keys = (RectF[]) field(piano, "keys");
      int[] offset = new int[2];
      runOnMainSync(() -> piano.getLocationOnScreen(offset));
      ok(keys[21] != null && keys[108] != null, "88-key endpoints present");
      float x0 = offset[0] + keys[48].centerX(),
          x1 = offset[0] + keys[60].centerX(),
          y = offset[1] + keys[60].bottom - 15;
      long start = SystemClock.uptimeMillis();
      pointer(start, MotionEvent.ACTION_DOWN, new float[] {x0}, new float[] {y});
      runOnMainSync(
          () -> ok(t.hits == 1 && Math.abs(t.time - 2) < .01, "one key holds incomplete chord"));
      pointer(
          start,
          MotionEvent.ACTION_POINTER_DOWN | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),
          new float[] {x0, x1},
          new float[] {y, y});
      runOnMainSync(
          () -> {
            ok(t.hits == 2, "multitouch chord scores both notes");
            ok(piano.down[48] && piano.down[60], "both touches held");
          });
      pointer(
          start,
          MotionEvent.ACTION_POINTER_UP | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),
          new float[] {x0, x1},
          new float[] {y, y});
      pointer(start, MotionEvent.ACTION_UP, new float[] {x0}, new float[] {y});
      runOnMainSync(() -> ok(!piano.down[48] && !piano.down[60], "touch release clears both keys"));
      runOnMainSync(
          () -> {
            t.playing = false;
            t.seek(2);
            t.playing = true;
            piano.invalidate();
            a.note(0, 48, 100, true);
            a.note(0, 60, 100, true);
            ok(t.hits == 2, "MIDI listener scores real-time chord");
            a.note(0, 48, 0, false);
            a.note(0, 60, 0, false);
            t.playing = false;
          });
      click("Latihan");
      runOnMainSync(
          () -> {
            ok(t.listen, "listen mode enabled");
            t.seek(2);
            t.playing = true;
            piano.invalidate();
          });
      Thread.sleep(250);
      runOnMainSync(() -> ok(t.time > 2, "listen clock advances"));
      click("Ⅱ Jeda");
      runOnMainSync(() -> ok(!t.playing, "pause button stops clock"));
      runOnMainSync(
          () -> {
            a.onActivityResult(
                10,
                Activity.RESULT_OK,
                new Intent()
                    .setData(Uri.parse("content://id.arus.piano.test.fixtures/uji-asli.mid")));
            find(a.getWindow().getDecorView(), "Demo").performClick();
          });
      SystemClock.sleep(250);
      runOnMainSync(() -> ok(t.song.notes.size() == 23, "demo cancels stale asynchronous import"));
      runOnMainSync(
          () ->
              a.onActivityResult(
                  10,
                  Activity.RESULT_OK,
                  new Intent()
                      .setData(Uri.parse("content://id.arus.piano.test.fixtures/uji-asli.mid"))));
      for (int i = 0; i < 50; i++) {
        Thread.sleep(100);
        final boolean[] ready = {false};
        runOnMainSync(() -> ready[0] = t.song.notes.size() == 2);
        if (ready[0]) break;
      }
      runOnMainSync(
          () -> {
            ok(t.song.notes.size() == 2, "content URI import uses production path");
            ok(t.song.duration == .5, "import timing correct");
          });
      runOnMainSync(
          () ->
              a.onActivityResult(
                  10,
                  Activity.RESULT_OK,
                  new Intent()
                      .setData(Uri.parse("content://id.arus.piano.test.fixtures/rusak.mid"))));
      Thread.sleep(350);
      runOnMainSync(
          () -> {
            try {
              ok(
                  ((TextView) field(a, "status")).getText().toString().startsWith("Impor gagal"),
                  "malformed import has visible error");
              ok(t.song.notes.size() == 2, "bad import preserves existing song");
            } catch (Exception e) {
              throw new RuntimeException(e);
            }
          });
      runOnMainSync(
          () -> {
            t.playing = true;
            piano.invalidate();
            callActivityOnPause(a);
            ok(!t.playing, "background pauses practice");
            callActivityOnResume(a);
          });
      click("Demo");
      a.getPreferences(0).edit().remove("uri").commit();
      FeatureChecks.run(this, a);
      result.putString(
          "stream",
          "PASS Android runtime: "
              + checks
              + " checks (landscape, AudioTrack, multitouch, MIDI listener, wait chord, pause,"
              + " import, invalid import, lifecycle).\n");
      finish(Activity.RESULT_OK, result);
    } catch (Throwable e) {
      result.putString(
          "stream", "FAIL after " + checks + " checks: " + android.util.Log.getStackTraceString(e));
      finish(Activity.RESULT_CANCELED, result);
    }
  }

  void pointer(long start, int action, float[] x, float[] y) {
    MotionEvent.PointerProperties[] pp = new MotionEvent.PointerProperties[x.length];
    MotionEvent.PointerCoords[] pc = new MotionEvent.PointerCoords[x.length];
    for (int i = 0; i < x.length; i++) {
      pp[i] = new MotionEvent.PointerProperties();
      pp[i].id = i;
      pp[i].toolType = MotionEvent.TOOL_TYPE_FINGER;
      pc[i] = new MotionEvent.PointerCoords();
      pc[i].x = x[i];
      pc[i].y = y[i];
      pc[i].pressure = 1;
      pc[i].size = 1;
    }
    MotionEvent e =
        MotionEvent.obtain(
            start,
            SystemClock.uptimeMillis(),
            action,
            x.length,
            pp,
            pc,
            0,
            0,
            1,
            1,
            0,
            0,
            android.view.InputDevice.SOURCE_TOUCHSCREEN,
            0);
    sendPointerSync(e);
    e.recycle();
    SystemClock.sleep(80);
  }
}

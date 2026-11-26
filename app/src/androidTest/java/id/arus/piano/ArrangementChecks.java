package id.arus.piano;

import android.app.*;
import android.content.*;
import android.media.AudioTrack;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;

final class ArrangementChecks {
  static void run(SmokeInstrumentation test, MainActivity main) throws Exception {
    Context context = test.getTargetContext();
    File source = new File(context.getFilesDir(), "duet-asli-test.mid");
    try (InputStream input = test.getContext().getAssets().open("duet-asli.mid");
        OutputStream output = new FileOutputStream(source)) {
      byte[] buffer = new byte[4096];
      int n;
      while ((n = input.read(buffer)) != -1) output.write(buffer, 0, n);
    }
    MidiSong fixture;
    try (InputStream input = new FileInputStream(source)) {
      fixture = MidiSong.read(input);
    }
    File stored = new LibraryStore(context).put(fixture, "Duet piano + gitar · latihan asli.mid");
    android.content.SharedPreferences preferences = context.getSharedPreferences("arrangements", 0);
    android.content.SharedPreferences.Editor clear = preferences.edit();
    for (String key : preferences.getAll().keySet())
      if (key.startsWith("song:" + stored.getAbsolutePath() + ":")) clear.remove(key);
    clear.commit();
    test.runOnMainSync(
        () ->
            main.onActivityResult(
                10, Activity.RESULT_OK, new Intent().setData(Uri.fromFile(source))));
    Trainer trainer = (Trainer) test.field(main, "trainer");
    for (int i = 0; i < 100; i++) {
      final boolean[] ready = {false};
      test.runOnMainSync(() -> ready[0] = trainer.song.tracks.containsValue("Guitar"));
      if (ready[0]) break;
      Thread.sleep(30);
    }
    test.runOnMainSync(
        () -> {
          test.ok(
              trainer.arrangement.parts.size() == 3 && trainer.arrangement.get(49).program == 24,
              "native import detects piano staves and GM guitar");
          trainer.time = 1;
          trainer.playing = true;
          trainer.listen = false;
          main.frame();
          try {
            SongPlayback playback = (SongPlayback) test.field(main, "songPlayback");
            int[][] held = (int[][]) test.field(playback, "held");
            test.ok(
                held[1][67] == 1 && held[0][60] == 0 && held[0][48] == 0,
                "native practice plays guitar while learner piano remains silent");
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
          test.ok(
              !trainer.visible(trainer.song.notes.get(2))
                  && !trainer.selected(trainer.song.notes.get(2)),
              "guitar is absent from learner score and visuals");
          main.note(0, 48, 100, true);
          main.note(0, 60, 100, true);
          main.note(0, 48, 0, false);
          main.note(0, 60, 0, false);
          test.ok(
              trainer.hits == 2, "actual learner MIDI chord scores without automatic guitar input");
          trainer.playing = false;
          main.frame();
        });
    Instrumentation.ActivityMonitor monitor =
        test.addMonitor(PartsActivity.class.getName(), null, false);
    test.click("Bagian & pengiring");
    PartsActivity editor = (PartsActivity) test.waitForMonitorWithTimeout(monitor, 5000);
    test.removeMonitor(monitor);
    test.ok(editor != null, "Parts editor opens through real toolbar");
    for (int i = 0; i < 100; i++) {
      if (test.field(editor, "trainer") != null) break;
      Thread.sleep(30);
    }
    test.waitForIdleSync();
    Trainer edited = (Trainer) test.field(editor, "trainer");
    final View[] guitarCard = {null};
    final Spinner[] role = {null};
    test.runOnMainSync(
        () -> {
          test.ok(
              edited.arrangement.parts.size() == 3,
              "parts editor loads the saved multi-instrument song");
          try {
            ListView list = (ListView) test.field(editor, "list");
            for (int i = 0; i < list.getChildCount(); i++) {
              Spinner spinner = findRole(list.getChildAt(i), "Peran bagian Guitar");
              if (spinner != null) {
                guitarCard[0] = list.getChildAt(i);
                role[0] = spinner;
                break;
              }
            }
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
          test.ok(role[0] != null, "guitar has its own visible role selector");
          role[0].setSelection(Arrangement.MUTE);
        });
    test.waitForIdleSync();
    Thread.sleep(80);
    test.runOnMainSync(
        () -> {
          test.ok(
              edited.arrangement.get(49).mode == Arrangement.MUTE,
              "role widget really mutes the chosen part");
          test.find(guitarCard[0], "Dengarkan").performClick();
        });
    boolean sounding = false;
    for (int i = 0; i < 150; i++) {
      try {
        SongPlayback playback = (SongPlayback) test.field(editor, "playback");
        if (playback != null) {
          int[][] held = (int[][]) test.field(playback, "held");
          if (held[1][67] > 0) {
            sounding = true;
            break;
          }
        }
      } catch (Exception ignored) {
      }
      Thread.sleep(20);
    }
    test.ok(sounding, "Listen genuinely auditions guitar even when its role is muted");
    OfflineSynth engine = (OfflineSynth) test.field(editor, "synth");
    AudioTrack audio = (AudioTrack) test.field(engine, "audio");
    test.ok(
        audio != null && audio.getPlayState() == AudioTrack.PLAYSTATE_PLAYING,
        "isolated preview uses a running native AudioTrack");
    final SeekBar[] level = {null};
    test.runOnMainSync(
        () -> test.find(editor.getWindow().getDecorView(), "Hentikan preview").performClick());
    test.waitForIdleSync();
    Thread.sleep(80);
    test.runOnMainSync(
        () -> {
          try {
            ListView list = (ListView) test.field(editor, "list");
            for (int i = 0; i < list.getChildCount(); i++) {
              Spinner spinner = findRole(list.getChildAt(i), "Peran bagian Guitar");
              if (spinner != null) {
                role[0] = spinner;
                level[0] = findLevel(list.getChildAt(i));
                break;
              }
            }
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
          role[0].setSelection(Arrangement.BACKING);
        });
    test.waitForIdleSync();
    Thread.sleep(80);
    touchLevel(test, level[0], .5f);
    SeekBar mix = (SeekBar) test.field(editor, "mix");
    touchLevel(test, mix, .6f);
    final int gain = edited.arrangement.get(49).volume, backing = edited.arrangement.backingVolume;
    test.ok(
        gain > 20 && gain < 80 && backing > 30 && backing < 90,
        "part and backing levels respond to real touch input");
    test.runOnMainSync(editor::finish);
    test.waitForIdleSync();
    Thread.sleep(100);
    test.runOnMainSync(
        () -> {
          test.ok(
              trainer.arrangement.get(49).mode == Arrangement.BACKING
                  && trainer.arrangement.get(49).volume == gain
                  && trainer.arrangement.backingVolume == backing,
              "return to practice reloads saved part role and mix levels");
          test.ok(
              trainer.hits == 2, "adjusting backing levels preserves the learner's existing score");
          Arrangement restored = new Arrangement(trainer.song);
          PartSettings.load(context, stored.getAbsolutePath(), restored);
          test.ok(
              restored.get(49).volume == gain && restored.backingVolume == backing,
              "part preferences survive a fresh arrangement model");
        });
    test.click("Demo");
  }

  private static Spinner findRole(View view, String description) {
    if (view instanceof Spinner
        && view.getContentDescription() != null
        && description.contentEquals(view.getContentDescription())) return (Spinner) view;
    if (view instanceof ViewGroup)
      for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
        Spinner found = findRole(((ViewGroup) view).getChildAt(i), description);
        if (found != null) return found;
      }
    return null;
  }

  private static SeekBar findLevel(View view) {
    if (view instanceof SeekBar) return (SeekBar) view;
    if (view instanceof ViewGroup)
      for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
        SeekBar found = findLevel(((ViewGroup) view).getChildAt(i));
        if (found != null) return found;
      }
    return null;
  }

  private static void touchLevel(SmokeInstrumentation test, SeekBar bar, float fraction) {
    final float[] xy = new float[2];
    test.runOnMainSync(
        () -> {
          int[] location = new int[2];
          bar.getLocationOnScreen(location);
          xy[0] = location[0] + bar.getWidth() * fraction;
          xy[1] = location[1] + bar.getHeight() / 2f;
        });
    long time = SystemClock.uptimeMillis();
    test.pointer(time, MotionEvent.ACTION_DOWN, new float[] {xy[0]}, new float[] {xy[1]});
    test.pointer(time, MotionEvent.ACTION_UP, new float[] {xy[0]}, new float[] {xy[1]});
    test.waitForIdleSync();
  }
}

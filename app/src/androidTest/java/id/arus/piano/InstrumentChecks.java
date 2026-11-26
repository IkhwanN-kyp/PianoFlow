package id.arus.piano;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;

final class InstrumentChecks {
  static void run(SmokeInstrumentation test, MainActivity main) throws Exception {
    Context context = test.getTargetContext();
    List<MidiSong.Note> notes =
        Arrays.asList(
            new MidiSong.Note(60, 90, 0, 0, 1, 4),
            new MidiSong.Note(64, 90, 0, 1, 1, 4),
            new MidiSong.Note(67, 90, 0, 2, 1, 4));
    Map<Integer, String> names = new TreeMap<>();
    for (int i = 0; i < 3; i++) names.put(i, "Track " + (i + 1));
    MidiSong song =
        new MidiSong(
            notes,
            names,
            0,
            Collections.singletonList(new MidiSong.Tempo(0, 0, 500000)),
            Collections.singletonList(new MidiSong.Meter(0, 4, 4)),
            Arrays.asList(
                new MidiSong.Message(0, 0xc0, 0, 0),
                new MidiSong.Message(1.2, 0xc0, 80, 0),
                new MidiSong.Message(1.3, 0xb0, 0, 5)));
    File source = new File(context.getFilesDir(), "instrument-choice-test.mid");
    try (OutputStream output = new FileOutputStream(source)) {
      MidiWriter.write(song, output);
    }
    File stored = new LibraryStore(context).put(song, "instrument-choice-test.mid");
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
      test.runOnMainSync(
          () ->
              ready[0] =
                  trainer.song.tracks.containsValue("Track 3") && trainer.song.notes.size() == 3);
      if (ready[0]) break;
      Thread.sleep(30);
    }
    test.runOnMainSync(
        () -> {
          test.ok(
              trainer.arrangement.parts.size() == 3
                  && trainer.arrangement.parts.stream()
                      .allMatch(p -> p.program == 0 && p.instrument == -1),
              "three same-channel all-piano parts initially follow source MIDI");
          trainer.time = 1;
          trainer.playing = true;
          for (int pitch : new int[] {60, 64, 67}) {
            main.note(0, pitch, 90, true);
            main.note(0, pitch, 0, false);
          }
          trainer.playing = false;
          main.frame();
          test.ok(trainer.hits == 3, "baseline learner chord scored before timbre-only changes");
        });
    Instrumentation.ActivityMonitor monitor =
        test.addMonitor(PartsActivity.class.getName(), null, false);
    test.click("Bagian & pengiring");
    PartsActivity editor = (PartsActivity) test.waitForMonitorWithTimeout(monitor, 5000);
    test.removeMonitor(monitor);
    test.ok(editor != null, "instrument editor opens from toolbar");
    for (int i = 0; i < 100; i++) {
      if (test.field(editor, "trainer") != null) break;
      Thread.sleep(30);
    }
    test.waitForIdleSync();
    Trainer edited = (Trainer) test.field(editor, "trainer");
    select(test, editor, "Track 1", "Grand Piano 1");
    select(test, editor, "Track 2", "Flute");
    select(test, editor, "Track 3", "Guitar (nylon)");
    test.ok(
        edited.arrangement.get(0).instrument == 0
            && edited.arrangement.get(16).instrument == 73
            && edited.arrangement.get(32).instrument == 24,
        "real instrument pickers select piano, flute and guitar independently");
    open(test, editor, "Track 3");
    option(test, editor, "128 instrumen General MIDI…");
    test.runOnMainSync(
        () -> {
          try {
            AlertDialog picker = (AlertDialog) test.field(editor, "instrumentPicker");
            test.ok(
                picker.isShowing() && picker.getListView().getAdapter().getCount() == 129,
                "full chooser contains Follow MIDI plus 128 real GM presets");
            picker.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
          } catch (Exception error) {
            throw new RuntimeException(error);
          }
        });
    test.waitForIdleSync();
    Thread.sleep(80);
    select(test, editor, "Track 2", "Ikuti instrumen MIDI");
    test.ok(
        edited.arrangement.get(16).instrument == -1,
        "Follow MIDI clears a fixed instrument selection");
    select(test, editor, "Track 2", "Flute");
    test.runOnMainSync(
        () -> {
          Button button = described(editor.getWindow().getDecorView(), "Instrumen bagian Track 2");
          test.find((View) button.getParent(), "Dengarkan").performClick();
        });
    SongPlayback preview = null;
    for (int i = 0; i < 200; i++) {
      preview = (SongPlayback) test.field(editor, "playback");
      if (preview != null) {
        int[][] held = (int[][]) test.field(preview, "held");
        int route = preview.routeOf(edited.song.notes.get(1));
        if (held[route][64] > 0) break;
      }
      Thread.sleep(20);
    }
    test.ok(preview != null, "selected flute part is actually auditioned");
    OfflineSynth synth = (OfflineSynth) test.field(editor, "synth");
    long handle = (long) test.field(synth, "handle");
    int previewChannel = 32 + preview.routeOf(edited.song.notes.get(1));
    test.ok(
        FluidNative.programOnChannel(handle, previewChannel) == 73,
        "preview engine uses actual flute preset rather than a renamed piano");
    Thread.sleep(450);
    test.ok(
        FluidNative.programOnChannel(handle, previewChannel) == 73,
        "file program/bank changes cannot replace preview flute");
    test.runOnMainSync(editor::finish);
    test.waitForIdleSync();
    Thread.sleep(100);
    test.runOnMainSync(
        () -> {
          test.ok(
              trainer.arrangement.get(0).instrument == 0
                  && trainer.arrangement.get(16).instrument == 73
                  && trainer.arrangement.get(32).instrument == 24,
              "instrument choices reload on return to practice");
          test.ok(trainer.hits == 3, "instrument-only changes preserve learner score");
          Arrangement restored = new Arrangement(trainer.song);
          PartSettings.load(context, stored.getAbsolutePath(), restored);
          test.ok(
              restored.get(16).instrument == 73 && restored.get(32).instrument == 24,
              "fixed instruments persist in fresh per-song settings");
          trainer.listen = true;
          trainer.time = 1;
          trainer.playing = true;
          main.frame();
          try {
            SongPlayback playback = (SongPlayback) test.field(main, "songPlayback");
            OfflineSynth engine = (OfflineSynth) test.field(main, "synth");
            long nativeHandle = (long) test.field(engine, "handle");
            int[] programs = {0, 73, 24};
            Set<Integer> routes = new HashSet<>();
            for (int i = 0; i < 3; i++) {
              int channel = 32 + playback.routeOf(trainer.song.notes.get(i));
              routes.add(channel);
              test.ok(
                  FluidNative.programOnChannel(nativeHandle, channel) == programs[i],
                  "native playback uses fixed program for track " + (i + 1));
            }
            test.ok(routes.size() == 3, "shared source channel uses three separate native voices");
            engine.program(4);
            for (int i = 0; i < 3; i++)
              test.ok(
                  FluidNative.programOnChannel(
                          nativeHandle, 32 + playback.routeOf(trainer.song.notes.get(i)))
                      == programs[i],
                  "live instrument change does not overwrite track preset");
            engine.program(0);
            trainer.time = .5;
            main.frame();
            trainer.time = 1.5;
            main.frame();
            for (int i = 0; i < 3; i++)
              test.ok(
                  FluidNative.programOnChannel(
                          nativeHandle, 32 + playback.routeOf(trainer.song.notes.get(i)))
                      == programs[i],
                  "seek restores fixed track instrument past source program change");
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
          trainer.playing = false;
          main.frame();
        });
    long probe = FluidNative.create(22050, 128);
    try {
      test.ok(
          FluidNative.load(probe, new File(context.getFilesDir(), "TimGM6mb.sf2").getPath()) >= 0,
          "extended channel engine loads redistributable GM bank");
      for (int channel : new int[] {57, 255}) {
        FluidNative.panic(probe);
        FluidNative.message(probe, 0xc000 | channel, 73, 0);
        FluidNative.message(probe, 0x9000 | channel, 60, 100);
        short[] pcm = new short[4096];
        FluidNative.render(probe, pcm, 2048);
        long energy = 0;
        for (short value : pcm) energy += Math.abs((int) value);
        test.ok(
            FluidNative.programOnChannel(probe, channel) == 73 && energy > 0,
            "high private channel is melodic, unaliased and produces flute audio: " + channel);
      }
    } finally {
      FluidNative.destroy(probe);
    }
    test.click("Demo");
  }

  private static void select(
      SmokeInstrumentation test, PartsActivity editor, String track, String label)
      throws Exception {
    open(test, editor, track);
    option(test, editor, label);
  }

  private static void open(SmokeInstrumentation test, PartsActivity editor, String track)
      throws Exception {
    test.runOnMainSync(
        () -> {
          Button button = described(editor.getWindow().getDecorView(), "Instrumen bagian " + track);
          test.ok(button != null, "part has an actionable instrument button: " + track);
          button.performClick();
        });
    test.waitForIdleSync();
    Thread.sleep(80);
  }

  private static void option(SmokeInstrumentation test, PartsActivity editor, String text)
      throws Exception {
    test.runOnMainSync(
        () -> {
          try {
            AlertDialog picker = (AlertDialog) test.field(editor, "instrumentPicker");
            ListView list = picker.getListView();
            int selected = -1;
            for (int i = 0; i < list.getAdapter().getCount(); i++)
              if (text.equals(list.getAdapter().getItem(i).toString())) {
                selected = i;
                break;
              }
            test.ok(
                picker.isShowing() && selected >= 0,
                "instrument menu contains real choice: " + text);
            list.performItemClick(
                list.getChildAt(selected - list.getFirstVisiblePosition()),
                selected,
                list.getAdapter().getItemId(selected));
          } catch (Exception error) {
            throw new RuntimeException(error);
          }
        });
    test.waitForIdleSync();
    Thread.sleep(80);
  }

  private static Button described(View view, String description) {
    if (view instanceof Button
        && view.getContentDescription() != null
        && description.contentEquals(view.getContentDescription())) return (Button) view;
    if (view instanceof ViewGroup)
      for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
        Button found = described(((ViewGroup) view).getChildAt(i), description);
        if (found != null) return found;
      }
    return null;
  }
}

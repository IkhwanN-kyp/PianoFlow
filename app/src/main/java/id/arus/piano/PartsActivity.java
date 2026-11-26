package id.arus.piano;

import android.app.*;
import android.content.*;
import android.graphics.Typeface;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.concurrent.*;

/** Per-part roles and levels with a genuine, isolated offline audition. */
public final class PartsActivity extends Activity {
  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private final Handler handler = new Handler(Looper.getMainLooper());
  private Trainer trainer;
  private AlertDialog instrumentPicker;
  private OfflineSynth synth;
  private SongPlayback playback;
  private TextView info, mixText;
  private SeekBar mix;
  private ListView list;
  private BaseAdapter adapter;
  private String path;
  private int preview = -1, pending = -1;
  private long clock;
  private double start, end;
  private boolean loadingAudio = false, closed = false;

  @Override
  public void onCreate(Bundle state) {
    Ui.theme(this);
    super.onCreate(state);
    Ui.immersive(this);
    path = getIntent().getStringExtra("song");
    if (path == null) path = "";
    LinearLayout root = Ui.column(this), header = Ui.row(this);
    header.addView(Ui.button(this, "‹ Kembali", "‹ Back", this::finish));
    header.addView(Ui.text(this, tr("Bagian & pengiring", "Parts & backing"), 23));
    root.addView(header);
    TextView explanation =
        Ui.text(
            this,
            tr(
                "Pilih bagian yang Anda mainkan. Bagian lain bisa menjadi pengiring otomatis atau"
                    + " dimatikan. Tempo, Tunggu dan loop berlaku bersama.",
                "Choose the parts you play. Other parts can play automatically or be muted. Tempo,"
                    + " Wait and loops stay synchronized."),
            15);
    root.addView(explanation);
    info = Ui.text(this, tr("Memuat bagian MIDI…", "Loading MIDI parts…"), 13);
    info.setTextColor(Ui.muted(this));
    root.addView(info);
    list = new ListView(this);
    list.setDivider(null);
    list.setDividerHeight(Ui.dp(this, 8));
    root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
    LinearLayout footer = Ui.row(this);
    mixText = Ui.text(this, tr("Volume pengiring · 70%", "Backing level · 70%"), 14);
    footer.addView(mixText);
    mix = new SeekBar(this);
    mix.setMax(100);
    mix.setProgress(70);
    mix.setEnabled(false);
    footer.addView(mix, new LinearLayout.LayoutParams(0, Ui.dp(this, 44), 1));
    footer.addView(Ui.button(this, "Hentikan preview", "Stop preview", this::stopPreview));
    root.addView(footer);
    setContentView(root);
    worker.execute(
        () -> {
          try {
            MidiSong song;
            if (path.isEmpty()) song = MidiSong.exercise();
            else
              try (InputStream input = new FileInputStream(path)) {
                song = MidiSong.read(input);
              }
            Trainer loaded = new Trainer();
            loaded.load(song);
            PartSettings.load(this, path, loaded.arrangement);
            runOnUiThread(
                () -> {
                  if (closed) return;
                  trainer = loaded;
                  bind();
                });
          } catch (Exception error) {
            runOnUiThread(
                () -> {
                  if (!closed) {
                    info.setText(tr("Tidak dapat membaca lagu.", "Unable to read this song."));
                    Ui.error(this, error);
                  }
                });
          }
        });
  }

  private String tr(String id, String en) {
    return Ui.tr(this, id, en);
  }

  private void bind() {
    boolean shared = false;
    for (Arrangement.Part part : trainer.arrangement.parts) shared |= part.sharedChannel;
    info.setText(
        tr(
                "Dengarkan mengisolasi satu bagian hingga 12 detik. Pilih instrumen pada setiap"
                    + " bagian, atau ikuti program MIDI asal.",
                "Listen isolates one part for up to 12 seconds. Choose an instrument on each part,"
                    + " or follow its MIDI program.")
            + (shared
                ? tr(
                    " Instrumen pilihan memiliki suara terpisah, termasuk jika channel asal sama."
                        + " Pedal dan pitch bend berkas tetap mengikuti channel asal.",
                    " Fixed instruments have separate voices, including on shared source channels."
                        + " File pedal and pitch bend still follow the source channel.")
                : ""));
    adapter =
        new BaseAdapter() {
          public int getCount() {
            return trainer.arrangement.parts.size();
          }

          public Object getItem(int position) {
            return trainer.arrangement.parts.get(position);
          }

          public long getItemId(int position) {
            return trainer.arrangement.parts.get(position).key;
          }

          public View getView(int position, View recycled, ViewGroup parent) {
            return card(trainer.arrangement.parts.get(position));
          }
        };
    list.setAdapter(adapter);
    mix.setProgress(trainer.arrangement.backingVolume);
    mix.setEnabled(true);
    updateMix();
    mix.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          public void onProgressChanged(SeekBar bar, int progress, boolean user) {
            if (user) {
              stopPreview();
              trainer.arrangement.backingVolume = progress;
              PartSettings.saveMix(PartsActivity.this, path, progress);
              updateMix();
            }
          }

          public void onStartTrackingTouch(SeekBar bar) {}

          public void onStopTrackingTouch(SeekBar bar) {}
        });
  }

  private void updateMix() {
    mixText.setText(
        tr("Volume pengiring · ", "Backing level · ") + trainer.arrangement.backingVolume + "%");
  }

  private Spinner choice(String[] values, int selected, int width) {
    Spinner spinner = new Spinner(this);
    spinner.setAdapter(
        new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, values));
    spinner.setSelection(selected);
    spinner.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(this, width), Ui.dp(this, 48)));
    return spinner;
  }

  private View card(Arrangement.Part part) {
    LinearLayout box = Ui.column(this);
    box.setBackgroundColor(Ui.panel(this));
    TextView title =
        Ui.text(
            this, part.name + " · Track " + (part.track + 1) + " · Ch " + (part.channel + 1), 17);
    title.setTypeface(null, Typeface.BOLD);
    box.addView(title);
    TextView subtitle =
        Ui.text(
            this,
            String.format(
                    java.util.Locale.US,
                    "GM %03d · %s",
                    part.selectedProgram() + 1,
                    GeneralMidi.NAMES[part.selectedProgram()])
                + (part.instrument < 0
                    ? tr(" · asal MIDI", " · MIDI source")
                    : tr(" · pilihan Anda", " · your selection"))
                + " · "
                + part.noteCount
                + tr(" not", " notes")
                + (part.changingProgram && part.instrument < 0
                    ? tr(" · program berubah di dalam lagu", " · program changes within song")
                    : ""),
            13);
    subtitle.setTextColor(Ui.muted(this));
    box.addView(subtitle);
    String selected =
        part.instrument < 0
            ? tr("Ikuti MIDI · ", "Follow MIDI · ") + GeneralMidi.NAMES[part.program]
            : GeneralMidi.NAMES[part.instrument];
    Button instrument =
        Ui.button(
            this,
            tr("Instrumen · ", "Instrument · ") + selected + " ▾",
            tr("Instrumen · ", "Instrument · ") + selected + " ▾",
            () -> instruments(part));
    instrument.setContentDescription(tr("Instrumen bagian ", "Part instrument ") + part.name);
    instrument.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
    LinearLayout.LayoutParams instrumentSize = new LinearLayout.LayoutParams(-1, Ui.dp(this, 44));
    instrumentSize.setMargins(Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4));
    instrument.setLayoutParams(instrumentSize);
    box.addView(instrument);
    HorizontalScrollView scroll = new HorizontalScrollView(this);
    LinearLayout row = Ui.row(this);
    Spinner role =
        choice(
            new String[] {
              tr("Saya mainkan", "I play"),
              tr("Pengiring otomatis", "Automatic backing"),
              tr("Matikan", "Mute")
            },
            part.mode,
            210);
    role.setContentDescription(tr("Peran bagian ", "Part role ") + part.name);
    row.addView(role);
    Spinner hand =
        choice(
            new String[] {
              tr("Otomatis · C4", "Auto · C4"),
              tr("Tangan kiri", "Left hand"),
              tr("Tangan kanan", "Right hand")
            },
            Math.max(0, part.hand),
            180);
    hand.setContentDescription(tr("Tangan bagian ", "Part hand ") + part.name);
    hand.setEnabled(part.mode == Arrangement.PLAY);
    row.addView(hand);
    TextView volume = Ui.text(this, tr("Volume ", "Level ") + part.volume + "%", 13);
    row.addView(volume);
    SeekBar level = new SeekBar(this);
    level.setMax(100);
    level.setProgress(part.volume);
    level.setContentDescription(tr("Volume bagian ", "Part level ") + part.name);
    row.addView(level, new LinearLayout.LayoutParams(Ui.dp(this, 200), Ui.dp(this, 48)));
    role.setOnItemSelectedListener(
        new AdapterView.OnItemSelectedListener() {
          public void onNothingSelected(AdapterView<?> parent) {}

          public void onItemSelected(AdapterView<?> parent, View view, int index, long id) {
            if (index != part.mode) {
              stopPreview();
              part.mode = index;
              hand.setEnabled(index == Arrangement.PLAY);
              PartSettings.savePart(PartsActivity.this, path, part);
            }
          }
        });
    hand.setOnItemSelectedListener(
        new AdapterView.OnItemSelectedListener() {
          public void onNothingSelected(AdapterView<?> parent) {}

          public void onItemSelected(AdapterView<?> parent, View view, int index, long id) {
            if (index != part.hand) {
              stopPreview();
              part.hand = index;
              PartSettings.savePart(PartsActivity.this, path, part);
            }
          }
        });
    level.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          public void onProgressChanged(SeekBar bar, int progress, boolean user) {
            if (user) {
              stopPreview();
              part.volume = progress;
              volume.setText(tr("Volume ", "Level ") + progress + "%");
              PartSettings.savePart(PartsActivity.this, path, part);
            }
          }

          public void onStartTrackingTouch(SeekBar bar) {}

          public void onStopTrackingTouch(SeekBar bar) {}
        });
    row.addView(
        Ui.button(
            this,
            preview == part.key ? "Hentikan" : "Dengarkan",
            preview == part.key ? "Stop" : "Listen",
            () -> {
              if (preview == part.key) stopPreview();
              else audition(part.key);
            }));
    scroll.addView(row);
    box.addView(scroll);
    return box;
  }

  private void instruments(Arrangement.Part part) {
    if (instrumentPicker != null) instrumentPicker.dismiss();
    String[] labels = {
      tr("Ikuti instrumen MIDI", "Follow MIDI instrument"),
      "Grand Piano 1",
      "Flute",
      "Guitar (nylon)",
      "Electric Piano",
      "Organ",
      "Strings",
      tr("128 instrumen General MIDI…", "128 General MIDI instruments…")
    };
    int[] programs = {-1, 0, 73, 24, 4, 16, 48};
    instrumentPicker =
        new AlertDialog.Builder(this)
            .setTitle(tr("Instrumen · ", "Instrument · ") + part.name)
            .setItems(
                labels,
                (dialog, index) -> {
                  if (index < programs.length) selectInstrument(part, programs[index]);
                  else {
                    String[] all = new String[129];
                    all[0] = tr("Ikuti instrumen MIDI", "Follow MIDI instrument");
                    for (int i = 0; i < 128; i++)
                      all[i + 1] =
                          String.format(
                              java.util.Locale.US, "%03d · %s", i + 1, GeneralMidi.NAMES[i]);
                    instrumentPicker =
                        new AlertDialog.Builder(this)
                            .setTitle(
                                tr("128 instrumen General MIDI", "128 General MIDI instruments"))
                            .setSingleChoiceItems(
                                all,
                                part.instrument + 1,
                                (choices, which) -> {
                                  selectInstrument(part, which - 1);
                                  choices.dismiss();
                                })
                            .setNegativeButton(tr("Batal", "Cancel"), null)
                            .create();
                    instrumentPicker.show();
                  }
                })
            .setNegativeButton(tr("Batal", "Cancel"), null)
            .create();
    instrumentPicker.show();
  }

  private void selectInstrument(Arrangement.Part part, int program) {
    stopPreview();
    part.instrument = Math.max(-1, Math.min(127, program));
    PartSettings.savePart(this, path, part);
    adapter.notifyDataSetChanged();
    info.setText(
        tr("Instrumen tersimpan · ", "Instrument saved · ")
            + part.name
            + " · "
            + GeneralMidi.NAMES[part.selectedProgram()]
            + tr(". Tekan Dengarkan untuk mencobanya.", ". Tap Listen to audition it."));
  }

  private void audition(int key) {
    stopPreview();
    pending = key;
    if (synth != null) {
      begin(key);
      return;
    }
    if (loadingAudio) return;
    loadingAudio = true;
    info.setText(tr("Menyiapkan suara offline…", "Preparing offline audio…"));
    worker.execute(
        () -> {
          OfflineSynth engine = null;
          try {
            engine = new OfflineSynth(getApplicationContext());
            SharedPreferences settings = getSharedPreferences("settings", 0);
            String bank = settings.getString("soundfont", "");
            if (!bank.isEmpty() && new File(bank).isFile() && !engine.importBank(new File(bank)))
              throw new IOException("SoundFont cannot be loaded");
            engine.configure(
                settings.getInt("polyphony", 128), settings.getBoolean("reverb", true));
            OfflineSynth ready = engine;
            runOnUiThread(
                () -> {
                  loadingAudio = false;
                  if (closed) {
                    ready.close();
                    return;
                  }
                  synth = ready;
                  if (pending >= 0) begin(pending);
                  else
                    info.setText(
                        tr(
                            "Suara siap. Pilih Dengarkan pada bagian yang ingin diperiksa.",
                            "Audio ready. Choose Listen on a part to audition it."));
                });
          } catch (Exception error) {
            if (engine != null) engine.close();
            runOnUiThread(
                () -> {
                  loadingAudio = false;
                  if (!closed) Ui.error(this, error);
                });
          }
        });
  }

  private void begin(int key) {
    Arrangement.Part part = trainer.arrangement.get(key);
    if (part == null) return;
    preview = key;
    pending = -1;
    start = Math.max(0, part.first - .1);
    end = Math.min(trainer.song.duration, start + 12);
    clock = SystemClock.elapsedRealtime();
    playback =
        new SongPlayback(trainer, (channel, kind, a, b) -> synth.midi(32 + channel, kind, a, b));
    playback.solo(key);
    info.setText(
        tr("Preview bagian · ", "Part preview · ")
            + part.name
            + " · "
            + GeneralMidi.NAMES[part.selectedProgram()]);
    adapter.notifyDataSetChanged();
    handler.post(tick);
  }

  private final Runnable tick =
      new Runnable() {
        public void run() {
          if (preview < 0 || closed) return;
          double time = start + (SystemClock.elapsedRealtime() - clock) / 1000.0;
          if (time > end) {
            stopPreview();
            return;
          }
          try {
            playback.sync(time, true, true);
            handler.postDelayed(this, 10);
          } catch (IllegalStateException error) {
            stopPreview();
            Ui.error(PartsActivity.this, error);
          }
        }
      };

  private void stopPreview() {
    pending = -1;
    handler.removeCallbacks(tick);
    if (playback != null) {
      playback.stop();
      playback = null;
    }
    if (synth != null) synth.silence();
    boolean changed = preview >= 0;
    preview = -1;
    if (changed && adapter != null) {
      info.setText(
          tr(
              "Preview dihentikan. Pilihan disimpan otomatis.",
              "Preview stopped. Your choices are saved automatically."));
      adapter.notifyDataSetChanged();
    }
  }

  @Override
  protected void onPause() {
    super.onPause();
    stopPreview();
  }

  @Override
  protected void onDestroy() {
    closed = true;
    if (instrumentPicker != null) instrumentPicker.dismiss();
    stopPreview();
    if (synth != null) synth.close();
    worker.shutdownNow();
    super.onDestroy();
  }
}

package id.arus.piano;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity implements PianoView.Host, MidiHub.Listener {
  private final Trainer trainer = new Trainer();
  private PianoView piano;
  private MidiHub midi;
  private OfflineSynth synth;
  private LibraryStore library;
  private TextView title, status, tempoLabel;
  private Button play, mode, waitButton, handButton, audioButton, outButton, importButton;
  private SeekBar position, tempoSlider;
  private LinearLayout timeline, songControls;
  private Button freeButton, clearLive, partsButton;
  private SongPlayback songPlayback;
  private MidiSong playbackSong;
  private boolean freePlay = false;
  private final ExecutorService loader = Executors.newSingleThreadExecutor();
  private final ExecutorService updateChecks = Executors.newSingleThreadExecutor();
  private final boolean[][] hardware = new boolean[16][128];
  private final int[] touchCount = new int[128];
  private final boolean[] sent = new boolean[128];
  private boolean audio = true,
      guide = false,
      dragging = false,
      destroyed = false,
      external = false,
      pedal = false,
      followPrograms = false;
  private int outputChannel = 0, loadGeneration = 0, program = 0;
  private volatile boolean routeAudio = true, resumed = false;
  private final ArrayList<MidiSong.Note> activeNotes = new ArrayList<>();
  private int songCursor = 0;
  private double lastTime = -1;
  private MidiSong lastSong;
  private long lastUi = 0;
  private String name = "Latihan tangga nada · buatan sendiri", currentPath = "";
  private final Map<Integer, Integer> computerKeys = new HashMap<>();

  private int dp(float x) {
    return Ui.dp(this, x);
  }

  private String tr(String id, String en) {
    return Ui.tr(this, id, en);
  }

  private SharedPreferences settings() {
    return getSharedPreferences("settings", 0);
  }

  @Override
  public void onCreate(Bundle saved) {
    Ui.theme(this);
    super.onCreate(saved);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    Ui.immersive(this);
    library = new LibraryStore(this);
    synth = new OfflineSynth(this);
    trainer.load(MidiSong.exercise());
    program = settings().getInt("program", 0);
    synth.program(program);
    synth.configure(settings().getInt("polyphony", 128), settings().getBoolean("reverb", true));
    freePlay = settings().getBoolean("freePlay", false);
    buildUi();
    midi = new MidiHub(this, this);
    String bank = settings().getString("soundfont", "");
    if (!bank.isEmpty() && new File(bank).isFile())
      loader.execute(
          () -> {
            if (!synth.importBank(new File(bank)))
              runOnUiThread(
                  () ->
                      status(
                          tr(
                              "SoundFont kustom gagal; memakai bank bawaan",
                              "Custom SoundFont failed; using bundled bank")));
          });
    String path = getPreferences(0).getString("currentSong", null),
        uri = getPreferences(0).getString("uri", null);
    if (path != null && new File(path).isFile()) load(Uri.fromFile(new File(path)), false);
    else if (uri != null) load(Uri.parse(uri), false);
    automaticUpdate();
  }

  private TextView label(String s, int size) {
    return Ui.text(this, Ui.t(this, s), size);
  }

  private LinearLayout row() {
    return Ui.row(this);
  }

  private Button button(String s, Runnable action) {
    return Ui.button(
        this,
        s,
        Ui.t(this, s),
        () -> {
          action.run();
          if (piano != null) piano.invalidate();
        });
  }

  private void buildUi() {
    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setBackgroundColor(Ui.bg(this));
    root.setOnApplyWindowInsetsListener(
        (v, insets) -> {
          if (Build.VERSION.SDK_INT >= 28 && insets.getDisplayCutout() != null) {
            DisplayCutout cut = insets.getDisplayCutout();
            root.setPadding(
                cut.getSafeInsetLeft(),
                cut.getSafeInsetTop(),
                cut.getSafeInsetRight(),
                cut.getSafeInsetBottom());
          }
          return insets;
        });
    LinearLayout header = row();
    header.setPadding(dp(12), dp(3), dp(12), 0);
    TextView brand = label("PIANOFLOW", 18);
    brand.setTextColor(Ui.accent(this));
    brand.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    header.addView(brand, new LinearLayout.LayoutParams(dp(150), dp(48)));
    title = label(name, 14);
    title.setSingleLine(true);
    title.setEllipsize(android.text.TextUtils.TruncateAt.END);
    header.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1));
    importButton = button(tr("+ Impor MIDI / XML", "+ Import MIDI / XML"), () -> pick(10, "*/*"));
    header.addView(importButton);
    header.addView(
        button(
            tr("Konversi", "Convert"),
            () -> {
              trainer.playing = false;
              quiet();
              startActivityForResult(new Intent(this, ConverterActivity.class), 40);
            }));
    header.addView(button(tr("Perpustakaan", "Library"), this::libraryMenu));
    header.addView(button(tr("Pengaturan", "Settings"), this::settingsMenu));
    root.addView(header);
    LinearLayout sub = row();
    sub.setPadding(dp(14), 0, dp(12), 0);
    status =
        label(
            tr("Offline siap · Yamaha P-145 USB MIDI", "Offline ready · Yamaha P-145 USB MIDI"),
            11);
    status.setTextColor(Ui.muted(this));
    status.setSingleLine(true);
    status.setEllipsize(android.text.TextUtils.TruncateAt.END);
    sub.addView(status, new LinearLayout.LayoutParams(0, dp(42), 1));
    partsButton = button(tr("Bagian & pengiring", "Parts & backing"), this::partsMenu);
    sub.addView(partsButton);
    freeButton = button(tr("Bermain bebas", "Free play"), () -> setFreePlay(!freePlay));
    sub.addView(freeButton);
    sub.addView(button(tr("Label tuts", "Key labels"), this::keyLabels));
    sub.addView(button("MIDI / USB", () -> midi.menu()));
    sub.addView(button(tr("Instrumen", "Instrument"), this::instruments));
    root.addView(sub);
    piano = new PianoView(this, trainer, this);
    piano.displayMode = settings().getInt("view", 0);
    piano.labelMode = NoteLabels.valid(settings().getInt("keyLabels", NoteLabels.LETTERS));
    piano.setFreePlay(freePlay);
    root.addView(piano, new LinearLayout.LayoutParams(-1, 0, 1));
    timeline = row();
    timeline.setPadding(dp(12), 0, dp(12), 0);
    timeline.addView(button("↺ Awal", () -> seek(trainer.loop ? trainer.loopA : 0)));
    position = new SeekBar(this);
    position.setMax(10000);
    position.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          public void onProgressChanged(SeekBar s, int p, boolean user) {
            if (user) seek(p / 10000.0 * trainer.song.duration);
          }

          public void onStartTrackingTouch(SeekBar s) {
            dragging = true;
          }

          public void onStopTrackingTouch(SeekBar s) {
            dragging = false;
          }
        });
    timeline.addView(position, new LinearLayout.LayoutParams(0, dp(38), 1));
    timeline.addView(
        button(
            "Loop A",
            () -> {
              trainer.loopA = trainer.time;
              trainer.loop = false;
              status(
                  String.format(Locale.US, "A = %.2f s · ", trainer.loopA)
                      + tr("geser lalu tentukan B", "seek and set B"));
            }));
    timeline.addView(
        button(
            "Loop B",
            () -> {
              if (trainer.time > trainer.loopA + .05) {
                trainer.loopB = trainer.time;
                trainer.loop = true;
                seek(trainer.loopA);
                status(
                    tr("Loop aktif · skor per putaran", "Loop enabled · score resets each pass"));
              } else status(tr("B harus sesudah A", "B must be after A"));
            }));
    timeline.addView(button("Loop mati", () -> trainer.loop = false));
    root.addView(timeline);
    HorizontalScrollView scroll = new HorizontalScrollView(this);
    scroll.setHorizontalScrollBarEnabled(true);
    LinearLayout controls = row();
    controls.setPadding(dp(12), 0, dp(12), dp(6));
    songControls = row();
    controls.addView(songControls);
    play =
        button(
            "▶ Mulai",
            () -> {
              if (!trainer.playing && !trainer.listen && !trainer.hasPracticeNotes()) {
                status(
                    tr(
                        "Pilih setidaknya satu bagian Saya mainkan di Bagian & pengiring.",
                        "Choose at least one I play part in Parts & backing."));
                return;
              }
              trainer.playing = !trainer.playing;
              if (trainer.playing && trainer.time >= trainer.song.duration)
                seek(trainer.loop ? trainer.loopA : 0);
              if (!trainer.playing) quiet();
            });
    songControls.addView(play);
    mode =
        button(
            trainer.listen ? "Dengarkan" : "Latihan",
            () -> {
              quiet();
              trainer.listen = !trainer.listen;
              trainer.seek(trainer.time);
              mode.setText(Ui.t(this, trainer.listen ? "Dengarkan" : "Latihan"));
            });
    songControls.addView(mode);
    waitButton =
        button(
            trainer.wait ? "Tunggu: ON" : "Tunggu: OFF",
            () -> {
              trainer.wait = !trainer.wait;
              waitButton.setText(Ui.t(this, trainer.wait ? "Tunggu: ON" : "Tunggu: OFF"));
            });
    songControls.addView(waitButton);
    handButton = button("Dua tangan", this::hands);
    songControls.addView(handButton);
    tempoLabel = label("100%", 12);
    songControls.addView(tempoLabel, new LinearLayout.LayoutParams(dp(42), dp(40)));
    SeekBar tempo = new SeekBar(this);
    tempoSlider = tempo;
    tempo.setMax(175);
    tempo.setProgress((int) (trainer.speed * 100) - 25);
    tempo.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          public void onProgressChanged(SeekBar s, int p, boolean user) {
            trainer.speed = (p + 25) / 100.0;
            tempoLabel.setText(String.format(Locale.US, "%d%%", p + 25));
          }

          public void onStartTrackingTouch(SeekBar s) {}

          public void onStopTrackingTouch(SeekBar s) {}
        });
    songControls.addView(tempo, new LinearLayout.LayoutParams(dp(100), dp(40)));
    songControls.addView(
        button(
            tr("Tampilan", "View"),
            () ->
                new AlertDialog.Builder(this)
                    .setTitle(tr("Tampilan latihan", "Learning view"))
                    .setSingleChoiceItems(
                        new String[] {"Falling notes", tr("Partitur", "Sheet music"), "Hybrid"},
                        piano.displayMode,
                        (d, w) -> {
                          piano.displayMode = w;
                          settings().edit().putInt("view", w).apply();
                          piano.invalidate();
                          d.dismiss();
                        })
                    .show()));
    audioButton = button("Audio: ON", this::audioMenu);
    controls.addView(audioButton);
    outButton = button("Panduan OUT: OFF", this::outputOptions);
    controls.addView(outButton);
    controls.addView(
        button(
            "Sustain",
            () -> {
              pedal = !pedal;
              synth.midi(16, 0xb0, 64, pedal ? 127 : 0);
              if (external) midi.sendMessage(0xb0 | outputChannel, 64, pedal ? 127 : 0);
              status("Sustain " + (pedal ? "ON" : "OFF"));
            }));
    songControls.addView(button("PDF", () -> pickPdf()));
    songControls.addView(
        button(
            "Demo",
            () -> {
              setFreePlay(false);
              loadGeneration++;
              importButton.setEnabled(true);
              quiet();
              trainer.load(MidiSong.exercise());
              currentPath = "";
              name = tr("Latihan tangga nada · buatan sendiri", "Original scale exercise");
              title.setText(name);
              getPreferences(0).edit().remove("uri").remove("currentSong").apply();
            }));
    clearLive = button(tr("Hentikan & bersihkan", "Stop & clear"), this::resetLiveInput);
    controls.addView(clearLive);
    scroll.addView(controls);
    root.addView(scroll);
    setContentView(root);
    updateLearningControls();
  }

  private void setFreePlay(boolean enabled) {
    if (freePlay == enabled) return;
    saveSession();
    trainer.playing = false;
    resetLiveInput();
    freePlay = enabled;
    piano.setFreePlay(enabled);
    settings().edit().putBoolean("freePlay", enabled).apply();
    updateLearningControls();
    status(
        enabled
            ? tr(
                "Mainkan tuts layar atau piano MIDI. Balok mengikuti lama tuts ditahan.",
                "Play the screen keys or MIDI piano. Bars follow how long you hold each key.")
            : tr("Lagu siap dilanjutkan", "Song ready to resume"));
  }

  private void updateLearningControls() {
    partsButton.setVisibility(freePlay ? View.GONE : View.VISIBLE);
    timeline.setVisibility(freePlay ? View.GONE : View.VISIBLE);
    songControls.setVisibility(freePlay ? View.GONE : View.VISIBLE);
    outButton.setVisibility(freePlay ? View.GONE : View.VISIBLE);
    clearLive.setVisibility(freePlay ? View.VISIBLE : View.GONE);
    freeButton.setText(
        tr(
            freePlay ? "Kembali ke lagu" : "Bermain bebas",
            freePlay ? "Back to song" : "Free play"));
    title.setText(freePlay ? tr("Bermain bebas", "Free play") : name);
  }

  private void resetLiveInput() {
    if (piano != null) piano.releaseTouches();
    for (boolean[] channel : hardware) Arrays.fill(channel, false);
    Arrays.fill(touchCount, 0);
    computerKeys.clear();
    quiet();
    if (piano != null) {
      Arrays.fill(piano.down, false);
      piano.live.clear();
      piano.invalidate();
    }
  }

  private void keyLabels() {
    new AlertDialog.Builder(this)
        .setTitle(tr("Label tuts · Do = C", "Key labels · Do = C"))
        .setSingleChoiceItems(
            new String[] {
              tr("Huruf · C D E F G A B", "Letters · C D E F G A B"),
              tr("Angka · 1 2 3 4 5 6 7", "Numbers · 1 2 3 4 5 6 7"),
              "Do Re Mi Fa Sol La Si",
              tr("Tanpa label", "No labels")
            },
            piano.labelMode,
            (dialog, which) -> {
              piano.labelMode = NoteLabels.valid(which);
              settings().edit().putInt("keyLabels", piano.labelMode).apply();
              piano.invalidate();
              dialog.dismiss();
            })
        .setNegativeButton(tr("Batal", "Cancel"), null)
        .show();
  }

  private void partsMenu() {
    trainer.playing = false;
    quiet();
    for (Arrangement.Part p : trainer.arrangement.parts)
      if (p.hand < 0) {
        int role = trainer.roles.getOrDefault(p.track, 0);
        p.hand = role == 3 ? 0 : role;
        if (role == 3) p.mode = Arrangement.MUTE;
      }
    PartSettings.save(this, currentPath, trainer.arrangement);
    startActivity(
        new Intent(this, PartsActivity.class).putExtra("song", currentPath).putExtra("name", name));
  }

  private void hands() {
    new AlertDialog.Builder(this)
        .setTitle(tr("Tangan yang dilatih", "Practice hand"))
        .setItems(
            new String[] {
              tr("Dua tangan", "Both hands"),
              tr("Kiri · biru", "Left · blue"),
              tr("Kanan · hijau", "Right · mint"),
              tr("Atur tangan per track", "Assign hands by track")
            },
            (d, w) -> {
              if (w < 3) {
                trainer.hand = w;
                seek(trainer.time);
                handButton.setText(Ui.t(this, w == 0 ? "Dua tangan" : w == 1 ? "Kiri" : "Kanan"));
              } else tracks();
            })
        .show();
  }

  private void tracks() {
    List<Integer> ids = new ArrayList<>(trainer.song.tracks.keySet());
    String[] names = new String[ids.size()];
    for (int i = 0; i < ids.size(); i++) {
      int id = ids.get(i);
      names[i] =
          trainer.song.tracks.get(id)
              + " · "
              + new String[] {
                    tr("otomatis C4", "auto C4"),
                    tr("kiri", "left"),
                    tr("kanan", "right"),
                    tr("abaikan", "ignore")
                  }
                  [trainer.roles.getOrDefault(id, 0)];
    }
    new AlertDialog.Builder(this)
        .setTitle(tr("Pemisahan tangan per track", "Hands by track"))
        .setItems(
            names,
            (d, w) -> {
              int id = ids.get(w);
              new AlertDialog.Builder(this)
                  .setSingleChoiceItems(
                      new String[] {
                        tr("Otomatis: nada < C4 = kiri", "Automatic: pitch < C4 = left"),
                        tr("Kiri", "Left"),
                        tr("Kanan", "Right"),
                        tr("Abaikan track", "Ignore track")
                      },
                      trainer.roles.getOrDefault(id, 0),
                      (dd, r) -> {
                        trainer.roles.put(id, r);
                        for (Arrangement.Part part : trainer.arrangement.parts)
                          if (part.track == id) {
                            part.hand = r == 3 ? 0 : r;
                            if (r == 3) part.mode = Arrangement.MUTE;
                            else if (part.mode == Arrangement.MUTE) part.mode = Arrangement.PLAY;
                          }
                        PartSettings.save(this, currentPath, trainer.arrangement);
                        seek(trainer.time);
                        dd.dismiss();
                      })
                  .show();
            })
        .setNegativeButton(tr("Selesai", "Done"), null)
        .show();
  }

  private void pick(int request, String mime) {
    Intent i =
        new Intent(Intent.ACTION_OPEN_DOCUMENT).setType(mime).addCategory(Intent.CATEGORY_OPENABLE);
    i.addFlags(
        Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
    try {
      startActivityForResult(i, request);
    } catch (ActivityNotFoundException e) {
      Ui.error(this, e);
    }
  }

  private void pickPdf() {
    trainer.playing = false;
    quiet();
    Intent i =
        new Intent(Intent.ACTION_CREATE_DOCUMENT)
            .setType("application/pdf")
            .addCategory(Intent.CATEGORY_OPENABLE)
            .putExtra(Intent.EXTRA_TITLE, "PianoFlow-score.pdf");
    startActivityForResult(i, 30);
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (result != RESULT_OK || data == null || data.getData() == null) return;
    Uri uri = data.getData();
    if (request == 10 || request == 40) {
      if (request == 40) {
        trainer.listen = false;
        mode.setText(Ui.t(this, "Latihan"));
      }
      try {
        getContentResolver()
            .takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
      } catch (Exception ignored) {
      }
      load(uri, true);
    }
    if (request == 21) importSoundfont(uri);
    if (request == 30) {
      MidiSong song = trainer.song;
      Map<Integer, Integer> roles = new HashMap<>(trainer.roles);
      Arrangement arrangement = new Arrangement(song);
      arrangement.copyFrom(trainer.arrangement);
      loader.execute(
          () -> {
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
              Trainer snapshot = new Trainer();
              snapshot.load(song);
              snapshot.roles.putAll(roles);
              snapshot.arrangement.copyFrom(arrangement);
              ScorePdf.write(this, snapshot, out);
              runOnUiThread(() -> status(tr("PDF berhasil diekspor", "PDF exported")));
            } catch (Exception e) {
              runOnUiThread(() -> Ui.error(this, e));
            }
          });
    }
  }

  private String fileName(Uri uri) {
    if ("file".equals(uri.getScheme())) return new File(uri.getPath()).getName();
    try (Cursor c =
        getContentResolver()
            .query(uri, new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
      if (c != null && c.moveToFirst()) return c.getString(0);
    } catch (Exception ignored) {
    }
    return "MIDI";
  }

  private void load(Uri uri, boolean store) {
    int generation = ++loadGeneration;
    trainer.playing = false;
    quiet();
    importButton.setEnabled(false);
    status(tr("Membaca MIDI / MusicXML…", "Reading MIDI / MusicXML…"));
    loader.execute(
        () -> {
          try (InputStream raw = getContentResolver().openInputStream(uri);
              BufferedInputStream input = new BufferedInputStream(raw)) {
            input.mark(8);
            int first = input.read();
            input.reset();
            String filename = fileName(uri);
            boolean xml =
                first == '<' || filename.toLowerCase(Locale.ROOT).matches(".*\\.(xml|musicxml)$");
            MidiSong song = xml ? MusicXml.read(input) : MidiSong.read(input);
            File existing = "file".equals(uri.getScheme()) ? new File(uri.getPath()) : null;
            File saved = store || existing == null ? library.put(song, filename) : existing;
            String label =
                saved.getParentFile().getName().equals("songs") ? library.name(saved) : filename;
            runOnUiThread(
                () -> {
                  if (destroyed || generation != loadGeneration) return;
                  if (store) setFreePlay(false);
                  if (!freePlay) quiet();
                  trainer.load(song);
                  if (xml)
                    for (int id : song.tracks.keySet()) trainer.roles.put(id, id % 2 == 0 ? 1 : 2);
                  currentPath = saved.getAbsolutePath();
                  name = label;
                  title.setText(name);
                  trainer.hand = 0;
                  handButton.setText(Ui.t(this, "Dua tangan"));
                  importButton.setEnabled(true);
                  getPreferences(0)
                      .edit()
                      .putString("currentSong", currentPath)
                      .remove("uri")
                      .apply();
                  if (!store) restoreSession();
                  PartSettings.load(this, currentPath, trainer.arrangement);
                  updateLearningControls();
                  status(
                      song.notes.size()
                          + tr(" not · ", " notes · ")
                          + song.tracks.size()
                          + tr(" track · drum dilewati: ", " tracks · percussion skipped: ")
                          + song.percussionSkipped
                          + " · "
                          + tr("atur di Bagian & pengiring", "review Parts & backing"));
                  piano.invalidate();
                });
          } catch (Exception e) {
            runOnUiThread(
                () -> {
                  if (destroyed || generation != loadGeneration) return;
                  importButton.setEnabled(true);
                  status(tr("Impor gagal: ", "Import failed: ") + e.getMessage());
                });
          }
        });
  }

  private void libraryMenu() {
    List<File> files = library.files();
    if (files.isEmpty()) {
      status(
          tr(
              "Belum ada lagu tersimpan. Impor MIDI atau hasil konversi terlebih dahulu.",
              "Library empty. Import MIDI or a conversion first."));
      return;
    }
    String[] names = new String[files.size()];
    for (int i = 0; i < files.size(); i++) names[i] = library.name(files.get(i));
    new AlertDialog.Builder(this)
        .setTitle(tr("Perpustakaan lokal", "Local library"))
        .setItems(names, (d, w) -> load(Uri.fromFile(files.get(w)), false))
        .setNegativeButton(tr("Batal", "Cancel"), null)
        .show();
  }

  private void instruments() {
    String[] quick = {
      "Grand Piano 1",
      "Grand Piano 2 · Bright",
      "Electric Piano",
      "Organ",
      "Flute",
      "Strings",
      "Guitar",
      tr("128 instrumen General MIDI…", "128 General MIDI instruments…")
    };
    int[] indices = {0, 1, 4, 16, 73, 48, 24};
    new AlertDialog.Builder(this)
        .setTitle(tr("Instrumen · ", "Instrument · ") + GeneralMidi.NAMES[program])
        .setItems(
            quick,
            (d, w) -> {
              if (w < 7) setProgram(indices[w]);
              else {
                String[] all = new String[128];
                for (int i = 0; i < 128; i++)
                  all[i] = String.format(Locale.US, "%03d  %s", i + 1, GeneralMidi.NAMES[i]);
                new AlertDialog.Builder(this)
                    .setSingleChoiceItems(
                        all,
                        program,
                        (dd, p) -> {
                          setProgram(p);
                          dd.dismiss();
                        })
                    .setNegativeButton(tr("Batal", "Cancel"), null)
                    .show();
              }
            })
        .show();
  }

  private void setProgram(int p) {
    program = p;
    synth.program(p);
    settings().edit().putInt("program", p).apply();
    if (external) midi.sendMessage(0xc0 | outputChannel, p, 0);
    status(GeneralMidi.NAMES[p]);
  }

  private void audioMenu() {
    new AlertDialog.Builder(this)
        .setTitle(tr("Output suara", "Sound output"))
        .setSingleChoiceItems(
            new String[] {
              tr("Tablet · FluidSynth SF2", "Tablet · FluidSynth SF2"),
              "Yamaha P-145 / MIDI OUT",
              tr("Senyap", "Silent")
            },
            !audio ? 2 : external ? 1 : 0,
            (d, w) -> {
              quiet();
              if (w == 1 && !midi.hasOutput()) {
                status(
                    tr(
                        "Pilih port output di MIDI / USB terlebih dahulu.",
                        "Choose an output port in MIDI / USB first."));
                d.dismiss();
                midi.menu();
                return;
              }
              audio = w != 2;
              external = w == 1;
              routeAudio = audio && !external;
              synth.enable(routeAudio);
              if (external) {
                guide = false;
                outButton.setText(Ui.t(this, "Panduan OUT: OFF"));
                midi.sendMessage(0xc0 | outputChannel, program, 0);
              }
              audioButton.setText(
                  external ? "Audio: MIDI" : Ui.t(this, audio ? "Audio: ON" : "Audio: OFF"));
              d.dismiss();
            })
        .show();
  }

  private void outputOptions() {
    new AlertDialog.Builder(this)
        .setTitle(tr("Panduan MIDI output", "MIDI output guide"))
        .setMessage(
            tr(
                "Note On/Off dikirim 0,45 detik sebelum garis. Ini dapat membunyikan perangkat"
                    + " tujuan. Lampu memerlukan dukungan Note On/Off generik; P-145 tidak memiliki"
                    + " lampu tuts.",
                "Sends Note On/Off 0.45 song seconds before the line. This can sound the"
                    + " destination. Lights require generic Note On/Off support; the P-145 has no"
                    + " key lights."))
        .setPositiveButton(
            tr(guide ? "Matikan" : "Aktifkan", guide ? "Disable" : "Enable"),
            (d, w) -> {
              if (!guide && !midi.hasOutput()) {
                status(
                    tr(
                        "Pilih port output MIDI terlebih dahulu",
                        "Choose a MIDI output port first"));
                return;
              }
              quiet();
              guide = !guide;
              outButton.setText(Ui.t(this, guide ? "Panduan OUT: ON" : "Panduan OUT: OFF"));
              piano.invalidate();
            })
        .setNeutralButton(
            tr("Kanal ", "Channel ") + (outputChannel + 1),
            (d, w) -> {
              String[] channels = new String[16];
              for (int i = 0; i < 16; i++) channels[i] = tr("Kanal ", "Channel ") + (i + 1);
              new AlertDialog.Builder(this)
                  .setItems(
                      channels,
                      (dd, ch) -> {
                        flushOutput();
                        outputChannel = ch;
                      })
                  .show();
            })
        .setNegativeButton(tr("Batal", "Cancel"), null)
        .show();
  }

  private void settingsMenu() {
    String[] choices = {
      "Software Update",
      tr("Suara, reverb & polyphony", "Sound, reverb & polyphony"),
      tr("Impor SoundFont SF2", "Import SoundFont SF2"),
      tr("Gunakan SoundFont bawaan", "Use bundled SoundFont"),
      tr("Tema gelap / terang", "Dark / light theme"),
      "Bahasa / Language",
      tr("Ikuti program input MIDI: ", "Follow MIDI input programs: ")
          + (followPrograms ? "ON" : "OFF"),
      tr("Bantuan & panduan", "Help & guide"),
      tr("Tentang aplikasi", "About")
    };
    new AlertDialog.Builder(this)
        .setTitle(tr("Pengaturan", "Settings"))
        .setItems(
            choices,
            (d, w) -> {
              if (w == 0) startActivity(new Intent(this, SoftwareUpdateActivity.class));
              if (w == 1) soundSettings();
              if (w == 2) pick(21, "*/*");
              if (w == 3) {
                settings().edit().remove("soundfont").apply();
                loader.execute(
                    () -> {
                      boolean ok = synth.importBank(new File(getFilesDir(), "TimGM6mb.sf2"));
                      runOnUiThread(
                          () -> {
                            quiet();
                            piano.invalidate();
                            status(
                                ok
                                    ? tr("Bank bawaan aktif", "Bundled bank active")
                                    : "SoundFont error");
                          });
                    });
              }
              if (w == 4) {
                saveSession();
                settings().edit().putBoolean("light", !Ui.light(this)).apply();
                recreate();
              }
              if (w == 5)
                new AlertDialog.Builder(this)
                    .setSingleChoiceItems(
                        new String[] {"Bahasa Indonesia", "English"},
                        Ui.english(this) ? 1 : 0,
                        (dd, index) -> {
                          saveSession();
                          settings().edit().putString("language", index == 1 ? "en" : "id").apply();
                          dd.dismiss();
                          recreate();
                        })
                    .show();
              if (w == 6) {
                followPrograms = !followPrograms;
                status(
                    tr("Ikuti program input MIDI: ", "Follow MIDI input programs: ")
                        + followPrograms);
              }
              if (w == 7) help();
              if (w == 8) startActivity(new Intent(this, AboutActivity.class));
            })
        .show();
  }

  private void soundSettings() {
    LinearLayout box = Ui.column(this);
    Switch reverb = new Switch(this);
    reverb.setText("Reverb");
    reverb.setChecked(settings().getBoolean("reverb", true));
    box.addView(reverb);
    Spinner poly = new Spinner(this);
    int[] values = {32, 64, 128, 256};
    poly.setAdapter(
        new ArrayAdapter<>(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            new String[] {"32 voices", "64 voices", "128 voices", "256 voices"}));
    int current = settings().getInt("polyphony", 128);
    for (int i = 0; i < values.length; i++) if (values[i] == current) poly.setSelection(i);
    box.addView(poly);
    new AlertDialog.Builder(this)
        .setTitle("FluidSynth · " + synth.sampleRate + " Hz")
        .setView(box)
        .setPositiveButton(
            tr("Simpan", "Save"),
            (d, w) -> {
              int value = values[poly.getSelectedItemPosition()];
              boolean rev = reverb.isChecked();
              synth.configure(value, rev);
              settings().edit().putBoolean("reverb", rev).putInt("polyphony", value).apply();
            })
        .show();
  }

  private void importSoundfont(Uri uri) {
    status(tr("Memuat SF2…", "Loading SF2…"));
    loader.execute(
        () -> {
          File temp = new File(getFilesDir(), "sf2-import.tmp");
          try (InputStream input = getContentResolver().openInputStream(uri);
              OutputStream out = new FileOutputStream(temp)) {
            byte[] b = new byte[32768];
            long count = 0;
            int n;
            while ((n = input.read(b)) != -1) {
              count += n;
              if (count > 1024L * 1024 * 1024)
                throw new IOException("SF2 exceeds safe 1 GiB file size");
              out.write(b, 0, n);
            }
            out.close();
            if (!synth.importBank(temp))
              throw new IOException("FluidSynth rejected the SF2; previous bank preserved");
            File bank = new File(getFilesDir(), "custom.sf2");
            if (bank.exists() && !bank.delete()) throw new IOException("Cannot replace SF2");
            if (!temp.renameTo(bank)) throw new IOException("Cannot store SF2");
            settings().edit().putString("soundfont", bank.getPath()).apply();
            runOnUiThread(
                () -> {
                  quiet();
                  piano.invalidate();
                  status(
                      tr(
                          "SF2 aktif; preset tersedia mengikuti isi bank.",
                          "SF2 active; available presets depend on the bank."));
                });
          } catch (Exception e) {
            temp.delete();
            runOnUiThread(
                () -> status(tr("SoundFont gagal: ", "SoundFont failed: ") + e.getMessage()));
          }
        });
  }

  private void help() {
    trainer.playing = false;
    quiet();
    startActivity(new Intent(this, HelpActivity.class));
  }

  private void seek(double time) {
    quiet();
    trainer.seek(time);
    lastTime = -1;
    piano.invalidate();
  }

  @Override
  public void touch(int pitch, boolean on) {
    int old = touchCount[pitch];
    touchCount[pitch] = Math.max(0, old + (on ? 1 : -1));
    if (on && !freePlay) trainer.press(pitch);
    if (audio && !external && ((old == 0 && on) || (touchCount[pitch] == 0 && !on)))
      synth.key(pitch, 85, on);
    if (freePlay && audio && external && ((old == 0 && on) || (touchCount[pitch] == 0 && !on))) {
      midi.noteOut(outputChannel, pitch, 85, on);
      sent[pitch] = on;
    }
    refreshKey(pitch);
  }

  @Override
  public void realtime(int channel, int pitch, int velocity, boolean on) {
    if (routeAudio && resumed) synth.midi(channel, on ? 0x90 : 0x80, pitch, on ? velocity : 0);
  }

  @Override
  public void message(int status, int a, int b) {
    if (routeAudio && resumed) {
      int kind = status & 240;
      if (kind != 0xc0 || followPrograms) synth.midi(status & 15, kind, a, b);
    }
  }

  @Override
  public void note(int channel, int pitch, int velocity, boolean on) {
    if (!resumed || channel < 0 || channel > 15 || pitch < 0 || pitch > 127) return;
    hardware[channel][pitch] = on;
    if (on && !freePlay) trainer.press(pitch);
    refreshKey(pitch, velocity);
  }

  private void refreshKey(int pitch) {
    refreshKey(pitch, 85);
  }

  private void refreshKey(int pitch, int velocity) {
    boolean held = touchCount[pitch] > 0;
    for (int c = 0; c < 16; c++) held |= hardware[c][pitch];
    piano.keyState(pitch, held, velocity);
  }

  @Override
  public void disconnected() {
    flushOutput();
    for (boolean[] c : hardware) Arrays.fill(c, false);
    if (piano != null) for (int p = 0; p < 128; p++) refreshKey(p);
    if (synth != null) synth.silence();
  }

  @Override
  public void status(String message) {
    if (destroyed) return;
    if (status != null) status.setText(message);
  }

  private void flushOutput() {
    if (midi != null) {
      for (int p = 0; p < 128; p++)
        if (sent[p]) {
          midi.noteOut(outputChannel, p, 0, false);
          sent[p] = false;
        }
      midi.sendMessage(0xb0 | outputChannel, 64, 0);
    }
    pedal = false;
  }

  private void quiet() {
    if (songPlayback != null) songPlayback.stop();
    flushOutput();
    if (synth != null) synth.silence();
    if (piano != null) Arrays.fill(piano.sounding, false);
    lastTime = -1;
  }

  @Override
  public void frame() {
    if (freePlay) return;
    double t = trainer.time;
    if (playbackSong != trainer.song) {
      if (songPlayback != null) songPlayback.stop();
      playbackSong = trainer.song;
      songPlayback =
          new SongPlayback(
              trainer,
              (channel, kind, a, b) -> {
                if (!audio) return;
                if (external) {
                  // Guide and file playback share channel 0: emit their union once below.
                  if (guide && channel == outputChannel && (kind == 0x90 || kind == 0x80)) return;
                  midi.sendMessage(kind | channel, a, b);
                } else synth.midi(32 + channel, kind, a, b);
              });
    }
    songPlayback.sync(t, trainer.playing, trainer.listen);
    boolean reset = lastSong != trainer.song || lastTime < 0 || t < lastTime;
    if (reset) {
      activeNotes.clear();
      songCursor = 0;
      lastSong = trainer.song;
    }
    while (songCursor < trainer.song.notes.size()
        && trainer.song.notes.get(songCursor).start <= t + .45) {
      MidiSong.Note n = trainer.song.notes.get(songCursor++);
      if (n.end > t) activeNotes.add(n);
    }
    Arrays.fill(piano.sounding, false);
    boolean[] desired = new boolean[128];
    int[] velocity = new int[128];
    for (Iterator<MidiSong.Note> it = activeNotes.iterator(); it.hasNext(); ) {
      MidiSong.Note n = it.next();
      if (n.end <= t) {
        it.remove();
        continue;
      }
      if (n.pitch < 21 || n.pitch > 108 || trainer.role(n) == 3) continue;
      if (trainer.playing
          && trainer.listen
          && trainer.arrangement.mode(n) != Arrangement.MUTE
          && n.start <= t) piano.sounding[n.pitch] = true;
      if (trainer.playing
          && audio
          && external
          && guide
          && n.channel == outputChannel
          && trainer.audible(n, trainer.listen)
          && n.start <= t) {
        desired[n.pitch] = true;
        velocity[n.pitch] = Math.max(velocity[n.pitch], trainer.arrangement.velocity(n));
      }
      if (trainer.playing && guide && trainer.selected(n)) {
        desired[n.pitch] = true;
        velocity[n.pitch] = 80;
      }
    }
    if (audio && external)
      for (int p = 0; p < 128; p++)
        if (touchCount[p] > 0) {
          desired[p] = true;
          velocity[p] = 85;
        }
    if (midi != null)
      for (int p = 0; p < 128; p++)
        if (desired[p] != sent[p]) {
          midi.noteOut(outputChannel, p, velocity[p], desired[p]);
          sent[p] = desired[p];
        }
    lastTime = t;
    String text = Ui.t(this, trainer.playing ? "Ⅱ Jeda" : "▶ Mulai");
    if (!play.getText().toString().equals(text)) play.setText(text);
    long now = SystemClock.elapsedRealtime();
    if (now - lastUi > 100 && !dragging) {
      position.setProgress(
          trainer.song.duration == 0
              ? 0
              : (int) (10000 * Math.min(t, trainer.song.duration) / trainer.song.duration));
      lastUi = now;
    }
  }

  private void saveSession() {
    if (piano == null) return;
    SharedPreferences.Editor e = getPreferences(0).edit();
    String key = "session:" + currentPath;
    StringBuilder roles = new StringBuilder();
    for (Map.Entry<Integer, Integer> r : trainer.roles.entrySet())
      roles.append(r.getKey()).append(':').append(r.getValue()).append(',');
    e.putFloat(key + ":time", (float) trainer.time)
        .putFloat(key + ":speed", (float) trainer.speed)
        .putInt(key + ":hand", trainer.hand)
        .putBoolean(key + ":wait", trainer.wait)
        .putBoolean(key + ":listen", trainer.listen)
        .putString(key + ":roles", roles.toString())
        .putInt(key + ":hits", trainer.hits)
        .putInt(key + ":misses", trainer.misses)
        .putInt(key + ":wrong", trainer.wrong)
        .putString(
            key + ":judged",
            android.util.Base64.encodeToString(trainer.judged, android.util.Base64.NO_WRAP))
        .putBoolean(key + ":loop", trainer.loop)
        .putFloat(key + ":a", (float) trainer.loopA)
        .putFloat(key + ":b", (float) trainer.loopB)
        .apply();
  }

  private void restoreSession() {
    SharedPreferences p = getPreferences(0);
    String k = "session:" + currentPath;
    trainer.seek(p.getFloat(k + ":time", 0));
    trainer.speed = p.getFloat(k + ":speed", 1);
    trainer.hand = p.getInt(k + ":hand", 0);
    trainer.wait = p.getBoolean(k + ":wait", true);
    trainer.listen = p.getBoolean(k + ":listen", false);
    tempoSlider.setProgress((int) Math.round(trainer.speed * 100) - 25);
    trainer.loopA = p.getFloat(k + ":a", 0);
    trainer.loopB = p.getFloat(k + ":b", (float) trainer.song.duration);
    trainer.loop = p.getBoolean(k + ":loop", false) && trainer.loopB > trainer.loopA;
    try {
      byte[] judged =
          android.util.Base64.decode(p.getString(k + ":judged", ""), android.util.Base64.DEFAULT);
      if (judged.length == trainer.judged.length) {
        trainer.judged = judged;
        trainer.hits = p.getInt(k + ":hits", 0);
        trainer.misses = p.getInt(k + ":misses", 0);
        trainer.wrong = p.getInt(k + ":wrong", 0);
      }
    } catch (IllegalArgumentException ignored) {
    }

    for (String pair : p.getString(k + ":roles", "").split(","))
      try {
        String[] parts = pair.split(":");
        trainer.roles.put(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
      } catch (Exception ignored) {
      }
    mode.setText(Ui.t(this, trainer.listen ? "Dengarkan" : "Latihan"));
    waitButton.setText(Ui.t(this, trainer.wait ? "Tunggu: ON" : "Tunggu: OFF"));
    handButton.setText(
        Ui.t(this, trainer.hand == 0 ? "Dua tangan" : trainer.hand == 1 ? "Kiri" : "Kanan"));
    tempoLabel.setText(String.format(Locale.US, "%.0f%%", trainer.speed * 100));
  }

  private void automaticUpdate() {
    if (!settings().getBoolean("autoUpdates", true)) return;
    long now = System.currentTimeMillis();
    if (now - settings().getLong("lastUpdateCheck", 0) < 86400000L) return;
    settings().edit().putLong("lastUpdateCheck", now).apply();
    updateChecks.execute(
        () -> {
          try {
            UpdateClient.Release release =
                UpdateClient.latest(
                    settings().getBoolean("beta", BuildConfig.VERSION_NAME.contains("-")));
            if (release != null && release.code > BuildConfig.VERSION_CODE)
              runOnUiThread(
                  () ->
                      status(
                          tr("Pembaruan tersedia: ", "Update available: ")
                              + release.version
                              + " · Settings > Software Update"));
          } catch (Exception ignored) {
            /* Manual check reports network/rate-limit errors without interrupting practice. */
          }
        });
  }

  @Override
  protected void onResume() {
    super.onResume();
    resumed = true;
    if (trainer != null) {
      String before = trainer.arrangement.learningSignature();
      PartSettings.load(this, currentPath, trainer.arrangement);
      if (!before.equals(trainer.arrangement.learningSignature())) trainer.seek(trainer.time);
    }
    if (piano != null) piano.start();
  }

  @Override
  protected void onPause() {
    super.onPause();
    saveSession();
    PartSettings.save(this, currentPath, trainer.arrangement);
    resumed = false;
    trainer.playing = false;
    if (piano != null) piano.stop();
    for (boolean[] c : hardware) Arrays.fill(c, false);
    Arrays.fill(touchCount, 0);
    computerKeys.clear();
    quiet();
  }

  @Override
  protected void onDestroy() {
    destroyed = true;
    if (piano != null) piano.stop();
    quiet();
    if (midi != null) midi.close();
    loader.shutdownNow();
    updateChecks.shutdownNow();
    if (synth != null) synth.close();
    super.onDestroy();
  }

  @Override
  public boolean onKeyDown(int key, KeyEvent event) {
    int index = "awsedftgyhujkolp;".indexOf((char) event.getUnicodeChar());
    if (index >= 0 && event.getRepeatCount() == 0) {
      int pitch = 60 + index;
      computerKeys.put(key, pitch);
      touch(pitch, true);
      return true;
    }
    return super.onKeyDown(key, event);
  }

  @Override
  public boolean onKeyUp(int key, KeyEvent event) {
    Integer pitch = computerKeys.remove(key);
    if (pitch != null) {
      touch(pitch, false);
      return true;
    }
    return super.onKeyUp(key, event);
  }
}

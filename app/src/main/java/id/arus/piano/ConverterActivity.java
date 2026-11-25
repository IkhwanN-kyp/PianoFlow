package id.arus.piano;

import android.app.*;
import android.content.*;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ConverterActivity extends Activity {
  private TextView info, detail;
  private ProgressBar progress;
  private ListView list;
  private ArrayAdapter<String> adapter;
  private Uri source;
  private final List<MidiSong.Note> notes = new ArrayList<>();
  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private final AtomicBoolean cancel = new AtomicBoolean();
  private volatile boolean busy = false, closed = false;
  private OfflineSynth synth;
  private SimplePlayback playback;
  private MediaPlayer original;
  private boolean useModel = true;
  private float threshold = .3f;
  private File model;
  private Button convert;

  private String tr(String id, String en) {
    return Ui.tr(this, id, en);
  }

  @Override
  public void onCreate(Bundle b) {
    Ui.theme(this);
    super.onCreate(b);
    Ui.immersive(this);
    synth = new OfflineSynth(this);
    playback = new SimplePlayback(synth);
    model = new File(getFilesDir(), "basic-pitch.tflite");
    LinearLayout root = Ui.column(this), nav = Ui.row(this);
    nav.addView(Ui.button(this, "‹ Latihan", "‹ Practice", this::finish));
    nav.addView(Ui.text(this, tr("Konversi audio → MIDI", "Audio → MIDI"), 23));
    root.addView(nav);
    info =
        Ui.text(
            this,
            tr(
                "Pilih MP3, WAV, M4A atau FLAC. Semua proses berjalan di perangkat.",
                "Choose MP3, WAV, M4A or FLAC. Processing stays on this device."),
            14);
    root.addView(info);
    LinearLayout actions = Ui.row(this);
    actions.addView(Ui.button(this, "Pilih audio", "Choose audio", () -> pick(11, "audio/*")));
    actions.addView(Ui.button(this, "Preview audio", "Preview audio", this::previewOriginal));
    actions.addView(
        Ui.button(this, "Impor model .tflite", "Import .tflite model", () -> pick(12, "*/*")));
    actions.addView(
        Ui.button(
            this,
            "Metode",
            "Method",
            () ->
                new AlertDialog.Builder(this)
                    .setSingleChoiceItems(
                        new String[] {
                          "Basic Pitch · "
                              + tr("polifonik, model opsional", "polyphonic, optional model"),
                          tr("Melodi ringan · satu nada", "Light melody · one pitch")
                        },
                        useModel ? 0 : 1,
                        (d, w) -> {
                          useModel = w == 0;
                          d.dismiss();
                          refreshDetail();
                        })
                    .show()));
    root.addView(actions);
    LinearLayout conversion = Ui.row(this);
    convert = Ui.button(this, "Konversi", "Convert", this::convert);
    conversion.addView(convert);
    conversion.addView(
        Ui.button(
            this,
            "Batalkan / Stop",
            "Cancel / Stop",
            () -> {
              cancel.set(true);
              stopPreview();
            }));
    conversion.addView(Ui.text(this, tr("Ambang not", "Note threshold"), 12));
    SeekBar sensitivity = new SeekBar(this);
    sensitivity.setMax(50);
    sensitivity.setProgress(20);
    sensitivity.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          public void onProgressChanged(SeekBar s, int p, boolean user) {
            threshold = (p + 10) / 100f;
            refreshDetail();
          }

          public void onStartTrackingTouch(SeekBar s) {}

          public void onStopTrackingTouch(SeekBar s) {}
        });
    conversion.addView(sensitivity, new LinearLayout.LayoutParams(0, 44, 1));
    root.addView(conversion);
    detail = Ui.text(this, "", 12);
    root.addView(detail);
    refreshDetail();
    progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
    progress.setMax(100);
    root.addView(progress, new LinearLayout.LayoutParams(-1, Ui.dp(this, 10)));
    root.addView(
        Ui.text(
            this,
            tr(
                "Hasil adalah estimasi. Ketuk not untuk mengoreksi pitch, waktu, durasi dan"
                    + " velocity.",
                "Results are estimates. Tap a note to edit pitch, timing, duration and velocity."),
            12));
    adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<>());
    list = new ListView(this);
    list.setAdapter(adapter);
    list.setOnItemClickListener((p, v, index, id) -> edit(index));
    root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
    LinearLayout output = Ui.row(this);
    output.addView(
        Ui.button(
            this,
            "Preview MIDI",
            "Preview MIDI",
            () -> {
              if (notes.isEmpty()) return;
              stopPreview();
              playback.play(song());
            }));
    output.addView(
        Ui.button(
            this,
            "Tambah not",
            "Add note",
            () -> {
              notes.add(new MidiSong.Note(60, 85, 0, 0, 0, .5));
              refreshNotes();
              edit(notes.size() - 1);
            }));
    output.addView(
        Ui.button(
            this,
            "Ekspor MIDI",
            "Export MIDI",
            () -> {
              if (notes.isEmpty()) return;
              Intent i =
                  new Intent(Intent.ACTION_CREATE_DOCUMENT)
                      .setType("audio/midi")
                      .putExtra(Intent.EXTRA_TITLE, "PianoFlow-converted.mid");
              i.addCategory(Intent.CATEGORY_OPENABLE);
              startActivityForResult(i, 13);
            }));
    output.addView(Ui.button(this, "Masuk latihan →", "Practice →", this::practice));
    root.addView(output);
    setContentView(root);
  }

  private void pick(int code, String mime) {
    if (busy) return;
    Intent i =
        new Intent(Intent.ACTION_OPEN_DOCUMENT).setType(mime).addCategory(Intent.CATEGORY_OPENABLE);
    startActivityForResult(i, code);
  }

  private void refreshDetail() {
    if (detail != null)
      detail.setText(
          (useModel
                  ? "Basic Pitch · "
                      + (model.isFile()
                          ? tr("model siap", "model ready")
                          : tr("impor modul model terlebih dahulu", "import model module first"))
                  : tr("DSP monofonik · tanpa model", "Monophonic DSP · no model"))
              + String.format(Locale.US, " · %.2f", threshold));
  }

  @Override
  protected void onActivityResult(int req, int result, Intent data) {
    super.onActivityResult(req, result, data);
    if (result != RESULT_OK || data == null || data.getData() == null) return;
    Uri uri = data.getData();
    if (req == 11) {
      stopPreview();
      source = uri;
      info.setText(tr("Audio dipilih: ", "Selected audio: ") + uri.getLastPathSegment());
    }
    if (req == 12) {
      busy = true;
      worker.execute(
          () -> {
            File temp = new File(getFilesDir(), "model-import.tmp");
            try {
              copy(uri, temp, 16 * 1024 * 1024);
              if (!UpdateClient.sha256(temp)
                  .equals("3db297d54af8e01c6e5618245c956b1d71b6a2b978cb2dedb527173186552676"))
                throw new IOException(
                    "Use the official PianoFlow Basic Pitch module (checksum mismatch)");
              try (org.tensorflow.lite.Interpreter test =
                  new org.tensorflow.lite.Interpreter(temp)) {
                int size = 1;
                for (int x : test.getInputTensor(0).shape()) size *= x;
                if (size != 43844) throw new IOException("Expected Basic Pitch model");
                if (!Arrays.asList(test.getSignatureOutputs(test.getSignatureKeys()[0]))
                    .contains("note")) throw new IOException("Invalid model outputs");
              }
              if (!temp.renameTo(model)) throw new IOException("Cannot save model");
              runOnUiThread(this::refreshDetail);
            } catch (Exception e) {
              temp.delete();
              reportError(e);
            } finally {
              busy = false;
            }
          });
    }
    if (req == 13) {
      try (OutputStream out = getContentResolver().openOutputStream(uri)) {
        MidiWriter.write(song(), out);
        info.setText(tr("MIDI berhasil diekspor", "MIDI exported"));
      } catch (Exception e) {
        Ui.error(this, e);
      }
    }
  }

  private void copy(Uri uri, File file, long limit) throws IOException {
    try (InputStream in = getContentResolver().openInputStream(uri);
        OutputStream out = new FileOutputStream(file)) {
      if (in == null) throw new IOException("Cannot open file");
      byte[] b = new byte[16384];
      int n;
      long total = 0;
      while ((n = in.read(b)) != -1) {
        total += n;
        if (total > limit) throw new IOException("Model exceeds safe memory limit");
        out.write(b, 0, n);
      }
    }
  }

  private void convert() {
    if (source == null || busy) return;
    if (useModel && !model.isFile()) {
      info.setText(
          tr(
              "Impor basic-pitch.tflite dari modul rilis, atau pilih metode Melodi ringan.",
              "Import basic-pitch.tflite from the release modules, or choose Light melody."));
      return;
    }
    stopPreview();
    cancel.set(false);
    busy = true;
    convert.setEnabled(false);
    Uri input = source;
    boolean neural = useModel;
    float thresholdNow = threshold;
    worker.execute(
        () -> {
          File pcm = new File(getCacheDir(), "decode-" + System.nanoTime() + ".pcm");
          try {
            AudioDecoder.Progress listener =
                (p, stage) ->
                    runOnUiThread(
                        () -> {
                          if (!closed) {
                            progress.setProgress(p);
                            info.setText(tr("Memproses ", "Processing ") + stage + " · " + p + "%");
                          }
                        });
            AudioDecoder.Decoded decoded = AudioDecoder.decode(this, input, pcm, cancel, listener);
            MidiSong result =
                neural
                    ? Transcriber.basicPitch(decoded, model, cancel, listener, thresholdNow)
                    : Transcriber.melody(decoded, cancel, listener);
            runOnUiThread(
                () -> {
                  if (closed) return;
                  notes.clear();
                  notes.addAll(result.notes);
                  refreshNotes();
                  progress.setProgress(100);
                  info.setText(
                      notes.size()
                          + tr(
                              " not diperkirakan · koreksi sebelum latihan",
                              " estimated notes · review before practice"));
                });
          } catch (Exception e) {
            reportError(e);
          } finally {
            pcm.delete();
            busy = false;
            runOnUiThread(
                () -> {
                  if (!closed) convert.setEnabled(true);
                });
          }
        });
  }

  private MidiSong song() {
    return MidiSong.of(new ArrayList<>(notes));
  }

  private void refreshNotes() {
    adapter.clear();
    for (int i = 0; i < notes.size(); i++) {
      MidiSong.Note n = notes.get(i);
      String pitch =
          new String[] {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"}
                  [n.pitch % 12]
              + (n.pitch / 12 - 1);
      adapter.add(
          String.format(
              Locale.US,
              "%d   %s (%d)     %.2f–%.2f s     velocity %d",
              i + 1,
              pitch,
              n.pitch,
              n.start,
              n.end,
              n.velocity));
    }
    adapter.notifyDataSetChanged();
  }

  private void edit(int index) {
    if (busy || index >= notes.size()) return;
    stopPreview();
    MidiSong.Note n = notes.get(index);
    LinearLayout box = Ui.column(this);
    EditText pitch = entry(box, "Pitch MIDI 0–127", n.pitch),
        start = entry(box, tr("Mulai (detik)", "Start (seconds)"), n.start),
        duration = entry(box, tr("Durasi (detik)", "Duration (seconds)"), n.end - n.start),
        velocity = entry(box, "Velocity 1–127", n.velocity);
    AlertDialog dialog =
        new AlertDialog.Builder(this)
            .setTitle(tr("Koreksi not", "Edit note"))
            .setView(box)
            .setPositiveButton(tr("Simpan", "Save"), null)
            .setNeutralButton(
                tr("Hapus", "Delete"),
                (d, w) -> {
                  notes.remove(index);
                  refreshNotes();
                })
            .setNegativeButton(tr("Batal", "Cancel"), null)
            .create();
    dialog.setOnShowListener(
        d ->
            dialog
                .getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(
                    v -> {
                      try {
                        int p = Integer.parseInt(pitch.getText().toString()),
                            vel = Integer.parseInt(velocity.getText().toString());
                        double s = Double.parseDouble(start.getText().toString()),
                            len = Double.parseDouble(duration.getText().toString());
                        if (p < 0
                            || p > 127
                            || vel < 1
                            || vel > 127
                            || !Double.isFinite(s)
                            || !Double.isFinite(len)
                            || s < 0
                            || len <= 0) throw new IllegalArgumentException("Invalid note values");
                        notes.set(index, new MidiSong.Note(p, vel, 0, 0, s, s + len));
                        notes.sort(Comparator.comparingDouble(a -> a.start));
                        refreshNotes();
                        dialog.dismiss();
                      } catch (Exception e) {
                        pitch.setError(tr("Periksa nilai", "Check values"));
                      }
                    }));
    dialog.show();
  }

  private EditText entry(LinearLayout root, String label, double value) {
    root.addView(Ui.text(this, label, 12));
    EditText e = new EditText(this);
    e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
    e.setText(String.format(Locale.US, "%.3f", value).replaceAll("\\.000$", ""));
    root.addView(e);
    return e;
  }

  private void previewOriginal() {
    if (source == null) return;
    stopPreview();
    try {
      original = new MediaPlayer();
      original.setDataSource(this, source);
      original.setOnPreparedListener(MediaPlayer::start);
      original.setOnErrorListener(
          (m, w, e) -> {
            info.setText(tr("Preview audio gagal", "Audio preview failed"));
            return true;
          });
      original.prepareAsync();
    } catch (Exception e) {
      Ui.error(this, e);
    }
  }

  private void stopPreview() {
    playback.stop();
    if (original != null) {
      original.release();
      original = null;
    }
  }

  private void practice() {
    if (notes.isEmpty() || busy) return;
    stopPreview();
    try {
      File result = new File(getFilesDir(), "converted.mid");
      try (OutputStream out = new FileOutputStream(result)) {
        MidiWriter.write(song(), out);
      }
      setResult(RESULT_OK, new Intent().setData(Uri.fromFile(result)));
      finish();
    } catch (Exception e) {
      Ui.error(this, e);
    }
  }

  private void reportError(Exception e) {
    runOnUiThread(
        () -> {
          if (!closed) info.setText(tr("Konversi gagal: ", "Conversion failed: ") + e.getMessage());
        });
  }

  @Override
  public void onPause() {
    super.onPause();
    stopPreview();
  }

  @Override
  public void onDestroy() {
    closed = true;
    cancel.set(true);
    worker.shutdownNow();
    stopPreview();
    synth.close();
    super.onDestroy();
  }
}

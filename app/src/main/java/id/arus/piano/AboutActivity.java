package id.arus.piano;

import android.app.*;
import android.content.*;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Credits, optional support and the original bundled license texts. */
public final class AboutActivity extends Activity {
  static final String SUPPORT_URL = "https://trakteer.id/radarswingid";
  static final String SOURCE_URL = "https://github.com/IkhwanN-kyp/PianoFlow";

  @Override
  public void onCreate(Bundle state) {
    Ui.theme(this);
    super.onCreate(state);
    Ui.immersive(this);
    LinearLayout root = Ui.column(this);
    LinearLayout header = Ui.row(this);
    header.addView(Ui.button(this, "‹ Kembali", "‹ Back", this::finish));
    header.addView(Ui.text(this, tr("Tentang aplikasi", "About"), 22));
    root.addView(header);
    ScrollView scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    LinearLayout columns = Ui.row(this);
    columns.setGravity(android.view.Gravity.TOP);
    LinearLayout left = Ui.column(this), right = Ui.column(this);
    columns.addView(left, new LinearLayout.LayoutParams(0, -2, 1));
    columns.addView(right, new LinearLayout.LayoutParams(0, -2, 1));
    heading(left, "PianoFlow");
    TextView version =
        Ui.text(
            this,
            tr("Versi ", "Version ")
                + BuildConfig.VERSION_NAME
                + "  ·  "
                + tr("Kode versi ", "Version code ")
                + BuildConfig.VERSION_CODE,
            15);
    version.setTextIsSelectable(true);
    left.addView(version);
    paragraph(left, "PianoFlow · @radarswingid", "PianoFlow · @radarswingid");
    paragraph(
        left,
        "Teman belajar piano Anda. Gratis, tanpa iklan, langganan, atau batas impor lagu.",
        "Your piano learning companion. Free, without ads, subscriptions or song import limits.");
    heading(left, tr("Bantu PianoFlow terus berkembang", "Help PianoFlow keep growing"));
    paragraph(
        left,
        "Masukan, ide, dan kontribusi Anda membantu PianoFlow menjadi teman belajar piano yang"
            + " semakin baik. Jika PianoFlow bermanfaat, Anda dapat mendukung pengembangannya"
            + " melalui Trakteer @radarswingid. Dukungan Anda memberi waktu dan semangat untuk"
            + " menghadirkan perbaikan serta fitur baru. Terima kasih sudah menjadi bagian dari"
            + " perjalanan PianoFlow.",
        "Your feedback, ideas and contributions help make PianoFlow a better piano learning"
            + " companion. If you find it useful, you can support its development through Trakteer"
            + " @radarswingid. Your support gives us time and encouragement to improve the app and"
            + " create new features. Thank you for being part of PianoFlow’s journey.");
    paragraph(
        left,
        "Dukungan bersifat sukarela. Seluruh fitur tetap gratis dan dapat digunakan tanpa donasi.",
        "Support is entirely optional. Every feature remains free and available without donating.");
    left.addView(
        Ui.button(this, "Dukung di Trakteer ↗", "Support on Trakteer ↗", () -> open(SUPPORT_URL)));
    left.addView(
        Ui.button(
            this,
            "Source code & kontribusi GitHub ↗",
            "Source code & contribute on GitHub ↗",
            () -> open(SOURCE_URL)));
    heading(right, tr("Kredit & sumber terbuka", "Credits & open source"));
    paragraph(
        right,
        "PianoFlow dikembangkan secara mandiri. MIDIVisualizer menjadi referensi visual; aplikasi"
            + " ini bukan fork dan tidak menyertakan kode atau asetnya.",
        "PianoFlow is independently developed. MIDIVisualizer is a visual reference; this app is"
            + " not a fork and does not include its code or assets.");
    credit(
        right,
        "MIDIVisualizer · Simon Rodriguez · MIT",
        "https://github.com/kosua20/MIDIVisualizer");
    credit(right, "FluidSynth · LGPL-2.1", "https://github.com/FluidSynth/fluidsynth");
    credit(
        right,
        "TimGM6mb · Tim Brechbill / David Bolton · GPL-2.0",
        "https://github.com/arbruijn/TimGM6mb");
    credit(right, "Basic Pitch · Spotify · Apache-2.0", "https://github.com/spotify/basic-pitch");
    credit(right, "Bravura · Steinberg · SIL OFL 1.1", "https://github.com/steinbergmedia/bravura");
    credit(right, "TensorFlow Lite · Apache-2.0", "https://github.com/tensorflow/tensorflow");
    credit(right, "Oboe · Apache-2.0", "https://github.com/google/oboe");
    right.addView(Ui.button(this, "Baca lisensi offline", "Read licenses offline", this::licenses));
    right.addView(
        Ui.button(
            this,
            "Daftar sumber & pemberitahuan lengkap ↗",
            "All sources & notices ↗",
            () -> open(SOURCE_URL + "/blob/main/THIRD_PARTY_NOTICES.md")));
    paragraph(
        right,
        "Hak cipta masing-masing komponen tetap milik pemiliknya. PianoFlow tidak berafiliasi"
            + " dengan Yamaha, Huawei, Spotify, atau proyek referensi tersebut.",
        "Each component’s copyright remains with its owners. PianoFlow is not affiliated with"
            + " Yamaha, Huawei, Spotify or the referenced projects.");
    scroll.addView(columns);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    setContentView(root);
  }

  private String tr(String id, String en) {
    return Ui.tr(this, id, en);
  }

  private void heading(LinearLayout box, String text) {
    TextView view = Ui.text(this, text, 21);
    view.setTypeface(null, Typeface.BOLD);
    view.setTextColor(Ui.accent(this));
    box.addView(view);
  }

  private void paragraph(LinearLayout box, String id, String en) {
    TextView view = Ui.text(this, tr(id, en), 15);
    view.setLineSpacing(Ui.dp(this, 3), 1);
    box.addView(view);
  }

  private void credit(LinearLayout box, String title, String url) {
    Button link = Ui.button(this, title + " ↗", title + " ↗", () -> open(url));
    link.setLayoutParams(new LinearLayout.LayoutParams(-1, Ui.dp(this, 48)));
    box.addView(link);
  }

  private void open(String url) {
    try {
      startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    } catch (ActivityNotFoundException e) {
      TextView link = Ui.text(this, url, 16);
      link.setTextIsSelectable(true);
      new AlertDialog.Builder(this)
          .setTitle(tr("Salin tautan", "Copy link"))
          .setView(link)
          .setPositiveButton("OK", null)
          .show();
    }
  }

  private void licenses() {
    try {
      String[] files = getAssets().list("licenses");
      Arrays.sort(files);
      new AlertDialog.Builder(this)
          .setTitle(tr("Lisensi komponen", "Component licenses"))
          .setItems(files, (dialog, index) -> showLicense(files[index]))
          .show();
    } catch (IOException e) {
      Ui.error(this, e);
    }
  }

  private void showLicense(String name) {
    try (InputStream input = getAssets().open("licenses/" + name);
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      byte[] buffer = new byte[8192];
      int n;
      while ((n = input.read(buffer)) != -1) out.write(buffer, 0, n);
      TextView text = Ui.text(this, new String(out.toByteArray(), StandardCharsets.UTF_8), 13);
      text.setTextIsSelectable(true);
      ScrollView scroll = new ScrollView(this);
      scroll.addView(text);
      new AlertDialog.Builder(this)
          .setTitle(name)
          .setView(scroll)
          .setPositiveButton("OK", null)
          .show();
    } catch (IOException e) {
      Ui.error(this, e);
    }
  }
}

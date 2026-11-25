package id.arus.piano;

import android.app.*;
import android.content.*;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import java.util.*;

final class Ui {
  static boolean english(Context c) {
    return c.getSharedPreferences("settings", 0).getString("language", "id").equals("en");
  }

  static boolean light(Context c) {
    return c.getSharedPreferences("settings", 0).getBoolean("light", false);
  }

  static String tr(Context c, String id, String en) {
    return english(c) ? en : id;
  }

  static int bg(Context c) {
    return light(c) ? 0xfff3f5f8 : 0xff0c1220;
  }

  static int panel(Context c) {
    return light(c) ? 0xffffffff : 0xff1b2940;
  }

  static int fg(Context c) {
    return light(c) ? 0xff172237 : 0xffe4edf8;
  }

  static int muted(Context c) {
    return light(c) ? 0xff516179 : 0xff91a0b8;
  }

  static int accent(Context c) {
    return light(c) ? 0xff087f77 : 0xff59dece;
  }

  static int dp(Context c, float value) {
    return Math.round(c.getResources().getDisplayMetrics().density * value);
  }

  static void theme(Activity a) {
    a.setTheme(
        light(a)
            ? android.R.style.Theme_Material_Light_NoActionBar
            : android.R.style.Theme_Material_NoActionBar);
  }

  static LinearLayout column(Context c) {
    LinearLayout l = new LinearLayout(c);
    l.setOrientation(LinearLayout.VERTICAL);
    l.setBackgroundColor(bg(c));
    l.setPadding(dp(c, 20), dp(c, 10), dp(c, 20), dp(c, 10));
    return l;
  }

  static LinearLayout row(Context c) {
    LinearLayout l = new LinearLayout(c);
    l.setOrientation(LinearLayout.HORIZONTAL);
    l.setGravity(Gravity.CENTER_VERTICAL);
    return l;
  }

  static TextView text(Context c, String s, int size) {
    TextView t = new TextView(c);
    t.setText(s);
    t.setTextSize(size);
    t.setTextColor(fg(c));
    t.setPadding(dp(c, 4), dp(c, 5), dp(c, 4), dp(c, 5));
    return t;
  }

  static Button button(Context c, String id, String en, Runnable click) {
    Button b = new Button(c);
    b.setText(tr(c, id, en));
    b.setAllCaps(false);
    b.setTextSize(13);
    b.setTextColor(fg(c));
    GradientDrawable d = new GradientDrawable();
    d.setColor(panel(c));
    d.setCornerRadius(dp(c, 8));
    d.setStroke(dp(c, 1), light(c) ? 0xffd6dfeb : 0xff2a3c55);
    b.setBackground(d);
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, dp(c, 44));
    p.setMargins(dp(c, 4), dp(c, 4), dp(c, 4), dp(c, 4));
    b.setPadding(dp(c, 14), 0, dp(c, 14), 0);
    b.setLayoutParams(p);
    b.setOnClickListener(v -> click.run());
    return b;
  }

  static void immersive(Activity a) {
    a.getWindow()
        .getDecorView()
        .setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
  }

  private static final Map<String, String> translations = new HashMap<>();

  static {
    String[][] pairs = {
      {"+ Impor MIDI", "+ Import MIDI"},
      {"Latihan", "Practice"},
      {"Dengarkan", "Listen"},
      {"Tunggu: ON", "Wait: ON"},
      {"Tunggu: OFF", "Wait: OFF"},
      {"Dua tangan", "Both hands"},
      {"Kiri", "Left"},
      {"Kanan", "Right"},
      {"▶ Mulai", "▶ Start"},
      {"Ⅱ Jeda", "Ⅱ Pause"},
      {"↺ Awal", "↺ Start"},
      {"Loop mati", "Loop off"},
      {"Audio: ON", "Audio: ON"},
      {"Audio: OFF", "Audio: OFF"},
      {"Panduan OUT: OFF", "Guide OUT: OFF"},
      {"Panduan OUT: ON", "Guide OUT: ON"},
      {"Batal", "Cancel"},
      {"Selesai", "Done"},
      {"Mengerti", "OK"},
      {"Kiri · biru", "Left · blue"},
      {"Kanan · hijau", "Right · mint"},
      {"Pilih input piano", "Select piano input"},
      {"Pilih tujuan output", "Select output destination"},
      {"Sambungan MIDI", "MIDI connection"},
      {"Atur tangan per track", "Assign hands by track"},
      {"Pemisahan tangan per track", "Hands by track"},
      {"Tangan yang dilatih", "Practice hand"},
      {"Putuskan semua", "Disconnect all"},
      {"Input · Android MIDI API", "Input · Android MIDI API"},
      {"Cadangan · USB host MIDI 1", "Fallback · USB host MIDI 1"},
      {
        "Output · Note On/Off (audio / lampu generik)",
        "Output · Note On/Off (audio / generic lights)"
      },
      {"Panduan MIDI output", "MIDI output guide"},
      {"Matikan", "Disable"},
      {"Aktifkan", "Enable"},
      {"Otomatis: nada < C4 = kiri", "Automatic: pitch < C4 = left"},
      {"Kiri (semua not track)", "Left (entire track)"},
      {"Kanan (semua not track)", "Right (entire track)"},
      {"Abaikan track", "Ignore track"},
      {"Latihan tangga nada · buatan sendiri", "Original scale exercise"}
    };
    for (String[] p : pairs) translations.put(p[0], p[1]);
  }

  static String t(Context c, String original) {
    return english(c) ? translations.getOrDefault(original, original) : original;
  }

  static void error(Activity a, Exception e) {
    new AlertDialog.Builder(a)
        .setTitle(tr(a, "Tidak dapat melanjutkan", "Unable to continue"))
        .setMessage(e.getMessage())
        .setPositiveButton("OK", null)
        .show();
  }
}

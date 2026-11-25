package id.arus.piano;

import android.content.*;
import android.graphics.*;
import java.util.*;

/**
 * Learning score: grand staff, display quantization to sixteenths, accidentals, note values and
 * rests.
 */
final class ScoreRenderer {
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Typeface music;
  private final Context context;

  ScoreRenderer(Context c) {
    context = c;
    music = Typeface.createFromAsset(c.getAssets(), "fonts/Bravura.otf");
  }

  private void glyph(Canvas c, String glyph, float x, float y, float spacing, int color) {
    paint.setColor(color);
    paint.setTypeface(music);
    paint.setTextSize(spacing * 4);
    paint.setStyle(Paint.Style.FILL);
    c.drawText(glyph, x, y, paint);
  }

  private void text(Canvas c, String text, float x, float y, float size, int color) {
    paint.setColor(color);
    paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
    paint.setTextSize(size);
    c.drawText(text, x, y, paint);
  }

  void draw(
      Canvas canvas,
      RectF box,
      Trainer trainer,
      boolean[] down,
      double pageBeat,
      boolean printing) {
    MidiSong song = trainer.song;
    int fg = printing ? 0xff172237 : Ui.fg(context),
        muted = printing ? 0xff5b6b80 : Ui.muted(context),
        bg = printing ? Color.WHITE : Ui.bg(context);
    paint.setColor(bg);
    canvas.drawRect(box, paint);
    if (box.height() < 90) return;
    canvas.save();
    canvas.clipRect(box);
    float spacing = Math.max(7, Math.min(17, box.height() / 15)),
        top = box.top + spacing * 3.4f,
        bass = top + spacing * 6.5f,
        left = box.left + spacing * 8,
        right = box.right - spacing;
    double currentBeat = song.beatAt(trainer.time);
    ScoreLayout layout = new ScoreLayout(song);
    ScoreLayout.Bar first = layout.at(pageBeat < 0 ? currentBeat : pageBeat),
        second = layout.next(first);
    double end = second.beat + second.length;
    float each = (right - left) / 2;
    for (int i = 0; i < 5; i++) {
      paint.setColor(muted);
      paint.setStrokeWidth(.7f);
      canvas.drawLine(box.left + spacing, top + i * spacing, right, top + i * spacing, paint);
      canvas.drawLine(box.left + spacing, bass + i * spacing, right, bass + i * spacing, paint);
    }
    glyph(canvas, "\uE050", box.left + spacing * 1.3f, top + spacing * 3, spacing, fg);
    glyph(canvas, "\uE062", box.left + spacing * 1.3f, bass + spacing, spacing, fg);
    text(
        canvas,
        "♩ = " + Math.round(60000000.0 / tempoAt(song, first.beat)),
        box.left + spacing,
        box.top + spacing * 1.3f,
        spacing,
        fg);
    text(
        canvas,
        "" + first.numerator,
        box.left + spacing * 5.6f,
        top + spacing * 1.8f,
        spacing * 1.6f,
        fg);
    text(
        canvas,
        "" + first.denominator,
        box.left + spacing * 5.6f,
        top + spacing * 3.5f,
        spacing * 1.6f,
        fg);
    text(
        canvas,
        "" + first.numerator,
        box.left + spacing * 5.6f,
        bass + spacing * 1.8f,
        spacing * 1.6f,
        fg);
    text(
        canvas,
        "" + first.denominator,
        box.left + spacing * 5.6f,
        bass + spacing * 3.5f,
        spacing * 1.6f,
        fg);
    paint.setColor(fg);
    paint.setStrokeWidth(1.2f);
    canvas.drawLine(box.left + spacing, top, box.left + spacing, bass + spacing * 4, paint);
    for (int i = 0; i <= 2; i++)
      canvas.drawLine(left + i * each, top, left + i * each, bass + spacing * 4, paint);
    text(canvas, "" + first.number, left + 4, top - spacing, spacing * .9f, muted);
    text(canvas, "" + second.number, left + each + 4, top - spacing, spacing * .9f, muted);
    boolean[][] has = new boolean[2][2];
    int startIndex = trainer.lower(song.secondsAt(first.beat) - .001),
        stop = trainer.lower(song.secondsAt(end) - .00001);
    for (int i = startIndex; i < stop && i < song.notes.size(); i++) {
      MidiSong.Note n = song.notes.get(i);
      if (n.pitch < 21 || n.pitch > 108 || trainer.role(n) == 3) continue;
      double b = ScoreLayout.quantize(n.beat);
      if (b < first.beat || b >= end) b = n.beat;
      int bar = b < second.beat ? 0 : 1;
      ScoreLayout.Bar grid = bar == 0 ? first : second;
      int staff = trainer.role(n) == 1 ? 1 : 0;
      has[bar][staff] = true;
      float x =
          left
              + bar * each
              + spacing * 1.9f
              + (float) ((b - grid.beat) / grid.length) * (each - spacing * 3.2f);
      int ref = staff == 0 ? ScoreLayout.diatonic(64) : ScoreLayout.diatonic(43);
      float baseline = (staff == 0 ? top : bass) + spacing * 4,
          y = baseline - (ScoreLayout.diatonic(n.pitch) - ref) * spacing * .5f;
      boolean active =
          !printing
              && ((down != null && down[n.pitch] && Math.abs(n.start - trainer.time) < .3)
                  || (i < trainer.judged.length && trainer.judged[i] == 1));
      int col =
          active
              ? Ui.accent(context)
              : !printing && Math.abs(n.start - trainer.time) < .08 ? 0xffc18b32 : fg;
      double value = ScoreLayout.quantize(n.endBeat - n.beat);
      String head = value >= 4 ? "\uE0A2" : value >= 2 ? "\uE0A3" : "\uE0A4";
      if (y < ((staff == 0 ? top : bass) - spacing * .5f) || y > baseline + spacing * .5f) {
        paint.setColor(muted);
        paint.setStrokeWidth(.9f);
        float base = staff == 0 ? top : bass;
        for (float yy = baseline + spacing; yy <= y + 1; yy += spacing)
          canvas.drawLine(x - spacing * .5f, yy, x + spacing * 1.6f, yy, paint);
        for (float yy = base - spacing; yy >= y - 1; yy -= spacing)
          canvas.drawLine(x - spacing * .5f, yy, x + spacing * 1.6f, yy, paint);
      }
      int pc = n.pitch % 12;
      if (pc == 1 || pc == 3 || pc == 6 || pc == 8 || pc == 10)
        glyph(canvas, "\uE262", x - spacing * 1.3f, y, spacing, col);
      glyph(canvas, head, x, y, spacing, col);
      if (value < 4) {
        boolean up = y >= (staff == 0 ? top : bass) + spacing * 2;
        float stemX = up ? x + spacing * 1.13f : x;
        paint.setColor(col);
        paint.setStrokeWidth(1.2f);
        float endY = y + (up ? -spacing * 3.3f : spacing * 3.3f);
        canvas.drawLine(stemX, y, stemX, endY, paint);
        if (value < 1) glyph(canvas, up ? "\uE240" : "\uE241", stemX, endY, spacing, col);
        if (value < .5) glyph(canvas, up ? "\uE242" : "\uE243", stemX, endY, spacing, col);
      }
      if (Math.abs(value - 1.5) < .1 || Math.abs(value - 3) < .1) {
        paint.setColor(col);
        canvas.drawCircle(x + spacing * 1.65f, y - spacing * .25f, spacing * .15f, paint);
      }
    }
    for (int bar = 0; bar < 2; bar++)
      for (int staff = 0; staff < 2; staff++)
        if (!has[bar][staff])
          glyph(
              canvas,
              "\uE4E3",
              left + bar * each + each * .5f,
              (staff == 0 ? top : bass) + spacing * 2,
              spacing,
              muted);
    if (!printing && currentBeat >= first.beat && currentBeat < end) {
      int bar = currentBeat < second.beat ? 0 : 1;
      ScoreLayout.Bar grid = bar == 0 ? first : second;
      float x =
          left
              + bar * each
              + spacing * 1.9f
              + (float) ((currentBeat - grid.beat) / grid.length) * (each - spacing * 3.2f);
      paint.setColor(Ui.accent(context));
      paint.setStrokeWidth(1);
      canvas.drawLine(x, top - spacing * .7f, x, bass + spacing * 4.7f, paint);
    }
    canvas.restore();
  }

  private int tempoAt(MidiSong song, double beat) {
    int value = 500000;
    for (MidiSong.Tempo t : song.tempos) {
      if (t.beat > beat) break;
      value = t.micros;
    }
    return value;
  }
}

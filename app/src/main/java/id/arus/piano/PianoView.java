package id.arus.piano;

import android.content.*;
import android.graphics.*;
import android.util.*;
import android.view.*;
import java.util.*;

/** Original Canvas renderer; one geometry shared by falling notes and touch keys. */
public final class PianoView extends View {
  public interface Host {
    void touch(int pitch, boolean on);

    void frame();
  }

  public int displayMode = 0;
  private final ScoreRenderer scoreRenderer;
  private final Host host;
  private final Trainer trainer;
  private final Paint paint = new Paint(3);
  private final RectF rect = new RectF();
  private final RectF[] keys = new RectF[128];
  private final SparseIntArray pointers = new SparseIntArray();
  public final boolean[] down = new boolean[128], sounding = new boolean[128];
  private final int mint = Color.rgb(89, 222, 206),
      blue = Color.rgb(131, 162, 255),
      ink = Color.rgb(12, 18, 32);
  private float density, top, bottom, keyTop, keyBottom;
  private long previous = 0;
  private boolean active = false;

  public PianoView(Context c, Trainer t, Host h) {
    super(c);
    trainer = t;
    host = h;
    scoreRenderer = new ScoreRenderer(c);
    density = getResources().getDisplayMetrics().density;
    setContentDescription("Piano multitouch 88 tuts, A0 sampai C8; not kiri biru, kanan hijau");
    setFocusable(true);
  }

  private boolean black(int n) {
    int p = n % 12;
    return p == 1 || p == 3 || p == 6 || p == 8 || p == 10;
  }

  public void start() {
    active = true;
    previous = 0;
    postInvalidateOnAnimation();
  }

  public void stop() {
    active = false;
    previous = 0;
    releaseTouches();
  }

  public void releaseTouches() {
    for (int i = 0; i < pointers.size(); i++) host.touch(pointers.valueAt(i), false);
    pointers.clear();
  }

  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    top = 54 * density;
    keyTop = h - Math.max(84 * density, h * .24f);
    bottom = keyTop - 8 * density;
    keyBottom = h - 10 * density;
    float unit = (w - 32 * density) / 52f, x = 16 * density;
    for (int n = 21; n <= 108; n++) {
      if (!black(n)) {
        keys[n] = new RectF(x, keyTop, x + unit - 1, keyBottom);
        x += unit;
      } else
        keys[n] =
            new RectF(
                x - unit * .32f, keyTop, x + unit * .32f, keyTop + (keyBottom - keyTop) * .62f);
    }
  }

  private void color(int c) {
    paint.setColor(c);
    paint.setShader(null);
    paint.setStyle(Paint.Style.FILL);
  }

  private void text(Canvas c, String s, float x, float y, float size, int col) {
    color(col);
    paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
    paint.setTextSize(size * density);
    c.drawText(s, x, y, paint);
  }

  private void box(Canvas c, float l, float t, float r, float b, float radius, int col) {
    color(col);
    rect.set(l, t, r, b);
    c.drawRoundRect(rect, radius, radius, paint);
  }

  @Override
  protected void onDraw(Canvas c) {
    super.onDraw(c);
    long now = android.os.SystemClock.elapsedRealtimeNanos();
    if (active) {
      if (previous != 0) trainer.update((now - previous) / 1e9);
      previous = now;
      host.frame();
    }
    c.drawColor(Ui.bg(getContext()));
    float d = density, w = getWidth();
    String state =
        trainer.listen
            ? Ui.tr(getContext(), "DENGARKAN", "LISTEN")
            : trainer.wait
                ? Ui.tr(getContext(), "LATIHAN · TUNGGU NOT", "PRACTICE · WAIT FOR NOTE")
                : Ui.tr(getContext(), "LATIHAN · RITME", "PRACTICE · RHYTHM");
    text(c, state, 18 * d, 24 * d, 12, Ui.accent(getContext()));
    text(
        c,
        String.format(
            Locale.US,
            "%02d:%02d  /  %02d:%02d",
            (int) trainer.time / 60,
            (int) trainer.time % 60,
            (int) trainer.song.duration / 60,
            (int) trainer.song.duration % 60),
        18 * d,
        44 * d,
        12,
        0xff91a0b8);
    String score =
        trainer.listen
            ? Ui.tr(getContext(), "Pratinjau offline", "Offline preview")
            : trainer.hits
                + Ui.tr(getContext(), " tepat  ·  ", " correct  ·  ")
                + trainer.misses
                + Ui.tr(getContext(), " lewat  ·  ", " missed  ·  ")
                + trainer.wrong
                + Ui.tr(getContext(), " salah  ·  ", " wrong  ·  ")
                + trainer.accuracy()
                + "%";
    paint.setTextSize(12 * d);
    text(
        c,
        score,
        Math.max(230 * d, w - paint.measureText(score) - 20 * d),
        30 * d,
        12,
        Ui.fg(getContext()));
    float savedTop = top;
    if (displayMode == 2) top = savedTop + (bottom - savedTop) * .46f;
    if (displayMode != 1) {
      color(Ui.light(getContext()) ? 0xffe6edf4 : 0xff141e30);
      c.drawRect(16 * d, top, w - 16 * d, bottom, paint);
      for (int n = 21; n <= 108; n++)
        if (!black(n)) {
          color(n % 12 == 0 ? 0xff2b3a53 : 0xff1b273b);
          c.drawLine(keys[n].left, top, keys[n].left, bottom, paint);
        }
      double horizon = 4.0;
      float rate = (bottom - top) / (float) horizon;
      for (int i = (int) Math.floor(trainer.time); i <= trainer.time + horizon; i++) {
        float y = bottom - (float) (i - trainer.time) * rate;
        color(0xff26334a);
        c.drawLine(16 * d, y, w - 16 * d, y, paint);
      }
      // Search the visible onset range; long sustained notes are retained via a cached prefix
      // maximum.
      ensurePrefix();
      int end = trainer.lower(trainer.time + horizon + .01);
      int first = prefixLower(trainer.time - .5);
      for (int i = first; i < end; i++) {
        MidiSong.Note n = trainer.song.notes.get(i);
        if (n.pitch < 21 || n.pitch > 108 || n.end < trainer.time - .5) continue;
        RectF k = keys[n.pitch];
        int role = trainer.role(n), col = role == 1 ? blue : mint;
        if (!trainer.selected(n)) col = 0xff3c4962;
        else if (trainer.judged[i] == 1) col = 0xffe6fff7;
        else if (trainer.judged[i] == 2) col = 0xffd27b88;
        float y1 = Math.max(top, bottom - (float) (n.end - trainer.time) * rate),
            y2 = Math.min(bottom, bottom - (float) (n.start - trainer.time) * rate);
        if (y2 >= top && y1 <= bottom && y2 >= y1) {
          float pad = black(n.pitch) ? 1 : 2;
          box(c, k.left + pad, y1, k.right - pad, Math.max(y1 + 4 * d, y2), 3 * d, col);
          color(0x55ffffff);
          c.drawRect(k.left + pad + 2, y1 + 2, k.right - pad - 2, Math.min(y1 + 4 * d, y2), paint);
        }
      }
      color(mint);
      paint.setStrokeWidth(2 * d);
      c.drawLine(16 * d, bottom, w - 16 * d, bottom, paint);
      if (!trainer.listen && trainer.wait && trainer.playing) {
        int next = trainer.next();
        if (next >= 0 && Math.abs(trainer.song.notes.get(next).start - trainer.time) < .01)
          text(
              c,
              Ui.tr(
                  getContext(),
                  "Mainkan not di garis untuk melanjutkan",
                  "Play the notes at the line to continue"),
              24 * d,
              top + 24 * d,
              12,
              Ui.fg(getContext()));
      }
    }
    if (displayMode != 0)
      scoreRenderer.draw(
          c,
          new RectF(16 * d, savedTop, w - 16 * d, displayMode == 2 ? top - 6 * d : bottom),
          trainer,
          down,
          -1,
          false);
    top = savedTop;
    for (int n = 21; n <= 108; n++) if (!black(n)) drawKey(c, n);
    for (int n = 21; n <= 108; n++) if (black(n)) drawKey(c, n);
    if (trainer.loop && trainer.loopB > trainer.loopA)
      text(
          c,
          String.format(Locale.US, "LOOP  %.1fs — %.1fs", trainer.loopA, trainer.loopB),
          w * .43f,
          44 * d,
          11,
          blue);
    boolean moving = active && trainer.playing;
    if (moving && !trainer.listen && trainer.wait) {
      int next = trainer.next();
      if (next >= 0 && trainer.song.notes.get(next).start <= trainer.time + .00001) moving = false;
    }
    if (moving) postInvalidateOnAnimation();
    else previous = 0;
  }

  private MidiSong cached;
  private double[] prefix;

  private void ensurePrefix() {
    if (cached == trainer.song) return;
    cached = trainer.song;
    prefix = new double[cached.notes.size()];
    double max = 0;
    for (int i = 0; i < prefix.length; i++) {
      max = Math.max(max, cached.notes.get(i).end);
      prefix[i] = max;
    }
  }

  private int prefixLower(double time) {
    int l = 0, h = prefix.length;
    while (l < h) {
      int m = (l + h) >>> 1;
      if (prefix[m] < time) l = m + 1;
      else h = m;
    }
    return l;
  }

  private void drawKey(Canvas c, int n) {
    RectF k = keys[n];
    int col = down[n] ? mint : sounding[n] ? 0xff91aafa : black(n) ? 0xff182237 : 0xffe2e9f3;
    box(c, k.left, k.top, k.right, k.bottom, black(n) ? 2 * density : 3 * density, col);
    if (!black(n)) {
      color(down[n] ? 0xff35a69e : 0xffb4c1d4);
      c.drawRect(k.left + 1, k.bottom - 5 * density, k.right - 1, k.bottom, paint);
    }
    if (n % 12 == 0 || n == 21) {
      String label = n == 21 ? "A0" : "C" + (n / 12 - 1);
      paint.setTextSize(8 * density);
      text(
          c,
          label,
          k.centerX() - paint.measureText(label) / 2,
          k.bottom - 12 * density,
          8,
          0xff5b6c84);
    }
    if (down[n] || sounding[n]) {
      color(0x8859dece);
      c.drawCircle(k.centerX(), k.top - 4 * density, 4 * density, paint);
    }
  }

  private int hit(float x, float y) {
    if (y < keyTop || y > keyBottom) return -1;
    for (int n = 21; n <= 108; n++) if (black(n) && keys[n].contains(x, y)) return n;
    for (int n = 21; n <= 108; n++) if (!black(n) && keys[n].contains(x, y)) return n;
    return -1;
  }

  @Override
  public boolean onTouchEvent(android.view.MotionEvent e) {
    int action = e.getActionMasked(), index = e.getActionIndex();
    if (action == MotionEvent.ACTION_CANCEL) {
      releaseTouches();
      return true;
    }
    if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
      int n = hit(e.getX(index), e.getY(index));
      if (n >= 0) {
        pointers.put(e.getPointerId(index), n);
        host.touch(n, true);
      }
      return true;
    }
    if (action == MotionEvent.ACTION_MOVE) {
      for (int i = 0; i < e.getPointerCount(); i++) {
        int id = e.getPointerId(i), old = pointers.get(id, -1), n = hit(e.getX(i), e.getY(i));
        if (old != n) {
          if (old >= 0) host.touch(old, false);
          if (n >= 0) {
            pointers.put(id, n);
            host.touch(n, true);
          } else pointers.delete(id);
        }
      }
      return true;
    }
    if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
      int id = e.getPointerId(index), n = pointers.get(id, -1);
      if (n >= 0) host.touch(n, false);
      pointers.delete(id);
      if (action == MotionEvent.ACTION_UP) performClick();
      return true;
    }
    return true;
  }

  @Override
  public boolean performClick() {
    super.performClick();
    return true;
  }
}

package id.arus.piano;

import android.content.Context;
import android.graphics.*;
import android.graphics.pdf.PdfDocument;
import java.io.*;

final class ScorePdf {
  static void write(Context context, Trainer trainer, OutputStream output) throws IOException {
    ScoreLayout grid = new ScoreLayout(trainer.song);
    int count = grid.count(), pages = (count + 5) / 6;
    if (pages > 300)
      throw new IOException(
          "Score exceeds safe PDF limit of 300 pages; export a shorter MIDI section");
    ScoreRenderer renderer = new ScoreRenderer(context);
    ScoreLayout.Bar bar = grid.at(0);
    PdfDocument pdf = new PdfDocument();
    try {
      for (int page = 0; page < Math.max(1, pages); page++) {
        PdfDocument.Page p =
            pdf.startPage(new PdfDocument.PageInfo.Builder(842, 595, page + 1).create());
        Canvas c = p.getCanvas();
        c.drawColor(Color.WHITE);
        Paint paint = new Paint(3);
        paint.setColor(0xff172237);
        paint.setTextSize(15);
        c.drawText("PianoFlow", 28, 28, paint);
        paint.setTextSize(10);
        c.drawText("" + (page + 1) + " / " + pages, 770, 28, paint);
        for (int row = 0; row < 3 && bar.number <= count; row++) {
          renderer.draw(
              c,
              new RectF(26, 42 + row * 174, 816, 210 + row * 174),
              trainer,
              null,
              bar.beat,
              true);
          bar = grid.next(grid.next(bar));
        }
        pdf.finishPage(p);
      }
      pdf.writeTo(output);
    } finally {
      pdf.close();
    }
  }
}

package id.arus.piano;

import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;

/** Test APK only: a read-only original MIDI fixture, never in the distributed application. */
public final class FixtureProvider extends ContentProvider {
  @Override
  public boolean onCreate() {
    return true;
  }

  @Override
  public String getType(Uri uri) {
    return "audio/midi";
  }

  @Override
  public Cursor query(Uri u, String[] p, String s, String[] a, String order) {
    MatrixCursor c = new MatrixCursor(new String[] {OpenableColumns.DISPLAY_NAME});
    c.addRow(new Object[] {u.getLastPathSegment()});
    return c;
  }

  @Override
  public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
    if (!"r".equals(mode)) throw new FileNotFoundException("read only");
    File f = new File(getContext().getCacheDir(), "fixture.mid");
    try (DataOutputStream d = new DataOutputStream(new FileOutputStream(f))) {
      if (uri.getLastPathSegment().equals("rusak.mid")) {
        d.writeBytes("not midi");
      } else {
        byte[] track = {
          0,
          (byte) 0x90,
          60,
          90,
          0,
          64,
          90,
          (byte) 0x83,
          0x60,
          (byte) 0x80,
          60,
          0,
          0,
          64,
          0,
          0,
          (byte) 0xff,
          0x2f,
          0
        };
        d.writeBytes("MThd");
        d.writeInt(6);
        d.writeShort(0);
        d.writeShort(1);
        d.writeShort(480);
        d.writeBytes("MTrk");
        d.writeInt(track.length);
        d.write(track);
      }
    } catch (IOException e) {
      throw new FileNotFoundException(e.getMessage());
    }
    return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
  }

  @Override
  public Uri insert(Uri u, ContentValues v) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int update(Uri u, ContentValues v, String s, String[] a) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int delete(Uri u, String s, String[] a) {
    throw new UnsupportedOperationException();
  }
}

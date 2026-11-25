package id.arus.piano;

import android.content.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Private, content-addressed MIDI library. No account, count quota or destructive update migration.
 */
final class LibraryStore {
  private final File directory;

  LibraryStore(Context c) {
    directory = new File(c.getFilesDir(), "songs");
    if (!directory.isDirectory()) directory.mkdirs();
  }

  File put(MidiSong song, String name) throws Exception {
    File temporary = File.createTempFile("import-", ".tmp", directory);
    try (OutputStream out = new FileOutputStream(temporary)) {
      MidiWriter.write(song, out);
    }
    String hash = UpdateClient.sha256(temporary);
    File target = new File(directory, hash + ".mid");
    if (target.isFile()) temporary.delete();
    else if (!temporary.renameTo(target)) throw new IOException("Cannot save song");
    try (OutputStream out = new FileOutputStream(new File(directory, hash + ".name"))) {
      out.write(name.getBytes(StandardCharsets.UTF_8));
    }
    return target;
  }

  List<File> files() {
    File[] files = directory.listFiles((d, n) -> n.endsWith(".mid"));
    List<File> list = new ArrayList<>();
    if (files != null) Collections.addAll(list, files);
    list.sort((a, b) -> Long.compare(b.lastModified(), a.lastModified()));
    return list;
  }

  String name(File file) {
    File metadata = new File(directory, file.getName().replace(".mid", ".name"));
    try (InputStream input = new FileInputStream(metadata);
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      byte[] b = new byte[1024];
      int n;
      while ((n = input.read(b)) != -1 && out.size() < 16384) out.write(b, 0, n);
      return out.toString("UTF-8");
    } catch (IOException e) {
      return file.getName();
    }
  }
}

package id.arus.piano;

import android.content.*;

/** Separate per-song preferences; legacy song files/checkpoints are never rewritten. */
final class PartSettings {
  static void load(Context context, String song, Arrangement arrangement) {
    SharedPreferences p = context.getSharedPreferences("arrangements", 0);
    String prefix = "song:" + song;
    arrangement.backingVolume = Math.max(0, Math.min(100, p.getInt(prefix + ":backing", 70)));
    for (Arrangement.Part part : arrangement.parts) {
      String key = prefix + ":" + part.key;
      part.mode = Math.max(0, Math.min(2, p.getInt(key + ":mode", part.mode)));
      part.volume = Math.max(0, Math.min(100, p.getInt(key + ":volume", 100)));
      part.hand = Math.max(-1, Math.min(2, p.getInt(key + ":hand", -1)));
    }
  }

  static void savePart(Context context, String song, Arrangement.Part part) {
    String key = "song:" + song + ":" + part.key;
    context
        .getSharedPreferences("arrangements", 0)
        .edit()
        .putInt(key + ":mode", part.mode)
        .putInt(key + ":volume", part.volume)
        .putInt(key + ":hand", part.hand)
        .apply();
  }

  static void saveMix(Context context, String song, int volume) {
    context
        .getSharedPreferences("arrangements", 0)
        .edit()
        .putInt("song:" + song + ":backing", volume)
        .apply();
  }

  static void save(Context context, String song, Arrangement arrangement) {
    SharedPreferences.Editor e = context.getSharedPreferences("arrangements", 0).edit();
    String prefix = "song:" + song;
    e.putInt(prefix + ":backing", arrangement.backingVolume);
    for (Arrangement.Part p : arrangement.parts) {
      String key = prefix + ":" + p.key;
      e.putInt(key + ":mode", p.mode)
          .putInt(key + ":volume", p.volume)
          .putInt(key + ":hand", p.hand);
    }
    e.apply();
  }
}

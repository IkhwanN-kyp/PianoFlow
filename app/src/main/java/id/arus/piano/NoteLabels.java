package id.arus.piano;

/** Fixed C-based note names; numbers/solfege are learning labels, not transposition. */
public final class NoteLabels {
  public static final int LETTERS = 0, NUMBERS = 1, SOLFEGE = 2, NONE = 3;
  private static final String[][] NAMES = {
    {"C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B"},
    {"1", "1♯", "2", "2♯", "3", "4", "4♯", "5", "5♯", "6", "6♯", "7"},
    {"Do", "Do♯", "Re", "Re♯", "Mi", "Fa", "Fa♯", "Sol", "Sol♯", "La", "La♯", "Si"}
  };

  public static int valid(int mode) {
    return mode >= 0 && mode <= NONE ? mode : LETTERS;
  }

  public static String key(int pitch, int mode) {
    if (pitch < 0 || pitch > 127 || valid(mode) == NONE) return "";
    return NAMES[valid(mode)][pitch % 12];
  }

  public static String scientific(int pitch) {
    if (pitch < 0 || pitch > 127) return "";
    return NAMES[LETTERS][pitch % 12] + (pitch / 12 - 1);
  }
}

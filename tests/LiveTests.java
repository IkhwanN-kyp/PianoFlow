package id.arus.piano;

public final class LiveTests {
  static int checks;

  static void ok(boolean value, String label) {
    checks++;
    if (!value) throw new AssertionError(label);
  }

  static void near(double actual, double expected, String label) {
    ok(Math.abs(actual - expected) < .00001, label);
  }

  public static void main(String[] args) {
    FreePlay live = new FreePlay();
    live.press(60, 85, 10);
    live.press(64, 120, 10);
    live.press(67, 70, 10.2);
    ok(live.notes().size() == 3, "chord trails remain independent");
    FreePlay.Note c = live.notes().get(0);
    near(c.age(11), 1, "head rises from physical onset");
    near(c.releaseAge(11), 0, "tail remains at keyboard while held");
    live.release(60, 11.5);
    near(c.releaseAge(12), .5, "released tail rises");
    near(c.age(12) - c.releaseAge(12), 1.5, "released bar preserves held duration");
    ok(c.velocity == 85 && live.notes().get(1).velocity == 120, "input velocity retained");
    live.prune(17);
    ok(live.notes().size() == 2 && !live.notes().contains(c), "expired released trail is removed");
    live.prune(1000);
    ok(live.notes().size() == 2, "long-held notes stay visible beyond window");
    live.press(64, 100, 1001);
    ok(
        !live.notes().get(0).held() && live.notes().get(2).held(),
        "same-pitch retrigger closes previous trail");
    live.clear();
    ok(!live.moving(1001) && live.notes().isEmpty(), "clear removes held and released notes");
    live.press(20, 90, 0);
    live.press(109, 90, 0);
    live.press(60, 0, 0);
    live.press(60, 90, Double.NaN);
    ok(live.notes().isEmpty(), "out-of-range, zero velocity and invalid clock rejected");
    live.release(-1, 0);
    live.release(128, 0);
    live.release(60, Double.NaN);
    live.press(60, 999, 1);
    live.release(60, .5);
    ok(
        live.notes().get(0).velocity == 127 && live.notes().get(0).end == 1,
        "velocity clamped and negative duration prevented");
    live.clear();
    live.press(48, 100, 0);
    for (int i = 0; i < 2000; i++) {
      live.press(60, 90, 1 + i * .0001);
      live.release(60, 1 + i * .0001);
    }
    ok(live.notes().size() <= FreePlay.LIMIT, "rapid input has bounded memory");
    ok(
        live.notes().get(0).pitch == 48 && live.notes().get(0).held(),
        "memory bound preserves held trail");
    live.prune(10);
    ok(live.notes().size() == 1, "rapid released trails expire");
    String[] letters = {"C", "D", "E", "F", "G", "A", "B"},
        numbers = {"1", "2", "3", "4", "5", "6", "7"},
        solfege = {"Do", "Re", "Mi", "Fa", "Sol", "La", "Si"};
    int[] pitches = {60, 62, 64, 65, 67, 69, 71};
    for (int i = 0; i < pitches.length; i++) {
      ok(NoteLabels.key(pitches[i], 0).equals(letters[i]), "letter mapping " + pitches[i]);
      ok(NoteLabels.key(pitches[i], 1).equals(numbers[i]), "fixed-C number mapping " + pitches[i]);
      ok(NoteLabels.key(pitches[i], 2).equals(solfege[i]), "fixed-Do mapping " + pitches[i]);
    }
    ok(
        NoteLabels.key(61, 0).equals("C♯")
            && NoteLabels.key(61, 1).equals("1♯")
            && NoteLabels.key(61, 2).equals("Do♯"),
        "chromatic keys retain sharp label");
    ok(
        NoteLabels.scientific(21).equals("A0")
            && NoteLabels.scientific(60).equals("C4")
            && NoteLabels.scientific(108).equals("C8"),
        "88-key octave anchors");
    for (int pitch = 21; pitch <= 108; pitch++) {
      for (int mode = 0; mode < 3; mode++)
        ok(!NoteLabels.key(pitch, mode).isEmpty(), "all 88 keys labelled");
      ok(NoteLabels.key(pitch, NoteLabels.NONE).isEmpty(), "labels can be hidden");
    }
    ok(
        NoteLabels.key(-1, 0).isEmpty()
            && NoteLabels.key(128, 1).isEmpty()
            && NoteLabels.valid(999) == 0,
        "invalid label settings fall back safely");
    System.out.println(
        "PASS live learning: "
            + checks
            + " checks (rising trails, lifecycle bounds, 88-key labels)");
  }
}

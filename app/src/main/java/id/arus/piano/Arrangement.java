package id.arus.piano;

import java.util.*;

/** Song parts retain SMF track/channel identities; controllers remain MIDI channel state. */
public final class Arrangement {
  public static final int PLAY = 0, BACKING = 1, MUTE = 2;

  public static final class Part {
    public final int key, track, channel, program, noteCount;
    public final String name;
    public final double first;
    public final boolean changingProgram, sharedChannel;
    public int mode = PLAY, volume = 100, hand = -1, instrument = -1;

    public int selectedProgram() {
      return instrument < 0 ? program : instrument;
    }

    Part(
        int key,
        String name,
        int program,
        int count,
        double first,
        boolean changes,
        boolean shared) {
      this.key = key;
      track = key >>> 4;
      channel = key & 15;
      this.name = name;
      this.program = program;
      noteCount = count;
      this.first = first;
      changingProgram = changes;
      sharedChannel = shared;
    }
  }

  public final List<Part> parts;
  public int backingVolume = 70;
  private final Map<Integer, Part> byKey = new HashMap<>();

  public static int key(MidiSong.Note note) {
    return (note.track << 4) | note.channel;
  }

  public Arrangement(MidiSong song) {
    Map<Integer, List<MidiSong.Note>> groups = new TreeMap<>();
    int[] channels = new int[16];
    for (MidiSong.Note n : song.notes)
      groups.computeIfAbsent(key(n), k -> new ArrayList<>()).add(n);
    for (int key : groups.keySet()) channels[key & 15]++;
    ArrayList<Part> list = new ArrayList<>();
    List<List<MidiSong.Message>> programsByChannel = new ArrayList<>();
    List<Set<Integer>> distinctPrograms = new ArrayList<>();
    for (int ch = 0; ch < 16; ch++) {
      programsByChannel.add(new ArrayList<>());
      distinctPrograms.add(new HashSet<>());
    }
    for (MidiSong.Message message : song.messages)
      if ((message.status & 240) == 0xc0) {
        programsByChannel.get(message.status & 15).add(message);
        distinctPrograms.get(message.status & 15).add(message.a);
      }
    for (List<MidiSong.Message> messages : programsByChannel)
      messages.sort(Comparator.comparingDouble(m -> m.time));
    boolean piano = false, other = false;
    for (Map.Entry<Integer, List<MidiSong.Note>> entry : groups.entrySet()) {
      int key = entry.getKey(), ch = key & 15, program = 0;
      double first = entry.getValue().get(0).start;
      List<MidiSong.Message> changes = programsByChannel.get(ch);
      int lo = 0, hi = changes.size();
      while (lo < hi) {
        int mid = (lo + hi) >>> 1;
        if (changes.get(mid).time <= first + .000001) lo = mid + 1;
        else hi = mid;
      }
      if (lo > 0) program = changes.get(lo - 1).a;
      boolean changing =
          distinctPrograms.get(ch).size() > 1
              || (lo == 0 && !changes.isEmpty() && !distinctPrograms.get(ch).contains(0));
      Part part =
          new Part(
              key,
              song.tracks.getOrDefault(key >>> 4, "Track " + ((key >>> 4) + 1)),
              program,
              entry.getValue().size(),
              first,
              changing,
              channels[ch] > 1);
      list.add(part);
      byKey.put(key, part);
      if (program < 8) piano = true;
      else other = true;
    }
    // A conservative suggestion: only choose accompaniment when a piano part also exists.
    if (piano && other) for (Part part : list) if (part.program >= 8) part.mode = BACKING;
    parts = Collections.unmodifiableList(list);
  }

  public Part part(MidiSong.Note note) {
    return byKey.get(key(note));
  }

  public Part get(int key) {
    return byKey.get(key);
  }

  public int mode(MidiSong.Note note) {
    Part p = part(note);
    return p == null ? PLAY : p.mode;
  }

  public int velocity(MidiSong.Note note) {
    Part p = part(note);
    double gain =
        p == null ? 1 : p.volume / 100.0 * (p.mode == BACKING ? backingVolume / 100.0 : 1);
    return Math.max(0, Math.min(127, (int) Math.round(note.velocity * gain)));
  }

  public String learningSignature() {
    StringBuilder result = new StringBuilder();
    for (Part part : parts)
      result
          .append(part.key)
          .append(':')
          .append(part.mode)
          .append(':')
          .append(part.hand)
          .append(';');
    return result.toString();
  }

  public void copyFrom(Arrangement other) {
    backingVolume = other.backingVolume;
    for (Part p : parts) {
      Part from = other.get(p.key);
      if (from == null) continue;
      p.mode = from.mode;
      p.volume = from.volume;
      p.hand = from.hand;
      p.instrument = from.instrument;
    }
  }
}

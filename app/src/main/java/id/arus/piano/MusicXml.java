package id.arus.piano;

import java.io.*;
import java.util.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;

/**
 * Secure, partwise MusicXML importer. Supports divisions, chord, backup, forward, staff, meter and
 * tempo.
 */
public final class MusicXml {
  private static String text(Element e, String name, String fallback) {
    NodeList n = e.getElementsByTagName(name);
    return n.getLength() == 0 ? fallback : n.item(0).getTextContent().trim();
  }

  private static double number(Element e, String name, double fallback) throws IOException {
    try {
      return Double.parseDouble(text(e, name, "" + fallback));
    } catch (NumberFormatException x) {
      throw new IOException("Invalid MusicXML " + name);
    }
  }

  private static Element child(Element e, String name) {
    for (Node n = e.getFirstChild(); n != null; n = n.getNextSibling())
      if (n instanceof Element && n.getNodeName().equals(name)) return (Element) n;
    return null;
  }

  private static final class Raw {
    int pitch, track, channel, velocity;
    double beat, end;
  }

  public static MidiSong read(InputStream input) throws IOException {
    try {
      ByteArrayOutputStream data = new ByteArrayOutputStream();
      byte[] buffer = new byte[8192];
      int n;
      while ((n = input.read(buffer)) != -1) {
        if (data.size() + n > 16 * 1024 * 1024)
          throw new IOException("MusicXML exceeds safe 16 MiB memory limit");
        data.write(buffer, 0, n);
      }
      byte[] xmlBytes = data.toByteArray();
      String declarations =
          new String(xmlBytes, java.nio.charset.StandardCharsets.UTF_8)
              .replace("\u0000", "")
              .toUpperCase(Locale.ROOT);
      if (declarations.contains("<!DOCTYPE") || declarations.contains("<!ENTITY"))
        throw new IOException("MusicXML DTD/entity declarations are not allowed");
      DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
      String[] features = {
        "http://apache.org/xml/features/disallow-doctype-decl",
        "http://xml.org/sax/features/external-general-entities",
        "http://xml.org/sax/features/external-parameter-entities"
      };
      for (int feature = 0; feature < features.length; feature++)
        try {
          f.setFeature(features[feature], feature == 0);
        } catch (ParserConfigurationException ignored) {
          /* DTD/entity preflight and denying resolver also protect Android parsers without these switches. */
        }
      try {
        f.setXIncludeAware(false);
      } catch (UnsupportedOperationException ignored) {
      }
      f.setExpandEntityReferences(false);
      DocumentBuilder builder = f.newDocumentBuilder();
      builder.setEntityResolver(
          (publicId, systemId) -> {
            throw new org.xml.sax.SAXException("External XML references are not allowed");
          });
      Document doc = builder.parse(new ByteArrayInputStream(xmlBytes));
      if (!doc.getDocumentElement().getTagName().equals("score-partwise"))
        throw new IOException("Use uncompressed score-partwise MusicXML");
      List<Raw> raw = new ArrayList<>();
      Map<Integer, String> names = new TreeMap<>();
      TreeMap<Double, Integer> tempoMap = new TreeMap<>();
      tempoMap.put(0.0, 500000);
      TreeMap<Double, MidiSong.Meter> meterMap = new TreeMap<>();
      meterMap.put(0.0, new MidiSong.Meter(0, 4, 4));
      NodeList parts = doc.getDocumentElement().getElementsByTagName("part");
      int partIndex = 0;
      for (int p = 0; p < parts.getLength(); p++) {
        Element part = (Element) parts.item(p);
        double measureBeat = 0, divisions = 1, barLength = 4;
        int channel = partIndex % 16;
        if (channel == 9) channel = 0;
        for (Node measureNode = part.getFirstChild();
            measureNode != null;
            measureNode = measureNode.getNextSibling()) {
          if (!(measureNode instanceof Element) || !measureNode.getNodeName().equals("measure"))
            continue;
          Element measure = (Element) measureNode;
          double cursor = 0, lastStart = 0, max = 0;
          for (Node node = measure.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (!(node instanceof Element)) continue;
            Element e = (Element) node;
            String tag = e.getTagName();
            if (tag.equals("attributes")) {
              divisions = number(e, "divisions", divisions);
              if (divisions <= 0) throw new IOException("Invalid divisions");
              Element time = child(e, "time");
              if (time != null) {
                int beats = (int) number(time, "beats", 4),
                    unit = (int) number(time, "beat-type", 4);
                if (beats <= 0 || unit <= 0 || unit > 128 || (unit & (unit - 1)) != 0)
                  throw new IOException("Unsupported meter");
                barLength = beats * 4.0 / unit;
                meterMap.put(measureBeat, new MidiSong.Meter(measureBeat, beats, unit));
              }
            } else if (tag.equals("backup")) {
              cursor -= number(e, "duration", 0) / divisions;
              if (cursor < -.001) throw new IOException("MusicXML backup before measure start");
              cursor = Math.max(0, cursor);
            } else if (tag.equals("forward")) {
              cursor += number(e, "duration", 0) / divisions;
              max = Math.max(max, cursor);
            } else if (tag.equals("direction")) {
              Element sound = child(e, "sound");
              double bpm =
                  sound != null && sound.hasAttribute("tempo")
                      ? Double.parseDouble(sound.getAttribute("tempo"))
                      : number(e, "per-minute", 0);
              if (bpm > 0) tempoMap.put(measureBeat + cursor, (int) Math.round(60000000 / bpm));
            } else if (tag.equals("note")) {
              boolean chord = child(e, "chord") != null,
                  rest = child(e, "rest") != null,
                  grace = child(e, "grace") != null;
              double duration = number(e, "duration", 0) / divisions,
                  start = chord ? lastStart : cursor;
              Element pitch = child(e, "pitch");
              if (!rest && !grace && pitch != null && duration > 0) {
                String step = text(pitch, "step", "C");
                int[] offsets = {0, 2, 4, 5, 7, 9, 11};
                int index = "CDEFGAB".indexOf(step);
                if (index < 0) throw new IOException("Invalid pitch");
                int key =
                    ((int) number(pitch, "octave", 4) + 1) * 12
                        + offsets[index]
                        + (int) number(pitch, "alter", 0);
                if (key < 0 || key > 127) throw new IOException("Pitch out of range");
                Raw r = new Raw();
                r.pitch = key;
                r.beat = measureBeat + start;
                r.end = r.beat + duration;
                int staff = (int) number(e, "staff", key < 60 ? 2 : 1);
                r.track = partIndex * 2 + (staff == 2 ? 0 : 1);
                r.channel = channel;
                r.velocity = 85;
                raw.add(r);
                names.put(
                    r.track, "Part " + (partIndex + 1) + (staff == 2 ? " · bass" : " · treble"));
                if (raw.size() > 250000) throw new IOException("MusicXML too dense for memory");
              }
              if (!chord) {
                lastStart = cursor;
                cursor += duration;
              }
              max = Math.max(max, start + duration);
            }
          }
          // Implicit/pickup measures advance by written duration; regular measures at least one
          // full bar.
          measureBeat +=
              ("yes".equals(measure.getAttribute("implicit")) ? max : Math.max(max, barLength));
        }
        partIndex++;
      }
      if (raw.isEmpty()) throw new IOException("No pitched notes in MusicXML");
      List<MidiSong.Tempo> tempos = new ArrayList<>();
      double seconds = 0, lastBeat = 0;
      int micros = 500000;
      for (Map.Entry<Double, Integer> e : tempoMap.entrySet()) {
        seconds += (e.getKey() - lastBeat) * micros / 1000000.0;
        lastBeat = e.getKey();
        micros = e.getValue();
        tempos.add(new MidiSong.Tempo(seconds, lastBeat, micros));
      }
      MidiSong timing =
          new MidiSong(
              new ArrayList<>(),
              names,
              0,
              tempos,
              new ArrayList<>(meterMap.values()),
              Collections.emptyList());
      List<MidiSong.Note> notes = new ArrayList<>();
      for (Raw r : raw)
        notes.add(
            new MidiSong.Note(
                r.pitch,
                r.velocity,
                r.channel,
                r.track,
                timing.secondsAt(r.beat),
                timing.secondsAt(r.end),
                r.beat,
                r.end));
      return new MidiSong(
          notes, names, 0, tempos, new ArrayList<>(meterMap.values()), Collections.emptyList());
    } catch (IOException e) {
      throw e;
    } catch (Exception e) {
      throw new IOException("MusicXML: " + e.getMessage(), e);
    }
  }
}

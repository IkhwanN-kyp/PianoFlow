package id.arus.piano;

import java.math.BigInteger;
import java.util.regex.*;

/** Semantic Versioning 2.0 comparison, including numeric prerelease ordering. */
public final class ReleaseVersion implements Comparable<ReleaseVersion> {
  private static final Pattern PATTERN =
      Pattern.compile(
          "^v?(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)(?:-([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?$");
  private final BigInteger[] core;
  private final String[] pre;

  public ReleaseVersion(String text) {
    Matcher m = PATTERN.matcher(text);
    if (!m.matches()) throw new IllegalArgumentException("Invalid semantic version");
    core =
        new BigInteger[] {
          new BigInteger(m.group(1)), new BigInteger(m.group(2)), new BigInteger(m.group(3))
        };
    pre = m.group(4) == null ? new String[0] : m.group(4).split("\\.");
    for (String p : pre)
      if (p.matches("[0-9]+") && p.length() > 1 && p.startsWith("0"))
        throw new IllegalArgumentException("Leading zero in prerelease");
  }

  public int compareTo(ReleaseVersion other) {
    for (int i = 0; i < 3; i++) {
      int c = core[i].compareTo(other.core[i]);
      if (c != 0) return c;
    }
    if (pre.length == 0 || other.pre.length == 0)
      return pre.length == other.pre.length ? 0 : pre.length == 0 ? 1 : -1;
    for (int i = 0; i < Math.min(pre.length, other.pre.length); i++) {
      String a = pre[i], b = other.pre[i];
      boolean an = a.matches("[0-9]+"), bn = b.matches("[0-9]+");
      int c =
          an && bn
              ? new BigInteger(a).compareTo(new BigInteger(b))
              : an != bn ? (an ? -1 : 1) : a.compareTo(b);
      if (c != 0) return c;
    }
    return Integer.compare(pre.length, other.pre.length);
  }
}

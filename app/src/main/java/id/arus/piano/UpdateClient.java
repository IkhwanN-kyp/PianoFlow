package id.arus.piano;

import android.content.*;
import android.content.pm.*;
import android.os.Build;
import com.android.apksig.ApkVerifier;
import java.io.*;
import java.net.*;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.*;
import org.json.*;

/**
 * Public GitHub Releases only. APK is verified cryptographically before invoking the OS installer.
 */
public final class UpdateClient {
  public static final String REPO = BuildConfig.UPDATE_REPOSITORY;
  public static final String CERT =
      "eda0f798078f5eb48b7237301a2e4261544639d333625ab6c4b265f55862636d";

  public interface Progress {
    void update(long done, long total);
  }

  public static final class Release {
    public String version, notes, apkUrl, manifestUrl, sha, certificate;
    public long size, code;
    public boolean beta;
  }

  private static boolean safeHost(String host) {
    return host.equals("api.github.com")
        || host.equals("github.com")
        || host.endsWith(".githubusercontent.com");
  }

  private static HttpURLConnection open(String url) throws IOException {
    for (int redirects = 0; redirects < 6; redirects++) {
      URL u = new URL(url);
      if (!u.getProtocol().equals("https") || !safeHost(u.getHost()))
        throw new IOException("Untrusted update URL");
      HttpURLConnection c = (HttpURLConnection) u.openConnection();
      c.setConnectTimeout(15000);
      c.setReadTimeout(30000);
      c.setInstanceFollowRedirects(false);
      c.setRequestProperty("Accept", "application/vnd.github+json");
      c.setRequestProperty("User-Agent", "PianoFlow/" + BuildConfig.VERSION_NAME);
      int status = c.getResponseCode();
      if (status >= 300 && status < 400) {
        String location = c.getHeaderField("Location");
        c.disconnect();
        if (location == null) throw new IOException("Missing redirect");
        url = new URL(u, location).toString();
        continue;
      }
      if (status != 200) {
        c.disconnect();
        throw new IOException(
            status == 403
                ? "GitHub rate limit or access denied; retry later"
                : "GitHub HTTP " + status);
      }
      return c;
    }
    throw new IOException("Too many redirects");
  }

  private static String read(String url, int limit) throws IOException {
    HttpURLConnection c = open(url);
    try (InputStream in = c.getInputStream();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
      byte[] b = new byte[8192];
      int n;
      while ((n = in.read(b)) != -1) {
        if (bytes.size() + n > limit) throw new IOException("Update response too large");
        bytes.write(b, 0, n);
      }
      return bytes.toString("UTF-8");
    } finally {
      c.disconnect();
    }
  }

  public static Release latest(boolean beta) throws Exception {
    JSONArray releases =
        new JSONArray(
            read(
                "https://api.github.com/repos/" + REPO + "/releases?per_page=100",
                2 * 1024 * 1024));
    JSONObject best = null;
    ReleaseVersion current = null;
    for (int i = 0; i < releases.length(); i++) {
      JSONObject r = releases.getJSONObject(i);
      if (r.optBoolean("draft") || (!beta && r.optBoolean("prerelease"))) continue;
      String tag = r.optString("tag_name");
      ReleaseVersion version;
      try {
        version = new ReleaseVersion(tag);
      } catch (IllegalArgumentException e) {
        continue;
      }
      if (!beta && tag.contains("-")) continue;
      JSONArray assets = r.getJSONArray("assets");
      boolean manifest = false;
      for (int a = 0; a < assets.length(); a++)
        if (assets.getJSONObject(a).optString("name").equals("update.json")) manifest = true;
      if (!manifest) continue;
      if (current == null || version.compareTo(current) > 0) {
        current = version;
        best = r;
      }
    }
    if (best == null) return null;
    Release result = new Release();
    result.version = best.getString("tag_name").replaceFirst("^v", "");
    result.notes = best.optString("body", "");
    result.beta = best.optBoolean("prerelease");
    JSONArray assets = best.getJSONArray("assets");
    String prefix =
        "https://github.com/" + REPO + "/releases/download/" + best.getString("tag_name") + "/";
    for (int a = 0; a < assets.length(); a++) {
      JSONObject asset = assets.getJSONObject(a);
      if (asset.getString("name").equals("update.json")) {
        result.manifestUrl = asset.getString("browser_download_url");
        if (!result.manifestUrl.startsWith(prefix))
          throw new IOException("Unexpected manifest source");
      }
    }
    JSONObject manifest = new JSONObject(read(result.manifestUrl, 16384));
    if (!manifest.getString("packageId").equals("id.arus.piano")
        || !manifest.getString("versionName").equals(result.version))
      throw new IOException("Release metadata mismatch");
    result.code = manifest.getLong("versionCode");
    result.sha = manifest.getString("sha256").toLowerCase(Locale.ROOT);
    result.certificate = manifest.getString("signerSha256").toLowerCase(Locale.ROOT);
    result.size = manifest.getLong("size");
    String name = manifest.getString("apk");
    if (!result.sha.matches("[0-9a-f]{64}")
        || !result.certificate.equals(CERT)
        || result.size <= 0
        || result.size > 1024L * 1024 * 1024
        || !name.matches("PianoFlow-[A-Za-z0-9.+-]+-arm64.apk"))
      throw new IOException("Invalid release trust metadata");
    for (int a = 0; a < assets.length(); a++) {
      JSONObject asset = assets.getJSONObject(a);
      if (asset.getString("name").equals(name)) {
        result.apkUrl = asset.getString("browser_download_url");
        if (asset.getLong("size") != result.size) throw new IOException("APK size mismatch");
      }
    }
    if (result.apkUrl == null || !result.apkUrl.startsWith(prefix))
      throw new IOException("APK release asset missing");
    return result;
  }

  public static long installedCode(Context c) throws PackageManager.NameNotFoundException {
    PackageInfo p = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
    return Build.VERSION.SDK_INT >= 28 ? p.getLongVersionCode() : p.versionCode;
  }

  public static File download(Context context, Release r, Progress progress) throws Exception {
    File directory = new File(context.getFilesDir(), "updates");
    if (!directory.isDirectory() && !directory.mkdirs())
      throw new IOException("Cannot create private update directory");
    File temp = new File(directory, "update.part"), apk = new File(directory, "verified.apk");
    boolean complete = false;
    HttpURLConnection c = open(r.apkUrl);
    try (InputStream input = c.getInputStream();
        OutputStream out = new FileOutputStream(temp)) {
      byte[] b = new byte[32768];
      long total = 0;
      int n;
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      while ((n = input.read(b)) != -1) {
        if (Thread.currentThread().isInterrupted())
          throw new InterruptedIOException("Download cancelled");
        total += n;
        if (total > r.size) throw new IOException("Downloaded APK larger than release");
        out.write(b, 0, n);
        digest.update(b, 0, n);
        progress.update(total, r.size);
      }
      out.flush();
      if (total != r.size || !hex(digest.digest()).equals(r.sha))
        throw new SecurityException("APK checksum mismatch");
      complete = true;
    } finally {
      c.disconnect();
      if (!complete) temp.delete();
    }
    try {
      verify(context, temp, r);
      if (apk.exists() && !apk.delete()) throw new IOException("Cannot replace old update");
      if (!temp.renameTo(apk)) throw new IOException("Cannot store verified APK");
      return apk;
    } catch (Exception e) {
      temp.delete();
      throw e;
    }
  }

  public static void verify(Context context, File apk, Release release) throws Exception {
    if (!sha256(apk).equals(release.sha)) throw new SecurityException("APK checksum mismatch");
    ApkVerifier.Result result =
        new ApkVerifier.Builder(apk)
            .setMinCheckedPlatformVersion(24)
            .setMaxCheckedPlatformVersion(Build.VERSION.SDK_INT)
            .build()
            .verify();
    if (!result.isVerified())
      throw new SecurityException("Invalid APK cryptographic signature: " + result.getErrors());
    List<X509Certificate> certs = result.getSignerCertificates();
    if (certs.size() != 1
        || !hex(MessageDigest.getInstance("SHA-256").digest(certs.get(0).getEncoded()))
            .equals(CERT)) throw new SecurityException("APK signing certificate mismatch");
    PackageManager pm = context.getPackageManager();
    PackageInfo self = pm.getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNATURES);
    if (self.signatures == null
        || self.signatures.length != 1
        || !hex(MessageDigest.getInstance("SHA-256").digest(self.signatures[0].toByteArray()))
            .equals(CERT))
      throw new SecurityException("Installed app is not signed with the official key");
    PackageInfo candidate = pm.getPackageArchiveInfo(apk.getAbsolutePath(), 0);
    if (candidate == null || !candidate.packageName.equals(context.getPackageName()))
      throw new SecurityException("APK package ID mismatch");
    long code =
        Build.VERSION.SDK_INT >= 28 ? candidate.getLongVersionCode() : candidate.versionCode;
    if (code != release.code
        || code <= installedCode(context)
        || !release.version.equals(candidate.versionName)
        || new ReleaseVersion(candidate.versionName)
                .compareTo(new ReleaseVersion(BuildConfig.VERSION_NAME))
            <= 0) throw new SecurityException("Downgrade or version mismatch rejected");
  }

  public static String sha256(File file) throws Exception {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    try (InputStream input = new FileInputStream(file)) {
      byte[] b = new byte[32768];
      int n;
      while ((n = input.read(b)) != -1) digest.update(b, 0, n);
    }
    return hex(digest.digest());
  }

  public static String hex(byte[] bytes) {
    StringBuilder s = new StringBuilder();
    for (byte b : bytes) s.append(String.format(Locale.ROOT, "%02x", b & 255));
    return s.toString();
  }
}

package id.arus.piano;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public final class SoftwareUpdateActivity extends Activity {
  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private TextView installed, latest, changelog, status;
  private ProgressBar progress;
  private Button check, download;
  private UpdateClient.Release release;
  private File apk;
  private volatile boolean busy = false, closed = false, installing = false;
  private boolean beta;

  private String tr(String id, String en) {
    return Ui.tr(this, id, en);
  }

  @Override
  public void onCreate(Bundle b) {
    Ui.theme(this);
    super.onCreate(b);
    Ui.immersive(this);
    beta =
        getSharedPreferences("settings", 0)
            .getBoolean("beta", BuildConfig.VERSION_NAME.contains("-"));
    LinearLayout root = Ui.column(this), nav = Ui.row(this);
    nav.addView(Ui.button(this, "‹ Pengaturan", "‹ Settings", this::finish));
    nav.addView(Ui.text(this, "Software Update", 23));
    root.addView(nav);
    installed =
        Ui.text(
            this,
            tr("Versi terpasang: ", "Installed version: ")
                + BuildConfig.VERSION_NAME
                + " ("
                + BuildConfig.VERSION_CODE
                + ")",
            16);
    root.addView(installed);
    latest = Ui.text(this, tr("Versi terbaru: belum diperiksa", "Latest version: not checked"), 16);
    root.addView(latest);
    LinearLayout choices = Ui.row(this);
    Spinner channel = new Spinner(this);
    channel.setAdapter(
        new ArrayAdapter<>(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            new String[] {"Stable Release", "Beta Release"}));
    channel.setSelection(beta ? 1 : 0);
    channel.setOnItemSelectedListener(
        new android.widget.AdapterView.OnItemSelectedListener() {
          public void onItemSelected(
              android.widget.AdapterView<?> p, android.view.View v, int index, long id) {
            boolean next = index == 1;
            if (beta != next && !busy) {
              beta = next;
              getSharedPreferences("settings", 0).edit().putBoolean("beta", beta).apply();
              release = null;
              apk = null;
              check();
            }
          }

          public void onNothingSelected(android.widget.AdapterView<?> p) {}
        });
    choices.addView(channel);
    Switch automatic = new Switch(this);
    automatic.setText(tr("Periksa otomatis setiap 24 jam", "Check automatically every 24 hours"));
    automatic.setChecked(getSharedPreferences("settings", 0).getBoolean("autoUpdates", true));
    automatic.setOnCheckedChangeListener(
        (bttn, on) ->
            getSharedPreferences("settings", 0).edit().putBoolean("autoUpdates", on).apply());
    choices.addView(automatic);
    root.addView(choices);
    progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
    progress.setMax(100);
    root.addView(progress, new LinearLayout.LayoutParams(-1, Ui.dp(this, 12)));
    status =
        Ui.text(
            this,
            tr(
                "Sumber resmi: GitHub Releases. Tidak memerlukan Google Play Services.",
                "Official source: GitHub Releases. No Google Play Services required."),
            13);
    root.addView(status);
    ScrollView scroll = new ScrollView(this);
    changelog = Ui.text(this, "", 14);
    changelog.setTextIsSelectable(true);
    scroll.addView(changelog);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    LinearLayout actions = Ui.row(this);
    check = Ui.button(this, "Periksa sekarang", "Check now", this::check);
    actions.addView(check);
    download =
        Ui.button(this, "Download & Install Update", "Download & Install Update", this::download);
    download.setEnabled(false);
    actions.addView(download);
    root.addView(actions);
    setContentView(root);
    check();
  }

  private void check() {
    if (busy || installing) return;
    busy = true;
    check.setEnabled(false);
    download.setEnabled(false);
    status.setText(tr("Memeriksa GitHub Releases…", "Checking GitHub Releases…"));
    boolean channel = beta;
    worker.execute(
        () -> {
          try {
            UpdateClient.Release found = UpdateClient.latest(channel);
            runOnUiThread(
                () -> {
                  if (closed) return;
                  release = found;
                  apk = null;
                  if (found == null) {
                    latest.setText(
                        tr("Belum ada APK pada kanal ini", "No APK releases on this channel"));
                    changelog.setText("");
                    status.setText(
                        tr(
                            "Tidak ada rilis yang memenuhi metadata verifikasi.",
                            "No release with verification metadata available."));
                    return;
                  }
                  latest.setText(
                      tr("Versi terbaru: ", "Latest version: ")
                          + found.version
                          + String.format(Locale.US, " · %.2f MiB", found.size / 1048576.0));
                  changelog.setText(
                      found.notes.isEmpty()
                          ? tr("Tidak ada catatan rilis.", "No release notes.")
                          : found.notes);
                  boolean newer =
                      found.code > BuildConfig.VERSION_CODE
                          && new ReleaseVersion(found.version)
                                  .compareTo(new ReleaseVersion(BuildConfig.VERSION_NAME))
                              > 0;
                  download.setEnabled(newer);
                  status.setText(
                      newer
                          ? tr(
                              "Pembaruan tersedia. Checksum dan signature akan diverifikasi sebelum"
                                  + " instalasi.",
                              "Update available. Checksum and signature are verified before"
                                  + " installation.")
                          : tr(
                              "Tidak ada versi yang lebih baru pada kanal ini.",
                              "No newer version on this channel."));
                });
          } catch (Exception e) {
            message(tr("Pemeriksaan gagal: ", "Update check failed: ") + e.getMessage());
          } finally {
            busy = false;
            runOnUiThread(
                () -> {
                  if (!closed) check.setEnabled(true);
                });
          }
        });
  }

  private void download() {
    if (busy || installing || release == null) return;
    if (apk != null) {
      install();
      return;
    }
    busy = true;
    download.setEnabled(false);
    check.setEnabled(false);
    status.setText(tr("Mengunduh ke penyimpanan privat…", "Downloading to private storage…"));
    UpdateClient.Release target = release;
    worker.execute(
        () -> {
          try {
            File file =
                UpdateClient.download(
                    this,
                    target,
                    (done, total) ->
                        runOnUiThread(
                            () -> {
                              if (closed) return;
                              progress.setProgress((int) (done * 100 / total));
                              status.setText(
                                  String.format(
                                      Locale.US,
                                      "%.1f / %.1f MiB",
                                      done / 1048576.0,
                                      total / 1048576.0));
                            }));
            runOnUiThread(
                () -> {
                  if (closed) return;
                  apk = file;
                  status.setText(
                      tr(
                          "Checksum dan signature valid. Siap dipasang.",
                          "Checksum and signature verified. Ready to install."));
                  install();
                });
          } catch (Exception e) {
            message(tr("Pembaruan ditolak: ", "Update rejected: ") + e.getMessage());
          } finally {
            busy = false;
            runOnUiThread(
                () -> {
                  if (!closed) {
                    download.setEnabled(!installing);
                    check.setEnabled(!installing);
                  }
                });
          }
        });
  }

  private void install() {
    if (apk == null || release == null) return;
    if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
      status.setText(
          tr(
              "Izinkan pemasangan pembaruan untuk PianoFlow, lalu kembali.",
              "Allow update installation for PianoFlow, then return."));
      startActivityForResult(
          new Intent(
              Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName())),
          77);
      return;
    }
    if (installing) return;
    installing = true;
    download.setEnabled(false);
    check.setEnabled(false);
    final File targetFile = apk;
    final UpdateClient.Release targetRelease = release;
    worker.execute(
        () -> {
          int sessionId = -1;
          boolean committed = false;
          PackageInstaller installer = getPackageManager().getPackageInstaller();
          try {
            UpdateClient.verify(this, targetFile, targetRelease);
            PackageInstaller.SessionParams params =
                new PackageInstaller.SessionParams(
                    PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            params.setAppPackageName(getPackageName());
            params.setSize(targetFile.length());
            sessionId = installer.createSession(params);
            try (PackageInstaller.Session session = installer.openSession(sessionId)) {
              try (InputStream input = new FileInputStream(targetFile);
                  OutputStream out = session.openWrite("PianoFlow.apk", 0, targetFile.length())) {
                byte[] buffer = new byte[32768];
                int n;
                while ((n = input.read(buffer)) != -1) out.write(buffer, 0, n);
                session.fsync(out);
              }
              int flags = PendingIntent.FLAG_UPDATE_CURRENT;
              if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE;
              Intent callback =
                  new Intent(this, InstallResultReceiver.class)
                      .setAction("id.arus.piano.INSTALL_RESULT");
              session.commit(
                  PendingIntent.getBroadcast(this, sessionId, callback, flags).getIntentSender());
              committed = true;
            }
            message(
                tr(
                    "Konfirmasi pemasangan pada dialog sistem. Data aplikasi dipertahankan.",
                    "Confirm installation in the system dialog. App data is retained."));
          } catch (Exception e) {
            if (sessionId >= 0 && !committed)
              try {
                installer.abandonSession(sessionId);
              } catch (Exception ignored) {
              }
            message(tr("Instalasi gagal: ", "Installation failed: ") + e.getMessage());
          } finally {
            installing = false;
            runOnUiThread(
                () -> {
                  if (!closed) {
                    check.setEnabled(true);
                    download.setEnabled(true);
                  }
                });
          }
        });
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (request == 77
        && Build.VERSION.SDK_INT >= 26
        && getPackageManager().canRequestPackageInstalls()) install();
  }

  private void message(String s) {
    runOnUiThread(
        () -> {
          if (!closed) status.setText(s);
        });
  }

  @Override
  public void onDestroy() {
    closed = true;
    worker.shutdownNow();
    super.onDestroy();
  }
}

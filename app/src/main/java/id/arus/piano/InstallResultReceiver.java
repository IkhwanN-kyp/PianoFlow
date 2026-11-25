package id.arus.piano;

import android.content.*;
import android.content.pm.PackageInstaller;
import android.widget.Toast;

public final class InstallResultReceiver extends BroadcastReceiver {
  @Override
  public void onReceive(Context context, Intent intent) {
    int result = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
    if (result == PackageInstaller.STATUS_PENDING_USER_ACTION) {
      Intent confirmation = intent.getParcelableExtra(Intent.EXTRA_INTENT);
      if (confirmation != null) {
        confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(confirmation);
      }
    } else if (result != PackageInstaller.STATUS_SUCCESS)
      Toast.makeText(
              context,
              Ui.tr(context, "Pemasangan belum selesai: ", "Installation incomplete: ")
                  + intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE),
              Toast.LENGTH_LONG)
          .show();
  }
}

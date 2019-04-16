package pt.up.fc.dcc.hyrax.od_launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class AutoStart : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            val launchIntent = Intent(context, ODLauncherService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context?.startForegroundService(launchIntent)
            } else {
                context?.startService(launchIntent)
            }
        }
    }
}
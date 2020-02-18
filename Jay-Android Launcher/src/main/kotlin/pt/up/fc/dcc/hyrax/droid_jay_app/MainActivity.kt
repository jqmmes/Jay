package pt.up.fc.dcc.hyrax.droid_jay_app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.support.v7.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val launchIntent = Intent(applicationContext, DroidJayLauncherService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            applicationContext?.startForegroundService(launchIntent)
        } else {
            applicationContext?.startService(launchIntent)
        }
        finish()
    }
}
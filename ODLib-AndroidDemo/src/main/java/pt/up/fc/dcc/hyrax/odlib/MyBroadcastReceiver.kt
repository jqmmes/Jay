package pt.up.fc.dcc.hyrax.odlib

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class MyBroadcastReceiver(val activity: MainActivity) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        //println("xpto")
        /*val action = intent.getStringArrayListExtra("action")
        println(action)
        for (act in action) {
            println(act)
        }
        println(intent.getStringExtra("action"))*/
        //activity.
    }
}
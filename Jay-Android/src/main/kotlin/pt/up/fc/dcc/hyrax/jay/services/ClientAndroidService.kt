package pt.up.fc.dcc.hyrax.jay.services

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder

class ClientAndroidService  : Service() {

    // Binder given to clients
    private val mBinder = LocalBinder()


    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    inner class LocalBinder : Binder() {
        // Return this instance of LocalService so clients can call public methods
        fun getService(): ClientAndroidService = this@ClientAndroidService
    }

    override fun onBind(intent: Intent): IBinder {
        return mBinder
    }

}
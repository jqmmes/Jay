package pt.up.fc.dcc.hyrax.odlib

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ToggleButton

import kotlinx.android.synthetic.main.activity_main.*
import pt.up.fc.dcc.hyrax.odlib.discover.MulticastAdvertiser
import pt.up.fc.dcc.hyrax.odlib.discover.MulticastListener
import pt.up.fc.dcc.hyrax.odlib.interfaces.DiscoverInterface

class MainActivity : AppCompatActivity() {

    private lateinit var odClient : ODLib
    private lateinit var loggingConsole : Logger

    companion object {
        fun updateIface(){

        }
    }

    private fun toggleServer(start : Boolean) {
        loggingConsole.log("Toggle Server $start")

        if (start) {
            findViewById<ToggleButton>(R.id.serviceToggleButton).isChecked = true
            toggleService(true)
            odClient.startGRPCServerService(odClient, 50001, true)
        }
        else odClient.stopGRPCServer()
    }
    private fun toggleService(start : Boolean) {
        loggingConsole.log("Toggle Service $start")
        if (start) odClient.startODService()
        else odClient.stopODService()
    }

    fun serviceToggleListener(target : View) {
        toggleService((target as ToggleButton).isChecked)
    }

    fun serverToggleListener(target : View) {
        toggleServer((target as ToggleButton).isChecked)
    }

    fun advertiseToggleListener(target : View) {
        if ((target as ToggleButton).isChecked) MulticastAdvertiser.advertise()
        else MulticastAdvertiser.stop()
        //toggleService((target as ToggleButton).isChecked)
    }

    inner class DiscoveredClient : DiscoverInterface {
        override fun onNewClientFound(remoteClient: RemoteODClient) {
            loggingConsole.log("Discovered new client")
        }

    }

    fun discoverToggleListener(target : View) {
        if ((target as ToggleButton).isChecked) MulticastListener.listen(DiscoveredClient())
        else MulticastListener.stop()
        //toggleServer((target as ToggleButton).isChecked)
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        loggingConsole = Logger(this, findViewById(R.id.loggingConsole))
        odClient = ODLib(this)
        odClient.enableLogs(loggingConsole)

        /*fab.setOnClickListener { view ->
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show()
        }*/
    }


    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }
}

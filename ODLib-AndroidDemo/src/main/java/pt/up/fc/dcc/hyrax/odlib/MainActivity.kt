package pt.up.fc.dcc.hyrax.odlib

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.ToggleButton

import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    private lateinit var odClient : ODLib

    fun serviceToggleListener(target : View) {
        runOnUiThread {
            findViewById<TextView>(R.id.loggingConsole).text = "Toggling Service"
        }
        if ((target as ToggleButton).isChecked) odClient.startODService()
        else odClient.stopODService()
    }

    fun serverToggleListener(target : View) {
        runOnUiThread {
            findViewById<TextView>(R.id.loggingConsole).text = "Toggling Server"
        }
        if ((target as ToggleButton).isChecked) {
            findViewById<ToggleButton>(R.id.serviceToggleButton).isChecked = true
        }
        else odClient.stopGRPCServer()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        odClient = ODLib(this)

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

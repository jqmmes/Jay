package pt.up.fc.dcc.hyrax.odlib

import android.Manifest
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import android.view.View

import kotlinx.android.synthetic.main.activity_main.*
import pt.up.fc.dcc.hyrax.odlib.discover.MulticastAdvertiser
import pt.up.fc.dcc.hyrax.odlib.discover.MulticastListener
import pt.up.fc.dcc.hyrax.odlib.interfaces.DiscoverInterface
import android.graphics.BitmapFactory
import java.io.File
import android.support.v4.app.ActivityCompat
import android.content.pm.PackageManager
import android.app.Activity
import android.widget.*
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var odClient : ODLib
    private lateinit var loggingConsole : Logger
    private val REQUEST_EXTERNAL_STORAGE = 1
    private val PERMISSIONS_STORAGE = arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)


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
    }

    fun downloadModel(target : View) {
        val spinner = findViewById<Spinner>(R.id.select_model)
        for (model in odClient.listModels(false)) {
            if (model.modelName == spinner.selectedItem) {
                if (!model.downloaded) odClient.setTFModel(model)
                loggingConsole.log("${model.modelName}\t\tloaded: ${model.downloaded}")
                return
            }
        }
    }

    fun loadModel(target : View) {
        val spinner = findViewById<Spinner>(R.id.select_model)
        for (model in odClient.listModels(false)) {
            if (model.modelName == spinner.selectedItem) {
                loggingConsole.log("${model.modelName}\tloaded: ${model.downloaded}")
                odClient.setTFModel(model)
                return
            }
        }
    }

    fun chooseImage(target: View) {
        thread{
            //val bitmap = BitmapFactory.decodeFile(File("/storage/emulated/0/img.png").absolutePath)
            //println(bitmap)
            val byteArray =odClient.localDetector.
                    getByteArrayFromImage(File("/storage/emulated/0/img.png").absolutePath)
            /*println(byteArray.size)
            println(BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size))*/
            odClient.localDetector.detectObjects(byteArray)
        }
    }

    inner class DiscoveredClient : DiscoverInterface {
        override fun onNewClientFound(remoteClient: RemoteODClient) {
            loggingConsole.log("Discovered new client")
        }

    }

    fun discoverToggleListener(target : View) {
        if ((target as ToggleButton).isChecked) MulticastListener.listen(DiscoveredClient())
        else MulticastListener.stop()
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        loggingConsole = Logger(this, findViewById(R.id.loggingConsole))
        odClient = ODLib(this)
        odClient.enableLogs(loggingConsole)
        val spinner = findViewById<Spinner>(R.id.select_model)

        //Sample String ArrayList
        val arrayList1 = ArrayList<String>()
        for (model in odClient.listModels(false)) {
            arrayList1.add(model.modelName)
        }

        val adp = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayList1)
        spinner.adapter = adp

        verifyStoragePermissions(this)
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

    fun verifyStoragePermissions(activity: Activity) {
        // Check if we have write permission
        val permission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE)

        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    activity,
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE
            )
        }
    }

}

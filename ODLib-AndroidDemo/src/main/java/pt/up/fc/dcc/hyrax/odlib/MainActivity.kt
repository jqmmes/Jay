package pt.up.fc.dcc.hyrax.odlib

import android.Manifest
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import android.view.View

import kotlinx.android.synthetic.main.activity_main.*
import pt.up.fc.dcc.hyrax.odlib.multicast.MulticastAdvertiser
import pt.up.fc.dcc.hyrax.odlib.multicast.MulticastListener
import android.support.v4.app.ActivityCompat
import android.content.pm.PackageManager
import android.app.Activity
import android.graphics.BitmapFactory
import android.widget.*
import pt.up.fc.dcc.hyrax.odlib.enums.LogLevel
import pt.up.fc.dcc.hyrax.odlib.interfaces.Scheduler
import pt.up.fc.dcc.hyrax.odlib.jobManager.JobManager
import pt.up.fc.dcc.hyrax.odlib.scheduler.*
import pt.up.fc.dcc.hyrax.odlib.services.ODComputingService
import pt.up.fc.dcc.hyrax.odlib.tensorflow.COCODataLabels
import pt.up.fc.dcc.hyrax.odlib.utils.*
import java.io.File
import kotlin.concurrent.thread
import kotlin.math.max

@Suppress("UNUSED_PARAMETER")
class MainActivity : AppCompatActivity() {

    private lateinit var odClient : ODLib
    private lateinit var loggingConsole : Logger
    private val requestExternalStorage = 1
    private val permissionsStorage = arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)


    private fun toggleServer(start : Boolean) {
        ODLogger.logInfo("Toggle Server $start")

        if (start) {
            findViewById<ToggleButton>(R.id.serviceToggleButton).isChecked = true
            toggleService(true)
            odClient.startGRPCServerService(odClient, useNettyServer = true)
        }
        else odClient.stopGRPCServer()
    }
    private fun toggleService(start : Boolean) {
        ODLogger.logInfo("Toggle Service $start")
        if (start) {
            ODLogger.logInfo("CPU Cores: ${SystemStats.getCpuCount()}")
            ODLogger.logInfo("Battery Status ${SystemStats.getBatteryPercentage(this)}\nCharging? ${SystemStats
                    .getBatteryCharging(this)}")
            ODComputingService.setWorkingThreads(max(SystemStats.getCpuCount()/2, 1))
            odClient.setScheduler(getScheduler())
            odClient.startODService()
            //JobManager.getScheduler().setJobCompleteCallback(Callback(0))
            JobManager.addResultsCallback(){id, results -> resultsCallback(id, results)}

        }
        else odClient.stopODService()
    }

    private fun resultsCallback(id: Long, results: List<ODUtils.ODDetection?>) {
        ODLogger.logInfo("Received results for Job $id")
        for (result in results) {
            if (result != null) ODLogger.logInfo("\t\t${COCODataLabels.label(result.class_)}\t${result.score*100.0}%")
        }
    }

    private fun getScheduler() : Scheduler {
        val schedulerSpinnerValue = findViewById<Spinner>(R.id.select_scheduler).selectedItem
        return when(schedulerSpinnerValue) {
            "RemoteRandomScheduler" -> RemoteRandomScheduler()
            "RemoteRoundRobinScheduler" -> RemoteRoundRobinScheduler()
            "JustRemoteRoundRobinScheduler" -> JustRemoteRoundRobinScheduler()
            "JustRemoteRandomScheduler" -> JustRemoteRandomScheduler()
            "CloudScheduler" -> CloudScheduler()
            else -> LocalScheduler()
        }
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
                ODLogger.logInfo("${model.modelName}\t\tloaded: ${model.downloaded}")
                return
            }
        }
    }

    fun loadModel(target : View) {
        val spinner = findViewById<Spinner>(R.id.select_model)
        for (model in odClient.listModels(false)) {
            if (model.modelName == spinner.selectedItem) {
                ODLogger.logInfo("${model.modelName}\tloaded: ${model.downloaded}")
                odClient.setTFModel(model)
                return
            }
        }
    }

    fun chooseImage(target : View) {
        thread (name="chooseImage CreateJob"){
            println("making job")
            /*val job = JobManager.createJob(
                ImageUtils.getByteArrayFromBitmapFast(
                        ImageUtils.getImageBitmapFromFile(File("/storage/emulated/0/img.png"))!!
                )
            )*/
            val job = JobManager.createJob(File("/storage/emulated/0/img.png").readBytes())
            println("job made")
            JobManager.addJob(job)
        }
    }

    fun discoverToggleListener(target : View) {
        if ((target as ToggleButton).isChecked) MulticastListener.listen()
        else MulticastListener.stop()
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        loggingConsole = Logger(this, findViewById(R.id.loggingConsole))
        odClient = ODLib(this)
        ODLogger.enableLogs(loggingConsole, LogLevel.Info)
        ODLogger.startBackgroundLoggingService()
        val modelSpinner = findViewById<Spinner>(R.id.select_model)
        val schedulerSpinner = findViewById<Spinner>(R.id.select_scheduler)
        val schedulerAdapter = arrayOf("LocalScheduler", "RemoteRandomScheduler", "RemoteRoundRobinScheduler",
                "JustRemoteRoundRobinScheduler", "JustRemoteRandomScheduler", "CloudScheduler")

        schedulerSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, schedulerAdapter)

        val arrayList1 = ArrayList<String>()
        for (model in odClient.listModels(false)) {
            arrayList1.add(model.modelName)
        }

        val adp = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayList1)
        modelSpinner.adapter = adp

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

    private fun verifyStoragePermissions(activity: Activity) {
        // Check if we have write permission
        val permission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE)

        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    activity,
                    permissionsStorage,
                    requestExternalStorage
            )
        }
    }
}

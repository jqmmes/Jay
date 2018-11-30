package pt.up.fc.dcc.hyrax.odlib

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v7.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.ToggleButton
import kotlinx.android.synthetic.main.activity_main.*
import pt.up.fc.dcc.hyrax.odlib.enums.LogLevel
import pt.up.fc.dcc.hyrax.odlib.multicast.MulticastAdvertiser
import pt.up.fc.dcc.hyrax.odlib.multicast.MulticastListener
import pt.up.fc.dcc.hyrax.odlib.scheduler.*
import pt.up.fc.dcc.hyrax.odlib.services.ODComputingService
import pt.up.fc.dcc.hyrax.odlib.tensorflow.COCODataLabels
import pt.up.fc.dcc.hyrax.odlib.utils.ODDetection
import pt.up.fc.dcc.hyrax.odlib.utils.ODLogger
import pt.up.fc.dcc.hyrax.odlib.utils.SystemStats
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.*
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
            odClient.startGRPCServerService(useNettyServer = true)
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
            Scheduler.addResultsCallback { id, results -> resultsCallback(id, results)}

        }
        else odClient.stopODService()
    }

    private fun resultsCallback(id: Long, results: List<ODDetection?>) {
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
            "SmartScheduler" -> SmartScheduler()
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
            val job = Scheduler.createJob(File("/storage/emulated/0/img.png").readBytes())
            Scheduler.addJob(job)
        }
    }

    private var startBenchmark = 0L

    fun sequentialBenchmark(target: View) {
        //for (asset in assets.list())
        //ODLogger.logInfo(R.drawable.titan)
        startBenchmark = System.currentTimeMillis()
        thread { sequentialBenchmark(listAssets()) }
    }

    private fun sequentialBenchmark(list: List<ByteArray>) {
        if (list.isEmpty()) {
            ODLogger.logInfo("Total Duration: ${System.currentTimeMillis() - startBenchmark}ms")
            return
        }
        Scheduler.addResultsCallback { jobID, results -> sequentialBenchmark(list.subList(1, list.size)) }
        Scheduler.addJob(Scheduler.createJob((list.first())))
    }

    private var totalJobs = 0
    fun parallelBenchmark(target: View) {
        thread {
            startBenchmark = System.currentTimeMillis()
            Scheduler.addResultsCallback() { jobID, results -> if (++totalJobs >= assets.list("benchmark").size) ODLogger.logInfo("Total Duration: ${System.currentTimeMillis() - startBenchmark}ms") }
            for (asset in listAssets()) {
                Scheduler.addJob(Scheduler.createJob(asset))
            }
        }
    }

    private fun listAssets(): List<ByteArray> {
        val ret = LinkedList<ByteArray>()
        for (asset in assets.list("benchmark")) {
            val stream = ByteArrayOutputStream()
            BitmapFactory.decodeStream(assets.open("benchmark/$asset")).compress(Bitmap.CompressFormat.PNG, 100, stream)
            ret.addLast(stream.toByteArray())
        }
        //assets.open("benchmark/$asset")
        //ImageUtils.getByteArrayFromImage()

        //BitmapFactory.decodeFile(imgPath).compress(Bitmap.CompressFormat.PNG, 100, stream)
        //return
        //    ODLogger.logInfo(asset)
        //return listOf(getDrawable(R.drawable.titan), getDrawable(R.drawable.titan))
        /*val stream = ByteArrayOutputStream()
        BitmapFactory.decodeStream(assets.open("benchmark/$asset")).compress(Bitmap.CompressFormat.PNG, 100, stream)
        stream.toByteArray()*/
        return ret
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
                "JustRemoteRoundRobinScheduler", "JustRemoteRandomScheduler", "CloudScheduler", "SmartScheduler")

        schedulerSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, schedulerAdapter)

        findViewById<ToggleButton>(R.id.serviceToggleButton).isChecked = true
        findViewById<ToggleButton>(R.id.serverToggleButton).isChecked = true
        odClient.startGRPCServerService(useNettyServer = true)

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

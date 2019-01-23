package pt.up.fc.dcc.hyrax.odlib

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v7.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import kotlinx.android.synthetic.main.activity_main.*
import pt.up.fc.dcc.hyrax.odlib.clients.ClientManager
import pt.up.fc.dcc.hyrax.odlib.clients.CloudODClient
import pt.up.fc.dcc.hyrax.odlib.enums.LogLevel
import pt.up.fc.dcc.hyrax.odlib.multicast.MulticastAdvertiser
import pt.up.fc.dcc.hyrax.odlib.multicast.MulticastListener
import pt.up.fc.dcc.hyrax.odlib.scheduler.*
import pt.up.fc.dcc.hyrax.odlib.services.ODComputingService
import pt.up.fc.dcc.hyrax.odlib.tensorflow.COCODataLabels
import pt.up.fc.dcc.hyrax.odlib.utils.ODDetection
import pt.up.fc.dcc.hyrax.odlib.utils.ODLogger
import pt.up.fc.dcc.hyrax.odlib.utils.ODSettings
import pt.up.fc.dcc.hyrax.odlib.utils.SystemStats
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.lang.Thread.sleep
import java.util.*
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import kotlin.math.max

@Suppress("UNUSED_PARAMETER")
class MainActivity : AppCompatActivity() {

    private lateinit var odClient: ODLib
    private lateinit var loggingConsole: Logger
    private val requestExternalStorage = 1
    private val permissionsStorage = arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)


    fun changeCloud(target: View) {
        ClientManager.changeCloudClient(CloudODClient(findViewById<EditText>(R.id.cloudIP).text.toString()))
    }

    private fun toggleServer(start: Boolean) {
        ODLogger.logInfo("Toggle Server $start")

        if (start) {
            findViewById<ToggleButton>(R.id.serviceToggleButton).isChecked = true
            toggleService(true)
            odClient.startGRPCServerService(useNettyServer = true)
        } else odClient.stopGRPCServer()
    }

    private fun toggleService(start: Boolean) {
        ODLogger.logInfo("Toggle Service $start")
        if (start) {
            ODLogger.logInfo("CPU Cores: ${SystemStats.getCpuCount()}")
            ODLogger.logInfo("Battery Status ${SystemStats.getBatteryPercentage(this)}\nCharging? ${SystemStats
                    .getBatteryCharging(this)}")
            ODComputingService.setWorkingThreads(max(SystemStats.getCpuCount() / 2, 1))
            odClient.setScheduler(getScheduler())
            if (getScheduler() is CloudScheduler) {
                thread {
                    val models = ClientManager.getCloudClient().getModels(false, true)
                    if (!models.isEmpty()) {
                        ClientManager.getCloudClient().selectModel(models.first())
                    }
                }
            }
            odClient.startODService()
            Scheduler.addResultsCallback { id, results -> resultsCallback(id, results) }

        } else odClient.stopODService()
    }

    private fun resultsCallback(id: Long, results: List<ODDetection?>) {
        ODLogger.logInfo("Received results for Job $id")
        for (result in results) {
            if (result != null) ODLogger.logInfo("\t\t${COCODataLabels.label(result.class_)}\t${result.score * 100.0}%")
        }
    }

    private fun getScheduler(): Scheduler {
        val schedulerSpinnerValue = findViewById<Spinner>(R.id.select_scheduler).selectedItem
        return when (schedulerSpinnerValue) {
            "RemoteRandomScheduler" -> RemoteRandomScheduler()
            "RemoteRoundRobinScheduler" -> RemoteRoundRobinScheduler()
            "JustRemoteRoundRobinScheduler" -> JustRemoteRoundRobinScheduler()
            "JustRemoteRandomScheduler" -> JustRemoteRandomScheduler()
            "CloudScheduler" -> CloudScheduler()
            "SmartScheduler" -> SmartScheduler()
            else -> LocalScheduler()
        }
    }

    fun serviceToggleListener(target: View) {
        toggleService((target as ToggleButton).isChecked)
    }

    fun serverToggleListener(target: View) {
        toggleServer((target as ToggleButton).isChecked)
    }

    fun advertiseToggleListener(target: View) {
        if ((target as ToggleButton).isChecked) MulticastAdvertiser.advertise()
        else MulticastAdvertiser.stop()
    }

    fun downloadModel(target: View) {
        val spinner = findViewById<Spinner>(R.id.select_model)
        for (model in odClient.listModels(false)) {
            if (model.modelName == spinner.selectedItem) {
                if (!model.downloaded) odClient.setTFModel(model)
                ODLogger.logInfo("${model.modelName}\t\tloaded: ${model.downloaded}")
                return
            }
        }
    }

    fun loadModel(target: View) {
        val spinner = findViewById<Spinner>(R.id.select_model)
        for (model in odClient.listModels(false)) {
            if (model.modelName == spinner.selectedItem) {
                ODLogger.logInfo("${model.modelName}\tloaded: ${model.downloaded}")
                odClient.setTFModel(model)
                return
            }
        }
    }

    fun chooseImage(target: View) {
        thread(name = "chooseImage CreateJob") {
            val job = Scheduler.createJob(
                    loadAssetImage(assets.open("benchmark-small/${assets.list("benchmark-small")!![0]}"))
            )

            ODLogger.logInfo("Battery_Start\t$${job.getId()}\t${SystemStats.getBatteryEnergyCounter(this)}")
            Scheduler.addResultsCallback { _, _ ->
                ODLogger.logInfo("Battery_End\t$${job.getId()}\t${SystemStats.getBatteryEnergyCounter(this)}")
            }
            Scheduler.addJob(job)
        }
    }

    private var startBenchmark = 0L
    private var assetImages: List<ByteArray> = emptyList()
    private var synchronizingLatch = CountDownLatch(1)

    fun sequentialBenchmark(target: View) {
        runBenchmark(SMALL_ASSETS)
    }

    private fun runBenchmark(assetSize: Int) {
        thread {
            if (assetImages.isEmpty()) assetImages = listAssets(assetSize)
            var client = ClientManager.getLocalODClient()
            if (getScheduler() is CloudScheduler) client = ClientManager.getCloudClient()
            val models = client.getModels(false, true)
            models.forEach { model ->
                ODLogger.logInfo("Model_Name\t${model.modelName}")
                if ((getScheduler() is LocalScheduler) or (getScheduler() is CloudScheduler)) {
                    client.selectModel(model)
                    sleep(1000)
                    while (!client.modelLoaded(model)) sleep(1000)

                } else {
                    var includeLocal = true
                    if ((getScheduler() is JustRemoteRoundRobinScheduler) or (getScheduler() is
                                    JustRemoteRandomScheduler)) {
                        includeLocal = false
                    }
                    ClientManager.getRemoteODClients(includeLocal).forEach { client ->
                        client.selectModel(model)
                        sleep(1000)
                        while (!client.modelLoaded(model)) sleep(1000)
                    }
                }

                startBenchmark = System.currentTimeMillis()
                assetImages.forEach { asset ->
                    val job = Scheduler.createJob(asset)
                    ODLogger.logInfo("Battery_Start\t${job.getId()}\t${SystemStats.getBatteryEnergyCounter(this)
                    }\t${SystemStats.getBatteryPercentage(this)}")
                    Scheduler.addResultsCallback { _, _ ->
                        ODLogger.logInfo("Battery_End\t${job.getId()}\t${SystemStats.getBatteryEnergyCounter
                        (this)}\t${SystemStats.getBatteryPercentage(this)}")
                        synchronizingLatch.countDown()
                    }
                    Scheduler.addJob(job)
                    synchronizingLatch.await()
                    synchronizingLatch = CountDownLatch(1)
                }

            }
        }
    }


    //private var totalJobs = 0
    fun parallelBenchmark(target: View) {
        runBenchmark(LARGE_ASSETS)
    }

    private fun listAssets(assetType: Int = SMALL_ASSETS): List<ByteArray> {
        val ret = LinkedList<ByteArray>()
        var assetDirectory = "benchmark-small"
        if (assetType == LARGE_ASSETS)
            assetDirectory = "benchmark-large"
        assets.list(assetDirectory)!!.forEach { asset ->
            ODLogger.logInfo(asset)
            ret.addLast(loadAssetImage(assets.open("$assetDirectory/$asset")))
        }
        return ret
    }

    private fun loadAssetImage(asset: InputStream): ByteArray {
        val stream = ByteArrayOutputStream()
        BitmapFactory.decodeStream(asset).compress(Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
    }

    fun discoverToggleListener(target: View) {
        if ((target as ToggleButton).isChecked) MulticastListener.listen()
        else MulticastListener.stop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        loggingConsole = Logger(this, findViewById(R.id.loggingConsole))
        loggingConsole.benchmark(this)
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

        findViewById<EditText>(R.id.cloudIP).setText(ODSettings.cloudIp, TextView.BufferType.EDITABLE)
        verifyStoragePermissions(this)
        requestBatteryPermissions()


        registerBroadcastReceiver()
    }


    private fun registerBroadcastReceiver(){
        val br : BroadcastReceiver = MyBroadcastReceiver(this)
        val filter = IntentFilter("pt.up.fc.dcc.hyrax.odlib.MainActivity.ODLibAppControl")
        this.registerReceiver(br, filter)
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

    private fun requestBatteryPermissions() {
        val permission = ActivityCompat.checkSelfPermission(this, Manifest.permission.BATTERY_STATS)
        if (permission != PackageManager.PERMISSION_GRANTED) {
            println("No battery stats")
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.BATTERY_STATS), 3)
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

    companion object {
        private const val SMALL_ASSETS: Int = 0
        private const val LARGE_ASSETS: Int = 1
    }
}

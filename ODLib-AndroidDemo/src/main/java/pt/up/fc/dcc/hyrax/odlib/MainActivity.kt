package pt.up.fc.dcc.hyrax.odlib

import android.Manifest
import android.annotation.SuppressLint
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
import pt.up.fc.dcc.hyrax.odlib.R
import pt.up.fc.dcc.hyrax.odlib.logger.LogLevel
//import pt.up.fc.dcc.hyrax.odlib.tensorflow.COCODataLabels
import pt.up.fc.dcc.hyrax.odlib.logger.ODLogger
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.*
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

@Suppress("UNUSED_PARAMETER")
class MainActivity : AppCompatActivity() {

    private lateinit var loggingConsole: Logger
    private val requestExternalStorage = 1
    private val permissionsStorage = arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
    private var schedulersIds : Set<Pair<String, String>> = setOf()


    fun workerToggleListener(target: View) {
        toggleWorker((target as ToggleButton).isChecked)
    }

    fun schedulerToggleListener(target: View) {
        toggleScheduler((target as ToggleButton).isChecked)
    }

    private fun toggleScheduler(start: Boolean) {
        ODLogger.logInfo("Toggle Server $start")

        if (start) {
            odClient.startScheduler()
        } else {
            odClient.stopScheduler()
        }
    }

    private fun toggleWorker(start: Boolean) {
        if (start) {
            odClient.startWorker()
        } else {
            odClient.stopWorker()
        }
    }

    /*private fun resultsCallback(id: Long, results: List<ODDetection?>) {
        ODLogger.logInfo("Received results for Job $id")
        for (result in results) {
            if (result != null) ODLogger.logInfo("\t\t${COCODataLabels.label(result.class_)}\t${result.score * 100.0}%")
        }
    }*/


    fun showSchedulersListener(target: View) {
        thread {
            schedulersArrayList.clear()
            odClient.listSchedulers {schedulers ->
                schedulersIds = schedulers
                for (scheduler in schedulers) schedulersArrayList.add(scheduler.second)
                runOnUiThread {
                    schedulerArrayAdapter.notifyDataSetChanged()
                    schedulerSpinner.postInvalidate()
                    schedulerSpinner.adapter = schedulerArrayAdapter
                }
            }
            return@thread
        }
    }

    fun chooseImage(target: View) {
        thread(name = "chooseImage CreateJob") {
            odClient.scheduleJob(loadAssetImage(assets.open("benchmark-small/${assets.list("benchmark-small")!![0]}")))

            return@thread
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
            /*var client = ClientManager.getLocalODClient()
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
                    val job = SchedulerBase.createJob(asset)
                    ODLogger.logInfo("Battery_Start\t${job.id}\t${SystemStats.getBatteryEnergyCounter(this)
                    }\t${SystemStats.getBatteryPercentage(this)}")
                    SchedulerBase.addResultsCallback { _, _ ->
                        ODLogger.logInfo("Battery_End\t${job.id}\t${SystemStats.getBatteryEnergyCounter
                        (this)}\t${SystemStats.getBatteryPercentage(this)}")
                        synchronizingLatch.countDown()
                    }
                    SchedulerBase.addJob(job)
                    synchronizingLatch.await()
                    synchronizingLatch = CountDownLatch(1)
                }

            }*/
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

    fun showModelsListener(target: View) {
        modelsArrayList.clear()
        odClient.listModels {models ->
            for (model in models) modelsArrayList.add(model.modelName)
            runOnUiThread {
                modelArrayAdapter.notifyDataSetChanged()
                modelSpinner.postInvalidate()
                modelSpinner.adapter = modelArrayAdapter
            }
        }
    }

    private val modelsArrayList = ArrayList<String>()
    private lateinit var modelArrayAdapter: ArrayAdapter<String>
    private val schedulersArrayList = ArrayList<String>()
    private lateinit var schedulerArrayAdapter: ArrayAdapter<String>

    private lateinit var modelSpinner: Spinner
    private lateinit var schedulerSpinner: Spinner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        loggingConsole = Logger(this, findViewById(R.id.loggingConsole))
        loggingConsole.benchmark(this)
        odClient = ODLib(this)
        ODLogger.enableLogs(loggingConsole, LogLevel.Info)
        ODLogger.startBackgroundLoggingService()
        modelSpinner = findViewById(R.id.select_model)
        modelSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener{

            override fun onNothingSelected(parent: AdapterView<*>?) {}

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                odClient.listModels { models ->
                    for (model in models) {
                        if (model.modelName == parent?.selectedItem) {
                            odClient.setModel(model)
                            ODLogger.logInfo("${model.modelName}\t\tloaded: ${model.downloaded}")
                            return@listModels
                        }
                    }
                }

            }
        }


        schedulerSpinner = findViewById(R.id.select_scheduler)
        schedulerSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener{

            override fun onNothingSelected(parent: AdapterView<*>?) {}

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                for (scheduler in schedulersIds)
                    if (scheduler.second == parent?.selectedItem) {
                        thread {
                            println(parent.selectedItem)
                            odClient.setScheduler(scheduler.first)
                        }
                    }
            }
        }

        schedulerSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, schedulersArrayList)

        findViewById<ToggleButton>(R.id.serviceToggleButton).isChecked = odClient.serviceRunningWorker()
        findViewById<ToggleButton>(R.id.serverToggleButton).isChecked = odClient.serviceRunningScheduler()


        schedulerArrayAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, schedulersArrayList)
        modelArrayAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, modelsArrayList)

        verifyStoragePermissions(this)
        requestBatteryPermissions()
        //registerBroadcastReceiver()
    }


    override fun onDestroy(){
        super.onDestroy()
        odClient.destroy(true)
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
        @SuppressLint("StaticFieldLeak")
        lateinit var odClient: ODLib
    }
}

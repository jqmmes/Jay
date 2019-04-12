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
import com.google.common.primitives.Floats.min
import pt.up.fc.dcc.hyrax.odlib.logger.LogLevel
//import pt.up.fc.dcc.hyrax.odlib.tensorflow.COCODataLabels
import pt.up.fc.dcc.hyrax.odlib.logger.ODLogger
import pt.up.fc.dcc.hyrax.odlib.structures.Model
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.*
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

@Suppress("UNUSED_PARAMETER")
class MainActivity : AppCompatActivity(), SeekBar.OnSeekBarChangeListener {
    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
        when (seekBar?.id) {
            R.id.computeSeekBar -> updateWeights(computeWeight=progress/100.0f)
            R.id.jobsSeekBar -> updateWeights(jobsWeight=progress/100.0f)
            R.id.queueSeekBar -> updateWeights(queueWeight=progress/100.0f)
            R.id.batterySeekBar -> updateWeights(batteryWeight=progress/100.0f)
            R.id.bandwidthSeekBar -> updateWeights(bandwidthWeight=progress/100.0f)
            else -> updateWeights()
        }
    }

    private var computeWeight = 0.5f
    private var jobsWeight = 0.1f
    private var queueWeight = 0.1f
    private var batteryWeight = 0.2f
    private var bandwidthWeight = 0.1f

    private fun updateWeights(computeWeight: Float = this.computeWeight, jobsWeight: Float = this.jobsWeight, queueWeight: Float = this.queueWeight, batteryWeight: Float = this.batteryWeight, bandwidthWeight: Float = this.bandwidthWeight) {
        when {
            computeWeight != this.computeWeight -> {
                this.computeWeight = computeWeight
                val increment = (1.0f - sumWeights())/4.0f
                incrementWeights(0f,increment,increment,increment,increment)
            }
            jobsWeight != this.jobsWeight -> {
                this.jobsWeight = jobsWeight
                val increment = (1.0f - sumWeights())/4.0f
                incrementWeights(increment,0f,increment,increment,increment)
            }
            queueWeight != this.queueWeight -> {
                this.queueWeight = queueWeight
                val increment = (1.0f - sumWeights())/4.0f
                incrementWeights(increment,increment,0f,increment,increment)
            }
            batteryWeight != this.batteryWeight -> {
                this.batteryWeight = batteryWeight
                val increment = (1.0f - sumWeights())/4.0f
                incrementWeights(increment,increment,increment,0f,increment)
            }
            bandwidthWeight != this.bandwidthWeight -> {
                this.bandwidthWeight = bandwidthWeight
                val increment = (1.0f - sumWeights())/4.0f
                incrementWeights(increment,increment,increment,increment,0f)
            }
        }
    }

    private fun incrementWeights(computeWeight: Float = 0f, jobsWeight: Float = 0f, queueWeight: Float = 0f, batteryWeight: Float = 0f, bandwidthWeight: Float = 0f) {
        this.computeWeight += computeWeight
            if (this.computeWeight < 0f) {
                val additional = -(this.computeWeight/3.0)
                this.computeWeight = 0f
                when {
                    jobsWeight != 0f -> this.jobsWeight + additional
                    queueWeight != 0f -> this.queueWeight + additional
                    batteryWeight != 0f -> this.batteryWeight + additional
                    bandwidthWeight != 0f -> this.bandwidthWeight + additional
                }
            }
        this.jobsWeight += jobsWeight
        if (this.jobsWeight < 0f) {
            val additional = -(this.jobsWeight/3.0)
            this.jobsWeight = 0f
            when {
                computeWeight != 0f -> this.computeWeight + additional
                queueWeight != 0f -> this.queueWeight + additional
                batteryWeight != 0f -> this.batteryWeight + additional
                bandwidthWeight != 0f -> this.bandwidthWeight + additional
            }
        }
        this.queueWeight += queueWeight
        if (this.queueWeight < 0f) {
            val additional = -(this.queueWeight/3.0)
            this.queueWeight = 0f
            when {
                jobsWeight != 0f -> this.jobsWeight + additional
                computeWeight != 0f -> this.computeWeight + additional
                batteryWeight != 0f -> this.batteryWeight + additional
                bandwidthWeight != 0f -> this.bandwidthWeight + additional
            }
        }
        this.batteryWeight += batteryWeight
        if (this.batteryWeight < 0f) {
            val additional = -(this.batteryWeight/3.0)
            this.batteryWeight = 0f
            when {
                jobsWeight != 0f -> this.jobsWeight + additional
                queueWeight != 0f -> this.queueWeight + additional
                computeWeight != 0f -> this.computeWeight + additional
                bandwidthWeight != 0f -> this.bandwidthWeight + additional
            }
        }
        this.bandwidthWeight += bandwidthWeight
        if (this.bandwidthWeight < 0f) {
            val additional = -(this.bandwidthWeight/3.0)
            this.bandwidthWeight = 0f
            when {
                jobsWeight != 0f -> this.jobsWeight + additional
                queueWeight != 0f -> this.queueWeight + additional
                batteryWeight != 0f -> this.batteryWeight + additional
                computeWeight != 0f -> this.computeWeight + additional
            }
        }
        val sum = sumWeights()
        if (sum > 1f) {
            val excess = sum-1f
            when {
                computeWeight == 0f -> removeExcess(excess, compute = true)
                jobsWeight == 0f -> removeExcess(excess, jobs = true)
                queueWeight == 0f -> removeExcess(excess, queue = true)
                batteryWeight == 0f -> removeExcess(excess, battery = true)
                bandwidthWeight == 0f -> removeExcess(excess, bandwidth = true)
            }
        }
    }

    private fun removeExcess(excess: Float, compute: Boolean = false, jobs: Boolean = false, queue: Boolean = false, battery: Boolean = false, bandwidth: Boolean = false) {
        var toDiv = 0
        when {
            !compute && computeWeight > 0f -> toDiv++
            !jobs && jobsWeight > 0f -> toDiv++
            !queue && queueWeight > 0f -> toDiv++
            !battery && batteryWeight > 0f -> toDiv++
            !bandwidth && bandwidthWeight > 0f -> toDiv++
        }
        val additional = excess/toDiv.toFloat()
        var newExcess = 0f
                if (computeWeight > 0f && !compute) {
                    val toRemove = min(computeWeight, additional)
                    computeWeight -= toRemove
                    newExcess += (additional-toRemove)
                }
                if (jobsWeight > 0f && !jobs) {
                    val toRemove = min(jobsWeight, additional)
                    jobsWeight -= toRemove
                    newExcess += (additional-toRemove)
                }
                if (queueWeight > 0f && !queue) {
                    val toRemove = min(queueWeight, additional)
                    queueWeight -= toRemove
                    newExcess += (additional-toRemove)
                }
                if (batteryWeight > 0f && !battery) {
                    val toRemove = min(batteryWeight, additional)
                    batteryWeight -= toRemove
                    newExcess += (additional-toRemove)
                }
                if (bandwidthWeight > 0f && !bandwidth) {
                    val toRemove = min(bandwidthWeight, additional)
                    bandwidthWeight -= toRemove
                    newExcess += (additional-toRemove)
                }
        if (newExcess > 0) removeExcess(newExcess, compute, jobs, queue, battery, bandwidth)
    }

    private fun sumWeights() : Float{
        return computeWeight+jobsWeight+queueWeight+batteryWeight+bandwidthWeight
    }

    override fun onStartTrackingTouch(seekBar: SeekBar?) {}

    override fun onStopTrackingTouch(seekBar: SeekBar?) {
        updateSeekBars()
        if (odClient.serviceRunningScheduler()) odClient.updateSmartWeights(computeWeight, jobsWeight, queueWeight, batteryWeight, bandwidthWeight) {S ->
            if (S) runOnUiThread { Toast.makeText(applicationContext,"Smart Weights updated",Toast.LENGTH_SHORT).show() }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateSeekBars() {
        findViewById<SeekBar>(R.id.computeSeekBar).progress = (computeWeight*100).toInt()
        findViewById<SeekBar>(R.id.jobsSeekBar).progress = (jobsWeight*100).toInt()
        findViewById<SeekBar>(R.id.queueSeekBar).progress = (queueWeight*100).toInt()
        findViewById<SeekBar>(R.id.batterySeekBar).progress = (batteryWeight*100).toInt()
        findViewById<SeekBar>(R.id.bandwidthSeekBar).progress = (bandwidthWeight*100).toInt()
        findViewById<TextView>(R.id.computeWeight).text = "%.2f".format(computeWeight)
        findViewById<TextView>(R.id.jobWeight).text = "%.2f".format(jobsWeight)
        findViewById<TextView>(R.id.queueWeight).text = "%.2f".format(queueWeight)
        findViewById<TextView>(R.id.batteryWeight).text = "%.2f".format(batteryWeight)
        findViewById<TextView>(R.id.bandwidthWeight).text = "%.2f".format(bandwidthWeight)
    }

    private lateinit var loggingConsole: LogInterface
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
            odClient.scheduleJob(loadAssetImage(assets.open("benchmark-small/${assets.list("benchmark-small")!![0]}"))) {R ->
                ODLogger.logInfo("New Result: $R")
            }

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
        modelsList.clear()
        odClient.listModels {models ->
            for (model in models) {
                modelsList.add(model)
                modelsArrayList.add(model.modelName)
            }
            runOnUiThread {
                modelArrayAdapter.notifyDataSetChanged()
                modelSpinner.postInvalidate()
                modelSpinner.adapter = modelArrayAdapter
            }
        }
    }

    private val modelsList = mutableListOf<Model>()
    private val modelsArrayList = ArrayList<String>()
    private lateinit var modelArrayAdapter: ArrayAdapter<String>
    private val schedulersArrayList = ArrayList<String>()
    private lateinit var schedulerArrayAdapter: ArrayAdapter<String>

    private lateinit var modelSpinner: Spinner
    private lateinit var schedulerSpinner: Spinner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        loggingConsole = LogInterface(this, findViewById(R.id.loggingConsole))
        loggingConsole.benchmark(this)
        odClient = ODLib(this)
        ODLogger.enableLogs(loggingConsole, LogLevel.Info)
        ODLogger.startBackgroundLoggingService()
        modelSpinner = findViewById(R.id.select_model)
        modelSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener{

            override fun onNothingSelected(parent: AdapterView<*>?) {}

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                for (model in modelsList) {
                    if (model.modelName == parent?.selectedItem) {
                        odClient.setModel(model)
                        ODLogger.logInfo("${model.modelName}\t\tloaded: ${model.downloaded}")
                        return
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
                            odClient.setScheduler(scheduler.first) {S ->
                                if(S) {
                                    runOnUiThread { Toast.makeText(applicationContext,"Scheduler ${parent.selectedItem} set", Toast.LENGTH_SHORT).show() }
                                    odClient.updateSmartWeights(computeWeight, jobsWeight, queueWeight, batteryWeight, bandwidthWeight) {SS ->
                                        if (SS) runOnUiThread { Toast.makeText(applicationContext,"Smart Weights updated",Toast.LENGTH_SHORT).show() }
                                        println("Weights $SS")
                                    }
                                }
                            }
                        }
                    }
            }
        }

        schedulerSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, schedulersArrayList)

        findViewById<ToggleButton>(R.id.serviceToggleButton).isChecked = odClient.serviceRunningWorker()
        findViewById<ToggleButton>(R.id.serverToggleButton).isChecked = odClient.serviceRunningScheduler()


        schedulerArrayAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, schedulersArrayList)
        modelArrayAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, modelsArrayList)

        findViewById<SeekBar>(R.id.computeSeekBar).setOnSeekBarChangeListener(this)
        findViewById<SeekBar>(R.id.jobsSeekBar).setOnSeekBarChangeListener(this)
        findViewById<SeekBar>(R.id.queueSeekBar).setOnSeekBarChangeListener(this)
        findViewById<SeekBar>(R.id.batterySeekBar).setOnSeekBarChangeListener(this)
        findViewById<SeekBar>(R.id.bandwidthSeekBar).setOnSeekBarChangeListener(this)

        updateSeekBars()


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

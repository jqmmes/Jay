package pt.up.fc.dcc.hyrax.jay.services.profiler

import com.google.gson.Gson
import com.google.gson.internal.LinkedTreeMap
import org.apache.commons.collections4.queue.CircularFifoQueue
import pt.up.fc.dcc.hyrax.jay.grpc.GRPCServerBase
import pt.up.fc.dcc.hyrax.jay.logger.JayLogger
import pt.up.fc.dcc.hyrax.jay.proto.JayProto.*
import pt.up.fc.dcc.hyrax.jay.proto.JayProto.ServiceStatus.Type.PROFILER
import pt.up.fc.dcc.hyrax.jay.services.broker.grpc.BrokerGRPCClient
import pt.up.fc.dcc.hyrax.jay.services.profiler.grpc.ProfilerGRPCServer
import pt.up.fc.dcc.hyrax.jay.services.profiler.status.device.battery.BatteryInfo
import pt.up.fc.dcc.hyrax.jay.services.profiler.status.device.battery.BatteryMonitor
import pt.up.fc.dcc.hyrax.jay.services.profiler.status.device.cpu.CPUManager
import pt.up.fc.dcc.hyrax.jay.services.profiler.status.device.sensors.SensorManager
import pt.up.fc.dcc.hyrax.jay.services.profiler.status.device.transport.CellularTechnology
import pt.up.fc.dcc.hyrax.jay.services.profiler.status.device.transport.TransportInfo
import pt.up.fc.dcc.hyrax.jay.services.profiler.status.device.transport.TransportManager
import pt.up.fc.dcc.hyrax.jay.services.profiler.status.device.transport.TransportMedium
import pt.up.fc.dcc.hyrax.jay.services.profiler.status.device.usage.PackageUsages
import pt.up.fc.dcc.hyrax.jay.services.profiler.status.device.usage.UsageManager
import pt.up.fc.dcc.hyrax.jay.services.profiler.status.jay.JayState
import pt.up.fc.dcc.hyrax.jay.services.profiler.status.jay.JayStateManager
import pt.up.fc.dcc.hyrax.jay.services.profiler.status.jay.JayStateManager.genJayStates
import pt.up.fc.dcc.hyrax.jay.utils.JaySettings
import pt.up.fc.dcc.hyrax.jay.utils.JayUtils
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.random.Random
import pt.up.fc.dcc.hyrax.jay.proto.JayProto.JayState as JayStateProto

object ProfilerService {
    private var usageManager: UsageManager? = null
    private var cpuManager: CPUManager? = null
    private var transportManager: TransportManager? = null
    private var sensorManager: SensorManager? = null
    private var batteryMonitor: BatteryMonitor? = null
    private var running: Boolean = false
    private val broker = BrokerGRPCClient("127.0.0.1")
    private var server: GRPCServerBase? = null
    private var recordingStartTime: Long = 0
    private var recording: AtomicBoolean = AtomicBoolean(false)
    private val LOCK: Any = Object()
    private var recordInterval = 500L // time in ms
    private var recalculateAveragesInterval = 1000L // time in ms
    private var recordingLatch = CountDownLatch(0)
    private val recordings = LinkedHashSet<ProfileRecording>()
    private val batteryInfo = BatteryInfo()

    private val RAW_EXPECTED_CPU_MAP_LOCK = Object()
    private val RAW_EXPECTED_CURRENT_MAP_LOCK = Object()
    private val EXPECTED_CPU_MAP_LOCK = Object()
    private val EXPECTED_CURRENT_MAP_LOCK = Object()

    private data class CpuEstimatorKey(val jayState: Set<JayState>, val deviceLoad: PackageUsages?)

    private fun genCpuEstimatorKeyFromStr(str: String): CpuEstimatorKey {
        val stateSet = mutableSetOf<JayState>()
        str.substring("CpuEstimatorKey(jayState=[".length, str.indexOf("]")).split(", ").forEach {
            stateSet.add(when (it) {
                "MULTICAST_LISTEN" -> JayState.MULTICAST_LISTEN
                "COMPUTE" -> JayState.COMPUTE
                "DATA_RCV" -> JayState.DATA_RCV
                "DATA_SND" -> JayState.DATA_SND
                "MULTICAST_ADVERTISE" -> JayState.MULTICAST_ADVERTISE
                else -> JayState.IDLE
            })
        }
        return CpuEstimatorKey(stateSet, null)
    }

    private data class BatteryCurrentKey(val transportInfo: TransportInfo?, val sensors: Set<String>,
                                         val batteryStatus: BatteryStatus)

    private fun genBatteryCurrentKey(str: String): BatteryCurrentKey {
        val transportInfo = str.substring("BatteryCurrentKey(transportInfo=TransportInfo(".length, str.indexOf(")"))
        val sensors = str.substring(str.indexOf("[") + 1, str.indexOf("]"))
        val batteryStatus = str.substring(str.indexOf("batteryStatus=") + "batteryStatus=".length, str.length - 1)

        var medium: TransportMedium? = null
        var up: Int? = null
        var down: Int? = null
        var cellular: CellularTechnology? = null
        transportInfo.split(", ").forEach {
            when (it.substring(0, it.indexOf("="))) {
                "medium" -> {
                    medium = when (it.substring(it.indexOf("=") + 1)) {
                        "WIFI" -> TransportMedium.WIFI
                        "CELLULAR" -> TransportMedium.CELLULAR
                        "BLUETOOTH" -> TransportMedium.BLUETOOTH
                        "ETHERNET" -> TransportMedium.ETHERNET
                        "VPN" -> TransportMedium.VPN
                        "WIFI_AWARE" -> TransportMedium.WIFI_AWARE
                        "LOWPAN" -> TransportMedium.LOWPAN
                        else -> TransportMedium.UNKNOWN
                    }
                }
                "upstreamBandwidth" -> try {
                    up = it.substring(it.indexOf("=") + 1).toInt()
                } catch (ignore: Exception) {
                }
                "downstreamBandwidth" -> try {
                    down = it.substring(it.indexOf("=") + 1).toInt()
                } catch (ignore: Exception) {
                }
                "cellularTechnology" -> {
                    cellular = when (it.substring(it.indexOf("="))) {
                        "SECOND_GEN" -> CellularTechnology.SECOND_GEN
                        "THIRD_GEN" -> CellularTechnology.THIRD_GEN
                        "FOURTH_GEN" -> CellularTechnology.FOURTH_GEN
                        "FIFTH_GEN," -> CellularTechnology.FIFTH_GEN
                        "UNKNOWN_GEN" -> CellularTechnology.UNKNOWN_GEN
                        else -> null
                    }
                }
            }
        }
        val sensorSet = mutableSetOf<String>()
        sensors.split(", ").forEach { sensorSet.add(it) }

        val battery = when (batteryStatus) {
            "FULL" -> BatteryStatus.FULL
            "AC_CHARGING" -> BatteryStatus.AC_CHARGING
            "USB_CHARGING" -> BatteryStatus.USB_CHARGING
            "QI_CHARGING" -> BatteryStatus.QI_CHARGING
            "CHARGING" -> BatteryStatus.CHARGING
            "DISCHARGING" -> BatteryStatus.DISCHARGING
            else -> BatteryStatus.UNKNOWN
        }

        return BatteryCurrentKey(TransportInfo(medium!!, up ?: 0, down ?: 0, cellular), sensorSet.toSet(), battery)
    }

    private val rawExpectedCpuHashMap = LinkedHashMap<CpuEstimatorKey, CircularFifoQueue<List<Long>>>()
    private val rawExpectedCurrentHashMap = LinkedHashMap<BatteryCurrentKey, HashMap<List<Long>, CircularFifoQueue<Int>>>()

    private val expectedCpuHashMap = LinkedHashMap<CpuEstimatorKey, List<Long>?>()
    private val expectedCurrentHashMap = LinkedHashMap<BatteryCurrentKey, HashMap<List<Long>, Int>>()

    private var recordingDir: File? = null

    private fun setSimilarity(v1: List<Long>, v2: List<Long>): Long {
        var diff = 0L
        if (v1.isEmpty() || v2.isEmpty()) return Long.MAX_VALUE
        for (i in v1.indices) {
            diff += if (i < v2.size)
                abs(v1.elementAt(i) - v2.elementAt(i))
            else
                abs(v1.elementAt(i) - v2.last())
        }
        if (v2.size > v1.size) {
            for (i in v1.size until v2.size) {
                diff += abs(v1.last() - v2.elementAt(i))
            }
        }
        return diff
    }

    private fun mostSimilarSet(v1: List<Long>, v2: Set<List<Long>>): List<Long>? {
        var maxDiff = Long.MAX_VALUE
        var retSet: List<Long>? = null
        v2.forEach {
            val diff = setSimilarity(v1, it)
            if (diff < maxDiff) {
                maxDiff = diff
                retSet = it
            }
        }
        return retSet
    }

    private fun getMovingAvg(v1: CircularFifoQueue<Int>?): Int {
        var tot = 0
        v1?.forEach { tot += it }
        return tot / (v1?.size ?: 1)
    }

    private fun getListMovingAvg(v1: CircularFifoQueue<List<Long>>?): List<Long>? {
        if (v1 == null || v1.isEmpty()) return null
        val cpuCoreCounts: MutableList<Int> = ArrayList()
        val cpuCoreSums: MutableList<Long> = ArrayList()
        val retSet: MutableList<Long> = mutableListOf()
        v1.forEach {
            for (i in it.indices) {
                if (cpuCoreCounts.size > i) cpuCoreCounts[i] = cpuCoreCounts[i] + 1
                else cpuCoreCounts.add(1)
                if (cpuCoreSums.size > i) cpuCoreSums[i] = cpuCoreSums[i] + it.elementAt(i)
                else cpuCoreSums.add(it.elementAt(i))
            }
        }
        for (i in 0 until cpuCoreCounts.size) {
            retSet.add((cpuCoreSums[i] / cpuCoreCounts[i]))
        }
        return retSet
    }

    private fun getExpectedCpuMhz(k: CpuEstimatorKey): List<Long>? {
        return if ((rawExpectedCpuHashMap.containsKey(k))) getListMovingAvg(rawExpectedCpuHashMap[k]) else null
    }

    private fun getExpectedCurrent(k: BatteryCurrentKey, expectedCpuMhz: List<Long>): Int? {
        var ret: Int? = null
        synchronized(EXPECTED_CURRENT_MAP_LOCK) {
            if (expectedCurrentHashMap.containsKey(k)) {
                val mostSimilarSet = mostSimilarSet(expectedCpuMhz, expectedCurrentHashMap[k]!!.keys)
                if (mostSimilarSet != null) ret = expectedCurrentHashMap[k]!![mostSimilarSet]
            }
        }
        return ret
    }

    fun getExpectedCurrents(): CurrentEstimations? {
        val key = BatteryCurrentKey(this.transportManager?.getTransport(),
                this.sensorManager?.getActiveSensors() ?: setOf(),
                this.batteryInfo.batteryStatus)

        val builder = CurrentEstimations.newBuilder()
        synchronized(EXPECTED_CPU_MAP_LOCK) {
            builder.idle = getExpectedCurrent(key, expectedCpuHashMap[CpuEstimatorKey(setOf(JayState.IDLE), null)]
                    ?: listOf()) ?: 0
            builder.compute = getExpectedCurrent(key, expectedCpuHashMap[CpuEstimatorKey(genJayStates(JayState.COMPUTE), null)]
                    ?: listOf()) ?: 0
            builder.rx = getExpectedCurrent(key, expectedCpuHashMap[CpuEstimatorKey(genJayStates(JayState.DATA_RCV), null)]
                    ?: listOf()) ?: 0
            builder.tx = getExpectedCurrent(key, expectedCpuHashMap[CpuEstimatorKey(genJayStates(JayState.DATA_SND), null)]
                    ?: listOf()) ?: 0
        }
        builder.batteryLevel = this.batteryInfo.batteryLevel
        builder.batteryCapacity = this.batteryInfo.batteryCapacity
        return builder.build()
    }

    fun start(useNettyServer: Boolean = false, batteryMonitor: BatteryMonitor? = null, transportManager:
    TransportManager? = null, cpuManager: CPUManager? = null, usageManager: UsageManager? = null, sensorManager:
              SensorManager? = null, recordingDir: File? = null) {
        JayLogger.logInfo("INIT")
        if (this.running) return
        this.batteryMonitor = batteryMonitor
        setBatteryCallbacks()
        this.batteryMonitor?.monitor()
        this.transportManager = transportManager
        this.cpuManager = cpuManager
        this.usageManager = usageManager
        this.sensorManager = sensorManager
        this.recordingDir = recordingDir
        repeat(30) {
            if (this.server == null) {
                this.server = ProfilerGRPCServer(useNettyServer).start()
                if (this.server == null) JaySettings.PROFILER_PORT = Random.nextInt(30000, 64000)
            }
        }
        this.running = true
        this.broker.announceServiceStatus(ServiceStatus.newBuilder().setType(PROFILER).setRunning(true).build())
        {
            JayLogger.logInfo("COMPLETE")
        }
    }

    fun stop(stopGRPCServer: Boolean = true): Status {
        return try {
            this.running = false
            if (stopGRPCServer) this.server?.stop()
            this.batteryMonitor?.destroy()
            this.broker.announceServiceStatus(ServiceStatus.newBuilder().setType(PROFILER).setRunning(false).build())
            {
                JayLogger.logInfo("STOP")
            }
            JayUtils.genStatusSuccess()
        } catch (ignore: Exception) {
            JayLogger.logError("Error Stopping ProfilerService")
            JayUtils.genStatusError()
        }
    }

    private fun getTimeRangeProto(start: Long, end: Long): TimeRange? {
        val timeRange = TimeRange.newBuilder()
        timeRange.start = if (start == 0L) end - JaySettings.WORKER_STATUS_UPDATE_INTERVAL else start
        timeRange.end = end
        return timeRange.build()
    }

    private fun getJayStatesProto(v1: Set<JayState>): Set<JayStateProto?> {
        val jayStates = LinkedHashSet<JayStateProto?>()
        v1.forEach { state ->
            jayStates.add(JayUtils.genJayStateProto(state))
        }
        return jayStates
    }

    private fun getTransportProto(transportInfo: TransportInfo): Transport {
        val transportBuilder = Transport.newBuilder()
        transportBuilder.medium = Transport.Medium.forNumber(transportInfo.medium.ordinal)
        if (transportInfo.medium == TransportMedium.CELLULAR)
            transportBuilder.cellularTechnology = Transport.CellularTechnology.forNumber(
                    transportInfo.cellularTechnology!!.ordinal)
        transportBuilder.downstreamBandwidth = transportInfo.downstreamBandwidth
        transportBuilder.upstreamBandwidth = transportInfo.upstreamBandwidth
        JayLogger.logInfo("TRANSPORT_INFO", "",
                "MEDIUM=${transportInfo.medium.name}",
                "DOWNSTREAM=${transportInfo.downstreamBandwidth}",
                "UPSTREAM=${transportInfo.upstreamBandwidth}"
        )
        if (transportInfo.medium == TransportMedium.CELLULAR)
            JayLogger.logInfo("TRANSPORT_INFO_CELLULAR", "",
                    "CELLULAR_TECHNOLOGY=${transportInfo.cellularTechnology?.name}"
            )
        return transportBuilder.build()
    }

    private fun getBatteryProto(): Battery? {
        this.batteryInfo.batteryCurrent = this.batteryMonitor?.getBatteryCurrentNow() ?: -1
        this.batteryInfo.batteryEnergy = this.batteryMonitor?.getBatteryRemainingEnergy() ?: -1
        this.batteryInfo.batteryCharge = this.batteryMonitor?.getBatteryCharge() ?: -1
        this.batteryInfo.batteryCapacity = this.batteryMonitor?.getBatteryCapacity() ?: -1
        this.batteryInfo.batteryLevel = if (this.batteryInfo.batteryLevel == -1)
            this.batteryMonitor?.getBatteryLevel() ?: -1 else this.batteryInfo.batteryLevel
        if (this.batteryInfo.batteryStatus == BatteryStatus.UNKNOWN) {
            this.batteryInfo.batteryStatus = this.batteryMonitor?.getBatteryStatus() ?: BatteryStatus.UNKNOWN
        }

        val batteryBuilder = Battery.newBuilder()
        batteryBuilder.batteryLevel = this.batteryInfo.batteryLevel
        batteryBuilder.batteryCurrent = this.batteryInfo.batteryCurrent
        batteryBuilder.batteryVoltage = this.batteryInfo.batteryVoltage
        batteryBuilder.batteryTemperature = this.batteryInfo.batteryTemperature
        batteryBuilder.batteryEnergy = this.batteryInfo.batteryEnergy
        batteryBuilder.batteryCharge = this.batteryInfo.batteryCharge
        batteryBuilder.batteryCapacity = this.batteryInfo.batteryCapacity
        batteryBuilder.batteryStatus = this.batteryInfo.batteryStatus
        JayLogger.logInfo("BATTERY_DETAILS", "",
                "CURRENT=${this.batteryInfo.batteryCurrent}",
                "ENERGY=${this.batteryInfo.batteryEnergy}",
                "CHARGE=${this.batteryInfo.batteryCharge}",
                "LEVEL=${this.batteryInfo.batteryLevel}",
                "VOLTAGE=${this.batteryInfo.batteryVoltage}",
                "TEMPERATURE=${this.batteryInfo.batteryTemperature}",
                "CAPACITY=${this.batteryInfo.batteryCapacity}",
                "STATUS=${this.batteryInfo.batteryStatus.name}"
        )
        return batteryBuilder.build()
    }

    /**
     *     TimeRange timeRange = 1;
     *     repeated JayState jayState = 2;
     *     Battery battery = 3
     *     int32 cpuCount = 4;
     *     repeated int32 cpuFrequency = 5;
     *     Transport transport = 6;
     *     repeated string systemUsage = 7;
     */
    internal fun getSystemProfile(recording: Boolean = false, msToRetrieve: Long = 500): ProfileRecording {
        if (this.recordingLatch.count <= 0 && recording)
            return ProfileRecording.getDefaultInstance()

        val recordBuilder = ProfileRecording.newBuilder()
        try {
            val currentTime = System.currentTimeMillis()
            val cpus = this.cpuManager?.getCpus() ?: emptySet()

            val medium = this.transportManager?.getTransport()

            val jayStates = JayStateManager.getJayStates().states
            val activeSensors = this.sensorManager?.getActiveSensors() ?: emptySet()

            recordBuilder.timeRange =
                    if (recording) getTimeRangeProto(this.recordingStartTime, currentTime)
                    else getTimeRangeProto(currentTime - msToRetrieve, currentTime)
            JayLogger.logInfo("TIME_RANGE", "", "START=${this.recordingStartTime}", "END=$currentTime")
            var states = ""

            getJayStatesProto(jayStates).forEach { state ->
                recordBuilder.addJayState(state)
                states += "${state?.jayState?.name}, "
            }
            JayLogger.logInfo("JAY_STATES", "", states)
            recordBuilder.battery = getBatteryProto()
            recordBuilder.cpuCount = cpus.size
            var cpuSpeedsStr = ""
            val cpuSpeeds = mutableListOf<Long>()
            cpus.forEach { cpu_number ->
                val cpuSpeed: Long = this.cpuManager?.getCurrentCPUClockSpeed(cpu_number) ?: 0L
                recordBuilder.addCpuFrequency(cpuSpeed)
                cpuSpeedsStr += "CPU_$cpu_number=$cpuSpeed, "
                cpuSpeeds.add(cpuSpeed)
            }
            JayLogger.logInfo("CPU", "", "CPU_COUNT=${cpus.size}, $cpuSpeedsStr")
            if (medium != null) recordBuilder.transport = getTransportProto(medium)
            val packageUsages = if (recording)
                this.usageManager?.getRecentUsageList(currentTime - this.recordingStartTime)
            else this.usageManager?.getRecentUsageList(msToRetrieve)
            var pkgs = ""
            packageUsages?.forEach { pkg ->
                recordBuilder.addSystemUsage(pkg.pkgName)
                pkgs += "${pkg.pkgName}, "
            }
            JayLogger.logInfo("PKG_USAGE", "", pkgs)
            var sensorStr = ""

            activeSensors.forEach { sensor ->
                recordBuilder.addSensors(sensor)
                sensorStr += "$sensor, "
            }

            /**
             * Insert recorded values in the circular FIFO for future processing
             */
            val cpuEstimatorKey = CpuEstimatorKey(jayStates, null)
            synchronized(RAW_EXPECTED_CPU_MAP_LOCK) {
                if (!this.rawExpectedCpuHashMap.containsKey(cpuEstimatorKey)) {
                    this.rawExpectedCpuHashMap[cpuEstimatorKey] = CircularFifoQueue(JaySettings.JAY_STATE_TO_CPU_CIRCULAR_FIFO_SIZE)
                }
                this.rawExpectedCpuHashMap[cpuEstimatorKey]!!.add(cpuSpeeds)
                JayLogger.logInfo("NEW_CPU_READING", "", "STATE=$cpuEstimatorKey", "SPEED=$cpuSpeeds")
                //JayLogger.logInfo("EXPECTED_CPU_HASH_MAP", "", "${this.expectedCpuHashMap}")
            }

            val batteryCurrentKey = BatteryCurrentKey(medium, activeSensors, batteryInfo.batteryStatus)
            synchronized(RAW_EXPECTED_CURRENT_MAP_LOCK) {
                if (!this.rawExpectedCurrentHashMap.containsKey(batteryCurrentKey)) {
                    this.rawExpectedCurrentHashMap[batteryCurrentKey] = LinkedHashMap()
                }
                if (!this.rawExpectedCurrentHashMap[batteryCurrentKey]!!.containsKey(cpuSpeeds)) {
                    this.rawExpectedCurrentHashMap[batteryCurrentKey]!![cpuSpeeds] =
                            CircularFifoQueue(JaySettings.CPU_TO_BAT_CURRENT_CIRCULAR_FIFO_SIZE)
                }
                this.rawExpectedCurrentHashMap[batteryCurrentKey]!![cpuSpeeds]!!.add(batteryInfo.batteryCurrent)
                JayLogger.logInfo("NEW_CURRENT_READING", "", "STATE=$batteryCurrentKey", "CURRENT=${batteryInfo.batteryCurrent}")
                //JayLogger.logInfo("EXPECTED_CURRENT_HASH_MAP", "", "${this.expectedCurrentHashMap}")
            }


            JayLogger.logInfo("ACTIVE_SENSORS", "", sensorStr)
            if (recording) this.recordingStartTime = currentTime
        } catch (ignore: java.lang.RuntimeException) {
            // To prevent java.lang.RuntimeException: android.os.DeadSystemException
            JayLogger.logError("TRANSPORT_EXCEPTION", "", "ERROR: ${ignore.message}")
        }
        return recordBuilder.build()
    }

    private fun setBatteryCallbacks() {
        this.batteryMonitor?.setCallbacks(
                _levelChangeCallback = { level, voltage, temperature ->
                    this.batteryInfo.batteryLevel = level
                    this.batteryInfo.batteryVoltage = voltage
                    this.batteryInfo.batteryTemperature = temperature
                    this.batteryInfo.batteryCurrent = this.batteryMonitor?.getBatteryCurrentNow() ?: -1
                    this.batteryInfo.batteryEnergy = this.batteryMonitor?.getBatteryRemainingEnergy() ?: -1
                    this.batteryInfo.batteryCharge = this.batteryMonitor?.getBatteryCharge() ?: -1
                    JayLogger.logInfo("LEVEL_CHANGE_CB", actions = *arrayOf("NEW_BATTERY_LEVEL=$level", "NEW_BATTERY_VOLTAGE=$voltage", "NEW_BATTERY_TEMPERATURE=$temperature", "NEW_BATTERY_CURRENT=${this.batteryInfo.batteryCurrent}", "REMAINING_ENERGY=${this.batteryInfo.batteryEnergy}", "NEW_BATTERY_CHARGE=${this.batteryInfo.batteryCharge}"))
                },
                _statusChangeCallback = { status ->
                    if (status != this.batteryInfo.batteryStatus) {
                        JayLogger.logInfo("STATUS_CHANGE_CB", actions = *arrayOf("NEW_BATTERY_STATUS=${status.name}"))
                        this.batteryInfo.batteryStatus = status
                    }
                }
        )
    }

    internal fun startRecording(): Boolean {
        synchronized(LOCK) {
            if (this.recording.get()) return false
            JayLogger.logInfo("START_RECORDING")
            this.recording.set(true)
            recordingStartTime = System.currentTimeMillis()
            recordingLatch = CountDownLatch(2)
            this.recordings.clear()
            thread {
                do {
                    this.recordings.add(getSystemProfile(true))
                    Thread.sleep(recordInterval)
                } while (this.recording.get())
                recordingLatch.countDown()
                JayLogger.logInfo("START_RECORDING", "", "END")
            }
            thread {
                var cpuRecordingsFile: File? = null
                var currentRecordingsFile: File? = null
                if (this.recordingDir != null) {
                    if (!this.recordingDir!!.exists()) this.recordingDir!!.mkdirs()
                    cpuRecordingsFile = File(this.recordingDir, "cpu_recordings.json")
                    if (!cpuRecordingsFile.exists()) cpuRecordingsFile.createNewFile()
                    currentRecordingsFile = File(this.recordingDir, "current_recordings.json")
                    if (!currentRecordingsFile.exists()) currentRecordingsFile.createNewFile()
                }
                if (JaySettings.READ_RECORDED_PROFILE_DATA) {
                    JayLogger.logInfo("READING_RECORDED_PROFILE_DATA")
                    if (cpuRecordingsFile?.readText() != "") {
                        synchronized(RAW_EXPECTED_CPU_MAP_LOCK) {
                            try {
                                Gson().fromJson<LinkedHashMap<String, ArrayList<List<Long>>>>(
                                        cpuRecordingsFile?.readText() ?: "", this.rawExpectedCpuHashMap::class.java
                                ).forEach { (keyStr, values) ->
                                    val key = genCpuEstimatorKeyFromStr(keyStr)
                                    if (!this.rawExpectedCpuHashMap.containsKey(key)) {
                                        this.rawExpectedCpuHashMap[key] = CircularFifoQueue(JaySettings.JAY_STATE_TO_CPU_CIRCULAR_FIFO_SIZE)
                                    }
                                    values.forEach {
                                        this.rawExpectedCpuHashMap[key]?.add(it)
                                        JayLogger.logInfo("READING_RECORDED_CPU", "", "KEY=$key", "VALUE=$it")
                                    }
                                }
                            } catch (ignore: Exception) {
                            }
                        }
                    }
                    synchronized(RAW_EXPECTED_CURRENT_MAP_LOCK) {
                        try {
                            Gson().fromJson<LinkedHashMap<String, LinkedTreeMap<String, ArrayList<Int>>>>(
                                    currentRecordingsFile?.readText() ?: "", this.rawExpectedCurrentHashMap::class.java
                            ).forEach { (rawKey, values) ->
                                val key = genBatteryCurrentKey(rawKey)
                                if (!this.rawExpectedCurrentHashMap.containsKey(key)) {
                                    this.rawExpectedCurrentHashMap[key] = hashMapOf()
                                }
                                values.forEach { (hashKeyStr, circularFifoValues) ->
                                    val hashKey = listFromStr(hashKeyStr)
                                    if (!this.rawExpectedCurrentHashMap[key]!!.containsKey(hashKey)) {
                                        this.rawExpectedCurrentHashMap[key]!![hashKey] = CircularFifoQueue(JaySettings.CPU_TO_BAT_CURRENT_CIRCULAR_FIFO_SIZE)
                                    }
                                    circularFifoValues.forEach {
                                        this.rawExpectedCurrentHashMap[key]!![hashKey]?.add(it)
                                        JayLogger.logInfo("READING_RECORDED_CURRENT", "", "KEY=$key", "SUB_KEY=$hashKey",
                                                "VALUE=$it")
                                    }
                                }
                            }
                        } catch (ignore: Exception) {
                        }
                    }
                }
                Thread.sleep(recalculateAveragesInterval)
                do {
                    synchronized(RAW_EXPECTED_CPU_MAP_LOCK) {
                        rawExpectedCpuHashMap.keys.forEach {
                            synchronized(EXPECTED_CPU_MAP_LOCK) {
                                expectedCpuHashMap[it] = getExpectedCpuMhz(it)
                            }
                        }
                        cpuRecordingsFile?.writeText(Gson().toJson(rawExpectedCpuHashMap))
                    }
                    synchronized(RAW_EXPECTED_CURRENT_MAP_LOCK) {
                        rawExpectedCurrentHashMap.keys.forEach {
                            rawExpectedCurrentHashMap[it]?.keys?.forEach { cpu ->
                                synchronized(EXPECTED_CURRENT_MAP_LOCK) {
                                    if (!expectedCurrentHashMap.containsKey(it)) {
                                        expectedCurrentHashMap[it] = LinkedHashMap()
                                    }
                                    expectedCurrentHashMap[it]!![cpu] = getMovingAvg(rawExpectedCurrentHashMap[it]!![cpu]!!)
                                }
                            }
                        }
                        currentRecordingsFile?.writeText(Gson().toJson(rawExpectedCurrentHashMap))
                    }
                    Thread.sleep(recalculateAveragesInterval)
                } while (this.recording.get())
                recordingLatch.countDown()
                JayLogger.logInfo("START_PROFILE_AVERAGES_RECALCULATION", "", "END")
            }
        }
        return true
    }

    private fun listFromStr(str: String): List<Long> {
        val list = mutableListOf<Long>()
        str.substring(1, str.length - 1).split(", ").forEach { list.add(it.toLong()) }
        return list
    }

    fun stopRecording(): ProfileRecordings {
        JayLogger.logInfo("STOP_RECORDING")
        this.recording.set(false)
        recordingLatch.await()  // Await recording termination
        JayLogger.logInfo("STOP_RECORDING", "", "END")
        return ProfileRecordings.newBuilder().addAllProfileRecording(this.recordings).build()
    }
}
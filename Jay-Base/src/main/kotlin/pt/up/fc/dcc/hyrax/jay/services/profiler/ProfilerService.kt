package pt.up.fc.dcc.hyrax.jay.services.profiler

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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.abs
import pt.up.fc.dcc.hyrax.jay.proto.JayProto.JayState as JayStateProto

/**
 *
 * fun getExpectedCpuMhz(jay_state): Set<Long>
 * {JAY_STATE} -> EXPECTED_CPU_MhZ
 * future improvements: {JAY_STATE} + DeviceUsageLoad (RAM, TOTAL_MEMORY, APPS/NUMBER_OF_APPS/CPU_LOAD): Set<Long>
 *
 * fun getExpectedCurrent(CPU_SPEEDS, Transport, Sensors): Long uA
 * EXPECTED_CPU_Mhz + Transport + ACTIVE_SENSORS + BatteryState -> EXPECTED_CURRENT
 *
 *
 */
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


    private data class CpuEstimatorKey(val jayState: Set<JayState>, val deviceLoad: PackageUsages?)

    private data class BatteryCurrentKey(val transportInfo: TransportInfo?, val sensors: Set<String>,
                                         val batteryStatus: BatteryStatus)


    private val rawExpectedCpuHashMap = LinkedHashMap<CpuEstimatorKey, CircularFifoQueue<List<Long>>>()
    private val rawExpectedCurrentHashMap = LinkedHashMap<BatteryCurrentKey, HashMap<List<Long>, CircularFifoQueue<Int>>>()

    private val expectedCpuHashMap = LinkedHashMap<CpuEstimatorKey, List<Long>?>()
    private val expectedCurrentHashMap = LinkedHashMap<BatteryCurrentKey, HashMap<List<Long>, Int>>()


    private fun setSimilarity(v1: List<Long>, v2: List<Long>): Long {
        var diff = 0L
        for (i in 0..v1.size) {
            diff += abs(v1.elementAt(i) - v2.elementAt(i))
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
        if (v1 == null) return null
        val cpuCoreCounts: MutableList<Int> = ArrayList()
        val cpuCoreSums: MutableList<Long> = ArrayList()
        val retSet: MutableList<Long> = mutableListOf()
        v1.forEach {
            for (i in 0..it.size) {
                if (cpuCoreCounts.size >= i) cpuCoreCounts[i] = cpuCoreCounts[i] + 1
                else cpuCoreCounts.add(1)
                if (cpuCoreSums.size >= 1) cpuCoreSums[i] = cpuCoreSums[i] + it.elementAt(i)
                else cpuCoreSums.add(it.elementAt(i))
            }
        }
        for (i in 0..cpuCoreCounts.size) {
            retSet.add((cpuCoreSums[i] / cpuCoreCounts[i]))
        }
        return retSet
    }

    private fun getExpectedCpuMhz(k: CpuEstimatorKey): List<Long>? {
        return if ((rawExpectedCpuHashMap.containsKey(k))) getListMovingAvg(rawExpectedCpuHashMap[k]) else null
    }

    private fun getExpectedCurrent(k: BatteryCurrentKey, expectedCpuMhz: List<Long>): Int? {
        if (!rawExpectedCurrentHashMap.containsKey(k)) return null
        val cpuSet = mostSimilarSet(expectedCpuMhz, expectedCurrentHashMap[k]!!.keys) ?: return null
        return expectedCurrentHashMap[k]?.get(cpuSet)
    }

    // todo: Estimate total battery capacity & time to drop 1%
    fun getExpectedCurrents(): CurrentEstimations? {
        val key = BatteryCurrentKey(this.transportManager?.getTransport(),
                this.sensorManager?.getActiveSensors() ?: setOf(),
                this.batteryInfo.batteryStatus)
        val idleCurrent = getExpectedCurrent(key,
                getExpectedCpuMhz(CpuEstimatorKey(setOf(JayState.IDLE), null)) ?: listOf()) ?: 0
        val computeCurrent = getExpectedCurrent(key,
                getExpectedCpuMhz(CpuEstimatorKey(genJayStates(JayState.COMPUTE), null)) ?: listOf()) ?: 0
        val rxCurrent = getExpectedCurrent(key,
                getExpectedCpuMhz(CpuEstimatorKey(genJayStates(JayState.DATA_RCV), null)) ?: listOf()) ?: 0
        val txCurrent = getExpectedCurrent(key,
                getExpectedCpuMhz(CpuEstimatorKey(genJayStates(JayState.DATA_SND), null)) ?: listOf()) ?: 0
        val builder = CurrentEstimations.newBuilder()

        builder.idleBuilder.batteryAvgCurrent = idleCurrent
        builder.computeBuilder.batteryAvgCurrent = computeCurrent
        builder.rxBuilder.batteryAvgCurrent = rxCurrent
        builder.txBuilder.batteryAvgCurrent = txCurrent
        builder.batteryLevel = this.batteryInfo.batteryLevel
        return builder.build()
    }

    fun start(useNettyServer: Boolean = false, batteryMonitor: BatteryMonitor? = null, transportManager:
    TransportManager? = null, cpuManager: CPUManager? = null, usageManager: UsageManager? = null, sensorManager:
              SensorManager? = null) {
        JayLogger.logInfo("INIT")
        if (this.running) return
        this.batteryMonitor = batteryMonitor
        setBatteryCallbacks()
        this.batteryMonitor?.monitor()
        this.transportManager = transportManager
        this.cpuManager = cpuManager
        this.usageManager = usageManager
        this.sensorManager = sensorManager
        this.server = ProfilerGRPCServer(useNettyServer).start()
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
        }!!
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
        this.batteryInfo.batteryLevel = if (this.batteryInfo.batteryLevel == -1)
            this.batteryMonitor?.getBatteryCapacity() ?: -1 else this.batteryInfo.batteryLevel
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
        batteryBuilder.batteryStatus = this.batteryInfo.batteryStatus
        JayLogger.logInfo("BATTERY_DETAILS", "",
                "CURRENT=${this.batteryInfo.batteryCurrent}",
                "ENERGY=${this.batteryInfo.batteryEnergy}",
                "CHARGE=${this.batteryInfo.batteryCharge}",
                "LEVEL=${this.batteryInfo.batteryLevel}",
                "VOLTAGE=${this.batteryInfo.batteryVoltage}",
                "TEMPERATURE=${this.batteryInfo.batteryTemperature}",
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
         * Insert recorded values in the circular fifos for future processing
         */
        val cpuEstimatorKey = CpuEstimatorKey(jayStates, null)
        if (!this.rawExpectedCpuHashMap.containsKey(cpuEstimatorKey)) {
            this.rawExpectedCpuHashMap[cpuEstimatorKey] = CircularFifoQueue(JaySettings.JAY_STATE_TO_CPU_CIRCULAR_FIFO_SIZE)
        }
        this.rawExpectedCpuHashMap[CpuEstimatorKey(jayStates, null)]!!.add(cpuSpeeds)

        JayLogger.logInfo("RAW_EXPECTED_CPU_HASH_MAP", "", "${this.rawExpectedCpuHashMap}")

        val batteryCurrentKey = BatteryCurrentKey(medium, activeSensors, batteryInfo.batteryStatus)
        if (!this.rawExpectedCurrentHashMap.containsKey(batteryCurrentKey)) {
            this.rawExpectedCurrentHashMap[batteryCurrentKey] = LinkedHashMap()
        }
        if (!this.rawExpectedCurrentHashMap[batteryCurrentKey]!!.containsKey(cpuSpeeds)) {
            this.rawExpectedCurrentHashMap[batteryCurrentKey]!![cpuSpeeds] =
                    CircularFifoQueue(JaySettings.CPU_TO_BAT_CURRENT_CIRCULAR_FIFO_SIZE)
        }
        this.rawExpectedCurrentHashMap[batteryCurrentKey]!![cpuSpeeds]!!.add(batteryInfo.batteryCurrent)
        JayLogger.logInfo("RAW_EXPECTED_CURRENT_HASH_MAP", "", "${this.rawExpectedCurrentHashMap}")

        JayLogger.logInfo("ACTIVE_SENSORS", "", sensorStr)
        if (recording) this.recordingStartTime = currentTime
        return recordBuilder.build()
    }

    private fun setBatteryCallbacks() {
        this.batteryMonitor?.setCallbacks(
                levelChangeCallback = { level, voltage, temperature ->
                    this.batteryInfo.batteryLevel = level
                    this.batteryInfo.batteryVoltage = voltage
                    this.batteryInfo.batteryTemperature = temperature
                    this.batteryInfo.batteryCurrent = this.batteryMonitor?.getBatteryCurrentNow() ?: -1
                    this.batteryInfo.batteryEnergy = this.batteryMonitor?.getBatteryRemainingEnergy() ?: -1
                    this.batteryInfo.batteryCharge = this.batteryMonitor?.getBatteryCharge() ?: -1
                    JayLogger.logInfo("LEVEL_CHANGE_CB", actions = *arrayOf("NEW_BATTERY_LEVEL=$level", "NEW_BATTERY_VOLTAGE=$voltage", "NEW_BATTERY_TEMPERATURE=$temperature", "NEW_BATTERY_CURRENT=${this.batteryInfo.batteryCurrent}", "REMAINING_ENERGY=${this.batteryInfo.batteryEnergy}", "NEW_BATTERY_CHARGE=${this.batteryInfo.batteryCharge}"))
                },
                statusChangeCallback = { status ->
                    JayLogger.logInfo("STATUS_CHANGE_CB", actions = *arrayOf("NEW_BATTERY_STATUS=${status.name}"))
                    this.batteryInfo.batteryStatus = status
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
                Thread.sleep(recalculateAveragesInterval)
                do {
                    rawExpectedCpuHashMap.keys.forEach {
                        expectedCpuHashMap[it] = getExpectedCpuMhz(it)
                    }
                    rawExpectedCurrentHashMap.keys.forEach {
                        rawExpectedCurrentHashMap[it]?.keys?.forEach { cpu ->
                            if (!expectedCurrentHashMap.containsKey(it)) {
                                expectedCurrentHashMap[it] = LinkedHashMap()
                            }
                            expectedCurrentHashMap[it]!![cpu] = getMovingAvg(rawExpectedCurrentHashMap[it]!![cpu]!!)
                        }
                    }
                    Thread.sleep(recalculateAveragesInterval)
                } while (this.recording.get())
                recordingLatch.countDown()
                JayLogger.logInfo("START_PROFILE_AVERAGES_RECALCULATION", "", "END")
            }
        }
        return true
    }

    fun stopRecording(): ProfileRecordings {
        JayLogger.logInfo("STOP_RECORDING")
        this.recording.set(false)
        recordingLatch.await()  // Await recording termination
        JayLogger.logInfo("STOP_RECORDING", "", "END")
        return ProfileRecordings.newBuilder().addAllProfileRecording(this.recordings).build()
    }
}
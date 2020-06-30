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
import pt.up.fc.dcc.hyrax.jay.services.profiler.status.device.usage.UsageManager
import pt.up.fc.dcc.hyrax.jay.services.profiler.status.jay.JayStateManager
import pt.up.fc.dcc.hyrax.jay.utils.JaySettings
import pt.up.fc.dcc.hyrax.jay.utils.JayUtils
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.abs

/**
 * todo: basic energy profiling statistics should be done here
 * todo: store these pairs and average results
 *
 * fun getExpectedCpuMhz(jay_state): Set<Long>
 * {JAY_STATE} -> EXPECTED_CPU_MhZ
 * future improvements: {JAY_STATE} + DeviceUsageLoad (RAM, TOTAL_MEMORY, APPS/NUMBER_OF_APPS/CPU_LOAD): Set<Long>
 *
 * fun getExpectedCurrent(CPU_SPEEDS, Transport, Sensors): Long uA
 * EXPECTED_CPU_Mhz + Transport + ACTIVE_SENSORS -> EXPECTED_CURRENT
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
    private var recordingLatch = CountDownLatch(0)
    private val recordings = LinkedHashSet<ProfileRecording>()
    private val batteryInfo = BatteryInfo()


    private val expectedCpuRawHashMap = LinkedHashMap<Set<JayState>, CircularFifoQueue<Set<Long>>>()
    private val expectedCurrentHashMap = LinkedHashMap<Set<Long>, HashMap<TransportInfo, HashMap<Set<String>, CircularFifoQueue<Long>>>>()

    private fun setSimilarity(v1: Set<Long>, v2: Set<Long>): Long {
        var diff = 0L
        for (i in 0..v1.size) {
            diff += abs(v1.elementAt(i) - v2.elementAt(i))
        }
        return diff
    }

    private fun mostSimilarSet(v1: Set<Long>, v2: Set<Set<Long>>): Set<Long>? {
        var maxDiff = Long.MAX_VALUE
        var retSet: Set<Long>? = null
        v2.forEach {
            val diff = setSimilarity(v1, it)
            if (diff < maxDiff) {
                maxDiff = diff
                retSet = it
            }
        }
        return retSet
    }

    private fun getSetMovingAvg(v1: CircularFifoQueue<Set<Long>>?): Set<Long>? {
        if (v1 == null) return null
        val cpuCoreCounts: MutableList<Int> = ArrayList()
        val cpuCoreSums: MutableList<Long> = ArrayList()
        val retSet: MutableSet<Long> = LinkedHashSet()
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

    fun getExpectedCpuMhz(v1: Set<JayState>): Set<Long>? {
        return if ((expectedCpuRawHashMap.containsKey(v1))) getSetMovingAvg(expectedCpuRawHashMap[v1]) else null
    }

    fun getExpectedCurrent(v1: Set<Long>, v2: TransportInfo, v3: Set<String>): Long? {
        val cpuSet = mostSimilarSet(v1, expectedCurrentHashMap.keys) ?: return null
        val subSet = expectedCurrentHashMap[cpuSet]!!
        if (!subSet.containsKey(v2)) return null
        if (!subSet[v2]!!.containsKey(v3)) return null
        var sum = 0L
        subSet[v2]!![v3]!!.forEach {
            sum += it
        }
        return sum / subSet[v2]!![v3]!!.size
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

    private fun getJayStatesProto(): Set<JayState?> {
        val jayStates = LinkedHashSet<JayState?>()
        JayStateManager.getJayStates().states.forEach { state ->
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
        val batteryBuilder = Battery.newBuilder()
        batteryBuilder.batteryLevel = this.batteryInfo.batteryLevel
        batteryBuilder.batteryCurrent = this.batteryInfo.batteryCurrent
        batteryBuilder.batteryVoltage = this.batteryInfo.batteryVoltage
        batteryBuilder.batteryTemperature = this.batteryInfo.batteryTemperature
        batteryBuilder.batteryEnergy = this.batteryInfo.batteryEnergy
        batteryBuilder.batteryCharge = this.batteryInfo.batteryCharge
        batteryBuilder.batteryStatus = Battery.BatteryStatus.forNumber(this.batteryInfo.batteryStatus.number)
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

        recordBuilder.timeRange =
                if (recording) getTimeRangeProto(this.recordingStartTime, currentTime)
                else getTimeRangeProto(currentTime - msToRetrieve, currentTime)
        JayLogger.logInfo("TIME_RANGE", "", "START=${this.recordingStartTime}", "END=$currentTime")
        var states = ""
        getJayStatesProto().forEach { state ->
            recordBuilder.addJayState(state)
            states += "${state?.jayState?.name}, "
        }
        JayLogger.logInfo("JAY_STATES", "", states)
        recordBuilder.battery = getBatteryProto()
        recordBuilder.cpuCount = cpus.size
        var cpuSpeeds = ""
        cpus.forEach { cpu_number ->
            recordBuilder.addCpuFrequency(this.cpuManager?.getCurrentCPUClockSpeed(cpu_number) ?: 0)
            cpuSpeeds += "CPU_$cpu_number=${this.cpuManager?.getCurrentCPUClockSpeed(cpu_number) ?: 0}, "
        }
        JayLogger.logInfo("CPU", "", "CPU_COUNT=${cpus.size}, $cpuSpeeds")
        if (medium != null) recordBuilder.transport = getTransportProto(medium)
        val packageUsages = if (recording)
            this.usageManager?.getRecentUsageList(currentTime - this.recordingStartTime)
        else this.usageManager?.getRecentUsageList(msToRetrieve)
        var pkgs = ""
        packageUsages?.forEach { pkg ->
            recordBuilder.addSystemUsage(pkg.pkgName)
            pkgs += "$pkg, "
        }
        JayLogger.logInfo("PKG_USAGE", "", pkgs)
        var sensorStr = ""
        this.sensorManager?.getActiveSensors()?.forEach { sensor ->
            recordBuilder.addSensors(sensor)
            sensorStr += "$sensor, "
        }
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
            recordingLatch = CountDownLatch(1)
            this.recordings.clear()
            thread {
                do {
                    this.recordings.add(getSystemProfile(true))
                    Thread.sleep(recordInterval)
                } while (this.recording.get())
                recordingLatch.countDown()
                JayLogger.logInfo("START_RECORDING", "", "END")
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
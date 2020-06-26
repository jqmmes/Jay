package pt.up.fc.dcc.hyrax.jay.services.profiler

import pt.up.fc.dcc.hyrax.jay.grpc.GRPCServerBase
import pt.up.fc.dcc.hyrax.jay.logger.JayLogger
import pt.up.fc.dcc.hyrax.jay.proto.JayProto.*
import pt.up.fc.dcc.hyrax.jay.proto.JayProto.ServiceStatus.Type.PROFILER
import pt.up.fc.dcc.hyrax.jay.services.broker.grpc.BrokerGRPCClient
import pt.up.fc.dcc.hyrax.jay.services.profiler.grpc.ProfilerGRPCServer
import pt.up.fc.dcc.hyrax.jay.services.profiler.status.device.battery.BatteryInfo
import pt.up.fc.dcc.hyrax.jay.services.profiler.status.device.battery.BatteryMonitor
import pt.up.fc.dcc.hyrax.jay.services.profiler.status.device.cpu.CPUManager
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

/**
 * todo: basic energy profiling statistics should be done here
 */
object ProfilerService {
    private var usageManager: UsageManager? = null
    private var cpuManager: CPUManager? = null
    private var transportManager: TransportManager? = null
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

    fun start(useNettyServer: Boolean = false, batteryMonitor: BatteryMonitor? = null, transportManager:
    TransportManager? = null, cpuManager: CPUManager? = null, usageManager: UsageManager? = null) {
        JayLogger.logInfo("INIT")
        if (this.running) return
        this.batteryMonitor = batteryMonitor
        setBatteryCallbacks()
        this.batteryMonitor?.monitor()
        this.transportManager = transportManager
        this.cpuManager = cpuManager
        this.usageManager = usageManager
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
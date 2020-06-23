package pt.up.fc.dcc.hyrax.jay.services.profiler

import pt.up.fc.dcc.hyrax.jay.grpc.GRPCServerBase
import pt.up.fc.dcc.hyrax.jay.logger.JayLogger
import pt.up.fc.dcc.hyrax.jay.proto.JayProto
import pt.up.fc.dcc.hyrax.jay.proto.JayProto.ServiceStatus
import pt.up.fc.dcc.hyrax.jay.proto.JayProto.ServiceStatus.Type.PROFILER
import pt.up.fc.dcc.hyrax.jay.services.broker.grpc.BrokerGRPCClient
import pt.up.fc.dcc.hyrax.jay.services.profiler.grpc.ProfilerGRPCServer
import pt.up.fc.dcc.hyrax.jay.services.profiler.status.device.battery.BatteryInfo
import pt.up.fc.dcc.hyrax.jay.services.profiler.status.device.battery.BatteryMonitor
import pt.up.fc.dcc.hyrax.jay.services.profiler.status.device.cpu.CPUManager
import pt.up.fc.dcc.hyrax.jay.services.profiler.status.device.cpu.CPUStat
import pt.up.fc.dcc.hyrax.jay.services.profiler.status.device.transport.TransportInfo
import pt.up.fc.dcc.hyrax.jay.services.profiler.status.device.transport.TransportManager
import pt.up.fc.dcc.hyrax.jay.services.profiler.status.device.usage.PackageUsages
import pt.up.fc.dcc.hyrax.jay.services.profiler.status.device.usage.UsageManager
import pt.up.fc.dcc.hyrax.jay.services.profiler.status.jay.ActiveState
import pt.up.fc.dcc.hyrax.jay.services.profiler.status.jay.JayStateManager
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

object ProfilerService {
    private var usageManager: UsageManager? = null
    private var cpuManager: CPUManager? = null
    private var transportManager: TransportManager? = null
    private var batteryMonitor: BatteryMonitor? = null
    private var running: Boolean = false
    private val brokerGRPC = BrokerGRPCClient("127.0.0.1")
    private var server: GRPCServerBase? = null
    private var recordingStartTime: Long = 0
    private var recording: AtomicBoolean = AtomicBoolean(false)
    private val LOCK: Any = Object()
    private var recordInterval = 500L // time in ms

    fun start(useNettyServer: Boolean = false, batteryMonitor: BatteryMonitor? = null, transportManager:
    TransportManager? = null, cpuManager: CPUManager? = null, usageManager: UsageManager? = null) {
        JayLogger.logInfo("INIT")
        if (this.running) return
        this.batteryMonitor = batteryMonitor
        this.transportManager = transportManager
        this.cpuManager = cpuManager
        this.usageManager = usageManager
        this.server = ProfilerGRPCServer(useNettyServer).start()
        this.running = true
        this.brokerGRPC.announceServiceStatus(ServiceStatus.newBuilder().setType(PROFILER).setRunning(true).build())
        {
            JayLogger.logInfo("COMPLETE")
        }
    }

    fun stop(stopGRPCServer: Boolean = true) {
        this.running = false
        if (stopGRPCServer) this.server?.stop()
        this.brokerGRPC.announceServiceStatus(ServiceStatus.newBuilder().setType(PROFILER).setRunning(false).build())
        {
            JayLogger.logInfo("STOP")
        }
    }

    internal data class SystemProfileData(
            val time: Long,
            val cpu_count: Int,
            val cpu: CPUStat,
            val transport: TransportInfo?,
            val jayState: ActiveState,
            val battery: BatteryInfo,
            var usage: Set<PackageUsages>
    )

    internal fun getSystemProfile(): SystemProfileData {
        val currentTime = System.currentTimeMillis()
        val cpus = this.cpuManager?.getCpus()
        this.transportManager?.getTransport()
        val cpuClocks = LinkedHashSet<Int>()
        cpus?.forEach { cpu_number ->
            cpuClocks.add(this.cpuManager?.getCurrentCPUClockSpeed(cpu_number) ?: 0)
        }
        //val cpuStat = JayStateManager.getJayStates()

        // Todo: BatteryStats
        // Todo: Usage
        return SystemProfileData(currentTime,
                cpus?.size ?: -1,
                CPUStat(currentTime, cpuClocks),
                this.transportManager?.getTransport(),
                JayStateManager.getJayStates(),
                BatteryInfo(0, 0, 0, 0, 0, 0, JayProto.Worker.BatteryStatus.CHARGING),
                emptySet()
        )
    }

    internal fun startRecording(): Boolean {
        synchronized(LOCK) {
            if (this.recording.get()) return false
            this.recording.set(true)
            recordingStartTime = System.currentTimeMillis()
            thread {
                do {
                    getSystemProfile()

                    Thread.sleep(recordInterval)
                } while (this.recording.get())
            }.start()
        }
        return true
    }

    internal fun stopRecording() {
        // Todo: UsageManager
    }

}
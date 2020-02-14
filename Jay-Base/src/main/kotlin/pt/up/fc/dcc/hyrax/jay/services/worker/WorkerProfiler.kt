package pt.up.fc.dcc.hyrax.jay.services.worker

import org.apache.commons.collections4.queue.CircularFifoQueue
import pt.up.fc.dcc.hyrax.jay.logger.JayLogger
import pt.up.fc.dcc.hyrax.jay.protoc.JayProto
import pt.up.fc.dcc.hyrax.jay.services.broker.grpc.BrokerGRPCClient
import pt.up.fc.dcc.hyrax.jay.services.worker.interfaces.BatteryMonitor
import pt.up.fc.dcc.hyrax.jay.utils.JaySettings
import java.lang.Thread.sleep
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

@Suppress("unused")
internal object WorkerProfiler {
    private val JOBS_LOCK = Object()
    private val builder = JayProto.Worker.newBuilder()
    private var brokerGRPC = BrokerGRPCClient("127.0.0.1")
    private var updaterRunning = false

    private val averageComputationTimes = CircularFifoQueue<Long>(JaySettings.AVERAGE_COMPUTATION_TIME_TO_SCORE)
    private val cpuCores: Int = Runtime.getRuntime().availableProcessors()
    private val totalMemory: Long = Runtime.getRuntime().totalMemory()
    private var freeMemory: Long = Runtime.getRuntime().freeMemory()

    var runningJobs: AtomicInteger = AtomicInteger(0)
    var totalJobs: AtomicInteger = AtomicInteger(0)
    private var queueSize: Int = Int.MAX_VALUE

    private var batteryLevel: Int = 100
    private var batteryCurrent: Int = -1
    private var batteryVoltage: Int = -1
    private var batteryTemperature: Float = -1f
    private var batteryEnergy: Long = -1
    private var batteryCharge: Int = -1
    private var batteryStatus: JayProto.Worker.BatteryStatus = JayProto.Worker.BatteryStatus.CHARGED
    private var batteryMonitor: BatteryMonitor? = null


    internal fun start() {
        JayLogger.logInfo("START")
        //brokerGRPC.announceMulticast()
        periodicStatusUpdate()
    }

    internal fun destroy() {
        JayLogger.logInfo("DESTROY")
        updaterRunning = false
        runningJobs.set(0)
        totalJobs.set(0)
        batteryMonitor?.destroy()
    }

    private fun periodicStatusUpdate() {
        if (updaterRunning) return
        thread {
            updaterRunning = true
            do {
                statusNotify()
                sleep(JaySettings.WORKER_STATUS_UPDATE_INTERVAL)
            } while (updaterRunning)
        }
    }

    internal fun checkBatteryEnergy(): Long {
        this.batteryEnergy = batteryMonitor?.getBatteryRemainingEnergy() ?: -1
        return this.batteryEnergy
    }

    internal fun checkBatteryCurrent(): Int {
        this.batteryCurrent = batteryMonitor?.getBatteryCurrentNow() ?: -1
        return this.batteryCurrent
    }

    internal fun checkBatteryCharge(): Int {
        this.batteryCharge = batteryMonitor?.getBatteryCharge() ?: -1
        return this.batteryCharge
    }

    internal fun profileExecution(code: (() -> Unit)) {
        JayLogger.logInfo("START")
        val computationStartTimestamp = System.currentTimeMillis()
        code.invoke()
        val totalTime = System.currentTimeMillis() - computationStartTimestamp
        averageComputationTimes.add(totalTime)
        JayLogger.logInfo("END", actions = *arrayOf("COMPUTATION_TIME=$totalTime", "NEW_AVERAGE_COMPUTATION_TIME=${(averageComputationTimes.sum() / averageComputationTimes.size)}"))
    }

    internal fun atomicOperation(vararg values: AtomicInteger, increment: Boolean = false) {
        synchronized(JOBS_LOCK) {
            for (value in values)
                if (increment) value.incrementAndGet() else value.decrementAndGet()
        }
    }

    internal fun setBatteryMonitor(monitor: BatteryMonitor?) {
        this.batteryMonitor = monitor
    }


    internal fun monitorBattery() {
        batteryMonitor?.setCallbacks(
                levelChangeCallback = { level, voltage, temperature ->
                    this.batteryLevel = level
                    this.batteryVoltage = voltage
                    this.batteryTemperature = temperature
                    this.batteryCurrent = batteryMonitor?.getBatteryCurrentNow() ?: -1
                    this.batteryEnergy = batteryMonitor?.getBatteryRemainingEnergy() ?: -1
                    this.batteryCharge = batteryMonitor?.getBatteryCharge() ?: -1
                    JayLogger.logInfo("LEVEL_CHANGE_CB", actions = *arrayOf("NEW_BATTERY_LEVEL=$level", "NEW_BATTERY_VOLTAGE=$voltage", "NEW_BATTERY_TEMPERATURE=$temperature", "NEW_BATTERY_CURRENT=$batteryCurrent", "REMAINING_ENERGY=$batteryEnergy", "NEW_BATTERY_CHARGE=$batteryCharge"))
                },
                statusChangeCallback = { status ->
                    JayLogger.logInfo("STATUS_CHANGE_CB", actions = *arrayOf("NEW_BATTERY_STATUS=${status.name}"))
                    this.batteryStatus = status
                })
        batteryMonitor?.monitor()
    }

    private fun statusNotify() {
        freeMemory = Runtime.getRuntime().freeMemory()
        builder.clear()
        builder.cpuCores = cpuCores
        builder.totalMemory = totalMemory
        builder.freeMemory = freeMemory
        builder.queueSize = queueSize
        builder.queuedJobs = totalJobs.get() - runningJobs.get()
        builder.batteryLevel = batteryLevel
        builder.batteryCurrent = batteryCurrent
        builder.batteryVoltage = batteryVoltage
        builder.batteryTemperature = batteryTemperature
        builder.batteryEnergy = batteryEnergy
        builder.batteryCharge = batteryCharge
        builder.batteryStatus = batteryStatus
        builder.runningJobs = runningJobs.get()
        builder.avgTimePerJob = if (averageComputationTimes.size > 0) averageComputationTimes.sum() / averageComputationTimes.size else 0
        brokerGRPC.diffuseWorkerStatus(builder.build())
    }
}
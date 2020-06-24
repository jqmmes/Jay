/*
package pt.up.fc.dcc.hyrax.jay.services.worker

import org.apache.commons.collections4.queue.CircularFifoQueue
import pt.up.fc.dcc.hyrax.jay.logger.JayLogger
import pt.up.fc.dcc.hyrax.jay.proto.JayProto
import pt.up.fc.dcc.hyrax.jay.services.broker.BrokerService
import pt.up.fc.dcc.hyrax.jay.services.broker.grpc.BrokerGRPCClient
import pt.up.fc.dcc.hyrax.jay.services.profiler.status.device.battery.BatteryMonitor
import pt.up.fc.dcc.hyrax.jay.services.profiler.status.jay.JayState
import pt.up.fc.dcc.hyrax.jay.utils.JaySettings
import pt.up.fc.dcc.hyrax.jay.utils.JayUtils
import java.lang.Thread.sleep
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

internal object WorkerProfiler {
    private val JOBS_LOCK = Object()
    //private var updaterRunning = false

    private val averageComputationTimes = CircularFifoQueue<Long>(JaySettings.AVERAGE_COMPUTATION_TIME_TO_SCORE)
    var runningJobs: AtomicInteger = AtomicInteger(0)
    var totalJobs: AtomicInteger = AtomicInteger(0)
    private var queueSize: Int = Int.MAX_VALUE




    */
/*private val cpuCores: Int = Runtime.getRuntime().availableProcessors()
    private val totalMemory: Long = Runtime.getRuntime().totalMemory()
    private var freeMemory: Long = Runtime.getRuntime().freeMemory()



    private var batteryLevel: Int = 100
    private var batteryCurrent: Int = -1
    private var batteryVoltage: Int = -1
    private var batteryTemperature: Float = -1f
    private var batteryEnergy: Long = -1
    private var batteryCharge: Int = -1
    private var batteryStatus: JayProto.Worker.BatteryStatus = JayProto.Worker.BatteryStatus.CHARGED*//*

    private var batteryMonitor: BatteryMonitor? = null


    internal fun start() {
        JayLogger.logInfo("START")
        //periodicStatusUpdate()
    }

    internal fun destroy() {
        JayLogger.logInfo("DESTROY")
        //updaterRunning = false
        runningJobs.set(0)
        totalJobs.set(0)
        //batteryMonitor?.destroy()
    }

    */
/*private fun periodicStatusUpdate() {
        if (updaterRunning) return
        thread {
            updaterRunning = true
            do {
                statusNotify()
                sleep(JaySettings.WORKER_STATUS_UPDATE_INTERVAL)
            } while (updaterRunning)
        }
    }*//*


    internal fun profileExecution(code: (() -> Unit)) {
        JayLogger.logInfo("START")
        val computationStartTimestamp = System.currentTimeMillis()
        WorkerService.profiler.setState(JayState.COMPUTE)
        code.invoke()
        WorkerService.profiler.unSetState(JayState.COMPUTE)
        val totalTime = System.currentTimeMillis() - computationStartTimestamp
        averageComputationTimes.add(totalTime)
        JayLogger.logInfo("END", actions = *arrayOf("COMPUTATION_TIME=$totalTime",
                "NEW_AVERAGE_COMPUTATION_TIME=${(averageComputationTimes.sum() / averageComputationTimes.size)}"))
    }

    internal fun atomicOperation(vararg values: AtomicInteger, increment: Boolean = false) {
        synchronized(JOBS_LOCK) {
            for (value in values)
                if (increment) value.incrementAndGet() else value.decrementAndGet()
        }
    }

    */
/*
    private fun checkBatteryEnergy(): Long {
        this.batteryEnergy = batteryMonitor?.getBatteryRemainingEnergy() ?: -1
        return this.batteryEnergy
    }

    private fun checkBatteryCurrent(): Int {
        this.batteryCurrent = batteryMonitor?.getBatteryCurrentNow() ?: -1
        return this.batteryCurrent
    }

    private fun checkBatteryCharge(): Int {
        this.batteryCharge = batteryMonitor?.getBatteryCharge() ?: -1
        return this.batteryCharge
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
                    this.batteryCurrent = checkBatteryCurrent()
                    this.batteryEnergy = checkBatteryEnergy()
                    this.batteryCharge = checkBatteryCharge()
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

        WorkerService.broker.diffuseWorkerStatus(JayUtils.genWorkerProto(batteryLevel = batteryLevel, batteryCurrent =
        batteryCurrent, batteryVoltage = batteryVoltage,
                batteryTemperature = batteryTemperature, batteryEnergy = batteryEnergy, batteryCharge = batteryCharge,
                avgComputingEstimate = if (averageComputationTimes.size > 0) averageComputationTimes.sum() / averageComputationTimes.size else 0,
                cpuCores = cpuCores, queueSize = queueSize, queuedJobs = totalJobs.get() - runningJobs.get(), runningJobs = runningJobs.get(),
                totalMemory = totalMemory, freeMemory = freeMemory))
    }*//*

}*/

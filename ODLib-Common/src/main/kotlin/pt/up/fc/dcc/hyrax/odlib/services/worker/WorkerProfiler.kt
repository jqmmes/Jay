package pt.up.fc.dcc.hyrax.odlib.services.worker

import org.apache.commons.collections4.queue.CircularFifoQueue
import pt.up.fc.dcc.hyrax.odlib.logger.ODLogger
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import pt.up.fc.dcc.hyrax.odlib.services.broker.grpc.BrokerGRPCClient
import pt.up.fc.dcc.hyrax.odlib.utils.ODSettings
import java.lang.Thread.sleep
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

internal object WorkerProfiler {
    private val JOBS_LOCK = Object()
    private val builder = ODProto.Worker.newBuilder()
    private var brokerGRPC = BrokerGRPCClient("127.0.0.1")
    private var updaterRunning = false

    private val averageComputationTimes = CircularFifoQueue<Long>(ODSettings.averageComputationTimesToStore)
    private val cpuCores : Int = Runtime.getRuntime().availableProcessors()
    private val totalMemory : Long = Runtime.getRuntime().totalMemory()
    private var freeMemory : Long = Runtime.getRuntime().freeMemory()

    var runningJobs : AtomicInteger = AtomicInteger(0)
    var totalJobs : AtomicInteger = AtomicInteger(0)
    private var queueSize : Int = Int.MAX_VALUE

    private var battery: Int = 100
    private var batteryStatus : ODProto.Worker.BatteryStatus = ODProto.Worker.BatteryStatus.CHARGED
    private var batteryMonitor: BatteryMonitor? = null


    internal fun start() {
        ODLogger.logInfo("START")
        brokerGRPC.announceMulticast()
        periodicStatusUpdate()
    }

    internal fun destroy() {
        ODLogger.logInfo("DESTROY")
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
                sleep(ODSettings.workerStatusUpdateInterval)
            } while (updaterRunning)
        }
    }

    internal fun profileExecution(code: (() -> Unit)) {
        ODLogger.logInfo("START")
        val computationStartTimestamp = System.currentTimeMillis()
        code.invoke()
        val totalTime = System.currentTimeMillis() - computationStartTimestamp
        averageComputationTimes.add(totalTime)
        ODLogger.logInfo("END",  actions = *arrayOf("COMPUTATION_TIME=$totalTime", "NEW_AVERAGE_COMPUTATION_TIME=${(averageComputationTimes.sum() / averageComputationTimes.size)}"))
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
                levelChangeCallback = { level ->
                    ODLogger.logInfo( "LEVEL_CHANGE_CB", actions = *arrayOf("NEW_BATTERY_LEVEL=$level"))
                    this.battery = level
                },
                statusChangeCallback = { status ->
                    ODLogger.logInfo("STATUS_CHANGE_CB",  actions = *arrayOf("NEW_BATTERY_STATUS=${status.name}"))
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
        builder.battery = battery
        builder.batteryStatus = batteryStatus
        builder.runningJobs = runningJobs.get()
        builder.avgTimePerJob = if (averageComputationTimes.size > 0) averageComputationTimes.sum()/averageComputationTimes.size else 0
        brokerGRPC.diffuseWorkerStatus(builder.build())
    }
}
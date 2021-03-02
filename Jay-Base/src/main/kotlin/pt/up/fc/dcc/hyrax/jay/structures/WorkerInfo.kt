package pt.up.fc.dcc.hyrax.jay.structures

import pt.up.fc.dcc.hyrax.jay.logger.JayLogger
import pt.up.fc.dcc.hyrax.jay.proto.JayProto
import pt.up.fc.dcc.hyrax.jay.utils.JayUtils
import java.util.*

data class WorkerInfo(val id: String = UUID.randomUUID().toString(),
                      val type: WorkerType, val address: String,
                      val grpcPort: Int
) {

    enum class Status {
        ONLINE,
        OFFLINE
    }

    internal var lastStatusUpdateTimestamp: Long = System.currentTimeMillis()
    internal var status: Status = Status.ONLINE
    private var avgComputingEstimate = 0L
    private var batteryLevel = 100
    private var batteryCapacity: Int = -1
    private var batteryStatus: PowerStatus = PowerStatus.UNKNOWN
    private var cpuCores = 0
    private var queueSize = 1
    internal var queuedTasks = 0
    private var waitingToReceiveTasks = 0
    private var runningTasks = 0
    private var totalMemory = 0L
    private var freeMemory = 0L // Getter done
    internal var bandwidthEstimate: Float = 0f // Getter done
    internal var avgResultSize = 0L // Getter done
    private var powerEstimations: PowerEstimations? = null
    private var gRPCPortChangedCb: ((Int) -> Unit)? = null

    fun getStatus(): Status { return status }
    fun getBandwidthEstimate(): Float { return bandwidthEstimate }
    fun getAverageResultSize(): Long { return avgResultSize }
    fun getFreeMemory(): Long { return freeMemory }
    fun getTotalMemory(): Long { return totalMemory }
    fun getRunningTasks(): Int { return runningTasks }
    fun getWaitingToReceiveTasks(): Int { return waitingToReceiveTasks }
    fun getQueuedTasks(): Int { return queuedTasks }
    fun getQueueSize(): Int { return queueSize }

    fun getCpuCores(): Int { return cpuCores }
    fun getBatteryStatus(): PowerStatus { return batteryStatus }

    fun getBatteryCapacity(): Int { return batteryCapacity }
    fun getBatteryLevel(): Int { return batteryLevel }
    fun getAvgComputingTimeEstimate(): Long { return avgComputingEstimate }

    fun getPowerEstimations(): PowerEstimations? { return powerEstimations }


    private fun genProtoType(type: WorkerType): JayProto.WorkerInfo.Type {
        return when (type) {
            WorkerType.LOCAL -> JayProto.WorkerInfo.Type.LOCAL
            WorkerType.REMOTE -> JayProto.WorkerInfo.Type.REMOTE
            WorkerType.CLOUD -> JayProto.WorkerInfo.Type.CLOUD
        }
    }

    internal fun setGPRCPortChangedCb(cb: ((Int) -> Unit)) {
        gRPCPortChangedCb = cb
    }

    internal fun getProto() : JayProto.WorkerInfo {
        val workerInfo = JayProto.WorkerInfo.newBuilder()
        workerInfo.id = id // Internal
        workerInfo.batteryLevel = batteryLevel // Modified by Worker
        workerInfo.batteryCapacity = batteryCapacity
        workerInfo.batteryStatus = JayUtils.powerStatusToProto(batteryStatus)
        workerInfo.avgTimePerTask = avgComputingEstimate // Modified by Worker
        workerInfo.cpuCores = cpuCores // Set by Worker
        workerInfo.queueSize = queueSize // Set by Worker
        workerInfo.queuedTasks = queuedTasks
        workerInfo.waitingToReceiveTasks = waitingToReceiveTasks
        workerInfo.runningTasks = runningTasks // Modified by Worker
        workerInfo.type = genProtoType(type) // Set in Broker
        workerInfo.bandwidthEstimate = bandwidthEstimate // Set internally
        workerInfo.totalMemory = totalMemory
        workerInfo.freeMemory = freeMemory
        workerInfo.avgResultSize = avgResultSize
        workerInfo.brokerPort = grpcPort
        if (powerEstimations != null) workerInfo.powerEstimations = powerEstimations!!.getProto()
        return workerInfo.build()
    }

    internal fun update(proto: JayProto.WorkerInfo?) {
        JayLogger.logInfo("INIT", actions = arrayOf("WORKER_ID=$id", "WORKER_TYPE=${type.name}"))
        if (proto == null) return
        batteryLevel = proto.batteryLevel
        batteryCapacity = proto.batteryCapacity
        batteryStatus = JayUtils.powerStatusFromProto(proto.batteryStatus)
        avgComputingEstimate = proto.avgTimePerTask
        runningTasks = proto.runningTasks
        cpuCores = proto.cpuCores
        queueSize = proto.queueSize
        queuedTasks = proto.queuedTasks
        waitingToReceiveTasks = proto.waitingToReceiveTasks
        totalMemory = proto.totalMemory
        freeMemory = proto.freeMemory
        powerEstimations = PowerEstimations(proto.powerEstimations)
        if (proto.brokerPort != grpcPort)
            gRPCPortChangedCb?.invoke(proto.brokerPort)
        lastStatusUpdateTimestamp = System.currentTimeMillis()
    }

    internal fun update(expectedPower: JayProto.PowerEstimations?) {
        if (expectedPower != null) powerEstimations = PowerEstimations(expectedPower)
    }

    internal fun update(computeProto: JayProto.WorkerComputeStatus?, profileProto: JayProto.ProfileRecording?) {
        JayLogger.logInfo("INIT", actions = arrayOf("WORKER_ID=$id", "WORKER_TYPE=${type.name}"))
        if (computeProto == null && profileProto == null) return
        runningTasks = computeProto!!.runningTasks
        avgComputingEstimate = computeProto.avgTimePerTask
        queueSize = computeProto.queueSize
        queuedTasks = computeProto.queuedTasks
        waitingToReceiveTasks = computeProto.waitingToReceiveTasks
        this.lastStatusUpdateTimestamp = System.currentTimeMillis()
    }
    override fun equals(other: Any?): Boolean {

        return super.equals(other)
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + address.hashCode()
        result = 31 * result + grpcPort.hashCode()
        result = 31 * result + status.hashCode()
        result = 31 * result + avgComputingEstimate.hashCode()
        result = 31 * result + batteryLevel
        result = 31 * result + batteryCapacity
        result = 31 * result + batteryStatus.hashCode()
        result = 31 * result + cpuCores
        result = 31 * result + queueSize
        result = 31 * result + queuedTasks
        result = 31 * result + waitingToReceiveTasks
        result = 31 * result + runningTasks
        result = 31 * result + totalMemory.hashCode()
        result = 31 * result + freeMemory.hashCode()
        result = 31 * result + bandwidthEstimate.hashCode()
        result = 31 * result + avgResultSize.hashCode()
        result = 31 * result + (powerEstimations?.hashCode() ?: 0)
        return result
    }
}

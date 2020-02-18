package pt.up.fc.dcc.hyrax.jay.services.broker

import pt.up.fc.dcc.hyrax.jay.grpc.GRPCServerBase
import pt.up.fc.dcc.hyrax.jay.interfaces.FileSystemAssistant
import pt.up.fc.dcc.hyrax.jay.interfaces.VideoUtils
import pt.up.fc.dcc.hyrax.jay.logger.JayLogger
import pt.up.fc.dcc.hyrax.jay.protoc.JayProto
import pt.up.fc.dcc.hyrax.jay.protoc.JayProto.*
import pt.up.fc.dcc.hyrax.jay.protoc.JayProto.Worker.Type
import pt.up.fc.dcc.hyrax.jay.services.broker.grpc.BrokerGRPCServer
import pt.up.fc.dcc.hyrax.jay.services.broker.multicast.MulticastAdvertiser
import pt.up.fc.dcc.hyrax.jay.services.broker.multicast.MulticastListener
import pt.up.fc.dcc.hyrax.jay.services.scheduler.grpc.SchedulerGRPCClient
import pt.up.fc.dcc.hyrax.jay.services.worker.grpc.WorkerGRPCClient
import pt.up.fc.dcc.hyrax.jay.services.worker.interfaces.BatteryMonitor
import pt.up.fc.dcc.hyrax.jay.structures.Worker
import pt.up.fc.dcc.hyrax.jay.utils.JaySettings
import pt.up.fc.dcc.hyrax.jay.utils.JayUtils
import java.lang.Thread.sleep
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import pt.up.fc.dcc.hyrax.jay.protoc.JayProto.Worker as ODWorker

object BrokerService {

    internal var batteryMonitor: BatteryMonitor? = null
    private var server: GRPCServerBase? = null
    private val workers: MutableMap<String, Worker> = hashMapOf()
    private val assignedJobs: MutableMap<String, String> = hashMapOf()
    private val scheduler = SchedulerGRPCClient("127.0.0.1")
    private val worker = WorkerGRPCClient("127.0.0.1")
    private val local: Worker = if (JaySettings.DEVICE_ID != "") Worker(id = JaySettings.DEVICE_ID, address = "127.0.0.1", type = Type.LOCAL) else Worker(address = "127.0.0.1", type = Type.LOCAL)
    private var heartBeats = false
    private var bwEstimates = false
    private var schedulerServiceRunning = false
    private var workerServiceRunning = false
    private var fsAssistant: FileSystemAssistant? = null
    private var videoUtils: VideoUtils? = null

    init {
        workers[local.id] = local
    }

    fun start(useNettyServer: Boolean = false, fsAssistant: FileSystemAssistant? = null, videoUtils: VideoUtils? = null, batteryMonitor: BatteryMonitor? = null) {
        this.fsAssistant = fsAssistant
        this.videoUtils = videoUtils
        this.batteryMonitor = batteryMonitor
        server = BrokerGRPCServer(useNettyServer).start()
        worker.testService { ServiceStatus -> workerServiceRunning = ServiceStatus?.running ?: false }
        scheduler.testService { ServiceStatus ->
            schedulerServiceRunning = ServiceStatus?.running ?: false
            if (schedulerServiceRunning) updateWorkers()
        }
    }

    fun stop(stopGRPCServer: Boolean = true) {
        if (stopGRPCServer) server?.stop()
        workers.values.filter { w -> w.type == Type.CLOUD }.forEach { w -> w.disableAutoStatusUpdate() }
    }

    fun stopService(callback: ((Status?) -> Unit)) {
        stop(false)
        callback(JayUtils.genStatusSuccess())
    }

    fun stopServer() {
        server?.stopNowAndWait()
    }

    internal fun extractVideoFrames(id: String?) {
        videoUtils?.extractFrames("${fsAssistant?.getAbsolutePath()}/$id", 1, (fsAssistant?.getAbsolutePath() ?: ""), (id?.dropLast(4) ?: "thumb"))
    }

    internal fun getByteArrayFromId(id: String?) : ByteArray? {
        JayLogger.logInfo("READING_IMAGE_BYTE_ARRAY", actions = *arrayOf("IMAGE_ID=$id"))
        if (id != null)
            return fsAssistant?.getByteArrayFast(id)
        return null
    }

    internal fun executeJob(request: Job?, callback: ((Results?) -> Unit)? = null) {
        val jobId = request?.id ?: ""
        JayLogger.logInfo("INIT", jobId)
        val workerJob = WorkerJob.newBuilder().setId(jobId).setFileId(fsAssistant?.createTempFile(request?.data?.toByteArray())).build()
        if (workerServiceRunning) worker.execute(workerJob, callback) else callback?.invoke(Results
                .getDefaultInstance())
        JayLogger.logInfo("COMPLETE", jobId)
    }

    internal fun scheduleJob(request: Job?, callback: ((Results?) -> Unit)? = null) {
        val jobId = request?.id ?: ""
        JayLogger.logInfo("INIT", jobId)
        val jobDetails = JayUtils.getJobDetails(request)
        if (schedulerServiceRunning) scheduler.schedule(jobDetails) { W ->
            if (request != null) assignedJobs[request.id] = W?.id ?: ""
            JayLogger.logInfo("SCHEDULED", jobId, "WORKER_ID=${W?.id}")
            if (W?.id == "" || W == null) {
                callback?.invoke(null)
            } else {
                if (JaySettings.SINGLE_REMOTE_IP == "0.0.0.0" || (W.type != Type.REMOTE || JaySettings.SINGLE_REMOTE_IP == workers[W.id]!!.address)) {
                    workers[W.id]!!.grpc.executeJob(request, callback) { scheduler.notifyJobComplete(jobDetails) }
                } else {
                    workers[local.id]!!.grpc.executeJob(request, callback) { scheduler.notifyJobComplete(jobDetails) }
                }
            }
        } else {
            callback?.invoke(Results.getDefaultInstance())
        }
        JayLogger.logInfo("COMPLETE", jobId)
    }

    internal fun passiveBandwidthUpdate(jobId: String, dataSize: Int, duration: Long) {
        if (jobId in assignedJobs && assignedJobs[jobId] != "") {
            workers[assignedJobs[jobId]]?.addRTT(duration.toInt(), dataSize)
        }
    }

    private fun updateWorker(worker: JayProto.Worker?, latch: CountDownLatch) {
        scheduler.notifyWorkerUpdate(worker) { S ->
            if (S?.code == StatusCode.Error) {
                sleep(50)
                updateWorker(worker, latch)
            } else (latch.countDown())
        }
    }

    internal fun updateWorkers() {
        val countDownLatch = CountDownLatch(workers.size)
        for (worker in workers.values) {
            if (worker.isOnline() || (worker.type == Type.LOCAL && workerServiceRunning)) updateWorker(worker.getProto(), countDownLatch)
        }
        countDownLatch.await()
    }

    internal fun getModels(callback: (Models) -> Unit) {
        if (workerServiceRunning) worker.listModels(callback) else callback(Models.getDefaultInstance())
    }

    internal fun setModel(request: Model?, callback: ((Status?) -> Unit)? = null) {
        if (workerServiceRunning) worker.selectModel(request, callback) else callback?.invoke(JayUtils.genStatusError())
    }

    internal fun diffuseWorkerStatus() : CountDownLatch {
        val countDownLatch = CountDownLatch(1)
        val atomicLock = AtomicInteger(0)
        var remoteWorkers = 0
        for (client in workers.values) {
            if (client.type == Type.REMOTE) {
                remoteWorkers++
                atomicLock.incrementAndGet()
                client.grpc.advertiseWorkerStatus(local.getProto()) { if (atomicLock.decrementAndGet() == 0) countDownLatch.countDown() }
            }
        }
        if (remoteWorkers == 0) countDownLatch.countDown()
        return countDownLatch
    }

    internal fun updateWorker(request: ODWorker?): CountDownLatch {//, updateCloud: Boolean = false) : CountDownLatch {
        JayLogger.logInfo("INIT", actions = *arrayOf("WORKER_ID=${request?.id}"))//, "UPDATE_CLOUD=$updateCloud"))
        val countDownLatch = CountDownLatch(1)
        JayLogger.logInfo("UPDATE_STATUS", actions = *arrayOf("WORKER_ID=${request?.id}"))//, "UPDATE_CLOUD=$updateCloud"))
        announceMulticast(worker = local.updateStatus(request))
        if (schedulerServiceRunning) scheduler.notifyWorkerUpdate(local.getProto()) { countDownLatch.countDown() }
        else countDownLatch.countDown()
        return countDownLatch

    }

    internal fun receiveWorkerStatus(request: JayProto.Worker?, completeCallback: (Status?) -> Unit) {
        JayLogger.logInfo("INIT", actions = *arrayOf("WORKER_ID=${request?.id}"))
        workers[request?.id]?.updateStatus(request)
        if (schedulerServiceRunning && request?.id != null && request.id in workers) {
            JayLogger.logInfo("COMPLETE", actions = *arrayOf("WORKER_ID=${request.id}"))
            scheduler.notifyWorkerUpdate(request, completeCallback)
        } else {
            completeCallback.invoke(JayUtils.genStatusError())
            if (!schedulerServiceRunning)
                JayLogger.logInfo("SCHEDULER_NOT_RUNNING", actions = *arrayOf("WORKER_ID=${request?.id}"))
            else if (request?.id != null && request.id in workers)
                JayLogger.logInfo("INVALID_REQUEST")
            else
                JayLogger.logInfo("UNKNOWN_ERROR", actions = *arrayOf("WORKER_ID=${request?.id}"))
        }
    }

    fun getSchedulers(callback: ((Schedulers?) -> Unit)? = null) {
        JayLogger.logInfo("INIT")
        if(schedulerServiceRunning) {
            JayLogger.logInfo("COMPLETE")
            scheduler.listSchedulers(callback)
        } else {
            JayLogger.logWarn("SCHEDULER_NOT_RUNNING")
            callback?.invoke(Schedulers.getDefaultInstance())
        }

    }

    fun setScheduler(request: Scheduler?, callback: ((Status?) -> Unit)? = null) {
        if (schedulerServiceRunning) scheduler.setScheduler(request, callback)
        else callback?.invoke(JayUtils.genStatusError())
    }

    internal fun listenMulticast(stopListener: Boolean = false) {
        if (stopListener) MulticastListener.stop()
        else MulticastListener.listen(callback = { W, A -> addOrUpdateWorker(W, A) })
    }

    private fun statusUpdate(statusUpdate: Worker.Status, workerId: String) {
        JayLogger.logInfo("STATUS_UPDATE", actions = *arrayOf("DEVICE_IP=${workers[workerId]?.address}", "DEVICE_ID=${workerId}", "WORKER_TYPE=${workers[workerId]?.type?.name}"))
        if (!schedulerServiceRunning) {
            JayLogger.logInfo("SCHEDULER_NOT_RUNNING", actions = *arrayOf("DEVICE_IP=${workers[workerId]?.address}", "DEVICE_ID=${workerId}", "WORKER_TYPE=${workers[workerId]?.type?.name}"))
            return
        }
        JayLogger.logInfo("SCHEDULER_RUNNING", actions = *arrayOf("DEVICE_IP=${workers[workerId]?.address}", "DEVICE_ID=${workerId}", "WORKER_TYPE=${workers[workerId]?.type?.name}"))
        if (statusUpdate == Worker.Status.ONLINE) {
            JayLogger.logInfo("NOTIFY_WORKER_UPDATE", actions = *arrayOf("DEVICE_IP=${workers[workerId]?.address}", "DEVICE_ID=${workerId}", "WORKER_TYPE=${workers[workerId]?.type?.name}"))
            scheduler.notifyWorkerUpdate(workers[workerId]?.getProto()) {}
        } else {
            JayLogger.logInfo("NOTIFY_WORKER_FAILURE", actions = *arrayOf("DEVICE_IP=${workers[workerId]?.address}", "DEVICE_ID=${workerId}", "WORKER_TYPE=${workers[workerId]?.type?.name}"))
            scheduler.notifyWorkerFailure(workers[workerId]?.getProto()) {}
        }
    }

    private fun addOrUpdateWorker(worker: JayProto.Worker?, address: String) {
        JayLogger.logInfo("INIT", actions = *arrayOf("DEVICE_IP=$address", "DEVICE_ID=${worker?.id}"))
        if (worker == null) return
        if (worker.id !in workers) {
            if (address == JaySettings.SINGLE_REMOTE_IP) JaySettings.CLOUDLET_ID = worker.id
            JayLogger.logInfo("NEW_DEVICE", actions = *arrayOf("DEVICE_IP=$address", "DEVICE_ID=${worker.id}"))
            workers[worker.id] = Worker(worker, address, heartBeats, bwEstimates) { status -> statusUpdate(status, worker.id) }
        } else {
            JayLogger.logInfo("NOTIFY_WORKER_UPDATE", actions = *arrayOf("DEVICE_IP=$address", "DEVICE_ID=${worker.id}"))
            workers[worker.id]?.updateStatus(worker)
        }
        statusUpdate(Worker.Status.ONLINE, worker.id)
    }

    internal fun announceMulticast(stopAdvertiser: Boolean = false, worker: ODWorker? = null) {
        if (stopAdvertiser) MulticastAdvertiser.stop()
        else {
            val data = worker?.toByteArray() ?: local.getProto()?.toByteArray()
            if (MulticastAdvertiser.isRunning()) MulticastAdvertiser.setAdvertiseData(data)
            else MulticastAdvertiser.start(data)
        }
    }

    fun requestWorkerStatus(): JayProto.Worker? {
        return local.getProto()
    }

    fun enableHearBeats(types: WorkerTypes?): Status? {
        if (heartBeats) return JayUtils.genStatusSuccess()
        heartBeats = true
        if (types == null) return JayUtils.genStatus(StatusCode.Error)
        for (worker in workers.values)
            if (worker.type in types.typeList && worker.type != Type.LOCAL)
                worker.enableHeartBeat { status -> statusUpdate(status, worker.id) }
        return JayUtils.genStatus(StatusCode.Success)
    }

    fun enableBandwidthEstimates(method: BandwidthEstimate?): Status? {
        if (bwEstimates) return JayUtils.genStatusSuccess()
        bwEstimates = true
        if (method == null) return JayUtils.genStatus(StatusCode.Error)
        for (worker in workers.values) {
            if (worker.type in method.workerTypeList && worker.type != Type.LOCAL) {
                when (JaySettings.BANDWIDTH_ESTIMATE_TYPE) {
                    in arrayOf("ACTIVE", "ALL") -> worker.doActiveRTTEstimates { status -> statusUpdate(status, worker.id) }
                    else -> worker.enableHeartBeat { status -> statusUpdate(status, worker.id) }
                }
            }
        }
        return JayUtils.genStatus(StatusCode.Success)
    }

    fun disableHearBeats(): Status? {
        if (!heartBeats) return JayUtils.genStatusSuccess()
        heartBeats = false
        for (worker in workers.values) worker.disableHeartBeat()
        return JayUtils.genStatusSuccess()
    }

    fun disableBandwidthEstimates(): Status? {
        if (!bwEstimates) return JayUtils.genStatusSuccess()
        bwEstimates = false
        for (worker in workers.values) worker.stopActiveRTTEstimates()
        return JayUtils.genStatusSuccess()
    }

    fun updateSmartSchedulerWeights(weights: Weights?, callback: ((Status?) -> Unit)) {
        if (schedulerServiceRunning) scheduler.updateSmartSchedulerWeights(weights) { S -> callback(S) }
        else callback(JayUtils.genStatusError())
    }

    fun serviceStatusUpdate(serviceStatus: ServiceStatus?, completeCallback: (Status?) -> Unit) {
        if (serviceStatus?.type == ServiceStatus.Type.SCHEDULER) {
            schedulerServiceRunning = serviceStatus.running
            if (!schedulerServiceRunning) {
                disableHearBeats()
                disableBandwidthEstimates()
            }
            completeCallback(JayUtils.genStatusSuccess())
        } else if (serviceStatus?.type == ServiceStatus.Type.WORKER) {
            workerServiceRunning = serviceStatus.running
            if (!workerServiceRunning) announceMulticast(stopAdvertiser = true)
            if (schedulerServiceRunning) {
                if (serviceStatus.running) scheduler.notifyWorkerUpdate(local.getProto()) { S -> completeCallback(S) }
                else scheduler.notifyWorkerFailure(local.getProto()) { S -> completeCallback(S) }
            } else completeCallback(JayUtils.genStatusSuccess())
        }
    }

    fun calibrateWorker(job: JayProto.String?, function: () -> Unit) {
        JayLogger.logInfo("INIT", "CALIBRATION")
        val workerJob = WorkerJob.newBuilder().setId("CALIBRATION").setFileId(fsAssistant?.createTempFile(fsAssistant?.getByteArrayFast(job?.str
                ?: "") ?: ByteArray(0))).build()
        if (workerServiceRunning) worker.execute(workerJob) { function() } else function()
        JayLogger.logInfo("COMPLETE", "CALIBRATION")
    }

    fun addCloud(cloud_ip: String) {
        JayLogger.logInfo("INIT", "", "CLOUD_IP=$cloud_ip")
        if (workers.values.find { w -> w.type == Type.CLOUD && w.address == cloud_ip } == null) {
            JayLogger.logInfo("NEW_CLOUD", "", "CLOUD_IP=$cloud_ip")
            val cloud = Worker(address = cloud_ip, type = Type.CLOUD, checkHearBeat = true, bwEstimates = bwEstimates)
            cloud.enableAutoStatusUpdate { workerProto ->
                if (!schedulerServiceRunning) {
                    JayLogger.logInfo("SCHEDULER_NOT_RUNNING", actions = *arrayOf("DEVICE_IP=$cloud_ip", "DEVICE_ID=${cloud.id}", "WORKER_TYPE=${cloud.type.name}"))
                    return@enableAutoStatusUpdate
                }
                scheduler.notifyWorkerUpdate(workerProto) { status ->
                    JayLogger.logInfo("CLOUD_TO_SCHEDULER_WORKER_UPDATE_COMPLETE", "",
                            "STATUS=${status?.code?.name}", "CLOUD_ID=$cloud.id", "CLOUD_IP=$cloud_ip")
                }
            }
            workers[cloud.id] = cloud
        }
        JayLogger.logInfo("COMPLETE", "", "CLOUD_IP=$cloud_ip")
    }
}

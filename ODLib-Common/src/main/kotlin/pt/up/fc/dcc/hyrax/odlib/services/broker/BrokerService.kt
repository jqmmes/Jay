package pt.up.fc.dcc.hyrax.odlib.services.broker

import pt.up.fc.dcc.hyrax.odlib.grpc.GRPCServerBase
import pt.up.fc.dcc.hyrax.odlib.interfaces.FileSystemAssistant
import pt.up.fc.dcc.hyrax.odlib.interfaces.VideoUtils
import pt.up.fc.dcc.hyrax.odlib.logger.ODLogger
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto.*
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto.Worker.Type
import pt.up.fc.dcc.hyrax.odlib.services.broker.grpc.BrokerGRPCServer
import pt.up.fc.dcc.hyrax.odlib.services.broker.multicast.MulticastAdvertiser
import pt.up.fc.dcc.hyrax.odlib.services.broker.multicast.MulticastListener
import pt.up.fc.dcc.hyrax.odlib.services.scheduler.grpc.SchedulerGRPCClient
import pt.up.fc.dcc.hyrax.odlib.services.worker.grpc.WorkerGRPCClient
import pt.up.fc.dcc.hyrax.odlib.structures.Worker
import pt.up.fc.dcc.hyrax.odlib.utils.ODSettings
import pt.up.fc.dcc.hyrax.odlib.utils.ODUtils
import java.lang.Thread.sleep
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto.Worker as ODWorker

object BrokerService {

    private var server: GRPCServerBase? = null
    private val workers: MutableMap<String, Worker> = hashMapOf()
    private val assignedJobs: MutableMap<String, String> = hashMapOf()
    private val scheduler = SchedulerGRPCClient("127.0.0.1")
    private val worker = WorkerGRPCClient("127.0.0.1")
    private val local: Worker = if (ODSettings.DEVICE_ID != "") Worker(id = ODSettings.DEVICE_ID, address = "127.0.0.1", type = Type.LOCAL) else Worker(address = "127.0.0.1", type = Type.LOCAL)
    private val cloud = Worker(address = ODSettings.CLOUD_IP, type = Type.CLOUD)
    private var heartBeats = false
    private var bwEstimates = false
    private var schedulerServiceRunning = false
    private var workerServiceRunning = false
    private var fsAssistant: FileSystemAssistant? = null
    private var videoUtils: VideoUtils? = null

    init {
        workers[local.id] = local
        workers[cloud.id] = cloud
        cloud.enableAutoStatusUpdate {workerInfo ->
            if(schedulerServiceRunning) scheduler.notifyWorkerUpdate(workerInfo) {}
        }
    }

    fun start(useNettyServer: Boolean = false, fsAssistant: FileSystemAssistant? = null, videoUtils: VideoUtils? = null) {
        this.fsAssistant = fsAssistant
        this.videoUtils = videoUtils
        server = BrokerGRPCServer(useNettyServer).start()
        worker.testService { ServiceStatus -> workerServiceRunning = ServiceStatus?.running ?: false}
        scheduler.testService {
            ServiceStatus -> schedulerServiceRunning = ServiceStatus?.running ?: false
            if (schedulerServiceRunning) updateWorkers()
        }
    }

    fun stop(stopGRPCServer: Boolean = true) {
        if (stopGRPCServer) server?.stop()
        cloud.disableAutoStatusUpdate()
    }

    fun stopService(callback: ((Status?) -> Unit)) {
        stop(false)
        callback(ODUtils.genStatusSuccess())
    }

    fun stopServer() {
        server?.stopNowAndWait()
    }

    internal fun extractVideoFrames(id: String?) {
        videoUtils?.extractFrames("${fsAssistant?.getAbsolutePath()}/$id", 1, (fsAssistant?.getAbsolutePath() ?: ""), (id?.dropLast(4) ?: "thumb"))
        //videoUtils?.extractFrameAt("${fsAssistant?.getAbsolutePath()}/$id",0,4,0,(fsAssistant?.getAbsolutePath() ?: ""), (id?.dropLast(4) ?: "thumb"))
    }

    internal fun getByteArrayFromId(id: String?) : ByteArray? {
        ODLogger.logInfo("READING_IMAGE_BYTE_ARRAY", actions = *arrayOf("IMAGE_ID=$id"))
        if (id != null)
            return fsAssistant?.getByteArrayFast(id)
        return null
    }

    internal fun executeJob(request: Job?, callback: ((Results?) -> Unit)? = null) {
        val jobId = request?.id ?: ""
        ODLogger.logInfo("INIT", jobId)
        val workerJob = WorkerJob.newBuilder().setId(jobId).setFileId(fsAssistant?.createTempFile(request?.data?.toByteArray())).build()
        if (workerServiceRunning) worker.execute(workerJob, callback) else callback?.invoke(Results
                .getDefaultInstance())
        ODLogger.logInfo("COMPLETE", jobId)
    }

    internal fun scheduleJob(request: Job?, callback: ((Results?) -> Unit)? = null) {
        val jobId = request?.id ?: ""
        ODLogger.logInfo("INIT", jobId)
        if (schedulerServiceRunning) scheduler.schedule(ODUtils.getJobDetails(request)) { W ->
            if (request != null) assignedJobs[request.id] = W?.id ?: ""
            ODLogger.logInfo("SCHEDULED", jobId, "WORKER_ID=${W?.id}")
            if (W?.id == "" || W == null) callback?.invoke(null) else workers[W.id]!!.grpc.executeJob(request, callback)
        } else {
            callback?.invoke(Results.getDefaultInstance())
        }
        ODLogger.logInfo("COMPLETE", jobId)
    }

    internal fun passiveBandwidthUpdate(jobId: String, dataSize: Int, duration: Long) {
        if (jobId in assignedJobs && assignedJobs[jobId] != "") {
            workers[assignedJobs[jobId]]?.addRTT(duration.toInt(), dataSize)
        }
    }

    private fun updateWorker(worker: ODProto.Worker?, latch: CountDownLatch) {
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
        if (workerServiceRunning) worker.selectModel(request, callback) else callback?.invoke(ODUtils.genStatusError())
    }

    internal fun diffuseWorkerStatus() : CountDownLatch {
        val countDownLatch = CountDownLatch(1)
        val atomicLock = AtomicInteger(0)
        for (client in workers.values) {
            if (client.type == Type.REMOTE) {
                atomicLock.incrementAndGet()
                client.grpc.advertiseWorkerStatus(local.getProto()) {if (atomicLock.decrementAndGet() == 0) countDownLatch.countDown()}
            }
        }
        return countDownLatch
    }

    internal fun updateWorker(request: ODWorker?, updateCloud: Boolean = false) : CountDownLatch {
        ODLogger.logInfo("INIT", actions = *arrayOf("WORKER_ID=${request?.id}", "UPDATE_CLOUD=$updateCloud"))
        val countDownLatch = CountDownLatch(1)
        if (updateCloud){
            ODLogger.logInfo("UPDATE_STATUS_CLOUD", actions = *arrayOf("WORKER_ID=${request?.id}", "UPDATE_CLOUD=$updateCloud"))
            cloud.updateStatus(request)
            if(schedulerServiceRunning) scheduler.notifyWorkerUpdate(cloud.getProto()) {countDownLatch.countDown()}
            else countDownLatch.countDown()
        } else {
            ODLogger.logInfo("UPDATE_STATUS", actions=*arrayOf("WORKER_ID=${request?.id}", "UPDATE_CLOUD=$updateCloud"))
            announceMulticast(worker = local.updateStatus(request))
            if(schedulerServiceRunning) scheduler.notifyWorkerUpdate(local.getProto()) {countDownLatch.countDown()}
            else countDownLatch.countDown()
        }
        return countDownLatch

    }

    internal fun receiveWorkerStatus(request: ODProto.Worker?, completeCallback: (Status?) -> Unit) {
        ODLogger.logInfo("INIT", actions = *arrayOf("WORKER_ID=${request?.id}"))
        workers[request?.id]?.updateStatus(request)
        if(schedulerServiceRunning && request?.id != null && request.id in workers) {
            ODLogger.logInfo("COMPLETE", actions = *arrayOf("WORKER_ID=${request.id}"))
            scheduler.notifyWorkerUpdate(request, completeCallback)
        } else {
            completeCallback.invoke(ODUtils.genStatusError())
            if (!schedulerServiceRunning)
                ODLogger.logInfo("SCHEDULER_NOT_RUNNING", actions = *arrayOf("WORKER_ID=${request?.id}"))
            else if (request?.id != null && request.id in workers)
                ODLogger.logInfo("INVALID_REQUEST")
            else
                ODLogger.logInfo("UNKNOWN_ERROR", actions = *arrayOf("WORKER_ID=${request?.id}"))
        }
    }

    fun getSchedulers(callback: ((Schedulers?) -> Unit)? = null) {
        ODLogger.logInfo("INIT")
        if(schedulerServiceRunning) {
            ODLogger.logInfo("COMPLETE")
            scheduler.listSchedulers(callback)
        } else {
            ODLogger.logWarn("SCHEDULER_NOT_RUNNING")
            callback?.invoke(Schedulers.getDefaultInstance())
        }

    }

    fun setScheduler(request: Scheduler?, callback: ((Status?) -> Unit)? = null) {
        if(schedulerServiceRunning) scheduler.setScheduler(request, callback)
        else callback?.invoke(ODUtils.genStatusError())
    }

    internal fun listenMulticast(stopListener: Boolean = false) {
        if (stopListener) MulticastListener.stop()
        else MulticastListener.listen(callback = { W, A -> checkWorker(W, A) })
    }

    private fun checkWorker(worker: ODProto.Worker?, address: String) {
        ODLogger.logInfo("INIT", actions = *arrayOf("DEVICE_IP=$address", "DEVICE_ID=${worker?.id}"))
        if (worker == null) return
        if (worker.id !in workers){
            ODLogger.logInfo("NEW_DEVICE", actions = *arrayOf("DEVICE_IP=$address", "DEVICE_ID=${worker.id}"))
            workers[worker.id] = Worker(worker, address, heartBeats, bwEstimates) { StatusUpdate ->
                ODLogger.logInfo("STATUS_UPDATE", actions = *arrayOf("DEVICE_IP=$address", "DEVICE_ID=${worker.id}"))
                if(!schedulerServiceRunning) {
                    ODLogger.logInfo("SCHEDULER_NOT_RUNNING", actions = *arrayOf("DEVICE_IP=$address", "DEVICE_ID=${worker.id}"))
                    return@Worker
                }
                ODLogger.logInfo("SCHEDULER_RUNNING", actions = *arrayOf("DEVICE_IP=$address", "DEVICE_ID=${worker.id}"))
                if (StatusUpdate == Worker.Status.ONLINE) {
                    ODLogger.logInfo("NOTIFY_WORKER_UPDATE", actions = *arrayOf("DEVICE_IP=$address", "DEVICE_ID=${worker.id}"))
                    scheduler.notifyWorkerUpdate(workers[worker.id]?.getProto()) {}
                } else {
                    ODLogger.logInfo("NOTIFY_WORKER_FAILURE", actions = *arrayOf("DEVICE_IP=$address", "DEVICE_ID=${worker.id}"))
                    scheduler.notifyWorkerFailure(workers[worker.id]?.getProto()) {}
                }
            }
        } else {
            ODLogger.logInfo("NOTIFY_WORKER_UPDATE", actions = *arrayOf("DEVICE_IP=$address", "DEVICE_ID=${worker.id}"))
            workers[worker.id]?.updateStatus(worker)
        }
        if(schedulerServiceRunning) scheduler.notifyWorkerUpdate(workers[worker.id]?.getProto()) { S -> ODLogger.logInfo("SCHEDULER_NOTIFIED", actions = *arrayOf("STATUS_CODE=$S"))}
    }

    internal fun announceMulticast(stopAdvertiser: Boolean = false, worker: ODWorker? = null) {
        if (stopAdvertiser) MulticastAdvertiser.stop()
        else {
            val data = worker?.toByteArray() ?: local.getProto()?.toByteArray()
            if (MulticastAdvertiser.isRunning()) MulticastAdvertiser.setAdvertiseData(data)
            else MulticastAdvertiser.start(data)
        }
    }

    fun requestWorkerStatus(): ODProto.Worker? {
        return local.getProto()
    }

    fun enableHearBeats(types: WorkerTypes?): Status? {
        if (heartBeats) return ODUtils.genStatusSuccess()
        heartBeats = true
        if (types == null) return ODUtils.genStatus(StatusCode.Error)
        for (key in workers.keys)
            if (workers[key]?.type in types.typeList && workers[key]?.type != Type.LOCAL) workers[key]?.enableHeartBeat { StatusUpdate ->
                if(!schedulerServiceRunning) return@enableHeartBeat
                if (StatusUpdate == Worker.Status.ONLINE) scheduler.notifyWorkerUpdate(workers[key]?.getProto()) {}
                else scheduler.notifyWorkerFailure(workers[key]?.getProto()) {}
            }
        return ODUtils.genStatus(StatusCode.Success)
    }

    fun enableBandwidthEstimates(method: BandwidthEstimate?): Status? {
        if (bwEstimates) return ODUtils.genStatusSuccess()
        bwEstimates = true
        if (method == null) return ODUtils.genStatus(StatusCode.Error)
        if (method.type == BandwidthEstimate.Type.ACTIVE) {
            for (key in workers.keys)
                if (workers[key]?.type in method.workerTypeList && workers[key]?.type != Type.LOCAL) workers[key]?.doActiveRTTEstimates {StatusUpdate ->
                    if(!schedulerServiceRunning) return@doActiveRTTEstimates
                    if (StatusUpdate == Worker.Status.ONLINE) scheduler.notifyWorkerUpdate(workers[key]?.getProto())  {S -> ODLogger.logInfo("WORKER_UPDATE_NOTIFIED", actions = *arrayOf("STATUS_CODE=$S"))}
                    else scheduler.notifyWorkerFailure(workers[key]?.getProto()) {S -> ODLogger.logInfo("WORKER_FAILURE_NOTIFIED", actions = *arrayOf("STATUS_CODE=$S"))}
                }
        }
        return ODUtils.genStatus(StatusCode.Success)
    }

    fun disableHearBeats(): Status? {
        if (!heartBeats) return ODUtils.genStatusSuccess()
        heartBeats = false
        for (key in workers.keys) workers[key]?.disableHeartBeat()
        return ODUtils.genStatusSuccess()
    }

    fun disableBandwidthEstimates(): Status? {
        if (!bwEstimates) return ODUtils.genStatusSuccess()
        bwEstimates = false
        for (key in workers.keys) workers[key]?.stopActiveRTTEstimates()
        return ODUtils.genStatusSuccess()
    }

    fun updateSmartSchedulerWeights(weights: Weights?, callback: ((Status?) -> Unit)) {
        if(schedulerServiceRunning) scheduler.updateSmartSchedulerWeights(weights) { S -> callback(S) }
        else callback(ODUtils.genStatusError())
    }

    fun serviceStatusUpdate(serviceStatus: ServiceStatus?, completeCallback: (Status?) -> Unit) {
        if (serviceStatus?.type == ServiceStatus.Type.SCHEDULER) {
            schedulerServiceRunning = serviceStatus.running
            if (!schedulerServiceRunning) {
                disableHearBeats()
                disableBandwidthEstimates()
            }
            completeCallback(ODUtils.genStatusSuccess())
        } else if (serviceStatus?.type == ServiceStatus.Type.WORKER) {
            workerServiceRunning = serviceStatus.running
            if (!workerServiceRunning) announceMulticast(stopAdvertiser = true)
            if (schedulerServiceRunning) {
                if (serviceStatus.running) scheduler.notifyWorkerUpdate(local.getProto()) { S -> completeCallback(S) }
                else scheduler.notifyWorkerFailure(local.getProto()) { S -> completeCallback(S) }
            } else completeCallback(ODUtils.genStatusSuccess())
        }
    }

    fun calibrateWorker(job: ODProto.String?, function: () -> Unit) {
        ODLogger.logInfo("INIT", "CALIBRATION")
        val workerJob = WorkerJob.newBuilder().setId("CALIBRATION").setFileId(fsAssistant?.createTempFile(fsAssistant?.getByteArrayFast(job?.str ?: "") ?: ByteArray(0))).build()
        if (workerServiceRunning) worker.execute(workerJob) {function()} else function()
        ODLogger.logInfo("COMPLETE", "CALIBRATION")
    }
}

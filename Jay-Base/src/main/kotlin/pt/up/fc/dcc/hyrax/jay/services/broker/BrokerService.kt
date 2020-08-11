package pt.up.fc.dcc.hyrax.jay.services.broker

import com.google.protobuf.Empty
import pt.up.fc.dcc.hyrax.jay.grpc.GRPCServerBase
import pt.up.fc.dcc.hyrax.jay.interfaces.FileSystemAssistant
import pt.up.fc.dcc.hyrax.jay.interfaces.VideoUtils
import pt.up.fc.dcc.hyrax.jay.logger.JayLogger
import pt.up.fc.dcc.hyrax.jay.proto.JayProto
import pt.up.fc.dcc.hyrax.jay.proto.JayProto.*
import pt.up.fc.dcc.hyrax.jay.proto.JayProto.Worker.Type
import pt.up.fc.dcc.hyrax.jay.services.broker.grpc.BrokerGRPCClient
import pt.up.fc.dcc.hyrax.jay.services.broker.grpc.BrokerGRPCServer
import pt.up.fc.dcc.hyrax.jay.services.broker.multicast.MulticastAdvertiser
import pt.up.fc.dcc.hyrax.jay.services.broker.multicast.MulticastListener
import pt.up.fc.dcc.hyrax.jay.services.profiler.grpc.ProfilerGRPCClient
import pt.up.fc.dcc.hyrax.jay.services.profiler.status.device.battery.BatteryMonitor
import pt.up.fc.dcc.hyrax.jay.services.scheduler.grpc.SchedulerGRPCClient
import pt.up.fc.dcc.hyrax.jay.services.worker.grpc.WorkerGRPCClient
import pt.up.fc.dcc.hyrax.jay.structures.Worker
import pt.up.fc.dcc.hyrax.jay.utils.JaySettings
import pt.up.fc.dcc.hyrax.jay.utils.JayUtils
import java.lang.Thread.sleep
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import kotlin.random.Random
import pt.up.fc.dcc.hyrax.jay.proto.JayProto.Worker as JayWorker

object BrokerService {


    internal var batteryMonitor: BatteryMonitor? = null
    private var server: GRPCServerBase? = null
    private val workers: MutableMap<String, Worker> = hashMapOf()
    private val assignedTasks: MutableMap<String, String> = hashMapOf()
    private val scheduler = SchedulerGRPCClient("127.0.0.1")
    private val worker = WorkerGRPCClient("127.0.0.1")
    internal val profiler = ProfilerGRPCClient("127.0.0.1")
    private val local: Worker = if (JaySettings.DEVICE_ID != "") Worker(id = JaySettings.DEVICE_ID, address = "127.0.0.1", type = Type.LOCAL) else Worker(address = "127.0.0.1", type = Type.LOCAL)
    private var heartBeats = false
    private var bwEstimates = false
    private var schedulerServiceRunning = false
    private var workerServiceRunning = false
    private var profilerServiceRunning = false
    private var fsAssistant: FileSystemAssistant? = null
    private var videoUtils: VideoUtils? = null
    private var readServiceData: Boolean = false
    private var readServiceDataLatch: CountDownLatch = CountDownLatch(0)

    init {
        workers[local.id] = local
    }

    fun start(useNettyServer: Boolean = false, fsAssistant: FileSystemAssistant? = null, videoUtils: VideoUtils? = null, batteryMonitor: BatteryMonitor? = null) {
        this.fsAssistant = fsAssistant
        this.videoUtils = videoUtils
        this.batteryMonitor = batteryMonitor
        repeat(30) {
            if (this.server == null) {
                this.server = BrokerGRPCServer(useNettyServer).start()
                if (this.server == null) JaySettings.BROKER_PORT = Random.nextInt(30000, 64000)
            }
        }
        announceMulticast()
        worker.testService { ServiceStatus -> workerServiceRunning = ServiceStatus?.running ?: false }
        scheduler.testService { ServiceStatus ->
            schedulerServiceRunning = ServiceStatus?.running ?: false
            if (schedulerServiceRunning) notifySchedulerForAvailableWorkers()
        }
        profiler.testService { ServiceStatus -> profilerServiceRunning = ServiceStatus?.running ?: false }
    }

    /**
     *  Should periodically read profiler and worker status, send this information to the scheduler
     *  To diffuse information, I should update update the information that is shared with multicast
     *  advertisement.
     *
     * @return success if it is not already running
     */
    private fun readAndDiffuseServiceData(): Boolean {
        if (readServiceData) return false
        readServiceData = true
        thread {
            readServiceDataLatch = CountDownLatch(1)
            do {
                val controlLatch = CountDownLatch(1)
                try {

                    val profile = if (profilerServiceRunning) profiler.getDeviceStatus() else null
                    if (workerServiceRunning) worker.getWorkerStatus {
                        local.updateStatus(it, profile)
                        controlLatch.countDown()
                    }
                } catch (ignore: Exception) {
                    JayLogger.logError("Failed to read Service Data")
                }
                controlLatch.await()
                announceMulticast(worker = local.getProto())
                // Assure that I need to inform scheduler for local host. Maybe scheduler can read this by itself.
                // Maybe Worker is the one that should call this.
                if (schedulerServiceRunning) scheduler.notifyWorkerUpdate(local.getProto()) {
                    JayLogger.logInfo("SCHEDULER_NOTIFIED", "", "DEVICE=LOCAL")
                }
                sleep(JaySettings.READ_SERVICE_DATA_INTERVAL)
            } while (readServiceData)
            readServiceDataLatch.countDown()
        }
        return true
    }

    fun stop(stopGRPCServer: Boolean = true) {
        if (stopGRPCServer) server?.stop()
        readServiceData = false
        readServiceDataLatch.await()
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
        videoUtils?.extractFrames("${fsAssistant?.getAbsolutePath()}/$id", 1, (fsAssistant?.getAbsolutePath()
                ?: ""), (id?.dropLast(4) ?: "thumb"))
    }

    internal fun getByteArrayFromId(id: String?): ByteArray? {
        JayLogger.logInfo("READING_IMAGE_BYTE_ARRAY", actions = *arrayOf("IMAGE_ID=$id"))
        if (id != null)
            return fsAssistant?.getByteArrayFast(id)
        return null
    }

    internal fun executeTask(request: Task?, callback: ((Response?) -> Unit)? = null) {
        val taskId = request?.id ?: ""
        JayLogger.logInfo("INIT", taskId)
        val workerTask = WorkerTask.newBuilder().setId(taskId).setFileId(fsAssistant?.createTempFile(request?.data?.toByteArray())).build()
        if (workerServiceRunning) worker.execute(workerTask) { R ->
            callback?.invoke(R)
        } else callback?.invoke(Response.getDefaultInstance())
        JayLogger.logInfo("COMPLETE", taskId)
    }

    internal fun scheduleTask(request: Task?, callback: ((Response?) -> Unit)? = null) {
        val taskId = request?.id ?: ""
        JayLogger.logInfo("INIT", taskId)
        val taskDetails = JayUtils.getTaskDetails(request)
        if (schedulerServiceRunning) scheduler.schedule(taskDetails) { W ->
            if (request != null) assignedTasks[request.id] = W?.id ?: ""
            JayLogger.logInfo("SCHEDULED", taskId, "WORKER_ID=${W?.id}")
            if (W?.id == "" || W == null) {
                callback?.invoke(null)
            } else {
                if (JaySettings.SINGLE_REMOTE_IP == "0.0.0.0" || (W.type != Type.LOCAL || JaySettings.SINGLE_REMOTE_IP == workers[W.id]!!.address)) {
                    workers[W.id]!!.grpc.executeTask(request, local = (W.id == local.id), callback = { R ->
                        workers[W.id]!!.addResultSize(R.bytes.size().toLong())
                        callback?.invoke(R)
                    }) {
                        scheduler.notifyTaskComplete(taskDetails)
                    }
                } else {
                    workers[local.id]!!.grpc.executeTask(request, true, { R ->
                        workers[local.id]!!.addResultSize(R.bytes.size().toLong())
                        callback?.invoke(R)
                    }) {
                        scheduler
                                .notifyTaskComplete(taskDetails)
                    }
                }
            }
        } else {
            callback?.invoke(Response.getDefaultInstance())
        }
        JayLogger.logInfo("COMPLETE", taskId)
    }

    internal fun passiveBandwidthUpdate(taskId: String, dataSize: Int, duration: Long) {
        if (taskId in assignedTasks && assignedTasks[taskId] != "") {
            workers[assignedTasks[taskId]]?.addRTT(duration.toInt(), dataSize)
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

    internal fun notifySchedulerForAvailableWorkers() {
        val countDownLatch = CountDownLatch(workers.size)
        for (worker in workers.values) {
            if (worker.isOnline() || (worker.type == Type.LOCAL && workerServiceRunning)) updateWorker(worker.getProto(), countDownLatch)
        }
        countDownLatch.await()
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

    private fun notifySchedulerOfWorkerStatusUpdate(statusUpdate: Worker.Status, workerId: String) {
        JayLogger.logInfo("STATUS_UPDATE", actions = *arrayOf("DEVICE_IP=${workers[workerId]?.address}", "DEVICE_ID=${workerId}", "WORKER_TYPE=${workers[workerId]?.type?.name}"))
        if (!schedulerServiceRunning) {
            JayLogger.logInfo("SCHEDULER_NOT_RUNNING", actions = *arrayOf("DEVICE_IP=${workers[workerId]?.address}", "DEVICE_ID=${workerId}", "WORKER_TYPE=${workers[workerId]?.type?.name}"))
            return
        }
        JayLogger.logInfo("SCHEDULER_RUNNING", actions = *arrayOf("DEVICE_IP=${workers[workerId]?.address}", "DEVICE_ID=${workerId}", "WORKER_TYPE=${workers[workerId]?.type?.name}"))
        if (statusUpdate == Worker.Status.ONLINE) {
            JayLogger.logInfo("NOTIFY_WORKER_UPDATE", actions = *arrayOf("DEVICE_IP=${workers[workerId]?.address}", "DEVICE_ID=${workerId}", "WORKER_TYPE=${workers[workerId]?.type?.name}"))
            scheduler.notifyWorkerUpdate(workers[workerId]?.getProto()) {
                JayLogger.logInfo("NOTIFY_WORKER_UPDATE_COMPLETE", actions = *arrayOf(
                        "STATUS=${it?.codeValue}", "DEVICE_IP=${workers[workerId]?.address}",
                        "DEVICE_ID=${workerId}", "WORKER_TYPE=${workers[workerId]?.type?.name}"))
            }
        } else {
            JayLogger.logInfo("NOTIFY_WORKER_FAILURE", actions = *arrayOf("DEVICE_IP=${workers[workerId]?.address}", "DEVICE_ID=${workerId}", "WORKER_TYPE=${workers[workerId]?.type?.name}"))
            scheduler.notifyWorkerFailure(workers[workerId]?.getProto()) {
                JayLogger.logInfo("NOTIFY_WORKER_FAILURE_COMPLETE", actions = *arrayOf(
                        "STATUS=${it?.codeValue}; DEVICE_IP=${workers[workerId]?.address}",
                        "DEVICE_ID=${workerId}", "WORKER_TYPE=${workers[workerId]?.type?.name}"))
            }
        }
    }

    private fun addOrUpdateWorker(worker: JayProto.Worker?, address: String) {
        JayLogger.logInfo("INIT", actions = *arrayOf("DEVICE_IP=$address", "DEVICE_ID=${worker?.id}"))
        if (worker == null) return
        if (worker.id !in workers) {
            if (address == JaySettings.SINGLE_REMOTE_IP) JaySettings.CLOUDLET_ID = worker.id
            JayLogger.logInfo("NEW_DEVICE", actions = *arrayOf("DEVICE_IP=$address", "DEVICE_ID=${worker.id}"))
            workers[worker.id] = Worker(worker, address, heartBeats, bwEstimates) { status -> notifySchedulerOfWorkerStatusUpdate(status, worker.id) }
            workers[worker.id]?.enableAutoStatusUpdate { _ ->
                if (!schedulerServiceRunning) {
                    JayLogger.logInfo("SCHEDULER_NOT_RUNNING", actions = *arrayOf("DEVICE_IP=$address",
                            "DEVICE_ID=${workers[worker.id]?.id}", "WORKER_TYPE=${workers[worker.id]?.type?.name}"))
                    return@enableAutoStatusUpdate
                }
                val latch = CountDownLatch(1)
                updateWorker(workers[worker.id]?.getProto(), latch)
                latch.await()
                JayLogger.logInfo("PROACTIVE_WORKER_STATUS_UPDATE_COMPLETE", "", "WORKER_ID=${workers[worker.id]?.id}")
            }
        } else {
            JayLogger.logInfo("NOTIFY_WORKER_UPDATE", actions = *arrayOf("DEVICE_IP=$address", "DEVICE_ID=${worker.id}"))
            workers[worker.id]?.updateStatus(worker)
        }
        notifySchedulerOfWorkerStatusUpdate(Worker.Status.ONLINE, worker.id)
    }

    internal fun announceMulticast(stopAdvertiser: Boolean = false, worker: JayWorker? = null) {
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
                worker.enableHeartBeat { status -> notifySchedulerOfWorkerStatusUpdate(status, worker.id) }
        return JayUtils.genStatus(StatusCode.Success)
    }

    fun enableBandwidthEstimates(method: BandwidthEstimate?): Status? {
        if (bwEstimates) return JayUtils.genStatusSuccess()
        bwEstimates = true
        if (method == null) return JayUtils.genStatus(StatusCode.Error)
        for (worker in workers.values) {
            if (worker.type in method.workerTypeList && worker.type != Type.LOCAL) {
                when (JaySettings.BANDWIDTH_ESTIMATE_TYPE) {
                    in arrayOf("ACTIVE", "ALL") -> worker.doActiveRTTEstimates { status -> notifySchedulerOfWorkerStatusUpdate(status, worker.id) }
                    else -> worker.enableHeartBeat { status -> notifySchedulerOfWorkerStatusUpdate(status, worker.id) }
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
        } else if (serviceStatus?.type == ServiceStatus.Type.PROFILER) {
            profilerServiceRunning = serviceStatus.running
            completeCallback(JayUtils.genStatusSuccess())
        }
    }

    fun calibrateWorker(task: JayProto.String?, function: () -> Unit) {
        JayLogger.logInfo("INIT", "CALIBRATION")
        val workerTask = WorkerTask.newBuilder().setId("CALIBRATION").setFileId(fsAssistant?.createTempFile(fsAssistant?.getByteArrayFast(task?.str
                ?: "") ?: ByteArray(0))).build()
        if (workerServiceRunning) worker.execute(workerTask) { function() } else function()
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

    fun callExecutorAction(request: Request?, callback: ((Response?) -> Unit)? = null) {
        JayLogger.logInfo("INIT", actions = *arrayOf("REQUEST=${request?.request}"))
        if (workerServiceRunning) worker.callExecutorAction(request, callback)
        else {
            val errCallResponse = Response.newBuilder()
            errCallResponse.status = JayUtils.genStatusError()
            callback?.invoke(errCallResponse.build())
        }
    }

    fun listTaskExecutors(callback: ((TaskExecutors) -> Unit)?) {
        JayLogger.logInfo("INIT")
        if (workerServiceRunning) worker.listTaskExecutors(Empty.getDefaultInstance(), callback)
        else callback?.invoke(TaskExecutors.getDefaultInstance())
    }

    fun runExecutorAction(request: Request?, callback: ((Status?) -> Unit)?) {
        JayLogger.logInfo("INIT", actions = *arrayOf("REQUEST=${request?.request}"))
        if (workerServiceRunning) worker.runExecutorAction(request, callback)
        else callback?.invoke(JayUtils.genStatusError())
    }

    fun selectTaskExecutor(request: TaskExecutor?, callback: ((Status?) -> Unit)?) {
        JayLogger.logInfo("INIT", actions = *arrayOf("EXECUTOR_NAME=${request?.name}"))
        if (workerServiceRunning) worker.selectTaskExecutor(request, callback)
        else callback?.invoke(JayUtils.genStatusError())
    }

    fun setExecutorSettings(request: Settings?, callback: ((Status?) -> Unit)?) {
        JayLogger.logInfo("INIT", actions = *arrayOf("SETTINGS=${request?.settingMap?.keys}"))
        if (workerServiceRunning) worker.setExecutorSettings(request, callback)
        else callback?.invoke(JayUtils.genStatusError())
    }

    fun enableWorkerStatusAdvertisement(): Status? {
        return JayUtils.genStatus(readAndDiffuseServiceData())
    }

    fun disableWorkerStatusAdvertisement(): Status? {
        return try {
            readServiceData = false
            readServiceDataLatch.await()
            JayUtils.genStatusSuccess()
        } catch (ignore: Exception) {
            JayLogger.logError("Failed to disableWorkerStatusAdvertisement")
            JayUtils.genStatusError()
        }
    }

    fun getExpectedCurrentFromRemote(id: String?, callback: ((CurrentEstimations?) -> Unit)) {
        if (!workers.containsKey(id)) callback.invoke(CurrentEstimations.getDefaultInstance())
        workers[id]!!.grpc.getExpectedCurrent(callback)
    }

    fun setSchedulerSettings(request: Settings?, callback: ((Status?) -> Unit)?) {
        JayLogger.logInfo("INIT", actions = *arrayOf("SETTINGS=${request?.settingMap?.keys}"))
        if (schedulerServiceRunning) scheduler.setSchedulerSettings(request, callback)
        else callback?.invoke(JayUtils.genStatusError())
    }

    fun benchmarkNetwork(duration: Long, grpc: BrokerGRPCClient, task: Task?, callback: ((Response) -> Unit)? = null,
                         schedulerInformCallback: (() -> Unit)? = null) {
        val startTime = System.currentTimeMillis()
        JayLogger.logInfo("START_NETWORK_START")
        do {
            val ignore = grpc.blockingStub.networkBenchmark(task)
            JayLogger.logInfo("RUNNING_NETWORK_BENCHMARK", task?.id ?: "",
                    "DURATION=${System.currentTimeMillis() - startTime}ms", "RESPONSE=$ignore")
        } while (System.currentTimeMillis() - startTime < duration)
        JayLogger.logInfo("START_NETWORK_FINISH")
        callback?.invoke(Response.getDefaultInstance())
        schedulerInformCallback?.invoke()
    }
}

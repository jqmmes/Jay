/*
 * Copyright (c) 2021 University of Porto/Faculty of Sciences (UP/FCUP).
 * 
 * Author: Joaquim Silva
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 */

package pt.up.fc.dcc.hyrax.jay.services.broker

import com.google.protobuf.Empty
import pt.up.fc.dcc.hyrax.jay.grpc.GRPCServerBase
import pt.up.fc.dcc.hyrax.jay.interfaces.FileSystemAssistant
import pt.up.fc.dcc.hyrax.jay.logger.JayLogger
import pt.up.fc.dcc.hyrax.jay.proto.JayProto
import pt.up.fc.dcc.hyrax.jay.services.broker.grpc.BrokerGRPCClient
import pt.up.fc.dcc.hyrax.jay.services.broker.grpc.BrokerGRPCServer
import pt.up.fc.dcc.hyrax.jay.services.broker.multicast.MulticastAdvertiser
import pt.up.fc.dcc.hyrax.jay.services.broker.multicast.MulticastListener
import pt.up.fc.dcc.hyrax.jay.services.profiler.grpc.ProfilerGRPCClient
import pt.up.fc.dcc.hyrax.jay.services.profiler.status.device.power.PowerMonitor
import pt.up.fc.dcc.hyrax.jay.services.scheduler.grpc.SchedulerGRPCClient
import pt.up.fc.dcc.hyrax.jay.services.worker.grpc.WorkerGRPCClient
import pt.up.fc.dcc.hyrax.jay.services.worker.taskExecutors.TaskExecutorManager
import pt.up.fc.dcc.hyrax.jay.structures.Worker
import pt.up.fc.dcc.hyrax.jay.structures.WorkerInfo
import pt.up.fc.dcc.hyrax.jay.structures.WorkerType
import pt.up.fc.dcc.hyrax.jay.utils.JaySettings
import pt.up.fc.dcc.hyrax.jay.utils.JayUtils
import java.lang.Thread.sleep
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import kotlin.random.Random
import pt.up.fc.dcc.hyrax.jay.proto.JayProto.WorkerInfo as JayWorker

object BrokerService {
    internal var powerMonitor: PowerMonitor? = null
    private var server: GRPCServerBase? = null
    private val workers: MutableMap<String, Worker> = hashMapOf()
    private val assignedTasks: MutableMap<String, String> = hashMapOf()
    private val scheduler = SchedulerGRPCClient("127.0.0.1")
    internal val worker = WorkerGRPCClient("127.0.0.1")
    internal val profiler = ProfilerGRPCClient("127.0.0.1")
    private val local: Worker = if (JaySettings.DEVICE_ID != "") Worker(id = JaySettings.DEVICE_ID, address = "127.0.0.1", type = WorkerType.LOCAL) else Worker(address = "127.0.0.1", type = WorkerType.LOCAL)
    private var heartBeats = false
    private var bwEstimates = false
    private var schedulerServiceRunning = false
    private var workerServiceRunning = false
    private var profilerServiceRunning = false
    internal var fsAssistant: FileSystemAssistant? = null
    private var readServiceData: Boolean = false
    private var readServiceDataLatch: CountDownLatch = CountDownLatch(0)

    init {
        workers[local.info.id] = local
    }

    fun start(useNettyServer: Boolean = false, fsAssistant: FileSystemAssistant? = null, powerMonitor: PowerMonitor? = null) {
        this.fsAssistant = fsAssistant
        this.powerMonitor = powerMonitor
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
                        local.info.update(it, profile)
                        controlLatch.countDown()
                    }
                    if (profilerServiceRunning) local.info.update(profiler.getExpectedPower())
                } catch (ignore: Exception) {
                    JayLogger.logError("Failed to read Service Data")
                }
                controlLatch.await()
                announceMulticast(worker = local.info.getProto())
                // Assure that I need to inform scheduler for local host. Maybe scheduler can read this by itself.
                // Maybe Worker is the one that should call this.
                if (schedulerServiceRunning) scheduler.notifyWorkerUpdate(local.info.getProto()) {
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
        workers.values.filter { w -> w.info.type == WorkerType.CLOUD }.forEach { w -> w.disableAutoStatusUpdate() }
    }

    fun stopService(callback: ((JayProto.Status?) -> Unit)) {
        stop(false)
        callback(JayUtils.genStatusSuccess())
    }

    fun stopServer() {
        server?.stopNowAndWait()
    }

    internal fun executeTask(task: JayProto.Task?, callback: ((JayProto.Response?) -> Unit)? = null) {
        val taskId = task?.info?.id ?: ""
        JayLogger.logInfo("INIT", taskId)
        fsAssistant?.cacheTask(task)
        val taskInfo = task?.info
        if (workerServiceRunning) worker.execute(taskInfo) { R ->
            callback?.invoke(R)
        } else callback?.invoke(JayProto.Response.getDefaultInstance())
        JayLogger.logInfo("COMPLETE", taskId)
    }

    internal fun scheduleTask(task: JayProto.Task?, callback: ((JayProto.Response?) -> Unit)? = null) {
        val taskId = task?.info?.id ?: ""
        JayLogger.logInfo("INIT", taskId)
        if (schedulerServiceRunning) scheduler.schedule(task?.info) { W ->
            if (task != null) assignedTasks[taskId] = W?.id ?: ""
            JayLogger.logInfo("SCHEDULED", taskId, "WORKER_ID=${W?.id}")
            if (W?.id == "" || W == null) {
                callback?.invoke(null)
            } else {
                if (JaySettings.SINGLE_REMOTE_IP == "0.0.0.0" || (W.type != JayProto.WorkerInfo.Type.LOCAL || JaySettings.SINGLE_REMOTE_IP == workers[W.id]!!.info.address)) {
                    //workers[W.id]!!.grpc.executeTask(JayTask(task?.info).getProto(getByteArrayFromId(taskId)),
                    workers[W.id]!!.grpc.executeTask(task,
                        local = (W.id == local.info.id), callback = { R ->
                        workers[W.id]!!.addResultSize(R.bytes.size().toLong())
                        callback?.invoke(R)
                    }) {
                        scheduler.notifyTaskComplete(task?.info)
                    }
                } else {
                    workers[local.info.id]!!.grpc.executeTask(task,
                            true, { R ->
                        workers[local.info.id]!!.addResultSize(R.bytes.size().toLong())
                        callback?.invoke(R)
                    }) {
                        scheduler.notifyTaskComplete(task?.info)
                    }
                }
            }
        } else {
            callback?.invoke(JayProto.Response.getDefaultInstance())
        }
        JayLogger.logInfo("COMPLETE", taskId)
    }

    internal fun passiveBandwidthUpdate(taskId: String, dataSize: Int, duration: Long) {
        if (taskId in assignedTasks && assignedTasks[taskId] != "") {
            workers[assignedTasks[taskId]]?.addRTT(duration.toInt(), dataSize)
        }
    }

    private fun updateWorker(worker: JayProto.WorkerInfo?, latch: CountDownLatch) {
        scheduler.notifyWorkerUpdate(worker) { S ->
            if (S?.code == JayProto.StatusCode.Error) {
                sleep(50)
                updateWorker(worker, latch)
            } else (latch.countDown())
        }
    }

    internal fun notifySchedulerForAvailableWorkers() {
        val countDownLatch = CountDownLatch(workers.size)
        for (worker in workers.values) {
            if (worker.isOnline() || (worker.info.type == WorkerType.LOCAL && workerServiceRunning)) updateWorker(worker.info.getProto(), countDownLatch)
        }
        countDownLatch.await()
    }

    fun getSchedulers(callback: ((JayProto.Schedulers?) -> Unit)? = null) {
        JayLogger.logInfo("INIT")
        if(schedulerServiceRunning) {
            JayLogger.logInfo("COMPLETE")
            scheduler.listSchedulers(callback)
        } else {
            JayLogger.logWarn("SCHEDULER_NOT_RUNNING")
            callback?.invoke(JayProto.Schedulers.getDefaultInstance())
        }
    }

    fun setScheduler(request: JayProto.Scheduler?, callback: ((JayProto.Status?) -> Unit)? = null) {
        if (schedulerServiceRunning) scheduler.setScheduler(request, callback)
        else callback?.invoke(JayUtils.genStatusError())
    }

    internal fun listenMulticast(stopListener: Boolean = false) {
        if (stopListener) MulticastListener.stop()
        else MulticastListener.listen(callback = { W, A -> addOrUpdateWorker(W, A) })
    }

    internal fun notifyAllocatedTask(notification: JayProto.TaskAllocationNotification, callback: ((JayProto.Status?) -> Unit)) {
        if (notification.workerId in workers) {
            try {
                val protoStr = JayProto.String.newBuilder().setStr(notification.taskId).build()
                if (notification.workerId == local.info.id) {
                    worker.informAllocatedTask(protoStr) { callback.invoke(it) }
                } else {
                    workers[notification.workerId]!!.grpc.informAllocatedTask(protoStr) { callback.invoke(it) }
                }
            } catch (e: Exception) {
                callback(JayUtils.genStatusError())
            }
        } else {
            callback(JayUtils.genStatusError())
        }
    }

    private fun notifySchedulerOfWorkerStatusUpdate(statusUpdate: WorkerInfo.Status, workerId: String) {
        JayLogger.logInfo("STATUS_UPDATE", actions = arrayOf("DEVICE_IP=${workers[workerId]?.info?.address}", "DEVICE_ID=${workerId}", "WORKER_TYPE=${workers[workerId]?.info?.type?.name}"))
        if (!schedulerServiceRunning) {
            JayLogger.logInfo("SCHEDULER_NOT_RUNNING", actions = arrayOf("DEVICE_IP=${workers[workerId]?.info?.address}", "DEVICE_ID=${workerId}", "WORKER_TYPE=${workers[workerId]?.info?.type?.name}"))
            return
        }
        JayLogger.logInfo("SCHEDULER_RUNNING", actions = arrayOf("DEVICE_IP=${workers[workerId]?.info?.address}", "DEVICE_ID=${workerId}", "WORKER_TYPE=${workers[workerId]?.info?.type?.name}"))
        if (statusUpdate == WorkerInfo.Status.ONLINE) {
            JayLogger.logInfo("NOTIFY_WORKER_UPDATE", actions = arrayOf("DEVICE_IP=${workers[workerId]?.info?.address}", "DEVICE_ID=${workerId}", "WORKER_TYPE=${workers[workerId]?.info?.type?.name}"))
            scheduler.notifyWorkerUpdate(workers[workerId]?.info?.getProto()) {
                JayLogger.logInfo("NOTIFY_WORKER_UPDATE_COMPLETE", actions = arrayOf(
                        "STATUS=${it?.codeValue}", "DEVICE_IP=${workers[workerId]?.info?.address}",
                        "DEVICE_ID=${workerId}", "WORKER_TYPE=${workers[workerId]?.info?.type?.name}"))
            }
        } else {
            JayLogger.logInfo("NOTIFY_WORKER_FAILURE", actions = arrayOf("DEVICE_IP=${workers[workerId]?.info?.address}", "DEVICE_ID=${workerId}", "WORKER_TYPE=${workers[workerId]?.info?.type?.name}"))
            scheduler.notifyWorkerFailure(workers[workerId]?.info?.getProto()) {
                JayLogger.logInfo("NOTIFY_WORKER_FAILURE_COMPLETE", actions = arrayOf(
                        "STATUS=${it?.codeValue}; DEVICE_IP=${workers[workerId]?.info?.address}",
                        "DEVICE_ID=${workerId}", "WORKER_TYPE=${workers[workerId]?.info?.type?.name}"))
            }
        }
    }

    private fun addOrUpdateWorker(worker: JayProto.WorkerInfo?, address: String) {
        JayLogger.logInfo("INIT", actions = arrayOf("DEVICE_IP=$address", "DEVICE_ID=${worker?.id}"))
        if (worker == null) return
        if (worker.id !in workers) {
            if (address == JaySettings.SINGLE_REMOTE_IP) JaySettings.CLOUDLET_ID = worker.id
            JayLogger.logInfo("NEW_DEVICE", actions = arrayOf("DEVICE_IP=$address", "DEVICE_ID=${worker.id}"))
            workers[worker.id] = Worker(worker, WorkerType.REMOTE, address, heartBeats, bwEstimates) { status -> notifySchedulerOfWorkerStatusUpdate(status, worker.id) }
            workers[worker.id]?.enableAutoStatusUpdate { _ ->
                if (!schedulerServiceRunning) {
                    JayLogger.logInfo("SCHEDULER_NOT_RUNNING", actions = arrayOf("DEVICE_IP=$address",
                            "DEVICE_ID=${workers[worker.id]?.info?.id}", "WORKER_TYPE=${workers[worker.id]?.info?.type?.name}"))
                    return@enableAutoStatusUpdate
                }
                val latch = CountDownLatch(1)
                updateWorker(workers[worker.id]?.info?.getProto(), latch)
                latch.await()
                JayLogger.logInfo("PROACTIVE_WORKER_STATUS_UPDATE_COMPLETE", "", "WORKER_ID=${workers[worker.id]?.info?.id}")
            }
        } else {
            JayLogger.logInfo("NOTIFY_WORKER_UPDATE", actions = arrayOf("DEVICE_IP=$address", "DEVICE_ID=${worker.id}"))
            workers[worker.id]?.info?.update(worker)
        }
        notifySchedulerOfWorkerStatusUpdate(WorkerInfo.Status.ONLINE, worker.id)
    }

    internal fun announceMulticast(stopAdvertiser: Boolean = false, worker: JayWorker? = null) {
        if (stopAdvertiser) MulticastAdvertiser.stop()
        else {
            val data = worker?.toByteArray() ?: local.info.getProto().toByteArray()
            if (MulticastAdvertiser.isRunning()) MulticastAdvertiser.setAdvertiseData(data)
            else MulticastAdvertiser.start(data)
        }
    }

    fun requestWorkerStatus(): JayProto.WorkerInfo {
        return local.info.getProto()
    }

    fun enableHearBeats(types: JayProto.WorkerTypes?): JayProto.Status {
        if (heartBeats) return JayUtils.genStatusSuccess()
        heartBeats = true
        if (types == null) return JayUtils.genStatus(JayProto.StatusCode.Error)
        for (worker in workers.values)
            if (worker.info.type in WorkerType.values() && worker.info.type != WorkerType.LOCAL)
                worker.enableHeartBeat { status -> notifySchedulerOfWorkerStatusUpdate(status, worker.info.id) }
        return JayUtils.genStatus(JayProto.StatusCode.Success)
    }

    fun enableBandwidthEstimates(method: JayProto.BandwidthEstimate?): JayProto.Status {
        if (bwEstimates) return JayUtils.genStatusSuccess()
        bwEstimates = true
        if (method == null) return JayUtils.genStatus(JayProto.StatusCode.Error)
        for (worker in workers.values) {
            if (worker.info.type in WorkerType.values() && worker.info.type != WorkerType.LOCAL) {
                when (JaySettings.BANDWIDTH_ESTIMATE_TYPE) {
                    in arrayOf("ACTIVE", "ALL") -> worker.doActiveRTTEstimates { status -> notifySchedulerOfWorkerStatusUpdate(status, worker.info.id) }
                    else -> worker.enableHeartBeat { status -> notifySchedulerOfWorkerStatusUpdate(status, worker.info.id) }
                }
            }
        }
        return JayUtils.genStatus(JayProto.StatusCode.Success)
    }

    fun disableHearBeats(): JayProto.Status {
        if (!heartBeats) return JayUtils.genStatusSuccess()
        heartBeats = false
        for (worker in workers.values) worker.disableHeartBeat()
        return JayUtils.genStatusSuccess()
    }

    fun disableBandwidthEstimates(): JayProto.Status {
        if (!bwEstimates) return JayUtils.genStatusSuccess()
        bwEstimates = false
        for (worker in workers.values) worker.stopActiveRTTEstimates()
        return JayUtils.genStatusSuccess()
    }

    fun serviceStatusUpdate(serviceStatus: JayProto.ServiceStatus?, completeCallback: (JayProto.Status?) -> Unit) {
        if (serviceStatus?.type == JayProto.ServiceStatus.Type.SCHEDULER) {
            schedulerServiceRunning = serviceStatus.running
            if (!schedulerServiceRunning) {
                disableHearBeats()
                disableBandwidthEstimates()
            }
            completeCallback(JayUtils.genStatusSuccess())
        } else if (serviceStatus?.type == JayProto.ServiceStatus.Type.WORKER) {
            workerServiceRunning = serviceStatus.running
            if (!workerServiceRunning) announceMulticast(stopAdvertiser = true)
            if (schedulerServiceRunning) {
                if (serviceStatus.running) scheduler.notifyWorkerUpdate(local.info.getProto()) { S -> completeCallback(S) }
                else scheduler.notifyWorkerFailure(local.info.getProto()) { S -> completeCallback(S) }
            } else completeCallback(JayUtils.genStatusSuccess())
        } else if (serviceStatus?.type == JayProto.ServiceStatus.Type.PROFILER) {
            profilerServiceRunning = serviceStatus.running
            completeCallback(JayUtils.genStatusSuccess())
        }
    }

    fun calibrateWorker(task: JayProto.Task?, function: () -> Unit) {
        JayLogger.logInfo("INIT", "CALIBRATION")
        if (workerServiceRunning)
            TaskExecutorManager.getCalibrationTasks().forEach { calibrationTask ->
                worker.execute(calibrationTask.getProto()) { function() }
            }
        else function()
        JayLogger.logInfo("COMPLETE", "CALIBRATION")
    }

    fun addCloud(cloud_ip: String) {
        JayLogger.logInfo("INIT", "", "CLOUD_IP=$cloud_ip")
        if (workers.values.find { w -> w.info.type == WorkerType.CLOUD && w.info.address == cloud_ip } == null) {
            JayLogger.logInfo("NEW_CLOUD", "", "CLOUD_IP=$cloud_ip")
            val cloud = Worker(address = cloud_ip, type = WorkerType.CLOUD, checkHeartBeat = true, bwEstimates = bwEstimates)
            cloud.enableAutoStatusUpdate { workerProto ->
                if (!schedulerServiceRunning) {
                    JayLogger.logInfo("SCHEDULER_NOT_RUNNING", actions = arrayOf("DEVICE_IP=$cloud_ip", "DEVICE_ID=${cloud.info.id}", "WORKER_TYPE=${cloud.info.type.name}"))
                    return@enableAutoStatusUpdate
                }
                scheduler.notifyWorkerUpdate(workerProto) { status ->
                    JayLogger.logInfo("CLOUD_TO_SCHEDULER_WORKER_UPDATE_COMPLETE", "",
                            "STATUS=${status?.code?.name}", "CLOUD_ID=$cloud.id", "CLOUD_IP=$cloud_ip")
                }
            }
            workers[cloud.info.id] = cloud
        }
        JayLogger.logInfo("COMPLETE", "", "CLOUD_IP=$cloud_ip")
    }

    fun callExecutorAction(request: JayProto.Request?, callback: ((JayProto.Response?) -> Unit)? = null) {
        JayLogger.logInfo("INIT", actions = arrayOf("REQUEST=${request?.request}"))
        if (workerServiceRunning) worker.callExecutorAction(request, callback)
        else {
            val errCallResponse = JayProto.Response.newBuilder()
            errCallResponse.status = JayUtils.genStatusError()
            callback?.invoke(errCallResponse.build())
        }
    }

    fun listTaskExecutors(callback: ((JayProto.TaskExecutors) -> Unit)?) {
        JayLogger.logInfo("INIT")
        if (workerServiceRunning) worker.listTaskExecutors(Empty.getDefaultInstance(), callback)
        else callback?.invoke(JayProto.TaskExecutors.getDefaultInstance())
    }

    fun runExecutorAction(request: JayProto.Request?, callback: ((JayProto.Status?) -> Unit)?) {
        JayLogger.logInfo("INIT", actions = arrayOf("REQUEST=${request?.request}"))
        if (workerServiceRunning) worker.runExecutorAction(request, callback)
        else callback?.invoke(JayUtils.genStatusError())
    }

    fun selectTaskExecutor(request: JayProto.TaskExecutor?, callback: ((JayProto.Status?) -> Unit)?) {
        JayLogger.logInfo("INIT", actions = arrayOf("EXECUTOR_NAME=${request?.name}"))
        if (workerServiceRunning) worker.selectTaskExecutor(request, callback)
        else callback?.invoke(JayUtils.genStatusError())
    }

    fun setExecutorSettings(request: JayProto.Settings?, callback: ((JayProto.Status?) -> Unit)?) {
        JayLogger.logInfo("INIT", actions = arrayOf("SETTINGS=${request?.settingMap?.keys}"))
        if (workerServiceRunning) worker.setExecutorSettings(request, callback)
        else callback?.invoke(JayUtils.genStatusError())
    }

    fun enableWorkerStatusAdvertisement(): JayProto.Status {
        return JayUtils.genStatus(readAndDiffuseServiceData())
    }

    fun disableWorkerStatusAdvertisement(): JayProto.Status? {
        return try {
            readServiceData = false
            readServiceDataLatch.await()
            JayUtils.genStatusSuccess()
        } catch (ignore: Exception) {
            JayLogger.logError("Failed to disableWorkerStatusAdvertisement")
            JayUtils.genStatusError()
        }
    }

    fun getExpectedCurrentFromRemote(id: String?, callback: ((JayProto.CurrentEstimations?) -> Unit)) {
        if (!workers.containsKey(id)) callback.invoke(JayProto.CurrentEstimations.getDefaultInstance())
        workers[id]!!.grpc.getExpectedCurrent(callback)
    }

    fun getExpectedPowerFromRemote(id: String?, callback: ((JayProto.PowerEstimations?) -> Unit)) {
        if (!workers.containsKey(id)) callback.invoke(JayProto.PowerEstimations.getDefaultInstance())
        workers[id]!!.grpc.getExpectedPower(callback)
    }

    fun setSchedulerSettings(request: JayProto.Settings?, callback: ((JayProto.Status?) -> Unit)?) {
        JayLogger.logInfo("INIT", actions = arrayOf("SETTINGS=${request?.settingMap?.keys}"))
        if (schedulerServiceRunning) scheduler.setSchedulerSettings(request, callback)
        else callback?.invoke(JayUtils.genStatusError())
    }

    fun benchmarkNetwork(duration: Long, grpc: BrokerGRPCClient, task: JayProto.Task?, callback: ((JayProto.Response) -> Unit)? = null,
                         schedulerInformCallback: (() -> Unit)? = null) {
        val startTime = System.currentTimeMillis()
        JayLogger.logInfo("START_NETWORK_START")
        do {
            val ignore = grpc.blockingStub.networkBenchmark(task)
            JayLogger.logInfo("RUNNING_NETWORK_BENCHMARK", task?.info?.id ?: "",
                    "DURATION=${System.currentTimeMillis() - startTime}ms", "RESPONSE=$ignore")
        } while (System.currentTimeMillis() - startTime < duration)
        JayLogger.logInfo("START_NETWORK_FINISH")
        callback?.invoke(JayProto.Response.getDefaultInstance())
        schedulerInformCallback?.invoke()
    }
}

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

package pt.up.fc.dcc.hyrax.jay.services.scheduler

import pt.up.fc.dcc.hyrax.jay.grpc.GRPCServerBase
import pt.up.fc.dcc.hyrax.jay.logger.JayLogger
import pt.up.fc.dcc.hyrax.jay.proto.JayProto
import pt.up.fc.dcc.hyrax.jay.proto.JayProto.ServiceStatus.Type.SCHEDULER
import pt.up.fc.dcc.hyrax.jay.services.broker.grpc.BrokerGRPCClient
import pt.up.fc.dcc.hyrax.jay.services.profiler.grpc.ProfilerGRPCClient
import pt.up.fc.dcc.hyrax.jay.services.profiler.status.device.power.PowerMonitor
import pt.up.fc.dcc.hyrax.jay.services.scheduler.grpc.SchedulerGRPCServer
import pt.up.fc.dcc.hyrax.jay.services.scheduler.schedulers.SchedulerManager
import pt.up.fc.dcc.hyrax.jay.structures.*
import pt.up.fc.dcc.hyrax.jay.utils.JaySettings
import pt.up.fc.dcc.hyrax.jay.utils.JayUtils
import java.util.*
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.Semaphore
import kotlin.concurrent.thread
import kotlin.math.max
import kotlin.random.Random
import pt.up.fc.dcc.hyrax.jay.proto.JayProto.WorkerInfo as WorkerInfoProto

object SchedulerService {
    enum class WorkerConnectivityStatus {
        ONLINE,
        OFFLINE
    }

    private var powerMonitor: PowerMonitor? = null
    private var server: GRPCServerBase? = null
    private val workers: MutableMap<String, WorkerInfo?> = hashMapOf()
    private val broker = BrokerGRPCClient("127.0.0.1")
    private val profiler = ProfilerGRPCClient("127.0.0.1")
    private val notifyListeners = LinkedList<((WorkerInfo?, WorkerConnectivityStatus) -> Unit)>()
    private var taskCompleteListener: ((String) -> Unit)? = null
    private var running = false
    private val workersLock = Object()
    private val concurrentFIFOQueueSemaphore = ConcurrentLinkedDeque<Semaphore>()
    private val concurrentSemaphoreLock = Object()

    fun enableHeartBeats(workerTypes: Set<WorkerType>, cb: ((Boolean) -> Unit)? = null) {
        val requestBuilder = JayProto.WorkerTypes.newBuilder()
        workerTypes.forEach { type ->
            requestBuilder.addType(
                when (type) {
                    WorkerType.REMOTE -> JayProto.WorkerInfo.Type.REMOTE
                    WorkerType.LOCAL -> JayProto.WorkerInfo.Type.LOCAL
                    WorkerType.CLOUD -> JayProto.WorkerInfo.Type.CLOUD
                }
            )
        }
        broker.enableHeartBeats(requestBuilder.build()) {s ->
            cb?.invoke(when(s.code) {
                JayProto.StatusCode.Success -> true
                else -> false
            })
        }
    }

    fun disableHeartBeats() {
        broker.disableHeartBeats()
    }

    fun enableBandwidthEstimates(bandwidthEstimateConfig: BandwidthEstimationConfig, cb: ((Boolean) -> Unit)? = null) {
        val requestBuilder = JayProto.BandwidthEstimate.newBuilder()
            .setType(
                when(bandwidthEstimateConfig.bandwidth) {
                    BandwidthEstimationType.ACTIVE -> JayProto.BandwidthEstimate.Type.ACTIVE
                    BandwidthEstimationType.PASSIVE -> JayProto.BandwidthEstimate.Type.PASSIVE
                    BandwidthEstimationType.ALL -> JayProto.BandwidthEstimate.Type.ALL
                })
        bandwidthEstimateConfig.workerTypes.forEach {wt ->
            requestBuilder.addWorkerType(when(wt) {
                WorkerType.LOCAL -> JayProto.WorkerInfo.Type.LOCAL
                WorkerType.REMOTE -> JayProto.WorkerInfo.Type.REMOTE
                WorkerType.CLOUD -> JayProto.WorkerInfo.Type.CLOUD
            })
        }
        broker.enableBandwidthEstimates(requestBuilder.build()) { s ->
            cb?.invoke(
                when (s.code) {
                    JayProto.StatusCode.Success -> true
                    else -> false
                }
            )
        }
    }

    fun disableBandwidthEstimates() {
        broker.disableBandwidthEstimates()
    }

    internal fun getWorker(id: String): WorkerInfo? {
        return synchronized(workersLock) {
            if (workers.containsKey(id)) workers[id] else null
        }
    }

    internal fun getWorkers(vararg filter: WorkerType): HashMap<String, WorkerInfo?> {
        return getWorkers(filter.asList())
    }

    internal fun getWorkers(filter: List<WorkerType>): HashMap<String, WorkerInfo?> {
        if (filter.isEmpty()) return workers as HashMap<String, WorkerInfo?>
        val filteredWorkers = mutableMapOf<String, WorkerInfo?>()
        synchronized(workersLock) {
            for (worker in workers) {
                if (worker.value?.type in filter) filteredWorkers[worker.key] = worker.value
            }
        }
        return filteredWorkers as HashMap<String, WorkerInfo?>
    }



    fun start(useNettyServer: Boolean = false, powerMonitor: PowerMonitor? = null) {
        JayLogger.logInfo("INIT")
        if (running) return
        this.powerMonitor = powerMonitor
        repeat(30) {
            if (this.server == null) {
                this.server = SchedulerGRPCServer(useNettyServer).start()
                if (this.server == null) JaySettings.SCHEDULER_PORT = Random.nextInt(30000, 64000)
            }
        }
        this.running = true
        this.broker.announceServiceStatus(JayProto.ServiceStatus.newBuilder().setType(SCHEDULER).setRunning(true).build()) {
            JayLogger.logInfo("COMPLETE")
        }
        thread {
            this.broker.notifySchedulerForAvailableWorkers { JayLogger.logInfo("COMPLETE") }
        }
    }

    internal fun schedule(request: JayProto.TaskInfo?): WorkerInfoProto? {
        if (SchedulerManager.scheduler == null) SchedulerManager.scheduler = SchedulerManager.schedulers.first()
        val w = SchedulerManager.scheduler?.scheduleTask(TaskInfo(request))
        JayLogger.logInfo("SELECTED_WORKER", request?.id ?: "", "WORKER=${w?.id}")
        if (w != null && workers.containsKey(w.id)) {
            broker.notifyAllocatedTask(JayProto.TaskAllocationNotification.newBuilder()
                    .setTaskId(request?.id ?: "")
                    .setWorkerId(w.id)
                    .build()
            ) {
                JayLogger.logInfo("WORKER_TASK_ALLOCATION_NOTIFIED", request?.id ?: "",
                        "STATUS=${it?.code?.name}", "WORKER=${w.id}"
                )
            }
            val semaphore = Semaphore(0)
            synchronized(concurrentSemaphoreLock) {
                concurrentFIFOQueueSemaphore.addLast(semaphore)
            }
            var queueIsFull: Boolean
            synchronized(workersLock) {
                queueIsFull = try {
                    if (workers[w.id]!!.getQueueSize() > 1)
                        workers[w.id]!!.getQueuedTasks() >= max(5, workers[w.id]!!.getQueueSize() - 5)
                    else
                        false
                } catch (ignore: Exception) {
                    false
                }
            }
            while (queueIsFull) {
                try {
                    JayLogger.logInfo("WORKER_QUEUE_FULL", request?.id ?: "", "WORKER=${w.id}",
                            "CURRENT_QUEUE=${workers[w.id]?.getQueuedTasks()}", "MAX_QUEUE=${workers[w.id]?.getQueueSize()}")
                } catch (ignore: Exception) {
                }
                semaphore.acquire()
                synchronized(workersLock) {
                    queueIsFull = try {
                        workers[w.id]!!.getQueuedTasks() >= max(5, workers[w.id]!!.getQueueSize() - 5)
                    } catch (ignore: Exception) {
                        false
                    }
                }
            }
            synchronized(workersLock) {
                try {
                    //= WorkerInfoProto.newBuilder(workers[w.id]).setQueuedTasks(workers[w.id]!!.getQueuedTasks() + 1).build()
                    workers[w.id]?.queuedTasks?.inc()
                } catch (ignore: Exception) {
                }
            }
            synchronized(concurrentSemaphoreLock) {
                concurrentFIFOQueueSemaphore.remove(semaphore)
            }
            try {
                JayLogger.logInfo("INCREMENT_QUEUED_TASKS", request?.id ?: "", "WORKER=${w.id}",
                        "NEW_SIZE=${workers[w.id]?.getQueuedTasks()}")
            } catch (ignore: Exception) {
            }
        }
        return w?.getProto()
    }

    internal fun notifyWorkerUpdate(worker: WorkerInfoProto?): JayProto.StatusCode {
        JayLogger.logInfo("WORKER_UPDATE", actions = arrayOf("WORKER_ID=${worker?.id}", "WORKER_TYPE=${worker?.type?.name}"))
        if (worker?.type == WorkerInfoProto.Type.REMOTE && JaySettings.CLOUDLET_ID != "" && JaySettings.CLOUDLET_ID != worker.id) return JayProto.StatusCode.Success
        synchronized(workersLock) {
            workers[worker!!.id]?.update(worker)
        }
        synchronized(concurrentSemaphoreLock) {
            concurrentFIFOQueueSemaphore.peekFirst()?.release()
        }
        JayLogger.logInfo(
                "WORKER=${worker!!.id}",
                "BAT_CAPACITY=${workers[worker.id]?.getPowerEstimations()?.batteryCapacity}",
                "BAT_LEVEL=${workers[worker.id]?.getPowerEstimations()?.batteryLevel}",
                "BAT_COMPUTE=${workers[worker.id]?.getPowerEstimations()?.compute}",
                "BAT_IDLE=${workers[worker.id]?.getPowerEstimations()?.idle}",
                "BAT_TX=${workers[worker.id]?.getPowerEstimations()?.tx}",
                "BAT_RX=${workers[worker.id]?.getPowerEstimations()?.rx}")
        for (listener in notifyListeners) listener.invoke(workers[worker.id], WorkerConnectivityStatus.ONLINE)
        return JayProto.StatusCode.Success
    }

    internal fun notifyWorkerFailure(worker: WorkerInfoProto?): JayProto.StatusCode {
        JayLogger.logInfo("WORKER_FAILED", actions = arrayOf("WORKER_ID=${worker?.id}", "WORKER_TYPE=${worker?.type?.name}"))
        if (worker!!.id in workers.keys) {
            var removedWorker: WorkerInfo?
            synchronized(workersLock) {
                removedWorker = workers.remove(worker.id)
            }
            for (listener in notifyListeners) listener.invoke(removedWorker, WorkerConnectivityStatus.OFFLINE)
            return JayProto.StatusCode.Success
        }
        return JayProto.StatusCode.Error
    }

    internal fun registerNotifyListener(listener: ((WorkerInfo?, WorkerConnectivityStatus) -> Unit)) {
        notifyListeners.addLast(listener)
    }

    internal fun registerNotifyTaskListener(listener: ((String) -> Unit)) {
        this.taskCompleteListener = listener
    }

    internal fun listenForWorkers(listen: Boolean, callback: ((JayProto.Status) -> Unit)? = null) {
        if (listen) broker.listenMulticastWorkers(callback = callback)
        else broker.listenMulticastWorkers(true, callback)
    }

    fun stop(stopGRPCServer: Boolean = true) {
        running = false
        if (stopGRPCServer) server?.stop()
        broker.announceServiceStatus(JayProto.ServiceStatus.newBuilder().setType(SCHEDULER).setRunning(false).build()) {
            JayLogger.logInfo("STOP")
        }
    }

    fun stopService(callback: ((JayProto.Status?) -> Unit)) {
        stop(false)
        callback(JayUtils.genStatusSuccess())
    }

    fun stopServer() {
        server?.stopNowAndWait()
    }

    internal fun isRunning(): Boolean {
        return running
    }

    internal fun notifyTaskComplete(id: String?) {
        taskCompleteListener?.invoke(id ?: "")
    }

    internal fun getExpectedCurrent(worker: WorkerInfo?, callback: ((CurrentEstimations) -> Unit)) {
        if (worker?.type == WorkerType.LOCAL) callback(CurrentEstimations(profiler.getExpectedCurrent()!!))
        broker.getExpectedCurrentFromRemote(worker?.getProto()) { callback(CurrentEstimations(it!!)) }
    }

    internal fun getExpectedPower(worker: WorkerInfo?, callback: ((PowerEstimations) -> Unit)) {
        if (worker?.type == WorkerType.LOCAL) callback(PowerEstimations(profiler.getExpectedPower()!!))
        broker.getExpectedPowerFromRemote(worker?.getProto()) { callback(PowerEstimations(it!!)) }
    }

    /**
     * todo: Validate that we check the new deadline format. We dropped taskDeadlineTimeStamp, and store deadline in ms (max task duration)
     */
    internal fun canMeetDeadline(taskInfo: TaskInfo, worker: WorkerInfo?): Boolean {
        if (worker == null) return false
        JayLogger.logInfo("CAN_MEET_DEADLINE", taskInfo.getId(), "WORKER=${worker.id}")
        if ((taskInfo.deadline == null) ||
            (taskInfo.deadline == 0L)) {
            JayLogger.logInfo("NO_DEADLINE_SET", taskInfo.getId(), "WORKER=${worker.id}")
            return true
        }
        val deadlineTime: Long = System.currentTimeMillis() + taskInfo.deadline
        val expectedCompletion = System.currentTimeMillis() + ((worker.getQueuedTasks() + 1) * worker.getAvgComputingTimeEstimate() + (worker.bandwidthEstimate * taskInfo.dataSize)).toLong()
        val deadlineTimeWithTolerance = deadlineTime - JaySettings.DEADLINE_CHECK_TOLERANCE
        JayLogger.logInfo("CHECK_WORKER_MEETS_DEADLINE", taskInfo.getId(),
                "WORKER=${worker.id}",
                "MAX_DEADLINE=${deadlineTime}",
                "EXPECTED_DEADLINE=${expectedCompletion}",
                "EXPECTED_DURATION=${((worker.getQueuedTasks() + 1) * worker.getAvgComputingTimeEstimate() + (worker.bandwidthEstimate * taskInfo.dataSize)).toLong()}",
                "QUEUE_SIZE=${worker.getQueuedTasks()}",
                "AVG_TIME_PER_TASK=${worker.getAvgComputingTimeEstimate()}",
                "TASK_DATA_SIZE=${taskInfo.dataSize}",
                "WORKER_BANDWIDTH=${worker.bandwidthEstimate}",
                "DEADLINE_WITH_TOLERANCE=${deadlineTimeWithTolerance}"
        )

        if (expectedCompletion <= deadlineTimeWithTolerance) {
            JayLogger.logInfo("WORKER_MEETS_DEADLINE", taskInfo.getId(), "WORKER=${worker.id};")
            return true
        }
        JayLogger.logInfo("WORKER_CANT_MEET_DEADLINE", taskInfo.getId(), "WORKER=${worker.id}")
        return false
    }

    fun setSchedulerSettings(settingMap: Map<String, Any>, callback: ((JayProto.Status?) -> Unit)?) {
        when(SchedulerManager.scheduler?.setSettings(settingMap)) {
            true -> callback?.invoke(JayUtils.genStatusSuccess())
            false -> callback?.invoke(JayUtils.genStatusError())
        }

    }
}
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

package pt.up.fc.dcc.hyrax.jay.services.worker

import org.apache.commons.collections4.queue.CircularFifoQueue
import pt.up.fc.dcc.hyrax.jay.grpc.GRPCServerBase
import pt.up.fc.dcc.hyrax.jay.interfaces.FileSystemAssistant
import pt.up.fc.dcc.hyrax.jay.logger.JayLogger
import pt.up.fc.dcc.hyrax.jay.proto.JayProto.*
import pt.up.fc.dcc.hyrax.jay.services.broker.grpc.BrokerGRPCClient
import pt.up.fc.dcc.hyrax.jay.services.profiler.grpc.ProfilerGRPCClient
import pt.up.fc.dcc.hyrax.jay.services.profiler.status.jay.JayState
import pt.up.fc.dcc.hyrax.jay.services.worker.grpc.WorkerGRPCServer
import pt.up.fc.dcc.hyrax.jay.services.worker.taskExecutors.TaskExecutor
import pt.up.fc.dcc.hyrax.jay.services.worker.taskExecutors.TaskExecutorManager
import pt.up.fc.dcc.hyrax.jay.utils.JaySettings
import pt.up.fc.dcc.hyrax.jay.utils.JayUtils
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.random.Random

object WorkerService {

    private val TASKS_LOCK = Object()

    private val taskQueue = LinkedBlockingQueue<RunnableTaskObjects>()
    private var running = false
    private var server: GRPCServerBase? = null
    private val broker = BrokerGRPCClient("127.0.0.1")
    private val profiler = ProfilerGRPCClient("127.0.0.1")
    private val averageComputationTimes = CircularFifoQueue<Long>(JaySettings.AVERAGE_COMPUTATION_TIME_TO_SCORE)
    private var runningTasks: AtomicInteger = AtomicInteger(0)
    private var totalTasks: AtomicInteger = AtomicInteger(0)
    private val receivedTasks = HashSet<String>()
    private val waitingToReceiveTasks = HashSet<String>()
    private val tasksLock = Object()
    private var fsAssistant: FileSystemAssistant? = null

    init {
        TaskExecutorManager.executorThreadPool.shutdown()
        JayLogger.logInfo("COMPLETE")
    }

    internal fun queueTask(task: TaskInfo, callback: ((Any) -> Unit)?): StatusCode {
        JayLogger.logInfo("INIT", task.id)
        if (!running) throw Exception("WorkerService not running")
        if (JaySettings.TRANSFER_BASELINE_FLAG) {
            thread {
                Thread.sleep(1000)
                TaskExecutorManager.getCurrentExecutor()?.getDefaultResponse { R ->
                    callback?.invoke(R)
                }
            }
            return StatusCode.Waiting
        }
        synchronized(tasksLock) {
            receivedTasks.add(task.id)
            if (task.id in waitingToReceiveTasks) {
                waitingToReceiveTasks.remove(task.id)
            }
        }
        taskQueue.put(RunnableTaskObjects(task) { R ->
            callback?.invoke(R)
        })
        atomicOperation(totalTasks, increment = true)
        JayLogger.logInfo("TASK_QUEUED", task.id, "TASKS_IN_QUEUE=${totalTasks.get() - runningTasks.get()}")
        return if (callback == null) StatusCode.Success else StatusCode.Waiting
    }

    internal fun informAllocatedTask(taskId: String): Status {
        synchronized(tasksLock) {
            if (taskId !in receivedTasks) waitingToReceiveTasks.add(taskId)
        }
        return JayUtils.genStatusSuccess()
    }

    fun start(useNettyServer: Boolean = false, fsAssistant: FileSystemAssistant? = null) {
        JayLogger.logInfo("INIT")
        if (running) return
        this.fsAssistant = fsAssistant
        if (TaskExecutorManager.executorThreadPool.isShutdown || TaskExecutorManager.executorThreadPool.isTerminated)
            TaskExecutorManager.executorThreadPool = Executors.newFixedThreadPool(JaySettings.WORKING_THREADS)
        repeat(30) {
            if (this.server == null) {
                this.server = WorkerGRPCServer(useNettyServer).start()
                if (this.server == null) JaySettings.WORKER_PORT = Random.nextInt(30000, 64000)
            }
        }
        thread(start = true, isDaemon = true, name = "WorkerService") {
            running = true
            while (running) {
                try {
                    if (!(TaskExecutorManager.executorThreadPool.isShutdown ||
                                TaskExecutorManager.executorThreadPool.isTerminated) && running) {
                        val task = taskQueue.take()
                        JayLogger.logInfo("DEQUEUE_TO_EXECUTOR", task.taskInfo?.id ?: "")
                        TaskExecutorManager.executorThreadPool.execute(task)
                    } else running = false

                } catch (e: Exception) {
                    running = false
                }
            }
            if (!TaskExecutorManager.executorThreadPool.isShutdown)
                TaskExecutorManager.executorThreadPool.shutdownNow()
        }
        broker.announceServiceStatus(
                ServiceStatus.newBuilder().setType(ServiceStatus.Type.WORKER).setRunning(true).build())
        { JayLogger.logInfo("RUNNING") }
        broker.enableWorkerStatusAdvertisement {
            if (it.code == StatusCode.Success) JayLogger.logInfo("WORKER_STATUS_ADVERTISEMENT_ENABLED")
            else JayLogger.logError("WORKER_STATUS_ADVERTISEMENT_FAILED")
        }
        profiler.startRecording()
    }

    fun stop(stopGRPCServer: Boolean = true, callback: ((Status?) -> Unit)? = null) {
        JayLogger.logInfo("INIT")
        running = false
        if (stopGRPCServer) server?.stop()
        taskQueue.clear()
        taskQueue.offer(RunnableTaskObjects(null) {})
        TaskExecutorManager.executorThreadPool.shutdownNow()
        TaskExecutorManager.getCurrentExecutor()?.destroy()
        runningTasks.set(0)
        totalTasks.set(0)
        broker.announceServiceStatus(ServiceStatus.newBuilder().setType(ServiceStatus.Type.WORKER).setRunning(false).build()) { S ->
            JayLogger.logInfo("COMPLETE")
            callback?.invoke(S)
        }
        profiler.stopRecording()
    }

    fun selectTaskExecutor(taskExecutorUUID: String, callback: ((Status?) -> Unit)?) {
        callback?.invoke(if (TaskExecutorManager.setExecutor(taskExecutorUUID)) JayUtils.genStatusSuccess() else JayUtils.genStatusError())
    }

    fun listTaskExecutors(): Set<TaskExecutor> {
        return TaskExecutorManager.getTaskExecutors()
    }

    fun callExecutorAction(action: String, callback: ((Status?, Any?) -> Unit), vararg args: Any) {
        TaskExecutorManager.getCurrentExecutor()?.internalCallAction(action, callback, *args)
    }

    fun runExecutorAction(action: String, callback: ((Status?) -> Unit), vararg args: Any) {
        TaskExecutorManager.getCurrentExecutor()?.internalRunAction(action, callback, *args)
    }

    internal fun isRunning(): Boolean {
        return running
    }

    fun stopService(callback: ((Status?) -> Unit)) {
        JayLogger.logInfo("INIT")
        stop(false) { S ->
            callback(S)
            JayLogger.logInfo("COMPLETE")
        }
    }

    fun stopServer() {
        server?.stopNowAndWait()
    }

    fun setExecutorSettings(settingMap: Map<String, Any>, callback: ((Status?) -> Unit)?) {
        callback?.invoke(TaskExecutorManager.getCurrentExecutor()?.setSettings(settingMap))
    }

    private fun profileExecution(code: (() -> Unit)) {
        JayLogger.logInfo("START")
        val computationStartTimestamp = System.currentTimeMillis()
        profiler.setState(JayState.COMPUTE)
        code.invoke()
        profiler.unSetState(JayState.COMPUTE)
        val totalTime = System.currentTimeMillis() - computationStartTimestamp
        averageComputationTimes.add(totalTime)
        JayLogger.logInfo("END", actions = arrayOf("COMPUTATION_TIME=$totalTime",
                "NEW_AVERAGE_COMPUTATION_TIME=${(this.averageComputationTimes.sum() / this.averageComputationTimes.size)}"))
    }

    private fun atomicOperation(vararg values: AtomicInteger, increment: Boolean = false) {
        synchronized(TASKS_LOCK) {
            for (value in values)
                if (increment) value.incrementAndGet() else value.decrementAndGet()
        }
    }

    fun getWorkerStatus(): WorkerComputeStatus? {
        val workerComputeStatusBuilder = WorkerComputeStatus.newBuilder()
        workerComputeStatusBuilder.avgTimePerTask = if (averageComputationTimes.size > 0) averageComputationTimes.sum() / averageComputationTimes.size else 0
        workerComputeStatusBuilder.queueSize = TaskExecutorManager.getCurrentExecutor()?.getQueueSize()
                ?: Integer.MAX_VALUE
        workerComputeStatusBuilder.runningTasks = runningTasks.get()
        synchronized(tasksLock) {
            workerComputeStatusBuilder.queuedTasks = totalTasks.get()
            workerComputeStatusBuilder.waitingToReceiveTasks = waitingToReceiveTasks.size
        }
        return workerComputeStatusBuilder.build()
    }

    private class RunnableTaskObjects(val taskInfo: TaskInfo?, var callback: ((Any) -> Unit)?) : Runnable {
        override fun run() {
            if (taskInfo == null) return
            val task = fsAssistant?.readTask(taskInfo) ?: return
            if (JaySettings.COMPUTATION_BASELINE_DURATION_FLAG) {
                val time = System.currentTimeMillis()
                do {
                    JayLogger.logInfo("INIT", taskInfo.id ?: "")
                    atomicOperation(runningTasks, increment = true)
                    profileExecution { TaskExecutorManager.getCurrentExecutor()?.executeTask(task, null) }
                    atomicOperation(runningTasks, totalTasks, increment = false)
                } while (System.currentTimeMillis() - time < JaySettings.COMPUTATION_BASELINE_DURATION)
            }
            JayLogger.logInfo("INIT", taskInfo.id ?: "")
            atomicOperation(runningTasks, increment = true)
            profileExecution { TaskExecutorManager.getCurrentExecutor()?.executeTask(task, callback) }
            atomicOperation(runningTasks, totalTasks, increment = false)
        }
    }
}

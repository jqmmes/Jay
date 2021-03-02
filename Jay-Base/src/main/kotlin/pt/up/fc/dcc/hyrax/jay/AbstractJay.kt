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

package pt.up.fc.dcc.hyrax.jay

import pt.up.fc.dcc.hyrax.jay.interfaces.FileSystemAssistant
import pt.up.fc.dcc.hyrax.jay.services.broker.BrokerService
import pt.up.fc.dcc.hyrax.jay.services.broker.grpc.BrokerGRPCClient
import pt.up.fc.dcc.hyrax.jay.services.profiler.ProfilerService
import pt.up.fc.dcc.hyrax.jay.services.scheduler.SchedulerService
import pt.up.fc.dcc.hyrax.jay.services.worker.WorkerService
import pt.up.fc.dcc.hyrax.jay.structures.Scheduler
import pt.up.fc.dcc.hyrax.jay.structures.Task
import pt.up.fc.dcc.hyrax.jay.structures.TaskExecutor
import pt.up.fc.dcc.hyrax.jay.structures.TaskResult
import pt.up.fc.dcc.hyrax.jay.utils.JaySettings
import java.io.ByteArrayInputStream
import java.io.ObjectInputStream
import java.lang.Thread.sleep
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

abstract class AbstractJay {

    protected val broker = BrokerGRPCClient("127.0.0.1")

    init {
        JaySettings.DEVICE_ID = UUID.randomUUID().toString()
    }

    internal companion object {
        val executorPool: ThreadPoolExecutor = ThreadPoolExecutor(100, 100, Long.MAX_VALUE, TimeUnit.MILLISECONDS, LinkedBlockingQueue(Int.MAX_VALUE))
    }

    fun listSchedulers(callback: ((Set<Scheduler>) -> Unit)? = null) {
        broker.getSchedulers(callback)
    }

    fun setScheduler(scheduler: Scheduler, completeCallback: (Boolean) -> Unit) {
        broker.setScheduler(scheduler, completeCallback)
    }

    fun listTaskExecutors(callback: ((Set<TaskExecutor>) -> Unit)? = null) {
        broker.listTaskExecutors(callback)
    }

    fun setTaskExecutor(taskExecutor: TaskExecutor, completeCallback: (Boolean) -> Unit) {
        broker.selectTaskExecutor(taskExecutor, completeCallback)
    }

    protected open fun startBroker(fsAssistant: FileSystemAssistant? = null) {
        BrokerService.start(fsAssistant = fsAssistant)
    }

    open fun startScheduler(fsAssistant: FileSystemAssistant? = null) {
        startBroker(fsAssistant)
        sleep(500)
        SchedulerService.start()
    }

    internal open fun getSchedulerService(): SchedulerService {
        return SchedulerService
    }

    internal open fun getWorkerService(): WorkerService {
        return WorkerService
    }

    abstract fun startWorker()

    /**
     * Asynchronous scheduleTask
     *
     */
    fun scheduleTask(task: Task, result: ((TaskResult) -> Unit)? = null) {
        broker.scheduleTask(task) {response ->
            var taskResult: TaskResult
            ByteArrayInputStream(response.bytes.toByteArray()).use {
                    b -> ObjectInputStream(b).use {
                    o -> taskResult = o.readObject() as TaskResult
            }
            }
            taskResult.taskId = response.id
            result?.invoke(taskResult)
        }
    }

    /**
     * Synchronous scheduleTask
     *
     */
    fun scheduleTask(task: Task): TaskResult? {
        val countDownLatch = CountDownLatch(1)
        var taskResult: TaskResult? = null
        scheduleTask(task) {
            taskResult = it
            countDownLatch.countDown()
        }
        countDownLatch.await()
        return taskResult
    }

    protected open fun stopBroker() {
        BrokerService.stop()
        stopScheduler()
        stopWorker()
    }

    open fun stopScheduler() {
        SchedulerService.stop()
    }

    open fun stopWorker() {
        BrokerService.announceMulticast(true)
        WorkerService.stop()
    }

    open fun destroy(keepServices: Boolean = false) {
        if (keepServices) return
        stopWorker()
        stopScheduler()
        stopBroker()
    }

    open fun startProfiler(fsAssistant: FileSystemAssistant? = null) {
        startBroker(fsAssistant)
        sleep(500)
        ProfilerService.start()
    }
}
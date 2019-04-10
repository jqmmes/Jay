package pt.up.fc.dcc.hyrax.odlib

import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import pt.up.fc.dcc.hyrax.odlib.services.broker.BrokerService
import pt.up.fc.dcc.hyrax.odlib.services.broker.grpc.BrokerGRPCClient
import pt.up.fc.dcc.hyrax.odlib.services.scheduler.SchedulerService
import pt.up.fc.dcc.hyrax.odlib.services.worker.WorkerService
import pt.up.fc.dcc.hyrax.odlib.structures.Job
import pt.up.fc.dcc.hyrax.odlib.structures.Model
import java.lang.Thread.sleep
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

abstract class AbstractODLib {

    protected val broker = BrokerGRPCClient("127.0.0.1")

    internal companion object {
        val executorPool: ThreadPoolExecutor = ThreadPoolExecutor(5, 30, Long.MAX_VALUE, TimeUnit.MILLISECONDS, LinkedBlockingQueue<Runnable>())
    }

    fun listModels(callback: ((Set<Model>) -> Unit)? = null) {
        broker.getModels(callback)
    }

    fun listSchedulers(callback: ((Set<Pair<String, String>>) -> Unit)? = null) {
        broker.getSchedulers(callback)
    }

    fun setModel(model: Model, callback: ((ODProto.Status) -> Unit)? = null) {
        broker.selectModel(model, callback)
    }

    fun setScheduler(id: String) {
        broker.setScheduler(id) {S -> println("schedulerSet $S")}
    }

    protected open fun startBroker() {
        BrokerService.start()
    }

    open fun startScheduler() {
        startBroker()
        sleep(500)
        SchedulerService.start()
    }

    abstract fun startWorker()


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

    fun scheduleJob(data: ByteArray, result: ((ODProto.Results) -> Unit)? = null) {
        broker.scheduleJob(Job(data), result)
    }

    open fun destroy(keepServices: Boolean = false) {
        if (keepServices) return
        stopWorker()
        stopScheduler()
        stopBroker()
    }
}
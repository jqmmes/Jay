package pt.up.fc.dcc.hyrax.odlib

import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import pt.up.fc.dcc.hyrax.odlib.services.broker.BrokerService
import pt.up.fc.dcc.hyrax.odlib.services.broker.grpc.BrokerGRPCClient
import pt.up.fc.dcc.hyrax.odlib.services.scheduler.SchedulerService
import pt.up.fc.dcc.hyrax.odlib.services.worker.WorkerService
import pt.up.fc.dcc.hyrax.odlib.utils.ODJob
import pt.up.fc.dcc.hyrax.odlib.utils.ODModel
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

abstract class AbstractODLib {

    private val broker = BrokerGRPCClient("127.0.0.1")

    internal companion object {
        val executorPool: ThreadPoolExecutor = ThreadPoolExecutor(5, 30, Long.MAX_VALUE, TimeUnit.MILLISECONDS, LinkedBlockingQueue<Runnable>())
    }

    fun listModels(callback: ((Set<ODModel>) -> Unit)? = null) {
        broker.getModels(callback)
    }

    fun listSchedulers(callback: ((Set<Pair<String, String>>) -> Unit)? = null) {
        broker.getSchedulers(callback)
    }

    fun setModel(model: ODModel, callback: ((ODProto.Status) -> Unit)? = null) {
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
        SchedulerService.start()
    }

    abstract fun startWorker()


    protected open fun stopBroker() {
        BrokerService.stop()
    }

    open fun stopScheduler() {
        SchedulerService.stop()
    }

    open fun stopWorker() {
        WorkerService.stop()
    }

    fun scheduleJob(data: ByteArray) {
        broker.scheduleJob(ODJob(data)) { R -> println("ODLib scheduleJob END $R") }
    }

    fun updateWorkers() {
        println("updateWorkers - START")
        broker.updateWorkers()
        println("updateWorkers - END")
    }

    open fun destroy(keepServices: Boolean = false) {
        if (keepServices) return
        stopWorker()
        stopScheduler()
        stopBroker()
    }
}
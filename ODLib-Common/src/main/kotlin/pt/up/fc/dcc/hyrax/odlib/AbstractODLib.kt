package pt.up.fc.dcc.hyrax.odlib

import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import pt.up.fc.dcc.hyrax.odlib.services.broker.BrokerService
import pt.up.fc.dcc.hyrax.odlib.services.broker.grpc.BrokerGRPCClient
import pt.up.fc.dcc.hyrax.odlib.services.scheduler.SchedulerService
import pt.up.fc.dcc.hyrax.odlib.services.scheduler.schedulers.deprecated.SchedulerBase
import pt.up.fc.dcc.hyrax.odlib.services.worker.WorkerService
import pt.up.fc.dcc.hyrax.odlib.utils.ODJob
import pt.up.fc.dcc.hyrax.odlib.utils.ODLogger
import pt.up.fc.dcc.hyrax.odlib.utils.ODModel
import java.util.concurrent.*

abstract class AbstractODLib {

    private val broker = BrokerGRPCClient("127.0.0.1")

    internal companion object {
        val executorPool: ThreadPoolExecutor = ThreadPoolExecutor(5, 30, Long.MAX_VALUE, TimeUnit.MILLISECONDS, LinkedBlockingQueue<Runnable>())
    }

    fun listModels(callback: ((Set<ODModel>) -> Unit)? = null) {
        broker.getModels(callback)
    }

    fun listSchedulers(callback: ((Set<String>) -> Unit)? = null) {
        broker.getSchedulers(callback)
    }

    fun setModel(model: ODModel, callback: ((ODProto.Status) -> Unit)? = null) {
        broker.selectModel(model, callback)
    }

    fun setScheduler(scheduler: String) {
        //broker.setScheduler()
        /*if (WorkerService.isRunning()) {
            ODLogger.logWarn("Can only change scheduler with ComputingService offline")
            return
        }
        ODLogger.logInfo("Setting scheduler: ${scheduler.javaClass.name}")*/
        //this.scheduler = scheduler
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
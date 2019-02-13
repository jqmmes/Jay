package pt.up.fc.dcc.hyrax.odlib

import pt.up.fc.dcc.hyrax.odlib.interfaces.DetectObjects
import pt.up.fc.dcc.hyrax.odlib.services.broker.BrokerService
import pt.up.fc.dcc.hyrax.odlib.services.broker.grpc.BrokerGRPCClient
import pt.up.fc.dcc.hyrax.odlib.services.scheduler.SchedulerService
import pt.up.fc.dcc.hyrax.odlib.services.scheduler.schedulers.LocalScheduler
import pt.up.fc.dcc.hyrax.odlib.services.scheduler.schedulers.Scheduler
import pt.up.fc.dcc.hyrax.odlib.services.worker.WorkerService
import pt.up.fc.dcc.hyrax.odlib.utils.ODJob
import pt.up.fc.dcc.hyrax.odlib.utils.ODLogger
import pt.up.fc.dcc.hyrax.odlib.utils.ODModel
import pt.up.fc.dcc.hyrax.odlib.utils.ODSettings
import java.util.*
import java.util.concurrent.*

abstract class AbstractODLib {

    //private var scheduler : Scheduler = LocalScheduler()
    private val broker = BrokerGRPCClient("127.0.0.1")

    internal companion object {
        private val executorPool: ThreadPoolExecutor = ThreadPoolExecutor(5, 30, Long.MAX_VALUE, TimeUnit.MILLISECONDS, LinkedBlockingQueue<Runnable>())

        fun put(runnable: Runnable): Future<*>? {
            return executorPool.submit(runnable)
        }
    }

    fun listModels(onlyLoaded: Boolean = true) : Set<ODModel> {
        return broker.getModels(onlyLoaded, true)
    }

    fun setModel(model: ODModel) {
        //ClientManager.getLocalODClient().
        broker.selectModel(model)
    }

    /*fun setTFModelMinScore(minimumScore: Float) {
        //ClientManager.getLocalODClient().configureModel(minimumScore)
        //broker.configureModel()
    }*/

    fun setScheduler(scheduler: Scheduler) {
        //broker.setScheduler()
        if (WorkerService.isRunning()) {
            ODLogger.logWarn("Can only change scheduler with ComputingService offline")
            return
        }
        ODLogger.logInfo("Setting scheduler: ${scheduler.javaClass.name}")
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
        broker.scheduleJob(ODJob(data)) { println("ODLib scheduleJob END") }
    }

    fun updateWorkers() {
        broker.updateWorkers()
    }

    open fun destroy() {
        stopWorker()
        stopScheduler()
        stopBroker()
    }
}
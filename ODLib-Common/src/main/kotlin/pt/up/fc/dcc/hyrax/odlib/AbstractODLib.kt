package pt.up.fc.dcc.hyrax.odlib

import pt.up.fc.dcc.hyrax.odlib.clients.ClientManager
import pt.up.fc.dcc.hyrax.odlib.grpc.GRPCServer
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

abstract class AbstractODLib {

    private var scheduler : Scheduler = LocalScheduler()
    private val broker = BrokerGRPCClient("127.0.0.1")

    fun listModels(onlyLoaded: Boolean = true) : Set<ODModel> {
        return ClientManager.getLocalODClient().getModels(onlyLoaded, true)
    }

    fun setTFModel(model: ODModel) {
        ClientManager.getLocalODClient().selectModel(model)
    }

    fun setTFModelMinScore(minimumScore: Float) {
        ClientManager.getLocalODClient().configureModel(minimumScore)
    }

    fun setScheduler(scheduler: Scheduler) {
        if (WorkerService.isRunning()) {
            ODLogger.logWarn("Can only change scheduler with ComputingService offline")
            return
        }
        ODLogger.logInfo("Setting scheduler: ${scheduler.javaClass.name}")
        this.scheduler = scheduler
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

    fun scheduleJob(byteArray: ByteArray) {
        println("results___0")
        broker.scheduleJob(ODJob(byteArray)) { println("results")}
    }

    open fun destroy() {
        stopWorker()
        stopScheduler()
        stopBroker()
    }
}
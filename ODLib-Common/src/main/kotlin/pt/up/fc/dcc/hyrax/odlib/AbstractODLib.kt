package pt.up.fc.dcc.hyrax.odlib

import pt.up.fc.dcc.hyrax.odlib.clients.ClientManager
import pt.up.fc.dcc.hyrax.odlib.grpc.GRPCServer
import pt.up.fc.dcc.hyrax.odlib.interfaces.DetectObjects
import pt.up.fc.dcc.hyrax.odlib.services.scheduler.schedulers.LocalScheduler
import pt.up.fc.dcc.hyrax.odlib.services.scheduler.schedulers.Scheduler
import pt.up.fc.dcc.hyrax.odlib.services.worker.WorkerService
import pt.up.fc.dcc.hyrax.odlib.utils.ODLogger
import pt.up.fc.dcc.hyrax.odlib.utils.ODModel
import pt.up.fc.dcc.hyrax.odlib.utils.ODSettings
import java.util.*

abstract class AbstractODLib (protected val localDetector : DetectObjects) {

    private var grpcServer : GRPCServer? = null
    private var jobManager : Scheduler? = null
    private var scheduler : Scheduler = LocalScheduler()
    private val uuid = UUID.randomUUID().toString()

    protected fun getUUID() : String {
        return uuid
    }

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

    fun startODService() {
        WorkerService.start(localDetector)
    }

    fun stopODService() {
        WorkerService.stop()
    }

    fun startGRPCServer(port : Int) {
        ODSettings.brokerPort = port
        if (grpcServer == null) {
            if (!WorkerService.isRunning()) startODService()
            grpcServer = GRPCServer.startServer(port)
        }
    }

    fun startGRPCServerService(port : Int = ODSettings.brokerPort, useNettyServer : Boolean = false) {
        ODSettings.brokerPort = port
        if (grpcServer == null) {
            if (!WorkerService.isRunning()) startODService()
            grpcServer = GRPCServer(port, useNettyServer).start()
        }
    }

    fun stopGRPCServer() {
        grpcServer?.stop()
        grpcServer = null
    }

    fun clean() {
        stopODService()
        stopGRPCServer()
    }
}
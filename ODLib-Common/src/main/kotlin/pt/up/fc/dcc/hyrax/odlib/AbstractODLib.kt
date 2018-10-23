package pt.up.fc.dcc.hyrax.odlib

import pt.up.fc.dcc.hyrax.odlib.clients.ClientManager
import pt.up.fc.dcc.hyrax.odlib.grpc.GRPCServer
import pt.up.fc.dcc.hyrax.odlib.interfaces.DetectObjects
import pt.up.fc.dcc.hyrax.odlib.interfaces.Scheduler
import pt.up.fc.dcc.hyrax.odlib.jobManager.JobManager
import pt.up.fc.dcc.hyrax.odlib.scheduler.LocalScheduler
import pt.up.fc.dcc.hyrax.odlib.services.ODComputingService
import pt.up.fc.dcc.hyrax.odlib.utils.ODLogger
import pt.up.fc.dcc.hyrax.odlib.utils.ODModel
import pt.up.fc.dcc.hyrax.odlib.utils.ODSettings

abstract class AbstractODLib (private val localDetector : DetectObjects) {

    private var grpcServer : GRPCServer? = null
    private var jobManager : JobManager? = null
    private var scheduler : Scheduler = LocalScheduler()

    fun getJobManager() : JobManager? {
        return jobManager
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
        if (ODComputingService.isRunning()) {
            ODLogger.logWarn("Can only change scheduler with ComputingService offline")
            return
        }
        ODLogger.logInfo("Setting scheduler: ${scheduler.javaClass.name}")
        this.scheduler = scheduler
    }

    fun startODService() {
        JobManager.startService(scheduler)
        ODComputingService.startService(localDetector)
    }

    fun stopODService() {
        ODComputingService.stop()
        JobManager.stopService()
    }

    fun startGRPCServer(odLib: AbstractODLib, port : Int) {
        ODSettings.serverPort = port
        if (grpcServer == null) {
            if (!ODComputingService.isRunning()) startODService()
            grpcServer = GRPCServer.startServer(odLib, port)
        }
    }

    fun startGRPCServerService(odLib: AbstractODLib, port : Int = ODSettings.serverPort, useNettyServer : Boolean = false) {
        ODSettings.serverPort = port
        if (grpcServer == null) {
            if (!ODComputingService.isRunning()) startODService()
            grpcServer = GRPCServer(odLib, port, useNettyServer).start()
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
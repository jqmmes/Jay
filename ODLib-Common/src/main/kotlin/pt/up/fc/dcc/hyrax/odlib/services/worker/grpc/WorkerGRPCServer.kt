package pt.up.fc.dcc.hyrax.odlib.services.worker.grpc

import com.google.protobuf.Empty
import pt.up.fc.dcc.hyrax.odlib.grpc.GRPCServerBase
import pt.up.fc.dcc.hyrax.odlib.logger.ODLogger
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import pt.up.fc.dcc.hyrax.odlib.protoc.WorkerServiceGrpc
import pt.up.fc.dcc.hyrax.odlib.services.worker.WorkerService
import pt.up.fc.dcc.hyrax.odlib.structures.Model
import pt.up.fc.dcc.hyrax.odlib.utils.ODSettings
import pt.up.fc.dcc.hyrax.odlib.utils.ODUtils

internal class WorkerGRPCServer(useNettyServer: Boolean = false) : GRPCServerBase(ODSettings.workerPort,
        useNettyServer) {

    override val grpcImpl: BindableService = object : WorkerServiceGrpc.WorkerServiceImplBase() {

        override fun execute(request: ODProto.Job?, responseObserver: StreamObserver<ODProto.Results>?) {
            ODLogger.logInfo("WorkerGRPCServer, EXECUTE, START, JOB_ID=${request?.id}")
            WorkerService.queueJob(request!!) { detectionList ->
                ODLogger.logInfo("WorkerGRPCServer, EXECUTE, COMPLETE, JOB_ID=${request.id}")
                genericComplete(ODUtils.genResults(request.id, detectionList), responseObserver)
            }
        }

        override fun listModels(request: Empty?, responseObserver: StreamObserver<ODProto.Models>?) {
            ODLogger.logInfo("WorkerGRPCServer, LIST_MODELS")
            genericComplete(ODUtils.genModelRequest(WorkerService.listModels()), responseObserver)
        }

        override fun selectModel(request: ODProto.Model?, responseObserver: StreamObserver<ODProto.Status>?) {
            ODLogger.logInfo("WorkerGRPCServer, SELECT_MODEL, START")
            WorkerService.loadModel(Model(request!!.id, request.name, request.url, request.downloaded)) { S ->
                ODLogger.logInfo("WorkerGRPCServer, SELECT_MODEL, COMPLETE")
                genericComplete(S, responseObserver)}
        }

        override fun testService(request: Empty?, responseObserver: StreamObserver<ODProto.ServiceStatus>?) {
            ODLogger.logInfo("WorkerGRPCServer, TEST_SERVICE")
            genericComplete(ODProto.ServiceStatus.newBuilder().setType(ODProto.ServiceStatus.Type.WORKER).setRunning(WorkerService.isRunning()).build(), responseObserver)
        }

        override fun stopService(request: Empty?, responseObserver: StreamObserver<ODProto.Status>?) {
            ODLogger.logInfo("WorkerGRPCServer, STOP_SERVICE, START")
            WorkerService.stopService { S ->
                genericComplete(S, responseObserver)
                WorkerService.stopServer()
                ODLogger.logInfo("WorkerGRPCServer, STOP_SERVICE, COMPLETE")
            }
        }
    }
}
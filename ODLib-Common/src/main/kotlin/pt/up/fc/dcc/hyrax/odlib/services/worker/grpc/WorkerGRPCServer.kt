package pt.up.fc.dcc.hyrax.odlib.services.worker.grpc

import com.google.protobuf.Empty
import io.grpc.BindableService
import io.grpc.stub.StreamObserver
import pt.up.fc.dcc.hyrax.odlib.grpc.GRPCServerBase
import pt.up.fc.dcc.hyrax.odlib.logger.ODLogger
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import pt.up.fc.dcc.hyrax.odlib.protoc.WorkerServiceGrpc
import pt.up.fc.dcc.hyrax.odlib.services.worker.WorkerService
import pt.up.fc.dcc.hyrax.odlib.structures.Model
import pt.up.fc.dcc.hyrax.odlib.utils.ODSettings
import pt.up.fc.dcc.hyrax.odlib.utils.ODUtils

internal class WorkerGRPCServer(useNettyServer: Boolean = false) : GRPCServerBase(ODSettings.WORKER_PORT,
        useNettyServer) {

    override val grpcImpl: BindableService = object : WorkerServiceGrpc.WorkerServiceImplBase() {

        override fun execute(request: ODProto.WorkerJob?, responseObserver: StreamObserver<ODProto.Results>?) {
            ODLogger.logInfo("INIT", request?.id ?: "")
            WorkerService.queueJob(request!!) { detectionList ->
                ODLogger.logInfo("COMPLETE", request.id ?: "")
                genericComplete(ODUtils.genResults(request.id, detectionList), responseObserver)
            }
        }

        override fun listModels(request: Empty?, responseObserver: StreamObserver<ODProto.Models>?) {
            ODLogger.logInfo("INIT")
            genericComplete(ODUtils.genModelRequest(WorkerService.listModels()), responseObserver)
            ODLogger.logInfo("COMPLETE")
        }

        override fun selectModel(request: ODProto.Model?, responseObserver: StreamObserver<ODProto.Status>?) {
            ODLogger.logInfo("INIT", actions = *arrayOf("MODEL_ID=${request?.id}"))
            WorkerService.loadModel(Model(request!!.id, request.name, request.url, request.downloaded)) { S ->
                ODLogger.logInfo("COMPLETE", actions = *arrayOf("MODEL_ID=${request.id}"))
                genericComplete(S, responseObserver)}
        }

        override fun testService(request: Empty?, responseObserver: StreamObserver<ODProto.ServiceStatus>?) {
            genericComplete(ODProto.ServiceStatus.newBuilder().setType(ODProto.ServiceStatus.Type.WORKER).setRunning(WorkerService.isRunning()).build(), responseObserver)
        }

        override fun stopService(request: Empty?, responseObserver: StreamObserver<ODProto.Status>?) {
            ODLogger.logInfo("INIT")
            WorkerService.stopService { S ->
                genericComplete(S, responseObserver)
                WorkerService.stopServer()
                ODLogger.logInfo("COMPLETE")
            }
        }
    }
}
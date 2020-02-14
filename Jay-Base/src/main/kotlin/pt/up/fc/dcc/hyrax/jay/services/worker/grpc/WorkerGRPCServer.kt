package pt.up.fc.dcc.hyrax.jay.services.worker.grpc

import com.google.protobuf.Empty
import io.grpc.BindableService
import io.grpc.stub.StreamObserver
import pt.up.fc.dcc.hyrax.jay.grpc.GRPCServerBase
import pt.up.fc.dcc.hyrax.jay.logger.JayLogger
import pt.up.fc.dcc.hyrax.jay.protoc.JayProto
import pt.up.fc.dcc.hyrax.jay.protoc.WorkerServiceGrpc
import pt.up.fc.dcc.hyrax.jay.services.worker.WorkerService
import pt.up.fc.dcc.hyrax.jay.structures.Model
import pt.up.fc.dcc.hyrax.jay.utils.JaySettings
import pt.up.fc.dcc.hyrax.jay.utils.JayUtils

internal class WorkerGRPCServer(useNettyServer: Boolean = false) : GRPCServerBase(JaySettings.WORKER_PORT,
        useNettyServer) {

    override val grpcImpl: BindableService = object : WorkerServiceGrpc.WorkerServiceImplBase() {

        override fun execute(request: JayProto.WorkerJob?, responseObserver: StreamObserver<JayProto.Results>?) {
            JayLogger.logInfo("INIT", request?.id ?: "")
            WorkerService.queueJob(request!!) { detectionList ->
                JayLogger.logInfo("COMPLETE", request.id ?: "")
                genericComplete(JayUtils.genResults(request.id, detectionList), responseObserver)
            }
        }

        override fun listModels(request: Empty?, responseObserver: StreamObserver<JayProto.Models>?) {
            JayLogger.logInfo("INIT")
            genericComplete(JayUtils.genModelRequest(WorkerService.listModels()), responseObserver)
            JayLogger.logInfo("COMPLETE")
        }

        override fun selectModel(request: JayProto.Model?, responseObserver: StreamObserver<JayProto.Status>?) {
            JayLogger.logInfo("INIT", actions = *arrayOf("MODEL_ID=${request?.id}"))
            WorkerService.loadModel(Model(request!!.id, request.name, request.url, request.downloaded)) { S ->
                JayLogger.logInfo("COMPLETE", actions = *arrayOf("MODEL_ID=${request.id}"))
                genericComplete(S, responseObserver)
            }
        }

        override fun testService(request: Empty?, responseObserver: StreamObserver<JayProto.ServiceStatus>?) {
            genericComplete(JayProto.ServiceStatus.newBuilder().setType(JayProto.ServiceStatus.Type.WORKER).setRunning(WorkerService.isRunning()).build(), responseObserver)
        }

        override fun stopService(request: Empty?, responseObserver: StreamObserver<JayProto.Status>?) {
            JayLogger.logInfo("INIT")
            WorkerService.stopService { S ->
                genericComplete(S, responseObserver)
                WorkerService.stopServer()
                JayLogger.logInfo("COMPLETE")
            }
        }
    }
}
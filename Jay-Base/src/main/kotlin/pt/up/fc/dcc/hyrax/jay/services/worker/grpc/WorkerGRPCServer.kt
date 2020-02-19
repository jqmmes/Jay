package pt.up.fc.dcc.hyrax.jay.services.worker.grpc

import com.google.protobuf.ByteString
import com.google.protobuf.Empty
import io.grpc.BindableService
import io.grpc.stub.StreamObserver
import pt.up.fc.dcc.hyrax.jay.grpc.GRPCServerBase
import pt.up.fc.dcc.hyrax.jay.logger.JayLogger
import pt.up.fc.dcc.hyrax.jay.proto.JayProto
import pt.up.fc.dcc.hyrax.jay.proto.WorkerServiceGrpc
import pt.up.fc.dcc.hyrax.jay.services.worker.WorkerService
import pt.up.fc.dcc.hyrax.jay.structures.Model
import pt.up.fc.dcc.hyrax.jay.utils.JaySettings
import pt.up.fc.dcc.hyrax.jay.utils.JayUtils

internal class WorkerGRPCServer(useNettyServer: Boolean = false) : GRPCServerBase(JaySettings.WORKER_PORT, useNettyServer) {

    override val grpcImpl: BindableService = object : WorkerServiceGrpc.WorkerServiceImplBase() {

        override fun execute(request: JayProto.WorkerJob?, responseObserver: StreamObserver<JayProto.Results>?) {
            JayLogger.logInfo("INIT", request?.id ?: "")
            WorkerService.queueJob(request!!) { detectionList ->
                JayLogger.logInfo("COMPLETE", request.id ?: "")
                genericComplete(JayUtils.genResults(request.id, detectionList), responseObserver)
            }
        }

        // @deprecated
        override fun listModels(request: Empty?, responseObserver: StreamObserver<JayProto.Models>?) {
            JayLogger.logInfo("INIT")
            WorkerService.listModels { _, byteArrayModel ->
                genericComplete(JayProto.Models.parseFrom(byteArrayModel as ByteArray), responseObserver)
            }
            JayLogger.logInfo("COMPLETE")
        }

        // @deprecated
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

        override fun callExecutorAction(request: JayProto.Request?, responseObserver: StreamObserver<JayProto.CallResponse>?) {
            JayLogger.logInfo("INIT")
            if (request == null) genericComplete(JayProto.CallResponse.newBuilder().setStatus(JayUtils.genStatusError()).build(), responseObserver)
            WorkerService.callExecutorAction(request!!.request, { Status, Response ->
                val callResponse = JayProto.CallResponse.newBuilder()
                callResponse.status = Status
                callResponse.bytes = ByteString.copyFrom((Response as ByteArray))
                genericComplete(callResponse.build(), responseObserver)
                JayLogger.logInfo("COMPLETE")
            }, *request.argsList.toTypedArray())
        }

        override fun runExecutorAction(request: JayProto.Request?, responseObserver: StreamObserver<JayProto.Status>?) {
            JayLogger.logInfo("INIT")
            if (request == null) genericComplete(JayUtils.genStatusError(), responseObserver)
            WorkerService.runExecutorAction(request!!.request,
                    { S -> genericComplete(S, responseObserver); JayLogger.logInfo("COMPLETE") },
                    *request.argsList.toTypedArray())
        }

        override fun listTaskExecutors(request: Empty?, responseObserver: StreamObserver<JayProto.TaskExecutors>?) {
            JayLogger.logInfo("INIT")
            val taskExecutorsProtoBuilder = JayProto.TaskExecutors.newBuilder()
            WorkerService.listTaskExecutors().forEach { taskExecutor ->
                val taskExecutorProto = JayProto.TaskExecutor.newBuilder()
                taskExecutorProto.id = taskExecutor.id
                taskExecutorProto.description = taskExecutor.description ?: ""
                taskExecutorProto.name = taskExecutor.name
                taskExecutorsProtoBuilder.addTaskExecutors(taskExecutorProto.build())
            }
            genericComplete(taskExecutorsProtoBuilder.build(), responseObserver)
            JayLogger.logInfo("COMPLETE")
        }

        override fun selectTaskExecutor(request: JayProto.TaskExecutor?, responseObserver: StreamObserver<JayProto.Status>?) {
            JayLogger.logInfo("INIT")
            if (request == null) genericComplete(JayUtils.genStatusError(), responseObserver)
            else WorkerService.selectTaskExecutor(request.id) { S -> genericComplete(S, responseObserver) }
            JayLogger.logInfo("COMPLETE")
        }

        // TODO: SetExecutorSettings
        override fun setExecutorSettings(request: JayProto.Settings?, responseObserver: StreamObserver<JayProto.Status>?) {
            JayLogger.logInfo("INIT")
            if (request == null) genericComplete(JayUtils.genStatusError(), responseObserver)
        }
    }
}
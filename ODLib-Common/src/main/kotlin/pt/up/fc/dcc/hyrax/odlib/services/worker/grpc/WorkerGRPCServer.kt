package pt.up.fc.dcc.hyrax.odlib.services.worker.grpc

import com.google.protobuf.Empty
import io.grpc.BindableService
import io.grpc.stub.StreamObserver
import pt.up.fc.dcc.hyrax.odlib.grpc.GRPCServerBase
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import pt.up.fc.dcc.hyrax.odlib.protoc.WorkerServiceGrpc
import pt.up.fc.dcc.hyrax.odlib.services.broker.BrokerService
import pt.up.fc.dcc.hyrax.odlib.services.worker.WorkerService
import pt.up.fc.dcc.hyrax.odlib.structures.Model
import pt.up.fc.dcc.hyrax.odlib.utils.ODSettings
import pt.up.fc.dcc.hyrax.odlib.utils.ODUtils

internal class WorkerGRPCServer(useNettyServer: Boolean = false) : GRPCServerBase(ODSettings.workerPort,
        useNettyServer) {

    override val grpcImpl: BindableService = object : WorkerServiceGrpc.WorkerServiceImplBase() {

        override fun execute(request: ODProto.Job?, responseObserver: StreamObserver<ODProto.Results>?) {
            WorkerService.queueJob(request!!) { detectionList -> genericComplete(ODUtils.genResults(request.id, detectionList), responseObserver) }
        }

        override fun listModels(request: Empty?, responseObserver: StreamObserver<ODProto.Models>?) {
            genericComplete(ODUtils.genModelRequest(WorkerService.listModels()), responseObserver)
        }

        override fun selectModel(request: ODProto.Model?, responseObserver: StreamObserver<ODProto.Status>?) {
            println("WorkerGRPCServer::selectModel ...")
            WorkerService.loadModel(Model(request!!.id, request.name, request.url, request.downloaded)) { S ->
                println("WorkerGRPCServer::selectModel END")
                genericComplete(S, responseObserver)}
        }

        override fun testService(request: Empty?, responseObserver: StreamObserver<ODProto.ServiceStatus>?) {
            genericComplete(ODProto.ServiceStatus.newBuilder().setType(ODProto.ServiceStatus.Type.WORKER).setRunning(WorkerService.isRunning()).build(), responseObserver)
        }

        override fun stopService(request: Empty?, responseObserver: StreamObserver<ODProto.Status>?) {
            WorkerService.stopService { S ->
                genericComplete(S, responseObserver)
                WorkerService.stopServer()
            }
        }
    }
}
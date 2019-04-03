package pt.up.fc.dcc.hyrax.odlib.services.worker.grpc

import com.google.protobuf.Empty
import io.grpc.BindableService
import io.grpc.stub.StreamObserver
import pt.up.fc.dcc.hyrax.odlib.grpc.GRPCServerBase
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import pt.up.fc.dcc.hyrax.odlib.protoc.WorkerServiceGrpc
import pt.up.fc.dcc.hyrax.odlib.services.worker.WorkerService
import pt.up.fc.dcc.hyrax.odlib.structures.ODModel
import pt.up.fc.dcc.hyrax.odlib.utils.ODSettings
import pt.up.fc.dcc.hyrax.odlib.utils.ODUtils

internal class WorkerGRPCServer(useNettyServer: Boolean = false) : GRPCServerBase(ODSettings.workerPort,
        useNettyServer) {

    override val grpcImpl: BindableService = object : WorkerServiceGrpc.WorkerServiceImplBase() {

        override fun execute(request: ODProto.Job?, responseObserver: StreamObserver<ODProto.Results>?) {
            println("WorkerGRPCServer::execute")
            WorkerService.queueJob(request!!) { detectionList -> genericComplete(ODUtils.genResults(request.id, detectionList), responseObserver) }
        }

        override fun listModels(request: Empty?, responseObserver: StreamObserver<ODProto.Models>?) {
            println("WorkerGRPCServer::listModels")
            genericComplete(ODUtils.genModelRequest(WorkerService.listModels()), responseObserver)
        }

        override fun selectModel(request: ODProto.Model?, responseObserver: StreamObserver<ODProto.Status>?) {
            println("WorkerGRPCServer::selectModel")
            WorkerService.loadModel(ODModel(request!!.id, request.name, request.url, request.downloaded)) { S -> genericComplete(S, responseObserver)}
        }
    }
}
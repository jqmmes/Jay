package pt.up.fc.dcc.hyrax.odlib.services.worker.grpc

import com.google.protobuf.Empty
import io.grpc.BindableService
import io.grpc.stub.StreamObserver
import pt.up.fc.dcc.hyrax.odlib.grpc.GRPCServerBase
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import pt.up.fc.dcc.hyrax.odlib.protoc.WorkerServiceGrpc
import pt.up.fc.dcc.hyrax.odlib.services.worker.WorkerService
import pt.up.fc.dcc.hyrax.odlib.utils.ODModel
import pt.up.fc.dcc.hyrax.odlib.utils.ODSettings
import pt.up.fc.dcc.hyrax.odlib.utils.ODUtils

internal class WorkerGRPCServer(useNettyServer: Boolean = false) : GRPCServerBase(ODSettings.workerPort,
        useNettyServer) {

    override val grpcImpl: BindableService = object : WorkerServiceGrpc.WorkerServiceImplBase() {

        override fun execute(request: ODProto.Job?, responseObserver: StreamObserver<ODProto.Results>?) {
            WorkerService.queueJob(request!!.data.toByteArray(), {
                detectionList -> genericComplete(ODUtils.genResults(request.id, detectionList), responseObserver)
            }, request.id)
        }

        override fun listModels(request: Empty?, responseObserver: StreamObserver<ODProto.Models>?) {
            genericComplete(ODUtils.genModelRequest(WorkerService.listModels()), responseObserver)
        }

        override fun selectModel(request: ODProto.Model?, responseObserver: StreamObserver<ODProto.Status>?) {
            WorkerService.loadModel(ODModel(request!!.id, request.name)) {S -> genericComplete(S, responseObserver)}
        }
    }
}
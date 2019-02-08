package pt.up.fc.dcc.hyrax.odlib.services.worker.grpc

import io.grpc.BindableService
import io.grpc.stub.StreamObserver
import pt.up.fc.dcc.hyrax.odlib.grpc.GRPCServerBase
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import pt.up.fc.dcc.hyrax.odlib.protoc.WorkerGrpc
import pt.up.fc.dcc.hyrax.odlib.services.worker.WorkerService
import pt.up.fc.dcc.hyrax.odlib.utils.ODSettings
import pt.up.fc.dcc.hyrax.odlib.utils.ODUtils

internal class WorkerGRPCServer(useNettyServer: Boolean = false) : GRPCServerBase(ODSettings.workerPort,
        useNettyServer) {
    override val grpcImpl: BindableService = object : WorkerGrpc.WorkerImplBase() {

        override fun submitJob(request: ODProto.Job?, responseObserver: StreamObserver<ODProto.JobResults>?) {
            println("WorkerGRPCServer submitJob")
            WorkerService.putJob(request!!.data.toByteArray(), { detectionList ->
                println("submitJob Complete")
                genericComplete(ODUtils.genResults(request.id, detectionList), responseObserver)
            }, request.id)
        }
    }
}
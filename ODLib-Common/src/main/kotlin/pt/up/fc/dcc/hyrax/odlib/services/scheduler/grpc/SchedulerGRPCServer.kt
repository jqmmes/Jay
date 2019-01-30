package pt.up.fc.dcc.hyrax.odlib.services.scheduler.grpc

import io.grpc.BindableService
import io.grpc.stub.StreamObserver
import pt.up.fc.dcc.hyrax.odlib.grpc.GRPCServerBase
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import pt.up.fc.dcc.hyrax.odlib.protoc.SchedulerGrpc
import pt.up.fc.dcc.hyrax.odlib.services.scheduler.SchedulerService
import pt.up.fc.dcc.hyrax.odlib.utils.ODSettings
import pt.up.fc.dcc.hyrax.odlib.utils.ODUtils

internal class SchedulerGRPCServer(useNettyServer: Boolean = false) : GRPCServerBase(ODSettings.workerPort,
        useNettyServer) {
    override val grpcImpl: BindableService = object : SchedulerGrpc.SchedulerImplBase() {
        override fun scheduleJob(request: ODProto.Job?, responseObserver: StreamObserver<ODProto.RemoteClient>?) {
            SchedulerService.scheduleJob(request!!.id)
            genericComplete(ODUtils.genLocalClient()!!, responseObserver!!)
        }
    }
}
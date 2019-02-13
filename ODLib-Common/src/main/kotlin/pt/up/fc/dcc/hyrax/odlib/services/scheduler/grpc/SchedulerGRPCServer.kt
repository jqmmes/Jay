package pt.up.fc.dcc.hyrax.odlib.services.scheduler.grpc

import io.grpc.BindableService
import io.grpc.stub.StreamObserver
import pt.up.fc.dcc.hyrax.odlib.grpc.GRPCServerBase
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import pt.up.fc.dcc.hyrax.odlib.protoc.SchedulerServiceGrpc
import pt.up.fc.dcc.hyrax.odlib.services.scheduler.SchedulerService
import pt.up.fc.dcc.hyrax.odlib.utils.ODSettings

internal class SchedulerGRPCServer(useNettyServer: Boolean = false) : GRPCServerBase(ODSettings.schedulerPort,
        useNettyServer) {

    override val grpcImpl: BindableService = object : SchedulerServiceGrpc.SchedulerServiceImplBase() {

        override fun schedule(request: ODProto.Job?, responseObserver: StreamObserver<ODProto.Worker>?) {
            genericComplete(ODProto.Worker.newBuilder().setId(SchedulerService.schedule(request)).setStatusValue(0).build(), responseObserver)
        }

        override fun notify(request: ODProto.Worker?, responseObserver: StreamObserver<ODProto.RequestStatus>?) {
            SchedulerService.notify(request)
            genericComplete(ODProto.RequestStatus.newBuilder().setCodeValue(0).build(), responseObserver)
        }
    }
}
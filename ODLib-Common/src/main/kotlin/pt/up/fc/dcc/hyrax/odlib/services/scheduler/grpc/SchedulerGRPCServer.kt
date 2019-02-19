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

        override fun notify(request: ODProto.Worker?, responseObserver: StreamObserver<ODProto.Status>?) {
            SchedulerService.notify(request)
            genericComplete(ODProto.Status.newBuilder().setCodeValue(0).build(), responseObserver)
        }

        override fun setScheduler(request: ODProto.Scheduler?, responseObserver: StreamObserver<ODProto.Status>?) {
            super.setScheduler(request, responseObserver)
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }
    }
}
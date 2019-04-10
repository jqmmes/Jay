package pt.up.fc.dcc.hyrax.odlib.services.scheduler.grpc

import com.google.protobuf.Empty
import io.grpc.BindableService
import io.grpc.stub.StreamObserver
import pt.up.fc.dcc.hyrax.odlib.grpc.GRPCServerBase
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import pt.up.fc.dcc.hyrax.odlib.protoc.SchedulerServiceGrpc
import pt.up.fc.dcc.hyrax.odlib.services.scheduler.SchedulerService
import pt.up.fc.dcc.hyrax.odlib.utils.ODSettings
import pt.up.fc.dcc.hyrax.odlib.utils.ODUtils

internal class SchedulerGRPCServer(useNettyServer: Boolean = false) : GRPCServerBase(ODSettings.schedulerPort,
        useNettyServer) {

    override val grpcImpl: BindableService = object : SchedulerServiceGrpc.SchedulerServiceImplBase() {

        override fun schedule(request: ODProto.Job?, responseObserver: StreamObserver<ODProto.Worker>?) {
            val worker = SchedulerService.schedule(request)
            genericComplete(worker, responseObserver)
        }

        override fun notifyWorkerUpdate(request: ODProto.Worker?, responseObserver: StreamObserver<ODProto.Status>?) {
            genericComplete(ODUtils.genStatus(SchedulerService.notifyWorkerUpdate(request)), responseObserver)
        }

        override fun notifyWorkerFailure(request: ODProto.Worker?, responseObserver: StreamObserver<ODProto.Status>?) {
            genericComplete(ODUtils.genStatus(SchedulerService.notifyWorkerFailure(request)), responseObserver)
        }

        override fun listSchedulers(request: Empty?, responseObserver: StreamObserver<ODProto.Schedulers>?) {
            genericComplete(SchedulerService.listSchedulers(), responseObserver)
        }

        override fun setScheduler(request: ODProto.Scheduler?, responseObserver: StreamObserver<ODProto.Status>?) {
            genericComplete(ODUtils.genStatus(SchedulerService.setScheduler(request?.id)), responseObserver)
        }

        override fun updateSmartSchedulerWeights(request: ODProto.Weights?, responseObserver: StreamObserver<ODProto.Status>?) {
            genericComplete(SchedulerService.updateWeights(request), responseObserver)
        }

        override fun testService(request: Empty?, responseObserver: StreamObserver<ODProto.ServiceStatus>?) {
            genericComplete(ODProto.ServiceStatus.newBuilder().setType(ODProto.ServiceStatus.Type.SCHEDULER).setRunning(SchedulerService.isRunning()).build(), responseObserver)
        }
    }
}
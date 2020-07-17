package pt.up.fc.dcc.hyrax.jay.services.scheduler.grpc

import com.google.protobuf.Empty
import io.grpc.BindableService
import io.grpc.stub.StreamObserver
import pt.up.fc.dcc.hyrax.jay.grpc.GRPCServerBase
import pt.up.fc.dcc.hyrax.jay.logger.JayLogger
import pt.up.fc.dcc.hyrax.jay.proto.JayProto
import pt.up.fc.dcc.hyrax.jay.proto.SchedulerServiceGrpc
import pt.up.fc.dcc.hyrax.jay.services.scheduler.SchedulerService
import pt.up.fc.dcc.hyrax.jay.utils.JaySettings
import pt.up.fc.dcc.hyrax.jay.utils.JayUtils

internal class SchedulerGRPCServer(useNettyServer: Boolean = false) : GRPCServerBase(JaySettings.SCHEDULER_PORT,
        useNettyServer) {

    override val grpcImpl: BindableService = object : SchedulerServiceGrpc.SchedulerServiceImplBase() {

        override fun schedule(request: JayProto.TaskDetails?, responseObserver: StreamObserver<JayProto.Worker>?) {
            val worker = SchedulerService.schedule(request)
            genericComplete(worker, responseObserver)
        }

        override fun notifyTaskComplete(request: JayProto.TaskDetails?, responseObserver: StreamObserver<Empty>?) {
            SchedulerService.notifyTaskComplete(request?.id)
            genericComplete(Empty.getDefaultInstance(), responseObserver)
        }

        override fun notifyWorkerUpdate(request: JayProto.Worker?, responseObserver: StreamObserver<JayProto.Status>?) {
            genericComplete(JayUtils.genStatus(SchedulerService.notifyWorkerUpdate(request)), responseObserver)
        }

        override fun notifyWorkerFailure(request: JayProto.Worker?, responseObserver: StreamObserver<JayProto.Status>?) {
            genericComplete(JayUtils.genStatus(SchedulerService.notifyWorkerFailure(request)), responseObserver)
        }

        override fun listSchedulers(request: Empty?, responseObserver: StreamObserver<JayProto.Schedulers>?) {
            JayLogger.logInfo("INIT")
            genericComplete(SchedulerService.listSchedulers(), responseObserver)
            JayLogger.logInfo("COMPLETE")
        }

        override fun setScheduler(request: JayProto.Scheduler?, responseObserver: StreamObserver<JayProto.Status>?) {
            genericComplete(JayUtils.genStatus(SchedulerService.setScheduler(request?.id)), responseObserver)
        }

        override fun setSchedulerSettings(request: JayProto.Settings?, responseObserver: StreamObserver<JayProto.Status>?) {
            JayLogger.logInfo("INIT")
            if (request == null) genericComplete(JayUtils.genStatusError(), responseObserver)
            else SchedulerService.setSchedulerSettings(request.settingMap) { S -> genericComplete(S, responseObserver) }
            JayLogger.logInfo("COMPLETE")
        }

        override fun testService(request: Empty?, responseObserver: StreamObserver<JayProto.ServiceStatus>?) {
            genericComplete(JayProto.ServiceStatus.newBuilder().setType(JayProto.ServiceStatus.Type.SCHEDULER)
                    .setRunning(SchedulerService.isRunning()).build(), responseObserver)
        }

        override fun stopService(request: Empty?, responseObserver: StreamObserver<JayProto.Status>?) {
            SchedulerService.stopService { S ->
                genericComplete(S, responseObserver)
                SchedulerService.stopServer()
            }
        }
    }
}
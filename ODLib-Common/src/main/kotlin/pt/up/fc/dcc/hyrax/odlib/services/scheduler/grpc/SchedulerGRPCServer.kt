package pt.up.fc.dcc.hyrax.odlib.services.scheduler.grpc

import io.grpc.BindableService
import io.grpc.stub.StreamObserver
import pt.up.fc.dcc.hyrax.odlib.grpc.GRPCServerBase
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import pt.up.fc.dcc.hyrax.odlib.protoc.SchedulerGrpc
import pt.up.fc.dcc.hyrax.odlib.services.scheduler.SchedulerService
import pt.up.fc.dcc.hyrax.odlib.utils.ODSettings

internal class SchedulerGRPCServer(useNettyServer: Boolean = false) : GRPCServerBase(ODSettings.schedulerPort,
        useNettyServer) {
    override val grpcImpl: BindableService = object : SchedulerGrpc.SchedulerImplBase() {
        override fun schedule(request: ODProto.Job?, responseObserver: StreamObserver<ODProto.WorkerId>?) {
            println("SchedulerGRPCServer Received scheduleJob")

            genericComplete(ODProto.WorkerId.newBuilder().setId(SchedulerService.schedule(request)).build(), responseObserver)

            /*val client = ODProto.RemoteClient.newBuilder()
            .setAddress("127.0.0.1")
                    .setPort(ODSettings.brokerPort)
                    //.setId(ODUtils.genClientId(NetworkUtils.getLocalIpV4(false)))
                    .setId("")
                    .build()
            genericComplete(client, responseObserver!!)*/
        }

        override fun notify(request: ODProto.WorkerStatus?, responseObserver: StreamObserver<ODProto.RequestStatus>?) {
            SchedulerService.notify(request)
            genericComplete(ODProto.RequestStatus.newBuilder().setCodeValue(0).build(), responseObserver)
        }
    }
}
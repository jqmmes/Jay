package pt.up.fc.dcc.hyrax.odlib.services.broker.grpc

import com.google.protobuf.Empty
import io.grpc.BindableService
import io.grpc.stub.StreamObserver
import pt.up.fc.dcc.hyrax.odlib.grpc.GRPCServerBase
import pt.up.fc.dcc.hyrax.odlib.protoc.BrokerGrpc
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import pt.up.fc.dcc.hyrax.odlib.services.scheduler.grpc.SchedulerGRPCClient
import pt.up.fc.dcc.hyrax.odlib.services.worker.grpc.WorkerGRPCClient
import pt.up.fc.dcc.hyrax.odlib.utils.ODSettings

internal class BrokerGRPCServer(useNettyServer: Boolean = false) : GRPCServerBase(ODSettings.brokerPort, useNettyServer) {

    private fun runJob(host: String, request: ODProto.Job?, responseObserver: StreamObserver<ODProto.JobResults>) {
        WorkerGRPCClient(host).submitJob(request!!) { results ->
            genericComplete(results, responseObserver)
        }
    }

    override val grpcImpl: BindableService = object : BrokerGrpc.BrokerImplBase() {
        override fun executeJob(request: ODProto.Job?, responseObserver: StreamObserver<ODProto.JobResults>?) {
            runJob("127.0.0.1", request, responseObserver!!)
        }

        override fun putJob(request: ODProto.Job?, responseObserver: StreamObserver<ODProto.JobResults>?) {
            SchedulerGRPCClient("127.0.0.1").scheduleJob(request!!) { client ->
                runJob(client.address, request, responseObserver!!)
            }
        }

        override fun ping(request: Empty?, responseObserver: StreamObserver<Empty>?) {
            genericComplete(Empty.newBuilder().build(), responseObserver)
        }
    }
}
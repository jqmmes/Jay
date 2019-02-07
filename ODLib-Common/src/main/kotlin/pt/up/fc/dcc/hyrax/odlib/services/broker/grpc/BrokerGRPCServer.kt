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
        println("runJob in $host")
        val client = WorkerGRPCClient(host)
        client.submitJob(request!!) { results ->
            genericComplete(results, responseObserver)
            println("runJob Complete")
            client.shutdownNow()
        }
    }

    override val grpcImpl: BindableService = object : BrokerGrpc.BrokerImplBase() {
        override fun executeJob(request: ODProto.Job?, responseObserver: StreamObserver<ODProto.JobResults>?) {
            runJob("127.0.0.1", request, responseObserver!!)
        }

        override fun putJob(request: ODProto.Job?, responseObserver: StreamObserver<ODProto.JobResults>?) {
            println("received putJob BrokerGRPCServer")
            //runJob("127.0.0.1", request, responseObserver!!)
            //return
            val client = SchedulerGRPCClient("127.0.0.1")
            client.scheduleJob(request!!) { remoteClient ->
                runJob(remoteClient.address, request, responseObserver!!)
                client.shutdownNow()
            }
        }

        override fun ping(request: ODProto.Ping, responseObserver: StreamObserver<ODProto.Ping>?) {
            genericComplete(ODProto.Ping.newBuilder().build(), responseObserver)
        }
    }
}
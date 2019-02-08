package pt.up.fc.dcc.hyrax.odlib.services.broker.grpc

import com.google.protobuf.ByteString
import com.google.protobuf.Empty
import io.grpc.BindableService
import io.grpc.stub.StreamObserver
import pt.up.fc.dcc.hyrax.odlib.grpc.GRPCServerBase
import pt.up.fc.dcc.hyrax.odlib.protoc.BrokerGrpc
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import pt.up.fc.dcc.hyrax.odlib.services.broker.BrokerService
import pt.up.fc.dcc.hyrax.odlib.utils.ODSettings

internal class BrokerGRPCServer(useNettyServer: Boolean = false) : GRPCServerBase(ODSettings.brokerPort, useNettyServer) {

    override val grpcImpl: BindableService = object : BrokerGrpc.BrokerImplBase() {
        override fun executeJob(request: ODProto.Job?, responseObserver: StreamObserver<ODProto.JobResults>?) {
            BrokerService.executeJob(request)
        }

        override fun scheduleJob(request: ODProto.Job?, responseObserver: StreamObserver<ODProto.JobResults>?) {
            println("received queueJob BrokerGRPCServer")
            BrokerService.scheduleJob(request)
        }

        override fun ping(request: ODProto.Ping, responseObserver: StreamObserver<ODProto.Ping>?) {
            if (request.hasField(request.descriptorForType.findFieldByName("reply")))
                if (request.reply) return genericComplete(request, responseObserver)
            genericComplete(ODProto.Ping.newBuilder().setData(ByteString.copyFrom(ByteArray(0))).build(), responseObserver)
        }

        override fun advertiseWorkerStatus(request: ODProto.WorkerStatus?, responseObserver: StreamObserver<ODProto.RequestStatus>?) {
            BrokerService.advertiseWorkerStatus(request)
            genericComplete(ODProto.RequestStatus.newBuilder().setCodeValue(0).build(), responseObserver)
        }

        override fun diffuseWorkerStatus(request: ODProto.WorkerStatus?, responseObserver: StreamObserver<ODProto.RequestStatus>?) {
            BrokerService.diffuseWorkerStatus(request)
            genericComplete(ODProto.RequestStatus.newBuilder().setCodeValue(0).build(), responseObserver)
        }

        override fun updateWorkers(request: Empty?, responseObserver: StreamObserver<Empty>?) {
            BrokerService.updateWorkers()
            genericComplete(request!!, responseObserver)
        }
    }



}
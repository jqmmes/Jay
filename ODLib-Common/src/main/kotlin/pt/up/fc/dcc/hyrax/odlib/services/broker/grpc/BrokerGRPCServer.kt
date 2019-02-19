package pt.up.fc.dcc.hyrax.odlib.services.broker.grpc

import com.google.protobuf.ByteString
import com.google.protobuf.Empty
import io.grpc.BindableService
import io.grpc.stub.StreamObserver
import pt.up.fc.dcc.hyrax.odlib.grpc.GRPCServerBase
import pt.up.fc.dcc.hyrax.odlib.protoc.BrokerServiceGrpc
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import pt.up.fc.dcc.hyrax.odlib.services.broker.BrokerService
import pt.up.fc.dcc.hyrax.odlib.utils.ODSettings

internal class BrokerGRPCServer(useNettyServer: Boolean = false) : GRPCServerBase(ODSettings.brokerPort, useNettyServer) {

    override val grpcImpl: BindableService = object : BrokerServiceGrpc.BrokerServiceImplBase() {
        override fun executeJob(request: ODProto.Job?, responseObserver: StreamObserver<ODProto.Results>?) {
            BrokerService.executeJob(request) { R -> genericComplete(R, responseObserver)}

        }

        override fun scheduleJob(request: ODProto.Job?, responseObserver: StreamObserver<ODProto.Results>?) {
            BrokerService.scheduleJob(request) {R -> genericComplete(R, responseObserver)}
        }

        override fun ping(request: ODProto.Ping, responseObserver: StreamObserver<ODProto.Ping>?) {
            if (request.hasField(request.descriptorForType.findFieldByName("reply")))
                if (request.reply) return genericComplete(request, responseObserver)
            genericComplete(ODProto.Ping.newBuilder().setData(ByteString.copyFrom(ByteArray(0))).build(), responseObserver)
        }

        override fun advertiseWorkerStatus(request: ODProto.Worker?, responseObserver: StreamObserver<ODProto.Status>?) {
            BrokerService.advertiseWorker(request)
            genericComplete(ODProto.Status.newBuilder().setCodeValue(0).build(), responseObserver)
        }

        override fun diffuseWorkerStatus(request: ODProto.Worker?, responseObserver: StreamObserver<ODProto.Status>?) {
            BrokerService.diffuseWorkers(request)
            genericComplete(ODProto.Status.newBuilder().setCodeValue(0).build(), responseObserver)
        }

        override fun updateWorkers(request: Empty?, responseObserver: StreamObserver<Empty>?) {
            BrokerService.updateWorkers()
            genericComplete(request, responseObserver)
        }

        override fun getModels(request: Empty?, responseObserver: StreamObserver<ODProto.Models>?) {
            BrokerService.getModels {M -> genericComplete(M, responseObserver)}
        }

        override fun setModel(request: ODProto.Model?, responseObserver: StreamObserver<ODProto.Status>?) {
            BrokerService.setModel(request) {S -> genericComplete(S, responseObserver)}
        }
    }



}
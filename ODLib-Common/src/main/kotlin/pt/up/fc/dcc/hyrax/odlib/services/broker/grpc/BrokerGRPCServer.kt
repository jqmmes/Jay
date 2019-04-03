package pt.up.fc.dcc.hyrax.odlib.services.broker.grpc

import com.google.protobuf.BoolValue
import com.google.protobuf.ByteString
import com.google.protobuf.Empty
import io.grpc.BindableService
import io.grpc.stub.StreamObserver
import org.omg.CORBA.Object
import pt.up.fc.dcc.hyrax.odlib.grpc.GRPCServerBase
import pt.up.fc.dcc.hyrax.odlib.protoc.BrokerServiceGrpc
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import pt.up.fc.dcc.hyrax.odlib.services.broker.BrokerService
import pt.up.fc.dcc.hyrax.odlib.utils.ODSettings
import pt.up.fc.dcc.hyrax.odlib.utils.ODUtils

internal class BrokerGRPCServer(useNettyServer: Boolean = false) : GRPCServerBase(ODSettings.brokerPort, useNettyServer) {

    override val grpcImpl: BindableService = object : BrokerServiceGrpc.BrokerServiceImplBase() {
        override fun executeJob(request: ODProto.Job?, responseObserver: StreamObserver<ODProto.Results>?) {
            println("BrokerGRPCServer::executeJob")
            BrokerService.executeJob(request) { R -> genericComplete(R, responseObserver)}

        }

        override fun scheduleJob(request: ODProto.Job?, responseObserver: StreamObserver<ODProto.Results>?) {
            println("BrokerGRPCServer::scheduleJob")
            BrokerService.scheduleJob(request) {R -> genericComplete(R, responseObserver)}
        }

        override fun ping(request: ODProto.Ping, responseObserver: StreamObserver<ODProto.Ping>?) {
            println("BrokerGRPCServer::ping")
            if (request.hasField(request.descriptorForType.findFieldByName("reply")))
                if (request.reply) return genericComplete(request, responseObserver)
            genericComplete(ODProto.Ping.newBuilder().setData(ByteString.copyFrom(ByteArray(0))).build(), responseObserver)
        }

        override fun advertiseWorkerStatus(request: ODProto.Worker?, responseObserver: StreamObserver<ODProto.Status>?) {
            println("BrokerGRPCServer::advertiseWorkerStatus")
            BrokerService.receiveWorkerStatus(request) {
                genericComplete(ODProto.Status.newBuilder().setCodeValue(0).build(), responseObserver)
            }
        }

        override fun diffuseWorkerStatus(request: ODProto.Worker?, responseObserver: StreamObserver<ODProto.Status>?) {
            println("BrokerGRPCServer::diffuseWorkerStatus")
            val notificationComplete = BrokerService.updateWorker(request)
            val diffuseComplete = BrokerService.diffuseWorkerStatus()
            notificationComplete.await()
            diffuseComplete.await()
            genericComplete(ODProto.Status.newBuilder().setCodeValue(0).build(), responseObserver)
        }

        override fun updateWorkers(request: Empty?, responseObserver: StreamObserver<Empty>?) {
            println("BrokerGRPCServer::updateWorkers")
            BrokerService.updateWorkers()
            genericComplete(request, responseObserver)
        }

        override fun getModels(request: Empty?, responseObserver: StreamObserver<ODProto.Models>?) {
            println("BrokerGRPCServer::getModels")
            BrokerService.getModels {M -> genericComplete(M, responseObserver)}
        }

        override fun setModel(request: ODProto.Model?, responseObserver: StreamObserver<ODProto.Status>?) {
            println("BrokerGRPCServer::setModel")
            BrokerService.setModel(request) {S -> genericComplete(S, responseObserver)}
        }

        override fun getSchedulers(request: Empty?, responseObserver: StreamObserver<ODProto.Schedulers>?) {
            println("BrokerGRPCServer::getSchedulers")
            BrokerService.getSchedulers {S -> genericComplete(S, responseObserver)}
        }

        override fun setScheduler(request: ODProto.Scheduler?, responseObserver: StreamObserver<ODProto.Status>?) {
            println("BrokerGRPCServer::setScheduler")
            BrokerService.setScheduler(request) {S -> genericComplete(S, responseObserver)}
            //genericComplete(ODUtils.genStatus(ODProto.Status.Code.Success), responseObserver)
        }

        override fun listenMulticast(request: BoolValue?, responseObserver: StreamObserver<ODProto.Status>?) {
            println("BrokerGRPCServer::listenMulticast")
            BrokerService.listenMulticast(request?.value ?: false)
            genericComplete(ODUtils.genStatus(ODProto.StatusCode.Success), responseObserver)
        }

        override fun announceMulticast(request: Empty?, responseObserver: StreamObserver<ODProto.Status>?) {
            println("BrokerGRPCServer::announceMulticast")
            BrokerService.announceMulticast()
            genericComplete(ODUtils.genStatus(ODProto.StatusCode.Success), responseObserver)
        }

        override fun requestWorkerStatus(request: Empty?, responseObserver: StreamObserver<ODProto.Worker>?) {
            println("BrokerGRPCServer::requestWorkerStatus")
            genericComplete(BrokerService.requestWorkerStatus(), responseObserver)
        }
    }
}
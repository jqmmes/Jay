package pt.up.fc.dcc.hyrax.odlib.services.broker.grpc

import com.google.protobuf.BoolValue
import com.google.protobuf.ByteString
import com.google.protobuf.Empty
import io.grpc.BindableService
import io.grpc.stub.StreamObserver
import pt.up.fc.dcc.hyrax.odlib.grpc.GRPCServerBase
import pt.up.fc.dcc.hyrax.odlib.logger.ODLogger
import pt.up.fc.dcc.hyrax.odlib.protoc.BrokerServiceGrpc
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import pt.up.fc.dcc.hyrax.odlib.services.broker.BrokerService
import pt.up.fc.dcc.hyrax.odlib.utils.ODSettings
import pt.up.fc.dcc.hyrax.odlib.utils.ODUtils

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
            BrokerService.receiveWorkerStatus(request) { S -> genericComplete(S, responseObserver) }
        }

        override fun diffuseWorkerStatus(request: ODProto.Worker?, responseObserver: StreamObserver<ODProto.Status>?) {
            val notificationComplete = BrokerService.updateWorker(request)
            val diffuseComplete = BrokerService.diffuseWorkerStatus()
            notificationComplete.await()
            diffuseComplete.await()
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

        override fun getSchedulers(request: Empty?, responseObserver: StreamObserver<ODProto.Schedulers>?) {
            ODLogger.logInfo("BrokerGRPCServer, GET_SCHEDULERS, INIT")
            BrokerService.getSchedulers {S ->
                ODLogger.logInfo("BrokerGRPCServer, GET_SCHEDULERS, COMPLETE")
                genericComplete(S, responseObserver)
            }
        }

        override fun setScheduler(request: ODProto.Scheduler?, responseObserver: StreamObserver<ODProto.Status>?) {
            BrokerService.setScheduler(request) {S -> genericComplete(S, responseObserver)}
        }

        override fun listenMulticast(request: BoolValue?, responseObserver: StreamObserver<ODProto.Status>?) {
            BrokerService.listenMulticast(request?.value ?: false)
            genericComplete(ODUtils.genStatus(ODProto.StatusCode.Success), responseObserver)
        }

        override fun announceMulticast(request: Empty?, responseObserver: StreamObserver<ODProto.Status>?) {
            BrokerService.announceMulticast()
            genericComplete(ODUtils.genStatus(ODProto.StatusCode.Success), responseObserver)
        }

        override fun requestWorkerStatus(request: Empty?, responseObserver: StreamObserver<ODProto.Worker>?) {
            genericComplete(BrokerService.requestWorkerStatus(), responseObserver)
        }

        override fun enableHearBeats(request: ODProto.WorkerTypes?, responseObserver: StreamObserver<ODProto.Status>?) {
            genericComplete(BrokerService.enableHearBeats(request), responseObserver)
        }

        override fun enableBandwidthEstimates(request: ODProto.BandwidthEstimate?, responseObserver: StreamObserver<ODProto.Status>?) {
            genericComplete(BrokerService.enableBandwidthEstimates(request), responseObserver)
        }

        override fun disableHearBeats(request: Empty?, responseObserver: StreamObserver<ODProto.Status>?) {
            genericComplete(BrokerService.disableHearBeats(), responseObserver)
        }

        override fun disableBandwidthEstimates(request: Empty?, responseObserver: StreamObserver<ODProto.Status>?) {
            genericComplete(BrokerService.disableBandwidthEstimates(), responseObserver)
        }

        override fun updateSmartSchedulerWeights(request: ODProto.Weights?, responseObserver: StreamObserver<ODProto.Status>?) {
            BrokerService.updateSmartSchedulerWeights(request) { S -> genericComplete(S, responseObserver) }
        }

        override fun announceServiceStatus(request: ODProto.ServiceStatus?, responseObserver: StreamObserver<ODProto.Status>?) {
            BrokerService.serviceStatusUpdate(request) { S -> genericComplete(S, responseObserver) }
        }

        override fun stopService(request: Empty?, responseObserver: StreamObserver<ODProto.Status>?) {
            BrokerService.stopService() { S ->
                genericComplete(S, responseObserver)
                BrokerService.stopServer()
            }
        }
    }
}
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
import pt.up.fc.dcc.hyrax.odlib.structures.Job
import pt.up.fc.dcc.hyrax.odlib.utils.ODSettings
import pt.up.fc.dcc.hyrax.odlib.utils.ODUtils.genStatus

internal class BrokerGRPCServer(useNettyServer: Boolean = false) : GRPCServerBase(ODSettings.brokerPort, useNettyServer) {

    override val grpcImpl: BindableService = object : BrokerServiceGrpc.BrokerServiceImplBase() {
        override fun executeJob(request: ODProto.Job?, responseObserver: StreamObserver<ODProto.Results>?) {
            responseObserver?.onNext(ODProto.Results.newBuilder().setStatus(ODProto.StatusCode.Received).build())
            BrokerService.executeJob(request) { R -> genericComplete(R, responseObserver)}
        }

        override fun scheduleJob(request: ODProto.Job?, responseObserver: StreamObserver<ODProto.Results>?) {
            BrokerService.scheduleJob(request) {R -> genericComplete(R, responseObserver) }
        }

        override fun calibrateWorker(request: ODProto.String?, responseObserver: StreamObserver<Empty>?) {
            BrokerService.calibrateWorker(request) { genericComplete(Empty.getDefaultInstance(), responseObserver) }
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
            ODLogger.logInfo("INIT", actions = *arrayOf("MODEL_ID=${request?.id}"))
            BrokerService.setModel(request) {S ->
                ODLogger.logInfo("COMPLETE", actions = *arrayOf("MODEL_ID=${request?.id}"))
                genericComplete(S, responseObserver)
            }
        }

        override fun getSchedulers(request: Empty?, responseObserver: StreamObserver<ODProto.Schedulers>?) {
            ODLogger.logInfo("INIT")
            BrokerService.getSchedulers {S ->
                ODLogger.logInfo("COMPLETE")
                genericComplete(S, responseObserver)
            }
        }

        override fun setScheduler(request: ODProto.Scheduler?, responseObserver: StreamObserver<ODProto.Status>?) {
            ODLogger.logInfo("INIT", actions = *arrayOf("SCHEDULER_ID=${request?.id}"))
            BrokerService.setScheduler(request) {S ->
                ODLogger.logInfo("COMPLETE", actions = *arrayOf("SCHEDULER_ID=${request?.id}"))
                genericComplete(S, responseObserver)
            }
        }

        override fun listenMulticast(request: BoolValue?, responseObserver: StreamObserver<ODProto.Status>?) {
            BrokerService.listenMulticast(request?.value ?: false)
            genericComplete(genStatus(ODProto.StatusCode.Success), responseObserver)
        }

        override fun announceMulticast(request: Empty?, responseObserver: StreamObserver<ODProto.Status>?) {
            BrokerService.announceMulticast()
            genericComplete(genStatus(ODProto.StatusCode.Success), responseObserver)
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
            BrokerService.stopService { S ->
                genericComplete(S, responseObserver)
                BrokerService.stopServer()
            }
        }

        override fun createJob(request: ODProto.String?, responseObserver: StreamObserver<ODProto.Results>?) {
            val reqId = request?.str ?: ""
            ODLogger.logInfo("INIT", actions = *arrayOf("REQUEST_ID=$reqId"))
            if (reqId.contains(".mp4")) {
                ODLogger.logInfo("EXTRACTING_FRAMES", actions = *arrayOf("INIT", " REQUEST_TYPE=VIDEO", "REQUEST_ID=$reqId"))
                BrokerService.extractVideoFrames(reqId)
                ODLogger.logInfo("EXTRACTING_FRAMES", actions = *arrayOf("COMPLETE", "REQUEST_TYPE=VIDEO", "REQUEST_ID=$reqId"))
            } else {
                ODLogger.logInfo("SUBMITTING_JOB", actions = *arrayOf("REQUEST_TYPE=IMAGE", "REQUEST_ID=$reqId"))
                scheduleJob(Job(BrokerService.getByteArrayFromId(reqId)
                        ?: ByteArray(0)).getProto(), responseObserver)
                ODLogger.logInfo("JOB_SUBMITTED", actions = *arrayOf("REQUEST_TYPE=IMAGE", "REQUEST_ID=$reqId"))
            }
            ODLogger.logInfo("COMPLETE", actions = *arrayOf("REQUEST_ID=$reqId"))
        }

        override fun setSettings(request: ODProto.Settings?, responseObserver: StreamObserver<ODProto.Status>?) {
            if (request == null) genericComplete(genStatus(ODProto.StatusCode.Error), responseObserver)
            try {
                val settingsMap = request!!.settingMap
                settingsMap.forEach { (K, V) ->
                    when (K) {
                        "CLOUD_IP" -> {
                            V.split("/").forEach { ip -> BrokerService.addCloud(ip) }
                        }
                        "GRPC_MAX_MESSAGE_SIZE" -> ODSettings.GRPC_MAX_MESSAGE_SIZE = V.toInt() //: Int = 150000000
                        "RTTHistorySize" -> ODSettings.RTTHistorySize = V.toInt() //: Int = 5
                        "pingTimeout" -> ODSettings.pingTimeout = V.toLong() //: Long = 10000L // 15s
                        "RTTDelayMillis" -> ODSettings.RTTDelayMillis = V.toLong() //: Long = 10000L // 10s
                        "PING_PAYLOAD_SIZE" -> ODSettings.PING_PAYLOAD_SIZE = V.toInt() //: Int = 32000 // 32Kb
                        "averageComputationTimesToStore" -> ODSettings.averageComputationTimesToStore = V.toInt() //: Int = 10
                        "workingThreads" -> ODSettings.workingThreads = V.toInt() //: Int = 1
                        "workerStatusUpdateInterval" -> ODSettings.workerStatusUpdateInterval = V.toLong() //: Long = 5000 // 5s
                        "AUTO_STATUS_UPDATE_INTERVAL_MS" -> ODSettings.AUTO_STATUS_UPDATE_INTERVAL_MS = V.toLong() //: Long = 5000 // 5s
                        "RTTDelayMillisFailRetry" -> ODSettings.RTTDelayMillisFailRetry = V.toLong() //: Long = 500 // 0.5s
                        "RTTDelayMillisFailAttempts" -> ODSettings.RTTDelayMillisFailAttempts = V.toLong() //: Long = 5
                        "DEVICE_ID" -> ODSettings.DEVICE_ID = V
                        "BANDWIDTH_ESTIMATE_TYPE" -> ODSettings.BANDWIDTH_ESTIMATE_TYPE = V
                        "MCAST_INTERFACE" -> ODSettings.MCAST_INTERFACE = V
                        "BANDWIDTH_ESTIMATE_CALC_METHOD" -> {
                            if (V.toLowerCase() in arrayOf("mean", "median")) ODSettings.BANDWIDTH_ESTIMATE_CALC_METHOD = V.toLowerCase()
                        }
                    }
                }
                genericComplete(genStatus(ODProto.StatusCode.Success), responseObserver)
            } catch (e: Exception) {
                genericComplete(genStatus(ODProto.StatusCode.Error), responseObserver)
            }
        }
    }
}
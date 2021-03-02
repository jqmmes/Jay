/*
 * Copyright (c) 2021 University of Porto/Faculty of Sciences (UP/FCUP).
 * 
 * Author: Joaquim Silva
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 */

package pt.up.fc.dcc.hyrax.jay.services.broker.grpc

import com.google.protobuf.BoolValue
import com.google.protobuf.ByteString
import com.google.protobuf.Empty
import io.grpc.BindableService
import io.grpc.stub.StreamObserver
import pt.up.fc.dcc.hyrax.jay.grpc.GRPCServerBase
import pt.up.fc.dcc.hyrax.jay.logger.JayLogger
import pt.up.fc.dcc.hyrax.jay.proto.BrokerServiceGrpc
import pt.up.fc.dcc.hyrax.jay.proto.JayProto.String as StringProto
import pt.up.fc.dcc.hyrax.jay.proto.JayProto.Settings as SettingsProto
import pt.up.fc.dcc.hyrax.jay.proto.JayProto.Request as RequestProto
import pt.up.fc.dcc.hyrax.jay.proto.JayProto.WorkerInfo as WorkerInfoProto
import pt.up.fc.dcc.hyrax.jay.proto.JayProto.WorkerTypes as WorkerTypesProto
import pt.up.fc.dcc.hyrax.jay.proto.JayProto.Scheduler as SchedulerProto
import pt.up.fc.dcc.hyrax.jay.proto.JayProto.Schedulers as SchedulersProto
import pt.up.fc.dcc.hyrax.jay.proto.JayProto.ServiceStatus as ServiceStatusProto
import pt.up.fc.dcc.hyrax.jay.proto.JayProto.TaskExecutor as TaskExecutorProto
import pt.up.fc.dcc.hyrax.jay.proto.JayProto.TaskExecutors as TaskExecutorsProto
import pt.up.fc.dcc.hyrax.jay.proto.JayProto.TaskStream as TaskStreamProto
import pt.up.fc.dcc.hyrax.jay.proto.JayProto.TaskAllocationNotification as TaskAllocationNotificationProto
import pt.up.fc.dcc.hyrax.jay.proto.JayProto.PowerEstimations as PowerEstimationsProto
import pt.up.fc.dcc.hyrax.jay.proto.JayProto.CurrentEstimations as CurrentEstimationsProto
import pt.up.fc.dcc.hyrax.jay.proto.JayProto.BandwidthEstimate as BandwidthEstimateProto
import pt.up.fc.dcc.hyrax.jay.proto.JayProto.Ping as PingProto
import pt.up.fc.dcc.hyrax.jay.proto.JayProto.Status as StatusProto
import pt.up.fc.dcc.hyrax.jay.proto.JayProto.StatusCode as StatusCodeProto
import pt.up.fc.dcc.hyrax.jay.proto.JayProto.Response as ResponseProto
import pt.up.fc.dcc.hyrax.jay.proto.JayProto.Task as TaskProto
import pt.up.fc.dcc.hyrax.jay.services.broker.BrokerService
import pt.up.fc.dcc.hyrax.jay.services.profiler.status.jay.JayState
import pt.up.fc.dcc.hyrax.jay.utils.JaySettings
import pt.up.fc.dcc.hyrax.jay.utils.JayUtils.genStatus
import java.util.*
import java.util.concurrent.CountDownLatch

internal class BrokerGRPCServer(useNettyServer: Boolean = false) : GRPCServerBase(JaySettings.BROKER_PORT, useNettyServer) {

    override val grpcImpl: BindableService = object : BrokerServiceGrpc.BrokerServiceImplBase() {

        inner class TaskResponseObserver(val responseObserver: StreamObserver<ResponseProto>?) :
                StreamObserver<TaskStreamProto> {

            private var isLocalTransfer = false

            override fun onNext(stream: TaskStreamProto?) {
                if (stream == null) {
                    return genErrorResponse(responseObserver)
                }
                JayLogger.logInfo("RECEIVED_TASK", stream.task.info.id ?: "", "ACTION=${stream.status.name}")
                when (stream.status) {
                    TaskStreamProto.Action.BEGIN_TRANSFER -> {
                        if (stream.localStream) isLocalTransfer = true else BrokerService.profiler.setState(JayState.DATA_RCV)
                        responseObserver?.onNext(ResponseProto.newBuilder()
                                .setStatus(StatusProto.newBuilder().setCode(StatusCodeProto.Ready)).build())
                    }
                    TaskStreamProto.Action.TRANSFER -> {
                        if (!isLocalTransfer) BrokerService.profiler.unSetState(JayState.DATA_RCV)
                        responseObserver?.onNext(ResponseProto.newBuilder()
                                .setStatus(StatusProto.newBuilder().setCode(StatusCodeProto.Received)).build())
                        val waitCountdownLatch = CountDownLatch(1)
                        BrokerService.executeTask(stream.task) { R ->
                            responseObserver?.onNext(R)
                            waitCountdownLatch.countDown()
                        }
                        waitCountdownLatch.await()
                    }
                    TaskStreamProto.Action.END_TRANSFER -> {
                        responseObserver?.onNext(ResponseProto.newBuilder()
                                .setStatus(StatusProto.newBuilder().setCode(StatusCodeProto.End)).build())
                    }
                    else -> genErrorResponse(responseObserver)
                }
            }

            override fun onError(t: Throwable?) {
                JayLogger.logError("Error on BrokerGRPCServer::executeTask")
            }

            override fun onCompleted() {
                JayLogger.logInfo("COMPLETE")
                responseObserver?.onCompleted()
            }
        }

        override fun executeTask(responseObserver: StreamObserver<ResponseProto>?): StreamObserver<TaskStreamProto> {
            return TaskResponseObserver(responseObserver)
        }

        override fun scheduleTask(taskProto: TaskProto?, responseObserver: StreamObserver<ResponseProto>?) {
            JayLogger.logInfo("BROKER_SCHEDULE_TASK", taskProto?.info?.id ?: "", "DATA_SIZE=${taskProto?.data?.size()}")
            BrokerService.scheduleTask(taskProto) { R -> genericComplete(R, responseObserver) }
        }

        override fun calibrateWorker(request: TaskProto?, responseObserver: StreamObserver<Empty>?) {
            BrokerService.calibrateWorker(request) { genericComplete(Empty.getDefaultInstance(), responseObserver) }
        }

        override fun ping(request: PingProto, responseObserver: StreamObserver<PingProto>?) {
            try {
                if (request.reply) return genericComplete(request, responseObserver)
            } catch (ignore: Exception) {
            }
            genericComplete(PingProto.newBuilder().setData(ByteString.copyFrom(ByteArray(0))).build(), responseObserver)
        }

        override fun notifySchedulerForAvailableWorkers(request: Empty?, responseObserver: StreamObserver<Empty>?) {
            BrokerService.notifySchedulerForAvailableWorkers()
            genericComplete(request, responseObserver)
        }

        override fun getSchedulers(request: Empty?, responseObserver: StreamObserver<SchedulersProto>?) {
            JayLogger.logInfo("INIT")
            BrokerService.getSchedulers { S ->
                JayLogger.logInfo("COMPLETE")
                genericComplete(S, responseObserver)
            }
        }

        override fun setScheduler(request: SchedulerProto?, responseObserver: StreamObserver<StatusProto>?) {
            JayLogger.logInfo("INIT", actions = arrayOf("SCHEDULER_ID=${request?.id}"))
            BrokerService.setScheduler(request) { S ->
                JayLogger.logInfo("COMPLETE", actions = arrayOf("SCHEDULER_ID=${request?.id}"))
                genericComplete(S, responseObserver)
            }
        }

        override fun listenMulticast(request: BoolValue?, responseObserver: StreamObserver<StatusProto>?) {
            BrokerService.listenMulticast(request?.value ?: false)
            genericComplete(genStatus(StatusCodeProto.Success), responseObserver)
        }

        override fun requestWorkerStatus(request: Empty?, responseObserver: StreamObserver<WorkerInfoProto>?) {
            genericComplete(BrokerService.requestWorkerStatus(), responseObserver)
        }

        override fun enableHearBeats(request: WorkerTypesProto?, responseObserver: StreamObserver<StatusProto>?) {
            genericComplete(BrokerService.enableHearBeats(request), responseObserver)
        }

        override fun enableBandwidthEstimates(request: BandwidthEstimateProto?, responseObserver: StreamObserver<StatusProto>?) {
            genericComplete(BrokerService.enableBandwidthEstimates(request), responseObserver)
        }

        override fun disableHearBeats(request: Empty?, responseObserver: StreamObserver<StatusProto>?) {
            genericComplete(BrokerService.disableHearBeats(), responseObserver)
        }

        override fun disableBandwidthEstimates(request: Empty?, responseObserver: StreamObserver<StatusProto>?) {
            genericComplete(BrokerService.disableBandwidthEstimates(), responseObserver)
        }

        override fun enableWorkerStatusAdvertisement(request: Empty?, responseObserver: StreamObserver<StatusProto>?) {
            genericComplete(BrokerService.enableWorkerStatusAdvertisement(), responseObserver)
        }

        override fun disableWorkerStatusAdvertisement(request: Empty?, responseObserver: StreamObserver<StatusProto>?) {
            genericComplete(BrokerService.disableWorkerStatusAdvertisement(), responseObserver)
        }

        override fun announceServiceStatus(request: ServiceStatusProto?, responseObserver: StreamObserver<StatusProto>?) {
            BrokerService.serviceStatusUpdate(request) { S -> genericComplete(S, responseObserver) }
        }

        override fun getExpectedCurrent(request: Empty?, responseObserver: StreamObserver<CurrentEstimationsProto>?) {
            genericComplete(BrokerService.profiler.getExpectedCurrent(), responseObserver)
        }

        override fun getExpectedCurrentFromRemote(request: WorkerInfoProto?, responseObserver: StreamObserver<CurrentEstimationsProto>?) {
            BrokerService.getExpectedCurrentFromRemote(request?.id) { S -> genericComplete(S, responseObserver) }
        }

        override fun getExpectedPower(request: Empty?, responseObserver: StreamObserver<PowerEstimationsProto>?) {
            genericComplete(BrokerService.profiler.getExpectedPower(), responseObserver)
        }

        override fun getExpectedPowerFromRemote(request: WorkerInfoProto?, responseObserver: StreamObserver<PowerEstimationsProto>?) {
            BrokerService.getExpectedPowerFromRemote(request?.id) { S -> genericComplete(S, responseObserver) }
        }

        override fun stopService(request: Empty?, responseObserver: StreamObserver<StatusProto>?) {
            BrokerService.stopService { S ->
                genericComplete(S, responseObserver)
                BrokerService.stopServer()
            }
        }

        /*override fun createTask(request: JayProto.TaskInfo?, responseObserver: StreamObserver<JayProto.Response>?) {
            val reqId = request?.path ?: ""
            JayLogger.logInfo("INIT", actions = arrayOf("REQUEST_ID=$reqId"))
            if (reqId.contains(".mp4")) {
                JayLogger.logInfo("EXTRACTING_FRAMES", actions = arrayOf("INIT", " REQUEST_TYPE=VIDEO", "REQUEST_ID=$reqId"))
                BrokerService.extractVideoFrames(reqId)
                JayLogger.logInfo("EXTRACTING_FRAMES", actions = arrayOf("COMPLETE", "REQUEST_TYPE=VIDEO", "REQUEST_ID=$reqId"))
            } else {
                JayLogger.logInfo("SUBMITTING_TASK", actions = arrayOf("REQUEST_TYPE=IMAGE", "REQUEST_ID=$reqId",
                        "DEADLINE=${request?.deadline}"))
                scheduleTask(Task(reqId, BrokerService.getFileSizeFromId(reqId) ?: 0, request?.deadline).getProto(),
                        responseObserver)
                JayLogger.logInfo("TASK_SUBMITTED", actions = arrayOf("REQUEST_TYPE=IMAGE", "REQUEST_ID=$reqId"))
            }
            JayLogger.logInfo("COMPLETE", actions = arrayOf("REQUEST_ID=$reqId"))
        }*/

        override fun callExecutorAction(request: RequestProto?, responseObserver: StreamObserver<ResponseProto>?) {
            BrokerService.callExecutorAction(request) { CR -> genericComplete(CR, responseObserver) }
        }

        override fun listTaskExecutors(request: Empty?, responseObserver: StreamObserver<TaskExecutorsProto>?) {
            BrokerService.listTaskExecutors { TE ->
                genericComplete(TE, responseObserver)
            }
        }

        override fun runExecutorAction(request: RequestProto?, responseObserver: StreamObserver<StatusProto>?) {
            BrokerService.runExecutorAction(request) { S -> genericComplete(S, responseObserver) }
        }

        override fun selectTaskExecutor(request: TaskExecutorProto?, responseObserver: StreamObserver<StatusProto>?) {
            BrokerService.selectTaskExecutor(request) { S -> genericComplete(S, responseObserver) }
        }

        override fun setExecutorSettings(request: SettingsProto?, responseObserver: StreamObserver<StatusProto>?) {
            BrokerService.setExecutorSettings(request) { S -> genericComplete(S, responseObserver) }
        }

        override fun setSchedulerSettings(request: SettingsProto?, responseObserver: StreamObserver<StatusProto>?) {
            BrokerService.setSchedulerSettings(request) { S -> genericComplete(S, responseObserver) }
        }

        override fun setSettings(request: SettingsProto?, responseObserver: StreamObserver<StatusProto>?) {
            JayLogger.logInfo("INIT")
            if (request == null) genericComplete(genStatus(StatusCodeProto.Error), responseObserver)
            try {
                val settingsMap = request!!.settingMap
                settingsMap.forEach { (K, V) ->
                    JayLogger.logInfo("SET_SETTING", "", "SETTING[$K]=$V")
                    when (K) {
                        "CLOUD_IP" -> {
                            V.split("/").forEach { ip -> BrokerService.addCloud(ip) }
                        }
                        "GRPC_MAX_MESSAGE_SIZE" -> JaySettings.GRPC_MAX_MESSAGE_SIZE = V.toInt() //: Int = 150000000
                        "RTT_HISTORY_SIZE" -> JaySettings.RTT_HISTORY_SIZE = V.toInt() //: Int = 5
                        "PING_TIMEOUT" -> JaySettings.PING_TIMEOUT = V.toLong() //: Long = 10000L // 15s
                        "RTT_DELAY_MILLIS" -> JaySettings.RTT_DELAY_MILLIS = V.toLong() //: Long = 10000L // 10s
                        "PING_PAYLOAD_SIZE" -> JaySettings.PING_PAYLOAD_SIZE = V.toInt() //: Int = 32000 // 32Kb
                        "AVERAGE_COMPUTATION_TIME_TO_SCORE" -> JaySettings.AVERAGE_COMPUTATION_TIME_TO_SCORE = V.toInt() //: Int = 10
                        "WORKING_THREADS" -> JaySettings.WORKING_THREADS = V.toInt() //: Int = 1
                        "WORKER_STATUS_UPDATE_INTERVAL" -> JaySettings.WORKER_STATUS_UPDATE_INTERVAL = V.toLong() //: Long = 5000 // 5s
                        "RTT_DELAY_MILLIS_FAIL_RETRY" -> JaySettings.RTT_DELAY_MILLIS_FAIL_RETRY = V.toLong() //: Long = 500 // 0.5s
                        "RTT_DELAY_MILLIS_FAIL_ATTEMPTS" -> JaySettings.RTT_DELAY_MILLIS_FAIL_ATTEMPTS = V.toLong() //: Long = 5
                        "READ_SERVICE_DATA_INTERVAL" -> JaySettings.READ_SERVICE_DATA_INTERVAL = V.toLong()
                        "DEVICE_ID" -> JaySettings.DEVICE_ID = V
                        "BANDWIDTH_ESTIMATE_TYPE" -> JaySettings.BANDWIDTH_ESTIMATE_TYPE = V
                        "BANDWIDTH_SCALING_FACTOR" -> JaySettings.BANDWIDTH_SCALING_FACTOR = V.toFloatOrNull() ?: 1.0f
                        "MULTICAST_INTERFACE" -> JaySettings.MULTICAST_INTERFACE = V
                        "MULTICAST_PKT_INTERVAL" -> JaySettings.MULTICAST_PKT_INTERVAL = V.toLong()
                        "BANDWIDTH_ESTIMATE_CALC_METHOD" -> {
                            if (V.toLowerCase(Locale.getDefault()) in arrayOf("mean", "median")) JaySettings.BANDWIDTH_ESTIMATE_CALC_METHOD = V.toLowerCase(Locale.getDefault())
                        }
                        "ADVERTISE_WORKER_STATUS" -> if (V.toLowerCase(Locale.getDefault()) != "false") JaySettings.ADVERTISE_WORKER_STATUS = true
                        "SINGLE_REMOTE_IP" -> JaySettings.SINGLE_REMOTE_IP = V
                        "READ_RECORDED_PROFILE_DATA" -> if (V.toLowerCase(Locale.getDefault()) != "true") JaySettings.READ_RECORDED_PROFILE_DATA = false
                        "COMPUTATION_BASELINE_DURATION_FLAG" -> if (V.toLowerCase(Locale.getDefault()) == "true") JaySettings.COMPUTATION_BASELINE_DURATION_FLAG = true
                        "COMPUTATION_BASELINE_DURATION" -> JaySettings.COMPUTATION_BASELINE_DURATION = try {
                            V.toLong()
                        } catch (ignore: Exception) {
                            10000
                        }
                        "TRANSFER_BASELINE_FLAG" -> if (V.toLowerCase(Locale.getDefault()) == "true") JaySettings.TRANSFER_BASELINE_FLAG = true
                        "TRANSFER_BASELINE_DURATION" -> JaySettings.TRANSFER_BASELINE_DURATION = try {
                            V.toLong()
                        } catch (ignore: Exception) {
                            10000
                        }
                        "USE_FIXED_POWER_ESTIMATIONS" -> if (V.toLowerCase(Locale.getDefault()) == "true") JaySettings.USE_FIXED_POWER_ESTIMATIONS = true
                        "TASK_DEADLINE_BROKEN_SELECTION" -> JaySettings.TASK_DEADLINE_BROKEN_SELECTION = V
                        "INCLUDE_IDLE_COSTS" -> JaySettings.INCLUDE_IDLE_COSTS = (V.toLowerCase(Locale.getDefault())
                                == "true")
                        "USE_CPU_ESTIMATIONS" -> if (V.toLowerCase(Locale.getDefault()) != "true") JaySettings.USE_CPU_ESTIMATIONS = false
                        "DEADLINE_CHECK_TOLERANCE" -> JaySettings.DEADLINE_CHECK_TOLERANCE = V.toInt()
                    }
                }
                genericComplete(genStatus(StatusCodeProto.Success), responseObserver)
            } catch (e: Exception) {
                genericComplete(genStatus(StatusCodeProto.Error), responseObserver)
            }
            JayLogger.logInfo("COMPLETE")
        }

        override fun networkBenchmark(request: TaskProto?, responseObserver: StreamObserver<Empty>?) {
            JayLogger.logInfo("RECEIVED_TASK_BENCHMARK", request?.info?.id ?: "")
            genericComplete(Empty.getDefaultInstance(), responseObserver)
        }

        override fun notifyAllocatedTask(request: TaskAllocationNotificationProto?, responseObserver: StreamObserver<StatusProto>?) {
            BrokerService.notifyAllocatedTask(request!!) {
                genericComplete(it, responseObserver)
            }
        }

        override fun informAllocatedTask(request: StringProto?, responseObserver: StreamObserver<StatusProto>?) {
            BrokerService.worker.informAllocatedTask(request) {
                genericComplete(it, responseObserver)
            }
        }
    }

    private fun genErrorResponse(responseObserver: StreamObserver<ResponseProto>?) {
        responseObserver?.onNext(ResponseProto.newBuilder().setStatus(StatusProto.newBuilder().setCode(StatusCodeProto.Error)).build())
    }
}
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

package pt.up.fc.dcc.hyrax.jay.services.worker.grpc

import com.google.protobuf.ByteString
import com.google.protobuf.Empty
import io.grpc.BindableService
import io.grpc.stub.StreamObserver
import pt.up.fc.dcc.hyrax.jay.grpc.GRPCServerBase
import pt.up.fc.dcc.hyrax.jay.logger.JayLogger
import pt.up.fc.dcc.hyrax.jay.proto.JayProto
import pt.up.fc.dcc.hyrax.jay.proto.WorkerServiceGrpc
import pt.up.fc.dcc.hyrax.jay.services.worker.WorkerService
import pt.up.fc.dcc.hyrax.jay.utils.JaySettings
import pt.up.fc.dcc.hyrax.jay.utils.JayUtils

internal class WorkerGRPCServer(useNettyServer: Boolean = false) : GRPCServerBase(JaySettings.WORKER_PORT, useNettyServer) {

    override val grpcImpl: BindableService = object : WorkerServiceGrpc.WorkerServiceImplBase() {

        override fun execute(request: JayProto.TaskInfo?, responseObserver: StreamObserver<JayProto.Response>?) {
            JayLogger.logInfo("INIT", request?.id ?: "")
            WorkerService.queueTask(request!!) { detectionList ->
                JayLogger.logInfo("COMPLETE", request.id ?: "")
                genericComplete(JayUtils.genResponse(request.id, detectionList as ByteString), responseObserver)
            }
        }

        override fun testService(request: Empty?, responseObserver: StreamObserver<JayProto.ServiceStatus>?) {
            genericComplete(JayProto.ServiceStatus.newBuilder().setType(JayProto.ServiceStatus.Type.WORKER).setRunning(WorkerService.isRunning()).build(), responseObserver)
        }

        override fun stopService(request: Empty?, responseObserver: StreamObserver<JayProto.Status>?) {
            JayLogger.logInfo("INIT")
            WorkerService.stopService { S ->
                genericComplete(S, responseObserver)
                WorkerService.stopServer()
                JayLogger.logInfo("COMPLETE")
            }
        }

        override fun callExecutorAction(request: JayProto.Request?, responseObserver: StreamObserver<JayProto.Response>?) {
            JayLogger.logInfo("INIT")
            if (request == null) genericComplete(JayProto.Response.newBuilder().setStatus(JayUtils.genStatusError()).build(), responseObserver)
            WorkerService.callExecutorAction(request!!.request, { Status, Response ->
                val callResponse = JayProto.Response.newBuilder()
                callResponse.status = Status
                if (Response != null) callResponse.bytes = ByteString.copyFrom((Response as ByteArray))
                else callResponse.bytes = ByteString.EMPTY
                genericComplete(callResponse.build(), responseObserver)
                JayLogger.logInfo("COMPLETE")
            }, *request.argsList.toTypedArray())
        }

        override fun runExecutorAction(request: JayProto.Request?, responseObserver: StreamObserver<JayProto.Status>?) {
            JayLogger.logInfo("INIT")
            if (request == null) genericComplete(JayUtils.genStatusError(), responseObserver)
            WorkerService.runExecutorAction(request!!.request,
                    { S -> genericComplete(S, responseObserver); JayLogger.logInfo("COMPLETE") },
                    *request.argsList.toTypedArray())
    }

        override fun listTaskExecutors(request: Empty?, responseObserver: StreamObserver<JayProto.TaskExecutors>?) {
            JayLogger.logInfo("INIT")
            val taskExecutorsProtoBuilder = JayProto.TaskExecutors.newBuilder()
            WorkerService.listTaskExecutors().forEach { taskExecutor ->
                val taskExecutorProto = JayProto.TaskExecutor.newBuilder()
                taskExecutorProto.id = taskExecutor.id
                taskExecutorProto.description = taskExecutor.description ?: ""
                taskExecutorProto.name = taskExecutor.name
                taskExecutorsProtoBuilder.addTaskExecutors(taskExecutorProto.build())
            }
            genericComplete(taskExecutorsProtoBuilder.build(), responseObserver)
            JayLogger.logInfo("COMPLETE")
        }

        override fun selectTaskExecutor(request: JayProto.TaskExecutor?, responseObserver: StreamObserver<JayProto.Status>?) {
            JayLogger.logInfo("INIT")
            if (request == null) genericComplete(JayUtils.genStatusError(), responseObserver)
            else WorkerService.selectTaskExecutor(request.id) { S -> genericComplete(S, responseObserver) }
            JayLogger.logInfo("COMPLETE")
        }

        override fun setExecutorSettings(request: JayProto.Settings?, responseObserver: StreamObserver<JayProto.Status>?) {
            JayLogger.logInfo("INIT")
            if (request == null) genericComplete(JayUtils.genStatusError(), responseObserver)
            else WorkerService.setExecutorSettings(request.settingMap) { S -> genericComplete(S, responseObserver) }
            JayLogger.logInfo("COMPLETE")
        }

        override fun getWorkerStatus(request: Empty?, responseObserver: StreamObserver<JayProto.WorkerComputeStatus>?) {
            genericComplete(WorkerService.getWorkerStatus(), responseObserver)
        }

        override fun informAllocatedTask(request: JayProto.String, responseObserver: StreamObserver<JayProto.Status>?) {
            genericComplete(WorkerService.informAllocatedTask(request.str), responseObserver)
        }
    }
}
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

package pt.up.fc.dcc.hyrax.jay.services.scheduler.grpc

import com.google.protobuf.Empty
import io.grpc.BindableService
import io.grpc.stub.StreamObserver
import pt.up.fc.dcc.hyrax.jay.grpc.GRPCServerBase
import pt.up.fc.dcc.hyrax.jay.logger.JayLogger
import pt.up.fc.dcc.hyrax.jay.proto.JayProto
import pt.up.fc.dcc.hyrax.jay.proto.SchedulerServiceGrpc
import pt.up.fc.dcc.hyrax.jay.services.scheduler.SchedulerService
import pt.up.fc.dcc.hyrax.jay.services.scheduler.schedulers.SchedulerManager
import pt.up.fc.dcc.hyrax.jay.utils.JaySettings
import pt.up.fc.dcc.hyrax.jay.utils.JayUtils

internal class SchedulerGRPCServer(useNettyServer: Boolean = false) : GRPCServerBase(JaySettings.SCHEDULER_PORT,
        useNettyServer) {

    override val grpcImpl: BindableService = object : SchedulerServiceGrpc.SchedulerServiceImplBase() {

        override fun schedule(request: JayProto.TaskInfo?, responseObserver: StreamObserver<JayProto.WorkerInfo>?) {
            val worker = SchedulerService.schedule(request)
            genericComplete(worker, responseObserver)
        }

        override fun notifyTaskComplete(request: JayProto.TaskInfo?, responseObserver: StreamObserver<Empty>?) {
            SchedulerService.notifyTaskComplete(request?.id)
            genericComplete(Empty.getDefaultInstance(), responseObserver)
        }

        override fun notifyWorkerUpdate(request: JayProto.WorkerInfo?, responseObserver: StreamObserver<JayProto.Status>?) {
            genericComplete(JayUtils.genStatus(SchedulerService.notifyWorkerUpdate(request)), responseObserver)
        }

        override fun notifyWorkerFailure(request: JayProto.WorkerInfo?, responseObserver: StreamObserver<JayProto.Status>?) {
            genericComplete(JayUtils.genStatus(SchedulerService.notifyWorkerFailure(request)), responseObserver)
        }

        override fun listSchedulers(request: Empty?, responseObserver: StreamObserver<JayProto.Schedulers>?) {
            JayLogger.logInfo("INIT")
            genericComplete(SchedulerManager.listSchedulers(), responseObserver)
            JayLogger.logInfo("COMPLETE")
        }

        override fun setScheduler(request: JayProto.Scheduler?, responseObserver: StreamObserver<JayProto.Status>?) {
            genericComplete(JayUtils.genStatus(SchedulerManager.setScheduler(request?.id)), responseObserver)
        }

        override fun setSchedulerSettings(request: JayProto.Settings?, responseObserver: StreamObserver<JayProto.Status>?) {
            JayLogger.logInfo("INIT")
            if (request == null) genericComplete(JayUtils.genStatusError(), responseObserver)
            else SchedulerService.setSchedulerSettings(request.settingMap) { S -> genericComplete(S, responseObserver) }
            JayLogger.logInfo("COMPLETE")
        }

        override fun testService(request: Empty?, responseObserver: StreamObserver<JayProto.ServiceStatus>?) {
            genericComplete(JayProto.ServiceStatus.newBuilder().setType(JayProto.ServiceStatus.Type.SCHEDULER)
                    .setRunning(SchedulerService.isRunning()).build(), responseObserver)
        }

        override fun stopService(request: Empty?, responseObserver: StreamObserver<JayProto.Status>?) {
            SchedulerService.stopService { S ->
                genericComplete(S, responseObserver)
                SchedulerService.stopServer()
            }
        }
    }
}
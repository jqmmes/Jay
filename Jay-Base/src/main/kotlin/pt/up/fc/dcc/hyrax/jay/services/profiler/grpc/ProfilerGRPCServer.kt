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

package pt.up.fc.dcc.hyrax.jay.services.profiler.grpc

import com.google.protobuf.Empty
import io.grpc.BindableService
import io.grpc.stub.StreamObserver
import pt.up.fc.dcc.hyrax.jay.grpc.GRPCServerBase
import pt.up.fc.dcc.hyrax.jay.proto.JayProto
import pt.up.fc.dcc.hyrax.jay.proto.ProfilerServiceGrpc
import pt.up.fc.dcc.hyrax.jay.services.profiler.ProfilerService
import pt.up.fc.dcc.hyrax.jay.services.profiler.ProfilerService.getSystemProfile
import pt.up.fc.dcc.hyrax.jay.services.profiler.status.jay.JayStateManager
import pt.up.fc.dcc.hyrax.jay.utils.JaySettings
import pt.up.fc.dcc.hyrax.jay.utils.JayUtils
import pt.up.fc.dcc.hyrax.jay.services.profiler.ProfilerService.startRecording as ProfilerStartRecording
import pt.up.fc.dcc.hyrax.jay.services.profiler.ProfilerService.stopRecording as ProfilerStopRecording

class ProfilerGRPCServer(useNettyServer: Boolean = false) : GRPCServerBase(JaySettings.PROFILER_PORT, useNettyServer) {
    override val grpcImpl: BindableService = object : ProfilerServiceGrpc.ProfilerServiceImplBase() {

        override fun getDeviceStatus(request: Empty, responseObserver: StreamObserver<JayProto.ProfileRecording>?) {
            genericComplete(getSystemProfile(), responseObserver)
        }

        override fun setState(request: JayProto.JayState?, responseObserver: StreamObserver<JayProto.Status>?) {
            val jayState = JayUtils.genJayState(request)
            if (jayState == null)
                genericComplete(JayUtils.genStatusError(), responseObserver)
            else {
                JayStateManager.setState(jayState)
                genericComplete(JayUtils.genStatusSuccess(), responseObserver)
            }
        }

        override fun startRecording(request: Empty?, responseObserver: StreamObserver<JayProto.Status>?) {
            genericComplete(JayUtils.genStatus(ProfilerStartRecording()), responseObserver)
        }

        override fun stopRecording(request: Empty?, responseObserver: StreamObserver<JayProto.ProfileRecordings>?) {
            genericComplete(ProfilerStopRecording(), responseObserver)
        }

        override fun unSetState(request: JayProto.JayState?, responseObserver: StreamObserver<JayProto.Status>?) {
            val jayState = JayUtils.genJayState(request)
            if (jayState == null)
                genericComplete(JayUtils.genStatusError(), responseObserver)
            else {
                JayStateManager.unsetState(jayState)
                genericComplete(JayUtils.genStatusSuccess(), responseObserver)
            }
        }

        override fun stopService(request: Empty?, responseObserver: StreamObserver<JayProto.Status>?) {
            genericComplete(ProfilerService.stop(), responseObserver)
        }

        override fun testService(request: Empty?, responseObserver: StreamObserver<JayProto.ServiceStatus>?) {
            genericComplete(JayProto.ServiceStatus.newBuilder().setType(JayProto.ServiceStatus.Type.PROFILER).setRunning(true).build(), responseObserver)
        }

        override fun getExpectedCurrent(request: Empty?, responseObserver: StreamObserver<JayProto.CurrentEstimations>?) {
            genericComplete(ProfilerService.getExpectedCurrents(), responseObserver)
        }

        override fun getExpectedPower(request: Empty?, responseObserver: StreamObserver<JayProto.PowerEstimations>?) {
            genericComplete(ProfilerService.getExpectedPowers(), responseObserver)
        }
    }
}

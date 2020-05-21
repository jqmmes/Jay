package pt.up.fc.dcc.hyrax.jay.services.profiler.grpc

import com.google.protobuf.Empty
import io.grpc.BindableService
import io.grpc.stub.StreamObserver
import pt.up.fc.dcc.hyrax.jay.grpc.GRPCServerBase
import pt.up.fc.dcc.hyrax.jay.proto.JayProto
import pt.up.fc.dcc.hyrax.jay.proto.ProfilerServiceGrpc
import pt.up.fc.dcc.hyrax.jay.services.profiler.ProfilerService.getSystemProfile
import pt.up.fc.dcc.hyrax.jay.utils.JaySettings
import pt.up.fc.dcc.hyrax.jay.services.profiler.ProfilerService.startRecording as ProfilerStartRecording
import pt.up.fc.dcc.hyrax.jay.services.profiler.ProfilerService.stopRecording as ProfilerStopRecording

class ProfilerGRPCServer(useNettyServer: Boolean = false) : GRPCServerBase(JaySettings.PROFILER_PORT, useNettyServer) {
    override val grpcImpl: BindableService = object : ProfilerServiceGrpc.ProfilerServiceImplBase() {

        override fun getDeviceStatus(request: Empty, responseObserver: StreamObserver<JayProto.ProfileRecording>?) {
            super.getDeviceStatus(request, responseObserver)
            getSystemProfile()
        }

        override fun setState(request: JayProto.JayState?, responseObserver: StreamObserver<JayProto.Status>?) {
            super.setState(request, responseObserver)
        }

        override fun startRecording(request: Empty?, responseObserver: StreamObserver<JayProto.Status>?) {
            super.startRecording(request, responseObserver)
            ProfilerStartRecording()
        }

        override fun stopRecording(request: Empty?, responseObserver: StreamObserver<JayProto.ProfileRecordings>?) {
            super.stopRecording(request, responseObserver)
            ProfilerStopRecording()
        }

        override fun unSetState(request: JayProto.JayState?, responseObserver: StreamObserver<JayProto.Status>?) {
            super.unSetState(request, responseObserver)
        }

        override fun stopService(request: Empty?, responseObserver: StreamObserver<JayProto.Status>?) {
            super.stopService(request, responseObserver)
        }

        override fun testService(request: Empty?, responseObserver: StreamObserver<JayProto.ServiceStatus>?) {
            genericComplete(JayProto.ServiceStatus.newBuilder().setType(JayProto.ServiceStatus.Type.PROFILER).setRunning(true).build(), responseObserver)
        }
    }
}

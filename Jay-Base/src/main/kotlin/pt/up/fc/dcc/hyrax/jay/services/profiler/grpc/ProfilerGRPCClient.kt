package pt.up.fc.dcc.hyrax.jay.services.profiler.grpc

import com.google.protobuf.Empty
import io.grpc.ConnectivityState
import pt.up.fc.dcc.hyrax.jay.AbstractJay
import pt.up.fc.dcc.hyrax.jay.grpc.GRPCClientBase
import pt.up.fc.dcc.hyrax.jay.proto.JayProto
import pt.up.fc.dcc.hyrax.jay.proto.ProfilerServiceGrpc
import pt.up.fc.dcc.hyrax.jay.services.profiler.status.jay.JayState
import pt.up.fc.dcc.hyrax.jay.utils.JaySettings
import pt.up.fc.dcc.hyrax.jay.utils.JayUtils.genStatusError
import java.util.concurrent.TimeUnit

@Suppress("DuplicatedCode")
class ProfilerGRPCClient(private val host: String) : GRPCClientBase<ProfilerServiceGrpc.ProfilerServiceBlockingStub,
        ProfilerServiceGrpc.ProfilerServiceFutureStub>(host, JaySettings.PROFILER_PORT) {
    override var blockingStub: ProfilerServiceGrpc.ProfilerServiceBlockingStub = ProfilerServiceGrpc.newBlockingStub(channel)
    override var futureStub: ProfilerServiceGrpc.ProfilerServiceFutureStub = ProfilerServiceGrpc.newFutureStub(channel)
    private var asyncStub: ProfilerServiceGrpc.ProfilerServiceStub = ProfilerServiceGrpc.newStub(channel)

    private fun checkConnection() {
        if (this.port != JaySettings.PROFILER_PORT) {
            reconnectChannel(host, JaySettings.PROFILER_PORT)
        }
    }

    override fun reconnectStubs() {
        blockingStub = ProfilerServiceGrpc.newBlockingStub(channel)
        futureStub = ProfilerServiceGrpc.newFutureStub(channel)
        asyncStub = ProfilerServiceGrpc.newStub(channel)
    }

    fun setState(state: JayState): JayProto.Status? {
        checkConnection()
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        return try {
            blockingStub.withDeadlineAfter(JaySettings.BLOCKING_STUB_DEADLINE, TimeUnit.MILLISECONDS)
                    .setState(JayProto.JayState.newBuilder().setJayStateValue(state.ordinal).build())
        } catch (ignore: Exception) {
            genStatusError()
        }
    }

    fun unSetState(state: JayState): JayProto.Status? {
        checkConnection()
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        return try {
            blockingStub.withDeadlineAfter(JaySettings.BLOCKING_STUB_DEADLINE, TimeUnit.MILLISECONDS)
                    .unSetState(JayProto.JayState.newBuilder().setJayStateValue(state.ordinal).build())
        } catch (ignore: Exception) {
            genStatusError()
        }
    }

    fun startRecording(): JayProto.Status? {
        checkConnection()
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        return try {
            blockingStub.withDeadlineAfter(JaySettings.BLOCKING_STUB_DEADLINE, TimeUnit.MILLISECONDS)
                    .startRecording(Empty.getDefaultInstance())
        } catch (ignore: Exception) {
            genStatusError()
        }
    }

    fun stopRecording(): JayProto.ProfileRecordings? {
        checkConnection()
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        return try {
            blockingStub.withDeadlineAfter(JaySettings.BLOCKING_STUB_DEADLINE, TimeUnit.MILLISECONDS)
                    .stopRecording(Empty.getDefaultInstance())
        } catch (ignore: Exception) {
            null
        }
    }

    fun getDeviceStatus(): JayProto.ProfileRecording? {
        checkConnection()
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        return try {
            blockingStub.withDeadlineAfter(JaySettings.BLOCKING_STUB_DEADLINE, TimeUnit.MILLISECONDS)
                    .getDeviceStatus(Empty.getDefaultInstance())
        } catch (ignore: Exception) {
            null
        }
    }

    fun testService(serviceStatus: ((JayProto.ServiceStatus?) -> Unit)) {
        checkConnection()
        if (channel.getState(true) != ConnectivityState.READY) serviceStatus(null)
        val call = futureStub.testService(Empty.getDefaultInstance())
        call.addListener(Runnable {
            try {
                serviceStatus(call.get())
            } catch (e: Exception) {
                serviceStatus(null)
            }
        }, AbstractJay.executorPool)
    }

    fun stopService(): JayProto.Status? {
        checkConnection()
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        return try {
            blockingStub.withDeadlineAfter(JaySettings.BLOCKING_STUB_DEADLINE * 2, TimeUnit.MILLISECONDS)
                    .stopService(Empty.getDefaultInstance())
        } catch (ignore: Exception) {
            genStatusError()
        }
    }

    fun getExpectedCurrent(): JayProto.CurrentEstimations? {
        checkConnection()
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        return try {
            blockingStub.withDeadlineAfter(JaySettings.BLOCKING_STUB_DEADLINE, TimeUnit.MILLISECONDS)
                    .getExpectedCurrent(Empty.getDefaultInstance())
        } catch (ignore: Exception) {
            JayProto.CurrentEstimations.getDefaultInstance()
        }
    }

    fun getExpectedPower(): JayProto.PowerEstimations? {
        checkConnection()
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        return try {
            blockingStub.withDeadlineAfter(JaySettings.BLOCKING_STUB_DEADLINE, TimeUnit.MILLISECONDS)
                    .getExpectedPower(Empty.getDefaultInstance())
        } catch (ignore: Exception) {
            JayProto.PowerEstimations.getDefaultInstance()
        }
    }
}
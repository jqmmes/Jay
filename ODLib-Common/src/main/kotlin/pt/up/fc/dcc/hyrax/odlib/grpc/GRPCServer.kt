package pt.up.fc.dcc.hyrax.odlib.grpc

import com.google.protobuf.Empty
import io.grpc.Context
import io.grpc.Server
import io.grpc.ServerBuilder
import io.grpc.netty.NettyServerBuilder
import io.grpc.stub.StreamObserver
import pt.up.fc.dcc.hyrax.odlib.clients.ClientManager
import pt.up.fc.dcc.hyrax.odlib.enums.ReturnStatus
import pt.up.fc.dcc.hyrax.odlib.interfaces.JobResultCallback
import pt.up.fc.dcc.hyrax.odlib.protoc.ODCommunicationGrpc
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import pt.up.fc.dcc.hyrax.odlib.scheduler.Scheduler
import pt.up.fc.dcc.hyrax.odlib.services.ODComputingService
import pt.up.fc.dcc.hyrax.odlib.status.StatusManager
import pt.up.fc.dcc.hyrax.odlib.utils.ODDetection
import pt.up.fc.dcc.hyrax.odlib.utils.ODLogger
import pt.up.fc.dcc.hyrax.odlib.utils.ODSettings
import pt.up.fc.dcc.hyrax.odlib.utils.ODUtils
import java.io.IOException

/**
 * Server that manages startup/shutdown of a `Greeter` server.
 *
 * Note: this file was automatically converted from Java
 */
internal class GRPCServer(private val port: Int = ODSettings.serverPort, private val useNettyServer : Boolean = false) {

    private var server: Server? = null


    @Throws(IOException::class)
    fun start() : GRPCServer{
        ODLogger.logInfo("will start server on port $port")
        server = if (useNettyServer){
            NettyServerBuilder.forPort(port)
                    .addService(ODCommunicationImpl())
                    .maxInboundMessageSize(ODSettings.grpcMaxMessageSize)
                    .build()
                    .start()
        } else {
            ServerBuilder.forPort(port)
                    .addService(ODCommunicationImpl())
                    .maxInboundMessageSize(ODSettings.grpcMaxMessageSize)
                    .build()
                    .start()
        }
        Runtime.getRuntime().addShutdownHook(object : Thread() {
            override fun run() {
                ODLogger.logError("*** shutting down gRPC server since JVM is shutting down")
                this@GRPCServer.stop()
                ODLogger.logError("*** server shut down")
            }
        })
        return this
    }

    fun stop() {
        server?.shutdown()
    }

    /**
     * Await termination on the main thread since the grpc library uses daemon threads.
     */
    @Throws(InterruptedException::class)
    private fun blockUntilShutdown() {
        server?.awaitTermination()
    }

    companion object {
        private val resultCallbacks = HashMap<Long, (List<ODDetection?>) -> Unit>()

        internal fun addAsyncResultsCallback(id: Long, callback: (List<ODDetection?>) -> Unit) {
            resultCallbacks[id] = callback
        }

        internal fun removeAsyncResultsCallback(id: Long) {
            if (resultCallbacks.containsKey(id)) resultCallbacks.remove(id)
        }

        fun startServer(port : Int) : GRPCServer {
            val server = GRPCServer(port)
            server.start()
            server.blockUntilShutdown()
            return server
        }
    }

    private fun <T>genericComplete (request: T, responseObserver: StreamObserver<T>) {
        if (!Context.current().isCancelled) {
            responseObserver.onNext(request)
            responseObserver.onCompleted()
        } else {
            ODLogger.logError("GRPCServer context canceled")
        }
    }

    inner class ODCommunicationImpl : ODCommunicationGrpc.ODCommunicationImplBase() {

        override fun sayHello(request: pt.up.fc.dcc.hyrax.odlib.protoc.ODProto.RemoteClient?, responseObserver: StreamObserver<pt.up.fc.dcc.hyrax.odlib.protoc.ODProto.Status>?) {
            //ODLogger.logInfo("Received seyHello")
            ClientManager.addOrIgnoreClient(request!!.address, request.port)
            genericComplete(ODUtils.genStatus(ReturnStatus.Success), responseObserver!!)
        }

        // Just send to odService and return
        override fun putJobAsync(req: ODProto.AsyncRequest?, responseObserver: StreamObserver<ODProto.Status>) {
            //ODLogger.logInfo("Received putJobAsync")
            ODComputingService.putJob(
                    ODUtils.parseAsyncRequestImageByteArray(req),
                    { results -> ODUtils.parseAsyncRequestRemoteClient(req)!!.putResults(req!!.job.id, results) },
                    req!!.job.id
            )

            genericComplete(ODUtils.genStatus(ReturnStatus.Success), responseObserver)
        }

        // Just send to odService and return
        override fun putResultAsync(request: ODProto.JobResults?, responseObserver: StreamObserver<ODProto.Status>) {
            //ODLogger.logInfo("Received putResultAsync")
            if (resultCallbacks.containsKey(request!!.id)) {
                resultCallbacks[request.id]!!(ODUtils.parseResults(request))
                removeAsyncResultsCallback(request.id)
            } else {
                Scheduler.addResults(request.id, ODUtils.parseResults(request))
            }
            genericComplete(ODUtils.genStatus(ReturnStatus.Success), responseObserver)
        }

        // Wait for an answer and send it back
        override fun putJobSync(request: ODProto.Job?, responseObserver: StreamObserver<ODProto.JobResults>) {
            //ODLogger.logInfo("Received putJobSync")
            class ResultCallback(override var id: Long) : JobResultCallback {
                override fun onNewResult(resultList: List<ODDetection?>) {
                    genericComplete(ODUtils.genResults(id, resultList), responseObserver)
                }
            }
            ODComputingService.putJob(request!!.data.toByteArray(), ResultCallback(request.id)::onNewResult, request!!.id)
        }

        override fun listModels (request: Empty, responseObserver: StreamObserver<ODProto.Models>) {
            //ODLogger.logInfo("Received listModels")
            genericComplete(ODUtils.genModels(ODComputingService.listModels()), responseObserver)
        }

        override fun selectModel (request: ODProto.Model?, responseObserver: StreamObserver<ODProto.Status>) {
            //ODLogger.logInfo("Received selectModel")
            ODComputingService.loadModel(ODUtils.parseModel(request))
            genericComplete(ODUtils.genStatus(ReturnStatus.Success), responseObserver)
        }

        override fun configModel (request: ODProto.ModelConfig?, responseObserver: StreamObserver<ODProto.Status>) {
            //ODLogger.logInfo("Received configModel")
            val configRequest = ODUtils.parseModelConfig(request)
            ODComputingService.configTFModel(configRequest)
            genericComplete(ODUtils.genStatus(ReturnStatus.Success), responseObserver)
        }

        override fun ping (request: Empty, responseObserver: StreamObserver<Empty>) {
            //ODLogger.logInfo("Received ping")
            genericComplete(Empty.newBuilder().build(), responseObserver)
        }

        override fun getStatus(request: Empty, responseObserver: StreamObserver<ODProto.DeviceStatus>) {
            //ODLogger.logInfo("Received getStatus")
            genericComplete(ODUtils.genDeviceStatus(StatusManager.getDeviceInformation()), responseObserver)
        }
    }
}

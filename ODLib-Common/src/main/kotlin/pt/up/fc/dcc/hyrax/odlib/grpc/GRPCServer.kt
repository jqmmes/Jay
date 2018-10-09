package pt.up.fc.dcc.hyrax.odlib.grpc

import com.google.protobuf.Empty
import io.grpc.Server
import io.grpc.ServerBuilder
import io.grpc.netty.NettyServerBuilder
import io.grpc.stub.StreamObserver
import pt.up.fc.dcc.hyrax.odlib.*
import pt.up.fc.dcc.hyrax.odlib.interfaces.RemoteODCallback
import pt.up.fc.dcc.hyrax.odlib.enums.ReturnStatus
import pt.up.fc.dcc.hyrax.odlib.protoc.ODCommunicationGrpc
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import java.io.IOException

/**
 * Server that manages startup/shutdown of a `Greeter` server.
 *
 * Note: this file was automatically converted from Java
 */
internal class GRPCServer(var odLib: AbstractODLib, private val port: Int = 50051, private val useNettyServer : Boolean = false) {

    private var server: Server? = null


    @Throws(IOException::class)
    fun start() : GRPCServer{
        ODLogger.logInfo("will start server on port $port")
        server = if (useNettyServer){
            NettyServerBuilder.forPort(port)
                    .addService(ODCommunicationImpl())
                    .build()
                    .start()
        } else {
            ServerBuilder.forPort(port)
                    .addService(ODCommunicationImpl())
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
        fun startServer(odLib: AbstractODLib, port : Int) : GRPCServer {
            val server = GRPCServer(odLib, port)
            server.start()
            server.blockUntilShutdown()
            return server
        }
    }

    private fun <T>genericComplete (request: T, responseObserver: StreamObserver<T>) {
        responseObserver.onNext(request)
        responseObserver.onCompleted()
    }

    inner class ODCommunicationImpl : ODCommunicationGrpc.ODCommunicationImplBase() {

        override fun sayHello(request: pt.up.fc.dcc.hyrax.odlib.protoc.ODProto.RemoteClient?, responseObserver: StreamObserver<pt.up.fc.dcc.hyrax.odlib.protoc.ODProto.Status>?) {
            ODLogger.logInfo("Received ${object{}.javaClass.enclosingMethod.name}")
            AbstractODLib.addRemoteClient(RemoteODClient(request!!.address, request.port))
            genericComplete(ODUtils.genStatus(ReturnStatus.Success), responseObserver!!)
        }

        // Just send to odService and return
        override fun putJobAsync(req: ODProto.AsyncRequest?, responseObserver: StreamObserver<ODProto.Status>) {
            ODLogger.logInfo("Received ${object{}.javaClass.enclosingMethod.name}")
            ODService.putJob(ODUtils.parseAsyncRequestImageByteArray(req)) { results-> ODUtils.parseAsyncRequestRemoteClient(req)!!.putResults(req!!.image.id, results)}
            genericComplete(ODUtils.genStatus(ReturnStatus.Success), responseObserver)
        }

        // Just send to odService and return
        override fun putResultAsync(request: ODProto.Results?, responseObserver: StreamObserver<ODProto.Status>) {
            ODLogger.logInfo("Received ${object{}.javaClass.enclosingMethod.name}")
            ODService.newRemoteResultAvailable(request!!.id, ODUtils.parseResults(request))
            genericComplete(ODUtils.genStatus(ReturnStatus.Success), responseObserver)
        }

        // Wait for an answer and send it back
        override fun putJobSync(request: ODProto.Image?, responseObserver: StreamObserver<ODProto.Results>) {
            ODLogger.logInfo("Received ${object{}.javaClass.enclosingMethod.name}")
            class ResultCallback(override var id: Int) : RemoteODCallback {
                override fun onNewResult(resultList: List<ODUtils.ODDetection?>) {
                    genericComplete(ODUtils.genResults(id, resultList), responseObserver)
                }
            }
            ODService.putJob(request!!.data.toByteArray(), ResultCallback(request.id)::onNewResult)
        }

        override fun listModels (request: Empty, responseObserver: StreamObserver<ODProto.Models>) {
            ODLogger.logInfo("Received ${object{}.javaClass.enclosingMethod.name}")
            genericComplete(ODUtils.genModels(odLib.listModels()), responseObserver)
        }

        override fun selectModel (request: ODProto.Model?, responseObserver: StreamObserver<ODProto.Status>) {
            ODLogger.logInfo("Received ${object{}.javaClass.enclosingMethod.name}")
            ODUtils.parseModel(request)
        }

        override fun configModel (request: ODProto.ModelConfig?, responseObserver: StreamObserver<ODProto.Status>) {
            ODLogger.logInfo("Received ${object{}.javaClass.enclosingMethod.name}")
            ODUtils.parseModelConfig(request)
        }

        override fun ping (request: Empty, responseObserver: StreamObserver<Empty>) {
            ODLogger.logInfo("Received ${object{}.javaClass.enclosingMethod.name}")
            genericComplete(Empty.newBuilder().build(), responseObserver)
        }
    }
}

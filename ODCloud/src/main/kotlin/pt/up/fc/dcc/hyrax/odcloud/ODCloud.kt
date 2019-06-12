package pt.up.fc.dcc.hyrax.odcloud


import io.grpc.Server
import io.grpc.ServerBuilder
import io.grpc.stub.StreamObserver
import pt.up.fc.dcc.hyrax.odlib.ODLib
import pt.up.fc.dcc.hyrax.odlib.logger.LogLevel
import pt.up.fc.dcc.hyrax.odlib.logger.ODLogger
import pt.up.fc.dcc.hyrax.odcloud.protoc.LauncherServiceGrpc
import pt.up.fc.dcc.hyrax.odcloud.protoc.ODCloudGRPC
import pt.up.fc.dcc.hyrax.odlib.interfaces.LogInterface
import pt.up.fc.dcc.hyrax.odlib.utils.ODSettings
import java.io.File
import java.io.FileOutputStream
import java.lang.Thread.sleep
import java.net.InetAddress
import kotlin.concurrent.thread

class ODCloud {
    companion object {
        private var odLib = ODLib()
        private var cloudlet = false
        private val hostname = InetAddress.getLocalHost().hostName
        private var server: Server? = null
        private var running = false

        @JvmStatic
        fun main(args: Array<String>) {
            if (args.isNotEmpty()) {
                if (args[0] == "cloudlet") {
                    cloudlet = true
                }
            }

            Runtime.getRuntime().addShutdownHook(object : Thread() {
                override fun run() {
                    ODLogger.logError("ERROR", actions = *arrayOf("ERROR='*** shutting down server since JVM is shutting down'"))
                    odLib.destroy()
                    stopNowAndWait()
                    running = false
                    ODLogger.logError("ERROR", actions = *arrayOf("ERROR='*** server shut down'"))
                }
            })

            startServer()

            while (true) {
                sleep(1000)
            }
        }


        private fun startServer() {
            server = ServerBuilder.forPort(50000)
                    .addService(GrpcImpl(odLib))
                    .maxInboundMessageSize(100)
                    .build()
                    .start()
        }

        private fun stopNowAndWait() {
            server?.shutdownNow()
            server?.awaitTermination()
        }

        internal class GrpcImpl(private val odClient: ODLib) : LauncherServiceGrpc.LauncherServiceImplBase() {

            private var logName = "logs"
            private var logging = false

            override fun setLogName(request: ODCloudGRPC.String?, responseObserver: StreamObserver<ODCloudGRPC.BoolValue>?) {
                logName = request?.str ?: "logs"
                genericComplete(ODCloudGRPC.BoolValue.newBuilder().setValue(true).build(), responseObserver)
            }

            override fun startWorker(request: ODCloudGRPC.Empty?, responseObserver: StreamObserver<ODCloudGRPC.BoolValue>?) {
                running = true
                enableLogs()
                thread(start = true) {
                    odClient.startWorker()
                    do ( sleep(1) ) while (running)
                }
                genericComplete(ODCloudGRPC.BoolValue.newBuilder().setValue(true).build(), responseObserver)
            }

            override fun stop(request: ODCloudGRPC.Empty?, responseObserver: StreamObserver<ODCloudGRPC.BoolValue>?) {
                odClient.destroy()
                running = false
                disableLogs()
                genericComplete(ODCloudGRPC.BoolValue.newBuilder().setValue(true).build(), responseObserver)
            }

            private fun enableLogs() {
                if (logging) return
                logging = true

                ODLogger.enableLogs(LoggingInterface(FileOutputStream(File(ODCloud::class.java.protectionDomain.codeSource.location.toURI().path.substringBefore("ODCloud.jar")+"/logs/$logName"), false)), LogLevel.Info)
            }

            private fun disableLogs() {
                if (logging) return
                logging = false
                ODLogger.enableLogs(LoggingInterface(), LogLevel.Disabled)
            }


            private fun <T> genericComplete(request: T?, responseObserver: StreamObserver<T>?) {
                if (!io.grpc.Context.current().isCancelled) {
                    responseObserver!!.onNext(request)
                    responseObserver.onCompleted()
                }
            }
        }
    }

    private class LoggingInterface() : LogInterface {
        private lateinit var logFile: FileOutputStream
        private var logging = false

        constructor(logFile: FileOutputStream) : this(){
            this.logFile = logFile
            logging = true
            this.logFile.write("NODE_NAME,NODE_ID,NODE_TYPE,TIMESTAMP,LOG_LEVEL,CLASS_METHOD_LINE,OPERATION,JOB_ID,ACTIONS\n".toByteArray())
            this.logFile.flush()
        }

        override fun log(id: String, message: String, logLevel: LogLevel, callerInfo: String, timestamp: Long) {
            if (logging) {
                logFile.write("$hostname,$id,${if (cloudlet) "CLOUDLET" else "CLOUD"},$timestamp,${logLevel.name},$callerInfo,$message\n".toByteArray())
                logFile.flush()
            }
        }

        override fun close() {
            if (logging) {
                logFile.flush()
                logFile.close()
            }
            logging = false
        }
    }
}
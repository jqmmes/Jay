package pt.up.fc.dcc.hyrax.jay


import io.grpc.Server
import io.grpc.ServerBuilder
import io.grpc.stub.StreamObserver
import pt.up.fc.dcc.hyrax.jay.interfaces.LogInterface
import pt.up.fc.dcc.hyrax.jay.logger.JayLogger
import pt.up.fc.dcc.hyrax.jay.logger.LogLevel
import pt.up.fc.dcc.hyrax.jay.protoc.LauncherServiceGrpc
import pt.up.fc.dcc.hyrax.jay.protoc.x86JayGRPC
import java.io.File
import java.io.FileOutputStream
import java.lang.Thread.sleep
import java.net.InetAddress
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

class X86JayLauncher {
    companion object {
        private var jay = Jay()
        private var cloudlet = false
        private val hostname = InetAddress.getLocalHost().hostName
        private var server: Server? = null
        private var runningLatch: CountDownLatch? = null

        @JvmStatic
        fun main(args: Array<String>) {
            if (args.isNotEmpty()) {
                if (args[0] == "cloudlet") {
                    cloudlet = true
                }
            }

            Runtime.getRuntime().addShutdownHook(object : Thread() {
                override fun run() {
                    JayLogger.logError("ERROR", actions = arrayOf("ERROR='*** shutting down server since JVM is shutting down'"))
                    jay.destroy()
                    stopNowAndWait()
                    runningLatch?.countDown()
                    JayLogger.logError("ERROR", actions = arrayOf("ERROR='*** server shut down'"))
                }
            })

            startServer()

            while (true) {
                sleep(1000)
            }
        }

        private fun startServer() {
            server = ServerBuilder.forPort(50000)
                    .addService(GrpcImpl(jay))
                    .maxInboundMessageSize(100)
                    .build()
                    .start()
        }

        private fun stopNowAndWait() {
            server?.shutdownNow()
            server?.awaitTermination()
        }

        internal class GrpcImpl(private val jay: Jay) : LauncherServiceGrpc.LauncherServiceImplBase() {

            private var logName = "logs"
            private var logging = false

            override fun setLogName(request: x86JayGRPC.String?, responseObserver: StreamObserver<x86JayGRPC.BoolValue>?) {
                logName = request?.str ?: "logs"
                genericComplete(x86JayGRPC.BoolValue.newBuilder().setValue(true).build(), responseObserver)
            }

            override fun startWorker(request: x86JayGRPC.Empty?, responseObserver: StreamObserver<x86JayGRPC.BoolValue>?) {
                runningLatch = CountDownLatch(1)
                enableLogs()
                thread(start = true) {
                    jay.startProfiler()
                    sleep(500)
                    jay.startWorker()
                    runningLatch?.await()
                }
                genericComplete(x86JayGRPC.BoolValue.newBuilder().setValue(true).build(), responseObserver)
            }

            override fun stop(request: x86JayGRPC.Empty?, responseObserver: StreamObserver<x86JayGRPC.BoolValue>?) {
                jay.destroy()
                runningLatch?.countDown()
                disableLogs()
                genericComplete(x86JayGRPC.BoolValue.newBuilder().setValue(true).build(), responseObserver)
            }

            private fun enableLogs() {
                if (logging) return
                logging = true

                JayLogger.enableLogs(LoggingInterface(FileOutputStream(File(X86JayLauncher::class.java.protectionDomain.codeSource.location.toURI().path.substringBefore("Jay-x86.jar") + "/logs/$logName"), false)), LogLevel.Info)
            }

            private fun disableLogs() {
                if (logging) return
                logging = false
                JayLogger.enableLogs(LoggingInterface(), LogLevel.Disabled)
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
            this.logFile.write(("NODE_NAME,NODE_ID,NODE_TYPE,TIMESTAMP,LOG_LEVEL,CLASS_METHOD_LINE,OPERATION," +
                    "TASK_ID,ACTIONS\n").toByteArray())
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
package pt.up.fc.dcc.hyrax.jay

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.support.v4.app.ActivityCompat
import android.support.v4.app.NotificationCompat
import io.grpc.Server
import io.grpc.netty.NettyServerBuilder
import io.grpc.stub.StreamObserver
import io.netty.util.internal.logging.InternalLoggerFactory
import io.netty.util.internal.logging.JdkLoggerFactory
import pt.up.fc.dcc.hyrax.jay.interfaces.LogInterface
import pt.up.fc.dcc.hyrax.jay.logger.JayLogger
import pt.up.fc.dcc.hyrax.jay.logger.LogLevel
import pt.up.fc.dcc.hyrax.jay.protoc.DroidJayLauncher
import pt.up.fc.dcc.hyrax.jay.protoc.DroidJayLauncher.BoolValue
import pt.up.fc.dcc.hyrax.jay.protoc.DroidJayLauncher.Empty
import pt.up.fc.dcc.hyrax.jay.protoc.LauncherServiceGrpc.LauncherServiceImplBase
import java.io.File
import java.io.FileOutputStream

@Suppress("PrivatePropertyName")
class DroidJayLauncherService : Service() {

    internal class GrpcImpl(private val odClient: Jay, private val context: Context) : LauncherServiceImplBase() {

        private var logName = "logs"
        private var logging = false

        override fun startScheduler(request: Empty?, responseObserver: StreamObserver<BoolValue>?) {
            enableLogs()
            odClient.startScheduler()
            genericComplete(BoolValue.newBuilder().setValue(true).build(), responseObserver)
        }

        override fun startWorker(request: Empty?, responseObserver: StreamObserver<BoolValue>?) {
            enableLogs()
            odClient.startWorker()
            genericComplete(BoolValue.newBuilder().setValue(true).build(), responseObserver)
        }

        override fun setLogName(request: DroidJayLauncher.String?, responseObserver: StreamObserver<BoolValue>?) {
            logName = request?.str ?: "logs"
            genericComplete(BoolValue.newBuilder().setValue(true).build(), responseObserver)
        }

        private fun enableLogs() {
            if (logging) return
            logging = true
            JayLogger.enableLogs(Logs(FileOutputStream(File("${context.getExternalFilesDir(null)}/$logName.csv"), false), context = context), LogLevel.Info)
        }

        private fun <T> genericComplete(request: T?, responseObserver: StreamObserver<T>?) {
            if (!io.grpc.Context.current().isCancelled) {
                responseObserver!!.onNext(request)
                responseObserver.onCompleted()
            }
        }
    }

    @Suppress("DEPRECATION")
    internal class Logs(private val logFile: FileOutputStream, private val context: Context) : LogInterface {

        override fun close() {
            logFile.flush()
            logFile.close()
        }

        init {
            logFile.write("NODE_NAME,NODE_ID,NODE_TYPE,TIMESTAMP,LOG_LEVEL,CLASS_METHOD_LINE,OPERATION,JOB_ID,ACTIONS\n".toByteArray())
            logFile.flush()
        }

        @SuppressLint("HardwareIds")
        override fun log(id: String, message: String, logLevel: LogLevel, callerInfo: String, timestamp: Long) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
                logFile.write("${Build.getSerial()},$id,ANDROID,$timestamp,${logLevel.name},$callerInfo,$message\n".toByteArray())
            } else {
                logFile.write("${Build.SERIAL},$id,ANDROID,$timestamp,${logLevel.name},$callerInfo,$message\n".toByteArray())
            }
            logFile.flush()
        }
    }

    private lateinit var odClient: Jay

    private val CHANNEL_ID = "DroidJayLauncherService"// The id of the channel.
    private val name = "DroidJay Service"// The user-visible name of the channel.
    @Suppress("DEPRECATION")
    private val importance = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        NotificationManager.IMPORTANCE_MIN
    } else {
        Notification.PRIORITY_LOW
    }

    private val GROUP_KEY_JAY_SERVICES = "pt.up.fc.dcc.hyrax.jay.SERVICES"

    private var server: Server? = null


    private fun startServer() {
        InternalLoggerFactory.setDefaultFactory(JdkLoggerFactory.INSTANCE)
        server = NettyServerBuilder.forPort(50000).addService(GrpcImpl(odClient, applicationContext))
                        .maxInboundMessageSize(100)
                        .build()
                        .start()
    }

    private fun stopNowAndWait() {
        server?.shutdownNow()
        server?.awaitTermination()
    }

    /**
     * Await termination on the main thread since the grpc library uses daemon threads.
     */
    @Suppress("unused")
    @Throws(InterruptedException::class)
    private fun blockUntilShutdown() {
        server?.awaitTermination()
    }

    override fun onCreate() {
        super.onCreate()

        val mNotificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mChannel = NotificationChannel(CHANNEL_ID, name, importance)
            mNotificationManager.createNotificationChannel(mChannel)
        }

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_bird_border)
                .setOngoing(true)
                .setPriority(importance)
                .setContentTitle(name)
                .setContentText("Running")
                .setGroup(GROUP_KEY_JAY_SERVICES)
                .build()
        Pair(1, notification)


        startForeground(1, notification)

        odClient = Jay(this)

        startServer()
        // To test only
        //odClient.startWorker()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        odClient.stopScheduler()
        odClient.stopWorker()
        odClient.destroy()
        stopNowAndWait()
        super.onDestroy()
    }
}
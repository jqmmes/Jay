package pt.up.fc.dcc.hyrax.od_launcher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.support.v4.app.NotificationCompat
import io.grpc.Server
import io.grpc.netty.NettyServerBuilder
import io.grpc.stub.StreamObserver
import io.netty.util.internal.logging.InternalLoggerFactory
import io.netty.util.internal.logging.JdkLoggerFactory
import pt.up.fc.dcc.hyrax.od_launcher.protoc.LauncherServiceGrpc.LauncherServiceImplBase
import pt.up.fc.dcc.hyrax.od_launcher.protoc.ODLauncher.Empty
import pt.up.fc.dcc.hyrax.od_launcher.protoc.ODLauncher.BoolValue
import pt.up.fc.dcc.hyrax.odlib.ODLib

class ODLauncherService : Service() {

    internal class GrpcImpl(val odClient: ODLib) : LauncherServiceImplBase() {

        override fun startScheduler(request: Empty?, responseObserver: StreamObserver<BoolValue>?) {
            odClient.startScheduler()
            genericComplete(BoolValue.newBuilder().setValue(true).build(), responseObserver)
        }

        override fun startWorker(request: Empty?, responseObserver: StreamObserver<BoolValue>?) {
            odClient.startWorker()
            genericComplete(BoolValue.newBuilder().setValue(true).build(), responseObserver)
        }

        fun <T> genericComplete(request: T?, responseObserver: StreamObserver<T>?) {
            if (!io.grpc.Context.current().isCancelled) {
                responseObserver!!.onNext(request)
                responseObserver.onCompleted()
            }
        }
    }

    private lateinit var odClient: ODLib


    private val CHANNEL_ID = "ODLauncherService"// The id of the channel.
    private val name = "OD Launcher Service"// The user-visible name of the channel.
    @Suppress("DEPRECATION")
    private val importance = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        NotificationManager.IMPORTANCE_MIN
    } else {
        Notification.PRIORITY_LOW
    }

    private val GROUP_KEY_ODLIB_SERVICES = "pt.up.fc.dcc.hyrax.odlib.SERVICES"

    private var server: Server? = null


    private fun startServer() {
        InternalLoggerFactory.setDefaultFactory(JdkLoggerFactory.INSTANCE)
        server = NettyServerBuilder.forPort(50000).addService(GrpcImpl(odClient))
                        .maxInboundMessageSize(100)
                        .build()
                        .start()
    }

    fun stopServer() {
        server?.shutdown()
    }

    fun stopNowAndWait() {
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
                .setSmallIcon(R.drawable.ic_launcher_background)
                .setOngoing(true)
                .setPriority(importance)
                .setContentTitle(name)
                .setContentText("Running")
                .setGroup(GROUP_KEY_ODLIB_SERVICES)
                .build()
        Pair(1, notification)


        startForeground(1, notification)

        odClient = ODLib(this)

        startServer()

    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopNowAndWait()
    }
}
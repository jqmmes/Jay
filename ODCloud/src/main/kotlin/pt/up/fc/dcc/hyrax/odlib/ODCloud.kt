package pt.up.fc.dcc.hyrax.odlib

import pt.up.fc.dcc.hyrax.odlib.enums.LogLevel
import pt.up.fc.dcc.hyrax.odlib.interfaces.ODLog
import pt.up.fc.dcc.hyrax.odlib.multicast.MulticastAdvertiser
import pt.up.fc.dcc.hyrax.odlib.multicast.MulticastListener
import pt.up.fc.dcc.hyrax.odlib.services.ODComputingService
import pt.up.fc.dcc.hyrax.odlib.status.StatusManager
import pt.up.fc.dcc.hyrax.odlib.utils.ODLogger

class ODCloud {
    companion object {
        private var odLib = ODLib()

        @JvmStatic
        fun main(args: Array<String>) {
            // Benchmark all protocols
            if (args.isNotEmpty()) {
                if (args[0] == "benchmark") {
                    Benchmark.run()
                    return
                }
                if (args[0] == "cloudlet") {
                    MulticastAdvertiser.advertise()
                    MulticastListener.listen()
                }
            }

            ODLogger.enableLogs(LoggingInterface(), LogLevel.Info)
            ODComputingService.setWorkingThreads(StatusManager.cpuDetails.getAvailableCores())
            odLib.startODService()
            odLib.startGRPCServerService()

            Runtime.getRuntime().addShutdownHook(object : Thread() {
                override fun run() {
                    ODLogger.logError("*** shutting down server since JVM is shutting down")
                    odLib.stopODService() // ODComputingService blocks until concluded or calling clean()
                    ODLogger.logError("*** server shut down")
                }
            })

            while (true) {
                Thread.sleep(1000)
            }
        }

        private class LoggingInterface : ODLog {
            override fun log(message: String, logLevel: LogLevel) {
                println("${System.currentTimeMillis()}\t$message")
            }
        }
    }
}
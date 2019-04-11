package pt.up.fc.dcc.hyrax.odcloud


import pt.up.fc.dcc.hyrax.odlib.ODLib
import pt.up.fc.dcc.hyrax.odlib.logger.LogLevel
import pt.up.fc.dcc.hyrax.odlib.interfaces.LogInterface
import pt.up.fc.dcc.hyrax.odlib.logger.ODLogger
import java.lang.Thread.sleep

class ODCloud {
    companion object {
        private var odLib = ODLib()

        @JvmStatic
        fun main(args: Array<String>) {
            // Benchmark
            if (args.isNotEmpty()) {
                var mModel = "all"
                if (args.size > 1) mModel = args[1]
                if (args[0] == "benchmark" || args[0] == "benchmark-small") {
                    Benchmark.run(mModel = mModel)
                    return
                }
                if (args[0] == "benchmark-large") {
                    Benchmark.run("large", mModel)
                    return
                }
                if (args[0] == "cloudlet") {
                    //MulticastAdvertiser.advertise()
                    //MulticastListener.listen()
                }
            }

            ODLogger.enableLogs(LoggingInterface(), LogLevel.Info)
            //WorkerService.setWorkingThreads(StatusManager.cpuDetails.getAvailableCores())
            odLib.startWorker()
            sleep(5000)
            odLib.listModels { ML -> odLib.setModel(ML.first()) }

            Runtime.getRuntime().addShutdownHook(object : Thread() {
                override fun run() {
                    ODLogger.logError("*** shutting down server since JVM is shutting down")
                    odLib.destroy()
                    ODLogger.logError("*** server shut down")
                }
            })

            while (true) {
                Thread.sleep(1000)
            }
        }

        private class LoggingInterface : LogInterface {
            override fun log(message: String, logLevel: LogLevel) {
                println("${System.currentTimeMillis()}\t$message")
            }
        }
    }
}
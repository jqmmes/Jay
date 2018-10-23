package pt.up.fc.dcc.hyrax.odlib

import pt.up.fc.dcc.hyrax.odlib.enums.LogLevel
import pt.up.fc.dcc.hyrax.odlib.interfaces.ODLog
import pt.up.fc.dcc.hyrax.odlib.multicast.MulticastAdvertiser
import pt.up.fc.dcc.hyrax.odlib.multicast.MulticastListener
import pt.up.fc.dcc.hyrax.odlib.services.ODComputingService
import pt.up.fc.dcc.hyrax.odlib.utils.ODLogger
import pt.up.fc.dcc.hyrax.odlib.utils.ODSettings

class ODCloudlet {
    companion object {
        private var odClient = ODLib()

        @JvmStatic
        fun main(args: Array<String>) {
            ODLogger.enableLogs(LoggingInterface(), LogLevel.Info)
            odClient.startODService()
            odClient.startGRPCServerService(odClient)
            odClient.setTFModel(odClient.listModels(false).toList()[0])
            MulticastAdvertiser.advertise()
            MulticastListener.listen()
            Runtime.getRuntime().addShutdownHook(object : Thread() {
                override fun run() {
                    ODLogger.logError("*** shutting down server since JVM is shutting down")
                    odClient.stopODService() // ODComputingService bloqueia até concluido ou fazer um clean()
                    ODLogger.logError("*** server shut down")
                }
            })

            while (true) {
                Thread.sleep(1000)
            }
        }

        private class LoggingInterface : ODLog {
            override fun log(message: String, logLevel: LogLevel) {
                println(message)
            }
        }
    }
}
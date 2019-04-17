@file:Suppress("unused")

package pt.up.fc.dcc.hyrax.odlib.logger

import pt.up.fc.dcc.hyrax.odlib.interfaces.LogInterface
import java.lang.Exception
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread

object ODLogger{
    private lateinit var loggingConsole : LogInterface
    private val LOG_QUEUE : BlockingQueue<Pair<String, LogLevel>> = LinkedBlockingQueue()
    private var running: Boolean = false
    private var logLevel : LogLevel = LogLevel.Disabled
    private val LOCK = Object()

    fun logInfo(message: String) {
        log(message, LogLevel.Info)
    }

    fun logError(message: String) {
        log(message, LogLevel.Error)
    }

    fun logWarn(message: String) {
        log(message, LogLevel.Warn)
    }

    private fun log(message : String, logLevel: LogLevel) {
        if (logLevel <= ODLogger.logLevel) {
            if (running) {
                LOG_QUEUE.offer(message to logLevel)
            } else {
                synchronized(LOCK) {
                    loggingConsole.log(message, logLevel)
                }
            }
        }
    }

    fun enableLogs(loggingInterface : LogInterface, logLevel: LogLevel = LogLevel.Error){
        ODLogger.logLevel = logLevel
        loggingConsole = loggingInterface
    }

    fun setLogLevel(logLevel: LogLevel) {
        ODLogger.logLevel = logLevel
    }

    fun stopBackgroundLoggingService() {
        if (running) {
            running = false
            LOG_QUEUE.put("" to LogLevel.Disabled)
        }
    }

    fun startBackgroundLoggingService() {
        if (running) return
        thread(isDaemon = true, name="ODLogger") {
            running = true
            while (running) {
                try {
                    val log = LOG_QUEUE.take()
                    loggingConsole.log(log.first, log.second)
                } catch (_ : Exception) {
                    running = false
                }
            }
        }
    }
}
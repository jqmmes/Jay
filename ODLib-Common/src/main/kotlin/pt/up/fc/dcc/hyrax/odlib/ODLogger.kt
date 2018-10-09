@file:Suppress("unused")

package pt.up.fc.dcc.hyrax.odlib

import pt.up.fc.dcc.hyrax.odlib.enums.LogLevel
import pt.up.fc.dcc.hyrax.odlib.interfaces.ODLog
import java.lang.Exception
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread

object ODLogger{
    lateinit var loggingConsole : ODLog
    private val logQueue : BlockingQueue<Pair<String, LogLevel>> = LinkedBlockingQueue()
    private var running: Boolean = false
    private var logLevel : LogLevel = LogLevel.Disabled

    fun logInfo(message: String) {
        log(message, LogLevel.Info)
    }

    fun logError(message: String) {
        log(message, LogLevel.Error)
    }

    fun logWarn(message: String) {
        log(message, LogLevel.Warn)
    }

    fun log(message : String, logLevel: LogLevel) {
        if (logLevel <= this.logLevel) {
            if (running) {
                logQueue.offer(message to logLevel)
            } else {
                loggingConsole.log(message, logLevel)
            }
        }
    }

    fun enableLogs(loggingInterface : ODLog, logLevel: LogLevel = LogLevel.Error){
        this.logLevel = logLevel
        loggingConsole = loggingInterface
    }

    fun setLogLevel(logLevel: LogLevel) {
        this.logLevel = logLevel
    }

    fun stopBackgroundLoggingService() {
        if (running) {
            running = false
            logQueue.put("" to LogLevel.Disabled)
        }
    }

    fun startBackgroundLoggingService() {
        if (running) return
        thread(isDaemon = true) {
            running = true
            while (running) {
                try {
                    val log = logQueue.take()
                    loggingConsole.log(log.first, log.second)
                } catch (_ : Exception) {
                    running = false
                }
            }
        }
    }
}
@file:Suppress("unused")

package pt.up.fc.dcc.hyrax.odlib.logger

import pt.up.fc.dcc.hyrax.odlib.interfaces.LogInterface
import pt.up.fc.dcc.hyrax.odlib.utils.ODSettings
import java.lang.Exception
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread

object ODLogger{
    private lateinit var loggingConsole : LogInterface
    private val LOG_QUEUE : BlockingQueue<Pair<String, Pair<Pair<String, String>, Pair<Long, LogLevel>>>> = LinkedBlockingQueue()
    private var running: Boolean = false
    private var logLevel : LogLevel = LogLevel.Disabled
    private val LOCK = Object()
    private const val DELIMITER = ","
    private const val ACTION_DELIMITER = ";"

    private fun buildCallerInfo(stackTrace: Array<StackTraceElement>): String {
        var i = 3
        if (stackTrace[i].className.removePrefix("pt.up.fc.dcc.hyrax.odlib.") == "logger.ODLogger") i++
        return "${stackTrace[i].className.removePrefix("pt.up.fc.dcc.hyrax.odlib.")}_${stackTrace[i].methodName}_${stackTrace[i].lineNumber}"
    }

    private fun buildMessage(operation: String, jobId: String = "", actions: Array<out String>): String {
        var msg = "$operation$DELIMITER$jobId$DELIMITER\""
        for (i in 0 until actions.size) {
            msg+=actions[i] + if (i < actions.size-1) ACTION_DELIMITER else ""
        }
        return msg+"\""
    }

    fun logInfo(operation: String, jobId: String = "", vararg actions: String) {
        log(buildMessage(operation, jobId, actions), LogLevel.Info, buildCallerInfo(Thread.currentThread().stackTrace))
    }

    fun logError(operation: String, jobId: String = "", vararg actions: String) {
        log(buildMessage(operation, jobId, actions), LogLevel.Error, buildCallerInfo(Thread.currentThread().stackTrace))
    }

    fun logWarn(operation: String, jobId: String = "", vararg actions: String) {
        log(buildMessage(operation, jobId, actions), LogLevel.Warn, buildCallerInfo(Thread.currentThread().stackTrace))
    }

    private fun log(message: String, logLevel: LogLevel, callerInfo: String = "") {
        if (logLevel <= ODLogger.logLevel) {
            if (running) {
                LOG_QUEUE.offer(ODSettings.MY_ID to ((message to callerInfo) to (System.currentTimeMillis() to logLevel)))
            } else {
                synchronized(LOCK) {
                    loggingConsole.log(ODSettings.MY_ID, message, logLevel, callerInfo, System.currentTimeMillis())
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
            LOG_QUEUE.put(ODSettings.MY_ID to (("" to "") to (0L to LogLevel.Disabled)))
        }
    }

    fun startBackgroundLoggingService() {
        if (running) return
        thread(isDaemon = true, name="ODLogger") {
            running = true
            while (running) {
                try {
                    val log = LOG_QUEUE.take()
                    loggingConsole.log(log.first, log.second.first.first, log.second.second.second, log.second.first.second, log.second.second.first)
                } catch (_ : Exception) {
                    running = false
                }
            }
        }
    }
}
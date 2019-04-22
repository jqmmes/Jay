package pt.up.fc.dcc.hyrax.odlib.interfaces

import pt.up.fc.dcc.hyrax.odlib.logger.LogLevel

interface LogInterface {
    fun log(message: String, logLevel: LogLevel, callerInfo: String)
    fun close()
}
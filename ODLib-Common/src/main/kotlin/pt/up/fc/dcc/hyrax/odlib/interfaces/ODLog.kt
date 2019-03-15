package pt.up.fc.dcc.hyrax.odlib.interfaces

import pt.up.fc.dcc.hyrax.odlib.logger.LogLevel

interface ODLog {
    fun log(message : String, logLevel : LogLevel)
}
package pt.up.fc.dcc.hyrax.odlib.interfaces

import pt.up.fc.dcc.hyrax.odlib.enums.LogLevel

interface ODLog {
    fun log(message : String, LogLevel : LogLevel)
}
/*
 * Copyright (c) 2021 University of Porto/Faculty of Sciences (UP/FCUP).
 */

package pt.up.fc.dcc.hyrax.jay.interfaces

import pt.up.fc.dcc.hyrax.jay.logger.LogLevel

interface LogInterface {
    fun log(id: String = "", message: String, logLevel: LogLevel, callerInfo: String, timestamp: Long = 0L)
    fun close()
}
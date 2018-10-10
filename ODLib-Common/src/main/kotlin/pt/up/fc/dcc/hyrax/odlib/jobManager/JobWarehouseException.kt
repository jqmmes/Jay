package pt.up.fc.dcc.hyrax.odlib.jobManager

import pt.up.fc.dcc.hyrax.odlib.ODLogger

class JobWarehouseException(s: String) : Exception(s) {
    init {
        ODLogger.logError("Warehouse not created yet.")
    }
}
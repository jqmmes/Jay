/*
 * Copyright (c) 2021 University of Porto/Faculty of Sciences (UP/FCUP).
 */

package pt.up.fc.dcc.hyrax.jay.services.profiler.status.device.transport

interface TransportManager {
    fun getTransport(): TransportInfo
}
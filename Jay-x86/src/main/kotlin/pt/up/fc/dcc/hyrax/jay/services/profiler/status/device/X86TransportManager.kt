/*
 * Copyright (c) 2021 University of Porto/Faculty of Sciences (UP/FCUP).
 */

package pt.up.fc.dcc.hyrax.jay.services.profiler.status.device

import pt.up.fc.dcc.hyrax.jay.services.profiler.status.device.transport.TransportInfo
import pt.up.fc.dcc.hyrax.jay.services.profiler.status.device.transport.TransportManager
import pt.up.fc.dcc.hyrax.jay.services.profiler.status.device.transport.TransportMedium

object X86TransportManager : TransportManager {
    override fun getTransport(): TransportInfo {
        return TransportInfo(TransportMedium.ETHERNET, 100000, 100000, null)
    }
}

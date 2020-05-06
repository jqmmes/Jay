package pt.up.fc.dcc.hyrax.jay.services.worker.status.device.transport

data class TransportInfo(val medium: TransportMedium,
                         val upstreamBandwidth: Int,
                         val downstreamBandwidth: Int,
                         val cellularTechnology: CellularTechnology?)
package pt.up.fc.dcc.hyrax.jay.services.profiler.status.device.transport

data class TransportInfo(val medium: TransportMedium,
                         val upstreamBandwidth: Int,
                         val downstreamBandwidth: Int,
                         val cellularTechnology: CellularTechnology?)
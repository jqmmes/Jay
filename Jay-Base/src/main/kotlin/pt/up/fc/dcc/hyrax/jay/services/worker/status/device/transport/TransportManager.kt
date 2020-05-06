package pt.up.fc.dcc.hyrax.jay.services.worker.status.device.transport

interface TransportManager {
    fun getTransport(): TransportInfo
}
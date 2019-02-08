package pt.up.fc.dcc.hyrax.odlib.clients

import pt.up.fc.dcc.hyrax.odlib.services.broker.grpc.BrokerGRPCClient

class RemoteClient(val id: String, address: String){

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RemoteClient

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    val grpc: BrokerGRPCClient = BrokerGRPCClient(address)

    var rttEstimate: Long = 0L
    var avgComputingEstimate: Long = 0L

    var battery = 100
    var cpuCores = 0
    var queueSize = 1L
    var runningJobs = 0L
}
package pt.up.fc.dcc.hyrax.odlib.services.broker.multicast

import java.io.Serializable
import java.util.*

data class AdvertisingMessage(val msgType: Int, val data: ByteArray) : Serializable {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AdvertisingMessage

        if (msgType != other.msgType) return false
        if (!Arrays.equals(data, other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = msgType
        result = 31 * result + Arrays.hashCode(data)
        return result
    }
}
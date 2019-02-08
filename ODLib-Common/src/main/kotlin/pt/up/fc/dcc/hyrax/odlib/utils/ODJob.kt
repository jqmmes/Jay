package pt.up.fc.dcc.hyrax.odlib.utils

import java.util.*

data class ODJob(val data: ByteArray) {

    val id = UUID.randomUUID().toString()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ODJob

        if (id != other.id) return false
        //if (!Arrays.equals(data, other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        //var result = jobId.hashCode()
        //result = 31 * result + Arrays.hashCode(data)
        return id.hashCode()
    }
}
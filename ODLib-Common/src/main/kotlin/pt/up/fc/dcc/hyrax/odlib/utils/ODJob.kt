package pt.up.fc.dcc.hyrax.odlib.utils

import java.util.*

data class ODJob(private val jobID: Long, private val data: ByteArray) {

    fun getId() : Long{
        return jobID
    }

    fun getData() : ByteArray {
        return data
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ODJob

        if (jobID != other.jobID) return false
        if (!Arrays.equals(data, other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = jobID.hashCode()
        result = 31 * result + Arrays.hashCode(data)
        return result
    }
}
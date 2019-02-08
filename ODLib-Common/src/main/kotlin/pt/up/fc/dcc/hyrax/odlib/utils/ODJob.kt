package pt.up.fc.dcc.hyrax.odlib.utils

import java.util.*

data class ODJob(private val data: ByteArray) {

    private val jobId = UUID.randomUUID().toString()

    fun getId(): String {
        return jobId
    }

    fun getData() : ByteArray {
        return data
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ODJob

        if (jobId != other.jobId) return false
        if (!Arrays.equals(data, other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = jobId.hashCode()
        result = 31 * result + Arrays.hashCode(data)
        return result
    }
}
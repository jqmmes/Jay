package pt.up.fc.dcc.hyrax.odlib.structures

import com.google.protobuf.ByteString
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import java.util.*

class Job {

    val id : String
    val data : ByteArray
    val dataSize : Int

    constructor(jobData: ByteArray) {
        id = UUID.randomUUID().toString()
        data = jobData
        dataSize = data.size
    }

    internal constructor(jobData: ODProto.Job?) {
        id = jobData!!.id
        data = jobData.data.toByteArray()
        dataSize = data.size
    }

    internal constructor(jobData: ODProto.JobDetails?) {
        id = jobData!!.id
        data = ByteArray(0)
        dataSize = jobData.dataSize
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Job

        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    internal fun getProto() : ODProto.Job? {
        return ODProto.Job.newBuilder().setId(id).setData(ByteString.copyFrom(data)).build()
    }
}
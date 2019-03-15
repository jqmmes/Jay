package pt.up.fc.dcc.hyrax.odlib.structures

import com.google.protobuf.ByteString
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import java.util.*

class ODJob {

    val id : String
    val data : ByteArray

    constructor(jobData: ByteArray) {
        id = UUID.randomUUID().toString()
        data = jobData
    }

    internal constructor(jobData: ODProto.Job?) {
        id = jobData!!.id
        data = jobData.data.toByteArray()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ODJob

        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    internal fun getProto() : ODProto.Job? {
        return ODProto.Job.newBuilder().setId(id).setData(ByteString.copyFrom(data)).build()
    }
}
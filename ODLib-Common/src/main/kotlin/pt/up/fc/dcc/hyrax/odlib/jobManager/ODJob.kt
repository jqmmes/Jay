package pt.up.fc.dcc.hyrax.odlib.jobManager

class ODJob(private val jobId: Long, private val data: ByteArray) {

    fun getId() : Long{
        return jobId
    }

    fun getData() : ByteArray {
        return data
    }
}
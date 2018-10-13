package pt.up.fc.dcc.hyrax.odlib.interfaces

import pt.up.fc.dcc.hyrax.odlib.utils.ODUtils
import pt.up.fc.dcc.hyrax.odlib.jobManager.ODJob

interface Scheduler {
    var jobResultCallback: JobResultCallback?

    fun setJobCompleteCallback(callback: JobResultCallback)
    fun jobCompleted(id: Long, results: List<ODUtils.ODDetection?>)
    fun scheduleJob(job: ODJob)
}
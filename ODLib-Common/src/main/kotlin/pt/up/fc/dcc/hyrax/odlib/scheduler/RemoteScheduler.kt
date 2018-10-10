package pt.up.fc.dcc.hyrax.odlib.scheduler

import pt.up.fc.dcc.hyrax.odlib.utils.ODUtils
import pt.up.fc.dcc.hyrax.odlib.jobManager.ODJob

class RemoteScheduler : Scheduler {
    override fun jobCompleted(id: Long, results: List<ODUtils.ODDetection?>) {

    }

    override fun scheduleJob(job: ODJob) {

    }
}
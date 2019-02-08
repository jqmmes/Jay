package pt.up.fc.dcc.hyrax.odlib.services.worker.status.cpu

import pt.up.fc.dcc.hyrax.odlib.services.worker.WorkerService

object CpuDetails {
    private var avgComputationTime : Double = 0.0
    private var avgJobCount: Long = 0
    private val AVG_LOCK = Object()

    fun addComputationTime(duration: Long) {
        synchronized(AVG_LOCK) {
            val w = 1.0/++avgJobCount
            avgComputationTime = avgComputationTime*(1.0-w) + (duration*w)
        }
    }

    fun getAvailableCores() : Int {
        return Runtime.getRuntime().availableProcessors()
    }

    fun getAvgComputationTime() : Double {
        return avgComputationTime
    }

    fun getQueueSize() : Int {
        return WorkerService.getQueueSize()
    }

    fun getWorkingThreads() : Int {
        return WorkerService.getWorkingThreads()
    }

    fun getPendingJobs() : Int {
        return WorkerService.getPendingJobsCount()
    }

    fun getRunningJobs() : Int {
        return WorkerService.getJobsRunningCount()
    }
}
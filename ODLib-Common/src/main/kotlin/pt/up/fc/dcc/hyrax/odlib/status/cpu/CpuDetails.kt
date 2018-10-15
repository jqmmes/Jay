package pt.up.fc.dcc.hyrax.odlib.status.cpu

import pt.up.fc.dcc.hyrax.odlib.services.ODComputingService

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
        return ODComputingService.getQueueSize()
    }

    fun getWorkingThreads() : Int {
        return ODComputingService.getWorkingThreads()
    }

    fun getPendingJobs() : Int {
        return ODComputingService.getPendingJobsCount()
    }

    fun getRunningJobs() : Int {
        return ODComputingService.getJobsRunningCount()
    }
}
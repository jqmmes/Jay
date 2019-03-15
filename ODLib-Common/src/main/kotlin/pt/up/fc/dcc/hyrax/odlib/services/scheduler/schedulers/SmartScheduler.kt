package pt.up.fc.dcc.hyrax.odlib.services.scheduler.schedulers

import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import pt.up.fc.dcc.hyrax.odlib.services.scheduler.SchedulerService
import pt.up.fc.dcc.hyrax.odlib.utils.ODJob
import java.util.concurrent.LinkedBlockingDeque
import kotlin.random.Random

class SmartScheduler : Scheduler("SmartScheduler") {

    private val rankedWorkers = LinkedBlockingDeque<RankedWorker>()

    override fun init() {
        SchedulerService.registerNotifyListener { W ->  rankWorker(W) }
        rankWorkers(SchedulerService.getWorkers().values.toList())
        super.init()
    }

    override fun scheduleJob(job: ODJob): ODProto.Worker? {
        return SchedulerService.getWorker(rankedWorkers.first.id!!)
    }

    override fun destroy() {
        rankedWorkers.clear()

    }

    private fun rankWorkers(workers: List<ODProto.Worker?>) {
        for (worker in workers) rankWorker(worker)
    }

    private fun rankWorker(worker: ODProto.Worker?) {
        if (RankedWorker(id=worker?.id) !in rankedWorkers) {
            rankedWorkers.addLast(RankedWorker(Random.nextFloat(), worker!!.id))
        } else {
            rankedWorkers.elementAt(rankedWorkers.indexOf(RankedWorker(id=worker?.id))).score = Random.nextFloat()
        }
        rankedWorkers.sortedWith(compareBy {it.score})
    }

    private data class RankedWorker(var score: Float = 0.0f, val id : String?) {


        override fun hashCode(): Int {
            return id.hashCode()
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true

            if (javaClass != other?.javaClass) return false

            other as RankedWorker

            if (id != other.id) return false

            return true
        }
    }

}
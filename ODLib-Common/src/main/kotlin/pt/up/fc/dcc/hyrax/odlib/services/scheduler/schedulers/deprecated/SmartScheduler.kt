/*package pt.up.fc.dcc.hyrax.odlib.services.scheduler.schedulers.deprecated


import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import pt.up.fc.dcc.hyrax.odlib.services.broker.multicast.MulticastListener
import pt.up.fc.dcc.hyrax.odlib.services.scheduler.SchedulerService
import pt.up.fc.dcc.hyrax.odlib.services.worker.status.StatusManager
import pt.up.fc.dcc.hyrax.odlib.utils.DeviceInformation
import pt.up.fc.dcc.hyrax.odlib.structures.ODJob
import pt.up.fc.dcc.hyrax.odlib.Logger.ODLogger
import java.lang.Thread.sleep
import java.util.*
import kotlin.collections.HashMap
import kotlin.concurrent.thread


@Suppress("unused")
class SmartScheduler : SchedulerBase("SmartScheduler") {

    private val clientList: MutableList<Pair<Float, String>> = mutableListOf()
    private val jobBooKeeping: HashMap<Long, List<Long>> = HashMap()
    private var running: Boolean = false
    private val sleepDuration: Long = 1000
    @Suppress("PrivatePropertyName")
    private val SORT_LOCK = Object()

    init {
        //RTTServer.startServer(ODSettings.rttPort)
        //smartTimer.schedule(RTTTimer(), rttDelayMillis)
        MulticastListener.listen()
        StatusManager.advertiseStatus()
        //MulticastAdvertiser.advertise()
        //ClientManager.setClientInfoCallback(this)
        reSortClientList(0)
        smartSchedulerUpdaterService()
    }

    private fun smartSchedulerUpdaterService() {
        if (running) return
        thread(isDaemon = true, name = "smartSchedulerUpdaterService") {
            running = true
            while (running) {
                try {
                    //onNewClientStatus(0, ClientManager.getLocalODClient().getDeviceStatus())
                    //onNewClientStatus(ClientManager.getCloudClient().getId(), ClientManager.getCloudClient().getDeviceStatus())
                    sleep(sleepDuration)
                } catch (e: Exception) {
                    ODLogger.logError("Error")
                }
            }
        }
    }

    private fun reSortClientList(clientID: Long) {
        *//*val client = clientList.find { T -> T.second == clientID }
        if (client != null) clientList.removeAt(clientList.indexOf(client))
        clientList.add(Pair(calculateClientScore(clientID), clientID))*//*
        clientList.sortWith(Comparator { lhs, rhs -> java.lang.Float.compare(rhs.first, lhs.first) })
    }

    override fun destroy() {
        smartTimer.cancel()
        running = false
    }

    override fun scheduleJob(job: ODJob) {
        *//*val nextClient = getNextClient()
        ODLogger.logInfo("Job_Scheduled\t${job.id}\t${nextClient.second.getAddress()}\tSMART")
        val startTime = System.currentTimeMillis()
        nextClient.second.asyncDetectObjects(job) {R -> jobCompleted(job.id, R)}
        if (nextClient.first) {
            ClientManager.getRemoteODClient(nextClient.second.getId())!!
                    .getDeviceStatus().networkLatency.addLatency(System.currentTimeMillis() -startTime)
        }*//*
    }

    *//*
    *
    * A -- B
    * x -- C(1.0)
    *
    * Converter x em 0.0-1.0
    *//*
    private fun crossMultiplication(A: Float, B: Float, C: Float = 1.0f): Float {
        return (C*A)/B
    }

    private fun calculateClientScore(clientID: Long): Float {

        //val clientInformation = ClientManager.getRemoteODClient(clientID)!!.getDeviceInformation()
        val clientInformation = DeviceInformation()
        *//*
         * Lower the better >= 0
         *//*
        // Assuming 100ms as top latency, reserve score
        val latency = 1f-crossMultiplication(clientInformation.networkLatency.getAvgLatency().toFloat(), 100f)
        // Assuming a total of 50 jobs as max, reverse score
        //val totalJobs = 1f-crossMultiplication(remoteClients[clientID]!!.pendingJobs.toFloat(), 5f)
        val runningJobs = 1f-crossMultiplication(clientInformation.runningJobs.toFloat(), clientInformation.computationThreads.toFloat())
        //val available spots
        val queueSpace = 1f-crossMultiplication(clientInformation.pendingJobs.toFloat(), clientInformation.queueSize.toFloat())

        *//*
         * Higher the better >= 0
         *//*
        // assuming 100% battery
        val scaledBattery = crossMultiplication(clientInformation.battery.toFloat(), 100f)
        // assuming 8 threads as max
        val scaledCPUs = crossMultiplication(clientInformation.computationThreads.toFloat(), 8f)



        val score = latency*0.20f+runningJobs*0.25f+queueSpace*0.2f+scaledBattery*0.25f+scaledCPUs*0.10f
        ODLogger.logInfo("New Score for $clientID: $score")

        return score
    }

    private fun getNextClient() : Pair<Boolean, ODProto.Worker?> {
        println("${clientList[0].second}\t${clientList[0].first}")
        return Pair(false, SchedulerService.getWorkers()[SchedulerService.getWorkers().keys.first()])
        //return Pair(false, ClientManager.getRemoteODClient(clientList[0].second)!!)
    }

    *//*inner class RTTTimer : TimerTask() {
        override fun run() {
            for (worker in SchedulerService.getWorkers()) {
                RTTClient.measureRTT(client, ODSettings.rttPort)
            }
            smartTimer.schedule(this, rttDelayMillis)
        }
    }*//*

    companion object {
        private val smartTimer: Timer = Timer()
        private const val rttDelayMillis: Long = 15000
    }
}*/
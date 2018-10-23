package pt.up.fc.dcc.hyrax.odlib.scheduler

import pt.up.fc.dcc.hyrax.odlib.clients.ClientManager
import pt.up.fc.dcc.hyrax.odlib.clients.DeviceInformation
import pt.up.fc.dcc.hyrax.odlib.clients.RemoteODClient
import pt.up.fc.dcc.hyrax.odlib.interfaces.ClientInfoInterface
import pt.up.fc.dcc.hyrax.odlib.interfaces.Scheduler
import pt.up.fc.dcc.hyrax.odlib.jobManager.ODJob
import pt.up.fc.dcc.hyrax.odlib.status.StatusManager
import pt.up.fc.dcc.hyrax.odlib.status.network.rtt.RTTClient
import pt.up.fc.dcc.hyrax.odlib.status.network.rtt.RTTServer
import pt.up.fc.dcc.hyrax.odlib.utils.ODLogger
import pt.up.fc.dcc.hyrax.odlib.utils.ODSettings
import pt.up.fc.dcc.hyrax.odlib.utils.ODUtils
import java.util.*
import kotlin.collections.HashMap

// TODO: Sort Next device
// TODO: Add knobs to tune sorting
@Suppress("unused")
class SmartScheduler : Scheduler(), ClientInfoInterface {

    private val remoteClients: HashMap<Long, DeviceInformation> = HashMap()
    private val clientList: MutableList<Pair<Float, Long>> = mutableListOf()

    init {
        //RTTServer.startServer(ODSettings.rttPort)
        //smartTimer.schedule(RTTTimer(), rttDelayMillis)
        StatusManager.advertiseStatus()
        ClientManager.setClientInfoCallback(this)
        for (client in ClientManager.getRemoteODClients(false)!!.toList()){
            if (!remoteClients.containsKey(client.getId())) { remoteClients[client.getId()] = DeviceInformation() }
        }
    }

    override fun onNewClientStatus(clientID: Long, information: DeviceInformation) {
        if (information == remoteClients[clientID]) return
        remoteClients[clientID] = information
        val client = clientList.find { T -> T.second == clientID }
        if (client != null) clientList.removeAt(clientList.indexOf(client))
        clientList.add(Pair(calculateClientScore(clientID), clientID))
        clientList.sortWith(Comparator{ lhs, rhs -> java.lang.Float.compare(lhs.first, rhs.first) })
    }

    override fun onNewClient(odClient: RemoteODClient) {
        if (!remoteClients.containsKey(odClient.getId())) { remoteClients[odClient.getId()] = DeviceInformation() }
    }

    override fun destroy() {
        smartTimer.cancel()
    }

    override fun scheduleJob(job: ODJob) {
        val nextClient = getNextClient()
        val startTime = System.currentTimeMillis()
        nextClient.second.asyncDetectObjects(job) {R -> jobCompleted(job.getId(), R)}
        if (nextClient.first) {
            remoteClients[nextClient.second.getId()]!!.networkLatency.addLatency(System.currentTimeMillis()-startTime)
        }
    }

    /*
    *
    * A -- B
    * x -- D(1.0)
    *
    * Converter x em 0.0-1.0
    */
    private fun regraTresSimples(A: Float, B: Float, C: Float = 1.0f): Float {
        return (C*A)/B
    }


    private fun calculateClientScore(clientID: Long): Float {
        /*
         * Lower the better >= 0
         */
        // Assuming 100ms as top latency, reserve score
        val latency = 1f-regraTresSimples(remoteClients[clientID]!!.networkLatency.getAvgLatency().toFloat(), 100f)
        // Assuming a total of 50 jobs as max, reverse score
        val totalJobs = 1f-regraTresSimples(remoteClients[clientID]!!.pendingJobs.toFloat(), 50f)

        /*
         * Higher the better >= 0
         */
        // assuming 100% battery
        val scaledBattery = regraTresSimples(remoteClients[clientID]!!.battery.toFloat(), 100f)
        // assuming 8 threads as max
        val scaledCpus = regraTresSimples(remoteClients[clientID]!!.computationThreads.toFloat(), 8f)

        ODLogger.logInfo("New Score for $clientID: ${latency*0.25f+totalJobs*0.25f+scaledBattery*0.25f+scaledCpus*0.25f}")
        return latency*0.25f+totalJobs*0.25f+scaledBattery*0.25f+scaledCpus*0.25f
    }

    private fun getNextClient() : Pair<Boolean, RemoteODClient> {

        return Pair(false, ClientManager.getLocalODClient())
    }

    inner class RTTTimer : TimerTask() {
        override fun run() {
            for (client in ClientManager.getRemoteODClients()!!.toList()) {
                RTTClient.measureRTT(client, ODSettings.rttPort)
            }
            smartTimer.schedule(this, rttDelayMillis)
        }
    }

    companion object {
        private val smartTimer: Timer = Timer()
        private const val rttDelayMillis: Long = 15000
    }
}
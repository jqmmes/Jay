package pt.up.fc.dcc.hyrax.odlib.scheduler

import pt.up.fc.dcc.hyrax.odlib.clients.ClientManager
import pt.up.fc.dcc.hyrax.odlib.clients.RemoteODClient
import pt.up.fc.dcc.hyrax.odlib.interfaces.Scheduler
import pt.up.fc.dcc.hyrax.odlib.jobManager.ODJob
import pt.up.fc.dcc.hyrax.odlib.status.network.rtt.RTTClient
import pt.up.fc.dcc.hyrax.odlib.status.network.rtt.RTTServer
import pt.up.fc.dcc.hyrax.odlib.utils.ODSettings
import java.util.*

// TODO: MCast device information
// TODO: Sort Next device
// TODO: Add knobs to tune sorting
@Suppress("unused")
class SmartScheduler : Scheduler() {

    init {
        RTTServer.startServer(ODSettings.rttPort)
        smartTimer.schedule(RTTTimer(), rttDelayMillis)

    }

    inner class RTTTimer : TimerTask() {
        override fun run() {
            for (client in ClientManager.getRemoteODClients()!!.toList()) {
                RTTClient.measureRTT(client as RemoteODClient, ODSettings.rttPort)
            }
            smartTimer.schedule(this, rttDelayMillis)
        }
    }

    override fun destroy() {
        smartTimer.cancel()
    }

    override fun scheduleJob(job: ODJob) {

    }

    companion object {
        private val smartTimer: Timer = Timer()
        private const val rttDelayMillis: Long = 15000
    }
}
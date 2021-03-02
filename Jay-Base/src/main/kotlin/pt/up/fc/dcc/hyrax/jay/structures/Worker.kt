/*
 * Copyright (c) 2021 University of Porto/Faculty of Sciences (UP/FCUP).
 * 
 * Author: Joaquim Silva
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 */

package pt.up.fc.dcc.hyrax.jay.structures

import io.grpc.ConnectivityState
import org.apache.commons.collections4.queue.CircularFifoQueue
import pt.up.fc.dcc.hyrax.jay.logger.JayLogger
import pt.up.fc.dcc.hyrax.jay.proto.JayProto
import pt.up.fc.dcc.hyrax.jay.services.broker.grpc.BrokerGRPCClient
import pt.up.fc.dcc.hyrax.jay.utils.JaySettings
import pt.up.fc.dcc.hyrax.jay.utils.JaySettings.PING_PAYLOAD_SIZE
import java.lang.Thread.sleep
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class Worker(id: String = UUID.randomUUID().toString(), address: String,
             type: WorkerType = WorkerType.LOCAL,
             checkHeartBeat: Boolean = false, bwEstimates: Boolean = false,
             private var statusChangeCallback: ((WorkerInfo.Status) -> Unit)? = null
) {

    internal val grpc: BrokerGRPCClient = BrokerGRPCClient(address)
    internal val info: WorkerInfo = WorkerInfo(id, type, address, grpc.port)

    private var smartPingScheduler: ScheduledThreadPoolExecutor = ScheduledThreadPoolExecutor(1)
    private var circularFIFO: CircularFifoQueue<Float> = CircularFifoQueue(JaySettings.RTT_HISTORY_SIZE)
    private var circularResultsFIFO: CircularFifoQueue<Long> = CircularFifoQueue(JaySettings.RESULTS_CIRCULAR_FIFO_SIZE)
    private var consecutiveTransientFailurePing = 0
    private var autoStatusUpdateEnabledFlag = false
    private var autoStatusUpdateRunning = CountDownLatch(0)
    private var calcRTT = false
    private var checkingHeartBeat = false

    private val rttLock = Object()
    private val resultsLock = Object()


    constructor(proto: JayProto.WorkerInfo?, type: WorkerType, address: String, checkHearBeat: Boolean,
                bwEstimates: Boolean, statusChangeCallback: ((WorkerInfo.Status) -> Unit)? = null) : this(id=proto!!.id, type=type, address=address) {
        info.update(proto)
        if (checkHearBeat) enableHeartBeat(statusChangeCallback)
        if (bwEstimates && JaySettings.BANDWIDTH_ESTIMATE_TYPE in arrayOf("ACTIVE", "ALL"))
            doActiveRTTEstimates(statusChangeCallback = statusChangeCallback)
    }

    init {
        info.bandwidthEstimate = when (type) {
            WorkerType.LOCAL -> 0f
            WorkerType.REMOTE -> 0.003f
            WorkerType.CLOUD -> 0.1f
        }
        if (checkHeartBeat) enableHeartBeat(statusChangeCallback)
        if (bwEstimates && JaySettings.BANDWIDTH_ESTIMATE_TYPE in arrayOf("ACTIVE", "ALL"))
            doActiveRTTEstimates(statusChangeCallback = statusChangeCallback)
        info.setGPRCPortChangedCb { port -> this.grpc.setNewPort(port) }
        JayLogger.logInfo("INIT", actions = arrayOf("WORKER_ID=$id", "WORKER_TYPE=${type.name}"))
    }

    /**
     * Request Worker Current Status Automatically. When receives the new status, updates this class information
     * Only request worker status when remote worker is online.
     */
    internal fun enableAutoStatusUpdate(updateNotificationCb: (JayProto.WorkerInfo?) -> Unit) {
        if (autoStatusUpdateEnabledFlag) return
        thread {
            autoStatusUpdateEnabledFlag = true
            autoStatusUpdateRunning = CountDownLatch(1)
            var backoffCount = 0
            do {
                if (grpc.channel.getState(true) != ConnectivityState.TRANSIENT_FAILURE) {
                    JayLogger.logInfo("REQUEST_WORKER_STATUS_INIT", actions = arrayOf("WORKER_ID=$info.id", "WORKER_TYPE=${info.type.name}"))
                    // Reduce a little bit the wait time because it takes time to update information and record last
                    if (isOnline() && System.currentTimeMillis() - info.lastStatusUpdateTimestamp >= JaySettings.WORKER_STATUS_UPDATE_INTERVAL * 0.8) {
                        grpc.requestWorkerStatus { W ->
                            JayLogger.logInfo("REQUEST_WORKER_STATUS_ONLINE", actions = arrayOf("WORKER_ID=$info.id", "WORKER_TYPE=${info.type.name}"))
                            info.update(W)
                            updateNotificationCb.invoke(W)
                            JayLogger.logInfo("REQUEST_WORKER_STATUS_COMPLETE", actions = arrayOf("WORKER_ID=$info.id", "WORKER_TYPE=${info.type.name}"))
                        }
                    } else {
                        JayLogger.logInfo("REQUEST_WORKER_STATUS_OFFLINE", actions = arrayOf("WORKER_ID=$info.id", "WORKER_TYPE=${info.type.name}"))
                    }
                } else {
                    if (++backoffCount % 5 == 0) grpc.channel.resetConnectBackoff()
                    JayLogger.logInfo("REQUEST_WORKER_STATUS_FAIL", actions = arrayOf("WORKER_ID=$info.id", "WORKER_TYPE=${info.type.name}"))
                }
                JayLogger.logInfo("AVAILABLE_MEMORY", "", "MAX_MEMORY=${Runtime.getRuntime().maxMemory()}", "MEMORY=${
                    Runtime
                            .getRuntime().totalMemory
                            () - Runtime.getRuntime().freeMemory()
                }")
                sleep(JaySettings.WORKER_STATUS_UPDATE_INTERVAL)
            } while (autoStatusUpdateEnabledFlag)
            autoStatusUpdateRunning.countDown()
        }
    }

    internal fun disableAutoStatusUpdate(wait: Boolean = true) {
        autoStatusUpdateEnabledFlag = false
        if (wait) autoStatusUpdateRunning.await()
    }

    fun addResultSize(size: Long) {
        synchronized(resultsLock) {
            circularResultsFIFO.add(size)
            var tot = 0L
            circularResultsFIFO.forEach { tot += it }
            info.avgResultSize = tot / circularResultsFIFO.size
        }
    }

    internal fun addRTT(millis: Int, payloadSize: Int = PING_PAYLOAD_SIZE) {
        info.bandwidthEstimate = JaySettings.BANDWIDTH_SCALING_FACTOR * if (JaySettings.BANDWIDTH_ESTIMATE_CALC_METHOD == "mean") {
            synchronized(rttLock) {
                circularFIFO.add(millis.toFloat() / payloadSize)
                if (circularFIFO.size > 0) circularFIFO.sum() / circularFIFO.size else 0f
            }
        } else {
            synchronized(rttLock) {
                when {
                    circularFIFO.size == 0 -> 0f
                    circularFIFO.size % 2 == 0 -> (circularFIFO.sorted()[circularFIFO.size / 2] + circularFIFO.sorted()[(circularFIFO.size / 2) - 1]) / 2.0f
                    else -> circularFIFO.sorted()[(circularFIFO.size - 1) / 2]
                }
            }
        }
        JayLogger.logInfo("NEW_BANDWIDTH_ESTIMATE", actions = arrayOf("WORKER_ID=$info.id", "BANDWIDTH_ESTIMATE=${info.bandwidthEstimate}", "WORKER_TYPE=${info.type.name}"))
    }

    internal fun enableHeartBeat(statusChangeCallback: ((WorkerInfo.Status) -> Unit)? = null) {
        checkingHeartBeat = true
        this.statusChangeCallback = statusChangeCallback
        if (smartPingScheduler.isShutdown) smartPingScheduler = ScheduledThreadPoolExecutor(1)
        smartPingScheduler.schedule(RTTTimer(), 0L, TimeUnit.MILLISECONDS)
    }

    internal fun disableHeartBeat() {
        smartPingScheduler.shutdownNow()
        checkingHeartBeat = false
        statusChangeCallback = null
    }

    internal fun doActiveRTTEstimates(statusChangeCallback: ((WorkerInfo.Status) -> Unit)? = null) {
        calcRTT = true
        if (!checkingHeartBeat) enableHeartBeat(statusChangeCallback)
    }

    internal fun stopActiveRTTEstimates() {
        if (checkingHeartBeat) disableHeartBeat()
        calcRTT = false
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Worker

        if (info.id != other.info.id) return false

        return true
    }

    override fun hashCode(): Int {
        return info.id.hashCode()
    }

    internal fun isOnline(): Boolean {
        return info.status == WorkerInfo.Status.ONLINE
    }

    internal fun getStatus(): WorkerInfo.Status {
        return info.status
    }


    private inner class RTTTimer : Runnable {
        override fun run() {
            grpc.ping(PING_PAYLOAD_SIZE, timeout = JaySettings.PING_TIMEOUT, callback = { T ->
                if (T == -1) {
                    if (info.status == WorkerInfo.Status.ONLINE) {
                        JayLogger.logInfo("HEARTBEAT", actions = arrayOf("WORKER_ID=$info.id", "WORKER_TYPE=${info.type.name}", "STATUS=DEVICE_OFFLINE"))
                        info.status = WorkerInfo.Status.OFFLINE
                        statusChangeCallback?.invoke(info.status)
                    }
                    if (!smartPingScheduler.isShutdown) smartPingScheduler.schedule(RTTTimer(), JaySettings.RTT_DELAY_MILLIS, TimeUnit.MILLISECONDS)
                } else if (T == -2 || T == -3) { // TRANSIENT_FAILURE || CONNECTING
                    if (info.status == WorkerInfo.Status.ONLINE) {
                        if (++consecutiveTransientFailurePing > JaySettings.RTT_DELAY_MILLIS_FAIL_ATTEMPTS) {
                            JayLogger.logInfo("HEARTBEAT", actions = arrayOf("WORKER_ID=$info.id", "WORKER_TYPE=${info.type.name}", "STATUS=DEVICE_OFFLINE"))
                            info.status = WorkerInfo.Status.OFFLINE
                            statusChangeCallback?.invoke(info.status)
                            if (!smartPingScheduler.isShutdown) smartPingScheduler.schedule(RTTTimer(), JaySettings.RTT_DELAY_MILLIS, TimeUnit.MILLISECONDS)
                            consecutiveTransientFailurePing = 0
                        } else {
                            if (!smartPingScheduler.isShutdown) smartPingScheduler.schedule(RTTTimer(), JaySettings.RTT_DELAY_MILLIS_FAIL_RETRY, TimeUnit.MILLISECONDS)
                        }
                    } else {
                        if (!smartPingScheduler.isShutdown) smartPingScheduler.schedule(RTTTimer(), JaySettings.RTT_DELAY_MILLIS, TimeUnit.MILLISECONDS)
                    }
                } else {
                    if (info.status == WorkerInfo.Status.OFFLINE) {
                        JayLogger.logInfo("HEARTBEAT", actions = arrayOf("WORKER_ID=$info.id", "WORKER_TYPE=${info.type.name}", "STATUS=DEVICE_ONLINE"))
                        info.status = WorkerInfo.Status.ONLINE
                        statusChangeCallback?.invoke(info.status)
                    }
                    if (!smartPingScheduler.isShutdown) smartPingScheduler.schedule(RTTTimer(), JaySettings.RTT_DELAY_MILLIS, TimeUnit.MILLISECONDS)
                    if (calcRTT) addRTT(T)
                }
            })
        }
    }
}
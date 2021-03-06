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

@file:Suppress("DuplicatedCode", "DuplicatedCode")

package pt.up.fc.dcc.hyrax.jay.services.profiler

import com.google.gson.Gson
import com.google.gson.internal.LinkedTreeMap
import org.apache.commons.collections4.queue.CircularFifoQueue
import pt.up.fc.dcc.hyrax.jay.grpc.GRPCServerBase
import pt.up.fc.dcc.hyrax.jay.logger.JayLogger
import pt.up.fc.dcc.hyrax.jay.proto.JayProto.*
import pt.up.fc.dcc.hyrax.jay.proto.JayProto.ServiceStatus.Type.PROFILER
import pt.up.fc.dcc.hyrax.jay.services.broker.grpc.BrokerGRPCClient
import pt.up.fc.dcc.hyrax.jay.services.profiler.grpc.ProfilerGRPCServer
import pt.up.fc.dcc.hyrax.jay.services.profiler.status.device.cpu.CPUManager
import pt.up.fc.dcc.hyrax.jay.services.profiler.status.device.power.PowerInfo
import pt.up.fc.dcc.hyrax.jay.services.profiler.status.device.power.PowerMonitor
import pt.up.fc.dcc.hyrax.jay.services.profiler.status.device.sensors.SensorManager
import pt.up.fc.dcc.hyrax.jay.services.profiler.status.device.transport.CellularTechnology
import pt.up.fc.dcc.hyrax.jay.services.profiler.status.device.transport.TransportInfo
import pt.up.fc.dcc.hyrax.jay.services.profiler.status.device.transport.TransportManager
import pt.up.fc.dcc.hyrax.jay.services.profiler.status.device.transport.TransportMedium
import pt.up.fc.dcc.hyrax.jay.services.profiler.status.device.usage.PackageUsages
import pt.up.fc.dcc.hyrax.jay.services.profiler.status.device.usage.UsageManager
import pt.up.fc.dcc.hyrax.jay.services.profiler.status.jay.JayState
import pt.up.fc.dcc.hyrax.jay.services.profiler.status.jay.JayStateManager
import pt.up.fc.dcc.hyrax.jay.services.profiler.status.jay.JayStateManager.genJayStates
import pt.up.fc.dcc.hyrax.jay.utils.JaySettings
import pt.up.fc.dcc.hyrax.jay.utils.JayUtils
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.random.Random
import pt.up.fc.dcc.hyrax.jay.proto.JayProto.JayState as JayStateProto

object ProfilerService {
    private var usageManager: UsageManager? = null
    private var cpuManager: CPUManager? = null
    private var transportManager: TransportManager? = null
    private var sensorManager: SensorManager? = null
    private var powerMonitor: PowerMonitor? = null
    private var running: Boolean = false
    private val broker = BrokerGRPCClient("127.0.0.1")
    private var server: GRPCServerBase? = null
    private var recordingStartTime: Long = 0
    private var recording: AtomicBoolean = AtomicBoolean(false)
    private val LOCK: Any = Object()
    private var recordInterval = 500L // time in ms
    private var recalculateAveragesInterval = 1000L // time in ms
    private var recordingLatch = CountDownLatch(0)
    private val recordings = LinkedHashSet<ProfileRecording>()
    private val powerInfo = PowerInfo()

    private val RAW_EXPECTED_CPU_MAP_LOCK = Object()
    private val RAW_EXPECTED_CURRENT_MAP_LOCK = Object()
    private val RAW_EXPECTED_POWER_MAP_LOCK = Object()
    private val EXPECTED_CPU_MAP_LOCK = Object()
    private val EXPECTED_CURRENT_MAP_LOCK = Object()
    private val EXPECTED_POWER_MAP_LOCK = Object()

    private val rawExpectedCpuHashMap = LinkedHashMap<CpuEstimatorKey, CircularFifoQueue<List<Long>>>()
    private val rawExpectedCurrentHashMap = LinkedHashMap<BatteryEstimationKey, HashMap<List<Long>, CircularFifoQueue<Float>>>()
    private val rawExpectedPowerHashMap = LinkedHashMap<BatteryEstimationKey, HashMap<List<Long>, CircularFifoQueue<Float>>>()

    private val expectedCpuHashMap = LinkedHashMap<CpuEstimatorKey, List<Long>?>()
    private val expectedCurrentHashMap = LinkedHashMap<BatteryEstimationKey, HashMap<List<Long>, Float>>()
    private val expectedPowerHashMap = LinkedHashMap<BatteryEstimationKey, HashMap<List<Long>, Float>>()


    // New simplified power estimation
    private val simpleExpectedCurrentHashMap = LinkedHashMap<BatteryEstimationKey, HashMap<Set<JayState>, Float>>()
    private val simpleExpectedPowerHashMap = LinkedHashMap<BatteryEstimationKey, HashMap<Set<JayState>, Float>>()
    private val simpleRawExpectedCurrentHashMap = LinkedHashMap<BatteryEstimationKey, HashMap<Set<JayState>, CircularFifoQueue<Float>>>()
    private val simpleRawExpectedPowerHashMap = LinkedHashMap<BatteryEstimationKey, HashMap<Set<JayState>, CircularFifoQueue<Float>>>()

    private var recordingDir: File? = null
    private var saveCounter = 0

    private data class CpuEstimatorKey(val jayState: Set<JayState>, val deviceLoad: PackageUsages?)

    private data class BatteryEstimationKey(val transportInfo: TransportInfo?, val sensors: Set<String>,
                                            val batteryStatus: PowerStatus)

    private fun genCpuEstimatorKeyFromStr(str: String): CpuEstimatorKey {
        val stateSet = mutableSetOf<JayState>()
        str.substring("CpuEstimatorKey(jayState=[".length, str.indexOf("]")).split(", ").forEach {
            stateSet.add(when (it) {
                "MULTICAST_LISTEN" -> JayState.MULTICAST_LISTEN
                "COMPUTE" -> JayState.COMPUTE
                "DATA_RCV" -> JayState.DATA_RCV
                "DATA_SND" -> JayState.DATA_SND
                "MULTICAST_ADVERTISE" -> JayState.MULTICAST_ADVERTISE
                else -> JayState.IDLE
            })
        }
        return CpuEstimatorKey(stateSet, null)
    }

    // todo: Read recorded file and fix reading
    /*private fun genJaySetFromRaw(str: String): Set<JayState> {
        val stateSet = mutableSetOf<JayState>()
        str.substring("CpuEstimatorKey(jayState=[".length, str.indexOf("]")).split(", ").forEach {
            stateSet.add(when (it) {
                "MULTICAST_LISTEN" -> JayState.MULTICAST_LISTEN
                "COMPUTE" -> JayState.COMPUTE
                "DATA_RCV" -> JayState.DATA_RCV
                "DATA_SND" -> JayState.DATA_SND
                "MULTICAST_ADVERTISE" -> JayState.MULTICAST_ADVERTISE
                else -> JayState.IDLE
            })
        }
        return stateSet
    }*/


    private fun genBatteryEstimationKey(str: String): BatteryEstimationKey {
        val transportInfo = str.substring("BatteryEstimationKey(transportInfo=TransportInfo(".length, str.indexOf(")"))
        val sensors = str.substring(str.indexOf("[") + 1, str.indexOf("]"))
        val batteryStatus = str.substring(str.indexOf("batteryStatus=") + "batteryStatus=".length, str.length - 1)

        var medium: TransportMedium? = null
        var up: Int? = null
        var down: Int? = null
        var cellular: CellularTechnology? = null

        transportInfo.split(", ").forEach {
            when (it.substring(0, it.indexOf("="))) {
                "medium" -> {
                    medium = when (it.substring(it.indexOf("=") + 1)) {
                        "WIFI" -> TransportMedium.WIFI
                        "CELLULAR" -> TransportMedium.CELLULAR
                        "BLUETOOTH" -> TransportMedium.BLUETOOTH
                        "ETHERNET" -> TransportMedium.ETHERNET
                        "VPN" -> TransportMedium.VPN
                        "WIFI_AWARE" -> TransportMedium.WIFI_AWARE
                        "LOWPAN" -> TransportMedium.LOWPAN
                        else -> TransportMedium.UNKNOWN
                    }
                }
                "upstreamBandwidth" -> try {
                    up = it.substring(it.indexOf("=") + 1).toInt()
                } catch (ignore: Exception) {
                }
                "downstreamBandwidth" -> try {
                    down = it.substring(it.indexOf("=") + 1).toInt()
                } catch (ignore: Exception) {
                }
                "cellularTechnology" -> {
                    cellular = when (it.substring(it.indexOf("="))) {
                        "SECOND_GEN" -> CellularTechnology.SECOND_GEN
                        "THIRD_GEN" -> CellularTechnology.THIRD_GEN
                        "FOURTH_GEN" -> CellularTechnology.FOURTH_GEN
                        "FIFTH_GEN," -> CellularTechnology.FIFTH_GEN
                        "UNKNOWN_GEN" -> CellularTechnology.UNKNOWN_GEN
                        else -> null
                    }
                }
            }
        }

        val sensorSet = mutableSetOf<String>()
        sensors.split(", ").forEach { sensorSet.add(it) }

        val battery = when (batteryStatus) {
            "FULL" -> PowerStatus.FULL
            "AC_CHARGING" -> PowerStatus.AC_CHARGING
            "USB_CHARGING" -> PowerStatus.USB_CHARGING
            "QI_CHARGING" -> PowerStatus.QI_CHARGING
            "CHARGING" -> PowerStatus.CHARGING
            "DISCHARGING" -> PowerStatus.DISCHARGING
            else -> PowerStatus.UNKNOWN
        }

        return BatteryEstimationKey(TransportInfo(medium!!, up ?: 0, down ?: 0, cellular), sensorSet.toSet(), battery)
    }


    private fun setSimilarity(v1: List<Long>, v2: List<Long>): Long {
        var diff = 0L
        if (v1.isEmpty() || v2.isEmpty()) return Long.MAX_VALUE
        for (i in v1.indices) {
            diff += if (i < v2.size)
                abs(v1.elementAt(i) - v2.elementAt(i))
            else
                abs(v1.elementAt(i) - v2.last())
        }
        if (v2.size > v1.size) {
            for (i in v1.size until v2.size) {
                diff += abs(v1.last() - v2.elementAt(i))
            }
        }
        return diff
    }

    private fun mostSimilarSet(v1: List<Long>, v2: Set<List<Long>>): List<Long>? {
        var maxDiff = Long.MAX_VALUE
        var retSet: List<Long>? = null
        v2.forEach {
            val diff = setSimilarity(v1, it)
            if (diff < maxDiff) {
                maxDiff = diff
                retSet = it
            }
        }
        return retSet
    }

    private fun getMovingAvg(v1: CircularFifoQueue<Float>?): Float {
        var tot = 0f
        v1?.forEach { tot += it }
        return tot / (v1?.size ?: 1)
    }

    private fun getListMovingAvg(v1: CircularFifoQueue<List<Long>>?): List<Long>? {
        if (v1 == null || v1.isEmpty()) return null
        val cpuCoreCounts: MutableList<Int> = ArrayList()
        val cpuCoreSums: MutableList<Long> = ArrayList()
        val retSet: MutableList<Long> = mutableListOf()
        v1.forEach {
            for (i in it.indices) {
                if (cpuCoreCounts.size > i) cpuCoreCounts[i] = cpuCoreCounts[i] + 1
                else cpuCoreCounts.add(1)
                if (cpuCoreSums.size > i) cpuCoreSums[i] = cpuCoreSums[i] + it.elementAt(i)
                else cpuCoreSums.add(it.elementAt(i))
            }
        }
        for (i in 0 until cpuCoreCounts.size) {
            retSet.add((cpuCoreSums[i] / cpuCoreCounts[i]))
        }
        return retSet
    }

    private fun getExpectedCpuMhz(k: CpuEstimatorKey): List<Long>? {
        return if ((rawExpectedCpuHashMap.containsKey(k))) getListMovingAvg(rawExpectedCpuHashMap[k]) else null
    }

    private fun getExpectedCurrent(k: BatteryEstimationKey, expectedCpuMhz: List<Long>): Float? {
        var ret: Float? = null
        synchronized(EXPECTED_CURRENT_MAP_LOCK) {
            if (expectedCurrentHashMap.containsKey(k)) {
                val mostSimilarSet = mostSimilarSet(expectedCpuMhz, expectedCurrentHashMap[k]!!.keys)
                if (mostSimilarSet != null) ret = expectedCurrentHashMap[k]!![mostSimilarSet]
            }
        }
        return ret
    }

    fun getExpectedCurrents(): CurrentEstimations? {
        val key = BatteryEstimationKey(this.transportManager?.getTransport(),
            this.sensorManager?.getActiveSensors() ?: setOf(),
            this.powerInfo.status)

        val builder = CurrentEstimations.newBuilder()
        if (JaySettings.USE_CPU_ESTIMATIONS) {
            synchronized(EXPECTED_CPU_MAP_LOCK) {
                builder.idle = getExpectedCurrent(key, expectedCpuHashMap[CpuEstimatorKey(setOf(JayState.IDLE), null)]
                    ?: listOf()) ?: 0f
                builder.compute = getExpectedCurrent(key, expectedCpuHashMap[CpuEstimatorKey(genJayStates(JayState.COMPUTE), null)]
                    ?: listOf()) ?: 0f
                builder.rx = getExpectedCurrent(key, expectedCpuHashMap[CpuEstimatorKey(genJayStates(JayState.DATA_RCV), null)]
                    ?: listOf()) ?: 0f
                builder.tx = getExpectedCurrent(key, expectedCpuHashMap[CpuEstimatorKey(genJayStates(JayState.DATA_SND), null)]
                    ?: listOf()) ?: 0f
            }
        } else {
            synchronized(EXPECTED_CURRENT_MAP_LOCK) {
                builder.idle = simpleExpectedCurrentHashMap[key]?.get(setOf(JayState.IDLE)) ?: 0f
                builder.compute = simpleExpectedCurrentHashMap[key]?.get(genJayStates(JayState.COMPUTE)) ?: 0f
                builder.rx = simpleExpectedCurrentHashMap[key]?.get(genJayStates(JayState.DATA_RCV)) ?: 0f
                builder.tx = simpleExpectedCurrentHashMap[key]?.get(genJayStates(JayState.DATA_SND)) ?: 0f
            }
        }
        builder.batteryLevel = this.powerInfo.level
        builder.batteryCapacity = this.powerInfo.capacity
        return builder.build()
    }

    private fun getExpectedPower(k: BatteryEstimationKey, expectedCpuMhz: List<Long>): Float? {
        var ret: Float? = null
        synchronized(EXPECTED_POWER_MAP_LOCK) {
            if (expectedPowerHashMap.containsKey(k)) {
                val mostSimilarSet = mostSimilarSet(expectedCpuMhz, expectedPowerHashMap[k]!!.keys)
                if (mostSimilarSet != null) ret = expectedPowerHashMap[k]!![mostSimilarSet]
            }
        }
        return ret
    }

    fun getExpectedPowers(): PowerEstimations? {
        if (JaySettings.USE_FIXED_POWER_ESTIMATIONS) {
            return this.powerMonitor?.getFixedPowerEstimations()
        } else {
            val key = BatteryEstimationKey(this.transportManager?.getTransport(),
                this.sensorManager?.getActiveSensors() ?: setOf(),
                this.powerInfo.status)

            val builder = PowerEstimations.newBuilder()
            if (JaySettings.USE_CPU_ESTIMATIONS) {
                synchronized(EXPECTED_CPU_MAP_LOCK) {
                    builder.idle = getExpectedPower(key, expectedCpuHashMap[CpuEstimatorKey(setOf(JayState.IDLE), null)]
                        ?: listOf()) ?: 0f
                    builder.compute = getExpectedPower(key, expectedCpuHashMap[CpuEstimatorKey(genJayStates(JayState.COMPUTE), null)]
                        ?: listOf()) ?: 0f
                    builder.rx = getExpectedPower(key, expectedCpuHashMap[CpuEstimatorKey(genJayStates(JayState.DATA_RCV), null)]
                        ?: listOf()) ?: 0f
                    builder.tx = getExpectedPower(key, expectedCpuHashMap[CpuEstimatorKey(genJayStates(JayState.DATA_SND), null)]
                        ?: listOf()) ?: 0f
                }
            } else {
                builder.idle = simpleExpectedPowerHashMap[key]?.get(setOf(JayState.IDLE)) ?: 0f
                builder.compute = simpleExpectedPowerHashMap[key]?.get(genJayStates(JayState.COMPUTE)) ?: 0f
                builder.rx = simpleExpectedPowerHashMap[key]?.get(genJayStates(JayState.DATA_RCV)) ?: 0f
                builder.tx = simpleExpectedPowerHashMap[key]?.get(genJayStates(JayState.DATA_SND)) ?: 0f
            }
            builder.batteryLevel = this.powerInfo.level
            builder.batteryCapacity = (this.powerInfo.capacity * this.powerInfo.voltage)
            return builder.build()
        }
    }

    fun start(useNettyServer: Boolean = false, powerMonitor: PowerMonitor? = null, transportManager:
    TransportManager? = null, cpuManager: CPUManager? = null, usageManager: UsageManager? = null, sensorManager:
              SensorManager? = null, recordingDir: File? = null) {
        JayLogger.logInfo("INIT")
        if (this.running) return
        this.powerMonitor = powerMonitor
        setBatteryCallbacks()
        this.powerMonitor?.monitor()
        this.transportManager = transportManager
        this.cpuManager = cpuManager
        this.usageManager = usageManager
        this.sensorManager = sensorManager
        this.recordingDir = recordingDir
        repeat(30) {
            if (this.server == null) {
                this.server = ProfilerGRPCServer(useNettyServer).start()
                if (this.server == null) JaySettings.PROFILER_PORT = Random.nextInt(30000, 64000)
            }
        }
        this.running = true
        this.broker.announceServiceStatus(ServiceStatus.newBuilder().setType(PROFILER).setRunning(true).build())
        {
            JayLogger.logInfo("COMPLETE")
        }
    }

    fun stop(stopGRPCServer: Boolean = true): Status {
        return try {
            this.running = false
            if (stopGRPCServer) this.server?.stop()
            this.powerMonitor?.destroy()
            this.broker.announceServiceStatus(ServiceStatus.newBuilder().setType(PROFILER).setRunning(false).build())
            {
                JayLogger.logInfo("STOP")
            }
            JayUtils.genStatusSuccess()
        } catch (ignore: Exception) {
            JayLogger.logError("Error Stopping ProfilerService")
            JayUtils.genStatusError()
        }
    }

    private fun getTimeRangeProto(start: Long, end: Long): TimeRange? {
        val timeRange = TimeRange.newBuilder()
        timeRange.start = if (start == 0L) end - JaySettings.WORKER_STATUS_UPDATE_INTERVAL else start
        timeRange.end = end
        return timeRange.build()
    }

    private fun getJayStatesProto(v1: Set<JayState>): Set<JayStateProto?> {
        val jayStates = LinkedHashSet<JayStateProto?>()
        v1.forEach { state ->
            jayStates.add(JayUtils.genJayStateProto(state))
        }
        return jayStates
    }

    private fun getTransportProto(transportInfo: TransportInfo): Transport {
        val transportBuilder = Transport.newBuilder()
        transportBuilder.medium = Transport.Medium.forNumber(transportInfo.medium.ordinal)
        if (transportInfo.medium == TransportMedium.CELLULAR)
            transportBuilder.cellularTechnology = Transport.CellularTechnology.forNumber(
                transportInfo.cellularTechnology!!.ordinal)
        transportBuilder.downstreamBandwidth = transportInfo.downstreamBandwidth
        transportBuilder.upstreamBandwidth = transportInfo.upstreamBandwidth
        JayLogger.logInfo("TRANSPORT_INFO", "",
            "MEDIUM=${transportInfo.medium.name}",
            "DOWNSTREAM=${transportInfo.downstreamBandwidth}",
            "UPSTREAM=${transportInfo.upstreamBandwidth}"
        )
        if (transportInfo.medium == TransportMedium.CELLULAR)
            JayLogger.logInfo("TRANSPORT_INFO_CELLULAR", "",
                "CELLULAR_TECHNOLOGY=${transportInfo.cellularTechnology?.name}"
            )
        return transportBuilder.build()
    }

    private fun getPowerProto(): Power? {
        this.powerInfo.current = this.powerMonitor?.getCurrentNow() ?: -1f
        this.powerInfo.power = this.powerMonitor?.getPower() ?: -1f
        this.powerInfo.energy = this.powerMonitor?.getRemainingEnergy() ?: -1
        this.powerInfo.charge = this.powerMonitor?.getCharge() ?: -1f
        this.powerInfo.capacity = this.powerMonitor?.getCapacity() ?: -1f
        this.powerInfo.level = if (this.powerInfo.level == -1)
            this.powerMonitor?.getLevel() ?: -1 else this.powerInfo.level
        if (this.powerInfo.status == PowerStatus.UNKNOWN) {
            this.powerInfo.status = this.powerMonitor?.getStatus() ?: PowerStatus.UNKNOWN
        }

        val powerBuilder = Power.newBuilder()
        powerBuilder.level = this.powerInfo.level
        powerBuilder.current = this.powerInfo.current
        powerBuilder.power = this.powerInfo.power
        powerBuilder.voltage = this.powerInfo.voltage
        powerBuilder.temperature = this.powerInfo.temperature
        powerBuilder.energy = this.powerInfo.energy
        powerBuilder.charge = this.powerInfo.charge
        powerBuilder.capacity = this.powerInfo.capacity
        powerBuilder.status = this.powerInfo.status
        JayLogger.logInfo("BATTERY_DETAILS", "",
            "CURRENT=${this.powerInfo.current}",
            "POWER=${this.powerInfo.power}",
            "ENERGY=${this.powerInfo.energy}",
            "CHARGE=${this.powerInfo.charge}",
            "LEVEL=${this.powerInfo.level}",
            "VOLTAGE=${this.powerInfo.voltage}",
            "TEMPERATURE=${this.powerInfo.temperature}",
            "CAPACITY=${this.powerInfo.capacity}",
            "STATUS=${this.powerInfo.status.name}"
        )
        return powerBuilder.build()
    }

    /**
     *     TimeRange timeRange = 1;
     *     repeated JayState jayState = 2;
     *     Battery battery = 3
     *     int32 cpuCount = 4;
     *     repeated int32 cpuFrequency = 5;
     *     Transport transport = 6;
     *     repeated string systemUsage = 7;
     */
    internal fun getSystemProfile(recording: Boolean = false, msToRetrieve: Long = 500): ProfileRecording {
        if (this.recordingLatch.count <= 0 && recording)
            return ProfileRecording.getDefaultInstance()

        val recordBuilder = ProfileRecording.newBuilder()
        try {
            val currentTime = System.currentTimeMillis()
            val cpus = this.cpuManager?.getCpus() ?: emptySet()

            val medium = this.transportManager?.getTransport()

            val jayStates = JayStateManager.getJayStates().states
            val activeSensors = this.sensorManager?.getActiveSensors() ?: emptySet()

            recordBuilder.timeRange =
                if (recording) getTimeRangeProto(this.recordingStartTime, currentTime)
                else getTimeRangeProto(currentTime - msToRetrieve, currentTime)
            JayLogger.logInfo("TIME_RANGE", "", "START=${this.recordingStartTime}", "END=$currentTime")
            var states = ""

            getJayStatesProto(jayStates).forEach { state ->
                recordBuilder.addJayState(state)
                states += "${state?.jayState?.name}, "
            }
            JayLogger.logInfo("JAY_STATES", "", states)
            recordBuilder.power = getPowerProto()
            recordBuilder.cpuCount = cpus.size
            var cpuSpeedsStr = ""
            val cpuSpeeds = mutableListOf<Long>()
            cpus.forEach { cpu_number ->
                val cpuSpeed: Long = this.cpuManager?.getCurrentCPUClockSpeed(cpu_number) ?: 0L
                recordBuilder.addCpuFrequency(cpuSpeed)
                cpuSpeedsStr += "CPU_$cpu_number=$cpuSpeed, "
                cpuSpeeds.add(cpuSpeed)
            }
            JayLogger.logInfo("CPU", "", "CPU_COUNT=${cpus.size}, $cpuSpeedsStr")
            if (medium != null) recordBuilder.transport = getTransportProto(medium)
            val packageUsages = if (recording)
                this.usageManager?.getRecentUsageList(currentTime - this.recordingStartTime)
            else this.usageManager?.getRecentUsageList(msToRetrieve)
            var pkgs = ""
            packageUsages?.forEach { pkg ->
                recordBuilder.addSystemUsage(pkg.pkgName)
                pkgs += "${pkg.pkgName}, "
            }
            JayLogger.logInfo("PKG_USAGE", "", pkgs)
            var sensorStr = ""

            activeSensors.forEach { sensor ->
                recordBuilder.addSensors(sensor)
                sensorStr += "$sensor, "
            }

            /**
             * Insert recorded values in the circular FIFO for future processing
             */
            val batteryEstimationKey = BatteryEstimationKey(medium, activeSensors, this.powerInfo.status)
            if (JaySettings.USE_CPU_ESTIMATIONS) {
                val cpuEstimatorKey = CpuEstimatorKey(jayStates, null)
                synchronized(RAW_EXPECTED_CPU_MAP_LOCK) {
                    if (!this.rawExpectedCpuHashMap.containsKey(cpuEstimatorKey)) {
                        this.rawExpectedCpuHashMap[cpuEstimatorKey] = CircularFifoQueue(JaySettings.JAY_STATE_TO_CPU_CIRCULAR_FIFO_SIZE)
                    }
                    this.rawExpectedCpuHashMap[cpuEstimatorKey]!!.add(cpuSpeeds)
                    JayLogger.logInfo("NEW_CPU_READING", "", "STATE=$cpuEstimatorKey", "SPEED=$cpuSpeeds")
                }
                synchronized(RAW_EXPECTED_CURRENT_MAP_LOCK) {
                    if (!this.rawExpectedCurrentHashMap.containsKey(batteryEstimationKey)) {
                        this.rawExpectedCurrentHashMap[batteryEstimationKey] = LinkedHashMap()
                    }
                    if (!this.rawExpectedCurrentHashMap[batteryEstimationKey]!!.containsKey(cpuSpeeds)) {
                        this.rawExpectedCurrentHashMap[batteryEstimationKey]!![cpuSpeeds] =
                            CircularFifoQueue(JaySettings.CPU_TO_BAT_CURRENT_CIRCULAR_FIFO_SIZE)
                    }
                    this.rawExpectedCurrentHashMap[batteryEstimationKey]!![cpuSpeeds]!!.add(this.powerInfo.current)
                    JayLogger.logInfo("NEW_CURRENT_READING", "", "STATE=$batteryEstimationKey", "CURRENT=${this.powerInfo.current}")
                }

                synchronized(RAW_EXPECTED_POWER_MAP_LOCK) {
                    if (!this.rawExpectedPowerHashMap.containsKey(batteryEstimationKey)) {
                        this.rawExpectedPowerHashMap[batteryEstimationKey] = LinkedHashMap()
                    }
                    if (!this.rawExpectedPowerHashMap[batteryEstimationKey]!!.containsKey(cpuSpeeds)) {
                        this.rawExpectedPowerHashMap[batteryEstimationKey]!![cpuSpeeds] =
                            CircularFifoQueue(JaySettings.CPU_TO_BAT_POWER_CIRCULAR_FIFO_SIZE)
                    }
                    this.rawExpectedPowerHashMap[batteryEstimationKey]!![cpuSpeeds]!!.add(this.powerInfo.power)
                    JayLogger.logInfo("NEW_POWER_READING", "", "STATE=$batteryEstimationKey", "POWER=${this.powerInfo.power}")
                }
            } else {
                synchronized(RAW_EXPECTED_CURRENT_MAP_LOCK) {
                    if (!this.simpleRawExpectedCurrentHashMap.containsKey(batteryEstimationKey)) {
                        this.simpleRawExpectedCurrentHashMap[batteryEstimationKey] = LinkedHashMap()
                    }
                    if (!this.simpleRawExpectedCurrentHashMap[batteryEstimationKey]!!.containsKey(jayStates)) {
                        this.simpleRawExpectedCurrentHashMap[batteryEstimationKey]!![jayStates] =
                            CircularFifoQueue(JaySettings.CPU_TO_BAT_CURRENT_CIRCULAR_FIFO_SIZE)
                    }
                    this.simpleRawExpectedCurrentHashMap[batteryEstimationKey]!![jayStates]!!.add(this.powerInfo.current)
                    JayLogger.logInfo("NEW_CURRENT_READING", "", "STATE=$batteryEstimationKey", "CURRENT=${this.powerInfo.current}")
                }

                synchronized(RAW_EXPECTED_POWER_MAP_LOCK) {
                    if (!this.simpleRawExpectedPowerHashMap.containsKey(batteryEstimationKey)) {
                        this.simpleRawExpectedPowerHashMap[batteryEstimationKey] = LinkedHashMap()
                    }
                    if (!this.simpleRawExpectedPowerHashMap[batteryEstimationKey]!!.containsKey(jayStates)) {
                        this.simpleRawExpectedPowerHashMap[batteryEstimationKey]!![jayStates] =
                            CircularFifoQueue(JaySettings.CPU_TO_BAT_POWER_CIRCULAR_FIFO_SIZE)
                    }
                    this.simpleRawExpectedPowerHashMap[batteryEstimationKey]!![jayStates]!!.add(this.powerInfo.power)
                    JayLogger.logInfo("NEW_POWER_READING", "", "STATE=$batteryEstimationKey", "POWER=${this.powerInfo.power}")
                }
            }

            JayLogger.logInfo("ACTIVE_SENSORS", "", sensorStr)
            if (recording) this.recordingStartTime = currentTime
        } catch (ignore: java.lang.RuntimeException) {
            // To prevent java.lang.RuntimeException: android.os.DeadSystemException
            JayLogger.logError("TRANSPORT_EXCEPTION", "", "ERROR: ${ignore.message}")
        }
        return recordBuilder.build()
    }

    private fun setBatteryCallbacks() {
        this.powerMonitor?.setCallbacks(
            _levelChangeCallback = { level, voltage, temperature ->
                this.powerInfo.level = level
                this.powerInfo.voltage = voltage
                this.powerInfo.temperature = temperature
                this.powerInfo.current = this.powerMonitor?.getCurrentNow() ?: -1f
                this.powerInfo.power = this.powerMonitor?.getPower() ?: -1f
                this.powerInfo.energy = this.powerMonitor?.getRemainingEnergy() ?: -1
                this.powerInfo.charge = this.powerMonitor?.getCharge() ?: -1f
                JayLogger.logInfo("LEVEL_CHANGE_CB", actions = arrayOf("NEW_BATTERY_LEVEL=$level", "NEW_BATTERY_VOLTAGE=$voltage", "NEW_BATTERY_TEMPERATURE=$temperature", "NEW_BATTERY_CURRENT=${this.powerInfo.current}", "NEW_POWER=${this.powerInfo.power}", "REMAINING_ENERGY=${this.powerInfo.energy}", "NEW_BATTERY_CHARGE=${this.powerInfo.charge}"))
            },
            _statusChangeCallback = { status ->
                if (status != this.powerInfo.status) {
                    JayLogger.logInfo("STATUS_CHANGE_CB", actions = arrayOf("NEW_BATTERY_STATUS=${status.name}"))
                    this.powerInfo.status = status
                }
            }
        )
    }


    internal fun startRecording(): Boolean {
        synchronized(LOCK) {
            if (this.recording.get()) return false
            JayLogger.logInfo("START_RECORDING")
            this.recording.set(true)
            recordingStartTime = System.currentTimeMillis()
            recordingLatch = CountDownLatch(2)
            this.recordings.clear()
            thread {
                do {
                    this.recordings.add(getSystemProfile(true))
                    Thread.sleep(recordInterval)
                } while (this.recording.get())
                recordingLatch.countDown()
                JayLogger.logInfo("START_RECORDING", "", "END")
            }
            thread {
                var cpuRecordingsFile: File? = null
                var currentRecordingsFile: File? = null
                var powerRecordingsFile: File? = null
                var simpleCurrentRecordingsFile: File? = null
                var simplePowerRecordingsFile: File? = null
                if (this.recordingDir != null) {
                    if (!this.recordingDir!!.exists()) this.recordingDir!!.mkdirs()
                    cpuRecordingsFile = File(this.recordingDir, "cpu_recordings.json")
                    if (!cpuRecordingsFile.exists()) cpuRecordingsFile.createNewFile()
                    currentRecordingsFile = File(this.recordingDir, "current_recordings.json")
                    if (!currentRecordingsFile.exists()) currentRecordingsFile.createNewFile()
                    powerRecordingsFile = File(this.recordingDir, "power_recordings.json")
                    if (!powerRecordingsFile.exists()) powerRecordingsFile.createNewFile()

                    simpleCurrentRecordingsFile = File(this.recordingDir, "simple_current_recordings.json")
                    if (!simpleCurrentRecordingsFile.exists()) simpleCurrentRecordingsFile.createNewFile()
                    simplePowerRecordingsFile = File(this.recordingDir, "simple_power_recordings.json")
                    if (!simplePowerRecordingsFile.exists()) simplePowerRecordingsFile.createNewFile()
                }
                if (JaySettings.READ_RECORDED_PROFILE_DATA) {
                    JayLogger.logInfo("READING_RECORDED_PROFILE_DATA")
                    if (JaySettings.USE_CPU_ESTIMATIONS) {
                        if (cpuRecordingsFile?.readText() != "") {
                            synchronized(RAW_EXPECTED_CPU_MAP_LOCK) {
                                try {
                                    Gson().fromJson<LinkedHashMap<String, ArrayList<List<Long>>>>(
                                        cpuRecordingsFile?.readText() ?: "", this.rawExpectedCpuHashMap::class.java
                                    ).forEach { (keyStr, values) ->
                                        val key = genCpuEstimatorKeyFromStr(keyStr)
                                        if (!this.rawExpectedCpuHashMap.containsKey(key)) {
                                            this.rawExpectedCpuHashMap[key] = CircularFifoQueue(JaySettings.JAY_STATE_TO_CPU_CIRCULAR_FIFO_SIZE)
                                        }
                                        values.forEach {
                                            this.rawExpectedCpuHashMap[key]?.add(it)
                                            JayLogger.logInfo("READING_RECORDED_CPU", "", "KEY=$key", "VALUE=$it")
                                        }
                                    }
                                } catch (ignore: Exception) {
                                }
                            }
                        }
                        synchronized(RAW_EXPECTED_CURRENT_MAP_LOCK) {
                            try {
                                Gson().fromJson<LinkedHashMap<String, LinkedTreeMap<String, ArrayList<Float>>>>(
                                    currentRecordingsFile?.readText()
                                        ?: "", this.rawExpectedCurrentHashMap::class.java
                                ).forEach { (rawKey, values) ->
                                    val key = genBatteryEstimationKey(rawKey)
                                    if (!this.rawExpectedCurrentHashMap.containsKey(key)) {
                                        this.rawExpectedCurrentHashMap[key] = hashMapOf()
                                    }
                                    values.forEach { (hashKeyStr, circularFifoValues) ->
                                        val hashKey = listFromStr(hashKeyStr)
                                        if (!this.rawExpectedCurrentHashMap[key]!!.containsKey(hashKey)) {
                                            this.rawExpectedCurrentHashMap[key]!![hashKey] =
                                                CircularFifoQueue(JaySettings.CPU_TO_BAT_CURRENT_CIRCULAR_FIFO_SIZE)
                                        }
                                        circularFifoValues.forEach {
                                            this.rawExpectedCurrentHashMap[key]!![hashKey]?.add(it)
                                            JayLogger.logInfo(
                                                "READING_RECORDED_CURRENT", "", "KEY=$key", "SUB_KEY=$hashKey",
                                                "VALUE=$it"
                                            )
                                        }
                                    }
                                }
                            } catch (ignore: Exception) {
                            }
                        }
                        synchronized(RAW_EXPECTED_POWER_MAP_LOCK) {
                            try {
                                Gson().fromJson<LinkedHashMap<String, LinkedTreeMap<String, ArrayList<Float>>>>(
                                    powerRecordingsFile?.readText() ?: "", this.rawExpectedPowerHashMap::class.java
                                ).forEach { (rawKey, values) ->
                                    val key = genBatteryEstimationKey(rawKey)
                                    if (!this.rawExpectedPowerHashMap.containsKey(key)) {
                                        this.rawExpectedPowerHashMap[key] = hashMapOf()
                                    }
                                    values.forEach { (hashKeyStr, circularFifoValues) ->
                                        val hashKey = listFromStr(hashKeyStr)
                                        if (!this.rawExpectedPowerHashMap[key]!!.containsKey(hashKey)) {
                                            this.rawExpectedPowerHashMap[key]!![hashKey] = CircularFifoQueue(JaySettings.CPU_TO_BAT_POWER_CIRCULAR_FIFO_SIZE)
                                        }
                                        circularFifoValues.forEach {
                                            this.rawExpectedPowerHashMap[key]!![hashKey]?.add(it)
                                            JayLogger.logInfo("READING_RECORDED_POWER", "", "KEY=$key", "SUB_KEY=$hashKey", "VALUE=$it")
                                        }
                                    }
                                }
                            } catch (ignore: Exception) {
                            }
                        }
                    } else {
                        /* todo:
                        synchronized(RAW_EXPECTED_CURRENT_MAP_LOCK) {
                            try {
                                Gson().fromJson<LinkedHashMap<String, LinkedTreeMap<String, ArrayList<Float>>>>(
                                        simpleCurrentRecordingsFile?.readText()
                                                ?: "", this.simpleRawExpectedCurrentHashMap::class.java
                                ).forEach { (rawKey, values) ->
                                    val key = genBatteryEstimationKey(rawKey)
                                    if (!this.simpleRawExpectedCurrentHashMap.containsKey(key)) {
                                        this.simpleRawExpectedCurrentHashMap[key] = hashMapOf()
                                    }
                                    values.forEach { (hashKeyStr, circularFifoValues) ->
                                        val hashKey = setFromStr(hashKeyStr)
                                        if (!this.simpleRawExpectedCurrentHashMap[key]!!.containsKey(hashKey)) {
                                            this.simpleRawExpectedCurrentHashMap[key]!![hashKey] = CircularFifoQueue
                                            (JaySettings.CPU_TO_BAT_CURRENT_CIRCULAR_FIFO_SIZE)
                                        }
                                        circularFifoValues.forEach {
                                            this.simpleRawExpectedCurrentHashMap[key]!![hashKey]?.add(it)
                                            JayLogger.logInfo("READING_RECORDED_CURRENT", "", "KEY=$key", "SUB_KEY=$hashKey",
                                                    "VALUE=$it")
                                        }
                                    }
                                }
                            } catch (ignore: Exception) {
                            }
                        }
                        synchronized(RAW_EXPECTED_POWER_MAP_LOCK) {
                            try {
                                Gson().fromJson<LinkedHashMap<String, LinkedTreeMap<String, ArrayList<Float>>>>(
                                        powerRecordingsFile?.readText() ?: "", this.rawExpectedPowerHashMap::class.java
                                ).forEach { (rawKey, values) ->
                                    val key = genBatteryEstimationKey(rawKey)
                                    if (!this.rawExpectedPowerHashMap.containsKey(key)) {
                                        this.rawExpectedPowerHashMap[key] = hashMapOf()
                                    }
                                    values.forEach { (hashKeyStr, circularFifoValues) ->
                                        val hashKey = listFromStr(hashKeyStr)
                                        if (!this.rawExpectedPowerHashMap[key]!!.containsKey(hashKey)) {
                                            this.rawExpectedPowerHashMap[key]!![hashKey] = CircularFifoQueue(JaySettings.CPU_TO_BAT_POWER_CIRCULAR_FIFO_SIZE)
                                        }
                                        circularFifoValues.forEach {
                                            this.rawExpectedPowerHashMap[key]!![hashKey]?.add(it)
                                            JayLogger.logInfo("READING_RECORDED_POWER", "", "KEY=$key", "SUB_KEY=$hashKey", "VALUE=$it")
                                        }
                                    }
                                }
                            } catch (ignore: Exception) {
                            }
                        }*/
                    }
                }
                Thread.sleep(recalculateAveragesInterval)
                do {
                    if (JaySettings.USE_CPU_ESTIMATIONS) {
                        synchronized(RAW_EXPECTED_CPU_MAP_LOCK) {
                            rawExpectedCpuHashMap.keys.forEach {
                                synchronized(EXPECTED_CPU_MAP_LOCK) {
                                    expectedCpuHashMap[it] = getExpectedCpuMhz(it)
                                }
                            }
                            if (saveCounter % 30 == 0) {
                                cpuRecordingsFile?.writeText(Gson().toJson(rawExpectedCpuHashMap))
                            }
                        }
                        synchronized(RAW_EXPECTED_CURRENT_MAP_LOCK) {
                            rawExpectedCurrentHashMap.keys.forEach {
                                rawExpectedCurrentHashMap[it]?.keys?.forEach { cpu ->
                                    synchronized(EXPECTED_CURRENT_MAP_LOCK) {
                                        if (!expectedCurrentHashMap.containsKey(it)) {
                                            expectedCurrentHashMap[it] = LinkedHashMap()
                                        }
                                        expectedCurrentHashMap[it]!![cpu] = getMovingAvg(rawExpectedCurrentHashMap[it]!![cpu]!!)
                                    }
                                }
                            }
                            if (saveCounter % 30 == 0) {
                                currentRecordingsFile?.writeText(Gson().toJson(rawExpectedCurrentHashMap))
                            }
                        }
                        synchronized(RAW_EXPECTED_POWER_MAP_LOCK) {
                            rawExpectedPowerHashMap.keys.forEach {
                                rawExpectedPowerHashMap[it]?.keys?.forEach { cpu ->
                                    synchronized(EXPECTED_POWER_MAP_LOCK) {
                                        if (!expectedPowerHashMap.containsKey(it)) {
                                            expectedPowerHashMap[it] = LinkedHashMap()
                                        }
                                        expectedPowerHashMap[it]!![cpu] = getMovingAvg(rawExpectedPowerHashMap[it]!![cpu]!!)
                                    }
                                }
                            }
                            if (saveCounter % 30 == 0) {
                                powerRecordingsFile?.writeText(Gson().toJson(rawExpectedPowerHashMap))
                            }
                        }
                    } else {
                        synchronized(RAW_EXPECTED_CURRENT_MAP_LOCK) {
                            simpleRawExpectedCurrentHashMap.keys.forEach {
                                simpleRawExpectedCurrentHashMap[it]?.keys?.forEach { cpu ->
                                    synchronized(EXPECTED_CURRENT_MAP_LOCK) {
                                        if (!simpleExpectedCurrentHashMap.containsKey(it)) {
                                            simpleExpectedCurrentHashMap[it] = LinkedHashMap()
                                        }
                                        simpleExpectedCurrentHashMap[it]!![cpu] = getMovingAvg(simpleRawExpectedCurrentHashMap[it]!![cpu]!!)
                                    }
                                }
                            }
                            if (saveCounter % 30 == 0) {
                                simpleCurrentRecordingsFile?.writeText(Gson().toJson(simpleRawExpectedCurrentHashMap))
                            }
                        }
                        synchronized(RAW_EXPECTED_POWER_MAP_LOCK) {
                            simpleRawExpectedPowerHashMap.keys.forEach {
                                simpleRawExpectedPowerHashMap[it]?.keys?.forEach { cpu ->
                                    synchronized(EXPECTED_POWER_MAP_LOCK) {
                                        if (!simpleExpectedPowerHashMap.containsKey(it)) {
                                            simpleExpectedPowerHashMap[it] = LinkedHashMap()
                                        }
                                        simpleExpectedPowerHashMap[it]!![cpu] = getMovingAvg(simpleRawExpectedPowerHashMap[it]!![cpu]!!)
                                    }
                                }
                            }
                            if (saveCounter % 30 == 0) {
                                simplePowerRecordingsFile?.writeText(Gson().toJson(simpleRawExpectedPowerHashMap))
                            }
                        }
                    }
                    Thread.sleep(recalculateAveragesInterval)
                    if (saveCounter % 30 == 0) saveCounter = 0
                    saveCounter++
                } while (this.recording.get())
                recordingLatch.countDown()
                JayLogger.logInfo("START_PROFILE_AVERAGES_RECALCULATION", "", "END")
            }
        }
        return true
    }

    private fun listFromStr(str: String): List<Long> {
        val list = mutableListOf<Long>()
        str.substring(1, str.length - 1).split(", ").forEach { list.add(it.toLong()) }
        return list
    }

    /*private fun setFromStr(str: String): Set<Long> {
        val set = mutableSetOf<Long>()
        str.substring(1, str.length - 1).split(", ").forEach { set.add(it.toLong()) }
        return set
    }*/

    fun stopRecording(): ProfileRecordings {
        JayLogger.logInfo("STOP_RECORDING")
        this.recording.set(false)
        recordingLatch.await()  // Await recording termination
        JayLogger.logInfo("STOP_RECORDING", "", "END")
        return ProfileRecordings.newBuilder().addAllProfileRecording(this.recordings).build()
    }
}
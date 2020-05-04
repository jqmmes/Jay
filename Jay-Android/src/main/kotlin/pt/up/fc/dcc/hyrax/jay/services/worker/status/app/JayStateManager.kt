package pt.up.fc.dcc.hyrax.jay.services.worker.status.app

import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

object JayStateManager {

    private var stateRecordInterval = 500L // time in ms
    private val recordingFlag: AtomicBoolean = AtomicBoolean(false)
    private val recordedStates: MutableSet<ActiveState> = LinkedHashSet<ActiveState>()

    private val activeStates: HashMap<JayState, Boolean> =
            hashMapOf(
                    Pair(JayState.DATA_RCV, false),
                    Pair(JayState.DATA_SND, false),
                    Pair(JayState.COMPUTE, false),
                    Pair(JayState.IDLE, true))

    fun startCPUStatsRecordings() {
        recordCPUStats()
    }

    fun stopCPUStatsRecordings() {
        recordingFlag.set(false)
    }

    fun clearStats() {
        recordedStates.clear()
    }

    fun getCPURecordings(): Set<ActiveState> {
        return recordedStates
    }

    private fun recordCPUStats(): Boolean {
        if (!recordingFlag.compareAndSet(false, true)) return false
        Thread {
            do {
                val activeStatesSet = LinkedHashSet<JayState>()
                JayState.values().forEach { state ->
                    if (activeStates[state] == true) activeStatesSet.add(state)
                }
                recordedStates.add(ActiveState(System.currentTimeMillis(), setOf(JayState.IDLE)))
                Thread.sleep(stateRecordInterval)
            } while (recordingFlag.get())
        }.start()
        return true
    }
}


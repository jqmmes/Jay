package pt.up.fc.dcc.hyrax.jay.services.profiler.status.jay

import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

internal object JayStateManager {

    private var stateRecordInterval = 500L // time in ms
    private val recordingFlag: AtomicBoolean = AtomicBoolean(false)
    private val recordedStates: MutableSet<ActiveState> = LinkedHashSet<ActiveState>()
    private val SET_LOCK: Any = Object()

    private val activeStates: HashMap<JayState, Int> =
            hashMapOf(
                    Pair(JayState.DATA_RCV, 0),
                    Pair(JayState.DATA_SND, 0),
                    Pair(JayState.COMPUTE, 0)
            )

    fun setState(state: JayState) {
        if (state in activeStates.keys) activeStates[state]!!.inc()
    }

    fun unsetState(state: JayState) {
        if (state in activeStates.keys) {
            activeStates[state]!!.dec()
            assert(activeStates[state]!! > 0) { throw AssertionError("Cannot unset $state without setting it first") }
        }
    }

    fun startRecording() {
        recordJayStates()
    }

    fun stopRecording() {
        recordingFlag.set(false)
    }

    fun clear() {
        synchronized(SET_LOCK) {
            recordedStates.clear()
        }
    }

    fun getRecordings(): Set<ActiveState> {
        return recordedStates
    }

    fun getAndEraseRecordings(): Set<ActiveState> {
        var retRecordedStates: Set<ActiveState>
        synchronized(SET_LOCK) {
            retRecordedStates = recordedStates
            recordedStates.clear()
        }
        return retRecordedStates
    }

    fun getJayStates(): ActiveState {
        val activeStatesSet = LinkedHashSet<JayState>()
        var idle = true
        JayState.values().forEach { state ->
            if (activeStates[state]!! > 0) {
                activeStatesSet.add(state)
                idle = false
            }
        }
        if (idle) activeStatesSet.add(JayState.IDLE)
        return ActiveState(System.currentTimeMillis(), activeStatesSet)
    }


    private fun recordJayStates(): Boolean {
        if (!recordingFlag.compareAndSet(false, true)) return false
        Thread {
            do {
                /*val activeStatesSet = LinkedHashSet<JayState>()
                var idle = true
                JayState.values().forEach { state ->
                    if (activeStates[state]!! > 0) {
                        activeStatesSet.add(state)
                        idle = false
                    }
                }
                if (idle) activeStatesSet.add(JayState.IDLE)*/
                synchronized(SET_LOCK) {
                    recordedStates.add(getJayStates())
                }
                Thread.sleep(stateRecordInterval)
            } while (recordingFlag.get())
        }.start()
        return true
    }
}


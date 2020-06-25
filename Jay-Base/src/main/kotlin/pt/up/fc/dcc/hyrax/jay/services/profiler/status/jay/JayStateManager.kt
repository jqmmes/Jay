package pt.up.fc.dcc.hyrax.jay.services.profiler.status.jay

import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

internal object JayStateManager {

    private val recordingFlag: AtomicBoolean = AtomicBoolean(false)
    private val recordedStates: MutableSet<ActiveState> = LinkedHashSet()
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

    fun stopRecording() {
        recordingFlag.set(false)
    }

    fun clear() {
        synchronized(SET_LOCK) {
            recordedStates.clear()
        }
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


}


package pt.up.fc.dcc.hyrax.jay.services.profiler.status.jay

import pt.up.fc.dcc.hyrax.jay.logger.JayLogger
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
                    Pair(JayState.COMPUTE, 0),
                    Pair(JayState.MULTICAST_LISTEN, 0),
                    Pair(JayState.MULTICAST_ADVERTISE, 0)
            )

    fun setState(state: JayState) {
        JayLogger.logInfo("SET_STATE", "", "STATE=$state")
        if (state in activeStates.keys) {
            JayLogger.logInfo("SET_STATE", "", "INC_STATE=$state")
            activeStates[state] = activeStates[state]!!.inc()
        }
        JayLogger.logInfo("STATES", "", "$activeStates")
    }

    fun unsetState(state: JayState) {
        JayLogger.logInfo("UNSET_STATE", "", "STATE=$state")
        if (state in activeStates.keys) {
            JayLogger.logInfo("SET_STATE", "", "DEC_STATE=$state")
            activeStates[state] = activeStates[state]!!.dec()
            assert(activeStates[state]!! > 0) { throw AssertionError("Cannot unset $state without setting it first") }
        }
        JayLogger.logInfo("STATES", "", "$activeStates")
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
            if (state != JayState.IDLE && activeStates[state]!! > 0) {
                activeStatesSet.add(state)
                idle = false
            }
        }
        if (idle) activeStatesSet.add(JayState.IDLE)
        return ActiveState(System.currentTimeMillis(), activeStatesSet)
    }

    fun genJayStates(includeState: JayState): Set<JayState> {
        val activeStatesSet = LinkedHashSet<JayState>()
        var idle = true
        JayState.values().forEach { state ->
            if (state != JayState.IDLE && (activeStates[state]!! > 0 || state == includeState)) {
                activeStatesSet.add(state)
                idle = false
            }
        }
        if (idle) activeStatesSet.add(JayState.IDLE)
        return activeStatesSet
    }
}


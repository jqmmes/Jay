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
            if (activeStates[state]!! > 0) {
                activeStates[state] = activeStates[state]!!.dec()
            } else {
                JayLogger.logWarn("INVALID_STATE", "", "CANNOT_DECREASE=$state")
            }
        }
        JayLogger.logInfo("STATES", "", "$activeStates")
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


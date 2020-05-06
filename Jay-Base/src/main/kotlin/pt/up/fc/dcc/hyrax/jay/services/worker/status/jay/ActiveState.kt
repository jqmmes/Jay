package pt.up.fc.dcc.hyrax.jay.services.worker.status.jay

data class ActiveState(val time: Long, val states: Set<JayState>)
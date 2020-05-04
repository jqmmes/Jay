package pt.up.fc.dcc.hyrax.jay.services.worker.status.cpu

data class CPUStat(val time: Long, val usage: Set<Int>)
package pt.up.fc.dcc.hyrax.odlib.utils

import org.apache.commons.collections4.queue.CircularFifoQueue

@Deprecated("Will be integrated into RemoteClient")
class LatencyMovingAverage(private var nAvg: Int = 5) {
    private var circularFIFO: CircularFifoQueue<Long> = CircularFifoQueue(this.nAvg)
    private var calculatedAvgLatency: Long = 0

    fun addLatency(millis: Long) {
        circularFIFO.add(millis)
        calculatedAvgLatency = circularFIFO.sum()/circularFIFO.size
    }

    fun getAvgLatency() : Long {
        return calculatedAvgLatency
    }
}
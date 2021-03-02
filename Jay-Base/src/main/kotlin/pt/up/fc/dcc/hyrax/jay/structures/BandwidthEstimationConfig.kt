package pt.up.fc.dcc.hyrax.jay.structures

data class BandwidthEstimationConfig(val bandwidth: BandwidthEstimationType, val workerTypes: Set<WorkerType>)

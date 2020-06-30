package pt.up.fc.dcc.hyrax.jay.services.profiler.status.device.sensors

interface SensorManager {
    fun getActiveSensors(): Set<String>
}
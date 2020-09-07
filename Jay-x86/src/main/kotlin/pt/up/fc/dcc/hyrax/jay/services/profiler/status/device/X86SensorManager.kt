package pt.up.fc.dcc.hyrax.jay.services.profiler.status.device

import pt.up.fc.dcc.hyrax.jay.services.profiler.status.device.sensors.SensorManager

object X86SensorManager : SensorManager {
    override fun getActiveSensors(): Set<String> {
        return emptySet()
    }

}

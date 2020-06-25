package pt.up.fc.dcc.hyrax.jay.services.profiler.status.jay

enum class JayState {
    IDLE,
    DATA_SND,
    DATA_RCV,
    COMPUTE,
    MULTICAST_ADVERTISE,
    MULTICAST_LISTEN
}
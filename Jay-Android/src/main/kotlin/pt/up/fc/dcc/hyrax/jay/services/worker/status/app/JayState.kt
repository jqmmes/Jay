package pt.up.fc.dcc.hyrax.jay.services.worker.status.app

enum class JayState {
    IDLE,
    DATA_SND,
    DATA_RCV,
    COMPUTE
}
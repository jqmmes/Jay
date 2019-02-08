package pt.up.fc.dcc.hyrax.odlib.enums

@Deprecated("Will be in proto")
enum class ReturnStatus(val code: Int) {
    Success(0),
    Error(1),
    Waiting(2)
}
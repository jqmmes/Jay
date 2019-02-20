package pt.up.fc.dcc.hyrax.odlib.enums

enum class ReturnStatus(val code: Int) {
    Success(0),
    Error(1),
    Waiting(2)
}
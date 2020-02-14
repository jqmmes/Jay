package pt.up.fc.dcc.hyrax.jay.structures

import pt.up.fc.dcc.hyrax.jay.protoc.JayProto

data class Detection(val score: Float, val class_: Int) {
    internal constructor(detection: JayProto.Detection?) : this(detection!!.score, detection.class_)
}
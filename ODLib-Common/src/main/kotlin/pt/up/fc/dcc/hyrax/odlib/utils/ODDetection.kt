package pt.up.fc.dcc.hyrax.odlib.utils

import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto

data class ODDetection(val score : Float, val class_: Int) {
    internal constructor(detection: ODProto.Detection?) : this(detection!!.score, detection.class_)
}
/*
 * Copyright (c) 2021 University of Porto/Faculty of Sciences (UP/FCUP).
 */

package pt.up.fc.dcc.hyrax.jay.structures

import pt.up.fc.dcc.hyrax.jay.proto.JayTensorFlowProto.Detection

data class Detection(val score: Float, val class_: Int) {
    internal constructor(detection: Detection?) : this(detection!!.score, detection.class_)
}
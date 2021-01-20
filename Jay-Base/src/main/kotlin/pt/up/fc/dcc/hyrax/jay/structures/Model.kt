/*
 * Copyright (c) 2021 University of Porto/Faculty of Sciences (UP/FCUP).
 */

package pt.up.fc.dcc.hyrax.jay.structures

import pt.up.fc.dcc.hyrax.jay.proto.JayTensorFlowProto.Model as JayModel

data class Model(val modelId: Int,
                 val modelName: String,
                 val remoteUrl: String? = null,
                 var downloaded: Boolean = false,
                 var isQuantized: Boolean = false,
                 var inputSize: Int = 320) {

    internal constructor(request: JayModel?) : this(request!!.id, request.name, request.url, request.downloaded)

    fun getProto(): JayModel {
        return JayModel.newBuilder().setId(modelId).setName(modelName).setUrl(remoteUrl).setDownloaded(downloaded).setIsQuantized(isQuantized).setInputSize(inputSize).build()
    }
}
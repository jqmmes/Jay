package pt.up.fc.dcc.hyrax.odlib.structures

import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto

data class Model(val modelId: Int,
                 val modelName: String,
                 val remoteUrl: String? = null,
                 var downloaded : Boolean = false) {

    internal constructor(request: ODProto.Model?) : this (request!!.id, request.name, request.url, request.downloaded)

    internal fun getProto() : ODProto.Model {
        return ODProto.Model.newBuilder().setId(modelId).setName(modelName).setUrl(remoteUrl).setDownloaded(downloaded).build()
    }
}
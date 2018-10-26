package pt.up.fc.dcc.hyrax.odlib.utils

data class ODModel(val modelId: Int,
                   val modelName: String,
                   val remoteUrl: String? = null,
                   var downloaded : Boolean = false)
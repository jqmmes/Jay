package pt.up.fc.dcc.hyrax.odlib

open class ODModel(val modelId: Int, val modelName: String, val remoteUrl: String? = null, var downloaded : Boolean = false) {

    lateinit var graphLocation: String
}
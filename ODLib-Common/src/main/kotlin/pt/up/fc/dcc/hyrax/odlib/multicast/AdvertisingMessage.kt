package pt.up.fc.dcc.hyrax.odlib.multicast

import java.io.Serializable

class AdvertisingMessage(val msgType: Int, val data: ByteArray) : Serializable
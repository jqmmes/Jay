package pt.up.fc.dcc.hyrax.odlib.interfaces

import pt.up.fc.dcc.hyrax.odlib.ODUtils

interface RemoteODCallback{
    var id: Long
    fun onNewResult(resultList: List<ODUtils.ODDetection?>)
}
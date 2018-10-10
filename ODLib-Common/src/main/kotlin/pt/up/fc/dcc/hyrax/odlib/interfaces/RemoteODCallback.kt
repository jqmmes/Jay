package pt.up.fc.dcc.hyrax.odlib.interfaces

import pt.up.fc.dcc.hyrax.odlib.utils.ODUtils

interface RemoteODCallback{
    var id: Long
    fun onNewResult(resultList: List<ODUtils.ODDetection?>)
}
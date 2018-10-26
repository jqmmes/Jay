package pt.up.fc.dcc.hyrax.odlib.interfaces

import pt.up.fc.dcc.hyrax.odlib.utils.ODDetection

interface JobResultCallback{
    var id: Long
    fun onNewResult(resultList: List<ODDetection?>)
}
package pt.up.fc.dcc.hyrax.odlib.interfaces

import pt.up.fc.dcc.hyrax.odlib.ODUtils

@FunctionalInterface
interface ODCallback {
    fun onNewResult(resultList: List<ODUtils.ODDetection?>)
}
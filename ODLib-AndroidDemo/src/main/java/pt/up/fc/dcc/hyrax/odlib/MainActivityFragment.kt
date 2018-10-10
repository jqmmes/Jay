package pt.up.fc.dcc.hyrax.odlib

import android.support.v4.app.Fragment
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import pt.up.fc.dcc.hyrax.odlib.utils.ODLogger

/**
 * A placeholder fragment containing a simple view.
 */
class MainActivityFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_main, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ODLogger.logInfo("Fragment onCreateView")
        //var lib = ODLib(context!!)
        //lib.startODService()
        //lib.getClient()
    }
}

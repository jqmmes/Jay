package pt.up.fc.dcc.hyrax.odlib

import pt.up.fc.dcc.hyrax.odlib.interfaces.AbstractODLib
import pt.up.fc.dcc.hyrax.odlib.tensorflow.DroidTensorFlow
import android.content.Context

class ODLib(var context : Context) : AbstractODLib(DroidTensorFlow(context))
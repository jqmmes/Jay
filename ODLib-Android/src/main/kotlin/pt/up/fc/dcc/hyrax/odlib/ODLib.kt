package pt.up.fc.dcc.hyrax.odlib

import pt.up.fc.dcc.hyrax.odlib.interfaces.AbstractODLib
import pt.up.fc.dcc.hyrax.odlib.tensorflow.DroidTensorFlow

class ODLib : AbstractODLib(DroidTensorFlow())
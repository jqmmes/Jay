package pt.up.fc.dcc.hyrax.odlib

import pt.up.fc.dcc.hyrax.odlib.interfaces.AbstractODLib
import pt.up.fc.dcc.hyrax.odlib.tensorflow.CloudletTensorFlow

class ODLib : AbstractODLib(CloudletTensorFlow())
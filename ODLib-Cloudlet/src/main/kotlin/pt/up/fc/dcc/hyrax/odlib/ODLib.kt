package pt.up.fc.dcc.hyrax.odlib

import pt.up.fc.dcc.hyrax.odlib.interfaces.ODLib
import pt.up.fc.dcc.hyrax.odlib.tensorflow.CloudletTensorFlow


class ODLib : ODLib(CloudletTensorFlow())
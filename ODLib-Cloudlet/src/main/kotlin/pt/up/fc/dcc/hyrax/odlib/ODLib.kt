package pt.up.fc.dcc.hyrax.odlib

import pt.up.fc.dcc.hyrax.odlib.interfaces.DetectObjects
import pt.up.fc.dcc.hyrax.odlib.tensorflow.CloudletTensorFlow


class ODLib : AbstractODLib(CloudletTensorFlow()) {
    override fun getDetector(): DetectObjects {
        return (localDetector as CloudletTensorFlow)
    }
}
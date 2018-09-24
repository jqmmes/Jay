package pt.up.fc.dcc.hyrax.odlib

import org.kamranzafar.jtar.TarEntry
import org.kamranzafar.jtar.TarInputStream
import pt.up.fc.dcc.hyrax.odlib.interfaces.AbstractODLib
import pt.up.fc.dcc.hyrax.odlib.tensorflow.CloudletTensorFlow
import java.io.*
import java.net.URL
import java.nio.channels.Channels


class ODLib : AbstractODLib(CloudletTensorFlow())
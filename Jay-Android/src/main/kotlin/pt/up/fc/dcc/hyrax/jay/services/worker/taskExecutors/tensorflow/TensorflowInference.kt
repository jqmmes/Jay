/*
 * Copyright (c) 2021 University of Porto/Faculty of Sciences (UP/FCUP).
 */

package pt.up.fc.dcc.hyrax.jay.services.worker.taskExecutors.tensorflow

import android.content.res.AssetManager
import org.tensorflow.Graph
import org.tensorflow.Session
import org.tensorflow.Tensor
import org.tensorflow.contrib.android.RunStats
import org.tensorflow.types.UInt8
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.FloatBuffer

class TensorflowInference {
    private val modelName: String
    private val g: Graph
    private val session: Session
    private var runner: Session.Runner? = null
    private val feedNames: MutableList<String> = ArrayList()
    private val feedTensors: MutableList<Tensor<*>> = ArrayList()
    private val fetchNames: MutableList<String> = ArrayList()
    private var fetchTensors: MutableList<Tensor<*>> = ArrayList()
    private var runStats: RunStats? = null

    constructor(assetManager: AssetManager, model: String) {
        this.prepareNativeRuntime()
        this.modelName = model
        this.g = Graph()
        this.session = Session(this.g)
        this.runner = this.session.runner()
        val hasAssetPrefix = model.startsWith("file:///android_asset/")
        val `is`: Any? = try {
            val assetName = if (hasAssetPrefix) model.split("file:///android_asset/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[1] else model
            assetManager.open(assetName)
        } catch (var9: IOException) {
            if (hasAssetPrefix) throw RuntimeException("Failed to load model from '$model'", var9)
            try {
                FileInputStream(model)
            } catch (var8: IOException) {
                throw RuntimeException("Failed to load model from '$model'", var9)
            }
        }

        try {
            val graphDef = ByteArray((`is` as InputStream).available())
            val numBytesRead = `is`.read(graphDef)
            if (numBytesRead != graphDef.size) {
                throw IOException("read error: read only " + numBytesRead + " of the graph, expected to read " + graphDef.size)
            } else {
                this.loadGraph(graphDef, this.g)
                `is`.close()
            }
        } catch (var7: IOException) {
            throw RuntimeException("Failed to load model from '$model'", var7)
        }

    }

    constructor(g: Graph) {
        this.prepareNativeRuntime()
        this.modelName = ""
        this.g = g
        this.session = Session(g)
        this.runner = this.session.runner()
    }

    fun run(outputNames: Array<String>, enableStats: Boolean) {
        this.closeFetches()
        val var5 = outputNames.size

        var var6 = 0
        var t: String
        while (var6 < var5) {
            t = outputNames[var6]
            this.fetchNames.add(t)
            val tid = TensorId.parse(t)
            this.runner!!.fetch(tid.name, tid.outputIndex)
            ++var6
        }

        try {
            if (enableStats) {
                val r = this.runner!!.setOptions(RunStats.runOptions()).runAndFetchMetadata()
                this.fetchTensors = r.outputs
                if (this.runStats == null) {
                    this.runStats = RunStats()
                }
                this.runStats!!.add(r.metadata)
            } else {
                this.fetchTensors = this.runner!!.run()
            }
        } catch (var12: RuntimeException) {
            throw var12
        } finally {
            this.closeFeeds()
            this.runner = this.session.runner()
        }
    }

    fun graph(): Graph {
        return this.g
    }

    fun close() {
        this.closeFeeds()
        this.closeFetches()
        this.session.close()
        this.g.close()
        if (this.runStats != null) {
            this.runStats!!.close()
        }
        this.runStats = null
    }

    fun closeSession() {
        this.closeFeeds()
        this.closeFetches()
        this.session.close()
        if (this.runStats != null) {
            this.runStats!!.close()
        }
        this.runStats = null
    }

    fun fetch(outputName: String, dst: FloatArray) {
        this.getTensor(outputName).writeTo(FloatBuffer.wrap(dst))
    }

    private fun prepareNativeRuntime() {
        try {
            RunStats()
        } catch (var4: UnsatisfiedLinkError) {
            try {
                System.loadLibrary("tensorflow_inference")
            } catch (var3: UnsatisfiedLinkError) {
                throw RuntimeException("Native TF methods not found; check that the correct native libraries are present in the APK.")
            }
        }
    }

    private fun addFeed(inputName: String, t: Tensor<*>) {
        val tid = TensorId.parse(inputName)
        this.runner!!.feed(tid.name, tid.outputIndex, t)
        this.feedNames.add(inputName)
        this.feedTensors.add(t)
    }

    fun feed(inputName: String, src: ByteArray, vararg dims: Long) {
        this.addFeed(inputName, Tensor.create(UInt8::class.java, dims, ByteBuffer.wrap(src)))
    }

    @Throws(IOException::class)
    private fun loadGraph(graphDef: ByteArray, g: Graph) {
        try {
            g.importGraphDef(graphDef)
        } catch (var7: IllegalArgumentException) {
            throw IOException("Not a valid TensorFlow Graph serialization: " + var7.message)
        }
    }

    private fun getTensor(outputName: String): Tensor<*> {
        var i = 0

        val var3 = this.fetchNames.iterator()
        while (var3.hasNext()) {
            val n = var3.next()
            if (n == outputName) {
                return this.fetchTensors[i]
            }
            ++i
        }
        throw RuntimeException("Node '$outputName' was not provided to run(), so it cannot be read")
    }

    private fun closeFeeds() {
        val var1 = this.feedTensors.iterator()

        while (var1.hasNext()) {
            val t = var1.next()
            t.close()
        }

        this.feedTensors.clear()
        this.feedNames.clear()
    }

    private fun closeFetches() {
        val var1 = this.fetchTensors.iterator()
        while (var1.hasNext()) {
            var1.next().close()
        }
        this.fetchTensors.clear()
        this.fetchNames.clear()
    }

    private class TensorId private constructor() {
        lateinit var name: String
        var outputIndex: Int = 0

        companion object {

            fun parse(name: String): TensorId {
                val tid = TensorId()
                // val colonIndex = name.lastIndexOf(58)
                val colonIndex = name.lastIndexOf(58.toChar())
                return if (colonIndex < 0) {
                    tid.outputIndex = 0
                    tid.name = name
                    tid
                } else {
                    try {
                        tid.outputIndex = Integer.parseInt(name.substring(colonIndex + 1))
                        tid.name = name.substring(0, colonIndex)
                    } catch (var4: NumberFormatException) {
                        tid.outputIndex = 0
                        tid.name = name
                    }
                    tid
                }
            }
        }
    }
}

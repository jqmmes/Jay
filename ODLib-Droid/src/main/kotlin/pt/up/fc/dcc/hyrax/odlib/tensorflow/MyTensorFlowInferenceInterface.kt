package pt.up.fc.dcc.hyrax.odlib.tensorflow

import android.content.res.AssetManager
import org.tensorflow.contrib.android.RunStats
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.util.ArrayList
import java.nio.ByteBuffer
import java.nio.DoubleBuffer
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.nio.LongBuffer
import org.tensorflow.Graph
import org.tensorflow.Operation
import org.tensorflow.Session
import org.tensorflow.Tensor
import org.tensorflow.Tensors
import org.tensorflow.types.UInt8

@Suppress("unused", "MemberVisibilityCanBePrivate")
class MyTensorFlowInferenceInterface {
    private val modelName: String
    private val g: Graph
    private val session: Session
    private var runner: Session.Runner? = null
    private val feedNames: MutableList<String> = ArrayList()
    private val feedTensors: MutableList<Tensor<*>> = ArrayList()
    private val fetchNames: MutableList<String> = ArrayList()
    private var fetchTensors: MutableList<Tensor<*>> = ArrayList()
    private var runStats: RunStats? = null

    val statString: String
        get() = if (this.runStats == null) "" else this.runStats!!.summary()

    constructor(assetManager: AssetManager, model: String) {
        this.prepareNativeRuntime()
        this.modelName = model
        this.g = Graph()
        this.session = Session(this.g)
        this.runner = this.session.runner()
        val hasAssetPrefix = model.startsWith("file:///android_asset/")
        val `is`: Any? = try {
            val aname = if (hasAssetPrefix) model.split("file:///android_asset/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[1] else model
            assetManager.open(aname)
        } catch (var9: IOException) {
            if (hasAssetPrefix) {
                throw RuntimeException("Failed to load model from '$model'", var9)
            }

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
                //Log.i("MyTensorFlowInferenceInterface", "Successfully loaded model from '$model'")
            }
        } catch (var7: IOException) {
            throw RuntimeException("Failed to load model from '$model'", var7)
        }

    }

    constructor(`is`: InputStream) {
        this.prepareNativeRuntime()
        this.modelName = ""
        this.g = Graph()
        this.session = Session(this.g)
        this.runner = this.session.runner()

        try {
            val baosInitSize = if (`is`.available() > 16384) `is`.available() else 16384
            val baos = ByteArrayOutputStream(baosInitSize)
            val buf = ByteArray(16384)

            var numBytesRead: Int  = `is`.read(buf, 0, buf.size)
            while (numBytesRead != -1) {
                baos.write(buf, 0, numBytesRead)
                numBytesRead = `is`.read(buf, 0, buf.size)
            }

            val graphDef = baos.toByteArray()

            this.loadGraph(graphDef, this.g)
            //Log.i("MyTensorFlowInferenceInterface", "Successfully loaded model from the input stream")
        } catch (var7: IOException) {
            throw RuntimeException("Failed to load model from the input stream", var7)
        }

    }

    constructor(g: Graph) {
        this.prepareNativeRuntime()
        this.modelName = ""
        this.g = g
        this.session = Session(g)
        this.runner = this.session.runner()
    }

    fun run(outputNames: Array<String>) {
        this.run(outputNames, false)
    }

    fun run(outputNames: Array<String>, enableStats: Boolean) {
        this.run(outputNames, enableStats, arrayOf())
    }

    fun run(outputNames: Array<String>, enableStats: Boolean, targetNodeNames: Array<String>) {
        this.closeFetches()
        var var4 = outputNames
        var var5 = outputNames.size

        var var6 = 0
        var t: String
        while (var6 < var5) {
            t = var4[var6]
            this.fetchNames.add(t)
            val tid = MyTensorFlowInferenceInterface.TensorId.parse(t)
            this.runner!!.fetch(tid.name, tid.outputIndex)
            ++var6
        }

        var4 = targetNodeNames
        var5 = targetNodeNames.size

        var6 = 0
        while (var6 < var5) {
            t = var4[var6]
            this.runner!!.addTarget(t)
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
            //Log.e("MyTensorFlowInferenceInterface", "Failed to run TensorFlow inference with inputs:[" + TextUtils.join(", ", this.feedNames) + "], outputs:[" + TextUtils.join(", ", this.fetchNames) + "]")
            throw var12
        } finally {
            this.closeFeeds()
            this.runner = this.session.runner()
        }

    }

    fun graph(): Graph {
        return this.g
    }

    fun graphOperation(operationName: String): Operation {
        val operation = this.g.operation(operationName)
        return operation
                ?: throw RuntimeException("Node '" + operationName + "' does not exist in model '" + this.modelName + "'")
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

    fun feed(inputName: String, src: BooleanArray, vararg dims: Long) {
        val b = ByteArray(src.size)

        for (i in src.indices) {
            b[i] = (if (src[i]) 1 else 0).toByte()
        }

        this.addFeed(inputName, Tensor.create(Boolean::class.java, dims, ByteBuffer.wrap(b)))
    }

    fun feed(inputName: String, src: FloatArray, vararg dims: Long) {
        this.addFeed(inputName, Tensor.create(dims, FloatBuffer.wrap(src)))
    }

    fun feed(inputName: String, src: IntArray, vararg dims: Long) {
        this.addFeed(inputName, Tensor.create(dims, IntBuffer.wrap(src)))
    }

    fun feed(inputName: String, src: LongArray, vararg dims: Long) {
        this.addFeed(inputName, Tensor.create(dims, LongBuffer.wrap(src)))
    }

    fun feed(inputName: String, src: DoubleArray, vararg dims: Long) {
        this.addFeed(inputName, Tensor.create(dims, DoubleBuffer.wrap(src)))
    }

    fun feed(inputName: String, src: ByteArray, vararg dims: Long) {
        this.addFeed(inputName, Tensor.create(UInt8::class.java, dims, ByteBuffer.wrap(src)))
    }

    fun feedString(inputName: String, src: ByteArray) {
        this.addFeed(inputName, Tensors.create(src))
    }

    fun feedString(inputName: String, src: Array<ByteArray>) {
        this.addFeed(inputName, Tensors.create(src))
    }

    fun feed(inputName: String, src: FloatBuffer, vararg dims: Long) {
        this.addFeed(inputName, Tensor.create(dims, src))
    }

    fun feed(inputName: String, src: IntBuffer, vararg dims: Long) {
        this.addFeed(inputName, Tensor.create(dims, src))
    }

    fun feed(inputName: String, src: LongBuffer, vararg dims: Long) {
        this.addFeed(inputName, Tensor.create(dims, src))
    }

    fun feed(inputName: String, src: DoubleBuffer, vararg dims: Long) {
        this.addFeed(inputName, Tensor.create(dims, src))
    }

    fun feed(inputName: String, src: ByteBuffer, vararg dims: Long) {
        this.addFeed(inputName, Tensor.create(UInt8::class.java, dims, src))
    }

    fun fetch(outputName: String, dst: FloatArray) {
        this.fetch(outputName, FloatBuffer.wrap(dst))
    }

    fun fetch(outputName: String, dst: IntArray) {
        this.fetch(outputName, IntBuffer.wrap(dst))
    }

    fun fetch(outputName: String, dst: LongArray) {
        this.fetch(outputName, LongBuffer.wrap(dst))
    }

    fun fetch(outputName: String, dst: DoubleArray) {
        this.fetch(outputName, DoubleBuffer.wrap(dst))
    }

    fun fetch(outputName: String, dst: ByteArray) {
        this.fetch(outputName, ByteBuffer.wrap(dst))
    }

    fun fetch(outputName: String, dst: FloatBuffer) {
        this.getTensor(outputName).writeTo(dst)
    }

    fun fetch(outputName: String, dst: IntBuffer) {
        this.getTensor(outputName).writeTo(dst)
    }

    fun fetch(outputName: String, dst: LongBuffer) {
        this.getTensor(outputName).writeTo(dst)
    }

    fun fetch(outputName: String, dst: DoubleBuffer) {
        this.getTensor(outputName).writeTo(dst)
    }

    fun fetch(outputName: String, dst: ByteBuffer) {
        this.getTensor(outputName).writeTo(dst)
    }

    private fun prepareNativeRuntime() {
        //Log.i("MyTensorFlowInferenceInterface", "Checking to see if TensorFlow native methods are already loaded")

        try {
            RunStats()
            //Log.i("MyTensorFlowInferenceInterface", "TensorFlow native methods already loaded")
        } catch (var4: UnsatisfiedLinkError) {
            //Log.i("MyTensorFlowInferenceInterface", "TensorFlow native methods not found, attempting to load via tensorflow_inference")

            try {
                System.loadLibrary("tensorflow_inference")
                //Log.i("MyTensorFlowInferenceInterface", "Successfully loaded TensorFlow native methods (RunStats error may be ignored)")
            } catch (var3: UnsatisfiedLinkError) {
                throw RuntimeException("Native TF methods not found; check that the correct native libraries are present in the APK.")
            }

        }

    }

    @Throws(IOException::class)
    private fun loadGraph(graphDef: ByteArray, g: Graph) {
        //val startMs = System.currentTimeMillis()
        
        try {
            g.importGraphDef(graphDef)
        } catch (var7: IllegalArgumentException) {
            throw IOException("Not a valid TensorFlow Graph serialization: " + var7.message)
        }

        //val endMs = System.currentTimeMillis()
        //Log.i("MyTensorFlowInferenceInterface", "Model load took " + (endMs - startMs) + "ms, TensorFlow version: " + TensorFlow.version())
    }

    private fun addFeed(inputName: String, t: Tensor<*>) {
        val tid = MyTensorFlowInferenceInterface.TensorId.parse(inputName)
        this.runner!!.feed(tid.name, tid.outputIndex, t)
        this.feedNames.add(inputName)
        this.feedTensors.add(t)
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
        internal lateinit var name: String
        internal var outputIndex: Int = 0

        companion object {

            fun parse(name: String): MyTensorFlowInferenceInterface.TensorId {
                val tid = MyTensorFlowInferenceInterface.TensorId()
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

    companion object {
        private const val TAG = "MyTensorFlowInferenceInterface"
        private const val ASSET_FILE_PREFIX = "file:///android_asset/"
    }
}

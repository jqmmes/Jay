package pt.up.fc.dcc.hyrax.jay

import pt.up.fc.dcc.hyrax.jay.interfaces.FileSystemAssistant
import pt.up.fc.dcc.hyrax.jay.protoc.JayProto
import pt.up.fc.dcc.hyrax.jay.services.broker.BrokerService
import pt.up.fc.dcc.hyrax.jay.services.broker.grpc.BrokerGRPCClient
import pt.up.fc.dcc.hyrax.jay.services.scheduler.SchedulerService
import pt.up.fc.dcc.hyrax.jay.services.worker.WorkerService
import pt.up.fc.dcc.hyrax.jay.structures.Job
import pt.up.fc.dcc.hyrax.jay.structures.Model
import pt.up.fc.dcc.hyrax.jay.utils.JaySettings
import java.lang.Thread.sleep
import java.util.*
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

abstract class AbstractJay {

    protected val broker = BrokerGRPCClient("127.0.0.1")

    init {
        JaySettings.DEVICE_ID = UUID.randomUUID().toString()
    }

    internal companion object {
        val executorPool: ThreadPoolExecutor = ThreadPoolExecutor(5, 30, Long.MAX_VALUE, TimeUnit.MILLISECONDS, LinkedBlockingQueue<Runnable>())
    }

    fun listModels(callback: ((Set<Model>) -> Unit)? = null) {
        broker.getModels(callback)
    }

    fun listSchedulers(callback: ((Set<Pair<String, String>>) -> Unit)? = null) {
        broker.getSchedulers(callback)
    }

    fun setModel(model: Model, callback: ((JayProto.Status) -> Unit)? = null) {
        broker.selectModel(model, callback)
    }

    fun setScheduler(id: String, completeCallback: (Boolean) -> Unit) {
        broker.setScheduler(id, completeCallback)
    }

    protected open fun startBroker(fsAssistant: FileSystemAssistant? = null) {
        BrokerService.start(fsAssistant = fsAssistant)
    }

    open fun startScheduler(fsAssistant: FileSystemAssistant? = null) {
        startBroker(fsAssistant)
        sleep(500)
        SchedulerService.start()
    }

    abstract fun startWorker()


    protected open fun stopBroker() {
        BrokerService.stop()
        stopScheduler()
        stopWorker()
    }

    open fun stopScheduler() {
        SchedulerService.stop()
    }

    open fun stopWorker() {
        BrokerService.announceMulticast(true)
        WorkerService.stop()
    }

    fun scheduleJob(data: ByteArray, result: ((JayProto.Results) -> Unit)? = null) {
        broker.scheduleJob(Job(data), result)
    }

    open fun destroy(keepServices: Boolean = false) {
        if (keepServices) return
        stopWorker()
        stopScheduler()
        stopBroker()
    }

    fun updateSmartWeights(computeWeight: Float, jobsWeight: Float, queueWeight: Float, batteryWeight: Float, bandwidthWeight: Float, callback: ((Boolean) -> Unit)) {
        broker.updateSmartSchedulerWeights(computeWeight, queueWeight, jobsWeight, batteryWeight, bandwidthWeight) {S ->
            callback(S?.code == JayProto.StatusCode.Success)
        }
    }
}
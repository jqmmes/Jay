package pt.up.fc.dcc.hyrax.odlib.services.broker

import pt.up.fc.dcc.hyrax.odlib.structures.Worker
import pt.up.fc.dcc.hyrax.odlib.grpc.GRPCServerBase
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto.Job
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto.Model
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto.Models
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto.Results
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto.Scheduler
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto.Schedulers
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto.Status
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto.Worker.Type
import pt.up.fc.dcc.hyrax.odlib.services.broker.grpc.BrokerGRPCServer
import pt.up.fc.dcc.hyrax.odlib.services.broker.multicast.MulticastAdvertiser
import pt.up.fc.dcc.hyrax.odlib.services.broker.multicast.MulticastListener
import pt.up.fc.dcc.hyrax.odlib.services.scheduler.grpc.SchedulerGRPCClient
import pt.up.fc.dcc.hyrax.odlib.services.worker.grpc.WorkerGRPCClient
import pt.up.fc.dcc.hyrax.odlib.utils.ODSettings
import java.lang.Thread.sleep
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto.Worker as ODWorker

object BrokerService {

    private var server: GRPCServerBase? = null
    private val workers: MutableMap<String, Worker> = hashMapOf()
    private val scheduler = SchedulerGRPCClient("127.0.0.1")
    private val worker = WorkerGRPCClient("127.0.0.1")
    private val local: Worker = Worker(address = "127.0.0.1", type = Type.LOCAL)
    private val cloud = Worker(address = ODSettings.cloudIp, type = Type.CLOUD)

    init {
        workers[local.id] = local
        workers[cloud.id] = cloud
        cloud.enableAutoStatusUpdate()
    }

    fun start(useNettyServer: Boolean = false) {
        server = BrokerGRPCServer(useNettyServer).start()
    }

    fun stop() {
        if (server != null) server!!.stop()
        cloud.disableAutoStatusUpdate()
    }

    internal fun executeJob(request: Job?, callback: ((Results?) -> Unit)? = null) {
        worker.execute(request, callback)
    }

    internal fun scheduleJob(request: Job?, callback: ((Results?) -> Unit)? = null) {
        scheduler.schedule(request) { W ->
            if (W == null) callback?.invoke(null) else workers[W.id]!!.grpc.executeJob(request, callback)
        }
    }

    private fun updateWorker(worker: ODProto.Worker?, latch: CountDownLatch) {
        scheduler.notify(worker) { S ->
            println("updateWorkers::notify Complete ${S?.code?.name}")
            if (S?.code == ODProto.StatusCode.Error) {
                sleep(100)
                BrokerService.updateWorker(worker, latch)
            } else (latch.countDown())
        }
    }

    internal fun updateWorkers() {
        println("updateWorkers ${workers.size}")
        val countDownLatch = CountDownLatch(workers.size)
        for (worker in workers.values) {
            println("updateWorkers:: $worker")
            updateWorker(worker.getProto(), countDownLatch)
        }
        countDownLatch.await()
        println("updateWorkers::End")
    }

    internal fun getModels(callback: (Models) -> Unit) {
        worker.listModels(callback)
    }

    internal fun setModel(request: Model?, callback: ((Status?) -> Unit)? = null) {
        worker.selectModel(request, callback)
    }

    internal fun diffuseWorkerStatus() : CountDownLatch {
        val countDownLatch = CountDownLatch(1)
        val atomicLock = AtomicInteger(0)
        for (client in workers.values) {
            if (client.type == Type.REMOTE) {
                atomicLock.incrementAndGet()
                client.grpc.advertiseWorkerStatus(local.getProto()) {if (atomicLock.decrementAndGet() == 0) countDownLatch.countDown()}
            }
        }
        return countDownLatch
    }

    internal fun updateWorker(request: ODWorker?, updateCloud: Boolean = false) : CountDownLatch {
        val countDownLatch = CountDownLatch(1)
        if (updateCloud){
            cloud.updateStatus(request)
            scheduler.notify(cloud.getProto()) {countDownLatch.countDown()}
        } else {
            announceMulticast(worker = local.updateStatus(request))
            scheduler.notify(local.getProto()) {countDownLatch.countDown()}
        }
        return countDownLatch

    }

    internal fun receiveWorkerStatus(request: ODProto.Worker?, completeCallback: () -> Unit) {
        workers[request?.id]?.updateStatus(request)
        scheduler.notify(request) {completeCallback()}
    }

    fun getSchedulers(callback: ((Schedulers?) -> Unit)? = null) {
        scheduler.listSchedulers(callback)

    }

    fun setScheduler(request: Scheduler?, callback: ((Status?) -> Unit)? = null) {
        scheduler.setScheduler(request, callback)
    }

    internal fun listenMulticast(stopListener: Boolean = false) {
        if (stopListener) MulticastListener.stop()
        else MulticastListener.listen(callback = { W, A -> println("Packet received to process :: Callback"); checkWorker(W, A) })
    }

    private fun checkWorker(worker: ODProto.Worker?, address: String) {
        println("Check Worker $worker")
        if (worker == null) return
        if (worker.id !in workers) workers[worker.id] = Worker(worker, address)
        else workers[worker.id]?.updateStatus(worker)
        scheduler.notify(workers[worker.id]?.getProto()) {S -> println("Notified Scheduler $S")}
    }

    internal fun announceMulticast(stopAdvertiser: Boolean = false, worker: ODWorker? = null) {
        if (stopAdvertiser) MulticastAdvertiser.stop()
        else {
            val data = worker?.toByteArray() ?: local.getProto()?.toByteArray()
            if (MulticastAdvertiser.isRunning()) MulticastAdvertiser.setAdvertiseData(data)
            else MulticastAdvertiser.start(data)
        }
    }

    fun requestWorkerStatus(): ODProto.Worker? {
        return local.getProto()
    }
}

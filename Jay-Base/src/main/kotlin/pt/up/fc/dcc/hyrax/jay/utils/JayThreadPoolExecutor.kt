package pt.up.fc.dcc.hyrax.jay.utils

import pt.up.fc.dcc.hyrax.jay.logger.JayLogger
import java.util.*
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class JayThreadPoolExecutor(private val coreThreads: Int, private val maxThreads: Int, private val timeout: Long = 0,
                            private val unit: TimeUnit = TimeUnit.MILLISECONDS, maxQueueSize: Int = Int.MAX_VALUE) {

    private companion object {

        val atomicInteger = AtomicInteger(0)
        var running = false
        val TAKE_LOCK = Object()
        val PUT_LOCK = Object()
    }

    private var runnableQueue: BlockingQueue<() -> Unit> = LinkedBlockingQueue<() -> Unit>()
    private val coreThreadSet: HashSet<Thread> = hashSetOf()
    private val extraThreadSet = LinkedBlockingQueue<Thread>(Int.MAX_VALUE)
    private val mainThread = thread(start = false) {
        do {
            println("----> TAKE")
            val r = runnableQueue.take()
            println("----> TAKEN")
            semaphore.acquire()
            thread {
                r()
                semaphore.release()
            }.start()
        } while (running)
    }
    private val semaphore: Semaphore = Semaphore(coreThreads)

    init {
        running = true
        /*repeat(coreThreads) {
            coreThreadSet.add(getThread())
        }*/

        mainThread.start()
    }

    private fun getThread(timeout: Long? = null, timeUnit: TimeUnit? = null): Thread {
        val t = thread(start = false) {
            val ran = Random().nextInt()
            do {
                var executing = false
                try {
                    var r: (() -> Unit)? = null
                    //synchronized(TAKE_LOCK) {
                    println("----> LOCK $ran")
                    r = if (timeout == null) runnableQueue.take() else runnableQueue.poll(timeout, unit)
                    //}
                    println("----> UNLOCK $ran")
                    if (r == null && timeout != null) break
                    atomicInteger.incrementAndGet()
                    executing = true
                    println("----> THREAD_POOL_EXECUTING\t$ran")
                    r()
                    atomicInteger.decrementAndGet()
                    println("----> THREAD_POOL_EXECUTING_COMPLETE\t$ran")
                } catch (e: InterruptedException) {
                    JayLogger.logWarn("THREAD_INTERRUPTED")
                    if (executing) atomicInteger.decrementAndGet()
                }
            } while (running)
            println("----> THREAD_POOL_COMPLETE\t$ran")
        }
        t.start()
        return t
    }

    fun getActiveThreadCount(): Int {
        var active = 0
        coreThreadSet.forEach { if (it.isAlive) active++ }
        extraThreadSet.forEach { if (it.isAlive) active++ }
        return active
    }

    fun getWorkingThreadCount(): Int {
        return coreThreads - semaphore.availablePermits()
    }

    fun submit(r: (() -> Unit)) {
        println("----> PUT")
        runnableQueue.put(r)
        println("----> PUTTED")
        /*synchronized(PUT_LOCK) {
            if (atomicInteger.get() in coreThreads until maxThreads) {
                extraThreadSet.put(getThread(timeout, unit))
            }
            runnableQueue.put(r)
        }*/
    }

    fun destroy() {
        running = false
        coreThreadSet.forEach {
            if (it.isAlive && !it.isInterrupted) it.interrupt()
        }
        extraThreadSet.forEach {
            if (it.isAlive && !it.isInterrupted) it.interrupt()
        }
    }
}
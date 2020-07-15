package pt.up.fc.dcc.hyrax.jay.utils

import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class JayThreadPoolExecutor(private val coreThreads: Int, maxQueueSize: Int = Int.MAX_VALUE) {

    private companion object {
        val atomicInteger = AtomicInteger(0)
        var running = false
    }

    private var runnableQueue: BlockingQueue<() -> Unit> = LinkedBlockingQueue<() -> Unit>(maxQueueSize)
    private val mainThread = thread(start = false) {
        do {
            val r = runnableQueue.take()
            semaphore.acquire()
            atomicInteger.incrementAndGet()
            thread {
                r()
                atomicInteger.decrementAndGet()
                semaphore.release()
            }
        } while (running)
    }
    private val semaphore: Semaphore = Semaphore(coreThreads)

    init {
        running = true
        mainThread.start()
    }

    fun getActiveThreadCount(): Int {
        return coreThreads
    }

    fun getWorkingThreadCount(): Int {
        return atomicInteger.get()
    }

    fun submit(r: (() -> Unit)) {
        if (running) runnableQueue.put(r)
    }

    fun destroy() {
        running = false
        if (mainThread.isAlive && !mainThread.isInterrupted) mainThread.interrupt()
        do {
            runnableQueue.take()
        } while (!runnableQueue.isEmpty())
    }
}
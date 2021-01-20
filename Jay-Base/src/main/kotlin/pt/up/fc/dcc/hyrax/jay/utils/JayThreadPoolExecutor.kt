/*
 * Copyright (c) 2021 University of Porto/Faculty of Sciences (UP/FCUP).
 */

package pt.up.fc.dcc.hyrax.jay.utils

import pt.up.fc.dcc.hyrax.jay.logger.JayLogger
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.random.Random

class JayThreadPoolExecutor(private val coreThreads: Int, maxQueueSize: Int = Int.MAX_VALUE) {

    private companion object {
        val atomicInteger = AtomicInteger(0)
        var running = false
    }

    private var runnableQueue: BlockingQueue<() -> Unit> = LinkedBlockingQueue(maxQueueSize)
    private val mainThread = thread(start = false) {
        do {
            val r = runnableQueue.take()
            semaphore.acquire()
            atomicInteger.incrementAndGet()
            try {
                thread {
                    val id = Random.nextInt()
                    JayLogger.logInfo("RUNNING_THREAD", "", "THREAD_ID=$id")
                    r()
                    atomicInteger.decrementAndGet()
                    semaphore.release()
                    JayLogger.logInfo("CLOSING_THREAD", "", "THREAD_ID=$id")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Thread.sleep(100)
                thread {
                    r()
                    atomicInteger.decrementAndGet()
                    semaphore.release()
                }
            }
            JayLogger.logInfo("SUBMIT_THREAD", "", "ACTIVE_THREADS=${atomicInteger.get()}",
                    "QUEUE_SIZE=${runnableQueue.size}")
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
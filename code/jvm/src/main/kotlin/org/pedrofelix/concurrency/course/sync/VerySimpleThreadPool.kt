package org.pedrofelix.concurrency.course.sync

import org.pedrofelix.concurrency.course.utils.NodeLinkedList
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.jvm.Throws

class VerySimpleThreadPool(
    private val maxThreads: Int,
) : Closeable {
    private val mutex = ReentrantLock()
    private var nOfThreads: Int = 0
    private var workItems = NodeLinkedList<Runnable>()
    private var closed = false
    private val joinCondition = mutex.newCondition()

    init {
        require(maxThreads > 0) { "maxThreads must be greater than zero" }
    }

    @Throws(RejectedExecutionException::class)
    fun execute(workItem: Runnable) {
        mutex.withLock {
            if (closed) {
                throw RejectedExecutionException()
            }
            // Check if there is any budget available to create new threads.
            if (nOfThreads < maxThreads) {
                // Budget available, so create a new worker thread and have it run `workItem`.
                Thread.ofPlatform().start {
                    workerThreadLoop(workItem)
                }
                nOfThreads += 1
            } else {
                // No budget available, add the `workItem` to the queue.
                workItems.addLast(workItem)
            }
        }
    }

    override fun close() =
        mutex.withLock {
            closed = true
            if (nOfThreads == 0) {
                joinCondition.signalAll()
            }
        }

    fun join(
        timeout: Long,
        timeoutUnits: TimeUnit,
    ): Boolean =
        mutex.withLock {
            var remainingNanos = timeoutUnits.toNanos(timeout)
            while (!closed || nOfThreads > 0) {
                remainingNanos = joinCondition.awaitNanos(remainingNanos)
                if (remainingNanos <= 0) {
                    return false
                }
            }
            return true
        }

    /**
     * Method call by the worker threads to obtain a new work item to run.
     */
    private fun getNextWorkItem(): GetNextWorkItemResult {
        mutex.withLock {
            if (workItems.notEmpty) {
                return GetNextWorkItemResult.WorkItem(workItems.getAndRemoveFirst().value)
            } else {
                // Update the state based on the fact that the worker thread will terminate.
                nOfThreads -= 1
                if (closed && nOfThreads == 0) {
                    joinCondition.signalAll()
                }
                return GetNextWorkItemResult.Terminate
            }
        }
    }

    private fun workerThreadLoop(initialWorkItem: Runnable) {
        var workItem = initialWorkItem
        while (true) {
            safeRun(workItem)
            workItem = when (val res = getNextWorkItem()) {
                is GetNextWorkItemResult.WorkItem -> res.item
                GetNextWorkItemResult.Terminate -> return
            }
        }
    }

    private sealed interface GetNextWorkItemResult {
        // Work item available, worker thread must run it
        data class WorkItem(
            val item: Runnable,
        ) : GetNextWorkItemResult

        // No work item available, worker thread must terminate
        data object Terminate : GetNextWorkItemResult
    }

    companion object {
        private val logger = LoggerFactory.getLogger(VerySimpleThreadPool::class.java)

        private fun safeRun(runnable: Runnable) {
            try {
                runnable.run()
            } catch (ex: Throwable) {
                logger.warn("Unexpected exception, ignoring it to keeping worker thread alive")
                // ignore
            }
        }
    }
}

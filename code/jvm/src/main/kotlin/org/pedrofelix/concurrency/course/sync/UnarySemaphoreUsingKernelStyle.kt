package org.pedrofelix.concurrency.course.sync

import org.pedrofelix.concurrency.course.utils.NodeLinkedList
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class UnarySemaphoreUsingKernelStyle(
    initialUnits: Int,
) {
    init {
        require(initialUnits >= 0) { "Initial units must not be negative" }
    }

    private val lock = ReentrantLock()
    private var units = initialUnits

    data class AcquireRequest(
        val condition: Condition,
        var isDone: Boolean = false,
    )

    private val acquireRequests = NodeLinkedList<AcquireRequest>()

    fun release() =
        lock.withLock {
            units += 1
            val headRequest = acquireRequests.headNode
            if (headRequest != null) {
                units -= 1
                acquireRequests.remove(headRequest)
                headRequest.value.condition.signal()
                headRequest.value.isDone = true
            }
        }

    fun tryAcquire(
        timeout: Long,
        timeoutUnits: TimeUnit,
    ): Boolean {
        lock.withLock {
            // fast-path
            if (units > 0) {
                units -= 1
                return true
            }
            // wait-path
            var timeoutInNanos = timeoutUnits.toNanos(timeout)
            val selfNode = acquireRequests.addLast(
                AcquireRequest(condition = lock.newCondition()),
            )
            while (true) {
                try {
                    timeoutInNanos = selfNode.value.condition.awaitNanos(timeoutInNanos)
                } catch (ex: InterruptedException) {
                    if (selfNode.value.isDone) {
                        // restores interrupt flag to true
                        // (because the InterruptedException throw clears it)
                        Thread.currentThread().interrupt()
                        return true
                    }
                    acquireRequests.remove(selfNode)
                    throw ex
                }
                // test for success
                if (selfNode.value.isDone) {
                    return true
                }
                // test for timeout
                if (timeoutInNanos <= 0) {
                    acquireRequests.remove(selfNode)
                    return false
                }
            }
        }
    }
}

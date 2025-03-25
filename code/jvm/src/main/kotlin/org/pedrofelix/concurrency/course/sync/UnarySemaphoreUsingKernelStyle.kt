package org.pedrofelix.concurrency.course.sync

import org.pedrofelix.concurrency.course.utils.NodeLinkedList
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Unary semaphore using _kernel-style:
 * - Each acquire operation is represented by a [AcquireRequest].
 * - These acquire requests are completed by release operations.
 *      - the acquire request is removed from the queue.
 *      - the `isDone` is set to true.
 * - Note that it is now impossible to simultaneously have units available and pending acquire requests.
 */
class UnarySemaphoreUsingKernelStyle(
    initialUnits: Int,
) : UnarySemaphore {
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

    override fun release() =
        lock.withLock {
            units += 1
            val headRequest = acquireRequests.headNode
            if (headRequest != null) {
                // NOTE how the release operation completes a pending acquire
                // by consuming a unit and marking the acquire request as done.
                // This is a characteristic of the _kernel-style_.
                units -= 1
                acquireRequests.remove(headRequest)
                headRequest.value.condition.signal()
                headRequest.value.isDone = true
            }
        }

    override fun tryAcquire(
        timeout: Long,
        timeoutUnit: TimeUnit,
    ): Boolean {
        lock.withLock {
            // fast-path
            if (units > 0) {
                units -= 1
                return true
            }
            // wait-path
            var timeoutInNanos = timeoutUnit.toNanos(timeout)
            val selfNode = acquireRequests.addLast(
                AcquireRequest(condition = lock.newCondition()),
            )
            while (true) {
                try {
                    timeoutInNanos = selfNode.value.condition.awaitNanos(timeoutInNanos)
                } catch (ex: InterruptedException) {
                    if (selfNode.value.isDone) {
                        // Too late to give up.
                        // Restores interrupt flag to true
                        // (because the InterruptedException throw clears it).
                        Thread.currentThread().interrupt()
                        return true
                    }
                    // When giving up, the request needs to be removed from the queue.
                    acquireRequests.remove(selfNode)
                    throw ex
                }
                // test for success
                if (selfNode.value.isDone) {
                    // NOTE that in case of success, the requesting thread
                    // exits the synchronizer *without* changing any state,
                    // because all the require state changes, namely decrementing the units,
                    // was already done by the _completing_ thread.
                    return true
                }
                // test for timeout
                if (timeoutInNanos <= 0) {
                    // When giving up, the request needs to be removed from the queue.
                    acquireRequests.remove(selfNode)
                    return false
                }
            }
        }
    }
}

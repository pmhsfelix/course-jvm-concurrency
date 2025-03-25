package org.pedrofelix.concurrency.course.sync

import org.pedrofelix.concurrency.course.utils.NodeLinkedList
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.time.Duration

/**
 * N-ary semaphore using a _kernel-style_ design and implementation.
 * - The number of units to acquire is stored in each [AcquireRequest] instance.
 * - It is now *possible* to simultaneously have available units and pending acquire requests,
 *   if the first acquire requests more than the available units.
 * - Also, an acquire operation giving up may create conditions to other acquire operations to complete successfully.
 */
class NArySemaphoreWithFairnessUsingKernelStyle(
    initialUnits: Long,
) {
    init {
        require(initialUnits >= 0) {
            "Initial units must not be negative"
        }
    }

    private val lock = ReentrantLock()

    data class AcquireRequest(
        var isDone: Boolean = false,
        val units: Long,
        val condition: Condition,
    ) {
        init {
            require(units > 0) { "requested units must be greater than zero" }
        }
    }

    private var units = initialUnits
    private val acquireRequests = NodeLinkedList<AcquireRequest>()

    fun release(releasedUnits: Long) =
        lock.withLock {
            units += releasedUnits
            completeAllPossible()
        }

    fun tryAcquire(
        requestedUnits: Long,
        timeout: Duration,
    ): Boolean {
        lock.withLock {
            var remainingTimeInNanos = timeout.inWholeNanoseconds
            // fast-path
            // available units and no other previous thread waiting
            if (units >= requestedUnits && acquireRequests.empty) {
                units -= requestedUnits
                return true
            }
            // wait-path
            val selfNode = acquireRequests.addLast(
                AcquireRequest(
                    units = requestedUnits,
                    condition = lock.newCondition(),
                ),
            )
            while (true) {
                try {
                    remainingTimeInNanos = selfNode.value.condition.awaitNanos(remainingTimeInNanos)
                } catch (e: InterruptedException) {
                    if (selfNode.value.isDone) {
                        Thread.currentThread().interrupt()
                        return true
                    }
                    acquireRequests.remove(selfNode)
                    completeAllPossible()
                    throw e
                }
                if (selfNode.value.isDone) {
                    return true
                }
                if (remainingTimeInNanos <= 0) {
                    acquireRequests.remove(selfNode)
                    completeAllPossible()
                    return false
                }
            }
        }
    }

    private fun completeAllPossible() {
        while (acquireRequests.headCondition { units >= it.units }) {
            val headRequest = acquireRequests.getAndRemoveFirst()
            headRequest.value.isDone = true
            headRequest.value.condition.signal()
            units -= headRequest.value.units
        }
    }
}

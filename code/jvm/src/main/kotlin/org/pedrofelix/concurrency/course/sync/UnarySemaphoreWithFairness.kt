package org.pedrofelix.concurrency.course.sync

import org.pedrofelix.concurrency.course.utils.NodeLinkedList
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Unary semaphore providing fairness on acquisition by maintaining a FIFO (First In First Out) list
 * of requesting threads.
 * Acquisition has two distinct paths:
 * - _fast path_ - an acquire succeeds without any wait if there are available units *and* there isn't any
 *   thread waiting.
 * - _wait path_ - an acquire success after an await if there are available units *and* the thread is the first
 *   in requesting list.
 *
 * Notice:
 * - In the wait-path, the requirement to be able to acquire a unit is the conjunction of:
 *      - Units being available.
 *      - The thread being at the front of the requesters list.
 * - The use of `Condition.signalAll`, since a unary `signal` does not ensure wake-up of the thread in the head of
 *   the request list.
 * - The need to do additional processing on cancellation, since changing the requesting list may create the condition
 *   for a thread to acquire a unit.
 *
 * Uses monitor-style - the releasing threads simply increment the number of available units and do a `signalAll`.
 */
class UnarySemaphoreWithFairness(
    initialUnits: Int,
) {
    init {
        require(initialUnits >= 0) { "Initial units must be non-negative." }
    }

    private val lock = ReentrantLock()
    private val condition = lock.newCondition()
    private var units = initialUnits
    private val requesters = NodeLinkedList<Thread>()

    fun tryAcquire(
        timeout: Long,
        timeoutUnits: TimeUnit,
    ): Boolean {
        lock.withLock {
            // fast-path
            if (units > 0 && requesters.empty) {
                units -= 1
                return true
            }
            // wait-path
            var timeoutInNanos = timeoutUnits.toNanos(timeout)
            val selfNode: NodeLinkedList.Node<Thread> = requesters.addLast(Thread.currentThread())
            while (true) {
                try {
                    timeoutInNanos = condition.awaitNanos(timeoutInNanos)
                } catch (ex: InterruptedException) {
                    requesters.remove(selfNode)
                    signalAllIfNeeded()
                    throw ex
                }
                if (units > 0 && requesters.isHeadNode(selfNode)) {
                    units -= 1
                    requesters.remove(selfNode)
                    signalAllIfNeeded()
                    return true
                }
                if (timeoutInNanos <= 0) {
                    requesters.remove(selfNode)
                    signalAllIfNeeded()
                    return false
                }
            }
        }
    }

    fun release() =
        lock.withLock {
            units += 1
            signalAllIfNeeded()
        }

    private fun signalAllIfNeeded() {
        if (units > 0 && requesters.notEmpty) {
            condition.signalAll()
        }
    }
}

package org.pedrofelix.concurrency.course.sync

import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Simple unary semaphore, i.e. can only acquire and release single units.
 * Does not provide any fairness guarantees.
 * Uses a simple monitor-style design:
 * - acquiring threads wait until `units > 0`, doing a condition wait between evaluations of `units`.
 * - releasing threads just increment `units` and signal the condition.
 * - cancellation via interruption propagates the signal, even if this is not strictly required
 *  (see https://docs.oracle.com/javase/specs/jls/se21/html/jls-17.html#jls-17.2.4) just to illustrate the problem.
 */
class SimpleUnarySemaphore(
    initialUnits: Int,
) : UnarySemaphore {
    private val lock = ReentrantLock()
    private val condition = lock.newCondition()
    private var units = initialUnits

    init {
        require(initialUnits >= 0) { "initial units must not be negative." }
    }

    override fun release() =
        lock.withLock {
            units += 1
            condition.signal()
        }

    @Throws(InterruptedException::class)
    override fun tryAcquire(
        timeout: Long,
        timeoutUnit: TimeUnit,
    ): Boolean =
        lock.withLock {
            var remainingTimeoutInNanos = timeoutUnit.toNanos(timeout)
            while (units == 0) {
                // Must be done after ensuring !(units > 0) to avoid losing signals
                if (remainingTimeoutInNanos < 0) {
                    return false
                }
                try {
                    remainingTimeoutInNanos = condition.awaitNanos(remainingTimeoutInNanos)
                } catch (e: InterruptedException) {
                    if (units > 0) {
                        condition.signal()
                    }
                    throw e
                }
            }
            units -= 1
            return true
        }
}

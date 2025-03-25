package org.pedrofelix.concurrency.course.sync

import org.pedrofelix.concurrency.course.utils.NodeLinkedList
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/*+
 * A FIFO (first-in first-out) queue without any internal capacity:
 * - An enqueue operation needs to wait for a dequeue operation to get the enqueued item.
 * - A dequeue operation needs to wait for an enqueue operation to provide an item.
 */
class SimpleMessageQueue<T> {
    private val mutex = ReentrantLock()

    /**
     * Represents an enqueue operation waiting for completion.
     */
    data class EnqueueRequest<T>(
        val condition: Condition,
        val item: T,
        var isDone: Boolean = false,
    )

    /**
     * Represents a dequeue operation waiting for completion.
     */
    data class DequeueRequest<T>(
        val condition: Condition,
        var item: T? = null,
    )

    private val enqueueRequests = NodeLinkedList<EnqueueRequest<T>>()

    private val dequeueRequests = NodeLinkedList<DequeueRequest<T>>()

    @Throws(InterruptedException::class)
    fun tryEnqueue(
        message: T,
        timeout: Long,
        timeoutUnit: TimeUnit,
    ): Boolean {
        mutex.withLock {
            // fast-path
            if (dequeueRequests.notEmpty) {
                val firstDequeueRequest = dequeueRequests.getAndRemoveFirst()
                firstDequeueRequest.value.item = message
                firstDequeueRequest.value.condition.signal()
                return true
            }
            // wait-path
            var timeoutInNanos = timeoutUnit.toNanos(timeout)
            val selfNode = enqueueRequests.addLast(
                EnqueueRequest(
                    condition = mutex.newCondition(),
                    item = message,
                ),
            )
            while (true) {
                try {
                    timeoutInNanos = selfNode.value.condition.awaitNanos(timeoutInNanos)
                } catch (ex: InterruptedException) {
                    if (selfNode.value.isDone) {
                        Thread.currentThread().interrupt()
                        return true
                    }
                    enqueueRequests.remove(selfNode)
                    // A cancellation does not create conditions to complete other requests
                    throw ex
                }
                // check for success
                if (selfNode.value.isDone) {
                    return true
                }
                // check for timeout
                if (timeoutInNanos <= 0) {
                    enqueueRequests.remove(selfNode)
                    // A cancellation does not create conditions to complete other requests
                    return false
                }
            }
        }
    }

    @Throws(InterruptedException::class)
    fun tryDequeue(
        timeout: Long,
        timeoutUnit: TimeUnit,
    ): T? {
        mutex.withLock {
            // fast-path
            if (enqueueRequests.notEmpty) {
                val firstInsertionRequest = enqueueRequests.getAndRemoveFirst()
                firstInsertionRequest.value.isDone = true
                firstInsertionRequest.value.condition.signal()
                return firstInsertionRequest.value.item
            }

            // wait-path
            var timeoutInNanos = timeoutUnit.toNanos(timeout)
            val selfNode = dequeueRequests.addLast(
                DequeueRequest(
                    condition = mutex.newCondition(),
                ),
            )
            while (true) {
                try {
                    timeoutInNanos = selfNode.value.condition.awaitNanos(timeoutInNanos)
                } catch (ex: InterruptedException) {
                    if (selfNode.value.item != null) {
                        Thread.currentThread().interrupt()
                        return selfNode.value.item
                    }
                    dequeueRequests.remove(selfNode)
                    // A cancellation does not create conditions to complete other requests
                    throw ex
                }
                // check for success
                if (selfNode.value.item != null) {
                    return selfNode.value.item
                }
                // check for timeout
                if (timeoutInNanos <= 0) {
                    dequeueRequests.remove(selfNode)
                    // A cancellation does not create conditions to complete other requests
                    return null
                }
            }
        }
    }
}

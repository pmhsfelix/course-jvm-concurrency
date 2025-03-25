package org.pedrofelix.concurrency.course.sync

import java.util.Collections
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VerySimpleThreadPoolTests {
    /**
     * The type of message inserted and retrieved from the queue
     */
    data class Message(
        val producerIx: Int,
        val messageIx: Int,
    )

    @Test
    fun `stress test`() {
        // given: a thread pool
        val threadPool = VerySimpleThreadPool(MAX_WORKER_THREADS)
        val synchronizedList = Collections.synchronizedList(mutableListOf<Message>())
        val activeWorkerThreads = AtomicInteger()
        val maxWorkerThreadsExceeded = AtomicBoolean()

        // when: N_OF_THREADS producers write into the queue
        val producers = List(N_OF_PRODUCERS) { producerIx ->
            Thread.ofPlatform().start {
                repeat(N_OF_REPS) { messageIx ->
                    val message = Message(producerIx, messageIx)
                    threadPool.execute {
                        val observedActiveWorkerThreads = activeWorkerThreads.incrementAndGet()
                        if (observedActiveWorkerThreads > MAX_WORKER_THREADS) {
                            maxWorkerThreadsExceeded.set(true)
                        }
                        synchronizedList.addLast(message)
                        Thread.sleep(1)
                        activeWorkerThreads.decrementAndGet()
                    }
                }
            }
        }

        // and: all threads are completed
        producers.forEach { it.join() }
        threadPool.close()

        // and: the pool does not have any pending worker thread
        assertTrue(threadPool.join(10, TimeUnit.SECONDS))

        // then: the total number of received messages equals N_OF_THREADS * N_OF_REP
        assertEquals(N_OF_PRODUCERS * N_OF_REPS, synchronizedList.size)

        // and: their union is also N_OF_THREADS * N_OF_REP
        assertEquals(N_OF_PRODUCERS * N_OF_REPS, HashSet<Message>(synchronizedList).size)

        // and: the maximum number of worker threads was not exceeded
        assertFalse(maxWorkerThreadsExceeded.get())
    }

    companion object {
        private const val N_OF_PRODUCERS = 5
        private const val MAX_WORKER_THREADS = 3
        private const val N_OF_REPS = 2_000
    }
}

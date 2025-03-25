package org.pedrofelix.concurrency.course.sync

import java.util.Collections
import java.util.LinkedHashSet
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SimpleMessageQueueTests {
    /**
     * The type of message inserted and retrieved from the queue
     */
    data class Message(
        val producerIx: Int,
        val messageIx: Int,
    )

    @Test
    fun `stress test`() {
        // given: a queue
        val queue = SimpleMessageQueue<Message>()

        // when: N_OF_THREADS producers write into the queue
        val producers = List(N_OF_THREADS) { ix ->
            Thread.ofPlatform().start {
                producer(ix, queue)
            }
        }

        // and: N_OF_THREADS consumers read from the queue
        val consumers = List(N_OF_THREADS) {
            Thread.ofPlatform().start {
                consumer(queue)
            }
        }

        // and: all threads are completed
        producers.forEach { it.join() }
        consumers.forEach { it.join() }

        // then: the total number of received messages equals N_OF_THREADS * N_OF_REP
        val totalReceivedMessages = synchronizedListOfLists.fold(0) { acc, list -> acc + list.size }
        assertEquals(N_OF_THREADS * N_OF_REPS, totalReceivedMessages)

        // and: their union is also N_OF_THREADS * N_OF_REP
        val linkedHashSet = LinkedHashSet<Message>()
        synchronizedListOfLists.forEach { linkedHashSet.addAll(it) }
        assertEquals(N_OF_THREADS * N_OF_REPS, linkedHashSet.size)

        // and: each consumer receives the producer messages in order
        synchronizedListOfLists.forEach { list ->
            val maxIndexPerProducer = mutableMapOf<Int, Int>()
            list.forEach { message ->
                val greatestIndexForProducer = maxIndexPerProducer[message.producerIx] ?: -1
                // I.e. a consumer could not receive a message with an index smaller or equal
                // to the index of a previous message by the same producer.
                assertTrue(message.messageIx > greatestIndexForProducer)
                maxIndexPerProducer[message.producerIx] = message.messageIx
            }
        }
    }

    private fun producer(
        producerIx: Int,
        queue: SimpleMessageQueue<Message>,
    ) {
        // Producers just write into the queue.
        repeat(N_OF_REPS) { ix ->
            queue.tryEnqueue(
                Message(producerIx, ix),
                1,
                TimeUnit.SECONDS,
            )
        }
    }

    private fun consumer(queue: SimpleMessageQueue<Message>) {
        // Consumers read from the queue and store the received items in a private list
        val messages = mutableListOf<Message>()
        while (true) {
            val res = queue.tryDequeue(1, TimeUnit.SECONDS)
                ?: break
            messages.addLast(res)
        }
        // At the end, they store its list in the shared and synchronized list
        synchronizedListOfLists.addLast(messages)
    }

    private val synchronizedListOfLists: MutableList<List<Message>> = Collections.synchronizedList(mutableListOf())

    companion object {
        private const val N_OF_THREADS = 3
        private const val N_OF_REPS = 100_000
    }
}

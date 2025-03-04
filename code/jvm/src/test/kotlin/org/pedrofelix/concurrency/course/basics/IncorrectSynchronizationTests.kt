package org.pedrofelix.concurrency.course.basics

import java.util.LinkedList
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

/**
 * Illustrates some less obvious errors when creating thread-safe data structures using locking.
 */
class IncorrectSynchronizationTests {
    /**
     * An incorrectly synchronized list implementation.
     * Can you identify the problem?
     */
    class AListWithIncorrectSynchronization<T> : Iterable<T> {
        private val list = LinkedList<T>()
        private val lock = ReentrantLock()

        fun addTail(t: T) =
            lock.withLock {
                list.addLast(t)
            }

        fun getHead(): T? =
            lock.withLock {
                if (list.isEmpty()) {
                    null
                } else {
                    list.removeFirst()
                }
            }

        override fun iterator(): Iterator<T> =
            lock.withLock {
                list.iterator()
            }
    }

    @Test
    fun `stress test`() {
        // given: a list incorrectly implemented
        val theList = AListWithIncorrectSynchronization<Int>()
        // and: a way to record exceptions
        val exceptionRecorder = ExceptionRecorder()

        // when: having N_OF_THREADS inserting, removing, and iterating on the list
        val threadBuilder = Thread.ofPlatform()
        val insertingThreads = List(N_OF_THREADS) {
            threadBuilder.start {
                repeat(N_OF_REPS) {
                    theList.addTail(it)
                }
            }
        }
        val removingThreads = List(N_OF_THREADS) {
            threadBuilder.start {
                repeat(N_OF_REPS) {
                    theList.getHead()
                }
            }
        }
        val iteratingThreads = List(N_OF_THREADS) {
            threadBuilder.start {
                repeat(N_OF_REPS) {
                    exceptionRecorder.run {
                        theList.sum()
                    }
                }
            }
        }

        // and: waiting for all threads to complete
        val threads = insertingThreads + removingThreads + iteratingThreads
        threads.forEach { it.join() }

        // then: the iterating threads have exceptions thrown
        val exceptions = exceptionRecorder.getExceptions()
        assertNotEquals(0, exceptions.size)
        // and: those exceptions include ConcurrentModificationException
        val concurrentModificationException = exceptions.firstOrNull { it is ConcurrentModificationException }
        assertNotNull(concurrentModificationException)

        // and: those exceptions include NullPointerException
        val nullPointerException = exceptions.firstOrNull { it is NullPointerException }
        assertNotNull(nullPointerException)

        // just for learning purposes
        println(concurrentModificationException)
        println(nullPointerException)
    }

    class ExceptionRecorder {
        private val exceptions = ConcurrentLinkedQueue<Throwable>()

        fun run(block: () -> Unit) {
            while (true) {
                try {
                    block()
                    return
                } catch (th: Throwable) {
                    exceptions.add(th)
                }
            }
        }

        fun getExceptions() = exceptions.toList()
    }

    companion object {
        private const val N_OF_THREADS = 10
        private const val N_OF_REPS = 10_000
    }
}

package org.pedrofelix.concurrency.course.basics

import org.junit.jupiter.api.Assertions.assertTrue
import java.util.Collections
import java.util.LinkedList
import java.util.NoSuchElementException
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
    interface SimpleListInterface<T> : Iterable<T> {
        fun addTail(t: T)

        fun removeHead(): T?
    }

    /**
     * An incorrectly synchronized list implementation.
     * Can you identify the problem?
     */
    class AListWithIncorrectSynchronization<T> : SimpleListInterface<T> {
        private val list = LinkedList<T>()
        private val lock = ReentrantLock()

        override fun addTail(t: T) =
            lock.withLock {
                list.addLast(t)
            }

        override fun removeHead(): T? =
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

    /**
     * A simple wrapper on Collections.synchronizedList.
     * Also incorrect.
     */
    class WrapperOverASynchronizedList<T> : SimpleListInterface<T> {
        private val list = Collections.synchronizedList(LinkedList<T>())

        override fun addTail(t: T) = list.addLast(t)

        override fun removeHead(): T? = if (list.isNotEmpty()) list.removeFirst() else null

        override fun iterator(): Iterator<T> = list.iterator()
    }

    @Test
    fun `stress test on AListWithIncorrectSynchronization`() {
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
                    theList.removeHead()
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

    @Test
    fun `stress test on WrapperOverASynchronizedList`() {
        // given: a list incorrectly implemented
        val theList = WrapperOverASynchronizedList<Int>()
        // and: a way to record exceptions
        val exceptionRecorder = ExceptionRecorder()

        // when: having threads inserting, removing, and iterating on the list
        val threadBuilder = Thread.ofPlatform()
        val nOfReps = 500 * N_OF_REPS
        val insertingThreads = List(N_OF_THREADS) {
            threadBuilder.start {
                repeat(nOfReps) {
                    theList.addTail(it)
                }
            }
        }
        val removingThreads = List(N_OF_THREADS) {
            threadBuilder.start {
                repeat(nOfReps) {
                    theList.removeHead()
                }
            }
        }
        val iteratingThreads = List(N_OF_THREADS) {
            threadBuilder.start {
                repeat(nOfReps) {
                    exceptionRecorder.run {
                        // There is an error in the following line
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

    @Test
    fun `stress test on WrapperOverASynchronizedList removeHead`() {
        // given: a list incorrectly implemented
        val theList = WrapperOverASynchronizedList<Int>()
        // and: a way to record exceptions
        val exceptionRecorder = ExceptionRecorder()

        // when: having threads inserting, removing, and iterating on the list
        val threadBuilder = Thread.ofPlatform()
        val nOfReps = 500 * N_OF_REPS
        val insertingThreads = List(1) {
            threadBuilder.start {
                repeat(nOfReps) {
                    theList.addTail(it)
                }
            }
        }
        val removingThreads = List(N_OF_THREADS) {
            threadBuilder.start {
                exceptionRecorder.run {
                    repeat(nOfReps) {
                        theList.removeHead()
                    }
                }
            }
        }

        // and: waiting for all threads to complete
        val threads = insertingThreads + removingThreads
        threads.forEach { it.join() }

        // then: the iterating threads have exceptions thrown
        val exceptions = exceptionRecorder.getExceptions()
        assertNotEquals(0, exceptions.size)

        // and: those exceptions include IndexOutOfBoundsException or NoSuchElementException
        val indexOutOfBoundsException = exceptions.firstOrNull { it is IndexOutOfBoundsException }
        val noSuchElementException = exceptions.firstOrNull { it is NoSuchElementException }
        assertTrue(indexOutOfBoundsException != null || noSuchElementException != null)

        // just for learning purposes
        println(indexOutOfBoundsException)
        println(noSuchElementException)
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
        private const val N_OF_THREADS = 5
        private const val N_OF_REPS = 50_000
    }
}

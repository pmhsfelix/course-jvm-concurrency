package org.pedrofelix.concurrency.course.sync

import org.junit.jupiter.api.assertThrows
import org.pedrofelix.concurrency.course.utils.TestHelper
import org.pedrofelix.concurrency.course.utils.spinUntilTimedWait
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.INFINITE
import kotlin.time.Duration.Companion.seconds

class SemaphoreTests {
    companion object {
        private const val INITIAL_UNITS = 3
        private const val N_OF_THREADS = 10
    }

    @Test
    fun `stress test SimpleUnarySemaphore`() {
        stressTestUnarySemaphore(SimpleUnarySemaphore(INITIAL_UNITS))
    }

    @Test
    fun `stress test UnarySemaphoreWithFairness`() {
        stressTestUnarySemaphore(UnarySemaphoreWithFairness(INITIAL_UNITS))
    }

    @Test
    fun `stress test UnarySemaphoreUsingKernelStyle`() {
        stressTestUnarySemaphore(UnarySemaphoreUsingKernelStyle(INITIAL_UNITS))
    }

    private fun stressTestUnarySemaphore(semaphore: UnarySemaphore) {
        val units = AtomicInteger(INITIAL_UNITS)
        val testHelper = TestHelper(5.seconds)
        testHelper.createAndStartMultiple(N_OF_THREADS) { _, isDone ->
            while (!isDone()) {
                assertTrue(semaphore.tryAcquire(1_000_000, TimeUnit.SECONDS))
                try {
                    val observedUnits = units.decrementAndGet()
                    assertTrue(observedUnits >= 0)
                    Thread.yield()
                } finally {
                    units.incrementAndGet()
                    semaphore.release()
                }
            }
        }
        testHelper.join()
    }

    @Test
    fun `stress test N-ary semaphore`() {
        val sem = NArySemaphoreWithFairnessUsingKernelStyle(INITIAL_UNITS.toLong())
        val units = AtomicInteger(INITIAL_UNITS)
        val testHelper = TestHelper(5.seconds)
        testHelper.createAndStartMultiple(N_OF_THREADS) { _, isDone ->
            while (!isDone()) {
                val requestedUnits = Random.nextLong(1, INITIAL_UNITS.toLong())
                assertTrue(sem.tryAcquire(requestedUnits, INFINITE))
                val observedUnits = units.addAndGet(-requestedUnits.toInt())
                assertTrue(observedUnits >= 0)
                Thread.yield()
                units.addAndGet(requestedUnits.toInt())
                sem.release(requestedUnits)
            }
        }
        testHelper.join()
    }

    @Test
    fun `interrupt test`() {
        val sem = NArySemaphoreWithFairnessUsingKernelStyle(3)
        val testHelper = TestHelper(10.seconds)
        val th1 = testHelper.thread {
            assertThrows<InterruptedException> { sem.tryAcquire(4, INFINITE) }
        }
        spinUntilTimedWait(th1)
        val th2 = testHelper.thread {
            assertTrue(sem.tryAcquire(3, INFINITE))
        }
        spinUntilTimedWait(th2)
        th1.interrupt()
        testHelper.join()
    }
}

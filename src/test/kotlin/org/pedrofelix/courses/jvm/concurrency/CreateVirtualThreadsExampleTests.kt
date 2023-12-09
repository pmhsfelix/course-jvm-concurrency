package org.pedrofelix.courses.jvm.concurrency

import org.junit.jupiter.api.Assertions.assertTrue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.test.Test

class CreateVirtualThreadsExampleTests {

    /**
     * Using [Thread.startVirtualThread] to create and start virtual threads.
     */
    @Test
    fun can_create_virtual_thread_using_startVirtualThread() {
        val latch = CountDownLatch(1)
        var isVirtual: Boolean? = null
        val th = Thread.startVirtualThread {
            isVirtual = Thread.currentThread().isVirtual
            latch.countDown()
        }
        latch.await()
        assertTrue(th.isVirtual)
        assertTrue(isVirtual == true)
    }

    /**
     * Using [Thread.ofVirtual] and [Thread.Builder.OfVirtual] to create and start virtual threads.
     */
    @Test
    fun can_create_virtual_threads_using_builders() {
        val future = CompletableFuture<Boolean>()
        // a `Thread.Builder.OfVirtual` is created by calling the static method `ofVirtual`
        val builder = Thread.ofVirtual()
        val th = builder.start {
            future.complete(Thread.currentThread().isVirtual)
        }
        assertTrue(th.isVirtual)
        assertTrue(future.get())
    }

    /**
     * Using an executor to create virtual threads.
     */
    @Test
    fun can_create_virtual_threads_using_executors() {
        val executor = Executors.newVirtualThreadPerTaskExecutor()
        val future = executor.submit<Boolean> {
            Thread.currentThread().isVirtual
        }
        assertTrue(future.get())
    }

    /**
     * Can create a significant amount of virtual threads.
     */
    @Test
    fun can_lots_of_virtual_threads() {
        val nOfThreads = 100_000
        val latch = CountDownLatch(nOfThreads)
        repeat(nOfThreads) {
            Thread.startVirtualThread {
                Thread.sleep(1000)
                latch.countDown()
            }
        }
        latch.await()
    }
}

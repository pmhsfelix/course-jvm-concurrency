package org.pedrofelix.concurrency.course.sync

import java.util.concurrent.TimeUnit
import kotlin.jvm.Throws

/**
 * Common interface for all unary semaphore implementations
 */
interface UnarySemaphore {
    fun release()

    @Throws(InterruptedException::class)
    fun tryAcquire(
        timeout: Long,
        timeoutUnit: TimeUnit,
    ): Boolean
}

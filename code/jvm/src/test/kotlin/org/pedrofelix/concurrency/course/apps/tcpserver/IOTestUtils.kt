package org.pedrofelix.concurrency.course.apps.tcpserver

import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import java.time.Duration
import java.time.Instant

fun waitUntilListening(
    address: InetSocketAddress,
    timeout: Duration = Duration.ofSeconds(5),
): Boolean {
    val startInstant = Instant.now()
    val timeoutInstant = startInstant + timeout
    while (Instant.now() < timeoutInstant) {
        Socket().use { socket ->
            try {
                socket.connect(address)
                return true
            } catch (e: ConnectException) {
                Thread.sleep(500)
            }
        }
    }
    return false
}

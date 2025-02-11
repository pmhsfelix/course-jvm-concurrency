package org.pedrofelix.concurrency.course.apps.tcpserver

import org.pedrofelix.concurrency.course.utils.writeLine
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.LinkedBlockingQueue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for EchoServer0SingleThreaded
 */
class EchoServer0Tests {
    /**
     * Represents a received message and the client that received it.
     */
    data class ReceivedMessage(
        val clientId: Int,
        val message: String,
    )

    @Test
    fun `clients are handled sequentially`() {
        // given: a listening server
        val serverAddress = InetSocketAddress("localhost", EchoServer0SingleThreaded.PORT)
        val serverMainThread =
            Thread.ofVirtual().start {
                EchoServer0SingleThreaded.main()
            }
        val serverIsListening = waitUntilListening(serverAddress)
        assertTrue(serverIsListening)

        // and: a list to store all received messages
        val concurrentLinkedQueue = LinkedBlockingQueue<ReceivedMessage>()

        // when: running N_OF_CLIENTS concurrently
        val threads =
            List(N_OF_CLIENTS) { index ->
                Thread.ofPlatform().start {
                    clientBehaviour(serverAddress, index, concurrentLinkedQueue)
                }
            }

        // and: waiting for those clients to terminate
        threads.forEach {
            it.join()
        }

        // then: the clients are handled sequentially
        val list = concurrentLinkedQueue.toList()
        // - the number of received messages is equal to the number of clients times the number of messages per client
        assertEquals(N_OF_CLIENTS * N_OF_MESSAGES_PER_CLIENT, list.size)

        // - the messages of a given client are contiguous
        repeat(N_OF_CLIENTS) { ix ->
            val startIx = ix * N_OF_MESSAGES_PER_CLIENT
            val sublist = list.slice(startIx..<(startIx + N_OF_CLIENTS))
            val clientIdSet = sublist.map { it.clientId }.toSet()
            assertEquals(1, clientIdSet.size)
        }
        // QUESTION this test assume a behavior that is highly likely but NOT guaranteed
        // Do you know what is that behavior?

        // cleanup: stop server and wait for it to end
        serverMainThread.interrupt()
        serverMainThread.join()
    }

    private fun clientBehaviour(
        serverAddress: InetSocketAddress,
        clientId: Int,
        concurrentLinkedQueue: LinkedBlockingQueue<ReceivedMessage>,
    ) {
        Socket().use { socket ->
            socket.connect(serverAddress)
            socket.getInputStream().bufferedReader().use { reader ->
                socket.getOutputStream().bufferedWriter().use { writer ->
                    // consume initial message
                    reader.readLine()
                    repeat(N_OF_MESSAGES_PER_CLIENT) { messageIx ->
                        val msgToSend = "message - $messageIx"
                        writer.writeLine(msgToSend)
                        val receivedMessage = reader.readLine()
                        concurrentLinkedQueue.add(
                            ReceivedMessage(
                                clientId,
                                receivedMessage,
                            ),
                        )
                        // wait a bit before sending the next message
                        Thread.sleep(1)
                    }
                }
            }
        }
    }

    companion object {
        const val N_OF_CLIENTS = 5
        const val N_OF_MESSAGES_PER_CLIENT = 7
    }
}

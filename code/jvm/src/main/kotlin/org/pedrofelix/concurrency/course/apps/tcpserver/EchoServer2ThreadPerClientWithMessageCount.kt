package org.pedrofelix.concurrency.course.apps.tcpserver

import org.pedrofelix.concurrency.course.utils.writeLine
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Locale

class EchoServer2ThreadPerClientWithMessageCount {
    companion object {
        private val logger: Logger = LoggerFactory.getLogger(EchoServer2ThreadPerClientWithMessageCount::class.java)
        private const val EXIT_LINE = "exit"
        private const val PORT = 8080

        @JvmStatic
        fun main(args: Array<String> = arrayOf()) {
            EchoServer2ThreadPerClientWithMessageCount().run("0.0.0.0", PORT)
        }
    }

    fun run(
        address: String,
        port: Int,
    ) {
        ServerSocket().use { serverSocket ->
            // bind (i.e. associate) the server socket to a local network interface and a port
            serverSocket.bind(InetSocketAddress(address, port))
            logger.info("server socket bound to {}:{}", address, port)
            acceptLoop(serverSocket)
        }
    }

    // NOTE: mutable field shared by all threads using the same instance
    private var messageCounter = 0

    /**
     * Keeps accepting client connections.
     */
    private fun acceptLoop(serverSocket: ServerSocket) {
        val threadBuilder: Thread.Builder = Thread.ofPlatform()
        var clientId = 0
        while (true) {
            logger.info("server socket is waiting for an incoming connection")
            val socket = serverSocket.accept()
            logger.info("incoming connection accepted, remote address is {}", socket.inetAddress.hostAddress)

            // QUESTION: could this increment and assignment be inside the lambda expression
            // passed to Thread.ofPlatform().start?
            clientId += 1
            val id = clientId
            // NOTE this is the main change when compared with EchoServer0SingleThreaded
            threadBuilder.start {
                echoLoop(socket, id)
            }
        }
    }

    /**
     * Keeps handling incoming lines and echoing them.
     */
    private fun echoLoop(
        socket: Socket,
        clientId: Int,
    ) {
        var lineNo = 0
        try {
            socket.use {
                socket.getInputStream().bufferedReader().use { reader ->
                    socket.getOutputStream().bufferedWriter().use { writer ->
                        writer.writeLine("Hi! You are client number %s", clientId.toString())
                        while (true) {
                            val line = reader.readLine()
                            if (line == null) {
                                logger.info("client socket is closed, ending echo loop")
                                return
                            }
                            if (line == EXIT_LINE) {
                                logger.info("client wants to exit, ending echo loop")
                                writer.writeLine("Bye.")
                                return
                            }
                            logger.info(
                                "Received line '{}', echoing it back",
                                line,
                            )
                            // NOTE: these two assignments are syntactically very similar
                            // but their consequences are very different!
                            lineNo += 1
                            messageCounter += 1
                            writer.writeLine(
                                "%d, %d: %s",
                                lineNo,
                                messageCounter,
                                line.uppercase(Locale.getDefault()),
                            )
                        }
                    }
                }
            }
        } catch (e: IOException) {
            logger.info("Connection ended with IO error: {}", e.message)
        }
    }
}

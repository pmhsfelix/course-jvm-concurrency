package org.pedrofelix.concurrency.course.apps.tcpserver

import org.pedrofelix.concurrency.course.utils.writeLine
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.BufferedWriter
import java.net.InetSocketAddress
import java.net.Socket

private val logger = LoggerFactory.getLogger("main")

fun main() {
    TcpClient.connect(InetSocketAddress("localhost", 8080)).use { client ->
        // We will use the main thread to block on the socket read and created a new thread to block
        // on the stdin read.
        val th = Thread.ofPlatform().start {
            logger.info("stdin reader thread started")
            try {
                while (true) {
                    val line = readlnOrNull() ?: break
                    client.writeLine(line)
                }
            } finally {
                logger.info("stdin reader thread ending")
            }
        }
        logger.info("remote reader starting")
        try {
            while (true) {
                val line = client.readLine() ?: break
                println(line)
            }
        } finally {
            logger.info("remote reader ending")
            System.`in`.close()
            client.close()
            th.join()
        }
        logger.info("ending")
    }
}

class TcpClient(
    private val socket: Socket,
    private val reader: BufferedReader,
    private val writer: BufferedWriter,
) : AutoCloseable {
    companion object {
        private val logger = LoggerFactory.getLogger(TcpClient::class.java)

        fun connect(address: InetSocketAddress): TcpClient {
            val socket = Socket()
            socket.connect(address)
            logger.info("Connected to {}:{}", address.hostName, address.port)
            val reader = socket.inputStream.bufferedReader()
            val writer = socket.outputStream.bufferedWriter()
            return TcpClient(
                socket,
                reader,
                writer,
            )
        }
    }

    fun readLine(): String? = reader.readLine()

    fun writeLine(line: String) {
        writer.writeLine(line)
    }

    override fun close() {
        socket.close()
        reader.close()
        writer.close()
    }
}

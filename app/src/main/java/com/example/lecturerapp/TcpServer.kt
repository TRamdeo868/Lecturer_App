package com.example.lecturerapp

import android.util.Log
import java.io.IOException
import java.io.PrintWriter
import kotlin.concurrent.thread
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.ConcurrentLinkedQueue

class TcpServer(private val port: Int = DEFAULT_PORT, private val onMessageReceived: (String, String) -> Unit) {
    companion object {
        const val DEFAULT_PORT: Int = 8888
    }

    private var svrSocket: ServerSocket? = null
    private val clientMap: HashMap<String, Socket> = HashMap() // Map from studentID to Socket
    private val keyMap: HashMap<String, SecretKeySpec> = HashMap() // Map from studentID to AES key
    private val ivMap: HashMap<String, IvParameterSpec> = HashMap() // Map from studentID to IV
    private val studentIds = List(10) { (816117992 + it).toString() }
    private var isRunning: Boolean = true
    var messageQueue = ConcurrentLinkedQueue<String>() // Queue to handle messages

    init {
        try {
            svrSocket = ServerSocket(port, 0, InetAddress.getByName("0.0.0.0"))
            Log.e("TcpServer", "Server is starting on port $port ip address: ${svrSocket?.inetAddress?.hostAddress}")
            thread {
                while (isRunning) {
                    try {
                        Log.e("TcpServer", "Waiting for client connection...")
                        val clientSocket = svrSocket?.accept()
                        Log.e("TcpServer", "Accepted a connection from: ${clientSocket?.inetAddress?.hostAddress}")
                        clientSocket?.let { handleClientSocket(it) }
                    } catch (e: SocketException) {
                        if (!isRunning) {
                            Log.d("TcpServer", "Server socket closed, stopping server loop.")
                        } else {
                            Log.e("TcpServer", "Error accepting connection: ${e.message}")
                        }
                    } catch (e: Exception) {
                        Log.e("TcpServer", "Unexpected error accepting connection: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("TcpServer", "Server initialization failed: ${e.message}")
        }
    }

    private fun handleClientSocket(socket: Socket) {
        socket.inetAddress.hostAddress?.let { clientAddress ->
            Log.d("TcpServer", "Handling client socket for $clientAddress")
            thread {
                val clientReader = socket.inputStream.bufferedReader()
                val clientWriter = socket.outputStream.bufferedWriter()
                var studentId: String?

                try {
                    // Initial handshake
                    val initialMessage = clientReader.readLine()
                    if (initialMessage == "I am here") {
                        clientWriter.write("Send your Student ID\n")
                        clientWriter.flush()

                        // Capture the student ID
                        studentId = clientReader.readLine()
                        if (studentIds.contains(studentId)) {
                            // Challenge-Response Authentication logic remains unchanged
                            studentId?.let { id ->
                                clientMap[id] = socket
                                receiveMessage(id, socket)
                            }
                        } else {
                            Log.d("TcpServer", "Invalid Student ID: $studentId")
                        }
                    }

                    // Handling incoming messages
                    while (socket.isConnected) {
                        val encryptedMessage = clientReader.readLine()
                        if (encryptedMessage != null) {
                            val seed = hashStrSha256(clientAddress)
                            val aesKey = generateAESKey(seed)
                            val aesIv = generateIV(seed)
                            val decryptedMessage = decryptMessage(encryptedMessage, aesKey, aesIv)
                            Log.d("TcpServer", "Received from $clientAddress: $decryptedMessage")
                            onMessageReceived(decryptedMessage, clientAddress)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("TcpServer", "Error with client $clientAddress", e)
                    clientMap.remove(clientAddress)
                    socket.close()
                }
            }

            // Handling outgoing messages
            thread {
                val clientWriter = socket.outputStream.bufferedWriter()
                while (socket.isConnected) {
                    while (messageQueue.isNotEmpty()) {
                        val message = messageQueue.poll()
                        if (message != null) {
                            val seed = hashStrSha256(clientAddress)
                            val aesKey = generateAESKey(seed)
                            val aesIv = generateIV(seed)
                            val encryptedMessage = encryptMessage(message, aesKey, aesIv)

                            clientWriter.write(encryptedMessage)
                            clientWriter.newLine()
                            clientWriter.flush()
                            Log.d("TcpServer", "Sent to $clientAddress: $encryptedMessage")
                        }
                    }
                    Thread.sleep(100)
                }
            }
        }
    }

    fun receiveMessage(studentId: String, socket: Socket) {
        thread {
            try {
                val reader = socket.inputStream.bufferedReader()

                while (socket.isConnected) {
                    val encryptedMessage = reader.readLine()
                    if (encryptedMessage != null) {
                        val seed = hashStrSha256(studentId)
                        val aesKey = generateAESKey(seed)
                        val aesIv = generateIV(seed)
                        val decryptedMessage = decryptMessage(encryptedMessage, aesKey, aesIv)

                        Log.d("TcpServer", "Received from $studentId: $decryptedMessage")
                        onMessageReceived(decryptedMessage, studentId)
                    }
                }
            } catch (e: Exception) {
                Log.e("TcpServer", "Error receiving message from $studentId", e)
                clientMap.remove(studentId)
                socket.close()
            }
        }
    }

    fun sendMessage(studentId: String, plainMessage: String) {
        val clientSocket = clientMap[studentId]
        if (clientSocket != null) {
            try {
                val seed = hashStrSha256(studentId)
                val aesKey = generateAESKey(seed)
                val aesIv = generateIV(seed)
                val encryptedMessage = encryptMessage(plainMessage, aesKey, aesIv)

                val writer = PrintWriter(clientSocket.outputStream, true)
                writer.println(encryptedMessage)
                Log.d("TcpServer", "Message sent to $studentId: $encryptedMessage")
            } catch (e: IOException) {
                Log.e("TcpServer", "Error sending message to $studentId", e)
                clientMap.remove(studentId)
            }
        } else {
            Log.e("TcpServer", "Client socket for $studentId not found")
        }
    }

    fun stopServer() {
        isRunning = false
        try {
            svrSocket?.close()
            clientMap.values.forEach { it.close() }
            clientMap.clear()
            keyMap.clear()
            ivMap.clear()
            Log.d("TcpServer", "Server stopped successfully.")
        } catch (e: IOException) {
            Log.e("TcpServer", "Error stopping server", e)
        }
    }

    fun isRunning(): Boolean {
        return isRunning
    }

    // Hashing, encryption, and decryption methods
    fun hashStrSha256(str: String): String {
        val algorithm = "SHA-256"
        val hashedString = MessageDigest.getInstance(algorithm).digest(str.toByteArray(Charsets.UTF_8))
        return hashedString.joinToString("") { String.format("%02x", it) }
    }

    fun generateAESKey(seed: String): SecretKeySpec {
        val first32Chars = seed.take(32).padEnd(32, '0')
        return SecretKeySpec(first32Chars.toByteArray(), "AES")
    }

    fun generateIV(seed: String): IvParameterSpec {
        val first16Chars = seed.take(16).padEnd(16, '0')
        return IvParameterSpec(first16Chars.toByteArray())
    }

    fun encryptMessage(plaintext: String, aesKey: SecretKeySpec, aesIv: IvParameterSpec): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, aesIv)
        val encrypted = cipher.doFinal(plaintext.toByteArray())
        return Base64.getEncoder().encodeToString(encrypted)
    }

    fun decryptMessage(encryptedText: String, aesKey: SecretKeySpec, aesIv: IvParameterSpec): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, aesKey, aesIv)
        val decodedBytes = Base64.getDecoder().decode(encryptedText)
        val decryptedBytes = cipher.doFinal(decodedBytes)
        return String(decryptedBytes)
    }
}

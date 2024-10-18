package com.example.lecturerapp

import android.util.Log
import java.io.IOException
import java.io.PrintWriter
import kotlin.Exception
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

class TcpServer(private val port: Int = DEFAULT_PORT, private val onMessageReceived: (String) -> Unit) {
    companion object {
        const val DEFAULT_PORT: Int = 8888
    }

    private val serverSocket: ServerSocket = ServerSocket(port, 0, InetAddress.getByName("0.0.0.0"))
    private val clientMap: HashMap<String, Socket> = HashMap()
    private val studentIds = List(10) { (816117992 + it).toString() }
    private var isRunning = true

    init {
        Log.e("TcpServer", "Server is starting on port $port ip address: ${serverSocket.inetAddress.hostAddress}")
        thread {
            while (isRunning) {
                try {
                    val clientSocket = serverSocket.accept()
                    Log.e("TcpServer", "Accepted a connection from: ${clientSocket.inetAddress.hostName}")
                    handleClientSocket(clientSocket)
                } catch (e: SocketException) {
                    if(isRunning){
                        Log.e("TcpServer", "Error accepting connection", e)
                    }else{
                        Log.d("TcpServer", "Server socket closed, stopping server loop.")
                    }
                }catch (e: Exception){
                    Log.e("TcpServer", "Unexpected error accepting connection", e)
                }
            }
        }
    }

    fun isRunning(): Boolean {
        return !serverSocket.isClosed
    }

    private fun handleClientSocket(socket: Socket) {
        val clientAddress = socket.inetAddress.hostAddress
        clientAddress?.let {
            clientMap[it] = socket
            Log.e("TcpServer", "New connection from: $it")

            thread {
                try {
                    val reader = socket.inputStream.bufferedReader()
                    val writer = socket.outputStream.bufferedWriter()

                    while (socket.isConnected) {
                        val studentMessage = reader.readLine()
                        if (studentMessage != null && studentIds.contains(studentMessage)) {
                            val challenge = (1000..9999).random().toString()
                            writer.write("$challenge\n")
                            writer.flush()

                            val response = reader.readLine()
                            if (response != null) {
                                val seed = hashStrSha256(studentMessage)
                                val aesKey = generateAESKey(seed)
                                val aesIv = generateIV(seed)

                                val decryptedResponse = decryptMessage(response, aesKey, aesIv)
                                if (decryptedResponse == challenge) {
                                    Log.d("TcpServer", "Student Authenticated")
                                    onMessageReceived(studentMessage)

                                    // Allow chat after successful authentication
                                    receiveMessage(studentMessage, socket)
                                    while (socket.isConnected) {
                                        val chatMessage = reader.readLine()
                                        if (chatMessage != null) {
                                            val decryptedMessage = decryptMessage(chatMessage, aesKey, aesIv)
                                            Log.d("TcpServer", "Received from $it: $decryptedMessage")
                                            // Optionally respond to the message
                                        }
                                    }
                                } else {
                                    Log.d("TcpServer", "Student Authentication Failed")
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("TcpServer", "Error with client $it", e)
                    clientMap.remove(it)
                    socket.close()
                }
            }
        }
    }


    fun close() {
        isRunning = false // Stop the server loop
        try {
            serverSocket.close()
            clientMap.values.forEach { it.close() } // Close all client sockets
            clientMap.clear()
            Log.d("TcpServer", "Server closed successfully.")
        } catch (e: IOException) {
            Log.e("TcpServer", "Error closing server", e)
        }
    }

    // Encryption and Decryption methods provided
    fun hashStrSha256(str: String): String {
        val algorithm = "SHA-256"
        val hashedString = MessageDigest.getInstance(algorithm).digest(str.toByteArray(Charsets.UTF_8))
        return hashedString.joinToString("") { String.format("%02x", it) }
    }

    fun generateAESKey(seed: String): SecretKeySpec {
        val first32Chars = seed.take(32).padEnd(32, '0')  // Ensure length is 32
        return SecretKeySpec(first32Chars.toByteArray(), "AES")
    }

    fun generateIV(seed: String): IvParameterSpec {
        val first16Chars = seed.take(16).padEnd(16, '0')  // Ensure length is 16
        return IvParameterSpec(first16Chars.toByteArray())
    }

    fun encryptMessage(plaintext: String, aesKey: SecretKeySpec, aesIv: IvParameterSpec): String {
        val plainTextByteArr = plaintext.toByteArray()

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, aesIv)

        val encrypt = cipher.doFinal(plainTextByteArr)
        return Base64.getEncoder().encodeToString(encrypt)
    }

    fun decryptMessage(encryptedText: String, aesKey: SecretKeySpec, aesIv: IvParameterSpec): String {
        val textToDecrypt = Base64.getDecoder().decode(encryptedText)

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, aesKey, aesIv)

        val decrypt = cipher.doFinal(textToDecrypt)
        return String(decrypt)
    }

    // Add this method inside the TcpServer class
    fun receiveMessage(studentMessage: String, socket: Socket) {
        val clientAddress = socket.inetAddress.hostAddress

        if (clientMap.containsKey(clientAddress)) {
            thread {
                try {
                    val reader = socket.inputStream.bufferedReader()

                    while (socket.isConnected) {
                        val chatMessage = reader.readLine()
                        if (chatMessage != null) {
                            // Decrypt the received message
                            val seed = hashStrSha256(studentMessage)
                            val aesKey = generateAESKey(seed)
                            val aesIv = generateIV(seed)
                            val decryptedMessage = decryptMessage(chatMessage, aesKey, aesIv)

                            Log.d("TcpServer", "Received from $clientAddress: $decryptedMessage")
                            // Invoke the onMessageReceived callback to handle the message
                            onMessageReceived(decryptedMessage)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("TcpServer", "Error receiving message from $clientAddress", e)
                    clientMap.remove(clientAddress)
                    socket.close()
                }
            }
        } else {
            Log.e("TcpServer", "Client not connected: $clientAddress")
        }
    }


    fun sendMessage(currentChatStudentId: String, encryptedMessage: String) {
        val clientSocket = clientMap[currentChatStudentId]
        if (clientSocket != null) {
            try {
                val outputStream = clientSocket.getOutputStream()
                val writer = PrintWriter(outputStream, true)
                writer.println(encryptedMessage)
                Log.d("TcpServer", "Message sent to $currentChatStudentId: $encryptedMessage")
            } catch (e: IOException) {
                Log.e("TcpServer", "Error sending message to $currentChatStudentId", e)
                // Handle the error, possibly by removing the client from the map
                clientMap.remove(currentChatStudentId)
            }
        } else {
            Log.e("TcpServer", "Client socket for $currentChatStudentId not found")
        }
    }
}

package com.example.lecturerapp

import android.util.Log
import java.io.IOException
import java.io.PrintWriter
import kotlin.Exception
import kotlin.concurrent.thread
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.MessageDigest
import java.util.Base64

class TcpServer(private val port: Int = DEFAULT_PORT, private val onMessageReceived: (String) -> Unit) {
    companion object {
        const val DEFAULT_PORT: Int = 8888
    }

    private val serverSocket: ServerSocket = ServerSocket(port, 0, InetAddress.getByName("192.168.100.196"))
    private val clientMap: HashMap<String, Socket> = HashMap()

    init {
        Log.e("TcpServer", "Server is starting on port $port ip address: ${serverSocket.inetAddress.hostAddress}")
        thread {
            while (true) {
                try {
                    val clientSocket = serverSocket.accept()
                    Log.e("TcpServer", "Accepted a connection from: ${clientSocket.inetAddress.hostAddress}")
                    handleClientSocket(clientSocket)
                } catch (e: Exception) {
                    Log.e("TcpServer", "Error accepting connection", e)
                }
            }
        }
    }

    fun isRunning(): Boolean {
        return !serverSocket.isClosed
    }

    private fun handleClientSocket(socket: Socket) {
        socket.inetAddress.hostAddress?.let{
            clientMap[it] = socket
            Log.e("TcpServer", "New connection from: $it")

            thread {
                socket.use {
                    val reader = socket.inputStream.bufferedReader()
                    val writer = socket.outputStream.bufferedWriter()

                    while (socket.isConnected) {
                        try {
                            val studentMessage = reader.readLine()
                            if (studentMessage != null) {
                                Log.e(
                                    "TcpServer",
                                    "Received message from $it: $studentMessage"
                                )

                                // Send challenge to student (plain text)
                                val challenge = (1000..9999).random().toString()
                                val seed = hashStrSha256(studentMessage)  // Assume student ID is passed as the message for hashing
                                val aesKey = generateAESKey(seed)
                                val aesIv = generateIV(seed)

                                // Encrypt the challenge
                                val encryptedChallenge = encryptMessage(challenge, aesKey, aesIv)
                                writer.write("$encryptedChallenge\n")
                                writer.flush()

                                // Wait for student's encrypted response
                                val response = reader.readLine()
                                if (response != null) {
                                    // Decrypt the student's response
                                    val decryptedResponse = decryptMessage(response, aesKey, aesIv)
                                    validateResponse(decryptedResponse, challenge)

                                    // Notify that the message was received
                                    onMessageReceived("Authentication message received from $it")
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("TcpServer", "Error with client $it", e)
                            break
                        }
                    }
                }
            }
        }
    }

    private fun validateResponse(response: String, challenge: String) {
        // Implement your validation logic here
        Log.e("TcpServer", "Validating response: $response against challenge: $challenge")
        // Check if the response matches the expected format (e.g., e(R, Hash(StudentID)))
    }

    fun close() {
        serverSocket.close()
        clientMap.values.forEach { it.close() } // Close all client sockets
        clientMap.clear()
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

    fun receiveMessage(){

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

package com.example.lecturerapp

import android.util.Log
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

    private val serverSocket: ServerSocket = ServerSocket(port, 0, InetAddress.getByName("192.168.49.1"))
    private val clientMap: HashMap<String, Socket> = HashMap()

    init {
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

    private fun handleClientSocket(socket: Socket) {
        val clientIp = socket.inetAddress.hostAddress
        clientMap[clientIp] = socket
        Log.e("TcpServer", "New connection from: $clientIp")

        thread {
            socket.use {
                val reader = it.inputStream.bufferedReader()
                val writer = it.outputStream.bufferedWriter()

                while (it.isConnected) {
                    try {
                        val studentMessage = reader.readLine()
                        if (studentMessage != null) {
                            Log.e("TcpServer", "Received message from $clientIp: $studentMessage")

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
                                onMessageReceived("Authentication message received from $clientIp")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("TcpServer", "Error with client $clientIp", e)
                        break
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

    fun sendMessage(currentChatStudentId: String, encryptedMessage: String) {

    }
}

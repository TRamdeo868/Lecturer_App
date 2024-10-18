package com.example.lecturerapp

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.properties.Delegates
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.MessageDigest
import java.util.Base64

class ClassManagementActivity : AppCompatActivity() {

    private lateinit var tcpServer: TcpServer
    private lateinit var wifiP2pManager: WifiP2pManager
    private lateinit var channel: WifiP2pManager.Channel
    private lateinit var peers: WifiP2pDeviceList
    private lateinit var peerReceiver: BroadcastReceiver
    private lateinit var classInfoTextView: TextView
    private lateinit var networkInfoTextView: TextView
    private lateinit var endSessionButton: Button
    private lateinit var attendeesRecyclerView: RecyclerView
    private val attendeesList = mutableListOf<String>()
    private lateinit var attendeesAdapter: AttendeesAdapter
    private lateinit var courseSessionNumber: String
    private lateinit var sessionType: String
    private var startTime by Delegates.notNull<Long>()
    private lateinit var runningTimeTextView: TextView
    private lateinit var handler: Handler
    private lateinit var runnable: Runnable

    // Chat interface variables
    private lateinit var chatInterface: LinearLayout
    private lateinit var chatWithStudentTextView: TextView
    private lateinit var messageInput: EditText
    private lateinit var sendMessageButton: Button
    private lateinit var chatMessagesTextView: TextView
    private lateinit var closeChatButton: Button
    private var currentChatStudentId: String? = null

    private lateinit var aesKey: SecretKeySpec
    private lateinit var aesIv: IvParameterSpec

    private val REQUEST_CODE_PERMISSIONS = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_class_management)

        // Initialize views
        initializeViews()

        // Initialize WiFi-Direct Manager
        wifiP2pManager = getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        channel = wifiP2pManager.initialize(this, mainLooper, null)

        // Request permissions and start discovering peers
        if (!checkPermissions()) {
            requestPermissions()
        } else {
            discoverPeers()
        }

        // Get Intent data from StartClassActivity
        retrieveIntentData()

        // Initialize TcpServer and handle incoming messages
        tcpServer = TcpServer(8888) { message ->
            runOnUiThread {
                chatMessagesTextView.append("\nStudent: ${decryptMessage(message)}")
            }
        }

        // Check if TcpServer is running
        if (tcpServer.isRunning()) {
            Log.d("ClassManagementActivity", "Server is running")
        } else {
            Log.e("ClassManagementActivity", "Server failed to start")
        }

        // Setup RecyclerView for attendees
        attendeesAdapter = AttendeesAdapter(this, attendeesList) { studentId ->
            openChat(studentId) // Open chat when button is clicked
        }
        attendeesRecyclerView.layoutManager = LinearLayoutManager(this)
        attendeesRecyclerView.adapter = attendeesAdapter

        // End Session button listener
        endSessionButton.setOnClickListener { endSession() }

        // Update the UI with running time
        startUpdatingRunningTime()

        // Register BroadcastReceiver for Wi-Fi Direct events
        registerPeerReceiver()

        // Send message button listener
        sendMessageButton.setOnClickListener { sendMessage() }
    }

    private fun initializeViews() {
        classInfoTextView = findViewById(R.id.class_info)
        networkInfoTextView = findViewById(R.id.network_info)
        endSessionButton = findViewById(R.id.end_session_button)
        attendeesRecyclerView = findViewById(R.id.attendees_recycler_view)
        runningTimeTextView = findViewById(R.id.running_time_text_view)

        // Initialize chat interface views
        chatInterface = findViewById(R.id.chat_interface)
        chatWithStudentTextView = findViewById(R.id.chat_with_student)
        messageInput = findViewById(R.id.message_input)
        sendMessageButton = findViewById(R.id.send_message_button)
        chatMessagesTextView = findViewById(R.id.chat_messages)
        closeChatButton = findViewById(R.id.close_chat_button)

        // Close chat button listener
        closeChatButton.setOnClickListener { closeChatInterface() }
    }

    @SuppressLint("MissingPermission")
    private fun retrieveIntentData() {
        val courseName = intent.getStringExtra("course_name") ?: "N/A"
        val courseCode = intent.getStringExtra("course_code") ?: "N/A"
        courseSessionNumber = intent.getStringExtra("session_number") ?: "N/A"
        sessionType = intent.getStringExtra("session_type") ?: "N/A"
        startTime = System.currentTimeMillis()

        // Display Class and Network Info
        wifiP2pManager.requestGroupInfo(channel) { group ->
            val ssid = group.networkName
            val password = group.passphrase

            classInfoTextView.text = """
                Course Name: $courseName
                Course Code: $courseCode
                Session Number: $courseSessionNumber
                Session Type: $sessionType
                """.trimIndent()

            networkInfoTextView.text = """
                Network SSID: $ssid
                Network Password: $password
                """.trimIndent()
        }

        // Initialize AES key and IV
        val seed = "studentMessage" // Replace this with actual seed logic
        aesKey = generateAESKey(seed)
        aesIv = generateIV(seed)
    }

    private fun registerPeerReceiver() {
        peerReceiver = object : BroadcastReceiver() {
            @SuppressLint("MissingPermission")
            override fun onReceive(context: Context, intent: Intent) {
                if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION == intent.action) {
                    if (checkPermissions()) {
                        wifiP2pManager.requestPeers(channel) { deviceList ->
                            peers = deviceList
                            updateAttendeesList()
                        }
                    } else {
                        requestPermissions()
                    }
                }
            }
        }
        val intentFilter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        }
        registerReceiver(peerReceiver, intentFilter)
    }

    private fun updateAttendeesList() {
        attendeesList.clear()
        for (device in peers.deviceList) {
            attendeesList.add(device.deviceName)
        }
        attendeesAdapter.notifyDataSetChanged()
    }

    private fun startUpdatingRunningTime() {
        handler = Handler(Looper.getMainLooper())
        runnable = object : Runnable {
            override fun run() {
                val elapsedTime = System.currentTimeMillis() - startTime
                val seconds = (elapsedTime / 1000) % 60
                val minutes = (elapsedTime / (1000 * 60)) % 60
                val hours = (elapsedTime / (1000 * 60 * 60)) % 24

                runningTimeTextView.text = String.format("%02d:%02d:%02d", hours, minutes, seconds)

                handler.postDelayed(this, 1000)
            }
        }
        handler.post(runnable)
    }

    @SuppressLint("MissingPermission")
    private fun discoverPeers() {
        if (checkPermissions()) {
            wifiP2pManager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Toast.makeText(this@ClassManagementActivity, "Discovery started", Toast.LENGTH_SHORT).show()
                }

                override fun onFailure(reason: Int) {
                    Toast.makeText(this@ClassManagementActivity, "Discovery failed: $reason", Toast.LENGTH_SHORT).show()
                }
            })
        } else {
            requestPermissions()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(peerReceiver)
        handler.removeCallbacks(runnable)
        tcpServer.close() // Ensure the server is closed on destruction
    }

    private fun checkPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.NEARBY_WIFI_DEVICES),
            REQUEST_CODE_PERMISSIONS)
    }

    private fun endSession() {
        // Logic to end the session
        finish() // Close the activity
    }

    private fun openChat(studentId: String) {
        currentChatStudentId = studentId
        chatWithStudentTextView.text = "Chat with $studentId"
        chatInterface.visibility = View.VISIBLE
    }

    private fun closeChatInterface() {
        currentChatStudentId = null
        chatInterface.visibility = View.GONE
        chatMessagesTextView.text = "" // Clear chat messages
    }

    private val connectedStudents = mutableSetOf<String>()

    // When a new student connects
    fun onStudentConnected(studentId: String) {
        connectedStudents.add(studentId)
    }

    // When a student disconnects
    fun onStudentDisconnected(studentId: String) {
        connectedStudents.remove(studentId)
    }

    private fun sendMessage() {
        val message = messageInput.text.toString().trim()
        if (message.isNotEmpty() && currentChatStudentId != null && connectedStudents.contains(currentChatStudentId)) {
            val encryptedMessage = encryptMessage(message)
            tcpServer.sendMessage(currentChatStudentId!!, encryptedMessage)
            chatMessagesTextView.append("\nYou: $message")
            messageInput.text.clear()
        } else {
            Toast.makeText(this, "Please select a connected student and enter a message", Toast.LENGTH_SHORT).show()
        }
    }



    private fun encryptMessage(message: String): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, aesIv)
        val encrypted = cipher.doFinal(message.toByteArray())
        return Base64.getEncoder().encodeToString(encrypted)
    }

    private fun decryptMessage(encryptedMessage: String): String {
        return try {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, aesKey, aesIv)
            val decoded = Base64.getDecoder().decode(encryptedMessage)
            val decrypted = cipher.doFinal(decoded)
            String(decrypted)
        } catch (e: Exception) {
            Log.e("ClassManagementActivity", "Decryption error: ${e.message}")
            "Error decrypting message"
        }
    }


    private fun generateAESKey(seed: String): SecretKeySpec {
        val key = MessageDigest.getInstance("SHA-256").digest(seed.toByteArray())
        return SecretKeySpec(key, "AES")
    }

    private fun generateIV(seed: String): IvParameterSpec {
        val iv = MessageDigest.getInstance("SHA-256").digest(seed.toByteArray()).copyOf(16)
        return IvParameterSpec(iv)
    }
}

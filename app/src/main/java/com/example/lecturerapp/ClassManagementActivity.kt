package com.example.lecturerapp

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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

    private val studentIds = List(10) { (816034662 + it).toString() }

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

        // Setup RecyclerView
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
    }

    private fun retrieveIntentData() {
        val courseName = intent.getStringExtra("course_name") ?: "N/A"
        val courseCode = intent.getStringExtra("course_code") ?: "N/A"
        val wifiSsid = intent.getStringExtra("wifi_ssid") ?: "N/A"
        val wifiPassword = intent.getStringExtra("wifi_password") ?: "N/A"
        courseSessionNumber = intent.getStringExtra("session_number") ?: "N/A"
        sessionType = intent.getStringExtra("session_type") ?: "N/A"
        startTime = System.currentTimeMillis()

        // Display Class and Network Info
        classInfoTextView.text = """
            Course Name: $courseName
            Course Code: $courseCode
            Session Number: $courseSessionNumber
            Session Type: $sessionType
        """.trimIndent()

        networkInfoTextView.text = """
            Network SSID: $wifiSsid
            Network Password: $wifiPassword
        """.trimIndent()

        // Initialize AES key and IV (You may want to modify this part according to your logic)
        val seed = "your_seed_based_on_logic" // Replace this with actual seed logic
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
                    Toast.makeText(
                        this@ClassManagementActivity,
                        "Discovery started",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                override fun onFailure(reason: Int) {
                    Toast.makeText(
                        this@ClassManagementActivity,
                        "Discovery failed: $reason",
                        Toast.LENGTH_SHORT
                    ).show()
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
    }

    private fun checkPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.NEARBY_WIFI_DEVICES
                ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.NEARBY_WIFI_DEVICES
            ),
            REQUEST_CODE_PERMISSIONS
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            discoverPeers()
        } else {
            Toast.makeText(
                this,
                "Permissions are required for the app to function",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun openChat(studentId: String) {
        currentChatStudentId = studentId
        chatWithStudentTextView.text = "Chatting with: $studentId"
        chatInterface.visibility = View.VISIBLE
        loadChatMessages() // Load existing chat messages if necessary
    }

    fun closeChatInterface() {
        val chatInterface = findViewById<LinearLayout>(R.id.chat_interface)
        val closeChatButton = findViewById<Button>(R.id.close_chat_button)

        chatInterface.visibility = View.GONE
        closeChatButton.visibility = View.GONE // Hide the button when chat is closed
    }

    private fun sendMessage() {
        val message = messageInput.text.toString()
        if (currentChatStudentId != null) {
            val encryptedMessage = encryptMessage(message) // Encrypt the message
            // Send the encrypted message logic here
            // e.g., sendMessageToStudent(currentChatStudentId, encryptedMessage)

            // For display purposes, just show it in chat messages view
            chatMessagesTextView.append("\nMe: $message")
            messageInput.text.clear()
        } else {
            Toast.makeText(this, "No student selected for chat", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadChatMessages() {
        // Load existing messages logic here, if necessary
        // For display purposes, this could be a static list or fetched from a server
    }

    private fun encryptMessage(message: String): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, aesIv)
        val encrypted = cipher.doFinal(message.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(encrypted)
    }

    private fun endSession() {
        // End the session logic here
        Toast.makeText(this, "Session ended", Toast.LENGTH_SHORT).show()
        finish()
    }

    // AES Key Generation
    private fun generateAESKey(seed: String): SecretKeySpec {
        val keyBytes = MessageDigest.getInstance("SHA-256").digest(seed.toByteArray())
        return SecretKeySpec(keyBytes, "AES")
    }

    private fun generateIV(seed: String): IvParameterSpec {
        val ivBytes = MessageDigest.getInstance("SHA-256").digest(seed.toByteArray()).copyOf(16)
        return IvParameterSpec(ivBytes)
    }
}

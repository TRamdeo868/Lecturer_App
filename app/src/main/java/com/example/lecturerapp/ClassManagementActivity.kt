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
import java.io.IOException
import java.net.Socket
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.MessageDigest
import kotlin.properties.Delegates

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
    private val connectedStudents = mutableMapOf<String, Socket>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_class_management)

        initializeViews()
        initializeWifiDirect()

        if (!checkPermissions()) {
            requestPermissions()
        } else {
            discoverPeers()
        }

        retrieveIntentData()
        startTcpServer()

        setupAttendeesRecyclerView()
        setupEndSessionButton()
        startUpdatingRunningTime()

        registerPeerReceiver()

        sendMessageButton.setOnClickListener { sendMessage() }
    }

    private fun initializeViews() {
        classInfoTextView = findViewById(R.id.class_info)
        networkInfoTextView = findViewById(R.id.network_info)
        endSessionButton = findViewById(R.id.end_session_button)
        attendeesRecyclerView = findViewById(R.id.attendees_recycler_view)
        runningTimeTextView = findViewById(R.id.running_time_text_view)

        chatInterface = findViewById(R.id.chat_interface)
        chatWithStudentTextView = findViewById(R.id.chat_with_student)
        messageInput = findViewById(R.id.message_input)
        sendMessageButton = findViewById(R.id.send_message_button)
        chatMessagesTextView = findViewById(R.id.chat_messages)
        closeChatButton = findViewById(R.id.close_chat_button)

        closeChatButton.setOnClickListener { closeChatInterface() }
    }

    private fun initializeWifiDirect() {
        wifiP2pManager = getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        channel = wifiP2pManager.initialize(this, mainLooper, null)
    }

    @SuppressLint("MissingPermission")
    private fun retrieveIntentData() {
        val courseName = intent.getStringExtra("course_name") ?: "N/A"
        val courseCode = intent.getStringExtra("course_code") ?: "N/A"
        courseSessionNumber = intent.getStringExtra("session_number") ?: "N/A"
        sessionType = intent.getStringExtra("session_type") ?: "N/A"
        startTime = System.currentTimeMillis()

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

        val seed = "studentMessage"
        aesKey = generateAESKey(seed)
        aesIv = generateIV(seed)
    }

    private fun startTcpServer() {
        tcpServer = TcpServer(8888) { message, studentId ->
            runOnUiThread {
                chatMessagesTextView.append("\nStudent $studentId: $message")
            }
        }

        if (tcpServer.isRunning()) {
            Log.d("ClassManagementActivity", "Server is running")
        } else {
            Log.e("ClassManagementActivity", "Server failed to start")
        }
    }

    private fun setupAttendeesRecyclerView() {
        attendeesAdapter = AttendeesAdapter(this, attendeesList) { studentId ->
            openChat(studentId)
        }
        attendeesRecyclerView.layoutManager = LinearLayoutManager(this)
        attendeesRecyclerView.adapter = attendeesAdapter
    }

    private fun setupEndSessionButton() {
        endSessionButton.setOnClickListener { endSession() }
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
            val studentId = device.deviceName
            attendeesList.add(studentId)
        }
        attendeesAdapter.notifyDataSetChanged()
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

    private fun sendMessage() {
        val message = messageInput.text.toString()
        if (message.isNotEmpty() && currentChatStudentId != null) {
            val encryptedMessage = encryptMessage(message)
            val socket = connectedStudents[currentChatStudentId]

            socket?.let {
                Thread {
                    try {
                        it.getOutputStream().write(encryptedMessage)
                        runOnUiThread {
                            chatMessagesTextView.append("\nYou: $message")
                        }
                    } catch (e: IOException) {
                        runOnUiThread {
                            Toast.makeText(this, "Error sending message", Toast.LENGTH_SHORT).show()
                        }
                    }
                }.start()
            }
            messageInput.text.clear()
        } else {
            Toast.makeText(this, "Please enter a message", Toast.LENGTH_SHORT).show()
        }
    }

    private fun encryptMessage(message: String): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, aesIv)
        return cipher.doFinal(message.toByteArray())
    }

    private fun openChat(studentId: String) {
        currentChatStudentId = studentId
        chatWithStudentTextView.text = "Chat with $studentId"
        chatInterface.visibility = View.VISIBLE
    }

    private fun closeChatInterface() {
        chatInterface.visibility = View.GONE
        currentChatStudentId = null
    }

    private fun endSession() {
        tcpServer.stopServer()
        finish()
    }

    private fun checkPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_WIFI_STATE) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_WIFI_STATE),
            REQUEST_CODE_PERMISSIONS
        )
    }

    private fun generateAESKey(seed: String): SecretKeySpec {
        val digest = MessageDigest.getInstance("SHA-256")
        val key = digest.digest(seed.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(key, "AES")
    }

    private fun generateIV(seed: String): IvParameterSpec {
        val digest = MessageDigest.getInstance("SHA-256")
        val iv = digest.digest(seed.toByteArray(Charsets.UTF_8)).copyOfRange(0, 16)
        return IvParameterSpec(iv)
    }
}

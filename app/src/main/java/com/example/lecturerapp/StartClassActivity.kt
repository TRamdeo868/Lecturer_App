package com.example.lecturerapp

import android.Manifest
import android.annotation.SuppressLint
import android.net.wifi.WifiManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AlertDialog

class StartClassActivity : AppCompatActivity() {

    private lateinit var courseName: EditText
    private lateinit var courseCode: EditText
    private lateinit var sessionTypeSpinner: Spinner
    private lateinit var sessionNumber: EditText
    private lateinit var startClassButton: Button

    private lateinit var wifiP2pManager: WifiP2pManager
    private lateinit var channel: WifiP2pManager.Channel

    private var isGroupCreationInProgress = false

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_start_class)

        // Initialize views
        initializeViews()

        // Initialize Wi-Fi Direct
        initWifiP2P()

        // Check permissions and proceed
        if (checkAndRequestPermissions()) {
            setupStartClassButton()
        }
    }

    private fun initializeViews() {
        courseName = findViewById(R.id.course_name)
        courseCode = findViewById(R.id.course_code)
        sessionTypeSpinner = findViewById(R.id.session_type_spinner)
        sessionNumber = findViewById(R.id.session_number)
        startClassButton = findViewById(R.id.start_class_button)
    }

    private fun initWifiP2P() {
        wifiP2pManager = getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        channel = wifiP2pManager.initialize(this, mainLooper, null)
    }

    private fun setupStartClassButton() {
        startClassButton.setOnClickListener {
            startClassSession()
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun checkAndRequestPermissions(): Boolean {
        val requiredPermissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.NEARBY_WIFI_DEVICES
            } else {
                Manifest.permission.ACCESS_WIFI_STATE
            }
        )

        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        return if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), 1)
            false
        } else {
            true
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            setupStartClassButton()
            Toast.makeText(this, "Permissions granted.", Toast.LENGTH_SHORT).show()
        } else {
            showPermissionsAlert()
        }
    }

    private fun showPermissionsAlert() {
        AlertDialog.Builder(this)
            .setTitle("Permissions Required")
            .setMessage("This app needs location and Wi-Fi Direct permissions to function. Please enable them in settings.")
            .setPositiveButton("Go to Settings") { _, _ ->
                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = android.net.Uri.fromParts("package", packageName, null)
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startClassSession() {
        val courseNameText = courseName.text.toString().trim()
        val courseCodeText = courseCode.text.toString().trim()
        val sessionNumberText = sessionNumber.text.toString().trim()

        if (courseNameText.isNotEmpty() && courseCodeText.isNotEmpty() && sessionNumberText.isNotEmpty()) {
            if (!isWifiEnabled()) {
                Toast.makeText(this, "Please enable Wi-Fi to create a group.", Toast.LENGTH_SHORT).show()
                return
            }

            if (isGroupCreationInProgress) {
                Toast.makeText(this, "Group creation in progress, please wait.", Toast.LENGTH_SHORT).show()
            } else {
                createGroup(courseNameText, courseCodeText, sessionNumberText)
            }
        } else {
            Toast.makeText(this, "Please fill out all fields", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createGroup(courseNameText: String, courseCodeText: String, sessionNumberText: String) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(this, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.NEARBY_WIFI_DEVICES else Manifest.permission.ACCESS_WIFI_STATE) != PackageManager.PERMISSION_GRANTED) {
            Log.e("StartClassActivity", "Required permissions not granted.")
            return
        }

        isGroupCreationInProgress = true

        val config = WifiP2pConfig.Builder()
            .setNetworkName("DIRECT-01")
            .setPassphrase("password")
            .build()

        wifiP2pManager.createGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d("StartClassActivity", "Group created successfully")
                navigateToClassManagement(courseNameText, courseCodeText, sessionNumberText)
                isGroupCreationInProgress = false
            }

            override fun onFailure(reason: Int) {
                handleGroupCreationFailure(reason)
                isGroupCreationInProgress = false
            }
        })
    }

    private fun handleGroupCreationFailure(reason: Int) {
        val errorMessage = when (reason) {
            WifiP2pManager.P2P_UNSUPPORTED -> "Wi-Fi Direct is not supported on this device."
            WifiP2pManager.ERROR -> "An unknown error occurred."
            WifiP2pManager.BUSY -> {
                Toast.makeText(this, "Wi-Fi Direct service is busy. Retrying...", Toast.LENGTH_SHORT).show()
                createGroup(courseName.text.toString(), courseCode.text.toString(), sessionNumber.text.toString()) // Retry
                return
            }
            else -> "Failed to create group: $reason"
        }
        Log.e("StartClassActivity", errorMessage)
        Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
    }

    private fun isWifiEnabled(): Boolean {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        return wifiManager.isWifiEnabled
    }

    private fun navigateToClassManagement(courseName: String, courseCode: String, sessionNumber: String) {
        val intent = Intent(this, ClassManagementActivity::class.java).apply {
            putExtra("course_name", courseName)
            putExtra("course_code", courseCode)
            putExtra("session_number", sessionNumber)
            putExtra("session_type", sessionTypeSpinner.selectedItem?.toString() ?: "")
        }
        startActivity(intent)
    }
}

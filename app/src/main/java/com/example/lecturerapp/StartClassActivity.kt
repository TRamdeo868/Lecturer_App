package com.example.lecturerapp

import android.Manifest
import android.net.wifi.WifiManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class StartClassActivity : AppCompatActivity() {

    private lateinit var courseName: EditText
    private lateinit var courseCode: EditText
    private lateinit var sessionTypeSpinner: Spinner
    private lateinit var sessionNumber: EditText
    private lateinit var wifiSSID: EditText
    private lateinit var wifiPassword: EditText
    private lateinit var startClassButton: Button

    private lateinit var wifiP2pManager: WifiP2pManager
    private lateinit var channel: WifiP2pManager.Channel
    private var isGroupCreated = false
    private var isGroupCreationInProgress = false // Added flag to track ongoing group creation

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_start_class)

        // Initialize views
        courseName = findViewById(R.id.course_name)
        courseCode = findViewById(R.id.course_code)
        sessionTypeSpinner = findViewById(R.id.session_type_spinner)
        sessionNumber = findViewById(R.id.session_number)
        wifiSSID = findViewById(R.id.wifi_ssid)
        wifiPassword = findViewById(R.id.wifi_password)
        startClassButton = findViewById(R.id.start_class_button)

        // Initialize Wi-Fi Direct
        wifiP2pManager = getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        channel = wifiP2pManager.initialize(this, mainLooper, null)

        // Check for permissions
        checkPermissions()

        // Set up button click listener for starting class
        startClassButton.setOnClickListener {
            startClassSession()
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun checkPermissions() {
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.NEARBY_WIFI_DEVICES
        )
        val permissionsNeeded = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsNeeded, 1)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                // All requested permissions were granted
                Toast.makeText(this, "Permissions granted.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "All permissions are required for Wi-Fi Direct functionality.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startClassSession() {
        // Get input data from views
        val courseNameText = courseName.text.toString().trim()
        val courseCodeText = courseCode.text.toString().trim()
        val sessionType = sessionTypeSpinner.selectedItem?.toString() ?: ""
        val sessionNumberText = sessionNumber.text.toString().trim()
        val ssid = wifiSSID.text.toString().trim()
        val password = wifiPassword.text.toString().trim()

        // Check if all fields are filled
        if (courseNameText.isNotEmpty() && courseCodeText.isNotEmpty() && sessionNumberText.isNotEmpty()
            && ssid.isNotEmpty() && password.isNotEmpty()) {

            // Create Wi-Fi Direct group
            createGroup()
        } else {
            Toast.makeText(this, "Please fill out all fields", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createGroup() {
        Log.d("StartClassActivity", "Creating group...")

        if (isGroupCreationInProgress) {
            Toast.makeText(this, "Group creation already in progress. Please wait.", Toast.LENGTH_SHORT).show()
            return
        }

        // Check if Wi-Fi is enabled
        if (!isWifiEnabled()) {
            Toast.makeText(this, "Please enable Wi-Fi to create a group.", Toast.LENGTH_SHORT).show()
            return
        }

        // Check permissions again
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) {
            Log.e("StartClassActivity", "Permissions not granted.")
            return
        }

        isGroupCreationInProgress = true // Set flag to indicate group creation is in progress

        val config = WifiP2pConfig.Builder()
            .setNetworkName("DIRECT-01")
            .setPassphrase("password")
            .build()

        wifiP2pManager.createGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d("StartClassActivity", "Group created successfully!")
                Toast.makeText(this@StartClassActivity, "Group created successfully!", Toast.LENGTH_SHORT).show()
                isGroupCreated = true
                isGroupCreationInProgress = false // Reset flag

                // Navigate to Class Management screen here
                val intent = Intent(this@StartClassActivity, ClassManagementActivity::class.java)
                intent.putExtra("course_name", courseName.text.toString().trim())
                intent.putExtra("course_code", courseCode.text.toString().trim())
                intent.putExtra("wifi_ssid", wifiSSID.text.toString().trim())
                intent.putExtra("wifi_password", wifiPassword.text.toString().trim()) // Add this line
                intent.putExtra("session_number", sessionNumber.text.toString()) // Add session number
                intent.putExtra("session_type", sessionTypeSpinner.selectedItem?.toString() ?: "") // Add session type
                startActivity(intent)
            }

            override fun onFailure(reason: Int) {
                val errorMessage = when (reason) {
                    WifiP2pManager.P2P_UNSUPPORTED -> "Wi-Fi Direct is not supported on this device."
                    WifiP2pManager.ERROR -> "An unknown error occurred."
                    WifiP2pManager.BUSY -> {
                        Log.e("StartClassActivity", "Wi-Fi Direct service is busy. Retrying group creation...")
                        createGroup() // Retry if busy
                        "Wi-Fi Direct service is busy. Retrying..."
                    }
                    else -> "Failed to create group: $reason"
                }
                Log.e("StartClassActivity", "Group creation failed: $errorMessage")
                Toast.makeText(this@StartClassActivity, "Group creation failed: $errorMessage", Toast.LENGTH_SHORT).show()
                isGroupCreationInProgress = false // Reset flag
            }
        })
    }

    private fun isWifiEnabled(): Boolean {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        return wifiManager.isWifiEnabled
    }

    private fun removeGroup() {
        Log.d("StartClassActivity", "Removing group...")
        wifiP2pManager.removeGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d("StartClassActivity", "Group removed successfully.")
                isGroupCreated = false
            }

            override fun onFailure(reason: Int) {
                Log.e("StartClassActivity", "Failed to remove group: $reason")
            }
        })
    }
}

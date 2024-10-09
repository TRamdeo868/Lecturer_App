package com.example.lecturerapp

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class PermissionsActivity : AppCompatActivity() {

    private lateinit var permissionStatus: TextView
    private lateinit var settingsButton: Button

    // Request codes
    private val LOCATION_PERMISSION_REQUEST_CODE = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permissions)

        permissionStatus = findViewById(R.id.permission_status)
        settingsButton = findViewById(R.id.settings_button)

        checkPermissions()

        settingsButton.setOnClickListener {
            openAppSettings()
        }
    }

    private fun checkPermissions() {
        val locationPermission = ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
        val wifiPermission = ContextCompat.checkSelfPermission(this, android.Manifest.permission.CHANGE_WIFI_STATE)

        // If permissions are not granted, request them
        if (locationPermission != PackageManager.PERMISSION_GRANTED || wifiPermission != PackageManager.PERMISSION_GRANTED) {
            requestPermissions()
        } else {
            navigateToNextScreen()
        }
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.CHANGE_WIFI_STATE),
            LOCATION_PERMISSION_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    navigateToNextScreen()
                } else {
                    permissionStatus.text = "No Permissions"
                    permissionStatus.text = "To use the attendance app, we require that all permissions are granted."
                    settingsButton.visibility = Button.VISIBLE // Show settings button
                }
            }
        }
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.data = Uri.fromParts("package", packageName, null)
        startActivity(intent)
    }

    private fun navigateToNextScreen() {
        // Navigate to the next screen (e.g., start class interface)
        val intent = Intent(this, StartClassActivity::class.java)
        startActivity(intent)
        finish() // Close the current activity
    }
}


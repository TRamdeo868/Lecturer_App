package com.example.lecturerapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat


class MainActivity : AppCompatActivity() {

    private val PERMISSIONS_REQUEST_CODE = 1

    private lateinit var permissionStatus: TextView
    private lateinit var settingsButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        permissionStatus = findViewById(R.id.permission_status)
        settingsButton = findViewById(R.id.settings_button)

        checkAndRequestPermissions()

        settingsButton.setOnClickListener {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        }
    }

    private fun checkAndRequestPermissions() {
        // Check WiFi-Direct and Location permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.CHANGE_WIFI_STATE) != PackageManager.PERMISSION_GRANTED) {

            // Show "No Permissions" message
            permissionStatus.text = "No Permissions"
            Toast.makeText(this, "Permissions are required to use the attendance app.", Toast.LENGTH_LONG).show()

            // Request permissions
            ActivityCompat.requestPermissions(this, arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.CHANGE_WIFI_STATE
            ), PERMISSIONS_REQUEST_CODE)
        } else {
            // Permissions granted, move to the next screen
            navigateToNextScreen()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED &&
                grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                // Permissions granted, move to the next screen
                navigateToNextScreen()
            } else {
                // Permissions not granted
                permissionStatus.text = "No Permissions"
                settingsButton.visibility = View.VISIBLE
            }
        }
    }

    private fun navigateToNextScreen() {
        val intent = Intent(this, StartClassActivity::class.java)
        startActivity(intent)
    }

}

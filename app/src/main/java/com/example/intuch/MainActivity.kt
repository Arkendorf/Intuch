package com.example.intuch

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.*
import java.util.concurrent.TimeUnit

// Main activity
class MainActivity : AppCompatActivity() {
    companion object {
        private const val PERMISSIONS_REQUEST_CODE = 555
        private const val WORK_TAG = "intuch"
    }

    // List of necessary permissions for the app
    private val necessaryPermissions = arrayOf(
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.READ_SMS,
    )

    private lateinit var contactManager: ContactManager // Instance of the contacts manager

    private lateinit var contactList: RecyclerView // ListView for the contacts list

    private var initialized = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Find components
        contactList = findViewById(R.id.contact_list)

        load()
    }

    // Populates the context list, or requests permissions if necessary
    private fun load() {
        // Check the SDK version and whether the permissions are already granted or not.
        for (permission in necessaryPermissions) {
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    necessaryPermissions,
                    PERMISSIONS_REQUEST_CODE
                )
                return
            }
        }

        initialized = true // Remember that we are now initialized

        // Continue if permissions are granted
        // Initialize managers
        contactManager = ContactManager(this)
        contactManager.loadContacts(this)
        contactManager.sort()

        // Prevent notifications from this app occurring while in the app
        unscheduleNotifications()

        // Load view
        val layoutManager = LinearLayoutManager(this)
        contactList.layoutManager = layoutManager
        contactList.adapter = ContactAdapter(this, layoutManager, contactManager)
    }

    // Called when a permission request returns
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                // Permission is granted
                if (!initialized) {
                    load()
                }
            } else {
                finish()
            }
        }
    }

    // Save on destroy
    override fun onPause() {
        super.onPause()

        if (initialized) {
            // Save data
            contactManager.saveContactData(this)
            // Schedule notifications
            scheduleNotifications()
        }
    }

    // Removes scheduled notifications
    private fun unscheduleNotifications() {
        // Remove previous worker
        WorkManager.getInstance(this).cancelAllWorkByTag(WORK_TAG)
    }

    // Schedules notifications to occur when app isn't running
    // unscheduleNotifications() should be called chronologically beforehand
    private fun scheduleNotifications() {
        // Don't have any constraints
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(false)
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .setRequiresCharging(false)
            .setRequiresStorageNotLow(false)
            .setRequiresDeviceIdle(false)
            .build()

        // Create and enqueue new worker
        val periodicWorkRequest = PeriodicWorkRequestBuilder<NotificationWorker>(NotificationWorker.ATTEMPT_INTERVAL, TimeUnit.HOURS)
            .addTag(WORK_TAG)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(this).enqueue(periodicWorkRequest)
    }
}
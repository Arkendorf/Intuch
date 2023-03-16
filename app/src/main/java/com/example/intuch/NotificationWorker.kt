package com.example.intuch

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.time.Duration
import java.time.Instant

class NotificationWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {
    companion object {
        const val ATTEMPT_INTERVAL: Long = 4 // How long between notification checks
        const val REPEAT_INTERVAL: Long = 24 // How long between notifications for the same contact

        private const val GROUP_ID = "intuch"
        private const val CHANNEL_ID = "reminders"
        private const val CHANNEL_NAME = "Reminders"
        private const val CHANNEL_IMPORTANCE = NotificationManager.IMPORTANCE_HIGH
    }

    override fun doWork(): Result {
        // Get contacts
        val contactManager = ContactManager()
        contactManager.loadContacts(applicationContext)

        // Keep track of number of notifications
        var notificationCount = 0

        // Construct and register the notification channel
        val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, CHANNEL_IMPORTANCE)
        val notificationManager: NotificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)

        // Iterate through contacts
        for (i in contactManager.contacts.indices) {
            val contact = contactManager.contacts[i]

            // Check if we should notify for this contact
            if (
                contact.notify &&
                contactManager.notifyPercent(contact) >= 1 &&
                contact.lastNotified + Duration.ofHours(REPEAT_INTERVAL) < Instant.now(
            )) {
                // Create intent for when notification is clicked
                val intent = Intent(Intent.ACTION_VIEW, Uri.fromParts("sms", contact.numbers[0], null))
                val pendingIntent: PendingIntent = PendingIntent.getActivity(applicationContext, 0, intent, PendingIntent.FLAG_IMMUTABLE)

                // Construct notification
                val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                    .setSmallIcon(R.mipmap.ic_notification)
                    .setContentTitle("${applicationContext.getString(R.string.notification_title)} ${contact.name}")
                    .setContentText(applicationContext.getString(R.string.notification_body))
                    .setContentIntent(pendingIntent)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setGroup(GROUP_ID)
                    .setAutoCancel(true)
                    .build()

                // Notify
                with(NotificationManagerCompat.from(applicationContext)) {
                    notify(i, notification)
                }

                notificationCount += 1

                // Remember that contact was notified
                contact.lastNotified = Instant.now()
            }
        }

        // If at least two notifications were sent, group them under a summary notification
        if (notificationCount > 1) {
            val summaryNotification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_notification)
                .setGroup(GROUP_ID)
                .setGroupSummary(true)
                .build()

            with(NotificationManagerCompat.from(applicationContext)) {
                notify(-1, summaryNotification)
            }
        }

        // Save last contacted times
        contactManager.saveContactData(applicationContext)

        return Result.success()
    }
}
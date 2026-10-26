package com.example.EnglishTagalogBible

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Worker
import androidx.work.WorkerParameters

class DailyVerseWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {

    override fun doWork(): Result {
        // 1. Verify OS-level notification permission state
        val isSystemNotificationEnabled = NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()
        if (!isSystemNotificationEnabled) {
            return Result.success()
        }

        // 2. Retrieve a fresh verse for the notification
        val verse = BibleRepository.getRandomVerse(applicationContext)
        showNotification(verse.text, verse.reference)

        return Result.success()
    }

    private fun showNotification(verseText: String, verseRef: String) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "daily_verse_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Daily Bible Verse",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Delivers daily Bible verse notifications"
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Action 1: Tap Notification to Open App
        val openAppIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("verse_text", verseText)
            putExtra("verse_ref", verseRef)
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action 2: "COPY" button action inside notification pop-out
        val copyIntent = Intent(applicationContext, CopyVerseReceiver::class.java).apply {
            putExtra("verse_text", "$verseText — $verseRef")
        }
        val copyPendingIntent = PendingIntent.getBroadcast(
            applicationContext,
            1,
            copyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_compass) // Uses system standard icon (or replace with your R.drawable.ic_notification)
            .setContentTitle("Verse of the Day")
            .setContentText("$verseText — $verseRef")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$verseText\n\n— $verseRef"))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openAppPendingIntent)
            .setAutoCancel(true)
            .addAction(android.R.drawable.ic_menu_send, "COPY", copyPendingIntent)
            .build()

        notificationManager.notify(1001, notification)
    }
}

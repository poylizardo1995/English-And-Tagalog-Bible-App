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
        val isSystemNotificationEnabled = NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()
        if (!isSystemNotificationEnabled) {
            return Result.success()
        }

        val verse = BibleRepository.getRandomVerse(applicationContext)
        showNotification(verse.text, verse.reference)

        return Result.success()
    }

    companion object {
        const val CHANNEL_ID = "daily_verse_channel"
        const val CHANNEL_NAME = "Daily Bible Verse"
    }

    private fun showNotification(verseText: String, verseRef: String) {
    val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Delivers daily Bible verse notifications"
            }
            notificationManager.createNotificationChannel(channel)
        }
    
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
    
        val copyIntent = Intent(applicationContext, VerseActionReceiver::class.java).apply {
            action = "ACTION_COPY_VERSE"
            putExtra("verse_text", verseText)
            putExtra("verse_ref", verseRef)
        }
        val copyPendingIntent = PendingIntent.getBroadcast(
            applicationContext,
            1,
            copyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
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

package com.example.EnglishTagalogBible

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.core.app.NotificationCompat

object LockScreenVerseService {
    private const val CHANNEL_ID = "daily_verse_channel"
    private const val NOTIFICATION_ID = 1001

    fun showLockScreenNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Daily Bible Verse",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Daily inspirational verse alerts"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val verse = BibleRepository.getRandomVerse(context)

        val largeIconBitmap = BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            putExtra("verse_text", verse.text)
            putExtra("verse_ref", verse.reference)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val openPendingIntent = PendingIntent.getActivity(
            context, 0, openIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val copyIntent = Intent(context, VerseActionReceiver::class.java).apply {
            action = "ACTION_COPY_VERSE"
            putExtra("verse_text", verse.text)
            putExtra("verse_ref", verse.reference)
        }
        val copyPendingIntent = PendingIntent.getBroadcast(
            context, 1, copyIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(largeIconBitmap)           
            .setContentTitle("Verse of the Day")
            .setContentText("${verse.text} — ${verse.reference}")
            .setStyle(NotificationCompat.BigTextStyle().bigText("${verse.text}\n\n— ${verse.reference}"))
            .setContentIntent(openPendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .addAction(android.R.drawable.ic_menu_set_as, "Copy", copyPendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}

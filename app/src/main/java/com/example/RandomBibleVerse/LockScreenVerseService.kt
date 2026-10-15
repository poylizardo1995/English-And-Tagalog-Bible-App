package com.example.RandomBibleVerse

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.BitmapFactory
import androidx.core.app.NotificationCompat

object LockScreenVerseService {
    private const val CHANNEL_ID = "bible_lockscreen_channel"

    fun showLockScreenNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Daily Bible Verse",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        // Pass 'context' here so it can successfully read from JSON assets
        val verse = BibleRepository.getRandomVerse(context)

        // Decode the full-color launcher icon for the notification shade preview
        val largeIconBitmap = BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification) // Clean white silhouette for status bar
            .setLargeIcon(largeIconBitmap)             // Full-color custom Bible icon inside notification card
            .setContentTitle("Verse of the Day")
            .setContentText("${verse.text} — ${verse.reference}")
            .setStyle(NotificationCompat.BigTextStyle().bigText("${verse.text}\n\n— ${verse.reference}"))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(true)
            .build()

        notificationManager.notify(1001, notification)
    }
}

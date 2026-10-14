package com.example.RandomBibleVerse

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
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

        // Pass 'context' here so it can successfully read from verses.json
        val verse = BibleRepository.getRandomVerse(context)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
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

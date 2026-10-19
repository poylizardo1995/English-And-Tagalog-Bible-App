package com.example.EnglishTagalogBible

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

class DailyVerseWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {
    override fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("BibleAppPrefs", Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean("key_notifications_enabled", true)
        
        if (isEnabled) {
            LockScreenVerseService.showLockScreenNotification(applicationContext)
        }
        return Result.success()
    }
}

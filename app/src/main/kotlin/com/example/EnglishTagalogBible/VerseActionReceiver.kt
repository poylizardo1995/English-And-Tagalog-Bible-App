package com.example.EnglishTagalogBible

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast

class VerseActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val text = intent.getStringExtra("verse_text") ?: return
        val ref = intent.getStringExtra("verse_ref") ?: return

        if (intent.action == "ACTION_COPY_VERSE") {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Bible Verse", "$text — $ref")
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "Verse copied to clipboard!", Toast.LENGTH_SHORT).show()
        }
    }
}

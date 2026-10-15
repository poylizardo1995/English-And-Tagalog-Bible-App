package com.example.RandomBibleVerse

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.animation.AlphaAnimation
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var tvVerseText: TextView
    private lateinit var tvVerseRef: TextView
    private lateinit var btnRefresh: Button
    private lateinit var btnCopy: MaterialButton
    private lateinit var btnShare: MaterialButton
    
    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var currentVerse: BibleVerse? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvVerseText = findViewById(R.id.tvVerseText)
        tvVerseRef = findViewById(R.id.tvVerseRef)
        btnRefresh = findViewById(R.id.btnRefresh)
        btnCopy = findViewById(R.id.btnCopy)
        btnShare = findViewById(R.id.btnShare)

        // Initialize Text-to-Speech engine
        tts = TextToSpeech(this, this)

        loadAndSpeakVerse(false) // Load initial verse silently

        btnRefresh.setOnClickListener {
            loadAndSpeakVerse(true) // Load, animate, and speak aloud
        }

        // Setup Copy Button
        btnCopy.setOnClickListener {
            val verseText = tvVerseText.text.toString()
            val verseRef = tvVerseRef.text.toString()
            val fullText = "$verseText $verseRef"

            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Bible Verse", fullText)
            clipboard.setPrimaryClip(clip)

            Toast.makeText(this, "Verse copied to clipboard!", Toast.LENGTH_SHORT).show()
        }

        // Setup Share Button
        btnShare.setOnClickListener {
            val verseText = tvVerseText.text.toString()
            val verseRef = tvVerseRef.text.toString()
            val shareBody = "$verseText\n\n$verseRef"

            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareBody)
            }
            startActivity(Intent.createChooser(shareIntent, "Share Verse via"))
        }

        LockScreenVerseService.showLockScreenNotification(this)
    }

    private fun loadAndSpeakVerse(speakAloud: Boolean) {
        currentVerse = BibleRepository.getRandomVerse(this)
        currentVerse?.let { verse ->
            tvVerseText.text = verse.text
            tvVerseRef.text = "— ${verse.reference}"

            // Add smooth fade-in animation on text change
            val fadeIn = AlphaAnimation(0.0f, 1.0f).apply {
                duration = 400
            }
            tvVerseText.startAnimation(fadeIn)
            tvVerseRef.startAnimation(fadeIn)

            if (speakAloud && isTtsReady) {
                speakVerse(verse)
            }
        }
    }

    private fun speakVerse(verse: BibleVerse) {
        val isTagalog = isTagalogText(verse.text)
        
        val locale = if (isTagalog) {
            Locale("fil", "PH") // Filipino / Tagalog accent
        } else {
            Locale.US // English accent
        }

        val result = tts?.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts?.setLanguage(Locale.getDefault())
        }

        val speechText = "${verse.text}. Reference: ${verse.reference}"
        tts?.speak(speechText, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun isTagalogText(text: String): Boolean {
        val tagalogKeywords = listOf("ang", "mga", "sa", "at", "na", "ng", "ay", "kayo", "tayo", "ka", "mo", "ko", "ni", "para", "isang", "rin", "din")
        val words = text.lowercase().split(Regex("\\s+"))
        val matchCount = words.count { tagalogKeywords.contains(it) }
        return matchCount >= 2
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.setLanguage(Locale.US)
            isTtsReady = true
        }
    }

    override fun onDestroy() {
        tts?.let {
            it.stop()
            it.shutdown()
        }
        super.onDestroy()
    }
}

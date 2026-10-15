package com.example.RandomBibleVerse

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var tvVerseText: TextView
    private lateinit var tvVerseRef: TextView
    private lateinit var btnRefresh: Button
    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var currentVerse: BibleVerse? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvVerseText = findViewById(R.id.tvVerseText)
        tvVerseRef = findViewById(R.id.tvVerseRef)
        btnRefresh = findViewById(R.id.btnRefresh)

        // Initialize Text-to-Speech engine
        tts = TextToSpeech(this, this)

        loadAndSpeakVerse(false) // Load initial verse silently or let user tap

        btnRefresh.setOnClickListener {
            loadAndSpeakVerse(true) // Load and speak aloud
        }

        LockScreenVerseService.showLockScreenNotification(this)
    }

    private fun loadAndSpeakVerse(speakAloud: Boolean) {
        currentVerse = BibleRepository.getRandomVerse(this)
        currentVerse?.let { verse ->
            tvVerseText.text = "${verse.text}"
            tvVerseRef.text = "— ${verse.reference}"

            if (speakAloud && isTtsReady) {
                speakVerse(verse)
            }
        }
    }

    private fun speakVerse(verse: BibleVerse) {
        // Simple heuristic: check if the text contains common Tagalog words or characters
        val isTagalog = isTagalogText(verse.text)
        
        val locale = if (isTagalog) {
            Locale("fil", "PH") // Filipino / Tagalog accent
        } else {
            Locale.US // English accent
        }

        val result = tts?.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            // Fallback to default if Tagalog pack isn't installed on the phone yet
            tts?.setLanguage(Locale.getDefault())
        }

        val speechText = "${verse.text}. Reference: ${verse.reference}"
        tts?.speak(speechText, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun isTagalogText(text: String): Boolean {
        val tagalogKeywords = listOf("ang", "mga", "sa", "at", "na", "ng", "ay", "kayo", "tayo", "ka", "mo", "ko", "ni", "para", "isang", "rin", "din")
        val words = text.lowercase().split(Regex("\\s+"))
        val matchCount = words.count { tagalogKeywords.contains(it) }
        return matchCount >= 2 // If it has multiple Tagalog marker words, switch accent
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

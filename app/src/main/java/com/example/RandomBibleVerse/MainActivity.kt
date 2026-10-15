package com.example.RandomBibleVerse

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.content.res.ColorStateList
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var tvVerseText: TextView
    private lateinit var tvVerseRef: TextView
    private lateinit var btnRefresh: Button
    private lateinit var btnCopy: MaterialButton
    private lateinit var btnShare: MaterialButton
    private lateinit var btnSpeak: MaterialButton
    private lateinit var btnSelectManual: MaterialButton
    private lateinit var btnToggleTheme: MaterialButton
    private lateinit var btnPrev: MaterialButton
    private lateinit var btnNext: MaterialButton
    private lateinit var btnAutoPlay: MaterialButton
    private lateinit var verseCard: MaterialCardView
    
    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var currentVerse: BibleVerse? = null
    private var isDarkMode = false
    private var isAutoPlaying = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvVerseText = findViewById(R.id.tvVerseText)
        tvVerseRef = findViewById(R.id.tvVerseRef)
        btnRefresh = findViewById(R.id.btnRefresh)
        btnCopy = findViewById(R.id.btnCopy)
        btnShare = findViewById(R.id.btnShare)
        btnSpeak = findViewById(R.id.btnSpeak)
        btnSelectManual = findViewById(R.id.btnSelectManual)
        btnToggleTheme = findViewById(R.id.btnToggleTheme)
        
        // --- NEW BUTTON INITIALIZATIONS ---
        // (Make sure to add these IDs to your activity_main.xml layout!)
        btnPrev = findViewById(R.id.btnPrev)
        btnNext = findViewById(R.id.btnNext)
        btnAutoPlay = findViewById(R.id.btnAutoPlay)
        
        verseCard = findViewById(R.id.verseCard)

        // Initialize Text-to-Speech engine
        tts = TextToSpeech(this, this)

        // Load initial verse silently
        loadVerse()

        // 1. New Random Verse Button
        btnRefresh.setOnClickListener {
            stopAutoPlay()
            loadVerse()
        }

        // 2. Choose Book, Chapter & Verse Button
        btnSelectManual.setOnClickListener {
            stopAutoPlay()
            showBookSelectionDialog()
        }

        // 3. Previous Verse Button
        btnPrev.setOnClickListener {
            currentVerse?.let { verse ->
                val prevVerse = BibleRepository.getPreviousVerse(verse, this)
                if (prevVerse != null) {
                    displayVerse(prevVerse)
                    if (isAutoPlaying) speakVerse(prevVerse)
                } else {
                    Toast.makeText(this, "Beginning of chapter reached", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // 4. Next Verse Button
        btnNext.setOnClickListener {
            currentVerse?.let { verse ->
                val nextVerse = BibleRepository.getNextVerse(verse, this)
                if (nextVerse != null) {
                    displayVerse(nextVerse)
                    if (isAutoPlaying) speakVerse(nextVerse)
                } else {
                    Toast.makeText(this, "End of chapter reached", Toast.LENGTH_SHORT).show()
                    stopAutoPlay()
                }
            }
        }

        // 5. Auto-Play / Continuous Listen Button
        btnAutoPlay.setOnClickListener {
            if (isAutoPlaying) {
                stopAutoPlay()
                Toast.makeText(this, "Auto-Play Stopped", Toast.LENGTH_SHORT).show()
            } else {
                startAutoPlay()
            }
        }

        // 6. Copy Button
        btnCopy.setOnClickListener {
            val verseText = tvVerseText.text.toString()
            val verseRef = tvVerseRef.text.toString()
            val fullText = "$verseText$verseRef"

            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Bible Verse", fullText)
            clipboard.setPrimaryClip(clip)

            Toast.makeText(this, "Verse copied to clipboard!", Toast.LENGTH_SHORT).show()
        }

        // 7. Share Button
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

        // 8. Speak Button (Manual TTS trigger)
        btnSpeak.setOnClickListener {
            currentVerse?.let { verse ->
                if (isTtsReady) {
                    stopAutoPlay()
                    speakVerse(verse)
                } else {
                    Toast.makeText(this, "Speech engine is still initializing...", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // 9. Day/Night Theme Toggle Button
        btnToggleTheme.setOnClickListener {
            isDarkMode = !isDarkMode
            applyTheme()
        }

        LockScreenVerseService.showLockScreenNotification(this)

        // Apply default theme colors immediately on startup
        applyTheme()
    }

    private fun startAutoPlay() {
        if (!isTtsReady) {
            Toast.makeText(this, "Speech engine not ready yet", Toast.LENGTH_SHORT).show()
            return
        }
        isAutoPlaying = true
        btnAutoPlay.text = "⏹ Stop Auto-Play"
        currentVerse?.let { speakVerse(it) }
        Toast.makeText(this, "Auto-Play Started", Toast.LENGTH_SHORT).show()
    }

    private fun stopAutoPlay() {
        isAutoPlaying = false
        btnAutoPlay.text = "▶ Auto-Play"
        tts?.stop()
    }

    private fun applyTheme() {
        val rootLayout = findViewById<ViewGroup>(android.R.id.content).rootView

        if (isDarkMode) {
            // --- NIGHT MODE COLORS ---
            rootLayout.setBackgroundColor(Color.parseColor("#121212"))
            verseCard.setCardBackgroundColor(Color.parseColor("#1E1E1E"))
            verseCard.strokeColor = Color.parseColor("#333333")
            
            tvVerseText.setTextColor(Color.parseColor("#E0E0E0"))
            tvVerseRef.setTextColor(Color.parseColor("#B08D73"))
            
            btnToggleTheme.text = "☀️ Switch to Day View"
            btnToggleTheme.setTextColor(Color.parseColor("#D7CCC8"))
            btnToggleTheme.strokeColor = ColorStateList.valueOf(Color.parseColor("#D7CCC8"))
            
            btnRefresh.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#8D6E63"))
            btnRefresh.setTextColor(Color.parseColor("#

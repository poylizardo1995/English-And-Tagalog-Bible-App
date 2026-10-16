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
    private lateinit var btnAbout: MaterialButton
    private lateinit var verseCard: MaterialCardView
    
    // Language Toggle Pill Views
    private lateinit var btnEnglish: TextView
    private lateinit var btnTagalog: TextView
    private lateinit var languageToggleContainer: ViewGroup
    
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
        
        // Navigation, Audio & About Buttons
        btnPrev = findViewById(R.id.btnPrev)
        btnNext = findViewById(R.id.btnNext)
        btnAutoPlay = findViewById(R.id.btnAutoPlay)
        btnAbout = findViewById(R.id.btnAbout)
        
        verseCard = findViewById(R.id.verseCard)

        // Bind Language Toggle Pill Views (included layout via @id/languageTogglePill)
        val languageToggleInclude = findViewById<android.view.View>(R.id.languageTogglePill)
        btnEnglish = languageToggleInclude.findViewById(R.id.btnKjv) // Binding to btnKjv view ID from XML
        btnTagalog = languageToggleInclude.findViewById(R.id.btnTagalog)
        languageToggleContainer = languageToggleInclude.parent as? ViewGroup ?: (languageToggleInclude as ViewGroup)

        // Initialize Text-to-Speech engine
        tts = TextToSpeech(this, this)

        // Set up Language Pill Click Listeners with Dropdown Support
        btnEnglish.setOnClickListener {
            stopAutoPlay()
            handleLanguageSelection("English")
        }

        btnTagalog.setOnClickListener {
            stopAutoPlay()
            handleLanguageSelection("Tagalog")
        }

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

        // 10. About & Credits Button
        btnAbout.setOnClickListener {
            showAboutDialog()
        }

        LockScreenVerseService.showLockScreenNotification(this)

        // Apply default theme colors immediately on startup
        applyTheme()
        updateLanguageToggleUI(BibleRepository.currentTranslation)
    }

    private fun handleLanguageSelection(groupName: String) {
        val matchingTranslations = BibleTranslation.values().filter { it.languageGroup == groupName }

        if (matchingTranslations.size == 1) {
            val target = matchingTranslations[0]
            BibleRepository.setTranslation(this, target)
            updateLanguageToggleUI(target)
            loadVerse()
        } else {
            val options = matchingTranslations.map { it.displayName }.toTypedArray()
            val currentIndex = matchingTranslations.indexOf(BibleRepository.currentTranslation)

            AlertDialog.Builder(this)
                .setTitle("Select $groupName Version")
                .setSingleChoiceItems(options, if (currentIndex != -1) currentIndex else 0) { dialog, which ->
                    val selected = matchingTranslations[which]
                    BibleRepository.setTranslation(this, selected)
                    updateLanguageToggleUI(selected)
                    loadVerse()
                    dialog.dismiss()
                }
                .show()
        }
    }

    private fun updateLanguageToggleUI(translation: BibleTranslation) {
        val isEnglishActive = translation.languageGroup == "English"
        
        // Update labels to show version dropdown hint if multiple options exist, or standard names
        btnEnglish.text = if (isEnglishActive) "${translation.displayName.substringAfter("(").substringBefore(")")} ▾" else "English ▾"
        btnTagalog.text = if (!isEnglishActive) "${translation.displayName.substringAfter("(").substringBefore(")")} ▾" else "Tagalog ▾"

        if (isDarkMode) {
            // --- NIGHT MODE TOGGLE STYLING ---
            languageToggleContainer.background = getDrawable(R.drawable.bg_pill_container)
            if (isEnglishActive) {
                btnEnglish.setBackgroundResource(R.drawable.bg_pill_selected)
                btnEnglish.setTextColor(Color.parseColor("#121212"))
                btnTagalog.setBackgroundColor(Color.TRANSPARENT)
                btnTagalog.setTextColor(Color.parseColor("#D7CCC8"))
            } else {
                btnTagalog.setBackgroundResource(R.drawable.bg_pill_selected)
                btnTagalog.setTextColor(Color.parseColor("#121212"))
                btnEnglish.setBackgroundColor(Color.TRANSPARENT)
                btnEnglish.setTextColor(Color.parseColor("#D7CCC8"))
            }
        } else {
            // --- DAY MODE TOGGLE STYLING ---
            if (isEnglishActive) {
                btnEnglish.setBackgroundResource(R.drawable.bg_pill_selected)
                btnEnglish.setTextColor(Color.WHITE)
                btnTagalog.setBackgroundColor(Color.TRANSPARENT)
                btnTagalog.setTextColor(Color.parseColor("#555555"))
            } else {
                btnTagalog.setBackgroundResource(R.drawable.bg_pill_selected)
                btnTagalog.setTextColor(Color.WHITE)
                btnEnglish.setBackgroundColor(Color.TRANSPARENT)
                btnEnglish.setTextColor(Color.parseColor("#555555"))
            }
        }
    }

    private fun showAboutDialog() {
        AlertDialog.Builder(this)
            .setTitle("About")
            .setMessage(
                "English and Tagalog Bible v1.1\n\n" +
                "Software Developer: Aldrin Paul Sipoy Lizardo\n\n" +
                "Copyright © 2026. All Rights Reserved. To God be the glory!\n\n" +
                "Scripture Text Provided via Public Domain Translations: King James Version (KJV), Ang Dating Biblia 1905 (ADB1905).\n\n" +    
                "Github: https://github.com/poylizardo1995\n\n"
            )
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .show()
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

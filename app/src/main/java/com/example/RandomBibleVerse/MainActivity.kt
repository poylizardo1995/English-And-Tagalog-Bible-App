package com.example.RandomBibleVerse

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.content.res.ColorStateList
import android.os.Bundle
import android.speech.tts.TextToSpeech
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
    private lateinit var verseCard: MaterialCardView
    
    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var currentVerse: BibleVerse? = null
    private var isDarkMode = false

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
        verseCard = findViewById(R.id.verseCard)

        // Initialize Text-to-Speech engine
        tts = TextToSpeech(this, this)

        // Load initial verse silently
        loadVerse()

        // 1. New Random Verse Button
        btnRefresh.setOnClickListener {
            loadVerse() // Loads and animates silently
        }

        // 2. Choose Book, Chapter & Verse Button
        btnSelectManual.setOnClickListener {
            showBookSelectionDialog()
        }

        // 3. Copy Button
        btnCopy.setOnClickListener {
            val verseText = tvVerseText.text.toString()
            val verseRef = tvVerseRef.text.toString()
            val fullText = "$verseText$verseRef"

            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Bible Verse", fullText)
            clipboard.setPrimaryClip(clip)

            Toast.makeText(this, "Verse copied to clipboard!", Toast.LENGTH_SHORT).show()
        }

        // 4. Share Button
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

        // 5. Speak Button (Manual TTS trigger)
        btnSpeak.setOnClickListener {
            currentVerse?.let { verse ->
                if (isTtsReady) {
                    speakVerse(verse)
                } else {
                    Toast.makeText(this, "Speech engine is still initializing...", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // 6. Day/Night Theme Toggle Button
        btnToggleTheme.setOnClickListener {
            isDarkMode = !isDarkMode
            applyTheme()
        }

        LockScreenVerseService.showLockScreenNotification(this)

        // Apply default theme colors immediately on startup
        applyTheme()
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
            btnRefresh.setTextColor(Color.parseColor("#121212"))
            
            btnCopy.setTextColor(Color.parseColor("#D7CCC8"))
            btnCopy.strokeColor = ColorStateList.valueOf(Color.parseColor("#D7CCC8"))
            
            btnShare.setTextColor(Color.parseColor("#D7CCC8"))
            btnShare.strokeColor = ColorStateList.valueOf(Color.parseColor("#D7CCC8"))

            btnSpeak.setTextColor(Color.parseColor("#D7CCC8"))
            btnSpeak.strokeColor = ColorStateList.valueOf(Color.parseColor("#D7CCC8"))
            
            btnSelectManual.setTextColor(Color.parseColor("#D7CCC8"))
            btnSelectManual.strokeColor = ColorStateList.valueOf(Color.parseColor("#D7CCC8"))

        } else {
            // --- DAY MODE COLORS (Original Warm Theme) ---
            rootLayout.setBackgroundResource(R.drawable.bg_gradient)
            verseCard.setCardBackgroundColor(Color.parseColor("#FFFFFF"))
            verseCard.strokeColor = Color.parseColor("#E8E2D5")
            
            tvVerseText.setTextColor(Color.parseColor("#2D241E"))
            tvVerseRef.setTextColor(Color.parseColor("#8C6D53"))
            
            btnToggleTheme.text = "🌙 Switch to Night View"
            btnToggleTheme.setTextColor(Color.parseColor("#6E472C"))
            btnToggleTheme.strokeColor = ColorStateList.valueOf(Color.parseColor("#6E472C"))
            
            btnRefresh.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#6E472C"))
            btnRefresh.setTextColor(Color.WHITE)
            
            btnCopy.setTextColor(Color.parseColor("#6E472C"))
            btnCopy.strokeColor = ColorStateList.valueOf(Color.parseColor("#6E472C"))
            
            btnShare.setTextColor(Color.parseColor("#6E472C"))
            btnShare.strokeColor = ColorStateList.valueOf(Color.parseColor("#6E472C"))

            btnSpeak.setTextColor(Color.parseColor("#6E472C"))
            btnSpeak.strokeColor = ColorStateList.valueOf(Color.parseColor("#6E472C"))
            
            btnSelectManual.setTextColor(Color.parseColor("#6E472C"))
            btnSelectManual.strokeColor = ColorStateList.valueOf(Color.parseColor("#6E472C"))
        }
    }

    // Dialog 1: Choose Book
    private fun showBookSelectionDialog() {
        val books = BibleRepository.getAvailableBooks(this).toTypedArray()
        
        if (books.isEmpty()) {
            Toast.makeText(this, "No books available", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Select Book")
            .setItems(books) { _, which ->
                val selectedBook = books[which]
                showChapterSelectionDialog(selectedBook)
            }
            .show()
    }

    // Dialog 2: Choose Chapter from Book
    private fun showChapterSelectionDialog(bookName: String) {
        val chapters = BibleRepository.getChaptersForBook(bookName, this).toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Select Chapter in $bookName")
            .setItems(chapters) { _, which ->
                val selectedChapter = chapters[which]
                showVerseSelectionDialog(selectedChapter)
            }
            .show()
    }

    // Dialog 3: Choose Verse from Chapter
    private fun showVerseSelectionDialog(chapterName: String) {
        val chapterVerses = BibleRepository.getVerseObjectsForChapter(chapterName, this)
        val verseTitles = chapterVerses.map { it.reference }.toTypedArray()

        if (verseTitles.isEmpty()) {
            Toast.makeText(this, "No verses found for $chapterName", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Select Verse in $chapterName")
            .setItems(verseTitles) { _, which ->
                val selectedVerse = chapterVerses[which]
                displayVerse(selectedVerse) // Display silently without auto-reading
            }
            .show()
    }

    // Helper function to update the screen with a specific verse silently
    private fun displayVerse(verse: BibleVerse) {
        currentVerse = verse
        tvVerseText.text = verse.text
        tvVerseRef.text = "— ${verse.reference}"

        // Smooth fade-in animation
        val fadeIn = AlphaAnimation(0.0f, 1.0f).apply { duration = 400 }
        tvVerseText.startAnimation(fadeIn)
        tvVerse

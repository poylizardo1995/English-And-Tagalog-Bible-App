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

    companion object {
        private const val KEY_VERSE_TEXT = "key_verse_text"
        private const val KEY_VERSE_REF = "key_verse_ref"
    }

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

        // Restore state on orientation change or load initial random verse on fresh launch
        if (savedInstanceState != null) {
            val savedText = savedInstanceState.getString(KEY_VERSE_TEXT)
            val savedRef = savedInstanceState.getString(KEY_VERSE_REF)
            if (savedText != null && savedRef != null) {
                val cleanRef = savedRef.removePrefix("— ").trim()
                currentVerse = BibleVerse(text = savedText, reference = cleanRef)
                tvVerseText.text = currentVerse?.text
                tvVerseRef.text = "— ${currentVerse?.reference}"
            } else {
                loadVerse()
            }
        } else {
            loadVerse()
        }

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
                    val toast = Toast.makeText(this, "Beginning of chapter reached.", Toast.LENGTH_SHORT)
                    toast.setGravity(android.view.Gravity.CENTER, 0, 0)
                    toast.show()
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
                    val toast = Toast.makeText(this, "End of chapter reached.", Toast.LENGTH_SHORT)
                    toast.setGravity(android.view.Gravity.CENTER, 0, 0)
                    toast.show()
                    stopAutoPlay()
                }
            }
        }

        // 5. Auto-Play / Continuous Listen Button
        btnAutoPlay.setOnClickListener {
            if (isAutoPlaying) {
                stopAutoPlay()                
                val toast = Toast.makeText(this, "Auto-Play Stopped.", Toast.LENGTH_SHORT)
                toast.setGravity(android.view.Gravity.CENTER, 0, 0)
                toast.show()
            } else {
                startAutoPlay()
            }
        }

        // 6. Copy Button
        btnCopy.setOnClickListener {
            val verseText = tvVerseText.text.toString()
            val verseRef = tvVerseRef.text.toString()
            val fullText = "$verseText $verseRef"

            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Bible Verse", fullText)
            clipboard.setPrimaryClip(clip)
            val toast = Toast.makeText(this, "Verse copied to clipboard!", Toast.LENGTH_SHORT)
            toast.setGravity(android.view.Gravity.CENTER, 0, 0)
            toast.show()
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
                    val toast = Toast.makeText(this, "Speech Engine is still initializing...", Toast.LENGTH_SHORT)
                    toast.setGravity(android.view.Gravity.CENTER, 0, 0)
                    toast.show()
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        currentVerse?.let {
            outState.putString(KEY_VERSE_TEXT, it.text)
            outState.putString(KEY_VERSE_REF, it.reference)
        }
    }

    private fun handleLanguageSelection(groupName: String) {
        val matchingTranslations = BibleTranslation.values().filter { it.languageGroup == groupName }

        if (matchingTranslations.size == 1) {
            val target = matchingTranslations[0]
            if (target != BibleRepository.currentTranslation) {
                switchToTranslation(target)
            }
        } else {
            val options = matchingTranslations.map { it.displayName }.toTypedArray()
            val currentIndex = matchingTranslations.indexOf(BibleRepository.currentTranslation)

            AlertDialog.Builder(this)
                .setTitle("Select $groupName Version")
                .setSingleChoiceItems(options, if (currentIndex != -1) currentIndex else 0) { dialog, which ->
                    val selected = matchingTranslations[which]
                    if (selected != BibleRepository.currentTranslation) {
                        switchToTranslation(selected)
                    }
                    dialog.dismiss()
                }
                .show()
        }
    }

    private fun switchToTranslation(target: BibleTranslation) {
        val currentBookChapterAndVerse = currentVerse?.reference?.substringBefore(" (")
        val previousTranslation = BibleRepository.currentTranslation

        // Check if the target translation actually has files for this specific book/chapter or suffix
        val assetFiles = assets.list("") ?: emptyArray()
        val targetSuffix = "_${target.suffix}.json"
        val hasSuffixFiles = assetFiles.any { it.endsWith(targetSuffix) }

        if (!hasSuffixFiles && target != BibleTranslation.KJV && target != BibleTranslation.ADB1905) {
            // Explicitly catch unpopulated translations that lack asset files
            val toast = Toast.makeText(this, "This translation is not available yet.", Toast.LENGTH_SHORT)
            toast.setGravity(android.view.Gravity.CENTER, 0, 0)
            toast.show()
            updateLanguageToggleUI(previousTranslation)
            return
        }

        if (currentBookChapterAndVerse != null) {
            BibleRepository.loadVerses(this, target)
            val matchingVerse = BibleRepository.getVerseByBookChapterAndVerse(currentBookChapterAndVerse, this)

            // Ensure the matching verse actually belongs to the target translation (not fallback Psalm 23:1)
            val isFallbackVerse = matchingVerse?.text == "The Lord is my shepherd; I shall not want." && currentBookChapterAndVerse != "Psalm 23:1 (KJV)"

            if (matchingVerse != null && !isFallbackVerse) {
                BibleRepository.setTranslation(this, target)
                updateLanguageToggleUI(target)
                displayVerse(matchingVerse)
            } else {
                BibleRepository.loadVerses(this, previousTranslation)
                updateLanguageToggleUI(previousTranslation)
                val toast = Toast.makeText(this, "This translation is not available yet.", Toast.LENGTH_SHORT)
                toast.setGravity(android.view.Gravity.CENTER, 0, 0)
                toast.show()
            }
        } else {
            BibleRepository.setTranslation(this, target)
            updateLanguageToggleUI(target)
            loadVerse()
        }
    }

    private fun updateLanguageToggleUI(translation: BibleTranslation) {
        val isEnglishActive = translation.languageGroup == "English"
        
        btnEnglish.text = if (isEnglishActive) "${translation.displayName.substringAfter("(").substringBefore(")")} ▾" else "ENGLISH ▾"
        btnTagalog.text = if (!isEnglishActive) "${translation.displayName.substringAfter("(").substringBefore(")")} ▾" else "TAGALOG ▾"

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
            .setTitle("About & Credits")
            .setMessage(
                "English and Tagalog Bible v1.1\n\n" +
                "App Developed by Poy Lizardo\n\n" +
                "Copyright © 2026. All Rights Reserved. To God be the glory!\n\n" +
                "Scripture Text Provided via Public Domain Translations: English - King James Version (KJV) and Tagalog - Ang Dating Biblia 1905 (ADB1905).\n\n" +    
                "Github: https://github.com/poylizardo1995/English-And-Tagalog-Bible-App\n\n"
            )
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun startAutoPlay() {
        if (!isTtsReady) {
            val toast = Toast.makeText(this, "Speech engine not ready yet", Toast.LENGTH_SHORT)
            toast.setGravity(android.view.Gravity.CENTER, 0, 0)
            toast.show()
            return
        }
        isAutoPlaying = true
        btnAutoPlay.text = "⏹ Stop Auto-Play"
        currentVerse?.let { speakVerse(it) }
        val toast = Toast.makeText(this, "Auto-Play Started", Toast.LENGTH_SHORT)
        toast.setGravity(android.view.Gravity.CENTER, 0, 0)
        toast.show()
    }

    private fun stopAutoPlay() {
        isAutoPlaying = false
        btnAutoPlay.text = "▶ Auto-Play"
        tts?.stop()
    }

    private fun applyTheme() {
        val rootLayout = findViewById<ViewGroup>(android.R.id.content).getChildAt(0) ?: window.decorView

        if (isDarkMode) {
            // --- PROFESSIONAL NIGHT MODE ---
            rootLayout.setBackgroundColor(Color.parseColor("#121212")) // Deep dark screen background
            
            // Force true dark card background and disable tint interference
            verseCard.setCardBackgroundColor(Color.parseColor("#1E1E1E"))
            verseCard.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#1E1E1E"))
            verseCard.cardElevation = 4f
            verseCard.strokeColor = Color.parseColor("#333333")
            
            tvVerseText.setTextColor(Color.parseColor("#FFFFFF")) // Bright white for crystal clear reading
            tvVerseRef.setTextColor(Color.parseColor("#D7CCC8"))   // Warm soft accent
            
            btnToggleTheme.text = "☀️ Switch to Day View"
            btnToggleTheme.setTextColor(Color.parseColor("#D7CCC8"))
            btnToggleTheme.strokeColor = ColorStateList.valueOf(Color.parseColor("#D7CCC8"))
            
            // Primary Accent Button
            btnRefresh.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#8D6E63"))
            btnRefresh.setTextColor(Color.parseColor("#FFFFFF"))
            
            // Secondary Buttons
            val nightButtons = listOf(btnCopy, btnShare, btnSpeak, btnSelectManual, btnPrev, btnNext, btnAutoPlay, btnAbout)
            for (btn in nightButtons) {
                btn.setTextColor(Color.parseColor("#D7CCC8"))
                btn.strokeColor = ColorStateList.valueOf(Color.parseColor("#D7CCC8"))
                btn.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#252525"))
            }

        } else {
            // --- DAY MODE (Warm Theme) ---
            rootLayout.setBackgroundResource(R.drawable.bg_gradient)
            
            verseCard.setCardBackgroundColor(Color.parseColor("#FFFFFF"))
            verseCard.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FFFFFF"))
            verseCard.cardElevation = 4f
            verseCard.strokeColor = Color.parseColor("#E8E2D5")
            
            tvVerseText.setTextColor(Color.parseColor("#2D241E"))
            tvVerseRef.setTextColor(Color.parseColor("#8C6D53"))
            
            btnToggleTheme.text = "🌙 Switch to Night View"
            btnToggleTheme.setTextColor(Color.parseColor("#6E472C"))
            btnToggleTheme.strokeColor = ColorStateList.valueOf(Color.parseColor("#6E472C"))
            
            btnRefresh.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#6E472C"))
            btnRefresh.setTextColor(Color.WHITE)
            
            val dayButtons = listOf(btnCopy, btnShare, btnSpeak, btnSelectManual, btnPrev, btnNext, btnAutoPlay, btnAbout)
            for (btn in dayButtons) {
                btn.setTextColor(Color.parseColor("#6E472C"))
                btn.strokeColor = ColorStateList.valueOf(Color.parseColor("#6E472C"))
                btn.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            }
        }
        
        // Refresh toggle button highlighting based on active translation & theme
        updateLanguageToggleUI(BibleRepository.currentTranslation)
    }

    // Dialog 1: Choose Book
    private fun showBookSelectionDialog() {
        val books = BibleRepository.getAvailableBooks(this).toTypedArray()
        
        if (books.isEmpty()) {
            val toast = Toast.makeText(this, "No books available", Toast.LENGTH_SHORT)
            toast.setGravity(android.view.Gravity.CENTER, 0, 0)
            toast.show()
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
        val verseTitles = chapterVerses.map { theVerse -> theVerse.reference }.toTypedArray()

        if (verseTitles.isEmpty()) {
            val toast = Toast.makeText(this, "No verses found for $chapterName", Toast.LENGTH_SHORT)
            toast.setGravity(android.view.Gravity.CENTER, 0, 0)
            toast.show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Select Verse in $chapterName")
            .setItems(verseTitles) { _, which ->
                val selectedVerse = chapterVerses[which]
                displayVerse(selectedVerse)
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
        tvVerseRef.startAnimation(fadeIn)
    }

    // Random verse picker (silent load)
    private fun loadVerse() {
        val verse = BibleRepository.getRandomVerse(this)
        displayVerse(verse)
    }

    // Text-to-Speech handler with Utterance ID for Auto-Play tracking
    private fun speakVerse(verse: BibleVerse) {
        val isTagalog = isTagalogText(verse.text)
        val locale = if (isTagalog) Locale("fil", "PH") else Locale.US

        val result = tts?.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts?.setLanguage(Locale.getDefault())
        }

        val speechText = "${verse.text}. Reference: ${verse.reference}"
        tts?.speak(speechText, TextToSpeech.QUEUE_FLUSH, null, "verse_utterance_id")
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

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}

                override fun onDone(utteranceId: String?) {
                    if (isAutoPlaying) {
                        runOnUiThread {
                            currentVerse?.let { verse ->
                                val nextVerse = BibleRepository.getNextVerse(verse, this@MainActivity)
                                if (nextVerse != null) {
                                    displayVerse(nextVerse)
                                    speakVerse(nextVerse)
                                } else {
                                    stopAutoPlay()
                                    val toast = Toast.makeText(this@MainActivity, "End of chapter reached", Toast.LENGTH_SHORT)
                                    toast.setGravity(android.view.Gravity.CENTER, 0, 0)
                                    toast.show()
                                }
                            }
                        }
                    }
                }

                override fun onError(utteranceId: String?) {
                    runOnUiThread {
                        stopAutoPlay()
                    }
                }
            })
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

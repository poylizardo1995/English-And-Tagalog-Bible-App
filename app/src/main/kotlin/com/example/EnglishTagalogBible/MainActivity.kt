package com.example.EnglishTagalogBible

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

        // Bind Language Toggle Pill Views
        val languageToggleInclude = findViewById<android.view.View>(R.id.languageTogglePill)
        btnEnglish = languageToggleInclude.findViewById(R.id.btnKjv)
        btnTagalog = languageToggleInclude.findViewById(R.id.btnTagalog)
        languageToggleContainer = languageToggleInclude.parent as? ViewGroup ?: (languageToggleInclude as ViewGroup)

        // Initialize Text-to-Speech engine
        tts = TextToSpeech(this, this)

        // Set up Language Pill Click Listeners
        btnEnglish.setOnClickListener {
            stopAutoPlay()
            handleLanguageSelection("English")
        }

        btnTagalog.setOnClickListener {
            stopAutoPlay()
            handleLanguageSelection("Tagalog")
        }

        // Restore state or load initial random verse
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

        // Button Click Listeners
        btnRefresh.setOnClickListener {
            stopAutoPlay()
            loadVerse()
        }

        btnSelectManual.setOnClickListener {
            stopAutoPlay()
            showBookSelectionDialog()
        }

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

        btnToggleTheme.setOnClickListener {
            isDarkMode = !isDarkMode
            applyTheme()
        }

        btnAbout.setOnClickListener {
            showAboutDialog()
        }

        LockScreenVerseService.showLockScreenNotification(this)

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

    // --- REUSABLE THEMED DIALOG HELPER ---
    private fun applyThemedDialogStyle(dialog: AlertDialog) {
        dialog.show()

        // Center-align title if present
        val titleView = dialog.findViewById<TextView>(androidx.appcompat.R.id.alertTitle)
        titleView?.apply {
            gravity = android.view.Gravity.CENTER
            textAlignment = android.view.View.TEXT_ALIGNMENT_CENTER
            setTextColor(if (isDarkMode) Color.parseColor("#FFFFFF") else Color.parseColor("#2D241E"))
        }

        // Center-align message if present
        val messageView = dialog.findViewById<TextView>(android.R.id.message)
        messageView?.apply {
            gravity = android.view.Gravity.CENTER
            textAlignment = android.view.View.TEXT_ALIGNMENT_CENTER
            setTextColor(if (isDarkMode) Color.parseColor("#E0E0E0") else Color.parseColor("#2D241E"))
        }

        // Style positive buttons (like OK)
        val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        positiveButton?.setTextColor(if (isDarkMode) Color.parseColor("#B08D73") else Color.parseColor("#6E472C"))

        // Recursively style all text views inside the dialog to guarantee readability in Dark/Day modes
        val rootDecorView = dialog.window?.decorView
        rootDecorView?.let { styleAllTextViews(it, isDarkMode) }

        // Apply Card Scroll background to dialog window
        dialog.window?.decorView?.background = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = 24f
            setColor(if (isDarkMode) Color.parseColor("#1E1E1E") else Color.parseColor("#FFFFFF"))
        }
    }

    private fun styleAllTextViews(view: android.view.View, darkMode: Boolean) {
        if (view is TextView) {
            view.setTextColor(if (darkMode) Color.parseColor("#E0E0E0") else Color.parseColor("#2D241E"))
        } else if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                styleAllTextViews(view.getChildAt(i), darkMode)
            }
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

            val dialog = AlertDialog.Builder(this)
                .setTitle("Select $groupName Version")
                .setSingleChoiceItems(options, if (currentIndex != -1) currentIndex else 0) { d, which ->
                    val selected = matchingTranslations[which]
                    if (selected != BibleRepository.currentTranslation) {
                        switchToTranslation(selected)
                    }
                    d.dismiss()
                }
                .create()
            applyThemedDialogStyle(dialog)
        }
    }

    private fun switchToTranslation(target: BibleTranslation) {
        val currentBookChapterAndVerse = currentVerse?.reference?.substringBefore(" (")
        val previousTranslation = BibleRepository.currentTranslation

        val assetFiles = assets.list("") ?: emptyArray()
        val targetSuffix = "_${target.suffix}.json"
        val hasSuffixFiles = assetFiles.any { it.endsWith(targetSuffix) }

        if (!hasSuffixFiles && target != BibleTranslation.KJV && target != BibleTranslation.ADB1905) {
            val toast = Toast.makeText(this, "This translation is not available yet.", Toast.LENGTH_SHORT)
            toast.setGravity(android.view.Gravity.CENTER, 0, 0)
            toast.show()
            updateLanguageToggleUI(previousTranslation)
            return
        }

        if (currentBookChapterAndVerse != null) {
            BibleRepository.loadVerses(this, target)
            val matchingVerse = BibleRepository.getVerseByBookChapterAndVerse(currentBookChapterAndVerse, this)

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

    private fun showAboutDialog() {
        val dialog = AlertDialog.Builder(this)
            .setMessage(
                "About & Credits\n\n" +
                "English and Tagalog Bible v1.2\n\n" +
                "App Developed by Poy Lizardo\n\n" +
                "Copyright © 2026. All Rights Reserved. To God be the glory!\n\n" +
                "Scripture Text Provided via Public Domain Translations: English - King James Version (KJV) and Tagalog - Ang Dating Biblia 1905 (ADB1905).\n\n" +    
                "Github: https://github.com/poylizardo1995/English-And-Tagalog-Bible-App"
            )
            .setPositiveButton("OK") { dialogInterface, _ -> dialogInterface.dismiss() }
            .create()

        applyThemedDialogStyle(dialog)

        val messageView = dialog.findViewById<TextView>(android.R.id.message)
        messageView?.textSize = 15f
    }

    private fun showBookSelectionDialog() {
        val rawBooks = BibleRepository.getAvailableBooks(this)
        
        // Filter out ONLY standalone numeric headers (like "1", "2", "3"), keeping full books like "1 Chronicles", "1 John", etc.
        val cleanedBooks = rawBooks.filter { book ->
            val trimmed = book.trim()
            !(trimmed.length <= 2 && trimmed.all { c -> c.isDigit() })
        }.toTypedArray()

        if (cleanedBooks.isEmpty()) {
            val toast = Toast.makeText(this, "No books available", Toast.LENGTH_SHORT)
            toast.setGravity(android.view.Gravity.CENTER, 0, 0)
            toast.show()
            return
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Select Book")
            .setItems(cleanedBooks) { _, which ->
                val selectedBook = cleanedBooks[which]
                showChapterSelectionDialog(selectedBook)
            }
            .create()

        applyThemedDialogStyle(dialog)
    }

    private fun showChapterSelectionDialog(bookName: String) {
        val chapters = BibleRepository.getChaptersForBook(bookName, this).toTypedArray()

        val dialog = AlertDialog.Builder(this)
            .setTitle("Select Chapter in $bookName")
            .setItems(chapters) { _, which ->
                val selectedChapter = chapters[which]
                showVerseSelectionDialog(selectedChapter)
            }
            .create()

        applyThemedDialogStyle(dialog)
    }

    private fun showVerseSelectionDialog(chapterName: String) {
        val chapterVerses = BibleRepository.getVerseObjectsForChapter(chapterName, this)
        val verseTitles = chapterVerses.map { theVerse -> theVerse.reference }.toTypedArray()

        if (verseTitles.isEmpty()) {
            val toast = Toast.makeText(this, "No verses found for $chapterName", Toast.LENGTH_SHORT)
            toast.setGravity(android.view.Gravity.CENTER, 0, 0)
            toast.show()
            return
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Select Verse in $chapterName")
            .setItems(verseTitles) { _, which ->
                val selectedVerse = chapterVerses[which]
                displayVerse(selectedVerse)
            }
            .create()

        applyThemedDialogStyle(dialog)
    }

    private fun displayVerse(verse: BibleVerse) {
        currentVerse = verse
        tvVerseText.text = verse.text
        tvVerseRef.text = "— ${verse.reference}"

        val fadeIn = AlphaAnimation(0.0f, 1.0f).apply { duration = 400 }
        tvVerseText.startAnimation(fadeIn)
        tvVerseRef.startAnimation(fadeIn)
    }

    private fun loadVerse() {
        val verse = BibleRepository.getRandomVerse(this)
        displayVerse(verse)
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
            rootLayout.setBackgroundResource(R.drawable.bg_gradient)
            
            verseCard.backgroundTintList = null
            verseCard.setCardBackgroundColor(ColorStateList.valueOf(Color.parseColor("#1E1E1E")))
            verseCard.strokeColor = Color.parseColor("#333333")
            verseCard.cardElevation = 0f
            
            tvVerseText.setTextColor(Color.parseColor("#FFFFFF"))
            tvVerseRef.setTextColor(Color.parseColor("#B08D73"))
            
            btnToggleTheme.text = "☀️ Switch to Day View"
            btnToggleTheme.setTextColor(Color.parseColor("#E0E0E0"))
            btnToggleTheme.strokeColor = ColorStateList.valueOf(Color.parseColor("#D7CCC8"))
            btnToggleTheme.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            
            btnRefresh.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#8D6E63"))
            btnRefresh.setTextColor(Color.parseColor("#FFFFFF"))
            if (btnRefresh is MaterialButton) {
                (btnRefresh as MaterialButton).strokeColor = ColorStateList.valueOf(Color.parseColor("#8D6E63"))
            }
            
            val nightButtons = listOf(btnCopy, btnShare, btnSpeak, btnSelectManual, btnPrev, btnNext, btnAutoPlay, btnAbout)
            for (btn in nightButtons) {
                btn.setTextColor(Color.parseColor("#E0E0E0"))
                btn.strokeColor = ColorStateList.valueOf(Color.parseColor("#D7CCC8"))
                btn.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            }

        } else {
            rootLayout.setBackgroundResource(R.drawable.bg_gradient)
            
            verseCard.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FFFFFF"))
            verseCard.setCardBackgroundColor(ColorStateList.valueOf(Color.parseColor("#FFFFFF")))
            verseCard.strokeColor = Color.parseColor("#E8E2D5")
            verseCard.cardElevation = 4f
            
            tvVerseText.setTextColor(Color.parseColor("#2D241E"))
            tvVerseRef.setTextColor(Color.parseColor("#8C6D53"))
            
            btnToggleTheme.text = "🌙 Switch to Night View"
            btnToggleTheme.setTextColor(Color.parseColor("#6E472C"))
            btnToggleTheme.strokeColor = ColorStateList.valueOf(Color.parseColor("#6E472C"))
            btnToggleTheme.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            
            btnRefresh.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#6E472C"))
            btnRefresh.setTextColor(Color.WHITE)
            if (btnRefresh is MaterialButton) {
                (btnRefresh as MaterialButton).strokeColor = ColorStateList.valueOf(Color.parseColor("#6E472C"))
            }
            
            val dayButtons = listOf(btnCopy, btnShare, btnSpeak, btnSelectManual, btnPrev, btnNext, btnAutoPlay, btnAbout)
            for (btn in dayButtons) {
                btn.setTextColor(Color.parseColor("#6E472C"))
                btn.strokeColor = ColorStateList.valueOf(Color.parseColor("#6E472C"))
                btn.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            }
        }
        
        updateLanguageToggleUI(BibleRepository.currentTranslation)
    }

    private fun updateLanguageToggleUI(translation: BibleTranslation) {
        val isEnglishActive = translation.languageGroup == "English"
        
        btnEnglish.text = if (isEnglishActive) "${translation.displayName.substringAfter("(").substringBefore(")")} ▾" else "ENGLISH ▾"
        btnTagalog.text = if (!isEnglishActive) "${translation.displayName.substringAfter("(").substringBefore(")")} ▾" else "TAGALOG ▾"

        if (isDarkMode) {
            verseCard.backgroundTintList = null
            verseCard.setCardBackgroundColor(ColorStateList.valueOf(Color.parseColor("#1E1E1E")))
            verseCard.strokeColor = Color.parseColor("#333333")

            if (isEnglishActive) {
                btnEnglish.setBackgroundResource(R.drawable.bg_pill_selected)
                btnEnglish.setTextColor(Color.parseColor("#121212"))
                btnTagalog.backgroundTintList = null
                btnTagalog.setBackgroundColor(Color.TRANSPARENT)
                btnTagalog.setTextColor(Color.parseColor("#D7CCC8"))
            } else {
                btnTagalog.setBackgroundResource(R.drawable.bg_pill_selected)
                btnTagalog.setTextColor(Color.parseColor("#121212"))
                btnEnglish.backgroundTintList = null
                btnEnglish.setBackgroundColor(Color.TRANSPARENT)
                btnEnglish.setTextColor(Color.parseColor("#D7CCC8"))
            }
        } else {
            verseCard.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FFFFFF"))
            verseCard.setCardBackgroundColor(ColorStateList.valueOf(Color.parseColor("#FFFFFF")))
            verseCard.strokeColor = Color.parseColor("#E8E2D5")

            if (isEnglishActive) {
                btnEnglish.setBackgroundResource(R.drawable.bg_pill_selected)
                btnEnglish.setTextColor(Color.WHITE)
                btnTagalog.backgroundTintList = null
                btnTagalog.setBackgroundColor(Color.TRANSPARENT)
                btnTagalog.setTextColor(Color.parseColor("#555555"))
            } else {
                btnTagalog.setBackgroundResource(R.drawable.bg_pill_selected)
                btnTagalog.setTextColor(Color.WHITE)
                btnEnglish.backgroundTintList = null
                btnEnglish.setBackgroundColor(Color.TRANSPARENT)
                btnEnglish.setTextColor(Color.parseColor("#555555"))
            }
        }
    }

    private fun speakVerse(verse: BibleVerse) {
        if (!isTtsReady || tts == null) {
            val toast = Toast.makeText(this, "TTS engine is not ready or installed on this device.", Toast.LENGTH_SHORT)
            toast.setGravity(android.view.Gravity.CENTER, 0, 0)
            toast.show()
            
            val installIntent = Intent().apply {
                action = TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA
            }
            try {
                startActivity(installIntent)
            } catch (e: Exception) {
                // Catch if intent is unavailable
            }
            return
        }

        val isTagalog = isTagalogText(verse.text)
        val locale = if (isTagalog) Locale("fil", "PH") else Locale.US

        val result = tts?.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            val fallbackToast = Toast.makeText(this, "Language data missing for speech. Defaulting to system language.", Toast.LENGTH_SHORT)
            fallbackToast.setGravity(android.view.Gravity.CENTER, 0, 0)
            fallbackToast.show()
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
            val result = tts?.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                isTtsReady = false
                val toast = Toast.makeText(this, "TTS Language data missing. Please check device Text-to-Speech settings.", Toast.LENGTH_SHORT)
                toast.setGravity(android.view.Gravity.CENTER, 0, 0)
                toast.show()
            } else {
                isTtsReady = true
            }

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
        } else {
            isTtsReady = false
            val toast = Toast.makeText(this, "Failed to initialize Text-to-Speech engine.", Toast.LENGTH_SHORT)
            toast.setGravity(android.view.Gravity.CENTER, 0, 0)
            toast.show()
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

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
            val fullText = "$verseText$verseRef"

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
                btnTagalog.setBackgroundResource(R.drawable.bg_pill_

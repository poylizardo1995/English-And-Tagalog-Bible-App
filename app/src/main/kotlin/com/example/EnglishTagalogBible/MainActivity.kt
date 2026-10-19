package com.example.EnglishTagalogBible

import android.provider.Settings
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.content.res.ColorStateList
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.text.style.ForegroundColorSpan
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var tvVerseText: TextView
    private lateinit var tvVerseRef: TextView
    private lateinit var btnRefresh: Button
    private lateinit var btnSearch: MaterialButton
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
    
    private lateinit var btnEnglish: TextView
    private lateinit var btnTagalog: TextView
  
    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var currentVerse: BibleVerse? = null
    private var isAutoPlaying = false

    private enum class ThemeMode(val displayName: String) {
        SEPIA("🟤 Classic Sepia"),
        CHARCOAL("⚫ Charcoal Contrast"),
        NAVY("🔵 Midnight Navy"),
        OLIVE("🟢 Olive Grace"),
        SUNSET("🟠 Warm Sunset")
    }
    private var currentTheme = ThemeMode.SEPIA

    private enum class AutoPlayMode {
        CHAPTER, BOOK, ALL_BOOKS
    }
    private var autoPlayMode = AutoPlayMode.CHAPTER
    private var currentBookName: String? = null
    private var currentChapterName: String? = null

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
        btnSearch = findViewById(R.id.btnSearch)
        btnCopy = findViewById(R.id.btnCopy)
        btnShare = findViewById(R.id.btnShare)
        btnSpeak = findViewById(R.id.btnSpeak)
        btnSelectManual = findViewById(R.id.btnSelectManual)
        btnToggleTheme = findViewById(R.id.btnToggleTheme)
        
        btnPrev = findViewById(R.id.btnPrev)
        btnNext = findViewById(R.id.btnNext)
        btnAutoPlay = findViewById(R.id.btnAutoPlay)
        btnAbout = findViewById(R.id.btnAbout)
        verseCard = findViewById(R.id.verseCard)

        val languageToggleInclude = findViewById<android.view.View>(R.id.languageTogglePill)
        btnEnglish = languageToggleInclude.findViewById(R.id.btnKjv)
        btnTagalog = languageToggleInclude.findViewById(R.id.btnTagalog)

        tts = TextToSpeech(this, this)

        btnEnglish.setOnClickListener {
            stopAutoPlay()
            handleLanguageSelection("English")
        }

        btnTagalog.setOnClickListener {
            stopAutoPlay()
            handleLanguageSelection("Tagalog")
        }

        val incomingText = intent.getStringExtra("verse_text")
        val incomingRef = intent.getStringExtra("verse_ref")

        if (incomingText != null && incomingRef != null) {
            currentVerse = BibleVerse(text = incomingText, reference = incomingRef)
            tvVerseText.text = currentVerse?.text
            tvVerseRef.text = "— ${currentVerse?.reference}"
            } else if (savedInstanceState != null) {
            val savedText = savedInstanceState.getString(KEY_VERSE_TEXT)
            val savedRef = savedInstanceState.getString(KEY_VERSE_REF)
            if (savedText != null && savedRef != null) {
                val cleanRef = savedRef.removePrefix("— ").trim()
                currentVerse = BibleVerse(text = savedText, reference = cleanRef)
                tvVerseText.text = currentVerse?.text
                tvVerseRef.text = "— ${currentVerse?.reference}"
            } else {
                loadInitialVerse()
            }
            } else {
            loadInitialVerse()
        }

        btnRefresh.setOnClickListener { stopAutoPlay(); loadRandomVerse() }
        btnSearch.setOnClickListener { stopAutoPlay(); showSearchQueryDialog() }
        btnSelectManual.setOnClickListener { stopAutoPlay(); showBookSelectionDialog() }

        btnPrev.setOnClickListener {
            currentVerse?.let { verse ->
                val prevVerse = BibleRepository.getPreviousVerse(verse, this)
                if (prevVerse != null) {
                    displayVerse(prevVerse)
                    if (isAutoPlaying) speakVerse(prevVerse)
                } else {
                    showCenteredToast("Beginning of chapter reached.")
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
                    showCenteredToast("End of chapter reached.")
                    stopAutoPlay()
                }
            }
        }

        btnAutoPlay.setOnClickListener {
            if (isAutoPlaying) {
                stopAutoPlay()                
                showCenteredToast("Auto-Play Stopped.")
            } else {
                showAutoPlayOptionsDialog()
            }
        }

        btnCopy.setOnClickListener {
            val verseText = tvVerseText.text.toString()
            val verseRef = tvVerseRef.text.toString()
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Bible Verse", "$verseText $verseRef")
            clipboard.setPrimaryClip(clip)
            showCenteredToast("Verse copied to clipboard!")
        }

        btnShare.setOnClickListener { showShareOptionsDialog() }
        btnSpeak.setOnClickListener {
            currentVerse?.let { verse ->
                if (isTtsReady) {
                    stopAutoPlay()
                    speakVerse(verse)
                } else {
                    showCenteredToast("Speech Engine is still initializing...")
                }
            }
        }

        btnToggleTheme.setOnClickListener { showThemeSelectionDialog() }
        btnAbout.setOnClickListener { showAboutDialog() }

        LockScreenVerseService.showLockScreenNotification(this)
        scheduleDailyVerseNotification()

        applyTheme()
        updateLanguageToggleUI(BibleRepository.currentTranslation)
    }

    private fun scheduleDailyVerseNotification() {
        val dailyWorkRequest = PeriodicWorkRequestBuilder<DailyVerseWorker>(24, TimeUnit.HOURS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("DailyVerseWork", ExistingPeriodicWorkPolicy.KEEP, dailyWorkRequest)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        currentVerse?.let {
            outState.putString(KEY_VERSE_TEXT, it.text)
            outState.putString(KEY_VERSE_REF, it.reference)
        }
    }

    private fun initTTS() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                configureTTSVoiceForCurrentLanguage()
            }
        }
    }

    private fun configureTTSVoiceForCurrentLanguage() {
        val ttsEngine = tts ?: return
        val currentLanguage = BibleRepository.currentTranslation.languageGroup

        if (currentLanguage == "Tagalog") {
            val localeTagalog = Locale("tl", "PH")
            ttsEngine.language = localeTagalog
            
            // Search for preferred Tagalog voice (Voice IV / 4th network voice or matching tagalog voice)
            val tagalogVoice = ttsEngine.voices?.find { voice ->
                voice.locale.language == "tl" && 
                (voice.name.contains("tag", ignoreCase = true) || voice.name.contains("iv", ignoreCase = true) || voice.name.contains("4"))
            } ?: ttsEngine.voices?.find { it.locale.language == "tl" }

            tagalogVoice?.let { ttsEngine.voice = it }
        } else {
            val localeEnglish = Locale.US
            ttsEngine.language = localeEnglish

            // Search for preferred English voice (Voice V / 5th network voice or sf-group)
            val englishVoice = ttsEngine.voices?.find { voice ->
                voice.locale.language == "en" && 
                (voice.name.contains("sfg", ignoreCase = true) || voice.name.contains("v", ignoreCase = true) || voice.name.contains("5"))
            } ?: ttsEngine.voices?.find { it.locale.language == "en" }

            englishVoice?.let { ttsEngine.voice = it }
        }
    }

    private fun openSystemTTSSettings() {
        var opened = false
        val intentsToTry = listOf(
            // 1. Explicit Intent for Google TTS Engine Settings package if installed
            Intent("com.google.android.tts.settings.VoiceDataActivity").apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            // 2. Standard Android TTS Settings action
            Intent("com.android.settings.TTS_SETTINGS").apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            // 3. Android System Accessibility / Speech Settings fallback
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        )

        for (intent in intentsToTry) {
            try {
                startActivity(intent)
                opened = true
                break
            } catch (_: Exception) {
                // Try next intent fallback
            }
        }

        if (!opened) {
            showCenteredToast("Unable to open Text-to-Speech settings.")
        }
    }

    private fun showAudioOptionsDialog() {
        val options = arrayOf(
            "🔊 Listen to Current Verse",
            "⚙️ Text-to-Speech Settings (Google TTS)"
        )

        val dialog = AlertDialog.Builder(this)
            .setTitle("Audio & Speech Options")
            .setAdapter(createCenteredListAdapter(options)) { _, index ->
                when (index) {
                    0 -> {
                        currentVerse?.let { verse ->
                            speakVerse(verse)
                        }
                    }
                    1 -> openSystemTTSSettings()
                }
            }
            .create()

        applyThemedDialogStyle(dialog)
    }

    private fun showThemeSelectionDialog() {
        val themes = ThemeMode.values()
        val options = themes.map { it.displayName }.toTypedArray()
        val dialog = AlertDialog.Builder(this)
            .setTitle("Select Reading Theme")
            .setAdapter(createCenteredListAdapter(options)) { _, which ->
                currentTheme = themes[which]
                applyTheme()
            }
            .create()
        applyThemedDialogStyle(dialog)
    }

    private fun applyThemedDialogStyle(dialog: AlertDialog) {
        dialog.show()
        val isDark = currentTheme != ThemeMode.SEPIA
        dialog.findViewById<TextView>(androidx.appcompat.R.id.alertTitle)?.apply {
            gravity = android.view.Gravity.CENTER
            textAlignment = android.view.View.TEXT_ALIGNMENT_CENTER
            setTextColor(if (isDark) Color.parseColor("#FFFFFF") else Color.parseColor("#2D241E"))
        }

        val accentColor = when (currentTheme) {
            ThemeMode.SEPIA -> Color.parseColor("#6E472C")
            ThemeMode.CHARCOAL -> Color.parseColor("#FFB74D")
            ThemeMode.NAVY -> Color.parseColor("#7986CB")
            ThemeMode.OLIVE -> Color.parseColor("#81C784")
            ThemeMode.SUNSET -> Color.parseColor("#FFB74D")
        }

        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(accentColor)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(accentColor)

        val dialogBgColor = when (currentTheme) {
            ThemeMode.SEPIA -> Color.parseColor("#FFFFFF")
            ThemeMode.CHARCOAL -> Color.parseColor("#1E1E1E")
            ThemeMode.NAVY -> Color.parseColor("#121826")
            ThemeMode.OLIVE -> Color.parseColor("#1A211C")
            ThemeMode.SUNSET -> Color.parseColor("#221A15")
        }

        val strokeColor = when (currentTheme) {
            ThemeMode.SEPIA -> Color.parseColor("#E8E2D5")
            ThemeMode.CHARCOAL -> Color.parseColor("#444444")
            ThemeMode.NAVY -> Color.parseColor("#283593")
            ThemeMode.OLIVE -> Color.parseColor("#2E7D32")
            ThemeMode.SUNSET -> Color.parseColor("#D77A61")
        }

        dialog.window?.decorView?.background = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = 24f
            setColor(dialogBgColor)
            setStroke(2, strokeColor)
        }
    }

    private fun createCenteredListAdapter(items: Array<String>): android.widget.ArrayAdapter<String> {
        val isDark = currentTheme != ThemeMode.SEPIA
        val textColor = if (isDark) Color.parseColor("#FFFFFF") else Color.parseColor("#2D241E")
        val accentColor = when (currentTheme) {
            ThemeMode.SEPIA -> Color.parseColor("#6E472C")
            ThemeMode.CHARCOAL -> Color.parseColor("#FFB74D")
            ThemeMode.NAVY -> Color.parseColor("#9FA8DA")
            ThemeMode.OLIVE -> Color.parseColor("#A5D6A7")
            ThemeMode.SUNSET -> Color.parseColor("#FFB74D")
        }

        return object : android.widget.ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, items) {
            override fun getView(position: Int, convertView: android.view.View?, parent: ViewGroup): android.view.View {
                val view = super.getView(position, convertView, parent)
                val text = items[position]
                (view as? TextView)?.apply {
                    this.gravity = android.view.Gravity.CENTER
                    this.textAlignment = android.view.View.TEXT_ALIGNMENT_CENTER
                    this.setPadding(0, 28, 0, 28)
                    
                    // Comprehensive check for headers, categories, status badges, and action buttons
                    if (text.startsWith("—") || 
                        text.contains("📜") || text.contains("✝️") || text.contains("📖") || text.contains("🗑️") ||
                        text.contains("✅") || text.contains("⬇️") || text.contains("🌐") || text.contains("🖼️") || 
                        text.contains("📝") || text.contains("🖼️") || text.contains("🟤") || text.contains("⚫") || 
                        text.contains("🔵") || text.contains("🟢") || text.contains("🟠") || text.contains("▶ Auto-Play ") || 
                        text.contains("🔊") || text.contains("⚙️") || 
                        text.contains("• Active")) {
 
                        this.setTextColor(accentColor)
                        this.setTypeface(null, android.graphics.Typeface.BOLD)
                    } else {
                        this.setTextColor(textColor)
                        this.setTypeface(null, android.graphics.Typeface.NORMAL)
                    }
                    layoutParams = android.widget.AbsListView.LayoutParams(android.widget.AbsListView.LayoutParams.MATCH_PARENT, android.widget.AbsListView.LayoutParams.WRAP_CONTENT)
                }
                return view
            }
        }
    }

    private fun showShareOptionsDialog() {
        val options = arrayOf("📝 Share as Text", "🖼️ Share as Image Card")
        val dialog = AlertDialog.Builder(this)
            .setTitle("Share Verse")
            .setAdapter(createCenteredListAdapter(options)) { _, which ->
                if (which == 0) {
                    val shareBody = "${tvVerseText.text}\n\n${tvVerseRef.text}"
                    val shareIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, shareBody)
                    }
                    startActivity(Intent.createChooser(shareIntent, "Share Verse via"))
                } else {
                    shareVerseAsImage(tvVerseText.text.toString(), tvVerseRef.text.toString())
                }
            }
            .create()
        applyThemedDialogStyle(dialog)
    }

    private fun shareVerseAsImage(verseText: String, verseRef: String) {
        try {
            val width = 1080
            val height = 1080
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            val colors = when (currentTheme) {
                ThemeMode.SEPIA -> intArrayOf(Color.parseColor("#2D241E"), Color.parseColor("#4A3B32"), Color.parseColor("#1E1A17"))
                ThemeMode.CHARCOAL -> intArrayOf(Color.parseColor("#121212"), Color.parseColor("#1E1E1E"), Color.parseColor("#0A0A0A"))
                ThemeMode.NAVY -> intArrayOf(Color.parseColor("#0F172A"), Color.parseColor("#1E293B"), Color.parseColor("#090D16"))
                ThemeMode.OLIVE -> intArrayOf(Color.parseColor("#132219"), Color.parseColor("#1B3324"), Color.parseColor("#0A140E"))
                ThemeMode.SUNSET -> intArrayOf(Color.parseColor("#2D1A12"), Color.parseColor("#42251B"), Color.parseColor("#1B0F0B"))
            }

            val gradient = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), colors, null, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), Paint().apply { shader = gradient })

            val borderColor = when (currentTheme) {
                ThemeMode.SEPIA -> Color.parseColor("#B08D73")
                ThemeMode.CHARCOAL -> Color.parseColor("#FFB74D")
                ThemeMode.NAVY -> Color.parseColor("#7986CB")
                ThemeMode.OLIVE -> Color.parseColor("#81C784")
                ThemeMode.SUNSET -> Color.parseColor("#FFB74D")
            }

            canvas.drawRoundRect(60f, 60f, (width - 60).toFloat(), (height - 60).toFloat(), 32f, 32f, Paint().apply { color = borderColor; style = Paint.Style.STROKE; strokeWidth = 6f })

            val textPaint = Paint().apply { color = Color.parseColor("#FFFFFF"); textSize = 54f; typeface = Typeface.create("serif", Typeface.ITALIC); isAntiAlias = true; textAlign = Paint.Align.CENTER }
            val refPaint = Paint().apply { color = Color.parseColor("#E0E0E0"); textSize = 42f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true; textAlign = Paint.Align.CENTER }

            val cachePath = File(cacheDir, "images").apply { mkdirs() }
            val file = File(cachePath, "shared_verse.png")
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

            val imageUri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, imageUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Share Verse Card via"))
        } catch (e: Exception) {
            showCenteredToast("Failed to generate verse card image.")
        }
    }

    private fun showAutoPlayOptionsDialog() {
        val options = arrayOf("▶ Auto-Play Chapter", "▶ Auto-Play Book", "▶ Auto-Play All Books")
        val dialog = AlertDialog.Builder(this)
            .setTitle("Select Auto-Play Mode")
            .setAdapter(createCenteredListAdapter(options)) { _, which ->
                autoPlayMode = when (which) { 0 -> AutoPlayMode.CHAPTER; 1 -> AutoPlayMode.BOOK; else -> AutoPlayMode.ALL_BOOKS }
                startAutoPlay()
            }
            .create()
        applyThemedDialogStyle(dialog)
    }

    private fun showSearchQueryDialog() {
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 16, 48, 0)
        }

        // Filter available translations strictly matching the currently active language group
        val currentGroup = BibleRepository.currentTranslation.languageGroup
        val availableTranslations = BibleTranslation.values()
            .filter { it.languageGroup == currentGroup && it.isAvailable(this) }
            
        val translationNames = availableTranslations.map { it.displayName }.toTypedArray()
        
        var selectedTranslationIndex = availableTranslations.indexOf(BibleRepository.currentTranslation)
        if (selectedTranslationIndex < 0) selectedTranslationIndex = 0

        val isDark = currentTheme != ThemeMode.SEPIA
        val spinnerLabel = TextView(this).apply {
            text = "Select Version ($currentGroup):"
            setTextColor(if (isDark) Color.parseColor("#E0E0E0") else Color.parseColor("#2D241E"))
            textSize = 13f
            setPadding(0, 0, 0, 4)
        }

        val versionSelectButton = Button(this).apply {
            text = "${availableTranslations.getOrNull(selectedTranslationIndex)?.displayName ?: "Select"} ▾"
            isAllCaps = false
            textSize = 14f
            gravity = android.view.Gravity.CENTER
            setBackgroundColor(if (isDark) Color.parseColor("#222222") else Color.parseColor("#F5F0EB"))
            setTextColor(if (isDark) Color.parseColor("#FFFFFF") else Color.parseColor("#2D241E"))
            setPadding(16, 16, 16, 16)
            
            setOnClickListener {
                val versionDialog = AlertDialog.Builder(this@MainActivity)
                    .setTitle("Select $currentGroup Version")
                    .setAdapter(createCenteredListAdapter(translationNames)) { _, which ->
                        selectedTranslationIndex = which
                        text = "${availableTranslations[which].displayName} ▾"
                    }
                    .create()
                applyThemedDialogStyle(versionDialog)
            }
        }

        val inputEditText = android.widget.EditText(this).apply {
            hint = "Enter keyword or reference"
            textSize = 15f
            setTextColor(if (isDark) Color.parseColor("#FFFFFF") else Color.parseColor("#2D241E"))
            setHintTextColor(if (isDark) Color.parseColor("#888888") else Color.parseColor("#888888"))
            setPadding(24, 28, 24, 28)
        }

        layout.addView(spinnerLabel)
        layout.addView(versionSelectButton)
        layout.addView(inputEditText)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Search Bible")
            .setView(layout)
            .setPositiveButton("Search") { _, _ ->
                val query = inputEditText.text.toString().trim()
                if (query.isNotEmpty() && availableTranslations.isNotEmpty()) {
                    performVerseSearch(query, availableTranslations[selectedTranslationIndex])
                } else {
                    showCenteredToast("Please enter a search term.")
                }
            }
            .setNegativeButton("Cancel", null)
            .create()

        applyThemedDialogStyle(dialog)
    }

    private fun performVerseSearch(query: String, targetTranslation: BibleTranslation) {
        BibleRepository.loadVerses(this, targetTranslation)
        val matchingVerses = mutableListOf<BibleVerse>()
        val lowercaseQuery = query.lowercase()

        val allBooks = BibleRepository.getAvailableBooks(this)
        for (book in allBooks) {
            val chapters = BibleRepository.getChaptersForBook(book, this)
            for (chapter in chapters) {
                val verses = BibleRepository.getVerseObjectsForChapter(chapter, this)
                for (verse in verses) {
                    if (verse.text.lowercase().contains(lowercaseQuery) || verse.reference.lowercase().contains(lowercaseQuery)) {
                        matchingVerses.add(verse)
                        if (matchingVerses.size >= 100) break
                    }
                }
                if (matchingVerses.size >= 100) break
            }
            if (matchingVerses.size >= 100) break
        }

        if (matchingVerses.isEmpty()) {
            showCenteredToast("No matching verses found for \"$query\".")
        } else {
            showSearchResultsDialog(query, matchingVerses, targetTranslation)
        }
    }

    private fun showSearchResultsDialog(query: String, results: List<BibleVerse>, targetTranslation: BibleTranslation) {
        val isDark = currentTheme != ThemeMode.SEPIA
        val textColor = if (isDark) Color.parseColor("#FFFFFF") else Color.parseColor("#2D241E")
        val accentColor = when (currentTheme) {
            ThemeMode.SEPIA -> Color.parseColor("#8C6D53")
            ThemeMode.CHARCOAL -> Color.parseColor("#FFB74D")
            ThemeMode.NAVY -> Color.parseColor("#9FA8DA")
            ThemeMode.OLIVE -> Color.parseColor("#A5D6A7")
            ThemeMode.SUNSET -> Color.parseColor("#FFB74D")
        }

        val adapter = object : android.widget.ArrayAdapter<BibleVerse>(this, android.R.layout.simple_list_item_1, results) {
            override fun getView(position: Int, convertView: android.view.View?, parent: ViewGroup): android.view.View {
                val view = super.getView(position, convertView, parent)
                val verse = getItem(position)
                (view as? TextView)?.apply {
                    if (verse != null) {
                        val fullText = "${verse.reference}: ${verse.text}"
                        val spannableString = SpannableString(fullText)
                        val lowercaseFull = fullText.lowercase()
                        val startIndex = lowercaseFull.indexOf(query.lowercase())
                        if (startIndex != -1) {
                            val endIndex = startIndex + query.length
                            spannableString.setSpan(StyleSpan(Typeface.BOLD), startIndex, endIndex, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                            spannableString.setSpan(ForegroundColorSpan(accentColor), startIndex, endIndex, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        }
                        text = spannableString
                    }
                    this.setTextColor(textColor)
                    this.gravity = android.view.Gravity.CENTER
                    this.textAlignment = android.view.View.TEXT_ALIGNMENT_CENTER
                    this.setPadding(0, 28, 0, 28)
                }
                return view
            }
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Results (${results.size}) - ${targetTranslation.displayName}")
            .setAdapter(adapter) { _, which ->
                BibleRepository.setTranslation(this, targetTranslation)
                updateLanguageToggleUI(targetTranslation)
                displayVerse(results[which])
            }
            .create()
        applyThemedDialogStyle(dialog)
    }

    private fun showRemoveTranslationsDialog(groupName: String) {
        val removableList = BibleTranslation.values()
            .filter { it.languageGroup == groupName && !it.isBuiltIn && it.isAvailable(this) }

        if (removableList.isEmpty()) {
            showCenteredToast("No removable translations found.")
            return
        }

        val options = removableList.map { "🗑️ Delete ${it.displayName}" }.toTypedArray()

        val dialog = AlertDialog.Builder(this)
            .setTitle("Remove Translation")
            .setAdapter(createCenteredListAdapter(options)) { _, index ->
                val toDelete = removableList[index]
                BibleManager.removeTranslationPackage(this, toDelete,
                    onSuccess = {
                        runOnUiThread {
                            showCenteredToast("${toDelete.displayName} removed.")
                            if (BibleRepository.currentTranslation == toDelete) {
                                val fallback = if (groupName == "Tagalog") BibleTranslation.ADB else BibleTranslation.KJV
                                switchToTranslation(fallback)
                            }
                        }
                    },
                    onError = { err -> runOnUiThread { showCenteredToast(err) } }
                )
            }
            .create()
        applyThemedDialogStyle(dialog)
    }

    private fun handleLanguageSelection(groupName: String) {
        val installedTranslations = BibleTranslation.values()
            .filter { it.languageGroup == groupName && it.isAvailable(this) }
            .sortedWith(Comparator { a, b ->
                when {
                    a.isBuiltIn -> -1
                    b.isBuiltIn -> 1
                    else -> a.displayName.compareTo(b.displayName)
                }
            })

        val listItems = mutableListOf<String>()
        
        // 1. Show Installed Versions with Status Badges
        installedTranslations.forEach {
            val badge = if (it.isBuiltIn) "✅ (Built-in)" else "✅ (Installed)"
            val activeMark = if (it == BibleRepository.currentTranslation) " • Active" else ""
            listItems.add("${it.displayName} $badge$activeMark")
        }

        // 2. Action buttons at the bottom
        listItems.add("🌐 Download Other Translations")
        
        val downloadableNonBuiltInCount = BibleTranslation.values()
            .filter { it.languageGroup == groupName && !it.isBuiltIn && it.isAvailable(this) }.size
            
        if (downloadableNonBuiltInCount > 0) {
            listItems.add("🗑️ Remove Installed Translation")
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Select $groupName Version")
            .setAdapter(createCenteredListAdapter(listItems.toTypedArray())) { _, index ->
                if (index < installedTranslations.size) {
                    // Direct switch without extra dialogs
                    val selected = installedTranslations[index]
                    if (selected != BibleRepository.currentTranslation) {
                        switchToTranslation(selected)
                    }
                } else {
                    val actionIndex = index - installedTranslations.size
                    if (actionIndex == 0) {
                        showDownloadableTranslationsDialog(groupName)
                    } else {
                        showRemoveTranslationsDialog(groupName)
                    }
                }
            }
            .create()
        applyThemedDialogStyle(dialog)
    }

    private fun showDownloadableTranslationsDialog(groupName: String) {
        val downloadableList = BibleTranslation.values()
            .filter { it.languageGroup == groupName && !it.isAvailable(this) }

        if (downloadableList.isEmpty()) {
            showCenteredToast("All available $groupName translations are already installed!")
            return
        }

        val options = downloadableList.map { "${it.displayName} ⬇️ (Download)" }.toTypedArray()

        val dialog = AlertDialog.Builder(this)
            .setTitle("Download $groupName Translations")
            .setAdapter(createCenteredListAdapter(options)) { _, index ->
                val selected = downloadableList[index]
                if (!BibleManager.isNetworkAvailable(this)) {
                    showCenteredToast("No internet connection available.")
                    return@setAdapter
                }
                showCenteredToast("Downloading ${selected.displayName} from GitHub...")
                BibleManager.downloadTranslationPackage(
                    context = this,
                    translation = selected,
                    onProgress = {},
                    onSuccess = { 
                        runOnUiThread { 
                            showCenteredToast("${selected.displayName} installed!") 
                            switchToTranslation(selected) 
                        } 
                    },
                    onError = { err -> runOnUiThread { showCenteredToast(err) } }
                )
            }
            .create()
        applyThemedDialogStyle(dialog)
    }

   private fun switchToTranslation(target: BibleTranslation) {
        val currentRef = currentVerse?.reference?.substringBefore(" (")?.trim()
        
        // Save the previous active translation and verse before attempting to switch
        val previousTranslation = BibleRepository.currentTranslation
        val previousVerse = currentVerse

        BibleRepository.setTranslation(this, target)

        if (currentRef != null) {
            val translatedVerse = BibleRepository.getVerseByBookChapterAndVerse(currentRef, this)
            if (translatedVerse != null) {
                // Verse exists in target translation -> switch successfully
                updateLanguageToggleUI(target)
                displayVerse(translatedVerse)
                
                // Re-configure TTS voice for the newly selected translation language
                configureTTSVoiceForCurrentLanguage()
                
                if (target == BibleTranslation.JPS || target == BibleTranslation.WNT) {
                    showCenteredToast("${target.displayName}: ${target.fullTranslationName}")
                }
            } else {
                // Verse does NOT exist in target translation (e.g. NT verse selected on OT-only JPS)
                // Revert repository and UI back to the previous translation
                BibleRepository.setTranslation(this, previousTranslation)
                updateLanguageToggleUI(previousTranslation)
                
                if (previousVerse != null) {
                    currentVerse = previousVerse
                    displayVerse(previousVerse)
                }
                
                // Ensure TTS voice remains matching the reverted translation
                configureTTSVoiceForCurrentLanguage()
                
                showCenteredToast("${target.displayName} (${target.fullTranslationName}) does not contain this verse.")
            }
        } else {
            updateLanguageToggleUI(target)
            configureTTSVoiceForCurrentLanguage()
            loadInitialVerse()
        }
    }

    private fun showAboutDialog() {
        val dialog = AlertDialog.Builder(this)
            .setMessage(
                "About & Credits\n\n" +
                "English and Tagalog Bible v1.4\n\n" +
                "App Developed by Poy Lizardo\n\n" +
                "Copyright © 2026. All Rights Reserved. To God be the glory!\n\n" +
                "Built-in Scripture Text (Public Domain Translations):\n" +
                "● English - King James Bible: Pure Cambridge Edition (1900) (KJV)\n" +
                "● Tagalog - Ang Dating Biblia (1905) (ADB).\n\n" +
                "Other Scripture Text (Public Domain Translations) are available for Download on APP\n" +
                "● English - American King James Version (2020) (AKJV)\n" +
                "● English - American Standard Version (1901) (ASV)\n" +
                "● English - Berean Annotated Bible (Early Draft Version) (BAB)\n" +
                "● English - Berean Literal Bible (Draft Version) (BLB)\n" +
                "● English - Berean Standard Bible (2020) (BSB)\n" +
                "● English - Berean Reader’s Bible (2021) (BRB)\n" +
                "● English - Catholic Public Domain Version (2009) (CPDV)\n" +
                "● English - Darby Bible Translation (1890) (DBT)\n" +
                "● English - Douay-Rheims Bible (NT:1582)(OT:1609-1610)(Challoner'sRevision:1749–1752) (DRB)\n" +
                "● English - English Revised Version (1885) (ERV)\n" +
                "● English - Jewish Publication Society's Tanakh (OT:1917) (JPS)\n" +
                "● English - Majority Standard Bible (2022) (MSB)\n" +
                "● English - Smith's Literal Translation (1876) (SLT)\n" +
                "● English - Webster Bible Translation (1833) (WBT)\n" +
                "● English - World English Bible (2020) (WEB)\n" +
                "● English - Weymouth New Testament (NT-1903) (WNT)\n" +
                "● English - Young's Literal Translation (1862) (YLT)\n\n" +
                "Github: https://github.com/poylizardo1995/English-And-Tagalog-Bible-App"
            )
            .setPositiveButton("OK") { dialogInterface, _ -> dialogInterface.dismiss() }
            .create()

        applyThemedDialogStyle(dialog)
        
        // Dynamically style the message text for crisp contrast across dark and sepia themes
        val isDark = currentTheme != ThemeMode.SEPIA
        dialog.findViewById<TextView>(android.R.id.message)?.apply {
            textSize = 15f
            setTextColor(if (isDark) Color.parseColor("#FFFFFF") else Color.parseColor("#2D241E"))
        }
    }

   private fun showBookSelectionDialog() {
        val allBooksAlphabetical = BibleRepository.getAvailableBooks(this).toTypedArray()
        val isTagalog = BibleRepository.currentTranslation.languageGroup == "Tagalog"

        val otCategoryTitle = if (isTagalog) "📜 LUMANG TIPAN" else "📜 OLD TESTAMENT"
        val ntCategoryTitle = if (isTagalog) "✝️ BAGONG TIPAN" else "✝️ NEW TESTAMENT"
        val allCategoryTitle = if (isTagalog) "📖 LAHAT NG AKLAT (A - Z)" else "📖 ALL BOOKS (A - Z)"

        val otItems = if (isTagalog) {
            arrayOf(
                "— Kautusan / Torah —",
                "Genesis", "Exodo", "Levitico", "Mga Bilang", "Deuteronomio",
                "— Kasaysayan —",
                "Josue", "Mga Hukom", "Ruth", "1 Samuel", "2 Samuel", "1 Mga Hari", "2 Mga Hari", "1 Cronica", "2 Cronica", "Ezra", "Nehemias", "Ester",
                "— Tula at Karunungan —",
                "Job", "Mga Awit", "Mga Kawikaan", "Eclesiastes", "Ang Awit ng mga Awit",
                "— Mga Dakilang Propeta —",
                "Isaias", "Jeremias", "Mga Panaghoy", "Ezekiel", "Daniel",
                "— Mga Munting Propeta —",
                "Oseas", "Joel", "Amos", "Obadias", "Jonas", "Mikas", "Nahum", "Habacuc", "Zefanias", "Hagai", "Zacarias", "Malakias"
            )
        } else {
            arrayOf(
                "— Law / Torah —",
                "Genesis", "Exodus", "Leviticus", "Numbers", "Deuteronomy",
                "— History —",
                "Joshua", "Judges", "Ruth", "1 Samuel", "2 Samuel", "1 Kings", "2 Kings", "1 Chronicles", "2 Chronicles", "Ezra", "Nehemiah", "Esther",
                "— Poetry & Wisdom —",
                "Job", "Psalm", "Proverbs", "Ecclesiastes", "Song of Solomon",
                "— Major Prophets —",
                "Isaiah", "Jeremiah", "Lamentations", "Ezekiel", "Daniel",
                "— Minor Prophets —",
                "Hosea", "Joel", "Amos", "Obadiah", "Jonah", "Micah", "Nahum", "Habakkuk", "Zephaniah", "Haggai", "Zechariah", "Malachi"
            )
        }

        val ntItems = if (isTagalog) {
            arrayOf(
                "— Mga Ebanghelyo —",
                "Mateo", "Marcos", "Lucas", "Juan",
                "— Kasaysayan —",
                "Mga Gawa",
                "— Mga Sulat ni Pablo —",
                "Roma", "1 Corinto", "2 Corinto", "Galacia", "Efeso", "Filipos", "Colosas", "1 Tesalonica", "2 Tesalonica", "1 Timoteo", "2 Timoteo", "Tito", "Filemon",
                "— Mga Pangkalahatang Sulat —",
                "Hebreo", "Santiago", "1 Pedro", "2 Pedro", "1 Juan", "2 Juan", "3 Juan", "Judas",
                "— Hula —",
                "Pahayag"
            )
        } else {
            arrayOf(
                "— Gospels —",
                "Matthew", "Mark", "Luke", "John",
                "— History —",
                "Acts",
                "— Pauline Epistles —",
                "Romans", "1 Corinthians", "2 Corinthians", "Galatians", "Ephesians", "Philippians", "Colossians", "1 Thessalonians", "2 Thessalonians", "1 Timothy", "2 Timothy", "Titus", "Philemon",
                "— General Epistles —",
                "Hebrews", "James", "1 Peter", "2 Peter", "1 John", "2 John", "3 John", "Jude",
                "— Prophecy —",
                "Revelation"
            )
        }

        val categoryMap = linkedMapOf(
            otCategoryTitle to otItems,
            ntCategoryTitle to ntItems,
            allCategoryTitle to allBooksAlphabetical
        )

        val categories = categoryMap.keys.toTypedArray()

        val categoryDialog = AlertDialog.Builder(this)
            .setTitle("Select Category")
            .setAdapter(createCenteredListAdapter(categories)) { _, catIndex ->
                val selectedCategory = categories[catIndex]
                val categoryItems = categoryMap[selectedCategory] ?: emptyArray()

                // Filter items to check if any books exist in this active translation
                val availableBooksInTranslation = BibleRepository.getAvailableBooks(this)
                val filteredCategoryItems = categoryItems.filter { item ->
                    item.startsWith("—") || availableBooksInTranslation.any { it.equals(item, ignoreCase = true) }
                }

                // Count actual book titles excluding section headers
                val bookCount = filteredCategoryItems.count { !it.startsWith("—") }

                if (bookCount == 0) {
                    val activeTranslation = BibleRepository.currentTranslation
                    showCenteredToast("${activeTranslation.displayName} - ${activeTranslation.fullTranslationName}")
                } else {
                    val bookDialog = AlertDialog.Builder(this)
                        .setTitle(selectedCategory)
                        .setAdapter(createCenteredListAdapter(filteredCategoryItems.toTypedArray())) { _, bookIndex ->
                            val selectedItem = filteredCategoryItems[bookIndex]
                            if (!selectedItem.startsWith("—")) {
                                showChapterSelectionDialog(selectedItem)
                            }
                        }
                        .create()

                    applyThemedDialogStyle(bookDialog)
                }
            }
            .create()

        applyThemedDialogStyle(categoryDialog)
    }
    
    private fun showChapterSelectionDialog(bookName: String) {
        val chapters = BibleRepository.getChaptersForBook(bookName, this).toTypedArray()
        val dialog = AlertDialog.Builder(this)
            .setTitle("Select Chapter in $bookName")
            .setAdapter(createCenteredListAdapter(chapters)) { _, which ->
                showVerseSelectionDialog(chapters[which])
            }
            .create()
        applyThemedDialogStyle(dialog)
    }

    private fun showVerseSelectionDialog(chapterName: String) {
        val chapterVerses = BibleRepository.getVerseObjectsForChapter(chapterName, this)
        val verseTitles = chapterVerses.map { it.reference }.toTypedArray()
        val dialog = AlertDialog.Builder(this)
            .setTitle("Select Verse in $chapterName")
            .setAdapter(createCenteredListAdapter(verseTitles)) { _, which ->
                displayVerse(chapterVerses[which])
            }
            .create()
        applyThemedDialogStyle(dialog)
    }

    private fun displayVerse(verse: BibleVerse) {
        currentVerse = verse
        tvVerseText.text = verse.text
        tvVerseRef.text = "— ${verse.reference}"
        tvVerseText.startAnimation(AlphaAnimation(0.0f, 1.0f).apply { duration = 400 })
        tvVerseRef.startAnimation(AlphaAnimation(0.0f, 1.0f).apply { duration = 400 })
    }

    // Called ONLY on initial app launch in onCreate
    private fun loadInitialVerse() {
        val defaultRef = if (BibleRepository.currentTranslation.languageGroup == "Tagalog") "Juan 3:16" else "John 3:16"
        val defaultVerse = BibleRepository.getVerseByBookChapterAndVerse(defaultRef, this)
        
        val verseToDisplay = defaultVerse ?: BibleRepository.getRandomVerse(this)
        displayVerse(verseToDisplay)
    }

    // Called when clicking "🔀 New Random Bible Verse" button
    private fun loadRandomVerse() {
        val verse = BibleRepository.getRandomVerse(this)
        displayVerse(verse)
    }
    
    private fun startAutoPlay() {
        if (!isTtsReady) { 
            showCenteredToast("Speech engine not ready")
            return 
        }
        isAutoPlaying = true
        btnAutoPlay.text = "⏹ Stop Auto-Play"
        
        setupTtsListener()
        
        currentVerse?.let { speakVerse(it) }
        showCenteredToast("Auto-Play Started")
    }

    private fun setupTtsListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}

            override fun onDone(utteranceId: String?) {
                if (!isAutoPlaying) return

                runOnUiThread {
                    playNextAutoPlayVerse()
                }
            }

            override fun onError(utteranceId: String?) {
                runOnUiThread { stopAutoPlay() }
            }
        })
    }

    private fun playNextAutoPlayVerse() {
        val verse = currentVerse ?: return

        // 1. Try finding the next verse in the current chapter
        val nextVerseInChapter = BibleRepository.getNextVerse(verse, this)
        if (nextVerseInChapter != null) {
            displayVerse(nextVerseInChapter)
            speakVerse(nextVerseInChapter)
            return
        }

        // 2. End of chapter reached: Handle modes
        if (autoPlayMode == AutoPlayMode.CHAPTER) {
            showCenteredToast("Chapter Auto-Play Completed")
            stopAutoPlay()
            return
        }

        // For BOOK or ALL_BOOKS mode: Find the current book and move to next chapter
        val currentBook = BibleRepository.getAvailableBooks(this).find { book ->
            verse.reference.startsWith(book, ignoreCase = true)
        } ?: return

        val chapters = BibleRepository.getChaptersForBook(currentBook, this)
        val cleanRef = verse.reference.substringBefore(" (").trim()
        val currentChapter = if (cleanRef.contains(':')) cleanRef.substringBeforeLast(':') else cleanRef
        val currentChapterIndex = chapters.indexOf(currentChapter)

        if (currentChapterIndex != -1 && currentChapterIndex < chapters.size - 1) {
            // Next chapter in current book
            val nextChapter = chapters[currentChapterIndex + 1]
            val firstVerseOfNextChapter = BibleRepository.getVerseObjectsForChapter(nextChapter, this).firstOrNull()
            if (firstVerseOfNextChapter != null) {
                displayVerse(firstVerseOfNextChapter)
                speakVerse(firstVerseOfNextChapter)
                return
            }
        }

        // 3. End of book reached
        if (autoPlayMode == AutoPlayMode.BOOK) {
            showCenteredToast("Book Auto-Play Completed")
            stopAutoPlay()
            return
        }

        // 4. For ALL_BOOKS mode: Move to the first chapter of the next book
        if (autoPlayMode == AutoPlayMode.ALL_BOOKS) {
            val allBooks = BibleRepository.getAvailableBooks(this)
            val currentBookIndex = allBooks.indexOf(currentBook)

            if (currentBookIndex != -1 && currentBookIndex < allBooks.size - 1) {
                val nextBook = allBooks[currentBookIndex + 1]
                val firstChapterOfNextBook = BibleRepository.getChaptersForBook(nextBook, this).firstOrNull()
                if (firstChapterOfNextBook != null) {
                    val firstVerseOfNextBook = BibleRepository.getVerseObjectsForChapter(firstChapterOfNextBook, this).firstOrNull()
                    if (firstVerseOfNextBook != null) {
                        displayVerse(firstVerseOfNextBook)
                        speakVerse(firstVerseOfNextBook)
                        return
                    }
                }
            }
        }

        showCenteredToast("Auto-Play Finished")
        stopAutoPlay()
    }

    private fun stopAutoPlay() {
        isAutoPlaying = false
        btnAutoPlay.text = "▶ Auto-Play"
        tts?.stop()
    }

    private fun showCenteredToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).apply { setGravity(android.view.Gravity.CENTER, 0, 0); show() }
    }

    // --- RESTORED COMPLETE CURATED THEMES & BUTTON STYLING ---
    private fun applyTheme() {
        val rootLayout = findViewById<ViewGroup>(android.R.id.content).getChildAt(0) ?: window.decorView

        val statusBarColor = when (currentTheme) {
            ThemeMode.SEPIA -> Color.parseColor("#E8E2D5")
            ThemeMode.CHARCOAL -> Color.parseColor("#121212")
            ThemeMode.NAVY -> Color.parseColor("#0B0F19")
            ThemeMode.OLIVE -> Color.parseColor("#111813")
            ThemeMode.SUNSET -> Color.parseColor("#2D1A12")
        }
        window.statusBarColor = statusBarColor

        when (currentTheme) {
            ThemeMode.SEPIA -> {
                rootLayout.setBackgroundResource(R.drawable.bg_gradient)
                
                verseCard.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FFFFFF"))
                verseCard.setCardBackgroundColor(ColorStateList.valueOf(Color.parseColor("#FFFFFF")))
                verseCard.strokeColor = Color.parseColor("#D0C4B2")
                verseCard.cardElevation = 8f
                
                tvVerseText.setTextColor(Color.parseColor("#2D241E"))
                tvVerseRef.setTextColor(Color.parseColor("#8C6D53"))
                
                btnToggleTheme.text = "🎨 Theme: Classic Sepia"
                btnToggleTheme.setTextColor(Color.parseColor("#6E472C"))
                btnToggleTheme.strokeColor = ColorStateList.valueOf(Color.parseColor("#6E472C"))
                btnToggleTheme.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
                
                btnRefresh.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#6E472C"))
                btnRefresh.setTextColor(Color.WHITE)
                if (btnRefresh is MaterialButton) {
                    (btnRefresh as MaterialButton).strokeColor = ColorStateList.valueOf(Color.parseColor("#6E472C"))
                }

                btnSearch.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#6E472C"))
                btnSearch.setTextColor(Color.WHITE)
                if (btnSearch is MaterialButton) {
                    (btnSearch as MaterialButton).strokeColor = ColorStateList.valueOf(Color.parseColor("#6E472C"))
                }
                
                val dayButtons = listOf(btnCopy, btnShare, btnSpeak, btnSelectManual, btnPrev, btnNext, btnAutoPlay, btnAbout)
                for (btn in dayButtons) {
                    btn.setTextColor(Color.parseColor("#6E472C"))
                    btn.strokeColor = ColorStateList.valueOf(Color.parseColor("#6E472C"))
                    btn.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
                }
            }
            ThemeMode.CHARCOAL -> {
                rootLayout.setBackgroundColor(Color.parseColor("#121212"))
                
                verseCard.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#1E1E1E"))
                verseCard.setCardBackgroundColor(ColorStateList.valueOf(Color.parseColor("#1E1E1E")))
                verseCard.strokeColor = Color.parseColor("#444444")
                verseCard.cardElevation = 6f
                
                tvVerseText.setTextColor(Color.parseColor("#FFFFFF"))
                tvVerseRef.setTextColor(Color.parseColor("#FFB74D"))
                
                btnToggleTheme.text = "🎨 Theme: Charcoal Contrast"
                btnToggleTheme.setTextColor(Color.parseColor("#FFB74D"))
                btnToggleTheme.strokeColor = ColorStateList.valueOf(Color.parseColor("#FFB74D"))
                btnToggleTheme.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
                
                btnRefresh.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#333333"))
                btnRefresh.setTextColor(Color.parseColor("#FFB74D"))
                if (btnRefresh is MaterialButton) {
                    (btnRefresh as MaterialButton).strokeColor = ColorStateList.valueOf(Color.parseColor("#FFB74D"))
                }

                btnSearch.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#333333"))
                btnSearch.setTextColor(Color.parseColor("#FFB74D"))
                if (btnSearch is MaterialButton) {
                    (btnSearch as MaterialButton).strokeColor = ColorStateList.valueOf(Color.parseColor("#FFB74D"))
                }
                
                val charcoalButtons = listOf(btnCopy, btnShare, btnSpeak, btnSelectManual, btnPrev, btnNext, btnAutoPlay, btnAbout)
                for (btn in charcoalButtons) {
                    btn.setTextColor(Color.parseColor("#FFFFFF"))
                    btn.strokeColor = ColorStateList.valueOf(Color.parseColor("#666666"))
                    btn.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
                }
            }
            ThemeMode.NAVY -> {
                rootLayout.setBackgroundColor(Color.parseColor("#0B0F19"))
                
                verseCard.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#121826"))
                verseCard.setCardBackgroundColor(ColorStateList.valueOf(Color.parseColor("#121826")))
                verseCard.strokeColor = Color.parseColor("#283593")
                verseCard.cardElevation = 6f
                
                tvVerseText.setTextColor(Color.parseColor("#FFFFFF"))
                tvVerseRef.setTextColor(Color.parseColor("#9FA8DA"))
                
                btnToggleTheme.text = "🎨 Theme: Midnight Navy"
                btnToggleTheme.setTextColor(Color.parseColor("#9FA8DA"))
                btnToggleTheme.strokeColor = ColorStateList.valueOf(Color.parseColor("#7986CB"))
                btnToggleTheme.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
                
                btnRefresh.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#3F51B5"))
                btnRefresh.setTextColor(Color.parseColor("#FFFFFF"))
                if (btnRefresh is MaterialButton) {
                    (btnRefresh as MaterialButton).strokeColor = ColorStateList.valueOf(Color.parseColor("#3F51B5"))
                }

                btnSearch.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#3F51B5"))
                btnSearch.setTextColor(Color.parseColor("#FFFFFF"))
                if (btnSearch is MaterialButton) {
                    (btnSearch as MaterialButton).strokeColor = ColorStateList.valueOf(Color.parseColor("#3F51B5"))
                }
                
                val navyButtons = listOf(btnCopy, btnShare, btnSpeak, btnSelectManual, btnPrev, btnNext, btnAutoPlay, btnAbout)
                for (btn in navyButtons) {
                    btn.setTextColor(Color.parseColor("#E0E0E0"))
                    btn.strokeColor = ColorStateList.valueOf(Color.parseColor("#7986CB"))
                    btn.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
                }
            }
            ThemeMode.OLIVE -> {
                rootLayout.setBackgroundColor(Color.parseColor("#111813"))
                
                verseCard.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#1A211C"))
                verseCard.setCardBackgroundColor(ColorStateList.valueOf(Color.parseColor("#1A211C")))
                verseCard.strokeColor = Color.parseColor("#2E7D32")
                verseCard.cardElevation = 6f
                
                tvVerseText.setTextColor(Color.parseColor("#FFFFFF"))
                tvVerseRef.setTextColor(Color.parseColor("#A5D6A7"))
                
                btnToggleTheme.text = "🎨 Theme: Olive Grace"
                btnToggleTheme.setTextColor(Color.parseColor("#A5D6A7"))
                btnToggleTheme.strokeColor = ColorStateList.valueOf(Color.parseColor("#81C784"))
                btnToggleTheme.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
                
                btnRefresh.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#2E7D32"))
                btnRefresh.setTextColor(Color.parseColor("#FFFFFF"))
                if (btnRefresh is MaterialButton) {
                    (btnRefresh as MaterialButton).strokeColor = ColorStateList.valueOf(Color.parseColor("#2E7D32"))
                }

                btnSearch.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#2E7D32"))
                btnSearch.setTextColor(Color.parseColor("#FFFFFF"))
                if (btnSearch is MaterialButton) {
                    (btnSearch as MaterialButton).strokeColor = ColorStateList.valueOf(Color.parseColor("#2E7D32"))
                }
                
                val oliveButtons = listOf(btnCopy, btnShare, btnSpeak, btnSelectManual, btnPrev, btnNext, btnAutoPlay, btnAbout)
                for (btn in oliveButtons) {
                    btn.setTextColor(Color.parseColor("#E0E0E0"))
                    btn.strokeColor = ColorStateList.valueOf(Color.parseColor("#81C784"))
                    btn.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
                }
            }
            ThemeMode.SUNSET -> {
                rootLayout.setBackgroundColor(Color.parseColor("#1B0F0B"))
                
                verseCard.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#2D1A12"))
                verseCard.setCardBackgroundColor(ColorStateList.valueOf(Color.parseColor("#2D1A12")))
                verseCard.strokeColor = Color.parseColor("#D77A61")
                verseCard.cardElevation = 6f
                
                tvVerseText.setTextColor(Color.parseColor("#FFE5D9"))
                tvVerseRef.setTextColor(Color.parseColor("#FFB74D"))
                
                btnToggleTheme.text = "🎨 Theme: Warm Sunset"
                btnToggleTheme.setTextColor(Color.parseColor("#FFB74D"))
                btnToggleTheme.strokeColor = ColorStateList.valueOf(Color.parseColor("#D77A61"))
                btnToggleTheme.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
                
                btnRefresh.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#A34828"))
                btnRefresh.setTextColor(Color.parseColor("#FFFFFF"))
                if (btnRefresh is MaterialButton) {
                    (btnRefresh as MaterialButton).strokeColor = ColorStateList.valueOf(Color.parseColor("#A34828"))
                }

                btnSearch.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#A34828"))
                btnSearch.setTextColor(Color.parseColor("#FFFFFF"))
                if (btnSearch is MaterialButton) {
                    (btnSearch as MaterialButton).strokeColor = ColorStateList.valueOf(Color.parseColor("#A34828"))
                }
                
                val sunsetButtons = listOf(btnCopy, btnShare, btnSpeak, btnSelectManual, btnPrev, btnNext, btnAutoPlay, btnAbout)
                for (btn in sunsetButtons) {
                    btn.setTextColor(Color.parseColor("#FFE5D9"))
                    btn.strokeColor = ColorStateList.valueOf(Color.parseColor("#D77A61"))
                    btn.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
                }
            }
        }
        updateLanguageToggleUI(BibleRepository.currentTranslation)
    }

    private fun updateLanguageToggleUI(translation: BibleTranslation) {
        val isEnglishActive = translation.languageGroup == "English"
        btnEnglish.text = if (isEnglishActive) translation.displayName.uppercase() + " ▾" else "ENGLISH ▾"
        btnTagalog.text = if (!isEnglishActive) translation.displayName.uppercase() + " ▾" else "TAGALOG ▾"
    
        val isDark = currentTheme != ThemeMode.SEPIA
    
        if (isDark) {
            verseCard.backgroundTintList = null
            verseCard.setCardBackgroundColor(ColorStateList.valueOf(
                when (currentTheme) {
                    ThemeMode.CHARCOAL -> Color.parseColor("#1E1E1E")
                    ThemeMode.NAVY -> Color.parseColor("#121826")
                    ThemeMode.OLIVE -> Color.parseColor("#1A211C")
                    ThemeMode.SUNSET -> Color.parseColor("#2D1A12")
                    else -> Color.parseColor("#1E1E1E")
                }
            ))
            verseCard.strokeColor = when (currentTheme) {
                ThemeMode.CHARCOAL -> Color.parseColor("#444444")
                ThemeMode.NAVY -> Color.parseColor("#283593")
                ThemeMode.OLIVE -> Color.parseColor("#2E7D32")
                ThemeMode.SUNSET -> Color.parseColor("#D77A61")
                else -> Color.parseColor("#444444")
            }
    
            val pillColorHex = when (currentTheme) {
                ThemeMode.CHARCOAL -> Color.parseColor("#D97706") // Rich Warm Amber
                ThemeMode.NAVY -> Color.parseColor("#3F51B5")     // Deep Indigo
                ThemeMode.OLIVE -> Color.parseColor("#2E7D32")    // Forest Green
                ThemeMode.SUNSET -> Color.parseColor("#A34828")   // Warm Sunset Terracotta
                else -> Color.parseColor("#8D6E63")
            }
            val darkPillColor = ColorStateList.valueOf(pillColorHex)
    
            // Reset backgrounds
            btnEnglish.backgroundTintList = null
            btnEnglish.setBackgroundColor(Color.TRANSPARENT)
            btnTagalog.backgroundTintList = null
            btnTagalog.setBackgroundColor(Color.TRANSPARENT)
    
            if (isEnglishActive) {
                btnEnglish.setBackgroundResource(R.drawable.bg_pill_selected)
                btnEnglish.backgroundTintList = darkPillColor
                btnEnglish.setTextColor(Color.WHITE) // High contrast pure white text on active pill
                
                btnTagalog.setTextColor(Color.parseColor("#B0BEC5")) // Soft readable light grey for inactive toggle option
            } else {
                btnTagalog.setBackgroundResource(R.drawable.bg_pill_selected)
                btnTagalog.backgroundTintList = darkPillColor
                btnTagalog.setTextColor(Color.WHITE) // Pure white text
                
                btnEnglish.setTextColor(Color.parseColor("#B0BEC5"))
            }
        } else {
            // Classic Sepia / Light Theme
            verseCard.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FFFFFF"))
            verseCard.setCardBackgroundColor(ColorStateList.valueOf(Color.parseColor("#FFFFFF")))
            verseCard.strokeColor = Color.parseColor("#D0C4B2")
    
            val dayPillColor = ColorStateList.valueOf(Color.parseColor("#6E472C"))
    
            btnEnglish.backgroundTintList = null
            btnEnglish.setBackgroundColor(Color.TRANSPARENT)
            btnTagalog.backgroundTintList = null
            btnTagalog.setBackgroundColor(Color.TRANSPARENT)
    
            if (isEnglishActive) {
                btnEnglish.setBackgroundResource(R.drawable.bg_pill_selected)
                btnEnglish.backgroundTintList = dayPillColor
                btnEnglish.setTextColor(Color.WHITE)
                
                btnTagalog.setTextColor(Color.parseColor("#4A3B32"))
            } else {
                btnTagalog.setBackgroundResource(R.drawable.bg_pill_selected)
                btnTagalog.backgroundTintList = dayPillColor
                btnTagalog.setTextColor(Color.WHITE)
                
                btnEnglish.setTextColor(Color.parseColor("#4A3B32"))
            }
        }
    }

    private fun speakVerse(verse: BibleVerse) {
        if (!isTtsReady || tts == null) return

        val isTagalog = BibleRepository.currentTranslation.languageGroup == "Tagalog"
        
        if (isTagalog) {
            // Try Filipino (fil_PH) first as Android TTS natively supports it best
            val filLocale = Locale("fil", "PH")
            val result = tts?.setLanguage(filLocale)
            
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                // Fall back to general Tagalog locale
                tts?.language = Locale("tl", "PH")
            }
            // Slightly refine speech rate for clear formal Tagalog narration
            tts?.setSpeechRate(0.88f)
            tts?.setPitch(1.0f)
        } else {
            tts?.language = Locale.US
            tts?.setSpeechRate(0.95f)
            tts?.setPitch(1.0f)
        }

        val utteranceId = "AutoPlay_${System.currentTimeMillis()}"
        tts?.speak("${verse.text}. Reference: ${verse.reference}", TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }
    
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsReady = true
        }
    }

    override fun onDestroy() {
        tts?.stop(); tts?.shutdown()
        super.onDestroy()
    }
}

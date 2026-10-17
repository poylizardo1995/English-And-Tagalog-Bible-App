package com.example.EnglishTagalogBible

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
        SEPIA("📜 Classic Sepia"),
        CHARCOAL("🖤 Charcoal Contrast"),
        NAVY("🌌 Midnight Navy"),
        OLIVE("🌿 Olive Grace"),
        SUNSET("🌅 Warm Sunset")
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
                loadVerse()
            }
        } else {
            loadVerse()
        }

        btnRefresh.setOnClickListener { stopAutoPlay(); loadVerse() }
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
            ThemeMode.SEPIA -> Color.parseColor("#8C6D53")
            ThemeMode.CHARCOAL -> Color.parseColor("#FFB74D")
            ThemeMode.NAVY -> Color.parseColor("#9FA8DA")
            ThemeMode.OLIVE -> Color.parseColor("#A5D6A7")
            ThemeMode.SUNSET -> Color.parseColor("#FFB74D")
        }

        return object : android.widget.ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, items) {
            override fun getView(position: Int, convertView: android.view.View?, parent: ViewGroup): android.view.View {
                val view = super.getView(position, convertView, parent)
                (view as? TextView)?.apply {
                    this.setTextColor(textColor)
                    this.gravity = android.view.Gravity.CENTER
                    this.textAlignment = android.view.View.TEXT_ALIGNMENT_CENTER
                    this.setPadding(0, 28, 0, 28)
                    if (items[position].startsWith("—") || items[position].startsWith("📜") || items[position].startsWith("✝️") || items[position].startsWith("📚") || items[position].contains("✅") || items[position].contains("⬇️")) {
                        this.setTextColor(accentColor)
                        this.setTypeface(null, android.graphics.Typeface.BOLD)
                    } else {
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

        val availableTranslations = BibleTranslation.values().filter { it.isAvailable(this) }
        val translationNames = availableTranslations.map { it.displayName }.toTypedArray()
        
        var selectedTranslationIndex = availableTranslations.indexOf(BibleRepository.currentTranslation)
        if (selectedTranslationIndex < 0) selectedTranslationIndex = 0

        val isDark = currentTheme != ThemeMode.SEPIA
        val spinnerLabel = TextView(this).apply {
            text = "Select Version:"
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
                    .setTitle("Select Version")
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

    private fun handleLanguageSelection(groupName: String) {
        val matchingTranslations = BibleTranslation.values().filter { it.languageGroup == groupName }

        val options = matchingTranslations.map { 
            val available = it.isAvailable(this)
            if (available) "${it.displayName} ✅" else "${it.displayName} ⬇️ (Download)"
        }.toTypedArray()

        val dialog = AlertDialog.Builder(this)
            .setTitle("Select $groupName Version")
            .setAdapter(createCenteredListAdapter(options)) { _, which ->
                val selected = matchingTranslations[which]
                if (selected.isAvailable(this)) {
                    if (selected != BibleRepository.currentTranslation) {
                        switchToTranslation(selected)
                    }
                } else {
                    showDownloadOrUploadChoiceDialog(selected)
                }
            }
            .create()
        applyThemedDialogStyle(dialog)
    }

    private fun showDownloadOrUploadChoiceDialog(translation: BibleTranslation) {
        val choices = arrayOf("🌐 Download via Internet", "📁 Upload JSON Manually")
        val dialog = AlertDialog.Builder(this)
            .setTitle("Get ${translation.displayName}")
            .setAdapter(createCenteredListAdapter(choices)) { _, index ->
                if (index == 0) {
                    if (!BibleManager.isNetworkAvailable(this)) {
                        showCenteredToast("No internet connection available.")
                        return@setAdapter
                    }
                    showCenteredToast("Downloading ${translation.displayName}...")
                    BibleManager.downloadTranslationPackage(
                        context = this,
                        translation = translation,
                        onProgress = {},
                        onSuccess = { runOnUiThread { showCenteredToast("${translation.displayName} installed!"); switchToTranslation(translation) } },
                        onError = { err -> runOnUiThread { showCenteredToast(err) } }
                    )
                } else {
                    val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "application/json" }
                    startActivityForResult(intent, 999)
                }
            }
            .create()
        applyThemedDialogStyle(dialog)
    }

    private fun switchToTranslation(target: BibleTranslation) {
        BibleRepository.setTranslation(this, target)
        updateLanguageToggleUI(target)
        loadVerse()
    }

    private fun showAboutDialog() {
        val dialog = AlertDialog.Builder(this)
            .setMessage(
                "About & Credits\n\n" +
                "English and Tagalog Bible v1.3\n\n" +
                "App Developed by Poy Lizardo\n\n" +
                "Copyright © 2026. All Rights Reserved. To God be the glory!\n\n" +
                "Scripture Text Provided via Public Domain Translations: English - King James Version (KJV) and Tagalog - Ang Dating Biblia 1905 (ADB1905).\n\n" +    
                "Github: https://github.com/poylizardo1995/English-And-Tagalog-Bible-App"
            )
            .setPositiveButton("OK") { dialogInterface, _ -> dialogInterface.dismiss() }
            .create()

        applyThemedDialogStyle(dialog)
        dialog.findViewById<TextView>(android.R.id.message)?.textSize = 15f
    }

    // --- RESTORED CATEGORIZED BOOK SELECTION DIALOG ---
    private fun showBookSelectionDialog() {
        val allBooksAlphabetical = BibleRepository.getAvailableBooks(this).toTypedArray()

        val categoryMap = linkedMapOf(
            "📜 OLD TESTAMENT" to arrayOf(
                "— Law / Torah —",
                "Genesis", "Exodus", "Leviticus", "Numbers", "Deuteronomy",
                "— History —",
                "Joshua", "Judges", "Ruth", "1 Samuel", "2 Samuel", "1 Kings", "2 Kings", "1 Chronicles", "2 Chronicles", "Ezra", "Nehemiah", "Esther",
                "— Poetry & Wisdom —",
                "Job", "Psalms", "Proverbs", "Ecclesiastes", "Song of Solomon",
                "— Major Prophets —",
                "Isaiah", "Jeremiah", "Lamentations", "Ezekiel", "Daniel",
                "— Minor Prophets —",
                "Hosea", "Joel", "Amos", "Obadiah", "Jonah", "Micah", "Nahum", "Habakkuk", "Zephaniah", "Haggai", "Zechariah", "Malachi"
            ),
            "✝️ NEW TESTAMENT" to arrayOf(
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
            ),
            "📚 ALL BOOKS (A - Z)" to allBooksAlphabetical
        )

        val categories = categoryMap.keys.toTypedArray()

        val categoryDialog = AlertDialog.Builder(this)
            .setTitle("Select Category")
            .setAdapter(createCenteredListAdapter(categories)) { _, catIndex ->
                val selectedCategory = categories[catIndex]
                val categoryItems = categoryMap[selectedCategory] ?: emptyArray()

                val bookDialog = AlertDialog.Builder(this)
                    .setTitle(selectedCategory)
                    .setAdapter(createCenteredListAdapter(categoryItems)) { _, bookIndex ->
                        val selectedItem = categoryItems[bookIndex]
                        if (!selectedItem.startsWith("—")) {
                            showChapterSelectionDialog(selectedItem)
                        }
                    }
                    .create()

                applyThemedDialogStyle(bookDialog)
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

    private fun loadVerse() {
        val verse = BibleRepository.getRandomVerse(this)
        displayVerse(verse)
    }

    private fun startAutoPlay() {
        if (!isTtsReady) { showCenteredToast("Speech engine not ready"); return }
        isAutoPlaying = true
        btnAutoPlay.text = "⏹ Stop Auto-Play"
        currentVerse?.let { speakVerse(it) }
        showCenteredToast("Auto-Play Started")
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
                ThemeMode.CHARCOAL -> Color.parseColor("#333333")
                ThemeMode.NAVY -> Color.parseColor("#3F51B5")
                ThemeMode.OLIVE -> Color.parseColor("#2E7D32")
                ThemeMode.SUNSET -> Color.parseColor("#A34828")
                else -> Color.parseColor("#8D6E63")
            }
            val darkPillColor = ColorStateList.valueOf(pillColorHex)

            if (isEnglishActive) {
                btnEnglish.setBackgroundResource(R.drawable.bg_pill_selected)
                btnEnglish.backgroundTintList = darkPillColor
                btnEnglish.setTextColor(if (currentTheme == ThemeMode.CHARCOAL) Color.parseColor("#FFB74D") else Color.parseColor("#FFFFFF"))
                
                btnTagalog.backgroundTintList = null
                btnTagalog.setBackgroundColor(Color.TRANSPARENT)
                btnTagalog.setTextColor(Color.parseColor("#D7CCC8"))
            } else {
                btnTagalog.setBackgroundResource(R.drawable.bg_pill_selected)
                btnTagalog.backgroundTintList = darkPillColor
                btnTagalog.setTextColor(if (currentTheme == ThemeMode.CHARCOAL) Color.parseColor("#FFB74D") else Color.parseColor("#FFFFFF"))
                
                btnEnglish.backgroundTintList = null
                btnEnglish.setBackgroundColor(Color.TRANSPARENT)
                btnTagalog.setTextColor(Color.parseColor("#D7CCC8"))
            }
        } else {
            verseCard.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FFFFFF"))
            verseCard.setCardBackgroundColor(ColorStateList.valueOf(Color.parseColor("#FFFFFF")))
            verseCard.strokeColor = Color.parseColor("#D0C4B2")

            val dayPillColor = ColorStateList.valueOf(Color.parseColor("#6E472C"))

            if (isEnglishActive) {
                btnEnglish.setBackgroundResource(R.drawable.bg_pill_selected)
                btnEnglish.backgroundTintList = dayPillColor
                btnEnglish.setTextColor(Color.WHITE)
                
                btnTagalog.backgroundTintList = null
                btnTagalog.setBackgroundColor(Color.TRANSPARENT)
                btnTagalog.setTextColor(Color.parseColor("#555555"))
            } else {
                btnTagalog.setBackgroundResource(R.drawable.bg_pill_selected)
                btnTagalog.backgroundTintList = dayPillColor
                btnTagalog.setTextColor(Color.WHITE)
                
                btnEnglish.backgroundTintList = null
                btnEnglish.setBackgroundColor(Color.TRANSPARENT)
                btnTagalog.setTextColor(Color.parseColor("#555555"))
            }
        }
    }

    private fun speakVerse(verse: BibleVerse) {
        if (!isTtsReady || tts == null) return
        tts?.setLanguage(Locale.US)
        tts?.speak("${verse.text}. Reference: ${verse.reference}", TextToSpeech.QUEUE_FLUSH, null, "utterance_id")
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

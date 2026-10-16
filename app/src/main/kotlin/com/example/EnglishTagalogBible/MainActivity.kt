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
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

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
    
    // Language Toggle Pill Views
    private lateinit var btnEnglish: TextView
    private lateinit var btnTagalog: TextView
    private lateinit var languageToggleContainer: ViewGroup
    
    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var currentVerse: BibleVerse? = null
    private var isDarkMode = false
    private var isAutoPlaying = false

    // Auto-Play State & Modes
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

        btnSearch.setOnClickListener {
            stopAutoPlay()
            showSearchQueryDialog()
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
            val fullText = "$verseText $verseRef"

            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Bible Verse", fullText)
            clipboard.setPrimaryClip(clip)
            showCenteredToast("Verse copied to clipboard!")
        }

        btnShare.setOnClickListener {
            showShareOptionsDialog()
        }

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

        val titleView = dialog.findViewById<TextView>(androidx.appcompat.R.id.alertTitle)
        titleView?.apply {
            gravity = android.view.Gravity.CENTER
            textAlignment = android.view.View.TEXT_ALIGNMENT_CENTER
            setTextColor(if (isDarkMode) Color.parseColor("#FFFFFF") else Color.parseColor("#2D241E"))
        }

        val messageView = dialog.findViewById<TextView>(android.R.id.message)
        messageView?.apply {
            gravity = android.view.Gravity.CENTER
            textAlignment = android.view.View.TEXT_ALIGNMENT_CENTER
            setTextColor(if (isDarkMode) Color.parseColor("#E0E0E0") else Color.parseColor("#2D241E"))
        }

        val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        positiveButton?.setTextColor(if (isDarkMode) Color.parseColor("#B08D73") else Color.parseColor("#6E472C"))

        val negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
        negativeButton?.setTextColor(if (isDarkMode) Color.parseColor("#B08D73") else Color.parseColor("#6E472C"))

        dialog.window?.decorView?.background = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = 24f
            setColor(if (isDarkMode) Color.parseColor("#25201D") else Color.parseColor("#FFFFFF"))
            setStroke(2, if (isDarkMode) Color.parseColor("#8D6E63") else Color.parseColor("#E8E2D5"))
        }
    }

    // --- REUSABLE CENTERED LIST ADAPTER HELPER ---
    private fun createCenteredListAdapter(items: Array<String>): android.widget.ArrayAdapter<String> {
        val textColor = if (isDarkMode) Color.parseColor("#FFFFFF") else Color.parseColor("#2D241E")
        return object : android.widget.ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, items) {
            override fun getView(position: Int, convertView: android.view.View?, parent: ViewGroup): android.view.View {
                val view = super.getView(position, convertView, parent)
                (view as? TextView)?.apply {
                    this.setTextColor(textColor)
                    this.gravity = android.view.Gravity.CENTER
                    this.textAlignment = android.view.View.TEXT_ALIGNMENT_CENTER
                    this.setPadding(0, 28, 0, 28)
                    
                    if (items[position].startsWith("—") || items[position].startsWith("📜") || items[position].startsWith("✝️") || items[position].startsWith("📚") || items[position].startsWith("▶") || items[position].startsWith("📝") || items[position].startsWith("🖼️")) {
                        this.setTextColor(if (isDarkMode) Color.parseColor("#B08D73") else Color.parseColor("#8C6D53"))
                        this.setTypeface(null, android.graphics.Typeface.BOLD)
                    } else {
                        this.setTypeface(null, android.graphics.Typeface.NORMAL)
                    }

                    layoutParams = android.widget.AbsListView.LayoutParams(
                        android.widget.AbsListView.LayoutParams.MATCH_PARENT,
                        android.widget.AbsListView.LayoutParams.WRAP_CONTENT
                    )
                }
                return view
            }
        }
    }

    private fun showShareOptionsDialog() {
        val verseText = tvVerseText.text.toString()
        val verseRef = tvVerseRef.text.toString()

        val options = arrayOf("📝 Share as Text", "🖼️ Share as Image Card")
        val dialog = AlertDialog.Builder(this)
            .setTitle("Share Verse")
            .setAdapter(createCenteredListAdapter(options)) { _, which ->
                if (which == 0) {
                    val shareBody = "$verseText\n\n$verseRef"
                    val shareIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, shareBody)
                    }
                    startActivity(Intent.createChooser(shareIntent, "Share Verse via"))
                } else {
                    shareVerseAsImage(verseText, verseRef)
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

            val gradient = LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                intArrayOf(Color.parseColor("#2D241E"), Color.parseColor("#4A3B32"), Color.parseColor("#1E1A17")),
                null,
                Shader.TileMode.CLAMP
            )
            val bgPaint = Paint().apply { shader = gradient }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

            val borderPaint = Paint().apply {
                color = Color.parseColor("#B08D73")
                style = Paint.Style.STROKE
                strokeWidth = 6f
            }
            canvas.drawRoundRect(60f, 60f, (width - 60).toFloat(), (height - 60).toFloat(), 32f, 32f, borderPaint)

            val textPaint = Paint().apply {
                color = Color.parseColor("#FFFFFF")
                textSize = 54f
                typeface = Typeface.create("serif", Typeface.ITALIC)
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }

            val refPaint = Paint().apply {
                color = Color.parseColor("#D7CCC8")
                textSize = 42f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }

            val brandingPaint = Paint().apply {
                color = Color.parseColor("#8D6E63")
                textSize = 32f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }

            val maxWidth = width - 200f
            val lines = mutableListOf<String>()
            val words = verseText.split(" ")
            var currentLine = ""

            for (word in words) {
                val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
                if (textPaint.measureText(testLine) <= maxWidth) {
                    currentLine = testLine
                } else {
                    lines.add(currentLine)
                    currentLine = word
                }
            }
            if (currentLine.isNotEmpty()) {
                lines.add(currentLine)
            }

            val lineHeight = 75f
            val totalTextHeight = lines.size * lineHeight
            var startY = (height / 2f) - (totalTextHeight / 2f) - 60f

            for (line in lines) {
                canvas.drawText(line, (width / 2f), startY, textPaint)
                startY += lineHeight
            }

            canvas.drawText(verseRef, (width / 2f), startY + 60f, refPaint)
            canvas.drawText("English & Tagalog Bible App", (width / 2f), height - 100f, brandingPaint)

            val cachePath = File(cacheDir, "images")
            cachePath.mkdirs()
            val file = File(cachePath, "shared_verse.png")
            val stream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.close()

            val imageUri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)

            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
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
        val options = arrayOf(
            "▶ Auto-Play Chapter (Stops at chapter end)",
            "▶ Auto-Play Book (Continuous chapters)",
            "▶ Auto-Play All Books (Continuous Bible)"
        )
        val dialog = AlertDialog.Builder(this)
            .setTitle("Select Auto-Play Mode")
            .setAdapter(createCenteredListAdapter(options)) { _, which ->
                autoPlayMode = when (which) {
                    0 -> AutoPlayMode.CHAPTER
                    1 -> AutoPlayMode.BOOK
                    else -> AutoPlayMode.ALL_BOOKS
                }
                startAutoPlay()
            }
            .create()
        applyThemedDialogStyle(dialog)
    }

    private fun showSearchQueryDialog() {
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 20, 48, 0)
        }

        val translations = BibleTranslation.values()
        val translationNames = translations.map { it.displayName }.toTypedArray()
        
        val spinnerLabel = TextView(this).apply {
            text = "Select Version:"
            setTextColor(if (isDarkMode) Color.parseColor("#E0E0E0") else Color.parseColor("#2D241E"))
            textSize = 14f
            setPadding(0, 0, 0, 8)
        }

        val versionSpinner = android.widget.Spinner(this).apply {
            val adapter = android.widget.ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                translationNames
            )
            setAdapter(adapter)
            
            val currentIndex = translations.indexOf(BibleRepository.currentTranslation)
            if (currentIndex != -1) setSelection(currentIndex)
        }

        val inputEditText = android.widget.EditText(this).apply {
            hint = "Enter keyword or reference (e.g., love, John 3)"
            setTextColor(if (isDarkMode) Color.parseColor("#FFFFFF") else Color.parseColor("#2D241E"))
            setHintTextColor(if (isDarkMode) Color.parseColor("#888888") else Color.parseColor("#888888"))
            setPadding(24, 36, 24, 36)
        }

        layout.addView(spinnerLabel)
        layout.addView(versionSpinner)
        layout.addView(inputEditText)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Search Bible")
            .setView(layout)
            .setPositiveButton("Search") { _, _ ->
                val query = inputEditText.text.toString().trim()
                val selectedTranslation = translations[versionSpinner.selectedItemPosition]
                
                if (query.isNotEmpty()) {
                    performVerseSearch(query, selectedTranslation)
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

        val allBooks = arrayOf(
            "Genesis", "Exodus", "Leviticus", "Numbers", "Deuteronomy", "Joshua", "Judges", "Ruth",
            "1 Samuel", "2 Samuel", "1 Kings", "2 Kings", "1 Chronicles", "2 Chronicles", "Ezra", "Nehemiah", "Esther",
            "Job", "Psalms", "Proverbs", "Ecclesiastes", "Song of Solomon", "Isaiah", "Jeremiah", "Lamentations",
            "Ezekiel", "Daniel", "Hosea", "Joel", "Amos", "Obadiah", "Jonah", "Micah", "Nahum", "Habakkuk",
            "Zephaniah", "Haggai", "Zechariah", "Malachi", "Matthew", "Mark", "Luke", "John", "Acts",
            "Romans", "1 Corinthians", "2 Corinthians", "Galatians", "Ephesians", "Philippians", "Colossians",
            "1 Thessalonians", "2 Thessalonians", "1 Timothy", "2 Timothy", "Titus", "Philemon", "Hebrews",
            "James", "1 Peter", "2 Peter", "1 John", "2 John", "3 John", "Jude", "Revelation"
        )

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
        val resultTitles = results.map { "${it.reference}: ${it.text}" }.toTypedArray()

        val dialog = AlertDialog.Builder(this)
            .setTitle("Results (${results.size}) - ${targetTranslation.displayName}")
            .setAdapter(createCenteredListAdapter(resultTitles)) { _, which ->
                BibleRepository.setTranslation(this, targetTranslation)
                updateLanguageToggleUI(targetTranslation)
                val selectedVerse = results[which]
                displayVerse(selectedVerse)
            }
            .create()

        applyThemedDialogStyle(dialog)
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

            val dialog = AlertDialog.Builder(this)
                .setTitle("Select $groupName Version")
                .setAdapter(createCenteredListAdapter(options)) { d, which ->
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
            showCenteredToast("This translation is not available yet.")
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
                showCenteredToast("This translation is not available yet.")
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
        val allBooksAlphabetical = arrayOf(
            "1 Chronicles", "1 Corinthians", "1 John", "1 Kings", "1 Peter", "1 Samuel",
            "1 Thessalonians", "1 Timothy", "2 Chronicles", "2 Corinthians", "2 John",
            "2 Kings", "2 Peter", "2 Samuel", "2 Thessalonians", "2 Timothy", "3 John",
            "Acts", "Amos", "Colossians", "Daniel", "Deuteronomy", "Ecclesiastes",
            "Ephesians", "Esther", "Exodus", "Ezekiel", "Ezra", "Galatians", "Genesis",
            "Habakkuk", "Haggai", "Hebrews", "Hosea", "Isaiah", "James", "Jeremiah",
            "Job", "Joel", "John", "Jonah", "Joshua", "Jude", "Judges", "Lamentations",
            "Leviticus", "Luke", "Malachi", "Mark", "Matthew", "Micah", "Nahum",
            "Nehemiah", "Numbers", "Obadiah", "Philemon", "Philippians", "Proverbs",
            "Psalms", "Revelation", "Romans", "Ruth", "Song of Solomon", "Titus",
            "Zechariah", "Zephaniah"
        )

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
            showCenteredToast("No verses found for $chapterName")
            return
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Select Verse in $chapterName")
            .setAdapter(createCenteredListAdapter(verseTitles)) { _, which ->
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

        val cleanRef = verse.reference.substringBefore(" (")
        val lastColonIndex = cleanRef.lastIndexOf(':')
        if (lastColonIndex != -1) {
            val bookAndChapter = cleanRef.substring(0, lastColonIndex)
            val lastSpaceIndex = bookAndChapter.lastIndexOf(' ')
            if (lastSpaceIndex != -1) {
                currentBookName = bookAndChapter.substring(0, lastSpaceIndex)
                currentChapterName = bookAndChapter
            }
        }

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
            showCenteredToast("Speech engine not ready yet")
            return
        }
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

    private fun advanceToNextVerseOrSection() {
        currentVerse?.let { verse ->
            val nextVerse = BibleRepository.getNextVerse(verse, this)
            if (nextVerse != null) {
                displayVerse(nextVerse)
                speakVerse(nextVerse)
            } else {
                when (autoPlayMode) {
                    AutoPlayMode.CHAPTER -> {
                        stopAutoPlay()
                        showCenteredToast("End of chapter reached.")
                    }
                    AutoPlayMode.BOOK -> {
                        if (!tryAdvanceToNextChapter(false)) {
                            stopAutoPlay()
                            showCenteredToast("End of book reached.")
                        }
                    }
                    AutoPlayMode.ALL_BOOKS -> {
                        if (!tryAdvanceToNextChapter(true)) {
                            stopAutoPlay()
                            showCenteredToast("End of Bible reached.")
                        }
                    }
                }
            }
        }
    }

    private fun tryAdvanceToNextChapter(allowBookTransition: Boolean): Boolean {
        val book = currentBookName ?: return false
        val chapters = BibleRepository.getChaptersForBook(book, this)
        val currentChap = currentChapterName ?: return false
        val currentChapIndex = chapters.indexOf(currentChap)

        if (currentChapIndex != -1 && currentChapIndex < chapters.size - 1) {
            val nextChapterName = chapters[currentChapIndex + 1]
            val verses = BibleRepository.getVerseObjectsForChapter(nextChapterName, this)
            if (verses.isNotEmpty()) {
                displayVerse(verses[0])
                speakVerse(verses[0])
                return true
            }
        } else if (allowBookTransition) {
            val allBooks = arrayOf(
                "Genesis", "Exodus", "Leviticus", "Numbers", "Deuteronomy", "Joshua", "Judges", "Ruth",
                "1 Samuel", "2 Samuel", "1 Kings", "2 Kings", "1 Chronicles", "2 Chronicles", "Ezra", "Nehemiah", "Esther",
                "Job", "Psalms", "Proverbs", "Ecclesiastes", "Song of Solomon", "Isaiah", "Jeremiah", "Lamentations",
                "Ezekiel", "Daniel", "Hosea", "Joel", "Amos", "Obadiah", "Jonah", "Micah", "Nahum", "Habakkuk",
                "Zephaniah", "Haggai", "Zechariah", "Malachi", "Matthew", "Mark", "Luke", "John", "Acts",
                "Romans", "1 Corinthians", "2 Corinthians", "Galatians", "Ephesians", "Philippians", "Colossians",
                "1 Thessalonians", "2 Thessalonians", "1 Timothy", "2 Timothy", "Titus", "Philemon", "Hebrews",
                "James", "1 Peter", "2 Peter", "1 John", "2 John", "3 John", "Jude", "Revelation"
            )
            val currentBookIndex = allBooks.indexOf(book)
            if (currentBookIndex != -1 && currentBookIndex < allBooks.size - 1) {
                val nextBookName = allBooks[currentBookIndex + 1]
                val nextBookChapters = BibleRepository.getChaptersForBook(nextBookName, this)
                if (nextBookChapters.isNotEmpty()) {
                    val firstChapterVerses = BibleRepository.getVerseObjectsForChapter(nextBookChapters[0], this)
                    if (firstChapterVerses.isNotEmpty()) {
                        displayVerse(firstChapterVerses[0])
                        speakVerse(firstChapterVerses[0])
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun showCenteredToast(message: String) {
        val toast = Toast.makeText(this, message, Toast.LENGTH_SHORT)
        toast.setGravity(android.view.Gravity.CENTER, 0, 0)
        toast.show()
    }

    private fun applyTheme() {
        val rootLayout = findViewById<ViewGroup>(android.R.id.content).getChildAt(0) ?: window.decorView

        if (isDarkMode) {
            rootLayout.setBackgroundResource(R.drawable.bg_gradient)
            
            verseCard.backgroundTintList = null
            verseCard.setCardBackgroundColor(ColorStateList.valueOf(Color.parseColor("#1E1E1E")))
            verseCard.strokeColor = Color.parseColor("#444444")
            verseCard.cardElevation = 6f
            
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

            btnSearch.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#8D6E63"))
            btnSearch.setTextColor(Color.parseColor("#FFFFFF"))
            if (btnSearch is MaterialButton) {
                (btnSearch as MaterialButton).strokeColor = ColorStateList.valueOf(Color.parseColor("#8D6E63"))
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
            verseCard.strokeColor = Color.parseColor("#D0C4B2")
            verseCard.cardElevation = 8f
            
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
        
        updateLanguageToggleUI(BibleRepository.currentTranslation)
    }

    private fun updateLanguageToggleUI(translation: BibleTranslation) {
        val isEnglishActive = translation.languageGroup == "English"
        
        btnEnglish.text = if (isEnglishActive) translation.displayName.uppercase() + " ▾" else "ENGLISH ▾"
        btnTagalog.text = if (!isEnglishActive) translation.displayName.uppercase() + " ▾" else "TAGALOG ▾"

        if (isDarkMode) {
            verseCard.backgroundTintList = null
            verseCard.setCardBackgroundColor(ColorStateList.valueOf(Color.parseColor("#1E1E1E")))
            verseCard.strokeColor = Color.parseColor("#444444")

            val nightPillColor = ColorStateList.valueOf(Color.parseColor("#8D6E63"))

            if (isEnglishActive) {
                btnEnglish.setBackgroundResource(R.drawable.bg_pill_selected)
                btnEnglish.backgroundTintList = nightPillColor
                btnEnglish.setTextColor(Color.parseColor("#FFFFFF"))
                
                btnTagalog.backgroundTintList = null
                btnTagalog.setBackgroundColor(Color.TRANSPARENT)
                btnTagalog.setTextColor(Color.parseColor("#D7CCC8"))
            } else {
                btnTagalog.setBackgroundResource(R.drawable.bg_pill_selected)
                btnTagalog.backgroundTintList = nightPillColor
                btnTagalog.setTextColor(Color.parseColor("#FFFFFF"))
                
                btnEnglish.backgroundTintList = null
                btnEnglish.setBackgroundColor(Color.TRANSPARENT)
                btnEnglish.setTextColor(Color.parseColor("#D7CCC8"))
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
        if (!isTtsReady || tts == null) {
            showCenteredToast("TTS engine is not ready or installed on this device.")
            
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
            showCenteredToast("Language data missing for speech. Defaulting to system language.")
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
                showCenteredToast("TTS Language data missing. Please check device Text-to-Speech settings.")
            } else {
                isTtsReady = true
            }

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}

                override fun onDone(utteranceId: String?) {
                    if (isAutoPlaying) {
                        runOnUiThread {
                            advanceToNextVerseOrSection()
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
            showCenteredToast("Failed to initialize Text-to-Speech engine.")
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

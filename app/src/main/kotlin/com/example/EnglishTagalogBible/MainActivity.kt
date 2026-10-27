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
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.ProgressBar
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
import android.widget.LinearLayout
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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var tvVerseText: TextView
    private lateinit var tvVerseRef: TextView
    private lateinit var btnRefresh: Button
    private lateinit var btnSearch: MaterialButton
    private lateinit var btnCopy: MaterialButton
    private lateinit var btnShare: MaterialButton
    private lateinit var btnSpeak: MaterialButton
    private lateinit var btnSelectManual: MaterialButton
    private lateinit var btnSettings: MaterialButton
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

    private enum class ThemeMode(val fullTranslationName: String) {
        SEPIA("🟤 Classic Sepia"),
        CHARCOAL("⚫ Charcoal Contrast"),
        NAVY("🔵 Midnight Navy"),
        OLIVE("🟢 Olive Grace"),
        SUNSET("🟠 Warm Sunset")
    }
    private var currentTheme = ThemeMode.SEPIA

    private data class ThemeConfig(
        val statusBarColor: Int,
        val backgroundRes: Int?,
        val backgroundColor: Int?,
        val cardBgColor: Int,
        val cardStrokeColor: Int,
        val textColor: Int,
        val refColor: Int,
        val accentColor: Int,
        val primaryButtonTint: Int,
        val primaryButtonTextColor: Int,
        val dialogBgColor: Int,
        val dialogStrokeColor: Int
    )

    private enum class AutoPlayMode {
        CHAPTER, BOOK, ALL_BOOKS
    }
    private var autoPlayMode = AutoPlayMode.CHAPTER

    companion object {
        private const val KEY_VERSE_TEXT = "key_verse_text"
        private const val KEY_VERSE_REF = "key_verse_ref"
        private const val PREFS_NAME = "BibleAppPrefs"
        private const val KEY_SAVED_THEME = "saved_theme_mode"
        private const val KEY_HAS_SEEN_WELCOME = "key_has_seen_welcome_v14"
        
        private val crossLanguageReverseMap = mapOf(
            "exodo" to "Exodus", "levitico" to "Leviticus", "mga bilang" to "Numbers",
            "deuteronomio" to "Deuteronomy", "josue" to "Joshua", "mga hukom" to "Judges",
            "mga hari" to "Kings", "cronica" to "Chronicles", "nehemias" to "Nehemiah",
            "ester" to "Esther", "mga awit" to "Psalm", "mga kawikaan" to "Proverbs",
            "isaias" to "Isaiah", "jeremias" to "Jeremiah", "mga panaghoy" to "Lamentations",
            "oseas" to "Hosea", "obadias" to "Obadiah", "jonas" to "Jonah",
            "mikas" to "Micah", "habacuc" to "Habakkuk", "zefanias" to "Zephaniah",
            "hagai" to "Haggai", "zacarias" to "Zechariah", "malakias" to "Malachi",
            "mateo" to "Matthew", "marcos" to "Mark", "lucas" to "Luke", "juan" to "John",
            "mga gawa" to "Acts", "roma" to "Romans", "corinto" to "Corinthians",
            "galacia" to "Galatians", "efeso" to "Ephesians", "filipos" to "Philippians",
            "colosas" to "Colossians", "tesalonica" to "Thessalonica", "timoteo" to "Timothy",
            "tito" to "Titus", "filemon" to "Philemon", "hebrews" to "Hebrews",
            "santiago" to "James", "pedro" to "Peter", "judas" to "Jude", "pahayag" to "Revelation"
        )
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
        btnSettings = findViewById(R.id.btnSettings)
        
        btnPrev = findViewById(R.id.btnPrev)
        btnNext = findViewById(R.id.btnNext)
        btnAutoPlay = findViewById(R.id.btnAutoPlay)
        btnAbout = findViewById(R.id.btnAbout)
        verseCard = findViewById(R.id.verseCard)

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)    
        val savedThemeName = prefs.getString(KEY_SAVED_THEME, ThemeMode.SEPIA.name)
        currentTheme = try {
            ThemeMode.valueOf(savedThemeName ?: ThemeMode.SEPIA.name)
        } catch (e: Exception) {
            ThemeMode.SEPIA
        }

        val languageToggleInclude = findViewById<android.view.View>(R.id.languageTogglePill)
        btnEnglish = languageToggleInclude.findViewById(R.id.btnKjv)
        btnTagalog = languageToggleInclude.findViewById(R.id.btnTagalog)

        initTTS()

        btnEnglish.setOnClickListener { stopAutoPlay(); handleLanguageSelection("English") }
        btnTagalog.setOnClickListener { stopAutoPlay(); handleLanguageSelection("Tagalog") }

        val incomingText = intent.getStringExtra("verse_text")
        val incomingRef = intent.getStringExtra("verse_ref")

        // Apply theme immediately to avoid white flash
        applyTheme()

        lifecycleScope.launch(Dispatchers.IO) {
            val savedTranslation = try {
                BibleRepository.getSavedTranslation(this@MainActivity)
            } catch (e: Exception) {
                BibleTranslation.KJV
            }

            try {
                BibleRepository.loadVerses(this@MainActivity, savedTranslation)
            } catch (e: Exception) {
                BibleRepository.setTranslation(this@MainActivity, BibleTranslation.KJV)
                BibleRepository.loadVerses(this@MainActivity, BibleTranslation.KJV, forceReload = true)
            }

            withContext(Dispatchers.Main) {
                updateLanguageToggleUI(BibleRepository.currentTranslation)

                if (incomingText != null && incomingRef != null) {
                    currentVerse = BibleVerse(text = incomingText, reference = incomingRef)
                    tvVerseText.text = currentVerse?.text
                    tvVerseRef.text = "— ${currentVerse?.reference}"
                    currentVerse?.let { updateNavigationButtonLabels(it) }
                } else if (savedInstanceState != null) {
                    val savedText = savedInstanceState.getString(KEY_VERSE_TEXT)
                    val savedRef = savedInstanceState.getString(KEY_VERSE_REF)
                    if (savedText != null && savedRef != null) {
                        val cleanRef = savedRef.removePrefix("— ").trim()
                        currentVerse = BibleVerse(text = savedText, reference = cleanRef)
                        tvVerseText.text = currentVerse?.text
                        tvVerseRef.text = "— ${currentVerse?.reference}"
                        currentVerse?.let { updateNavigationButtonLabels(it) }
                    } else {
                        loadInitialVerse()
                    }
                } else {
                    loadInitialVerse()
                }
            }
        }

        btnRefresh.setOnClickListener { stopAutoPlay(); loadRandomVerse() }
        btnSearch.setOnClickListener { stopAutoPlay(); showSearchQueryDialog() }
        btnSelectManual.setOnClickListener { stopAutoPlay(); showBookSelectionDialog() }

        btnPrev.setOnClickListener {
            currentVerse?.let { verse ->
                val prevVerse = BibleRepository.getPreviousVerse(verse, this) 
                    ?: BibleRepository.getPreviousChapterLastVerse(verse, this)

                if (prevVerse != null) {
                    displayVerse(prevVerse)
                    if (isAutoPlaying) {
                        speakVerse(prevVerse)
                    } else {
                        tts?.stop()
                    }
                } else {
                    showCenteredToast("Beginning of Bible reached.")
                }
            }
        }

        btnNext.setOnClickListener {
            currentVerse?.let { verse ->
                val nextVerse = BibleRepository.getNextVerse(verse, this) 
                    ?: BibleRepository.getNextChapterFirstVerse(verse, this)

                if (nextVerse != null) {
                    displayVerse(nextVerse)
                    if (isAutoPlaying) {
                        speakVerse(nextVerse)
                    } else {
                        tts?.stop()
                    }
                } else {
                    showCenteredToast("End of Bible reached.")
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
                if (checkAndPromptTTSAvailability()) {
                    stopAutoPlay()
                    speakVerse(verse)
                }
            }
        }

        btnSettings.setOnClickListener { showSettingsDialog() }
        btnAbout.setOnClickListener { showAboutDialog() }

        LockScreenVerseService.showLockScreenNotification(this)
        scheduleDailyVerseNotification()

        showWelcomeIntroDialog()
    }

    private fun showWelcomeIntroDialog() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val hasSeenWelcome = prefs.getBoolean(KEY_HAS_SEEN_WELCOME, false)

        if (!hasSeenWelcome) {
            prefs.edit().putBoolean(KEY_HAS_SEEN_WELCOME, true).apply()

            val isDark = currentTheme != ThemeMode.SEPIA
            val textColor = if (isDark) Color.parseColor("#FFFFFF") else Color.parseColor("#2D241E")

            val introMessage = "English & Tagalog Bible version 1.4\n" +
                    "App Developed by Poy Lizardo\n" +
                    "Copyright © 2026. All Rights Reserved.\n\n" +
                    "Experience daily inspiration in both English and Tagalog with powerful features:\n\n" +
                    "• 🔀 Random Daily Inspiration: Instantly discover uplifting new verses with a single tap of the randomizer.\n" +
                    "• 🌐 Dual-Language & Versions: Easily toggle between English and Tagalog, with 16+ downloadable public domain translations.\n" +
                    "• 🔊 Audio & Auto-Play: Listen hands-free with single-verse reading or continuous chapter and book playback.\n" +
                    "• 🔍 Smart Search & Browsing: Find specific passages instantly or navigate through Old and New Testaments seamlessly.\n" +
                    "• 🖼️ Share & Customize: Copy verses, export custom image cards, and personalize your reading with 5 gorgeous themes.\n\n" +
                    "Tap 'Get Started' to begin your journey. To God be the glory~"

            val dialog = AlertDialog.Builder(this)
                .setTitle("Welcome to English and Tagalog Bible")
                .setMessage(introMessage)
                .setPositiveButton("Get Started") { dialogInterface, _ ->
                    dialogInterface.dismiss()
                }
                .create()

            applyThemedDialogStyle(dialog)

            dialog.findViewById<TextView>(android.R.id.message)?.apply {
                textSize = 14f
                setTextColor(textColor)
                setLineSpacing(0f, 1.3f)
            }
        }
    }

    private fun initTTS() {
        tts = TextToSpeech(this, this)
    }

    private fun checkAndPromptTTSAvailability(): Boolean {
        val ttsEngine = tts
        val isLanguageAvailable = try {
            if (ttsEngine != null) {
                val currentLang = BibleRepository.currentTranslation.languageGroup
                val targetLocale = if (currentLang == "Tagalog") Locale("fil", "PH") else Locale.US
                val result = ttsEngine.isLanguageAvailable(targetLocale)
                result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
            } else false
        } catch (e: Exception) {
            false
        }

        if (!isTtsReady || ttsEngine == null || !isLanguageAvailable) {
            stopAutoPlay()
            showTTSSettingsRequiredDialog()
            return false
        }
        return true
    }

    private fun showTTSSettingsRequiredDialog() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Text-to-Speech Settings")
            .setMessage("To listen to verses, please ensure your Text-to-Speech engine is installed and set up.\n\nFor the best experience, we recommend:\n• English (US) — Voice V\n• Filipino (Philippines) — Voice IV")
            .setPositiveButton("Open TTS Settings") { _, _ -> openSystemTTSSettingsWithIntro() }
            .setNeutralButton("Play Store") { _, _ -> promptInstallGoogleTTS() }
            .setNegativeButton("Cancel", null)
            .create()

        applyThemedDialogStyle(dialog)
        
        val isDark = currentTheme != ThemeMode.SEPIA
        dialog.findViewById<TextView>(android.R.id.message)?.apply {
            textSize = 14f
            setTextColor(if (isDark) Color.parseColor("#FFFFFF") else Color.parseColor("#2D241E"))
        }
    }

    private fun scheduleDailyVerseNotification() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isPrefEnabled = prefs.getBoolean("key_notifications_enabled", true)
        val isSystemNotificationEnabled = androidx.core.app.NotificationManagerCompat.from(this).areNotificationsEnabled()    
        val workManager = WorkManager.getInstance(this)
        if (!isPrefEnabled || !isSystemNotificationEnabled) {
            workManager.cancelUniqueWork("DailyVerseWork")
            workManager.cancelAllWorkByTag("BibleNotificationSlot")
            return
        }    
        val dailyWorkRequest = PeriodicWorkRequestBuilder<DailyVerseWorker>(24, TimeUnit.HOURS).build()
        workManager.enqueueUniquePeriodicWork("DailyVerseWork", ExistingPeriodicWorkPolicy.KEEP, dailyWorkRequest)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        currentVerse?.let {
            outState.putString(KEY_VERSE_TEXT, it.text)
            outState.putString(KEY_VERSE_REF, it.reference)
        }
    }

    private fun getThemeConfig(mode: ThemeMode): ThemeConfig {
        return when (mode) {
            ThemeMode.SEPIA -> ThemeConfig(
                statusBarColor = Color.parseColor("#E8E2D5"),
                backgroundRes = R.drawable.bg_gradient,
                backgroundColor = null,
                cardBgColor = Color.parseColor("#FFFFFF"),
                cardStrokeColor = Color.parseColor("#D0C4B2"),
                textColor = Color.parseColor("#2D241E"),
                refColor = Color.parseColor("#8C6D53"),
                accentColor = Color.parseColor("#6E472C"),
                primaryButtonTint = Color.parseColor("#6E472C"),
                primaryButtonTextColor = Color.WHITE,
                dialogBgColor = Color.parseColor("#FFFFFF"),
                dialogStrokeColor = Color.parseColor("#E8E2D5")
            )
            ThemeMode.CHARCOAL -> ThemeConfig(
                statusBarColor = Color.parseColor("#121212"),
                backgroundRes = null,
                backgroundColor = Color.parseColor("#121212"),
                cardBgColor = Color.parseColor("#1E1E1E"),
                cardStrokeColor = Color.parseColor("#444444"),
                textColor = Color.parseColor("#FFFFFF"),
                refColor = Color.parseColor("#FFB74D"),
                accentColor = Color.parseColor("#FFB74D"),
                primaryButtonTint = Color.parseColor("#333333"),
                primaryButtonTextColor = Color.parseColor("#FFB74D"),
                dialogBgColor = Color.parseColor("#1E1E1E"),
                dialogStrokeColor = Color.parseColor("#444444")
            )
            ThemeMode.NAVY -> ThemeConfig(
                statusBarColor = Color.parseColor("#0B0F19"),
                backgroundRes = null,
                backgroundColor = Color.parseColor("#0B0F19"),
                cardBgColor = Color.parseColor("#121826"),
                cardStrokeColor = Color.parseColor("#283593"),
                textColor = Color.parseColor("#FFFFFF"),
                refColor = Color.parseColor("#9FA8DA"),
                accentColor = Color.parseColor("#7986CB"),
                primaryButtonTint = Color.parseColor("#3F51B5"),
                primaryButtonTextColor = Color.parseColor("#FFFFFF"),
                dialogBgColor = Color.parseColor("#121826"),
                dialogStrokeColor = Color.parseColor("#283593")
            )
            ThemeMode.OLIVE -> ThemeConfig(
                statusBarColor = Color.parseColor("#111813"),
                backgroundRes = null,
                backgroundColor = Color.parseColor("#111813"),
                cardBgColor = Color.parseColor("#1A211C"),
                cardStrokeColor = Color.parseColor("#2E7D32"),
                textColor = Color.parseColor("#FFFFFF"),
                refColor = Color.parseColor("#A5D6A7"),
                accentColor = Color.parseColor("#81C784"),
                primaryButtonTint = Color.parseColor("#2E7D32"),
                primaryButtonTextColor = Color.parseColor("#FFFFFF"),
                dialogBgColor = Color.parseColor("#1A211C"),
                dialogStrokeColor = Color.parseColor("#2E7D32")
            )
            ThemeMode.SUNSET -> ThemeConfig(
                statusBarColor = Color.parseColor("#2D1A12"),
                backgroundRes = null,
                backgroundColor = Color.parseColor("#1B0F0B"),
                cardBgColor = Color.parseColor("#2D1A12"),
                cardStrokeColor = Color.parseColor("#D77A61"),
                textColor = Color.parseColor("#FFE5D9"),
                refColor = Color.parseColor("#FFB74D"),
                accentColor = Color.parseColor("#FFB74D"),
                primaryButtonTint = Color.parseColor("#A34828"),
                primaryButtonTextColor = Color.parseColor("#FFFFFF"),
                dialogBgColor = Color.parseColor("#221A15"),
                dialogStrokeColor = Color.parseColor("#D77A61")
            )
        }
    }

    private fun configureTTSVoiceForCurrentLanguage() {
        val ttsEngine = tts ?: return
        if (!isTtsReady) return

        val currentLanguage = BibleRepository.currentTranslation.languageGroup
        val targetLocale = if (currentLanguage == "Tagalog") {
            Locale("fil", "PH")
        } else {
            Locale.US
        }

        try {
            ttsEngine.language = targetLocale
        } catch (_: Exception) {}

        if (currentLanguage == "Tagalog") {
            ttsEngine.setSpeechRate(0.88f)
        } else {
            ttsEngine.setSpeechRate(0.95f)
        }
        ttsEngine.setPitch(1.0f)
    }
    
    private fun openSystemTTSSettingsWithIntro() {
        val isDark = currentTheme != ThemeMode.SEPIA
        val primaryColor = if (isDark) Color.parseColor("#E8D5C4") else Color.parseColor("#6E472C")
        val textColor = if (isDark) Color.parseColor("#FFFFFF") else Color.parseColor("#2D241E")

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 20)
            gravity = Gravity.CENTER
        }

        val progressBar = ProgressBar(this).apply {
            isIndeterminate = true
            indeterminateTintList = ColorStateList.valueOf(primaryColor)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 30
            }
        }

        val messageText = TextView(this).apply {
            text = "Opening Text-to-Speech Settings...\n\nConfigure your preferred voice engine (e.g. English US or Filipino) for daily verse listening."
            textSize = 14f
            setTextColor(textColor)
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.2f)
        }

        layout.addView(progressBar)
        layout.addView(messageText)

        val dialog = AlertDialog.Builder(this)
            .setTitle("🎙️ Speech Engine Setup")
            .setView(layout)
            .setCancelable(false)
            .create()

        applyThemedDialogStyle(dialog)
        dialog.show()

        Handler(Looper.getMainLooper()).postDelayed({
            if (!isFinishing && !isDestroyed) {
                dialog.dismiss()
                openSystemTTSSettings()
            }
        }, 1200)
    }

    private fun openSystemTTSSettings() {
        val isTtsPackageInstalled = try {
            packageManager.getPackageInfo("com.google.android.tts", 0)
            true
        } catch (e: Exception) {
            false
        }
    
        if (!isTtsPackageInstalled) {
            showTTSSettingsRequiredDialog()
            return
        }
    
        var opened = false

        val intentsToTry = listOf(
            Intent("com.google.android.tts.settings.VoiceDataActivity"),
            Intent("com.android.settings.TTS_SETTINGS"),
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        )
    
        for (intent in intentsToTry) {
            try {
                startActivity(intent)
                opened = true
                break
            } catch (_: Exception) {}
        }
    
        if (!opened) {
            showCenteredToast("Unable to open Text-to-Speech settings.")
        }
    }

    private fun showSettingsDialog() {
        val isSystemNotificationEnabled = androidx.core.app.NotificationManagerCompat.from(this).areNotificationsEnabled()
        
        val notificationToggleLabel = if (isSystemNotificationEnabled) {
            "🔔 Notifications: ON (System Settings)"
        } else {
            "🔕 Notifications: OFF (System Settings)"
        }
    
        val options = arrayOf(
            "🎨 Change Reading Theme",
            "🔊 Text-to-Speech Settings (Google TTS)",
            notificationToggleLabel,
            "🚪 Exit App",
            "Close"
        )
    
        var dialog: AlertDialog? = null
        dialog = AlertDialog.Builder(this)
            .setTitle("Settings")
            .setAdapter(createCenteredListAdapter(options)) { _, index ->
                when (index) {
                    0 -> showThemeSelectionDialog()
                    1 -> openSystemTTSSettingsWithIntro()
                    2 -> {
                        try {
                            val intent = Intent().apply {
                                action = android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS
                                putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName)
                            }
                            startActivity(intent)
                        } catch (e: Exception) {
                            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = android.net.Uri.parse("package:$packageName")
                            }
                            startActivity(intent)
                        }
                        dialog?.dismiss()
                    }
                    3 -> exitApp()
                    4 -> dialog?.dismiss()
                }
            }
            .create()
    
        applyThemedDialogStyle(dialog)
    }
    
    private fun showThemeSelectionDialog() {
        val themes = ThemeMode.values()
        val options = themes.map { it.fullTranslationName }.toTypedArray()
        val dialog = AlertDialog.Builder(this)
            .setTitle("Select Reading Theme")
            .setAdapter(createCenteredListAdapter(options)) { _, which ->
                currentTheme = themes[which]
                val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().putString(KEY_SAVED_THEME, currentTheme.name).apply()
                applyTheme()
            }
            .create()
        applyThemedDialogStyle(dialog)
    }

    private fun exitApp() {
        stopAutoPlay()
        tts?.stop()
        tts?.shutdown()
        finishAndRemoveTask()
        android.os.Process.killProcess(android.os.Process.myPid())
        kotlin.system.exitProcess(0)
    }

    private fun applyThemedDialogStyle(dialog: AlertDialog) {
        dialog.show()
        val config = getThemeConfig(currentTheme)
        val isDark = currentTheme != ThemeMode.SEPIA

        dialog.findViewById<TextView>(androidx.appcompat.R.id.alertTitle)?.apply {
            gravity = android.view.Gravity.CENTER
            textAlignment = android.view.View.TEXT_ALIGNMENT_CENTER
            setTextColor(if (isDark) Color.parseColor("#FFFFFF") else Color.parseColor("#2D241E"))
        }

        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(config.accentColor)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(config.accentColor)

        dialog.window?.decorView?.background = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = 24f
            setColor(config.dialogBgColor)
            setStroke(2, config.dialogStrokeColor)
        }
    }

    private fun createCenteredListAdapter(items: Array<String>): android.widget.ArrayAdapter<String> {
        val isDark = currentTheme != ThemeMode.SEPIA
        val textColor = if (isDark) Color.parseColor("#FFFFFF") else Color.parseColor("#2D241E")
        val accentColor = getThemeConfig(currentTheme).accentColor

        return object : android.widget.ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, items) {
            override fun isEnabled(position: Int): Boolean {
                val item = items[position]
                return !item.startsWith("—")
            }

            override fun getView(position: Int, convertView: android.view.View?, parent: ViewGroup): android.view.View {
                val view = super.getView(position, convertView, parent)
                val text = items[position]
                (view as? TextView)?.apply {
                    this.gravity = android.view.Gravity.CENTER
                    this.textAlignment = android.view.View.TEXT_ALIGNMENT_CENTER
                    this.setPadding(0, 28, 0, 28)
                    
                    if (text.startsWith("—")) {
                        this.setTextColor(accentColor)
                        this.setTypeface(null, android.graphics.Typeface.BOLD)
                    } else if (text.contains("📜") || text.contains("✝️") || text.contains("📖") || text.contains("🗑️") ||
                        text.contains("✅") || text.contains("⬇️") || text.contains("🌐") || text.contains("🖼️") || 
                        text.contains("📝") || text.contains("🟤") || text.contains("⚫") || text.contains("Close") || 
                        text.contains("🔵") || text.contains("🟢") || text.contains("🟠") || text.contains("▶ Auto-Play ") || 
                        text.contains("🔊") || text.contains("⚙️") || text.contains("🎨") || text.contains("🚪") || 
                        text.contains("🔔") || text.contains("🔕") ||
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
                ThemeMode.SEPIA -> intArrayOf(Color.parseColor("#FBF8F3"), Color.parseColor("#EFE6D5"), Color.parseColor("#E2D4BF"))
                ThemeMode.CHARCOAL -> intArrayOf(Color.parseColor("#121212"), Color.parseColor("#1E1E1E"), Color.parseColor("#0A0A0A"))
                ThemeMode.NAVY -> intArrayOf(Color.parseColor("#0F172A"), Color.parseColor("#1E293B"), Color.parseColor("#090D16"))
                ThemeMode.OLIVE -> intArrayOf(Color.parseColor("#132219"), Color.parseColor("#1B3324"), Color.parseColor("#0A140E"))
                ThemeMode.SUNSET -> intArrayOf(Color.parseColor("#2D1A12"), Color.parseColor("#42251B"), Color.parseColor("#1B0F0B"))
            }

            val gradient = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), colors, null, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), Paint().apply { shader = gradient })

            val borderColor = getThemeConfig(currentTheme).cardStrokeColor
            canvas.drawRoundRect(60f, 60f, (width - 60).toFloat(), (height - 60).toFloat(), 32f, 32f, Paint().apply { color = borderColor; style = Paint.Style.STROKE; strokeWidth = 6f })

            val textColor = getThemeConfig(currentTheme).textColor
            val refColor = getThemeConfig(currentTheme).refColor

            val textPaint = android.text.TextPaint().apply {
                color = textColor
                textSize = 48f
                typeface = Typeface.create("serif", Typeface.ITALIC)
                isAntiAlias = true
            }

            val textWidth = width - 240
            val staticLayout = android.text.StaticLayout.Builder.obtain(verseText, 0, verseText.length, textPaint, textWidth)
                .setAlignment(android.text.Layout.Alignment.ALIGN_CENTER)
                .setLineSpacing(0f, 1.2f)
                .build()

            val totalTextHeight = staticLayout.height
            val startY = (height - totalTextHeight) / 2f - 40f

            canvas.save()
            canvas.translate(120f, startY)
            staticLayout.draw(canvas)
            canvas.restore()

            val refPaint = Paint().apply {
                color = refColor
                textSize = 38f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(verseRef, (width / 2).toFloat(), startY + totalTextHeight + 90f, refPaint)

            val watermarkPaint = Paint().apply {
                color = if (currentTheme == ThemeMode.SEPIA) Color.parseColor("#8C6D53") else Color.parseColor("#888888")
                textSize = 28f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("English & Tagalog Bible App", (width / 2).toFloat(), height - 100f, watermarkPaint)

            val cachePath = File(cacheDir, "images").apply { mkdirs() }
            val imageFile = File(cachePath, "verse_card_${System.currentTimeMillis()}.png")
            
            FileOutputStream(imageFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.flush()
            }

            val imageUri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", imageFile)
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

        val currentGroup = BibleRepository.currentTranslation.languageGroup
        val availableTranslations = BibleTranslation.values()
            .filter { it.languageGroup == currentGroup && it.isAvailable(this) }
            
        val translationNames = availableTranslations.map { it.fullTranslationName }.toTypedArray()
        
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
            text = "${availableTranslations.getOrNull(selectedTranslationIndex)?.fullTranslationName ?: "Select"} ▾"
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
                        text = "${availableTranslations[which].fullTranslationName} ▾"
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
            .setTitle("Search Bible 🔎")
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
        val isDark = currentTheme != ThemeMode.SEPIA
        val primaryColor = if (isDark) Color.parseColor("#E8D5C4") else Color.parseColor("#6E472C")
        val textColor = if (isDark) Color.parseColor("#FFFFFF") else Color.parseColor("#2D241E")

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 20)
            gravity = Gravity.CENTER
        }

        val progressBar = ProgressBar(this).apply {
            isIndeterminate = true
            indeterminateTintList = ColorStateList.valueOf(primaryColor)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 30
            }
        }

        val messageText = TextView(this).apply {
            text = "Searching Bible for \"$query\"..."
            textSize = 14f
            setTextColor(textColor)
            gravity = Gravity.CENTER
        }

        layout.addView(progressBar)
        layout.addView(messageText)

        val loadingDialog = AlertDialog.Builder(this)
            .setTitle("🔎 Searching Bible")
            .setView(layout)
            .setCancelable(false)
            .create()

        applyThemedDialogStyle(loadingDialog)
        loadingDialog.show()

        lifecycleScope.launch(Dispatchers.IO) {
            BibleRepository.loadVerses(this@MainActivity, targetTranslation)
            val matchingVerses = mutableListOf<BibleVerse>()
            val lowercaseQuery = query.lowercase()
    
            val allBooks = BibleRepository.getAvailableBooks(this@MainActivity)
            for (book in allBooks) {
                val chapters = BibleRepository.getChaptersForBook(book, this@MainActivity)
                for (chapter in chapters) {
                    val verses = BibleRepository.getVerseObjectsForChapter(chapter, this@MainActivity)
                    for (verse in verses) {
                        if (verse.text.lowercase().contains(lowercaseQuery) || verse.reference.lowercase().contains(lowercaseQuery)) {
                            matchingVerses.add(verse)
                        }
                    }
                }
            }
    
            withContext(Dispatchers.Main) {
                try {
                    loadingDialog.dismiss()
                } catch (_: Exception) {}

                if (matchingVerses.isEmpty()) {
                    showCenteredToast("No matching verses found for \"$query\".")
                } else {
                    showSearchResultsDialog(query, matchingVerses, targetTranslation)
                }
            }
        }
    }

    private fun showSearchResultsDialog(
        query: String,
        allResults: List<BibleVerse>,
        targetTranslation: BibleTranslation,
        initialPage: Int = 0
    ) {
        val itemsPerPage = 20
        var currentPage = initialPage
        val totalPages = (allResults.size + itemsPerPage - 1) / itemsPerPage
    
        val isDark = currentTheme != ThemeMode.SEPIA
        val textColor = if (isDark) Color.parseColor("#FFFFFF") else Color.parseColor("#2D241E")
        val accentColor = getThemeConfig(currentTheme).accentColor
        val secondaryTextColor = if (isDark) Color.parseColor("#B0BEC5") else Color.parseColor("#795548")
    
        val rootContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 16)
        }
    
        val insightCard = TextView(this).apply {
            text = "📖 Bible Insight: \"$query\" appears in ${allResults.size} verses across ${targetTranslation.fullTranslationName}."
            setTextColor(accentColor)
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(16, 12, 16, 16)
            setBackgroundColor(if (isDark) Color.parseColor("#1E293B") else Color.parseColor("#F5F0EB"))
        }
    
        val listView = android.widget.ListView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1.0f
            ).apply {
                topMargin = 12
                bottomMargin = 12
            }
            divider = null
        }
    
        fun getPageItems(page: Int): List<BibleVerse> {
            val startIndex = page * itemsPerPage
            val endIndex = minOf(startIndex + itemsPerPage, allResults.size)
            return allResults.subList(startIndex, endIndex)
        }
    
        fun updateAdapter(page: Int) {
            val pageVerses = getPageItems(page)
            val adapter = object : android.widget.ArrayAdapter<BibleVerse>(
                this,
                android.R.layout.simple_list_item_1,
                pageVerses
            ) {
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
                        this.gravity = Gravity.CENTER
                        this.textAlignment = android.view.View.TEXT_ALIGNMENT_CENTER
                        this.setPadding(12, 24, 12, 24)
                    }
                    return view
                }
            }
            listView.adapter = adapter
        }
    
        updateAdapter(currentPage)
    
        val paginationLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 4)
        }
    
        val btnPrevPage = Button(this).apply {
            text = "‹"
            textSize = 20f
            setTextColor(accentColor)
            setBackgroundColor(Color.TRANSPARENT)
            isEnabled = currentPage > 0
        }
    
        val pageIndicatorText = TextView(this).apply {
            text = "Page ${currentPage + 1} of $totalPages"
            setTextColor(secondaryTextColor)
            textSize = 13f
            setPadding(32, 0, 32, 0)
            gravity = Gravity.CENTER
        }
    
        val btnNextPage = Button(this).apply {
            text = "›"
            textSize = 20f
            setTextColor(accentColor)
            setBackgroundColor(Color.TRANSPARENT)
            isEnabled = currentPage < totalPages - 1
        }
    
        btnPrevPage.setOnClickListener {
            if (currentPage > 0) {
                currentPage--
                updateAdapter(currentPage)
                pageIndicatorText.text = "Page ${currentPage + 1} of $totalPages"
                btnPrevPage.isEnabled = currentPage > 0
                btnNextPage.isEnabled = currentPage < totalPages - 1
                listView.setSelection(0)
            }
        }
    
        btnNextPage.setOnClickListener {
            if (currentPage < totalPages - 1) {
                currentPage++
                updateAdapter(currentPage)
                pageIndicatorText.text = "Page ${currentPage + 1} of $totalPages"
                btnPrevPage.isEnabled = currentPage > 0
                btnNextPage.isEnabled = currentPage < totalPages - 1
                listView.setSelection(0)
            }
        }
    
        paginationLayout.addView(btnPrevPage)
        paginationLayout.addView(pageIndicatorText)
        paginationLayout.addView(btnNextPage)
    
        rootContainer.addView(insightCard)
        rootContainer.addView(listView)
        rootContainer.addView(paginationLayout)
    
        var dialog: AlertDialog? = null
    
        listView.setOnItemClickListener { _, _, position, _ ->
            val selectedVerse = getPageItems(currentPage)[position]
            BibleRepository.setTranslation(this, targetTranslation)
            updateLanguageToggleUI(targetTranslation)
            displayVerse(selectedVerse)
            dialog?.dismiss()
        }
    
        dialog = AlertDialog.Builder(this)
            .setTitle("Search Results - ${targetTranslation.fullTranslationName}")
            .setView(rootContainer)
            .setNegativeButton("Close", null)
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

        val options = removableList.map { "🗑️ Delete ${it.fullTranslationName}" }.toTypedArray()

        val dialog = AlertDialog.Builder(this)
            .setTitle("Remove Translation")
            .setAdapter(createCenteredListAdapter(options)) { _, index ->
                val toDelete = removableList[index]
                BibleManager.removeTranslationPackage(this, toDelete,
                    onSuccess = {
                        runOnUiThread {
                            showCenteredToast("${toDelete.fullTranslationName} removed.")
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
        installedTranslations.forEach {
            val badge = if (it.isBuiltIn) "✅ (Built-in)" else "✅ (Installed)"
            val activeMark = if (it == BibleRepository.currentTranslation) " • Active" else ""
            listItems.add("${it.fullTranslationName} $badge$activeMark")
        }

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
                    val selected = installedTranslations[index]
                    switchToTranslation(selected)
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

        val options = downloadableList.map { "${it.fullTranslationName} ⬇️ (Download)" }.toTypedArray()

        val dialog = AlertDialog.Builder(this)
            .setTitle("Download $groupName Translations")
            .setAdapter(createCenteredListAdapter(options)) { _, index ->
                val selected = downloadableList[index]
                if (!BibleManager.isNetworkAvailable(this)) {
                    showCenteredToast("No internet connection available.")
                    return@setAdapter
                }
                showCenteredToast("Downloading ${selected.fullTranslationName}...")
                BibleManager.downloadTranslationPackage(
                    context = this,
                    translation = selected,
                    onProgress = {},
                    onSuccess = { 
                        runOnUiThread { 
                            showCenteredToast("${selected.fullTranslationName} installed!") 
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
        val currentVerseObj = currentVerse
        val currentRef = currentVerseObj?.reference?.substringBefore(" (")?.trim()
        val previousTranslation = BibleRepository.currentTranslation

        lifecycleScope.launch(Dispatchers.IO) {
            BibleRepository.setTranslation(this@MainActivity, target)
            BibleRepository.loadVerses(this@MainActivity, target, forceReload = true)

            withContext(Dispatchers.Main) {
                if (currentRef != null) {
                    var translatedVerse = BibleRepository.getVerseByBookChapterAndVerse(currentRef, this@MainActivity)
                    
                    if (translatedVerse == null && currentVerseObj != null) {
                        val cleanRef = currentVerseObj.reference.substringBefore(" (").trim()
                        val colonIdx = cleanRef.lastIndexOf(':')
                        if (colonIdx != -1) {
                            val bookAndChapter = cleanRef.substring(0, colonIdx).trim()
                            val verseNum = cleanRef.substring(colonIdx)
                            val lastSpace = bookAndChapter.lastIndexOf(' ')
                            if (lastSpace != -1) {
                                val bookPart = bookAndChapter.substring(0, lastSpace).trim()
                                val chapterPart = bookAndChapter.substring(lastSpace + 1).trim()
                                
                                val englishBookName = crossLanguageReverseMap[bookPart.lowercase()] ?: bookPart
                                val mappedRef = "$englishBookName $chapterPart$verseNum"
                                translatedVerse = BibleRepository.getVerseByBookChapterAndVerse(mappedRef, this@MainActivity)
                            }
                        }
                    }

                    if (translatedVerse != null) {
                        updateLanguageToggleUI(target)
                        displayVerse(translatedVerse)
                        configureTTSVoiceForCurrentLanguage()
                        
                        if (target == BibleTranslation.JPS || target == BibleTranslation.WNT) {
                            showCenteredToast("${target.displayName}: ${target.fullTranslationName}")
                        }
                    } else {
                        BibleRepository.setTranslation(this@MainActivity, previousTranslation)
                        BibleRepository.loadVerses(this@MainActivity, previousTranslation, forceReload = true)
                        updateLanguageToggleUI(previousTranslation)
                        if (currentVerseObj != null) {
                            displayVerse(currentVerseObj)
                        }
                        configureTTSVoiceForCurrentLanguage()
                        showCenteredToast("${target.displayName} (${target.fullTranslationName}) does not contain this verse.")
                    }
                } else {
                    updateLanguageToggleUI(target)
                    configureTTSVoiceForCurrentLanguage()
                    loadInitialVerse()
                }
            }
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
                "● English - Catholic Public Domain Version (2009) (CPDV)\n" +
                "● English - Darby Bible Translation (1890) (DBT)\n" +
                "● English - Douay-Rheims Bible (NT:1582)(OT:1609-1610)(Challoner'sRev:1749–1752) (DRB)\n" +
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
        
        val isDark = currentTheme != ThemeMode.SEPIA
        dialog.findViewById<TextView>(android.R.id.message)?.apply {
            textSize = 15f
            setTextColor(if (isDark) Color.parseColor("#FFFFFF") else Color.parseColor("#2D241E"))
        }
    }

    private fun showBookSelectionDialog() {
        val allBooksAlphabetical = BibleRepository.getAllBooksAlphabetical(this).toTypedArray()
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

        val categories = (categoryMap.keys + "Close").toTypedArray()

        var categoryDialog: AlertDialog? = null
        categoryDialog = AlertDialog.Builder(this)
            .setTitle("Select Category")
            .setAdapter(createCenteredListAdapter(categories)) { _, catIndex ->
                if (catIndex == categories.size - 1) {
                    categoryDialog?.dismiss()
                    return@setAdapter
                }

                val selectedCategory = categories[catIndex]
                val categoryItems = categoryMap[selectedCategory] ?: emptyArray()

                val availableBooksInTranslation = BibleRepository.getAvailableBooks(this)
                val filteredCategoryItems = categoryItems.filter { item ->
                    item.startsWith("—") || availableBooksInTranslation.any { it.equals(item, ignoreCase = true) }
                }

                val finalBookList = (filteredCategoryItems + "Close").toTypedArray()

                var bookDialog: AlertDialog? = null
                bookDialog = AlertDialog.Builder(this)
                    .setTitle(selectedCategory)
                    .setAdapter(createCenteredListAdapter(finalBookList)) { _, bookIndex ->
                        if (bookIndex == finalBookList.size - 1) {
                            bookDialog?.dismiss()
                            return@setAdapter
                        }

                        val selectedItem = finalBookList[bookIndex]
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
        val chapters = (BibleRepository.getChaptersForBook(bookName, this) + "Close").toTypedArray()
        var dialog: AlertDialog? = null
        dialog = AlertDialog.Builder(this)
            .setTitle("Select Chapter in $bookName")
            .setAdapter(createCenteredListAdapter(chapters)) { _, which ->
                if (which == chapters.size - 1) {
                    dialog?.dismiss()
                    return@setAdapter
                }
                showVerseSelectionDialog(chapters[which])
            }
            .create()
        applyThemedDialogStyle(dialog)
    }

    private fun showVerseSelectionDialog(chapterName: String) {
        val chapterVerses = BibleRepository.getVerseObjectsForChapter(chapterName, this)
        val verseTitles = (chapterVerses.map { it.reference } + "Close").toTypedArray()
        var dialog: AlertDialog? = null
        dialog = AlertDialog.Builder(this)
            .setTitle("Select Verse in $chapterName")
            .setAdapter(createCenteredListAdapter(verseTitles)) { _, which ->
                if (which == verseTitles.size - 1) {
                    dialog?.dismiss()
                    return@setAdapter
                }
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
        updateNavigationButtonLabels(verse)
    }

    private fun updateNavigationButtonLabels(verse: BibleVerse) {
        val nextVerse = BibleRepository.getNextVerse(verse, this)
        val prevVerse = BibleRepository.getPreviousVerse(verse, this)

        if (nextVerse != null) {
            btnNext.text = "Next Verse ►"
        } else {
            val nextChapterVerse = BibleRepository.getNextChapterFirstVerse(verse, this)
            if (nextChapterVerse != null) {
                val currentBook = extractBookNameFromRef(verse.reference)
                val targetBook = extractBookNameFromRef(nextChapterVerse.reference)
                if (currentBook.equals(targetBook, ignoreCase = true)) {
                    btnNext.text = "Next Chapter ►"
                } else {
                    btnNext.text = "Next Book ►"
                }
            } else {
                btnNext.text = "End of Bible ►"
            }
        }

        if (prevVerse != null) {
            btnPrev.text = "◄ Prev Verse"
        } else {
            val prevChapterVerse = BibleRepository.getPreviousChapterLastVerse(verse, this)
            if (prevChapterVerse != null) {
                val currentBook = extractBookNameFromRef(verse.reference)
                val targetBook = extractBookNameFromRef(prevChapterVerse.reference)
                if (currentBook.equals(targetBook, ignoreCase = true)) {
                    btnPrev.text = "◄ Prev Chapter"
                } else {
                    btnPrev.text = "◄ Prev Book"
                }
            } else {
                btnPrev.text = "◄ Start of Bible"
            }
        }
    }

    private fun extractBookNameFromRef(reference: String): String {
        val cleanRef = reference.substringBefore(" (").trim()
        val colonIndex = cleanRef.lastIndexOf(':')
        if (colonIndex == -1) return cleanRef
        val beforeColon = cleanRef.substring(0, colonIndex)
        val lastSpaceIndex = beforeColon.lastIndexOf(' ')
        return if (lastSpaceIndex != -1) beforeColon.substring(0, lastSpaceIndex) else beforeColon
    }

    private fun loadInitialVerse() {
        val defaultRef = if (BibleRepository.currentTranslation.languageGroup == "Tagalog") "Juan 3:16" else "John 3:16"
        val defaultVerse = BibleRepository.getVerseByBookChapterAndVerse(defaultRef, this)
        
        val verseToDisplay = defaultVerse ?: BibleRepository.getRandomVerse(this)
        displayVerse(verseToDisplay)
    }

    private fun loadRandomVerse() {
        val verse = BibleRepository.getRandomVerse(this)
        displayVerse(verse)
    }
    
    private fun startAutoPlay() {
        if (!checkAndPromptTTSAvailability()) {
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

        if (!checkAndPromptTTSAvailability()) {
            stopAutoPlay()
            return
        }

        val nextVerseInChapter = BibleRepository.getNextVerse(verse, this)
        if (nextVerseInChapter != null) {
            displayVerse(nextVerseInChapter)
            speakVerse(nextVerseInChapter)
            return
        }

        if (autoPlayMode == AutoPlayMode.CHAPTER) {
            showCenteredToast("Chapter Auto-Play Completed")
            stopAutoPlay()
            return
        }

        val currentBook = BibleRepository.getAvailableBooks(this).find { book ->
            verse.reference.startsWith(book, ignoreCase = true)
        } ?: return

        val chapters = BibleRepository.getChaptersForBook(currentBook, this)
        val cleanRef = verse.reference.substringBefore(" (").trim()
        val currentChapter = if (cleanRef.contains(':')) cleanRef.substringBeforeLast(':') else cleanRef
        val currentChapterIndex = chapters.indexOf(currentChapter)

        if (currentChapterIndex != -1 && currentChapterIndex < chapters.size - 1) {
            val nextChapter = chapters[currentChapterIndex + 1]
            val firstVerseOfNextChapter = BibleRepository.getVerseObjectsForChapter(nextChapter, this).firstOrNull()
            if (firstVerseOfNextChapter != null) {
                displayVerse(firstVerseOfNextChapter)
                speakVerse(firstVerseOfNextChapter)
                return
            }
        }

        if (autoPlayMode == AutoPlayMode.BOOK) {
            showCenteredToast("Book Auto-Play Completed")
            stopAutoPlay()
            return
        }

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

    private fun applyTheme() {
        val config = getThemeConfig(currentTheme)
        val rootLayout = findViewById<ViewGroup>(android.R.id.content).getChildAt(0) ?: window.decorView

        window.statusBarColor = config.statusBarColor

        if (config.backgroundRes != null) {
            rootLayout.setBackgroundResource(config.backgroundRes)
        } else if (config.backgroundColor != null) {
            rootLayout.setBackgroundColor(config.backgroundColor)
        }

        val isDark = currentTheme != ThemeMode.SEPIA
        val cardAlphaColor = if (!isDark) {
            Color.parseColor("#E8FFFFFF")
        } else {
            when (currentTheme) {
                ThemeMode.CHARCOAL -> Color.parseColor("#D91E1E1E")
                ThemeMode.NAVY -> Color.parseColor("#D9121826")
                ThemeMode.OLIVE -> Color.parseColor("#D91A211C")
                ThemeMode.SUNSET -> Color.parseColor("#D92D1A12")
                else -> Color.parseColor("#D91E1E1E")
            }
        }

        verseCard.backgroundTintList = ColorStateList.valueOf(cardAlphaColor)
        verseCard.setCardBackgroundColor(ColorStateList.valueOf(cardAlphaColor))
        verseCard.strokeColor = config.cardStrokeColor
        verseCard.cardElevation = if (currentTheme == ThemeMode.SEPIA) 8f else 6f

        findViewById<BiblicalAnimationBackgroundView>(R.id.animatedBackgroundView)?.updateTheme(
            accentColor = config.accentColor,
            isDark = isDark
        )

        tvVerseText.setTextColor(config.textColor)
        tvVerseRef.setTextColor(config.refColor)

        btnSettings.text = "⚙️ Settings"
        btnSettings.setTextColor(config.accentColor)
        btnSettings.strokeColor = ColorStateList.valueOf(config.accentColor)
        btnSettings.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)

        val primaryHighlightButtons = listOf(btnRefresh, btnSearch)
        for (btn in primaryHighlightButtons) {
            btn.backgroundTintList = ColorStateList.valueOf(config.primaryButtonTint)
            btn.setTextColor(config.primaryButtonTextColor)
            if (btn is MaterialButton) {
                btn.strokeColor = ColorStateList.valueOf(config.primaryButtonTint)
            }
        }

        val allUtilityButtons = listOf(btnCopy, btnShare, btnSpeak, btnSelectManual, btnPrev, btnNext, btnAutoPlay, btnAbout, btnSettings)
        for (btn in allUtilityButtons) {
            btn.setTextColor(config.accentColor)
            btn.strokeColor = ColorStateList.valueOf(config.accentColor)
            btn.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
        }

        updateLanguageToggleUI(BibleRepository.currentTranslation)
    }
    
    private fun updateLanguageToggleUI(translation: BibleTranslation) {
        val isEnglishActive = translation.languageGroup == "English"
        btnEnglish.text = if (isEnglishActive) translation.fullTranslationName.uppercase() + " ▾" else "ENGLISH ▾"
        btnTagalog.text = if (!isEnglishActive) translation.fullTranslationName.uppercase() + " ▾" else "TAGALOG ▾"
    
        val isDark = currentTheme != ThemeMode.SEPIA
        val cardAlphaColor = if (!isDark) {
            Color.parseColor("#E8FFFFFF")
        } else {
            when (currentTheme) {
                ThemeMode.CHARCOAL -> Color.parseColor("#D91E1E1E")
                ThemeMode.NAVY -> Color.parseColor("#D9121826")
                ThemeMode.OLIVE -> Color.parseColor("#D91A211C")
                ThemeMode.SUNSET -> Color.parseColor("#D92D1A12")
                else -> Color.parseColor("#D91E1E1E")
            }
        }
    
        verseCard.backgroundTintList = ColorStateList.valueOf(cardAlphaColor)
        verseCard.setCardBackgroundColor(ColorStateList.valueOf(cardAlphaColor))
        verseCard.strokeColor = getThemeConfig(currentTheme).cardStrokeColor

        val pillColorHex = when (currentTheme) {
            ThemeMode.CHARCOAL -> Color.parseColor("#D97706")
            ThemeMode.NAVY -> Color.parseColor("#3F51B5")
            ThemeMode.OLIVE -> Color.parseColor("#2E7D32")
            ThemeMode.SUNSET -> Color.parseColor("#A34828")
            else -> Color.parseColor("#8D6E63")
        }
        val pillColorState = ColorStateList.valueOf(if (isDark) pillColorHex else Color.parseColor("#6E472C"))

        btnEnglish.backgroundTintList = null
        btnEnglish.setBackgroundColor(Color.TRANSPARENT)
        btnTagalog.backgroundTintList = null
        btnTagalog.setBackgroundColor(Color.TRANSPARENT)

        if (isEnglishActive) {
            btnEnglish.setBackgroundResource(R.drawable.bg_pill_selected)
            btnEnglish.backgroundTintList = pillColorState
            btnEnglish.setTextColor(Color.WHITE)
            btnTagalog.setTextColor(if (isDark) Color.parseColor("#B0BEC5") else Color.parseColor("#4A3B32"))
        } else {
            btnTagalog.setBackgroundResource(R.drawable.bg_pill_selected)
            btnTagalog.backgroundTintList = pillColorState
            btnTagalog.setTextColor(Color.WHITE)
            btnEnglish.setTextColor(if (isDark) Color.parseColor("#B0BEC5") else Color.parseColor("#4A3B32"))
        }
    }
    
    private fun promptInstallGoogleTTS() {
        try {
            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=com.google.android.tts"))
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
        } catch (e: Exception) {
            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.tts"))
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
        }
    }

    private fun speakVerse(verse: BibleVerse) {
        if (!checkAndPromptTTSAvailability()) return
        val ttsEngine = tts ?: return

        configureTTSVoiceForCurrentLanguage()

        val utteranceId = "AutoPlay_${System.currentTimeMillis()}"
        ttsEngine.speak("${verse.text}. Reference: ${verse.reference}", TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }
    
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsReady = true
            configureTTSVoiceForCurrentLanguage()
        } else {
            isTtsReady = false
        }
    }
    
    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}

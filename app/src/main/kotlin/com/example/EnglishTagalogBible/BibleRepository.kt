package com.example.EnglishTagalogBible

import android.content.Context
import org.json.JSONArray
import java.io.File
import java.io.InputStream

enum class BibleTranslation(
    val languageGroup: String,
    val displayName: String,
    val suffix: String,
    val isBuiltIn: Boolean,
    val fullTranslationName: String
) {
    AKJV("English", "English (AKJV)", "akjv", false, "American King James Version"),
    ASV("English", "English (ASV)", "asv", false, "American Standard Version (1901)"),
    BAB("English", "English (BAB)", "bab", false, "Berean Annotated Bible (Early Draft Ver.)"),
    BLB("English", "English (BLB)", "blb", false, "Berean Literal Bible (Draft Ver.)"),
    BSB("English", "English (BSB)", "bsb", false, "Berean Standard Bible (2020)"),    
    CPDV("English", "English (CPDV)", "cpdv", false, "Catholic Public Domain Version (2009)"),
    DBT("English", "English (DBT)", "dbt", false, "Darby Bible Translation (1890)"),
    DRB("English", "English (DRB)", "drb", false, "Douay-Rheims Bible (1582-1752)"),
    ERV("English", "English (ERV)", "erv", false, "English Revised Version (1885)"),
    JPS("English", "English (JPS)", "jps", false, "Jewish Publication Society's Tanakh (OT:1917)"),
    KJV("English", "English (KJV)", "kjv", true, "King James Bible: Pure Cambridge Edition (1900)"),
    MSB("English", "English (MSB)", "msb", false, "Majority Standard Bible (2022)"),
    SLT("English", "English (SLT)", "slt", false, "Smith's Literal Translation (1876)"),
    WBT("English", "English (WBT)", "wbt", false, "Webster Bible Translation (1833)"),
    WEB("English", "English (WEB)", "web", false, "World English Bible (2020)"),
    WNT("English", "English (WNT)", "wnt", false, "Weymouth New Testament (NT:1903)"),
    YLT("English", "English (YLT)", "ylt", false, "Young's Literal Translation (1862)"),
    ADB("Tagalog", "Tagalog (ADB)", "adb", true, "Ang Dating Biblia (1905)");

    fun isAvailable(context: Context): Boolean {
        if (isBuiltIn) return true
        val biblesDir = File(context.filesDir, "bibles")
        if (!biblesDir.exists()) return false
        val files = biblesDir.listFiles() ?: return false
        val cleanSuffix = suffix.lowercase()
        return files.any { 
            it.name.lowercase().endsWith("_${cleanSuffix}.json") || 
            it.name.lowercase() == "${cleanSuffix}.json" 
        }
    }
}

data class BibleVerse(
    val text: String,
    val reference: String,
    val connectedTo: String? = null,
    val connectedFrom: String? = null
)

object BibleRepository {
    private val verses = mutableListOf<BibleVerse>()
    @Volatile
    private var isLoaded = false
    
    var currentTranslation: BibleTranslation = BibleTranslation.KJV
        private set

    private val canonicalBookOrder = listOf(
        // Old Testament
        "Genesis", "Exodus", "Exodo", "Leviticus", "Levitico", "Numbers", "Mga Bilang", "Deuteronomy", "Deuteronomio",
        "Joshua", "Josue", "Judges", "Mga Hukom", "Ruth", "1 Samuel", "2 Samuel", "1 Kings", "1 Mga Hari", "2 Kings", "2 Mga Hari",
        "1 Chronicles", "1 Cronica", "2 Chronicles", "2 Cronica", "Ezra", "Nehemiah", "Nehemias", "Esther", "Ester", "Job",
        "Psalm", "Mga Awit", "Proverbs", "Mga Kawikaan", "Ecclesiastes", "Eclesiastes", "Song of Solomon", "Ang Awit ng mga Awit",
        "Isaiah", "Isaias", "Jeremiah", "Jeremias", "Lamentations", "Mga Panaghoy", "Ezekiel", "Daniel",
        "Hosea", "Oseas", "Joel", "Amos", "Obadiah", "Obadias", "Jonah", "Jonas", "Micah", "Mikas",
        "Nahum", "Habakkuk", "Habacuc", "Zephaniah", "Zefanias", "Haggai", "Hagai", "Zechariah", "Zacarias", "Malachi", "Malakias",
        // New Testament
        "Matthew", "Mateo", "Mark", "Marcos", "Luke", "Lucas", "John", "Juan",
        "Acts", "Mga Gawa", "Romans", "Roma", "1 Corinthians", "1 Corinto", "2 Corinthians", "2 Corinto",
        "Galatians", "Galacia", "Ephesians", "Efeso", "Philippians", "Filipos", "Colosas", "Colossians",
        "1 Thessalonians", "1 Tesalonica", "2 Thessalonians", "2 Tesalonica", "1 Timothy", "1 Timoteo",
        "2 Timothy", "2 Timoteo", "Titus", "Tito", "Philemon", "Filemon",
        "Hebrews", "Hebreo", "James", "Santiago", "1 Peter", "1 Pedro", "2 Peter", "2 Pedro",
        "1 Juan", "1 John", "2 Juan", "2 John", "3 Juan", "3 John", "Jude", "Judas", "Revelation", "Pahayag"
    )

    private val crossLanguageMappings = mapOf(
        "genesis" to "Genesis", "exodus" to "Exodo", "exodo" to "Exodus",
        "leviticus" to "Levitico", "levitico" to "Leviticus",
        "numbers" to "Mga Bilang", "mga bilang" to "Numbers",
        "deuteronomy" to "Deuteronomio", "deuteronomio" to "Deuteronomy",
        "joshua" to "Josue", "josue" to "Joshua",
        "judges" to "Mga Hukom", "mga hukom" to "Judges",
        "ruth" to "Ruth",
        "1 samuel" to "1 Samuel", "2 samuel" to "2 Samuel",
        "1 kings" to "1 Mga Hari", "1 mga hari" to "1 Kings",
        "2 kings" to "2 Mga Hari", "2 mga hari" to "2 Kings",
        "1 chronicles" to "1 Cronica", "1 cronica" to "1 Chronicles",
        "2 chronicles" to "2 Cronica", "2 cronica" to "2 Chronicles",
        "ezra" to "Ezra", "nehemiah" to "Nehemias", "nehemias" to "Nehemiah",
        "esther" to "Ester", "ester" to "Esther", "job" to "Job",
        "psalm" to "Mga Awit", "mga awit" to "Psalm",
        "proverbs" to "Mga Kawikaan", "mga kawikaan" to "Proverbs",
        "ecclesiastes" to "Eclesiastes", "song of solomon" to "Ang Awit ng mga Awit",
        "isaiah" to "Isaias", "isaias" to "Isaiah",
        "jeremiah" to "Jeremias", "jeremias" to "Jeremiah",
        "lamentations" to "Mga Panaghoy", "mga panaghoy" to "Lamentations",
        "ezekiel" to "Ezekiel", "daniel" to "Daniel",
        "hosea" to "Oseas", "oseas" to "Hosea",
        "joel" to "Joel", "amos" to "Amos",
        "obadiah" to "Obadias", "obadias" to "Obadiah",
        "jonah" to "Jonas", "jonas" to "Jonah",
        "micah" to "Mikas", "mikas" to "Micah",
        "nahum" to "Nahum",
        "habakkuk" to "Habacuc", "habacuc" to "Habakkuk",
        "zephaniah" to "Zefanias", "zefanias" to "Zephaniah",
        "haggai" to "Hagai", "hagai" to "Haggai",
        "zechariah" to "Zacarias", "zacarias" to "Zechariah",
        "malachi" to "Malakias", "malakias" to "Malachi",
        "matthew" to "Mateo", "mateo" to "Matthew",
        "mark" to "Marcos", "marcos" to "Mark",
        "luke" to "Lucas", "lucas" to "Luke",
        "john" to "Juan", "juan" to "John",
        "acts" to "Mga Gawa", "mga gawa" to "Acts",
        "romans" to "Roma", "roma" to "Romans",
        "1 corinthians" to "1 Corinto", "1 corinto" to "1 Corinthians",
        "2 corinthians" to "2 Corinto", "2 corinto" to "2 Corinthians",
        "galatians" to "Galacia", "galacia" to "Galatians",
        "ephesians" to "Efeso", "efeso" to "Ephesians",
        "philippians" to "Filipos", "filipos" to "Philippians",
        "colossians" to "Colosas", "colosas" to "Colosas",
        "1 thessalonians" to "1 Tesalonica", "1 tesalonica" to "1 Thessalonians",
        "2 thessalonians" to "2 Tesalonica", "2 tesalonica" to "2 Thessalonians",
        "1 timothy" to "1 Timoteo", "1 timoteo" to "1 Timothy",
        "2 timothy" to "2 Timoteo", "2 timoteo" to "2 Timothy",
        "titus" to "Tito", "tito" to "Titus",
        "philemon" to "Filemon", "filemon" to "Philemon",
        "hebrews" to "Hebreo", "hebreo" to "Hebrews",
        "james" to "Santiago", "santiago" to "James",
        "1 peter" to "1 Pedro", "1 pedro" to "1 Peter",
        "2 peter" to "2 Pedro", "2 pedro" to "2 Peter",
        "1 john" to "1 Juan", "1 juan" to "1 John",
        "2 john" to "2 Juan", "2 juan" to "2 John",
        "3 john" to "3 Juan", "3 juan" to "3 John",
        "jude" to "Judas", "judas" to "Jude",
        "revelation" to "Pahayag", "pahayag" to "Revelation"
    )

    @Synchronized
    fun loadVerses(context: Context, translation: BibleTranslation = currentTranslation, forceReload: Boolean = false) {
        if (!forceReload && isLoaded && translation == currentTranslation && verses.isNotEmpty()) return
        
        currentTranslation = translation
        verses.clear()
        isLoaded = false
        
        val cleanSuffix = translation.suffix.lowercase()
        val targetSuffix = "_${cleanSuffix}.json"
        val zipAssetName = "${cleanSuffix}.zip"

        try {
            var loadedFromZip = false
            val assetFiles = context.assets.list("")?.toList() ?: emptyList()

            if (assetFiles.any { it.equals(zipAssetName, ignoreCase = true) }) {
                val actualZipName = assetFiles.first { it.equals(zipAssetName, ignoreCase = true) }
                try {
                    context.assets.open(actualZipName).use { rawStream ->
                        java.util.zip.ZipInputStream(rawStream).use { zipInput ->
                            var entry = zipInput.nextEntry
                            while (entry != null) {
                                if (!entry.isDirectory && entry.name.endsWith(".json", ignoreCase = true)) {
                                    val jsonString = zipInput.bufferedReader().readText()
                                    parseAndAddVerses(jsonString)
                                }
                                zipInput.closeEntry()
                                entry = zipInput.nextEntry
                            }
                        }
                    }
                    loadedFromZip = verses.isNotEmpty()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            if (!loadedFromZip) {
                val biblesDir = File(context.filesDir, "bibles")
                val downloadedFiles = if (biblesDir.exists()) {
                    biblesDir.listFiles()?.map { it.name } ?: emptyList()
                } else {
                    emptyList()
                }

                val allFiles = (assetFiles + downloadedFiles).distinct()
                val filesToLoad = allFiles.filter { file ->
                    val lower = file.lowercase()
                    lower.endsWith(targetSuffix) || lower == "${cleanSuffix}.json"
                }

                for (fileName in filesToLoad) {
                    try {
                        val stream = getChapterStream(context, fileName) ?: continue
                        val jsonString = stream.bufferedReader().use { it.readText() }
                        stream.close()
                        parseAndAddVerses(jsonString)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            if (verses.isEmpty()) {
                serveFallback()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            serveFallback()
        }
        isLoaded = true
    }

    private fun parseAndAddVerses(jsonString: String) {
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                verses.add(
                    BibleVerse(
                        text = item.getString("text"),
                        reference = item.getString("reference"),
                        connectedTo = item.optString("connected_to", null),
                        connectedFrom = item.optString("connected_from", null)
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getChapterStream(context: Context, fileName: String): InputStream? {
        val downloadedFile = File(context.filesDir, "bibles/$fileName")
        if (downloadedFile.exists()) {
            return downloadedFile.inputStream()
        }
        return try {
            context.assets.open(fileName)
        } catch (e: Exception) {
            null
        }
    }

    private fun serveFallback() {
        if (verses.isEmpty()) {
            if (currentTranslation.languageGroup == "Tagalog") {
                verses.add(BibleVerse(text = "Ang Dios ay ating kanlungan at kalakasang tunay.", reference = "Mga Awit 46:1 (ADB)"))
            } else {
                verses.add(BibleVerse(text = "The Lord is my shepherd; I shall not want.", reference = "Psalm 23:1 (KJV)"))
            }
        }
    }

    fun setTranslation(context: Context, translation: BibleTranslation) {
        currentTranslation = translation
        val prefs = context.getSharedPreferences("BibleAppPrefs", Context.MODE_PRIVATE)
        prefs.edit().putString("last_translation_code", translation.name).apply()
    }
    
    fun getSavedTranslation(context: Context): BibleTranslation {
        val prefs = context.getSharedPreferences("BibleAppPrefs", Context.MODE_PRIVATE)
        val savedName = prefs.getString("last_translation_code", BibleTranslation.KJV.name)
        return try {
            BibleTranslation.valueOf(savedName ?: BibleTranslation.KJV.name)
        } catch (e: Exception) {
            BibleTranslation.KJV
        }
    }

    fun getRandomVerse(context: Context): BibleVerse {
        loadVerses(context)
        return if (verses.isNotEmpty()) verses.random() else BibleVerse("The Lord is my shepherd; I shall not want.", "Psalm 23:1 (KJV)")
    }

    fun getVerseByBookChapterAndVerse(baseReference: String, context: Context): BibleVerse? {
        loadVerses(context)
        val directMatch = verses.find { it.reference.equals(baseReference, ignoreCase = true) || it.reference.startsWith("$baseReference (") }
        if (directMatch != null) return directMatch

        val colonIndex = baseReference.lastIndexOf(':')
        if (colonIndex != -1) {
            val bookAndChapter = baseReference.substring(0, colonIndex).trim()
            val verseNum = baseReference.substring(colonIndex)
            
            val lastSpace = bookAndChapter.lastIndexOf(' ')
            if (lastSpace != -1) {
                val bookPart = bookAndChapter.substring(0, lastSpace).trim()
                val chapterPart = bookAndChapter.substring(lastSpace + 1).trim()
                
                val mappedBook = crossLanguageMappings[bookPart.lowercase()]
                if (mappedBook != null) {
                    val translatedRef = "$mappedBook $chapterPart$verseNum"
                    return verses.find { it.reference.equals(translatedRef, ignoreCase = true) || it.reference.startsWith("$translatedRef (") }
                }
            }
        }
        return null
    }

    fun extractBookName(reference: String): String {
        val cleanRef = reference.substringBefore(" (").trim()
        val colonIndex = cleanRef.lastIndexOf(':')
        if (colonIndex == -1) return cleanRef
        val beforeColon = cleanRef.substring(0, colonIndex)
        val lastSpaceIndex = beforeColon.lastIndexOf(' ')
        return if (lastSpaceIndex != -1) beforeColon.substring(0, lastSpaceIndex) else beforeColon
    }

    fun getAvailableBooks(context: Context): List<String> {
        loadVerses(context)
        val rawBooks = verses.map { extractBookName(it.reference) }.distinct()
        return rawBooks.sortedWith(compareBy { bookName ->
            val index = canonicalBookOrder.indexOfFirst { it.equals(bookName, ignoreCase = true) }
            if (index != -1) index else Int.MAX_VALUE
        })
    }

    fun getChaptersForBook(bookName: String, context: Context): List<String> {
        loadVerses(context)
        val chapters = verses
            .filter { extractBookName(it.reference).equals(bookName, ignoreCase = true) }
            .map { 
                val cleanRef = it.reference.substringBefore(" (").trim()
                val colonIndex = cleanRef.lastIndexOf(':')
                if (colonIndex != -1) cleanRef.substring(0, colonIndex) else cleanRef
            }
            .distinct()

        return chapters.sortedWith(compareBy { chapterStr ->
            val numStr = chapterStr.substringAfterLast(' ').trim()
            numStr.toIntOrNull() ?: 0
        })
    }

    fun getVersesForChapter(chapterName: String, context: Context): List<String> {
        loadVerses(context)
        return verses.filter { it.reference.startsWith("$chapterName:") }.map { it.reference }
    }

    fun getVerseObjectsForChapter(chapterName: String, context: Context): List<BibleVerse> {
        loadVerses(context)
        return verses.filter { it.reference.startsWith("$chapterName:") }
    }

    fun getNextVerse(currentVerse: BibleVerse, context: Context): BibleVerse? {
        loadVerses(context)
        val cleanCurrentRef = currentVerse.reference.substringBefore(" (").trim()
        val chapterPart = cleanCurrentRef.substringBefore(":")
        val chapterVerses = getVerseObjectsForChapter(chapterPart, context)
        val currentIndex = chapterVerses.indexOfFirst { it.reference.substringBefore(" (").trim() == cleanCurrentRef }
        return if (currentIndex != -1 && currentIndex < chapterVerses.size - 1) {
            chapterVerses[currentIndex + 1]
        } else {
            null
        }
    }

    fun getPreviousVerse(currentVerse: BibleVerse, context: Context): BibleVerse? {
        loadVerses(context)
        val cleanCurrentRef = currentVerse.reference.substringBefore(" (").trim()
        val chapterPart = cleanCurrentRef.substringBefore(":")
        val chapterVerses = getVerseObjectsForChapter(chapterPart, context)
        val currentIndex = chapterVerses.indexOfFirst { it.reference.substringBefore(" (").trim() == cleanCurrentRef }
        return if (currentIndex > 0) {
            chapterVerses[currentIndex - 1]
        } else {
            null
        }
    }

    fun getNextChapterFirstVerse(currentVerse: BibleVerse, context: Context): BibleVerse? {
        loadVerses(context)
        val cleanRef = currentVerse.reference.substringBefore(" (").trim()
        val currentChapter = cleanRef.substringBeforeLast(":")
        val bookName = extractBookName(currentVerse.reference)
        val chapters = getChaptersForBook(bookName, context)
        val chapterIndex = chapters.indexOf(currentChapter)
        
        if (chapterIndex != -1 && chapterIndex < chapters.size - 1) {
            val nextChapter = chapters[chapterIndex + 1]
            return getVerseObjectsForChapter(nextChapter, context).firstOrNull()
        } else {
            val allBooks = getAvailableBooks(context)
            val bookIndex = allBooks.indexOf(bookName)
            if (bookIndex != -1 && bookIndex < allBooks.size - 1) {
                val nextBook = allBooks[bookIndex + 1]
                val nextChapters = getChaptersForBook(nextBook, context)
                if (nextChapters.isNotEmpty()) {
                    return getVerseObjectsForChapter(nextChapters.first(), context).firstOrNull()
                }
            }
        }
        return null
    }

    fun getPreviousChapterLastVerse(currentVerse: BibleVerse, context: Context): BibleVerse? {
        loadVerses(context)
        val cleanRef = currentVerse.reference.substringBefore(" (").trim()
        val currentChapter = cleanRef.substringBeforeLast(":")
        val bookName = extractBookName(currentVerse.reference)
        val chapters = getChaptersForBook(bookName, context)
        val chapterIndex = chapters.indexOf(currentChapter)
        
        if (chapterIndex > 0) {
            val prevChapter = chapters[chapterIndex - 1]
            return getVerseObjectsForChapter(prevChapter, context).lastOrNull()
        } else {
            val allBooks = getAvailableBooks(context)
            val bookIndex = allBooks.indexOf(bookName)
            if (bookIndex > 0) {
                val prevBook = allBooks[bookIndex - 1]
                val prevChapters = getChaptersForBook(prevBook, context)
                if (prevChapters.isNotEmpty()) {
                    return getVerseObjectsForChapter(prevChapters.last(), context).lastOrNull()
                }
            }
        }
        return null
    }
}

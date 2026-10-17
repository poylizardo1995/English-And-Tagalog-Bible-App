package com.example.EnglishTagalogBible

import android.content.Context
import org.json.JSONArray
import java.io.File
import java.io.InputStream

enum class BibleTranslation(
    val languageGroup: String,
    val displayName: String,
    val suffix: String,
    val isBuiltIn: Boolean
) {
    KJV("English", "English (KJV)", "kjv", true),
    WEB("English", "English (WEB)", "web", false),
    ADB1905("Tagalog", "Tagalog (ADB1905)", "adb1905", true),
    MBB("Tagalog", "Tagalog (MBB)", "mbb", false);

    fun isAvailable(context: Context): Boolean {
        if (isBuiltIn) return true
        val biblesDir = File(context.filesDir, "bibles")
        if (!biblesDir.exists()) return false
        val files = biblesDir.listFiles() ?: return false
        return files.any { it.name.endsWith("_${suffix}.json") }
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
    private var isLoaded = false
    
    var currentTranslation: BibleTranslation = BibleTranslation.KJV
        private set

    // Bidirectional Mapping Dictionary for English <-> Tagalog book names
    private val bookNameMappings = mapOf(
        "Genesis" to "Genesis",
        "Exodus" to "Exodo",
        "Exodo" to "Exodus",
        "Leviticus" to "Levitico",
        "Levitico" to "Leviticus",
        "Numbers" to "Mga Bilang",
        "Mga Bilang" to "Numbers",
        "Deuteronomy" to "Deuteronomio",
        "Deuteronomio" to "Deuteronomy",
        "Joshua" to "Josue",
        "Josue" to "Joshua",
        "Judges" to "Mga Hukom",
        "Mga Hukom" to "Judges",
        "Ruth" to "Ruth",
        "1 Samuel" to "1 Samuel",
        "2 Samuel" to "2 Samuel",
        "1 Mga Hari" to "1 Kings",
        "1 Kings" to "1 Mga Hari",
        "2 Mga Hari" to "2 Kings",
        "2 Kings" to "2 Mga Hari",
        "1 Cronica" to "1 Chronicles",
        "1 Chronicles" to "1 Cronica",
        "2 Cronica" to "2 Chronicles",
        "2 Chronicles" to "2 Cronica",
        "Ezra" to "Ezra",
        "Nehemias" to "Nehemiah",
        "Nehemiah" to "Nehemias",
        "Ester" to "Esther",
        "Esther" to "Ester",
        "Job" to "Job",
        "Mga Awit" to "Psalms",
        "Psalms" to "Mga Awit",
        "Mga Kawikaan" to "Proverbs",
        "Proverbs" to "Mga Kawikaan",
        "Eclesiastes" to "Ecclesiastes",
        "Ecclesiastes" to "Eclesiastes",
        "Ang Awit ng mga Awit" to "Song of Solomon",
        "Song of Solomon" to "Ang Awit ng mga Awit",
        "Isaias" to "Isaiah",
        "Isaiah" to "Isaias",
        "Jeremias" to "Jeremiah",
        "Jeremiah" to "Jeremias",
        "Mga Panaghoy" to "Lamentations",
        "Lamentations" to "Mga Panaghoy",
        "Ezekiel" to "Ezekiel",
        "Daniel" to "Daniel",
        "Oseas" to "Hosea",
        "Hosea" to "Oseas",
        "Joel" to "Joel",
        "Amos" to "Amos",
        "Obadias" to "Obadiah",
        "Obadiah" to "Obadias",
        "Jonas" to "Jonah",
        "Jonah" to "Jonas",
        "Mikas" to "Micah",
        "Micah" to "Mikas",
        "Nahum" to "Nahum",
        "Habacuc" to "Habakkuk",
        "Habakkuk" to "Habacuc",
        "Zefanias" to "Zephaniah",
        "Zephaniah" to "Zefanias",
        "Hagai" to "Haggai",
        "Haggai" to "Hagai",
        "Zacarias" to "Zechariah",
        "Zechariah" to "Zacarias",
        "Malakias" to "Malachi",
        "Malachi" to "Malakias",
        "Mateo" to "Matthew",
        "Matthew" to "Mateo",
        "Marcos" to "Mark",
        "Mark" to "Marcos",
        "Lucas" to "Luke",
        "Luke" to "Lucas",
        "Juan" to "John",
        "John" to "Juan",
        "Mga Gawa" to "Acts",
        "Acts" to "Mga Gawa",
        "Roma" to "Romans",
        "Romans" to "Roma",
        "1 Corinto" to "1 Corinthians",
        "1 Corinthians" to "1 Corinto",
        "2 Corinto" to "2 Corinthians",
        "2 Corinthians" to "2 Corinto",
        "Galacia" to "Galatians",
        "Galatians" to "Galacia",
        "Efeso" to "Ephesians",
        "Ephesians" to "Efeso",
        "Filipos" to "Philippians",
        "Philippians" to "Filipos",
        "Colosas" to "Colossians",
        "Colossians" to "Colosas",
        "1 Tesalonica" to "1 Thessalonians",
        "1 Thessalonians" to "1 Tesalonica",
        "2 Tesalonica" to "2 Thessalonians",
        "2 Thessalonians" to "2 Tesalonica",
        "1 Timoteo" to "1 Timothy",
        "1 Timothy" to "1 Timoteo",
        "2 Timoteo" to "2 Timothy",
        "2 Timothy" to "2 Timoteo",
        "Tito" to "Titus",
        "Titus" to "Tito",
        "Filemon" to "Philemon",
        "Philemon" to "Filemon",
        "Hebreo" to "Hebrews",
        "Hebrews" to "Hebreo",
        "Santiago" to "James",
        "James" to "Santiago",
        "1 Pedro" to "1 Peter",
        "1 Peter" to "1 Pedro",
        "2 Pedro" to "2 Peter",
        "2 Peter" to "2 Pedro",
        "1 Juan" to "1 John",
        "1 John" to "1 Juan",
        "2 Juan" to "2 John",
        "2 John" to "2 Juan",
        "3 Juan" to "3 John",
        "3 John" to "3 Juan",
        "Judas" to "Jude",
        "Jude" to "Judas",
        "Pahayag" to "Revelation",
        "Revelation" to "Pahayag",
        "Apocalipsis" to "Apocalypse",
        "Apocalypse" to "Apocalipsis",
        "Apocalipsis" to "Revelation",
        "Revelation" to  "Apocalipsis",
        "Pahayag" to "Apocalypse",
        "Apocalypse" to "Pahayag",
        "Pahayag" to "Apocalipsis",
        "Apocalipsis" to "Pahayag",
        "Revelation" to "Apocalypse",
        "Apocalypse" to "Revelation"
    )

    /**
     * Normalizes a book name to match user selection or lookups interchangeably.
     */
    fun normalizeBookName(bookName: String): String {
        val key = bookName.trim().lowercase()
        return bookNameMappings[key] ?: bookName.trim()
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

    fun loadVerses(context: Context, translation: BibleTranslation = currentTranslation) {
        if (isLoaded && translation == currentTranslation) return
        
        currentTranslation = translation
        verses.clear()
        
        try {
            val assetFiles = context.assets.list("")?.toList() ?: emptyList()
            val biblesDir = File(context.filesDir, "bibles")
            val downloadedFiles = if (biblesDir.exists()) {
                biblesDir.listFiles()?.map { it.name } ?: emptyList()
            } else {
                emptyList()
            }

            val targetSuffix = "_${translation.suffix}.json"
            val allFiles = (assetFiles + downloadedFiles).distinct()
            val matchingFiles = allFiles.filter { it.endsWith(targetSuffix) }
            val filesToLoad = if (matchingFiles.isNotEmpty()) {
                matchingFiles
            } else {
                allFiles.filter { it.contains("_ch") && it.endsWith(".json") }
            }

            for (fileName in filesToLoad) {
                try {
                    val stream = getChapterStream(context, fileName) ?: continue
                    val jsonString = stream.bufferedReader().use { it.readText() }
                    stream.close()
                    
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

            if (verses.isEmpty()) {
                verses.add(BibleVerse(text = "The Lord is my shepherd; I shall not want.", reference = "Psalm 23:1 (KJV)"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            if (verses.isEmpty()) {
                verses.add(BibleVerse(text = "The Lord is my shepherd; I shall not want.", reference = "Psalm 23:1 (KJV)"))
            }
        }
        isLoaded = true
    }

    fun setTranslation(context: Context, translation: BibleTranslation) {
        isLoaded = false
        loadVerses(context, translation)
    }

    fun getRandomVerse(context: Context): BibleVerse {
        loadVerses(context)
        return if (verses.isNotEmpty()) verses.random() else BibleVerse("The Lord is my shepherd; I shall not want.", "Psalm 23:1 (KJV)")
    }

    fun getVerseByBookChapterAndVerse(baseReference: String, context: Context): BibleVerse? {
        loadVerses(context)
        return verses.find { it.reference.equals(baseReference, ignoreCase = true) || it.reference.startsWith("$baseReference (") }
    }

    private fun extractBookName(reference: String): String {
        val cleanRef = reference.substringBefore(" (").trim()
        val colonIndex = cleanRef.lastIndexOf(':')
        if (colonIndex == -1) return cleanRef
        val beforeColon = cleanRef.substring(0, colonIndex)
        val lastSpaceIndex = beforeColon.lastIndexOf(' ')
        val book = if (lastSpaceIndex != -1) beforeColon.substring(0, lastSpaceIndex) else beforeColon
        return normalizeBookName(book)
    }

    fun getAvailableBooks(context: Context): List<String> {
        loadVerses(context)
        return verses.map { extractBookName(it.reference) }.distinct()
    }

    fun getChaptersForBook(bookName: String, context: Context): List<String> {
        loadVerses(context)
        val targetNormalized = normalizeBookName(bookName).lowercase()
        return verses
            .filter { extractBookName(it.reference).lowercase() == targetNormalized }
            .map { 
                val cleanRef = it.reference.substringBefore(" (").trim()
                val colonIndex = cleanRef.lastIndexOf(':')
                if (colonIndex != -1) cleanRef.substring(0, colonIndex) else cleanRef
            }
            .distinct()
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
}

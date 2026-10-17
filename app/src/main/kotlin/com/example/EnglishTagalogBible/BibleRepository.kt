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

    // ALL KEYS MUST BE LOWERCASE for case-insensitive mapping to work properly!
    private val bookNameMappings = mapOf(
        "genesis" to "Genesis",
        "exodus" to "Exodo",
        "exodo" to "Exodus",
        "leviticus" to "Levitico",
        "levitico" to "Leviticus",
        "numbers" to "Mga Bilang",
        "mga bilang" to "Numbers",
        "deuteronomy" to "Deuteronomio",
        "deuteronomio" to "Deuteronomy",
        "joshua" to "Josue",
        "josue" to "Joshua",
        "judges" to "Mga Hukom",
        "mga hukom" to "Judges",
        "ruth" to "Ruth",
        "1 samuel" to "1 Samuel",
        "2 samuel" to "2 Samuel",
        "1 mga hari" to "1 Kings",
        "1 kings" to "1 Mga Hari",
        "2 mga hari" to "2 Kings",
        "2 kings" to "2 Mga Hari",
        "1 cronica" to "1 Chronicles",
        "1 chronicles" to "1 Cronica",
        "2 cronica" to "2 Chronicles",
        "2 chronicles" to "2 Cronica",
        "ezra" to "Ezra",
        "nehemias" to "Nehemiah",
        "nehemiah" to "Nehemias",
        "ester" to "Esther",
        "esther" to "Ester",
        "job" to "Job",
        "mga awit" to "Psalms",
        "psalms" to "Mga Awit",
        "mga kawikaan" to "Proverbs",
        "proverbs" to "Mga Kawikaan",
        "eclesiastes" to "Ecclesiastes",
        "ecclesiastes" to "Eclesiastes",
        "ang awit ng mga awit" to "Song of Solomon",
        "song of solomon" to "Ang Awit ng mga Awit",
        "isaias" to "Isaiah",
        "isaiah" to "Isaias",
        "jeremias" to "Jeremiah",
        "jeremiah" to "Jeremias",
        "mga panaghoy" to "Lamentations",
        "lamentations" to "Mga Panaghoy",
        "ezekiel" to "Ezekiel",
        "daniel" to "Daniel",
        "oseas" to "Hosea",
        "hosea" to "Oseas",
        "joel" to "Joel",
        "amos" to "Amos",
        "obadias" to "Obadiah",
        "obadiah" to "Obadias",
        "jonas" to "Jonah",
        "jonah" to "Jonas",
        "mikas" to "Micah",
        "micah" to "Mikas",
        "nahum" to "Nahum",
        "habacuc" to "Habakkuk",
        "habakkuk" to "Habacuc",
        "zefanias" to "Zephaniah",
        "zephaniah" to "Zefanias",
        "hagai" to "Haggai",
        "haggai" to "Hagai",
        "zacarias" to "Zechariah",
        "zechariah" to "Zacarias",
        "malakias" to "Malachi",
        "malachi" to "Malakias",
        "mateo" to "Matthew",
        "matthew" to "Mateo",
        "marcos" to "Mark",
        "mark" to "Marcos",
        "lucas" to "Luke",
        "luke" to "Lucas",
        "juan" to "John",
        "john" to "Juan",
        "mga gawa" to "Acts",
        "acts" to "Mga Gawa",
        "roma" to "Romans",
        "romans" to "Roma",
        "mga taga roma" to "Romans",
        "romans" to "Mga Taga Roma",
        "1 corinto" to "1 Corinthians",
        "1 corinthians" to "1 Corinto",
        "2 corinto" to "2 Corinthians",
        "2 corinthians" to "2 Corinto",
        "galacia" to "Galatians",
        "galatians" to "Galacia",
        "efeso" to "Ephesians",
        "ephesians" to "Efeso",
        "filipos" to "Philippians",
        "philippians" to "Filipos",
        "colosas" to "Colossians",
        "colossians" to "Colosas",
        "1 tesalonica" to "1 Thessalonians",
        "1 thessalonians" to "1 Tesalonica",
        "2 tesalonica" to "2 Thessalonians",
        "2 thessalonians" to "2 Tesalonica",
        "1 timoteo" to "1 Timothy",
        "1 timothy" to "1 Timoteo",
        "2 timoteo" to "2 Timothy",
        "2 timothy" to "2 Timoteo",
        "tito" to "Titus",
        "titus" to "Tito",
        "filemon" to "Philemon",
        "philemon" to "Filemon",
        "hebreo" to "Hebrews",
        "hebrews" to "Hebreo",
        "santiago" to "James",
        "james" to "Santiago",
        "1 pedro" to "1 Peter",
        "1 peter" to "1 Pedro",
        "2 pedro" to "2 Peter",
        "2 peter" to "2 Pedro",
        "1 juan" to "1 John",
        "1 john" to "1 Juan",
        "2 juan" to "2 John",
        "2 john" to "2 Juan",
        "3 juan" to "3 John",
        "3 john" to "3 Juan",
        "judas" to "Jude",
        "jude" to "Judas",
        "pahayag" to "Revelation",
        "revelation" to "Pahayag"
    )

    fun normalizeBookName(bookName: String): String {
        val key = bookName.trim().lowercase()
        return bookNameManager(bookNameMappings[key] ?: bookName.trim())
    }

    private fun bookNameManager(name: String): String {
        // Standardize capitalization or keep mapped value
        return name
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

    /**
     * Finds a verse by matching the reference, handling cross-language book name equivalents automatically.
     */
    fun getVerseByBookChapterAndVerse(baseReference: String, context: Context): BibleVerse? {
        loadVerses(context)
        
        // Direct match attempt
        val directMatch = verses.find { it.reference.equals(baseReference, ignoreCase = true) || it.reference.startsWith("$baseReference (") }
        if (directMatch != null) return directMatch

        // Cross-language fallback (e.g. converting "Isaiah 14:16" to look for "Isaias 14:16")
        val colonIndex = baseReference.lastIndexOf(':')
        if (colonIndex != -1) {
            val bookAndChapter = baseReference.substring(0, colonIndex).trim()
            val verseNum = baseReference.substring(colonIndex) // e.g. ":16"
            
            val lastSpace = bookAndChapter.lastIndexOf(' ')
            if (lastSpace != -1) {
                val bookPart = bookAndChapter.substring(0, lastSpace).trim()
                val chapterPart = bookAndChapter.substring(lastSpace + 1).trim()
                
                val mappedBook = bookNameMappings[bookPart.lowercase()]
                if (mappedBook != null) {
                    val translatedRef = "$mappedBook $chapterPart$verseNum"
                    return verses.find { it.reference.equals(translatedRef, ignoreCase = true) || it.reference.startsWith("$translatedRef (") }
                }
            }
        }
        return null
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

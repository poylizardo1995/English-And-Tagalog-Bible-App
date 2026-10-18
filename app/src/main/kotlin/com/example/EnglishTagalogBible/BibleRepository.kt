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
    BAB("English", "English (BAB)", "bab", false, "Berean Annotated Bible (Early Draft Version)"),
    BLB("English", "English (BLB)", "blb", false, "Berean Literal Bible (Draft Version)"),
    BSB("English", "English (BSB)", "bsb", false, "Berean Standard Bible (2022)"),
    BRB("English", "English (BRB)", "brb", false, "Berean Reader’s Bible (2020)"),
    CPDV("English", "English (CPDV)", "cpdv", false, "Catholic Public Domain Version"),
    DBT("English", "English (DBT)", "dbt", false, "Darby Bible Translation (1890)"),
    DRB("English", "English (DRB)", "drb", false, "Douay-Rheims Bible [1582 (NT) - 1609–1610 (OT) / (Challoner Revision 1752)]"),
    ERV("English", "English (ERV)", "erv", false, "English Revised Version (1885)"),
    JPS("English", "English (JPS)", "jps", false, "Jewish Publication Society's Tanakh (OT only)(1917)"),
    KJV("English", "English (KJV)", "kjv", true, "King James Bible: Pure Cambridge Edition (1900)"),
    MSB("English", "English (MSB)", "msb", false, "Majority Standard Bible (2022)"),
    SLT("English", "English (SLT)", "slt", false, "Smith's Literal Translation"),
    WBT("English", "English (WBT)", "wbt", false, "Webster Bible Translation (1833)"),
    WEB("English", "English (WEB)", "web", false, "World English Bible (2020-2022)"),
    WNT("English", "English (WNT)", "wnt", false, "Weymouth New Testament (NT only)(1903)"),
    YLT("English", "English (YLT)", "ylt", false, "Young's Literal Translation (1862)"),
    ADB1905("Tagalog", "Tagalog (ADB1905)", "adb1905", true, "Ang Dating Biblia (1905)"),
    MBB("Tagalog", "Tagalog (MBB)", "mbb", false, "Magandang Balita Biblia");

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
    private var isLoaded = false
    
    var currentTranslation: BibleTranslation = BibleTranslation.KJV
        private set

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

            val cleanSuffix = translation.suffix.lowercase()
            val targetSuffix = "_${cleanSuffix}.json"
            val singleFileName = "${cleanSuffix}.json"

            val allFiles = (assetFiles + downloadedFiles).distinct()
            val matchingFiles = allFiles.filter { file ->
                file.lowercase().endsWith(targetSuffix) || file.lowercase() == singleFileName
            }

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
            e.printStackTrace serveFallback()
        }
        isLoaded = true
    }

    private fun serveFallback() {
        if (verses.isEmpty()) {
            verses.add(BibleVerse(text = "The Lord is my shepherd; I shall not want.", reference = "Psalm 23:1 (KJV)"))
        }
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

    private fun extractBookName(reference: String): String {
        val cleanRef = reference.substringBefore(" (").trim()
        val colonIndex = cleanRef.lastIndexOf(':')
        if (colonIndex == -1) return cleanRef
        val beforeColon = cleanRef.substring(0, colonIndex)
        val lastSpaceIndex = beforeColon.lastIndexOf(' ')
        return if (lastSpaceIndex != -1) beforeColon.substring(0, lastSpaceIndex) else beforeColon
    }

    fun getAvailableBooks(context: Context): List<String> {
        loadVerses(context)
        return verses.map { extractBookName(it.reference) }.distinct().sorted()
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

        // Numerically sort chapters so Genesis 1 comes before Genesis 2 & 10
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
}

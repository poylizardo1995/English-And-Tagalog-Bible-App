package com.example.EnglishTagalogBible

import android.content.Context
import org.json.JSONArray
import java.io.IOException

enum class BibleTranslation(
    val languageGroup: String,
    val displayName: String,
    val suffix: String
) {
    KJV("English", "English (KJV)", "kjv"),
    WEB("English", "English (WEB)", "web"),
    ADB1905("Tagalog", "Tagalog (ADB1905)", "adb1905"),
    MBB("Tagalog", "Tagalog (MBB)", "mbb")
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

    fun loadVerses(context: Context, translation: BibleTranslation = currentTranslation) {
        if (isLoaded && translation == currentTranslation) return
        
        currentTranslation = translation
        verses.clear()
        
        try {
            val assetFiles = context.assets.list("") ?: emptyArray()
            val targetSuffix = "_${translation.suffix}.json"
            
            val matchingFiles = assetFiles.filter { it.endsWith(targetSuffix) }

            val filesToLoad = if (matchingFiles.isNotEmpty()) {
                matchingFiles
            } else {
                assetFiles.filter { it.contains("_ch") && it.endsWith(".json") }
            }

            for (fileName in filesToLoad) {
                try {
                    val jsonString = context.assets.open(fileName).bufferedReader().use { it.readText() }
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
                verses.add(
                    BibleVerse(
                        text = "The Lord is my shepherd; I shall not want.",
                        reference = "Psalm 23:1 (KJV)"
                    )
                )
            }
            
        } catch (e: Exception) {
            e.printStackTrace()
            if (verses.isEmpty()) {
                verses.add(
                    BibleVerse(
                        text = "The Lord is my shepherd; I shall not want.",
                        reference = "Psalm 23:1 (KJV)"
                    )
                )
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

    // Helper to accurately extract full book name (e.g., "1 Chronicles", "Genesis") before chapter/verse separator ":"
    private fun extractBookName(reference: String): String {
        val cleanRef = reference.substringBefore(" (").trim()
        // Find the index of the chapter number separator ':'
        val colonIndex = cleanRef.lastIndexOf(':')
        if (colonIndex == -1) return cleanRef
        // Substring everything before the last space preceding the chapter number
        val beforeColon = cleanRef.substring(0, colonIndex)
        val lastSpaceIndex = beforeColon.lastIndexOf(' ')
        return if (lastSpaceIndex != -1) beforeColon.substring(0, lastSpaceIndex) else beforeColon
    }

    // 1. Properly extracts all unique full Book names without splitting numbered books like "1 Chronicles"
    fun getAvailableBooks(context: Context): List<String> {
        loadVerses(context)
        return verses.map { extractBookName(it.reference) }.distinct()
    }

    // 2. Extracts all chapters belonging to a selected book
    fun getChaptersForBook(bookName: String, context: Context): List<String> {
        loadVerses(context)
        return verses
            .filter { extractBookName(it.reference).equals(bookName, ignoreCase = true) }
            .map { 
                val cleanRef = it.reference.substringBefore(" (").trim()
                val colonIndex = cleanRef.lastIndexOf(':')
                if (colonIndex != -1) cleanRef.substring(0, colonIndex) else cleanRef
            }
            .distinct()
    }

    // 3. Filters and returns all verse reference strings belonging to a specific chapter
    fun getVersesForChapter(chapterName: String, context: Context): List<String> {
        loadVerses(context)
        return verses.filter { it.reference.startsWith("$chapterName:") }.map { it.reference }
    }

    // 4. Returns the actual BibleVerse objects for a specific chapter
    fun getVerseObjectsForChapter(chapterName: String, context: Context): List<BibleVerse> {
        loadVerses(context)
        return verses.filter { it.reference.startsWith("$chapterName:") }
    }

    // 5. Finds the next verse in the current chapter
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

    // 6. Finds the previous verse in the current chapter
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

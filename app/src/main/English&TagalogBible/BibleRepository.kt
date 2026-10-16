package com.example.English&TagalogBible

import android.content.Context
import org.json.JSONArray
import java.io.IOException

enum class BibleTranslation(
    val languageGroup: String,
    val displayName: String,
    val suffix: String
) {
    KJV("English", "English (KJV)", "kjv"),
    WEB("English", "English (WEB)", "web"), // Future translation option of The World English Bible (WEB)
    ADB1905("Tagalog", "Tagalog (ADB1905)", "adb1905"),
    MBB("Tagalog", "Tagalog (MBB)", "mbb") // Future translation option of Magandang Balita Biblia (MBB)
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
    
    // Updated visibility so MainActivity can read currentTranslation safely
    var currentTranslation: BibleTranslation = BibleTranslation.KJV
        private set

    /**
     * Dynamically loads verses from all matching JSON assets.
     * Searches for files following the pattern: <book_slug>_ch<chapter>_<version>.json
     */
    fun loadVerses(context: Context, translation: BibleTranslation = currentTranslation) {
        // If translation changed, force re-indexing
        if (isLoaded && translation == currentTranslation) return
        
        currentTranslation = translation
        verses.clear()
        
        try {
            // Retrieve all JSON files inside the app's assets folder
            val assetFiles = context.assets.list("") ?: emptyArray()
            val targetSuffix = "_${translation.suffix}.json"
            
            // Filter files that match the version naming format (e.g., "genesis_ch01_kjv.json")
            val matchingFiles = assetFiles.filter { it.endsWith(targetSuffix) }

            // Fallback for older file names if new ones aren't added yet (e.g., "genesis_ch01.json")
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
                    // Gracefully handle any individual missing/corrupted file
                    e.printStackTrace()
                }
            }

            // Fallback default verse if assets are empty
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

    /**
     * Swaps translation between versions dynamically.
     */
    fun setTranslation(context: Context, translation: BibleTranslation) {
        isLoaded = false
        loadVerses(context, translation)
    }

    fun getRandomVerse(context: Context): BibleVerse {
        loadVerses(context)
        return if (verses.isNotEmpty()) verses.random() else BibleVerse("The Lord is my shepherd; I shall not want.", "Psalm 23:1 (KJV)")
    }

    // Find a specific verse by base reference (ignoring version suffix like "(KJV)" or "(ADB1905)")
    fun getVerseByBookChapterAndVerse(baseReference: String, context: Context): BibleVerse? {
        loadVerses(context)
        return verses.find { it.reference.equals(baseReference, ignoreCase = true) || it.reference.startsWith("$baseReference (") }
    }

    // 1. Automatically extracts all unique Book names (e.g., "Genesis") from loaded references
    fun getAvailableBooks(context: Context): List<String> {
        loadVerses(context)
        return verses.map { it.reference.substringBefore(" ") }.distinct()
    }

    // 2. Automatically extracts all chapters belonging to a selected book (e.g., "Genesis 1", "Genesis 2")
    fun getChaptersForBook(bookName: String, context: Context): List<String> {
        loadVerses(context)
        return verses
            .filter { it.reference.startsWith(bookName) }
            .map { 
                val referencePart = it.reference.substringBefore(":")
                referencePart
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
        val chapterPart = currentVerse.reference.substringBefore(":")
        val chapterVerses = getVerseObjectsForChapter(chapterPart, context)
        val currentIndex = chapterVerses.indexOfFirst { it.reference == currentVerse.reference }
        return if (currentIndex != -1 && currentIndex < chapterVerses.size - 1) {
            chapterVerses[currentIndex + 1]
        } else {
            null
        }
    }

    // 6. Finds the previous verse in the current chapter
    fun getPreviousVerse(currentVerse: BibleVerse, context: Context): BibleVerse? {
        loadVerses(context)
        val chapterPart = currentVerse.reference.substringBefore(":")
        val chapterVerses = getVerseObjectsForChapter(chapterPart, context)
        val currentIndex = chapterVerses.indexOfFirst { it.reference == currentVerse.reference }
        return if (currentIndex > 0) {
            chapterVerses[currentIndex - 1]
        } else {
            null
        }
    }
}

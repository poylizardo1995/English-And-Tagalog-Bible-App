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
        return if (lastSpaceIndex != -1) beforeColon.substring(0, lastSpaceIndex) else beforeColon
    }

    fun getAvailableBooks(context: Context): List<String> {
        loadVerses(context)
        return verses.map { extractBookName(it.reference) }.distinct()
    }

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

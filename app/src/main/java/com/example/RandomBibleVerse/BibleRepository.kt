package com.example.RandomBibleVerse

import android.content.Context
import org.json.JSONArray

data class BibleVerse(val text: String, val reference: String)

object BibleRepository {
    private val verses = mutableListOf<BibleVerse>()
    private var isLoaded = false

    fun loadVerses(context: Context) {
        if (isLoaded) return
        
        try {
            val fileNames = listOf(
                "genesis_ch1.json",
                "genesis_ch2.json",
                "genesis_ch3.json",
                "genesis_ch4.json",
                "genesis_ch5.json"
            )

            for (fileName in fileNames) {
                try {
                    val jsonString = context.assets.open(fileName).bufferedReader().use { it.readText() }
                    val jsonArray = JSONArray(jsonString)
                    
                    for (i in 0 until jsonArray.length()) {
                        val item = jsonArray.getJSONObject(i)
                        verses.add(
                            BibleVerse(
                                text = item.getString("text"),
                                reference = item.getString("reference")
                            )
                        )
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            if (verses.isEmpty()) {
                verses.add(BibleVerse("The Lord is my shepherd; I shall not want.", "Psalm 23:1 (KJV)"))
            }
            
        } catch (e: Exception) {
            verses.add(BibleVerse("The Lord is my shepherd; I shall not want.", "Psalm 23:1 (KJV)"))
        }
        
        isLoaded = true
    }

    fun getRandomVerse(context: Context): BibleVerse {
        loadVerses(context)
        return if (verses.isNotEmpty()) verses.random() else BibleVerse("The Lord is my shepherd; I shall not want.", "Psalm 23:1 (KJV)")
    }

    // Returns a list of available chapters to display in the selection dialog
    fun getAvailableChapters(): List<String> {
        return listOf(
            "Genesis 1",
            "Genesis 2",
            "Genesis 3",
            "Genesis 4",
            "Genesis 5"
        )
    }

    // Filters and returns all verses belonging to a specific chapter
    fun getVersesForChapter(chapterName: String, context: Context): List<BibleVerse> {
        loadVerses(context)
        return verses.filter { it.reference.startsWith(chapterName) }
    }
}

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
            // Add all your JSON file names to this list
            val fileNames = listOf("genesis_ch1.json", "genesis_ch2.json", "genesis_ch3.json", "genesis_ch4.json", "genesis_ch5.json")

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
                    // If a specific file fails to open or parse, it skips it and continues to the next file
                    e.printStackTrace()
                }
            }

            // Fallback if all files failed or the list ended up empty
            if (verses.isEmpty()) {
                verses.add(BibleVerse("The Lord is my shepherd; I shall not want.", "Psalm 23:1 (KJV)"))
            }
            
        } catch (e: Exception) {
            // General fallback if something unexpected happens
            verses.add(BibleVerse("The Lord is my shepherd; I shall not want.", "Psalm 23:1 (KJV)"))
        }
        
        isLoaded = true
    }

    fun getRandomVerse(context: Context): BibleVerse {
        loadVerses(context)
        return if (verses.isNotEmpty()) verses.random() else BibleVerse("The Lord is my shepherd", "Psalm 23:1 (KJV)")
    }
}

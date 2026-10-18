package com.example.EnglishTagalogBible

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

object BibleManager {

    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun downloadTranslationPackage(
        context: Context,
        translation: BibleTranslation,
        onProgress: (Int) -> Unit,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (!isNetworkAvailable(context)) {
            onError("No internet connection available. Please check your network and try again.")
            return
        }

        Thread {
            try {
                val fileSuffix = translation.suffix.lowercase()
                val urlStr = "https://raw.githubusercontent.com/poylizardo1995/English-And-Tagalog-Bible-App/main/downloadable_bibles/$fileSuffix.json"
                val url = URL(urlStr)
                val connection = url.openConnection() as HttpURLConnection
                connection.connect()

                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    onError("Server error: HTTP ${connection.responseCode}")
                    return@Thread
                }

                val fileLength = connection.contentLength
                val input: InputStream = connection.inputStream
                val targetDir = File(context.filesDir, "bibles").apply { mkdirs() }

                val outputFile = File(targetDir, "$fileSuffix.json")
                val outputStream = FileOutputStream(outputFile)

                val buffer = ByteArray(4096)
                var totalBytesRead = 0
                var count: Int

                while (input.read(buffer).also { count = it } != -1) {
                    outputStream.write(buffer, 0, count)
                    totalBytesRead += count
                    if (fileLength > 0) {
                        onProgress((totalBytesRead * 100 / fileLength))
                    }
                }

                outputStream.flush()
                outputStream.close()
                input.close()
                connection.disconnect()
                
                onSuccess()

            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Download failed")
            }
        }.start()
    }

    fun importManualFile(context: Context, uri: Uri, onSuccess: () -> Unit, onError: (String) -> Unit) {
        try {
            val targetDir = File(context.filesDir, "bibles").apply { mkdirs() }
            val inputStream = context.contentResolver.openInputStream(uri) 
                ?: return onError("Could not read file stream")
            
            var fileName: String? = null
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    fileName = cursor.getString(nameIndex)
                }
            }

            val finalFileName = fileName ?: "custom_translation_${System.currentTimeMillis()}.json"
            val outputFile = File(targetDir, finalFileName)
            
            inputStream.use { input ->
                outputFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            onSuccess()
        } catch (e: Exception) {
            onError(e.localizedMessage ?: "Manual import failed")
        }
    }
}

package io.github.staninna.warosso.data

import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.IOException

/** Text files the user picked through the Storage Access Framework. */
class Documents(private val resolver: ContentResolver) {

    @Throws(IOException::class)
    suspend fun read(uri: Uri): String = withContext(Dispatchers.IO) {
        resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: throw FileNotFoundException(uri.toString())
    }

    @Throws(IOException::class)
    suspend fun write(uri: Uri, text: String) = withContext(Dispatchers.IO) {
        // "wt" truncates: without it, overwriting a longer file would leave its tail behind.
        resolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(text) } ?: throw FileNotFoundException(uri.toString())
    }
}

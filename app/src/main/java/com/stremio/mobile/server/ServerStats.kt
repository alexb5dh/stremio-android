package com.stremio.mobile.server

import com.stremio.mobile.core.extensions.use
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL

/** Info hashes of the torrents the server has loaded (the only signal it gives): playing now, or until about 2 minutes after the last stream stops. */
suspend fun fetchActiveInfoHashes(baseUrl: String): Set<String>? = withContext(Dispatchers.IO) {
    runCatching {
        val connection = URL("$baseUrl/stats.json").openConnection() as HttpURLConnection
        connection.connectTimeout = 1_500
        connection.readTimeout = 3_000
        connection.setRequestProperty("Accept", "application/json")
        connection.use {
            if (it.responseCode != HttpURLConnection.HTTP_OK) return@runCatching null
            parseActiveInfoHashes(it.inputStream.bufferedReader().use { reader -> reader.readText() })
        }
    }.onFailure { Timber.d("Streaming server stats request failed: %s", it) }.getOrNull()
}

/** `/stats.json` is keyed by info hash. */
fun parseActiveInfoHashes(json: String): Set<String> = JSONObject(json).keys().asSequence().map { it.lowercase() }.toSet()

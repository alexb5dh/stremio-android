package com.stremio.mobile.core

import com.stremio.core.Storage
import org.json.JSONObject
import timber.log.Timber

/** The core's stream history (`StreamsBucket`), which it persists in [storage] but doesn't expose in any model. */
class StreamsHistory(private val storage: Storage) {
    private var cachedRaw: String? = null
    private var cachedEntries: Map<String, Map<String, PlayedStream>> = emptyMap()

    /** A null [videoId] matches any video of [metaId]. */
    fun contains(metaId: String, videoId: String?): Boolean {
        val videos = entries()[metaId] ?: return false
        return videoId == null || videoId in videos
    }

    fun playedStream(metaId: String, videoId: String): PlayedStream? = entries()[metaId]?.get(videoId)

    @Synchronized
    private fun entries(): Map<String, Map<String, PlayedStream>> {
        val raw = (storage.get(STORAGE_KEY) as? Storage.Result.Ok)?.value
        if (raw != cachedRaw) {
            cachedEntries = parse(raw)
            cachedRaw = raw
        }
        return cachedEntries
    }

    companion object {
        /** `STREAMS_STORAGE_KEY` in stremio-core. */
        const val STORAGE_KEY = "streams"

        /** Parses `{"items": [[{"metaId", "videoId"}, {"stream": {...}}], ...]}`, skipping malformed entries. */
        fun parse(raw: String?): Map<String, Map<String, PlayedStream>> {
            if (raw.isNullOrBlank()) return emptyMap()
            val items = runCatching { JSONObject(raw).getJSONArray("items") }.getOrElse {
                Timber.w(it, "Failed to parse the core's stream history")
                return emptyMap()
            }
            val result = mutableMapOf<String, MutableMap<String, PlayedStream>>()
            for (index in 0 until items.length()) {
                runCatching {
                    val entry = items.getJSONArray(index)
                    val key = entry.getJSONObject(0)
                    val stream = entry.getJSONObject(1).getJSONObject("stream")
                    result.getOrPut(key.getString("metaId")) { mutableMapOf() }[key.getString("videoId")] =
                        PlayedStream(streamSourceKey(stream))
                }.onFailure { Timber.w(it, "Skipping a malformed stream history entry") }
            }
            return result
        }
    }
}

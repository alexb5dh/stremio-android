package com.stremio.mobile.core

import com.stremio.core.Storage
import org.json.JSONObject
import timber.log.Timber

/**
 * Reads which videos stremio-core has a played stream for. The core keeps this history (its `StreamsBucket`) in [storage]
 * but doesn't expose it in any model.
 */
class StreamsHistory(private val storage: Storage) {
    private var cachedRaw: String? = null
    private var cachedVideoIds: Map<String, Set<String>> = emptyMap()

    /** Whether a stream was played for [videoId] of [metaId], or for any of its videos when [videoId] is null. */
    fun contains(metaId: String, videoId: String?): Boolean {
        val videoIds = videoIdsByMetaId()[metaId] ?: return false
        return videoId == null || videoId in videoIds
    }

    @Synchronized
    private fun videoIdsByMetaId(): Map<String, Set<String>> {
        val raw = (storage.get(STORAGE_KEY) as? Storage.Result.Ok)?.value
        if (raw != cachedRaw) {
            cachedVideoIds = parse(raw)
            cachedRaw = raw
        }
        return cachedVideoIds
    }

    companion object {
        /** `STREAMS_STORAGE_KEY` in stremio-core. */
        const val STORAGE_KEY = "streams"

        /** Parses the serialized `StreamsBucket`: `{"uid": ..., "items": [[{"metaId", "videoId"}, item], ...]}`. */
        fun parse(raw: String?): Map<String, Set<String>> {
            if (raw.isNullOrBlank()) return emptyMap()
            return runCatching {
                val items = JSONObject(raw).getJSONArray("items")
                val result = mutableMapOf<String, MutableSet<String>>()
                for (index in 0 until items.length()) {
                    val key = items.getJSONArray(index).getJSONObject(0)
                    result.getOrPut(key.getString("metaId")) { mutableSetOf() }.add(key.getString("videoId"))
                }
                result
            }.getOrElse {
                Timber.w(it, "Failed to parse the core's stream history")
                emptyMap()
            }
        }
    }
}

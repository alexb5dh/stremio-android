package com.stremio.mobile.core

import com.stremio.core.models.LoadableMetaItem
import com.stremio.core.models.LoadableStream
import com.stremio.core.models.MetaDetails
import com.stremio.core.types.resource.Stream
import com.stremio.mobile.core.extensions.optNullableString
import org.json.JSONObject

/** Why the core picked a stream as the last used one (`MetaDetails.last_used_stream`). */
enum class LastUsedMatch {
    /** This stream was played for the selected video. */
    EXACT,

    /** Not the stream played for the selected video: the core matched it by binge group, possibly from an earlier episode. */
    SAME_RELEASE,
}

/** The stream history entry for one video: the played stream's [streamSourceKey], null for source kinds without one. */
data class PlayedStream(val sourceKey: String?)

/** Tags the first of [streams] that is the core's last used [pick] with [match]. */
fun tagLastUsed(streams: List<CoreStream>, pick: LoadableStream?, match: (Stream) -> LastUsedMatch): List<CoreStream> {
    if (pick == null) return streams
    val stream = (pick.content as? LoadableStream.Content.Ready)?.value?.stream ?: return streams
    val index = streams.indexOfFirst { it.stream == stream && it.streamRequest == pick.request }
    if (index < 0) return streams
    return streams.toMutableList().also { it[index] = it[index].copy(lastUsed = match(stream)) }
}

/**
 * The core picks the played stream by source and falls back to one with the same binge group, so an existing history entry
 * alone doesn't make [pick] the played stream. Source kinds without a key (archives, NZB) can only be checked by the entry.
 */
fun lastUsedMatch(pick: Stream, played: PlayedStream?): LastUsedMatch = when {
    played == null -> LastUsedMatch.SAME_RELEASE
    played.sourceKey == null || played.sourceKey == streamSourceKey(pick) -> LastUsedMatch.EXACT
    else -> LastUsedMatch.SAME_RELEASE
}

/**
 * Whether the core may still produce the last used stream: the addon that served it, or the meta item it needs, is loading.
 * No meta item at all means no addon serves it, so there will be no pick.
 */
fun isLastUsedStreamPending(details: MetaDetails): Boolean {
    val pick = details.lastUsedStream
    if (pick != null) return pick.content == null || pick.content is LoadableStream.Content.Loading
    val metaItem = details.metaItem ?: return false
    return metaItem.content == null || metaItem.content is LoadableMetaItem.Content.Loading
}

/** Identifies the source of a stream converted by the core's protobuf bridge; null for kinds that aren't compared. */
fun streamSourceKey(stream: Stream): String? = when (val source = stream.source) {
    is Stream.Source.Url -> "url:${source.value.url}"
    is Stream.Source.YouTube -> "yt:${source.value.ytId}"
    is Stream.Source.Tramvai -> torrentSourceKey(source.value.infoHash, source.value.fileIdx)
    is Stream.Source.PlayerFrame -> "frame:${source.value.playerFrameUrl}"
    is Stream.Source.External -> externalSourceKey(source.value.externalUrl, source.value.androidTvUrl)
    else -> null
}

/** [streamSourceKey] of a stream as the core serializes it (flattened `StreamSource` fields). */
fun streamSourceKey(stream: JSONObject): String? = when {
    stream.has("url") -> "url:${stream.getString("url")}"
    stream.has("ytId") -> "yt:${stream.getString("ytId")}"
    stream.has("infoHash") -> torrentSourceKey(stream.getString("infoHash"), if (stream.isNull("fileIdx")) null else stream.getInt("fileIdx"))
    stream.has("playerFrameUrl") -> "frame:${stream.getString("playerFrameUrl")}"
    stream.has("externalUrl") || stream.has("androidTvUrl") ->
        externalSourceKey(stream.optNullableString("externalUrl"), stream.optNullableString("androidTvUrl"))
    else -> null
}

private fun torrentSourceKey(infoHash: String, fileIdx: Int?) = "torrent:${infoHash.lowercase()}:${fileIdx ?: ""}"

private fun externalSourceKey(externalUrl: String?, androidTvUrl: String?) = "external:${externalUrl.orEmpty()}|${androidTvUrl.orEmpty()}"

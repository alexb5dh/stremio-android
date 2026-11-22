package com.stremio.mobile.core

import com.stremio.core.models.LoadableMetaItem
import com.stremio.core.models.LoadableStream
import com.stremio.core.models.MetaDetails
import com.stremio.core.types.resource.Stream
import com.stremio.mobile.core.extensions.optNullableString
import org.json.JSONObject

/** Why the core picked `MetaDetails.last_used_stream`: played for this video, or matched by binge group. */
enum class LastUsedMatch {
    EXACT,
    SAME_RELEASE,
}

data class PlayedStream(val sourceKey: String?)

fun tagLastUsed(streams: List<CoreStream>, pick: LoadableStream?, match: (Stream) -> LastUsedMatch): List<CoreStream> {
    if (pick == null) return streams
    val stream = (pick.content as? LoadableStream.Content.Ready)?.value?.stream ?: return streams
    val index = streams.indexOfFirst { it.stream == stream && it.streamRequest == pick.request }
    if (index < 0) return streams
    return streams.toMutableList().also { it[index] = it[index].copy(lastUsed = match(stream)) }
}

/** The core falls back to a stream with the same binge group, so a history entry alone doesn't mean [pick] was played. */
fun lastUsedMatch(pick: Stream, played: PlayedStream?): LastUsedMatch =
    if (played != null && played.sourceKey == streamSourceKey(pick)) LastUsedMatch.EXACT else LastUsedMatch.SAME_RELEASE

/** Whether the core may still pick the last used stream; without a meta item it never will. */
fun isLastUsedStreamPending(details: MetaDetails): Boolean {
    val pick = details.lastUsedStream
    if (pick != null) return pick.content == null || pick.content is LoadableStream.Content.Loading
    val metaItem = details.metaItem ?: return false
    return metaItem.content == null || metaItem.content is LoadableMetaItem.Content.Loading
}

/** Null for source kinds that aren't compared (archives, NZB). */
fun streamSourceKey(stream: Stream): String? = when (val source = stream.source) {
    is Stream.Source.Url -> "url:${source.value.url}"
    is Stream.Source.YouTube -> "yt:${source.value.ytId}"
    is Stream.Source.Tramvai -> torrentSourceKey(source.value.infoHash, source.value.fileIdx)
    is Stream.Source.PlayerFrame -> "frame:${source.value.playerFrameUrl}"
    is Stream.Source.External -> externalSourceKey(source.value.externalUrl, source.value.androidTvUrl)
    else -> null
}

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

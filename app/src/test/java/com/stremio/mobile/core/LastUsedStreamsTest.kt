package com.stremio.mobile.core

import com.stremio.core.models.LoadableMetaItem
import com.stremio.core.models.LoadableStream
import com.stremio.core.models.MetaDetails
import com.stremio.core.models.OptionStream
import com.stremio.core.models.common.Loading
import com.stremio.core.types.addon.ResourcePath
import com.stremio.core.types.addon.ResourceRequest
import com.stremio.core.types.resource.Stream
import com.stremio.mobile.core.StreamFixtures.request
import com.stremio.mobile.core.StreamFixtures.stream
import com.stremio.mobile.core.StreamFixtures.torrent
import com.stremio.mobile.core.StreamFixtures.url
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LastUsedStreamsTest {
    private val addonA = request("https://a.example/manifest.json")
    private val addonB = request("https://b.example/manifest.json")

    private fun core(stream: Stream, request: ResourceRequest = addonA) = CoreStream(stream, request, metaRequest = null, addonTitle = "Addon")

    private fun ready(stream: Stream, request: ResourceRequest = addonA) =
        LoadableStream(request = request, content = LoadableStream.Content.Ready(OptionStream(stream = stream)))

    @Test
    fun `tags only the first stream equal to the pick from the same addon`() {
        val picked = url("https://cdn.example/b.mp4")
        val streams = listOf(core(url("https://cdn.example/a.mp4")), core(picked, addonB), core(picked), core(picked))

        val tagged = tagLastUsed(streams, ready(picked)) { LastUsedMatch.EXACT }

        assertEquals(listOf(null, null, LastUsedMatch.EXACT, null), tagged.map { it.lastUsed })
    }

    @Test
    fun `tags nothing without a ready pick in the list`() {
        val streams = listOf(core(url("https://cdn.example/a.mp4")))
        val loading = LoadableStream(request = addonA, content = LoadableStream.Content.Loading(Loading()))

        assertEquals(streams, tagLastUsed(streams, null) { LastUsedMatch.EXACT })
        assertEquals(streams, tagLastUsed(streams, loading) { LastUsedMatch.EXACT })
        assertEquals(streams, tagLastUsed(streams, ready(url("https://cdn.example/other.mp4"))) { LastUsedMatch.EXACT })
    }

    @Test
    fun `exact only when the played stream has the pick's source`() {
        val pick = torrent("0123456789abcdef0123456789abcdef01234567", 2)

        assertEquals(LastUsedMatch.SAME_RELEASE, lastUsedMatch(pick, played = null))
        assertEquals(LastUsedMatch.EXACT, lastUsedMatch(pick, PlayedStream(streamSourceKey(pick))))
        assertEquals(LastUsedMatch.SAME_RELEASE, lastUsedMatch(pick, PlayedStream("torrent:0123456789abcdef0123456789abcdef01234567:3")))
        assertEquals(LastUsedMatch.SAME_RELEASE, lastUsedMatch(pick, PlayedStream(sourceKey = null)))
        assertEquals(LastUsedMatch.EXACT, lastUsedMatch(stream(Stream.Source.Rar(Stream.Rar())), PlayedStream(sourceKey = null)))
    }

    @Test
    fun `source keys of the core's JSON and of converted streams agree`() {
        val hash = "0123456789abcdef0123456789abcdef01234567"
        val pairs = listOf(
            """{"url":"https://cdn.example/a.mp4"}""" to url("https://cdn.example/a.mp4"),
            """{"ytId":"abc"}""" to stream(Stream.Source.YouTube(Stream.YouTube(ytId = "abc"))),
            """{"infoHash":"${hash.uppercase()}","fileIdx":2,"announce":["tracker"]}""" to torrent(hash, 2),
            """{"infoHash":"$hash","fileIdx":null}""" to torrent(hash, null),
            """{"playerFrameUrl":"https://frame.example/"}""" to
                stream(Stream.Source.PlayerFrame(Stream.PlayerFrame(playerFrameUrl = "https://frame.example/"))),
            """{"externalUrl":"https://ext.example/","androidTvUrl":null}""" to
                stream(Stream.Source.External(Stream.External(externalUrl = "https://ext.example/"))),
        )
        for ((json, converted) in pairs) {
            assertEquals(json, streamSourceKey(converted), streamSourceKey(JSONObject(json)))
        }
        assertFalse(streamSourceKey(torrent(hash, null)) == streamSourceKey(torrent(hash, 0)))
    }

    @Test
    fun `archives have no source key`() {
        assertNull(streamSourceKey(JSONObject("""{"rarUrls":[["https://cdn.example/a.rar"]],"fileIdx":null}""")))
        assertNull(streamSourceKey(stream(Stream.Source.Rar(Stream.Rar()))))
    }

    @Test
    fun `pending while the pick or the meta item it needs is loading`() {
        val metaRequest =
            ResourceRequest(base = "https://meta.example/manifest.json", path = ResourcePath(resource = "meta", type = "movie", id = "tt1"))
        fun meta(content: LoadableMetaItem.Content<*>?) = LoadableMetaItem(title = "Meta", request = metaRequest, content = content)
        val metaLoading = meta(LoadableMetaItem.Content.Loading(Loading()))
        val metaFailed = meta(LoadableMetaItem.Content.Error(com.stremio.core.models.common.Error(message = "failed")))

        assertFalse(isLastUsedStreamPending(MetaDetails()))
        assertTrue(isLastUsedStreamPending(MetaDetails(metaItem = metaLoading)))
        assertFalse(isLastUsedStreamPending(MetaDetails(metaItem = metaFailed)))
        assertTrue(
            isLastUsedStreamPending(
                MetaDetails(lastUsedStream = LoadableStream(request = addonA, content = LoadableStream.Content.Loading(Loading()))),
            ),
        )
        assertFalse(isLastUsedStreamPending(MetaDetails(metaItem = metaLoading, lastUsedStream = ready(url("https://cdn.example/a.mp4")))))
    }
}

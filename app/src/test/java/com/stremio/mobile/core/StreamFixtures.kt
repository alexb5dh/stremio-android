package com.stremio.mobile.core

import com.stremio.core.types.addon.ResourcePath
import com.stremio.core.types.addon.ResourceRequest
import com.stremio.core.types.resource.Stream
import com.stremio.core.types.resource.StreamBehaviorHints
import com.stremio.core.types.resource.StreamDeepLinks

object StreamFixtures {
    fun request(base: String) = ResourceRequest(base = base, path = ResourcePath(resource = "stream", type = "movie", id = "tt1"))

    fun stream(source: Stream.Source<*>?, name: String = "1080p") = Stream(
        source = source,
        name = name,
        behaviorHints = StreamBehaviorHints(notWebReady = false),
        deepLinks = StreamDeepLinks(player = "", externalPlayer = StreamDeepLinks.ExternalPlayerLink()),
    )

    fun url(url: String) = stream(Stream.Source.Url(Stream.Url(url = url)))

    fun torrent(infoHash: String, fileIdx: Int?) = stream(Stream.Source.Tramvai(Stream.Tramvai(infoHash = infoHash, fileIdx = fileIdx)))
}

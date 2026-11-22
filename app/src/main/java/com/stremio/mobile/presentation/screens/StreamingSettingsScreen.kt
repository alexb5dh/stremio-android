package com.stremio.mobile.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.stremio.mobile.core.theme.AccentPurple
import com.stremio.mobile.core.theme.GlassSurface
import com.stremio.mobile.core.theme.MutedText
import com.stremio.mobile.presentation.components.ThemedButton
import com.stremio.mobile.presentation.components.ThemedCard
import com.stremio.mobile.presentation.components.UrlInputRow
import com.stremio.mobile.presentation.components.rememberLocalNetworkPermissionRequest
import com.stremio.mobile.server.StreamingServerState
import com.stremio.mobile.server.formatServerErrorMessage
import com.stremio.mobile.server.hasLocalNetworkPermission
import com.stremio.mobile.server.needsLocalNetworkPermission
import com.stremio.mobile.server.parseStreamingServerUrl

@Composable
fun StreamingSettingsScreen(
    serverState: StreamingServerState,
    customServerUrl: String,
    isCustomServerEnabled: Boolean,
    usesRemoteServer: Boolean,
    onRetryServer: () -> Unit,
    onSetCustomServer: (enabled: Boolean, url: String) -> Boolean,
    serverSettings: com.stremio.core.models.StreamingServer.Settings?,
    isSeedingEnabled: Boolean,
    minSeedsThreshold: Int,
    minDownloadSpeedBps: Long,
    preferredQuality: String,
    isAutoSwitchOnDeadStream: Boolean,
    onUpdateServerSettings: (com.stremio.core.models.StreamingServer.Settings) -> Unit,
    onSetSeedingEnabled: (Boolean) -> Unit,
    onSetMinSeedsThreshold: (Int) -> Unit,
    onSetMinDownloadSpeedBps: (Long) -> Unit,
    onSetPreferredQuality: (String) -> Unit,
    onSetAutoSwitchOnDeadStream: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SettingsHeader(title = "Streaming Server", onBack = onBack)

        // Status Card
        ThemedCard(
            modifier = Modifier
                .fillMaxWidth(),
            cornerRadius = 20.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = "Server Status",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val dotColor = when (serverState) {
                        is StreamingServerState.Ready -> Color(0xFF4CAF50)
                        is StreamingServerState.Starting -> Color(0xFFFFC107)
                        is StreamingServerState.Stopped -> Color(0xFF9E9E9E)
                        is StreamingServerState.Failed -> Color(0xFFF44336)
                    }
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )

                    val statusText = when (serverState) {
                        is StreamingServerState.Ready -> "Running"
                        is StreamingServerState.Starting -> "Starting..."
                        is StreamingServerState.Stopped -> "Stopped"
                        is StreamingServerState.Failed -> "Failed: ${formatServerErrorMessage(serverState.message)}"
                    }
                    Text(
                        text = "Status: $statusText",
                        color = Color.White,
                        fontSize = 14.sp,
                    )
                }

                if (serverState is StreamingServerState.Ready) {
                    Text(
                        text = "URL: ${serverState.baseUrl}",
                        color = MutedText,
                        fontSize = 12.sp,
                    )
                }

                if (usesRemoteServer && serverState is StreamingServerState.Failed) {
                    val context = LocalContext.current
                    var hasPermission by remember { mutableStateOf(hasLocalNetworkPermission(context)) }
                    // Both the system dialog and app settings resume the activity, so a grant is picked up here.
                    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                        val granted = hasLocalNetworkPermission(context)
                        if (granted && !hasPermission) onRetryServer()
                        hasPermission = granted
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ThemedButton(text = "Retry", onClick = onRetryServer)
                        if (!hasPermission) {
                            val requestLocalNetwork = rememberLocalNetworkPermissionRequest(openSettingsIfBlocked = true)
                            ThemedButton(text = "Allow Nearby Devices", onClick = { requestLocalNetwork {} })
                        }
                    }
                }
            }
        }

        CustomServerCard(
            customServerUrl = customServerUrl,
            isCustomServerEnabled = isCustomServerEnabled,
            onSetCustomServer = onSetCustomServer,
        )

        if (usesRemoteServer) {
            Text(
                text = "Server options of a custom server are changed on that device, e.g. in Stremio on your PC.",
                color = MutedText,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 4.dp),
            )
        } else if (serverSettings != null) {
            Text(
                text = "SERVER OPTIONS",
                color = MutedText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp)
            )

            val cacheSizes = listOf(
                0.0 to "Disabled",
                2147483648.0 to "2 GB",
                5368709120.0 to "5 GB",
                10737418240.0 to "10 GB",
                21474836480.0 to "20 GB"
            )

            val torrentProfiles = listOf(
                200L to "Default (Balanced)",
                80L to "Soft (Low Connections)",
                500L to "Fast (High Connections)",
                800L to "Ultra Fast (Unrestricted)"
            )

            val transcodeProfiles = listOf(
                "disabled" to "No Transcoding",
                "default" to "Default (Balanced)",
                "fast" to "Fast (Lower Quality)",
                "slow" to "High Quality (High CPU)"
            )

            SettingsDropdownRow(
                title = "Server Cache Size",
                selectedValue = serverSettings.cacheSize ?: 10737418240.0,
                options = cacheSizes,
                onSelect = { onUpdateServerSettings(serverSettings.copy(cacheSize = it)) },
                description = "Disk cache allocated for torrent file buffers"
            )

            SettingsDropdownRow(
                title = "Torrent Connections Limit",
                selectedValue = serverSettings.btMaxConnections,
                options = torrentProfiles,
                onSelect = { onUpdateServerSettings(serverSettings.copy(btMaxConnections = it)) },
                description = "Maximum peers that can connect simultaneously"
            )

            SettingsDropdownRow(
                title = "Transcoding Profile",
                selectedValue = serverSettings.transcodeProfile ?: "default",
                options = transcodeProfiles,
                onSelect = { onUpdateServerSettings(serverSettings.copy(transcodeProfile = it)) },
                description = "Adjust CPU usage for mobile video stream transcoding"
            )

            SettingsToggleRow(
                title = "Proxy Video Streams",
                checked = serverSettings.proxyStreamsEnabled,
                onCheckedChange = { onUpdateServerSettings(serverSettings.copy(proxyStreamsEnabled = it)) },
                description = "Force video player stream traffic through server proxy"
            )

            SettingsToggleRow(
                title = "Seeding Enabled",
                checked = isSeedingEnabled,
                onCheckedChange = onSetSeedingEnabled,
                description = "Continue seeding torrents in background after download finishes"
            )
        }

        Text(
            text = "STREAM HEALTH",
            color = MutedText,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp)
        )

        val minSeedsOptions = listOf(0 to "Any", 1 to "1", 2 to "2", 3 to "3", 5 to "5")
        val minSpeedOptions = listOf(
            0L to "Disabled",
            25_000L to "25 KB/s",
            50_000L to "50 KB/s",
            100_000L to "100 KB/s",
            200_000L to "200 KB/s",
        )
        val qualityOptions = listOf(
            "Any" to "Any",
            "2160p" to "4K",
            "1080p" to "1080p",
            "720p" to "720p",
            "480p" to "480p",
        )

        SettingsDropdownRow(
            title = "Minimum Seeds",
            selectedValue = minSeedsThreshold,
            options = minSeedsOptions,
            onSelect = onSetMinSeedsThreshold,
            description = "Flag a playing stream as dead when it has fewer peers than this"
        )

        SettingsDropdownRow(
            title = "Minimum Download Speed",
            selectedValue = minDownloadSpeedBps,
            options = minSpeedOptions,
            onSelect = onSetMinDownloadSpeedBps,
            description = "Flag a playing stream as too slow below this throughput"
        )

        SettingsDropdownRow(
            title = "Preferred Video Quality",
            selectedValue = preferredQuality,
            options = qualityOptions,
            onSelect = onSetPreferredQuality,
            description = "Used to break ties when sorting streams or picking a fallback"
        )

        SettingsToggleRow(
            title = "Auto-Switch on Dead Stream",
            checked = isAutoSwitchOnDeadStream,
            onCheckedChange = onSetAutoSwitchOnDeadStream,
            description = "Automatically play the next best stream instead of asking"
        )
    }
}

@Composable
private fun CustomServerCard(
    customServerUrl: String,
    isCustomServerEnabled: Boolean,
    onSetCustomServer: (enabled: Boolean, url: String) -> Boolean,
) {
    var urlInput by rememberSaveable { mutableStateOf(customServerUrl) }
    var urlError by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val requestLocalNetwork = rememberLocalNetworkPermissionRequest()

    // Asked before saving, which may restart the app; a denial still saves.
    fun save(newEnabled: Boolean, url: String) {
        val commit = { urlError = !onSetCustomServer(newEnabled, url) }
        val parsedUrl = parseStreamingServerUrl(url)
        if (newEnabled && parsedUrl != null && needsLocalNetworkPermission(context, parsedUrl)) {
            requestLocalNetwork { commit() }
        } else {
            commit()
        }
    }

    SettingsToggleRow(
        title = "Use Custom Server",
        checked = isCustomServerEnabled,
        // Turning off doesn't save edits in the field.
        onCheckedChange = { checked -> save(checked, if (checked) urlInput else customServerUrl) },
        description = "Stream through a server on another device, e.g. Stremio on your PC, instead of the built-in one. " +
            "Switching servers restarts the app.",
    )
    UrlInputRow(
        value = urlInput,
        onValueChange = { urlInput = it; urlError = false },
        placeholder = "http://192.168.1.10:11470",
        buttonText = "Save",
        onSubmit = { save(isCustomServerEnabled, urlInput) },
        errorText = "Enter a valid http(s) URL, e.g. http://192.168.1.10:11470".takeIf { urlError },
    )
}

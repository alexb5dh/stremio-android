package com.stremio.mobile

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.posthog.PostHog
import com.posthog.android.PostHogAndroid
import com.posthog.android.PostHogAndroidConfig
import java.io.File
import java.util.concurrent.ConcurrentHashMap

private const val LOGGED_OUT_MARKER = ""

internal data class IdentityChange(val reset: Boolean, val identify: String?, val marker: String)

/**
 * Decides how to move PostHog from the last synced identity [identified] to [userId]: `null` when nothing changes.
 * [identified] is `null` when never synced (PostHog may hold an identity from an older app version), [LOGGED_OUT_MARKER] when logged out.
 */
internal fun identityChange(identified: String?, userId: String?): IdentityChange? {
    val target = userId ?: LOGGED_OUT_MARKER
    if (identified == target) return null
    return IdentityChange(reset = identified != LOGGED_OUT_MARKER, identify = userId, marker = target)
}

object Telemetry {
    private const val IDENTIFIED_USER_KEY = "posthog_identified_user"

    private lateinit var appContext: Context
    private lateinit var preferences: SharedPreferences
    private var userId: String? = null
    private val customKeys = ConcurrentHashMap<String, String>()
    private val crashlytics get() = FirebaseCrashlytics.getInstance()

    @Volatile
    var isEnabled: Boolean = false
        private set

    fun init(context: Context, enabled: Boolean) {
        appContext = context.applicationContext
        preferences = appContext.getSharedPreferences("telemetry", Context.MODE_PRIVATE)
        applyEnabled(enabled)
    }

    fun setEnabled(enabled: Boolean) {
        if (enabled == isEnabled) return
        applyEnabled(enabled)
        if (enabled) {
            syncUser()
            customKeys.forEach { (key, value) -> crashlytics.setCustomKey(key, value) }
        }
    }

    /** Hashed ID of the logged-in user, or `null` after logout. */
    fun setUser(id: String?) {
        userId = id
        if (isEnabled) syncUser()
    }

    fun setCustomKey(key: String, value: String) {
        customKeys[key] = value
        if (isEnabled) crashlytics.setCustomKey(key, value)
    }

    // Firebase SDKs start disabled via AndroidManifest meta-data. The runtime overrides below are persisted by each SDK
    // and take precedence over the manifest flags on later launches.
    private fun applyEnabled(enabled: Boolean) {
        isEnabled = enabled
        setPostHogEnabled(enabled)

        crashlytics.setCrashlyticsCollectionEnabled(enabled)
        // Drops reports finalized from earlier sessions.
        if (!enabled) crashlytics.deleteUnsentReports()

        FirebaseAnalytics.getInstance(appContext).setAnalyticsCollectionEnabled(enabled)
    }

    // PostHog opt-out only blocks events, not its config/flags requests, so it is set up only while enabled.
    // Until set up, all PostHog.* calls are no-ops.
    private fun setPostHogEnabled(enabled: Boolean) {
        if (!isPostHogConfigured()) return

        if (enabled) {
            PostHogAndroid.setup(appContext, createPostHogConfig())
            // Clears an opt-out persisted by older app versions, which setup prefers over the config.
            PostHog.optIn()
        } else {
            PostHog.close()
            // Events queued but not yet sent must not go out after a later opt-in.
            postHogQueueDir().deleteRecursively()
        }
    }

    // PostHog ignores identify() while identified and no-ops while not set up, so the last identified ID is tracked here.
    private fun syncUser() {
        val id = userId
        crashlytics.setUserId(id ?: "")
        if (!isPostHogConfigured()) return

        val change = identityChange(preferences.getString(IDENTIFIED_USER_KEY, null), id) ?: return
        if (change.reset) PostHog.reset()
        change.identify?.let { PostHog.identify(it) }
        preferences.edit().putString(IDENTIFIED_USER_KEY, change.marker).apply()
    }

    private fun isPostHogConfigured() = BuildConfig.POSTHOG_API_KEY.isNotEmpty()

    // PostHog's default location, set explicitly so the directory deleted on disable is the one configured here.
    private fun postHogQueueDir() = File(appContext.cacheDir, "posthog-disk-queue")

    private fun createPostHogConfig() = PostHogAndroidConfig(
        apiKey = BuildConfig.POSTHOG_API_KEY,
        host = BuildConfig.POSTHOG_HOST
    ).apply {
        storagePrefix = postHogQueueDir().absolutePath
        captureApplicationLifecycleEvents = true
        captureScreenViews = true
        sessionReplay = false
    }
}

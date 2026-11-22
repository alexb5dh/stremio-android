package com.stremio.mobile

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.perf.FirebasePerformance
import com.google.firebase.perf.application.AppStateMonitor
import com.posthog.PostHog
import com.posthog.android.PostHogAndroid
import com.posthog.android.PostHogAndroidConfig
import com.stremio.mobile.auth.FacebookLoginBridge
import java.io.File
import java.util.concurrent.ConcurrentHashMap

internal const val LOGGED_OUT_MARKER = ""

internal data class IdentityChange(val reset: Boolean, val identify: String?)

/**
 * Decides how to move PostHog from the last synced identity [identified] to [userId]: `null` when nothing changes.
 * [identified] is `null` when never synced (PostHog may hold an identity from an older app version), [LOGGED_OUT_MARKER] when logged out.
 */
internal fun identityChange(identified: String?, userId: String?): IdentityChange? {
    if (identified == (userId ?: LOGGED_OUT_MARKER)) return null
    return IdentityChange(reset = identified != LOGGED_OUT_MARKER, identify = userId)
}

object Telemetry {
    private const val IDENTIFIED_USER_KEY = "posthog_identified_user"

    private lateinit var appContext: Context
    private lateinit var preferences: SharedPreferences
    private var userId: String? = null
    private val customKeys = ConcurrentHashMap<String, String>()
    private val crashlytics get() = FirebaseCrashlytics.getInstance()
    private var isPerformanceStarted = false

    @Volatile
    var isEnabled: Boolean = false
        private set

    fun init(context: Context, enabled: Boolean) {
        appContext = context.applicationContext
        preferences = appContext.getSharedPreferences("telemetry", Context.MODE_PRIVATE)
        applyEnabled(enabled, atStartup = true)
    }

    fun setEnabled(enabled: Boolean) {
        if (enabled == isEnabled) return
        applyEnabled(enabled, atStartup = false)
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

    // Crashlytics and Firebase Analytics start disabled via AndroidManifest meta-data. The runtime overrides below are persisted
    // by each SDK and take precedence over the manifest flags on later launches.
    private fun applyEnabled(enabled: Boolean, atStartup: Boolean) {
        isEnabled = enabled
        setPostHogEnabled(enabled)
        setPerformanceEnabled(enabled, atStartup)
        // The SDK can't be stopped once started, so turning telemetry off takes effect on the next launch.
        if (enabled) FacebookLoginBridge.startSdk(appContext)

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

    // Firebase Performance fetches Remote Config even with collection disabled, so it must not be constructed while off. It is
    // constructed on the first activity resume via AppStateMonitor, so unregistering that at startup keeps it from starting.
    private fun setPerformanceEnabled(enabled: Boolean, atStartup: Boolean) {
        when {
            isPerformanceStarted -> FirebasePerformance.getInstance().setPerformanceCollectionEnabled(enabled)
            enabled && atStartup -> {
                // Overrides a persisted false from an earlier in-app opt-out.
                FirebasePerformance.getInstance().setPerformanceCollectionEnabled(true)
                isPerformanceStarted = true
            }
            // Starting now would upload network metrics the SDK's instrumentation queued in memory while off; starts on next launch.
            enabled -> Unit
            else -> AppStateMonitor.getInstance().unregisterActivityLifecycleCallbacks(appContext)
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
        preferences.edit().putString(IDENTIFIED_USER_KEY, change.identify ?: LOGGED_OUT_MARKER).apply()
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

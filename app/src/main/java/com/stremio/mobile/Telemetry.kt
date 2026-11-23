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

/** [identified] is `null` if never synced, [LOGGED_OUT_MARKER] if logged out. Returns `null` when nothing changes. */
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

    fun setUser(id: String?) {
        userId = id
        if (isEnabled) syncUser()
    }

    fun setCustomKey(key: String, value: String) {
        customKeys[key] = value
        if (isEnabled) crashlytics.setCustomKey(key, value)
    }

    private fun applyEnabled(enabled: Boolean, atStartup: Boolean) {
        isEnabled = enabled
        setPostHogEnabled(enabled)
        setPerformanceEnabled(enabled, atStartup)
        // Can't be stopped; opt-out applies on next launch.
        if (enabled) FacebookLoginBridge.startSdk(appContext)

        crashlytics.setCrashlyticsCollectionEnabled(enabled)
        if (!enabled) crashlytics.deleteUnsentReports()

        FirebaseAnalytics.getInstance(appContext).setAnalyticsCollectionEnabled(enabled)
    }

    // Set up only while enabled: its opt-out still lets config/flags requests through.
    private fun setPostHogEnabled(enabled: Boolean) {
        if (!isPostHogConfigured()) return

        if (enabled) {
            PostHogAndroid.setup(appContext, createPostHogConfig())
            // Clears an opt-out persisted by older app versions.
            PostHog.optIn()
        } else {
            PostHog.close()
            postHogQueueDir().deleteRecursively()
        }
    }

    // Fetches Remote Config even when disabled, so it must not be constructed while off; AppStateMonitor constructs it on first resume.
    private fun setPerformanceEnabled(enabled: Boolean, atStartup: Boolean) {
        when {
            isPerformanceStarted -> FirebasePerformance.getInstance().setPerformanceCollectionEnabled(enabled)
            enabled && atStartup -> {
                // Overrides a persisted in-app opt-out.
                FirebasePerformance.getInstance().setPerformanceCollectionEnabled(true)
                isPerformanceStarted = true
            }
            // Would upload metrics queued while off; starts on next launch.
            enabled -> Unit
            else -> AppStateMonitor.getInstance().unregisterActivityLifecycleCallbacks(appContext)
        }
    }

    // PostHog ignores identify() while identified, so the last synced ID is tracked here.
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

    // PostHog's default, set explicitly to match the directory deleted on disable.
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

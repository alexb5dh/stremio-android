package com.stremio.mobile

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.posthog.PostHog
import com.posthog.android.PostHogAndroid
import com.posthog.android.PostHogAndroidConfig

object Telemetry {
    private const val IDENTIFIED_USER_KEY = "posthog_identified_user"

    private lateinit var appContext: Context
    private lateinit var preferences: SharedPreferences
    private var userId: String? = null

    @Volatile
    var isEnabled: Boolean = false
        private set

    fun init(context: Context, enabled: Boolean) {
        appContext = context.applicationContext
        preferences = appContext.getSharedPreferences("telemetry", Context.MODE_PRIVATE)
        applyEnabled(enabled)
    }

    fun setEnabled(enabled: Boolean) {
        applyEnabled(enabled)
        if (enabled) syncUser()
    }

    /** Hashed ID of the logged-in user, or `null` after logout. */
    fun setUser(id: String?) {
        userId = id
        if (isEnabled) syncUser()
        // Crashlytics keeps the user ID in memory regardless of its collection flag.
        else if (id == null) FirebaseCrashlytics.getInstance().setUserId("")
    }

    fun setCustomKey(key: String, value: String) {
        if (isEnabled) FirebaseCrashlytics.getInstance().setCustomKey(key, value)
    }

    // Firebase SDKs start disabled via AndroidManifest meta-data. The runtime overrides below are persisted by each SDK
    // and take precedence over the manifest flags on later launches.
    private fun applyEnabled(enabled: Boolean) {
        isEnabled = enabled
        setPostHogEnabled(enabled)

        val crashlytics = FirebaseCrashlytics.getInstance()
        crashlytics.setCrashlyticsCollectionEnabled(enabled)
        // Drops finalized reports from earlier sessions; the open session gets no data while disabled (see isEnabled checks).
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
        }
    }

    // PostHog ignores identify() for a different ID while already identified, and identify()/reset() are no-ops while it is
    // not set up, so the last identified ID is tracked here and reconciled whenever telemetry is on.
    private fun syncUser() {
        val id = userId
        FirebaseCrashlytics.getInstance().setUserId(id ?: "")
        if (!isPostHogConfigured()) return

        val identified = preferences.getString(IDENTIFIED_USER_KEY, null)
        if (identified == id) return
        if (identified != null) PostHog.reset()
        if (id != null) PostHog.identify(id)
        preferences.edit().putString(IDENTIFIED_USER_KEY, id).apply()
    }

    private fun isPostHogConfigured() = BuildConfig.POSTHOG_API_KEY.isNotEmpty()

    private fun createPostHogConfig() = PostHogAndroidConfig(
        apiKey = BuildConfig.POSTHOG_API_KEY,
        host = BuildConfig.POSTHOG_HOST
    ).apply {
        captureApplicationLifecycleEvents = true
        captureScreenViews = true
        sessionReplay = false
        sessionReplayConfig.maskAllTextInputs = true
        sessionReplayConfig.maskAllImages = true
    }
}

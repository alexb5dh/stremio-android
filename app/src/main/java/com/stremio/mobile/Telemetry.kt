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

object Telemetry {
    private const val IDENTIFIED_USER_KEY = "posthog_identified_user"
    private const val LOGGED_OUT_MARKER = ""

    private lateinit var appContext: Context
    private lateinit var preferences: SharedPreferences
    private var userId: String? = null
    private val customKeys = ConcurrentHashMap<String, String>()

    @Volatile
    var isEnabled: Boolean = false
        private set

    fun init(context: Context, enabled: Boolean) {
        appContext = context.applicationContext
        preferences = appContext.getSharedPreferences("telemetry", Context.MODE_PRIVATE)
        // Default queue location of older app versions; nothing reads it since storagePrefix was changed.
        File(appContext.cacheDir, "posthog-disk-queue").deleteRecursively()
        applyEnabled(enabled)
    }

    fun setEnabled(enabled: Boolean) {
        applyEnabled(enabled)
        if (enabled) {
            syncUser()
            customKeys.forEach { (key, value) -> FirebaseCrashlytics.getInstance().setCustomKey(key, value) }
        }
    }

    /** Hashed ID of the logged-in user, or `null` after logout. */
    fun setUser(id: String?) {
        userId = id
        if (isEnabled) syncUser()
    }

    fun setCustomKey(key: String, value: String) {
        customKeys[key] = value
        if (isEnabled) FirebaseCrashlytics.getInstance().setCustomKey(key, value)
    }

    // Firebase SDKs start disabled via AndroidManifest meta-data. The runtime overrides below are persisted by each SDK
    // and take precedence over the manifest flags on later launches.
    private fun applyEnabled(enabled: Boolean) {
        isEnabled = enabled
        setPostHogEnabled(enabled)

        val crashlytics = FirebaseCrashlytics.getInstance()
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

    // PostHog ignores identify() for a different ID while already identified, and identify()/reset() are no-ops while it is
    // not set up, so the last identified ID is tracked here and reconciled whenever telemetry is on.
    // A missing marker means PostHog may still hold an identity from an older app version, so it is reset once.
    private fun syncUser() {
        val id = userId
        FirebaseCrashlytics.getInstance().setUserId(id ?: "")
        if (!isPostHogConfigured()) return

        val target = id ?: LOGGED_OUT_MARKER
        val identified = preferences.getString(IDENTIFIED_USER_KEY, null)
        if (identified == target) return
        if (identified != LOGGED_OUT_MARKER) PostHog.reset()
        if (id != null) PostHog.identify(id)
        preferences.edit().putString(IDENTIFIED_USER_KEY, target).apply()
    }

    private fun isPostHogConfigured() = BuildConfig.POSTHOG_API_KEY.isNotEmpty()

    private fun postHogQueueDir() = File(appContext.noBackupFilesDir, "posthog-queue")

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

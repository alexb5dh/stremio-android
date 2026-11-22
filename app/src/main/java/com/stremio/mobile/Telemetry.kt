package com.stremio.mobile

import android.content.Context
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.perf.FirebasePerformance
import com.posthog.PostHog
import com.posthog.android.PostHogAndroid
import com.posthog.android.PostHogAndroidConfig

object Telemetry {
    // Firebase SDKs start disabled via AndroidManifest meta-data. The runtime overrides below are persisted by each SDK
    // and take precedence over the manifest flags on later launches.
    fun setEnabled(context: Context, enabled: Boolean) {
        setPostHogEnabled(context, enabled)

        val crashlytics = FirebaseCrashlytics.getInstance()
        crashlytics.setCrashlyticsCollectionEnabled(enabled)
        // Crashlytics keeps reports recorded while disabled and would upload them once enabled.
        if (!enabled) crashlytics.deleteUnsentReports()

        FirebaseAnalytics.getInstance(context).setAnalyticsCollectionEnabled(enabled)
        FirebasePerformance.getInstance().isPerformanceCollectionEnabled = enabled
    }

    // PostHog opt-out only blocks events, not its config/flags requests, so it is set up only while enabled.
    // Until set up, all PostHog.* calls are no-ops.
    private fun setPostHogEnabled(context: Context, enabled: Boolean) {
        if (BuildConfig.POSTHOG_API_KEY.isEmpty()) return

        if (enabled) {
            PostHogAndroid.setup(context.applicationContext, createPostHogConfig())
            // Clears an opt-out persisted by an earlier disable, which setup prefers over the config.
            PostHog.optIn()
        } else {
            PostHog.optOut()
            PostHog.close()
        }
    }

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

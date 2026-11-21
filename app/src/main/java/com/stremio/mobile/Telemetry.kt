package com.stremio.mobile

import android.content.Context
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.perf.FirebasePerformance
import com.posthog.PostHog

object Telemetry {
    // Firebase SDKs start disabled via AndroidManifest meta-data; these runtime overrides are persisted by each SDK.
    fun setEnabled(context: Context, enabled: Boolean) {
        if (enabled) PostHog.optIn() else PostHog.optOut()
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(enabled)
        FirebaseAnalytics.getInstance(context).setAnalyticsCollectionEnabled(enabled)
        FirebasePerformance.getInstance().isPerformanceCollectionEnabled = enabled
    }
}

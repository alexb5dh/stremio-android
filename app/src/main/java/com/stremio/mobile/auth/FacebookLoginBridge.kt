package com.stremio.mobile.auth

import android.content.Context
import com.facebook.CallbackManager
import com.facebook.FacebookSdk

object FacebookLoginBridge {
    val callbackManager: CallbackManager = CallbackManager.Factory.create()

    val isSdkStarted: Boolean get() = FacebookSdk.isInitialized()

    /**
     * Starts the Facebook SDK, whose init provider is removed from the manifest so that it doesn't contact Facebook on every
     * launch. Called on telemetry opt-in and on a Facebook login tap. Must run before any `LoginManager` use.
     */
    @Suppress("DEPRECATION") // The supported replacement, fullyInitialize(), doesn't initialize the SDK on its own.
    fun startSdk(context: Context) {
        FacebookSdk.sdkInitialize(context.applicationContext)
        // Graph responses are rejected until fully initialized; not persisted, unlike setAutoInitEnabled().
        FacebookSdk.fullyInitialize()
    }
}

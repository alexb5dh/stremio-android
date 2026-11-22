package com.stremio.mobile.auth

import android.content.Context
import com.facebook.CallbackManager
import com.facebook.FacebookSdk

object FacebookLoginBridge {
    val callbackManager: CallbackManager = CallbackManager.Factory.create()

    val isSdkStarted: Boolean get() = FacebookSdk.isInitialized()

    /** Replaces the init provider removed from the manifest; must run before any `LoginManager` use. */
    @Suppress("DEPRECATION")
    fun startSdk(context: Context) {
        FacebookSdk.sdkInitialize(context.applicationContext)
        // Graph responses are rejected until this runs.
        FacebookSdk.fullyInitialize()
    }
}

package com.academicstrong.nutrition.ai

import android.app.Application
import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.initialize

/**
 * Call once from your Application.onCreate():
 *   installAppCheck(this, BuildConfig.DEBUG)
 *
 * Debug builds use the debug provider: run the app once, copy the debug token from Logcat,
 * and register it in Firebase console > App Check > Apps > Manage debug tokens.
 * Release builds use Play Integrity, which needs the app registered in App Check.
 */
fun installAppCheck(app: Application, isDebug: Boolean) {
    Firebase.initialize(context = app)
    Firebase.appCheck.installAppCheckProviderFactory(
        if (isDebug) DebugAppCheckProviderFactory.getInstance()
        else PlayIntegrityAppCheckProviderFactory.getInstance()
    )
}

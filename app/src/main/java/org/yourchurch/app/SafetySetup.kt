package org.yourchurch.app

import android.content.Context
import android.content.pm.ApplicationInfo
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Safety and quality:
 *  - App Check lets Firebase tell the real app from a copy or a script. It only blocks anything once you switch
 *    "enforcement" on in the Firebase console, which you should do only after the app is on Google Play.
 *  - Crash reports (Crashlytics) show what went wrong on people's phones.
 */
object SafetySetup {
    /**
     * Switch this to true after the app is on Google Play and App Check is set up in the Firebase console
     * (see NEW-FEATURES-STEPS.md). Until then it only adds a possible source of trouble.
     */
    private const val APP_CHECK_ENABLED = false

    fun init(context: Context) {
        if (!APP_CHECK_ENABLED) return
        runCatching {
            FirebaseApp.initializeApp(context)
            val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                if (debuggable) DebugAppCheckProviderFactory.getInstance()
                else PlayIntegrityAppCheckProviderFactory.getInstance()
            )
        }
    }

    /** Lets crash reports say which (anonymous) account had the problem. */
    fun identify(uid: String?) {
        runCatching { FirebaseCrashlytics.getInstance().setUserId(uid ?: "") }
    }
}

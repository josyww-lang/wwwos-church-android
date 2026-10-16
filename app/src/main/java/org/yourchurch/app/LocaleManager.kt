package org.yourchurch.app

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import java.util.Locale

/**
 * Lets people pick the app's language (English, Norsk, Afaan Oromoo)
 * independently of the phone's language. "system" means follow the phone.
 */
object LocaleManager {
    private const val PREFS = "settings"
    private const val KEY = "language"
    const val SYSTEM = "system"

    fun saved(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, SYSTEM) ?: SYSTEM

    fun save(context: Context, code: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, code).apply()
    }

    /** Language code the app is currently using (also used for Firebase's emails). */
    fun languageCode(context: Context): String {
        val code = saved(context)
        return if (code == SYSTEM) Resources.getSystem().configuration.locales[0].language else code
    }

    /** Called from MainActivity.attachBaseContext so every screen uses the chosen language. */
    fun wrap(base: Context): Context {
        val code = saved(base)
        if (code == SYSTEM) {
            Locale.setDefault(Resources.getSystem().configuration.locales[0])
            return base
        }
        val locale = Locale.forLanguageTag(code)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }
}

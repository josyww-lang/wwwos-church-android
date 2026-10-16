package org.yourchurch.app.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.yourchurch.app.data.BrandingStore

enum class ThemeMode { System, Light, Dark }

/** The person's choice of light / dark and colour combination. Kept on the phone; changing it restyles the app at once. */
object ThemeSettings {
    private const val FILE = "settings"

    var mode by mutableStateOf(ThemeMode.System)
        private set
    var accent by mutableIntStateOf(0)
        private set
    /** True: each section has its own colours. False (the default): the chosen combination everywhere. */
    var colorful by mutableStateOf(false)
        private set
    /** True: use the colours the church leaders chose. False: the person picked their own. */
    var followChurch by mutableStateOf(true)
        private set
    private var loaded = false

    /** The combination actually used: the church's, unless the person chose their own. */
    val effectiveAccent: Int
        get() = (if (followChurch) BrandingStore.churchPalette ?: 0 else accent).coerceIn(0, ACCENTS.lastIndex)

    fun load(context: Context) {
        if (loaded) return
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        mode = ThemeMode.entries.getOrElse(prefs.getInt("theme_mode", 0)) { ThemeMode.System }
        accent = prefs.getInt("theme_palette", 0).coerceIn(0, ACCENTS.lastIndex)
        // Someone who already picked colours keeps them; everyone else starts with the church's.
        followChurch = prefs.getBoolean("theme_follow_church", !prefs.contains("theme_palette"))
        colorful = prefs.getBoolean("theme_colorful", false)
        loaded = true
    }

    fun setMode(context: Context, value: ThemeMode) {
        mode = value
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putInt("theme_mode", value.ordinal).apply()
    }

    fun setAccent(context: Context, index: Int) {
        accent = index.coerceIn(0, ACCENTS.lastIndex)
        followChurch = false
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putInt("theme_palette", accent).putBoolean("theme_follow_church", false).apply()
    }

    /** Go back to the colours the church leaders chose. */
    fun useChurchColours(context: Context) {
        followChurch = true
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean("theme_follow_church", true).apply()
    }

    fun setColorful(context: Context, value: Boolean) {
        colorful = value
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean("theme_colorful", value).apply()
    }
}

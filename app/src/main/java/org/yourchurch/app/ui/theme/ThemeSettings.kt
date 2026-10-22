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
    /**
     * The colour behind all the pages (not the top card, not the bottom bar), chosen with the sliders: 0 follows the colours chosen above.
     * The light look and the dark look each remember their own.
     */
    var pageLightColor by mutableIntStateOf(0)
        private set
    var pageDarkColor by mutableIntStateOf(0)
        private set
    /** The main colour of the app, if the person made their own with the sliders (0: none, the combinations are used). */
    var customMain by mutableIntStateOf(0)
        private set
    val usesCustom: Boolean get() = !followChurch && customMain != 0
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
        pageLightColor = prefs.getInt("page_light_color", 0)
        pageDarkColor = prefs.getInt("page_dark_color", 0)
        customMain = prefs.getInt("theme_custom_main", 0)
        loaded = true
    }

    fun setMode(context: Context, value: ThemeMode) {
        mode = value
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putInt("theme_mode", value.ordinal).apply()
    }

    fun setAccent(context: Context, index: Int) {
        accent = index.coerceIn(0, ACCENTS.lastIndex)
        followChurch = false
        customMain = 0
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putInt("theme_palette", accent).putBoolean("theme_follow_church", false).putInt("theme_custom_main", 0).apply()
    }

    /** Go back to the colours the church leaders chose. */
    fun useChurchColours(context: Context) {
        followChurch = true
        customMain = 0
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean("theme_follow_church", true).putInt("theme_custom_main", 0).apply()
    }

    /** Chooses the page background for the light look ([dark] false) or the dark look ([dark] true). 0 is automatic. */
    fun setPageColor(context: Context, dark: Boolean, argb: Int) {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
        if (dark) {
            pageDarkColor = argb
            prefs.putInt("page_dark_color", argb)
        } else {
            pageLightColor = argb
            prefs.putInt("page_light_color", argb)
        }
        prefs.apply()
    }

    /** The person made their own main colour with the sliders. */
    fun setCustomMain(context: Context, argb: Int) {
        customMain = argb
        followChurch = false
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putInt("theme_custom_main", argb).putBoolean("theme_follow_church", false).apply()
    }

    fun setColorful(context: Context, value: Boolean) {
        colorful = value
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean("theme_colorful", value).apply()
    }
}

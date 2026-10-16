package org.yourchurch.app.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/** How big the text is: 0 = normal, 1 = large, 2 = extra large. Kept on the phone; changes the whole app at once. */
object TextSize {
    private const val FILE = "settings"
    private const val KEY = "text_size"

    var level by mutableIntStateOf(0)
        private set
    private var loaded = false

    val factor: Float
        get() = when (level) {
            1 -> 1.15f
            2 -> 1.3f
            else -> 1f
        }

    fun load(context: Context) {
        if (loaded) return
        level = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getInt(KEY, 0).coerceIn(0, 2)
        loaded = true
    }

    fun set(context: Context, value: Int) {
        level = value.coerceIn(0, 2)
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putInt(KEY, level).apply()
    }
}

package org.yourchurch.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** The colour families used to tell the sections of the app apart (when the person switches this on in Appearance). */
enum class ToneKind { Primary, Gold, Purple, Teal, Coral, Blue, Green, Rose, Slate }

/** A soft background ([container]), a strong colour for icons, words and bars ([accent]), and the text colour on top of [accent]. */
class Tone(val container: Color, val accent: Color, val onAccent: Color)

private class Palette(val lightContainer: Long, val lightAccent: Long, val darkContainer: Long, val darkAccent: Long)

private val PALETTES = mapOf(
    ToneKind.Gold to Palette(0xFFFBF1D0, 0xFF8A6D0B, 0xFF4A3C0A, 0xFFF1D98A),
    ToneKind.Purple to Palette(0xFFECE5FB, 0xFF5B3FA8, 0xFF332654, 0xFFCDBBFF),
    ToneKind.Teal to Palette(0xFFDDF3EA, 0xFF12705A, 0xFF12433A, 0xFF8AE0C8),
    ToneKind.Coral to Palette(0xFFFCE7DD, 0xFFB04A1E, 0xFF5A2A16, 0xFFF5B496),
    ToneKind.Blue to Palette(0xFFE3EDFB, 0xFF1F5FA8, 0xFF1B3556, 0xFFA9C8F5),
    ToneKind.Green to Palette(0xFFE4F2DD, 0xFF3B7A1E, 0xFF23421A, 0xFFB2E09A),
    ToneKind.Rose to Palette(0xFFFBE4EC, 0xFFA8325E, 0xFF5A1F36, 0xFFF4A9C4),
    ToneKind.Slate to Palette(0xFFE8EBF0, 0xFF475569, 0xFF2A323E, 0xFFB7C1D0)
)

/**
 * The colours for one section. With "Different colours for each section" on (Appearance) every section has its own
 * family; otherwise everything uses the colour combination the person chose.
 */
@Composable
fun tone(kind: ToneKind): Tone {
    val scheme = MaterialTheme.colorScheme
    val palette = PALETTES[kind]
    if (palette == null || !ThemeSettings.colorful) return Tone(scheme.primaryContainer, scheme.primary, scheme.onPrimary)
    return if (isAppDark()) {
        Tone(Color(palette.darkContainer), Color(palette.darkAccent), Color(0xFF111418))
    } else {
        Tone(Color(palette.lightContainer), Color(palette.lightAccent), Color.White)
    }
}

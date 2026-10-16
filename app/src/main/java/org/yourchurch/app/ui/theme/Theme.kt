package org.yourchurch.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.yourchurch.app.R
import org.yourchurch.app.data.Category

/*
 * COLORS - change these to restyle the whole app.
 * Light mode: bright blue on a cool white background.
 */
/** The gold of the church colours: use it for small highlights and fills, never for text on white. */
val Gold = Color(0xFFC9A227)

private val LightColors = lightColorScheme(
    primary = Color(0xFF2563EB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBE7FF),
    onPrimaryContainer = Color(0xFF0B2A6B),
    secondary = Color(0xFFB45309),            // warm amber, used for "Pinned"
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDBE7FF),
    onSecondaryContainer = Color(0xFF0B2A6B),
    background = Color(0xFFFAF7F2),
    onBackground = Color(0xFF0F172A),
    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFEFEBE3),
    onSurfaceVariant = Color(0xFF5B6578),
    outline = Color(0xFFDDD8CD),
    outlineVariant = Color(0xFFEFEBE3),
    error = Color(0xFFDC2626),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF6F2EA),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color.White
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8AB4FF),
    onPrimary = Color(0xFF0B1B3F),
    primaryContainer = Color(0xFF1E3566),
    onPrimaryContainer = Color(0xFFDCE7FF),
    secondary = Color(0xFFFBBF24),
    onSecondary = Color(0xFF3A2A00),
    secondaryContainer = Color(0xFF1E3566),
    onSecondaryContainer = Color(0xFFDCE7FF),
    background = Color(0xFF0B1220),
    onBackground = Color(0xFFE8EDF7),
    surface = Color(0xFF121A2B),
    onSurface = Color(0xFFE8EDF7),
    surfaceVariant = Color(0xFF1B2640),
    onSurfaceVariant = Color(0xFF9AA7BF),
    outline = Color(0xFF263250),
    outlineVariant = Color(0xFF1B2640),
    error = Color(0xFFF87171),
    surfaceContainerLowest = Color(0xFF0B1220),
    surfaceContainerLow = Color(0xFF0F1729),
    surfaceContainer = Color(0xFF141D33),
    surfaceContainerHigh = Color(0xFF1A2540),
    surfaceContainerHighest = Color(0xFF202C4A)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/* FONTS - clean modern sans-serif (the phone's default), with bolder headings. */
private val base = Typography()
// One type scale for the whole app: regular and medium weights only, the same sizes everywhere.
// Figtree, the app's font, is friendly and very readable, so body text is 16sp.
private val AppTypography = base.copy(
    headlineMedium = base.headlineMedium.copy(fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    headlineSmall = base.headlineSmall.copy(fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    titleLarge = base.titleLarge.copy(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    titleMedium = base.titleMedium.copy(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    titleSmall = base.titleSmall.copy(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.copy(fontSize = 17.sp, lineHeight = 25.sp, fontWeight = FontWeight.Medium),
    bodyMedium = base.bodyMedium.copy(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
    bodySmall = base.bodySmall.copy(fontSize = 13.sp, lineHeight = 19.sp, fontWeight = FontWeight.Medium),
    labelLarge = base.labelLarge.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium.copy(fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold),
    labelSmall = base.labelSmall.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold)
)

/**
 * THE APP'S FONT: Figtree (free, SIL Open Font License, see legal/Figtree-OFL.txt). To use another font, replace the two
 * files in app/src/main/res/font, app_font_regular.ttf and app_font_medium.ttf, keeping those names.
 */
private val AppFontFamily = FontFamily(
    Font(R.font.app_font_regular, FontWeight.Normal),
    Font(R.font.app_font_medium, FontWeight.Medium),
    Font(R.font.app_font_semibold, FontWeight.SemiBold)
)

private fun Typography.withFont(family: FontFamily) = copy(
    displayLarge = displayLarge.copy(fontFamily = family),
    displayMedium = displayMedium.copy(fontFamily = family),
    displaySmall = displaySmall.copy(fontFamily = family),
    headlineLarge = headlineLarge.copy(fontFamily = family),
    headlineMedium = headlineMedium.copy(fontFamily = family),
    headlineSmall = headlineSmall.copy(fontFamily = family),
    titleLarge = titleLarge.copy(fontFamily = family),
    titleMedium = titleMedium.copy(fontFamily = family),
    titleSmall = titleSmall.copy(fontFamily = family),
    bodyLarge = bodyLarge.copy(fontFamily = family),
    bodyMedium = bodyMedium.copy(fontFamily = family),
    bodySmall = bodySmall.copy(fontFamily = family),
    labelLarge = labelLarge.copy(fontFamily = family),
    labelMedium = labelMedium.copy(fontFamily = family),
    labelSmall = labelSmall.copy(fontFamily = family)
)

private val AppTypographyFont = AppTypography.withFont(AppFontFamily)

/** One colour choice for the whole app: the main colour in light and dark, and the gradient behind headers. */
class Accent(
    val lightPrimary: Color,
    val lightContainer: Color,
    val onLightContainer: Color,
    val darkPrimary: Color,
    val darkContainer: Color,
    val onDarkContainer: Color,
    val heroLight: List<Color>,
    val heroDark: List<Color>,
    /** The second colour of the combination (gold, terracotta, amber): date tiles and small highlights. */
    val highlight: Color,
    /** A darker version of [highlight], for small text on white. */
    val highlightText: Color,
    /** The page colour. */
    val background: Color
)

/** The colours people can pick under Appearance. The first one is the original blue. */
/** Three complete colour combinations. Choosing one changes the whole look, its second colour included. */
val ACCENTS = listOf(
    // A. Fjord and gold
    Accent(
        lightPrimary = Color(0xFF1F3A5F), lightContainer = Color(0xFFE8EEF6), onLightContainer = Color(0xFF0F2238),
        darkPrimary = Color(0xFF9DB9DE), darkContainer = Color(0xFF1D3556), onDarkContainer = Color(0xFFDCE7F7),
        heroLight = listOf(Color(0xFF1F3A5F), Color(0xFF1F3A5F)), heroDark = listOf(Color(0xFF1F3A5F), Color(0xFF1B3252)),
        highlight = Color(0xFFC9A227), highlightText = Color(0xFF8A6D0B), background = Color(0xFFFAF7F2)
    ),
    // B. Highland green
    Accent(
        lightPrimary = Color(0xFF2F6B4F), lightContainer = Color(0xFFE6F1EA), onLightContainer = Color(0xFF0E2E21),
        darkPrimary = Color(0xFF7FD1A5), darkContainer = Color(0xFF1F4A36), onDarkContainer = Color(0xFFD8F2E4),
        heroLight = listOf(Color(0xFF2F6B4F), Color(0xFF2F6B4F)), heroDark = listOf(Color(0xFF2F6B4F), Color(0xFF22503B)),
        highlight = Color(0xFFC8643B), highlightText = Color(0xFF9A4521), background = Color(0xFFF8F6F0)
    ),
    // C. Sunrise
    Accent(
        lightPrimary = Color(0xFF7A2E3A), lightContainer = Color(0xFFF6E9EB), onLightContainer = Color(0xFF3F121A),
        darkPrimary = Color(0xFFEFA2AD), darkContainer = Color(0xFF5A1F2A), onDarkContainer = Color(0xFFFADCE0),
        heroLight = listOf(Color(0xFF7A2E3A), Color(0xFF7A2E3A)), heroDark = listOf(Color(0xFF7A2E3A), Color(0xFF5A1F2A)),
        highlight = Color(0xFFE09F3E), highlightText = Color(0xFF8F5A0A), background = Color(0xFFFBF7F4)
    ),
    // D. Royal purple and gold
    Accent(
        lightPrimary = Color(0xFF4A2C82), lightContainer = Color(0xFFEEE8F7), onLightContainer = Color(0xFF23123F),
        darkPrimary = Color(0xFFC9B6F2), darkContainer = Color(0xFF3A2466), onDarkContainer = Color(0xFFE9DFFB),
        heroLight = listOf(Color(0xFF4A2C82), Color(0xFF4A2C82)), heroDark = listOf(Color(0xFF4A2C82), Color(0xFF37205F)),
        highlight = Color(0xFFD4A62A), highlightText = Color(0xFF8A6A08), background = Color(0xFFFAF8F4)
    ),
    // E. Ocean teal and coral
    Accent(
        lightPrimary = Color(0xFF0E5F6E), lightContainer = Color(0xFFE2F1F3), onLightContainer = Color(0xFF06303A),
        darkPrimary = Color(0xFF7FD3E0), darkContainer = Color(0xFF124A56), onDarkContainer = Color(0xFFD6F1F6),
        heroLight = listOf(Color(0xFF0E5F6E), Color(0xFF0E5F6E)), heroDark = listOf(Color(0xFF0E5F6E), Color(0xFF0B4A56)),
        highlight = Color(0xFFE4725A), highlightText = Color(0xFFA8442F), background = Color(0xFFF6F8F7)
    ),
    // F. Clear sky and orange
    Accent(
        lightPrimary = Color(0xFF1257A8), lightContainer = Color(0xFFE4EDFA), onLightContainer = Color(0xFF0A2A52),
        darkPrimary = Color(0xFFA9C8F5), darkContainer = Color(0xFF17406F), onDarkContainer = Color(0xFFDCE8FA),
        heroLight = listOf(Color(0xFF1257A8), Color(0xFF1257A8)), heroDark = listOf(Color(0xFF1257A8), Color(0xFF0F4688)),
        highlight = Color(0xFFF08A24), highlightText = Color(0xFF9A540A), background = Color(0xFFF7F9FC)
    ),
    // G. Charcoal and amber
    Accent(
        lightPrimary = Color(0xFF2D3340), lightContainer = Color(0xFFE9EBEF), onLightContainer = Color(0xFF14171D),
        darkPrimary = Color(0xFFC3CAD6), darkContainer = Color(0xFF343B49), onDarkContainer = Color(0xFFE4E8EE),
        heroLight = listOf(Color(0xFF2D3340), Color(0xFF2D3340)), heroDark = listOf(Color(0xFF2D3340), Color(0xFF232834)),
        highlight = Color(0xFFF0A500), highlightText = Color(0xFF8A5F00), background = Color(0xFFF8F7F5)
    ),
    // H. Emerald and gold
    Accent(
        lightPrimary = Color(0xFF0F5D45), lightContainer = Color(0xFFE3F2EB), onLightContainer = Color(0xFF06281D),
        darkPrimary = Color(0xFF7ADBB5), darkContainer = Color(0xFF14503C), onDarkContainer = Color(0xFFD5F3E5),
        heroLight = listOf(Color(0xFF0F5D45), Color(0xFF0F5D45)), heroDark = listOf(Color(0xFF0F5D45), Color(0xFF0B4634)),
        highlight = Color(0xFFD4A62A), highlightText = Color(0xFF8A6A08), background = Color(0xFFF8F7F1)
    ),
    // I. Our church logo: the deep blue of its lettering and the fresh green of its ring
    Accent(
        lightPrimary = Color(0xFF0B1A7A), lightContainer = Color(0xFFE6E8F7), onLightContainer = Color(0xFF05094A),
        darkPrimary = Color(0xFFAEB8FF), darkContainer = Color(0xFF1A2370), onDarkContainer = Color(0xFFDDE1FF),
        heroLight = listOf(Color(0xFF0B1A7A), Color(0xFF0B1A7A)), heroDark = listOf(Color(0xFF0B1A7A), Color(0xFF060E55)),
        highlight = Color(0xFFC6D810), highlightText = Color(0xFF6B7500), background = Color(0xFFFBFBF5)
    ),
    // J. Oromo heritage: red, black and white (the colours of traditional Oromo dress), with green for the land
    Accent(
        lightPrimary = Color(0xFFA4161A), lightContainer = Color(0xFFFBE9E7), onLightContainer = Color(0xFF410002),
        darkPrimary = Color(0xFFFFB4AB), darkContainer = Color(0xFF5A1214), onDarkContainer = Color(0xFFFFDAD6),
        heroLight = listOf(Color(0xFF1B1B1B), Color(0xFF1B1B1B)), heroDark = listOf(Color(0xFF1B1B1B), Color(0xFF000000)),
        highlight = Color(0xFF7CB342), highlightText = Color(0xFF33691E), background = Color(0xFFFAF8F6)
    ),
    // K. Highland green and gold: deep forest green with warm gold on ivory
    Accent(
        lightPrimary = Color(0xFF1F5B3A), lightContainer = Color(0xFFE1EADB), onLightContainer = Color(0xFF0F2A1B),
        darkPrimary = Color(0xFF9ED0AE), darkContainer = Color(0xFF1C3326), onDarkContainer = Color(0xFFD3EBDA),
        heroLight = listOf(Color(0xFF1F5B3A), Color(0xFF1F5B3A)), heroDark = listOf(Color(0xFF1F5B3A), Color(0xFF173F29)),
        highlight = Color(0xFFF4D37A), highlightText = Color(0xFF7A5C00), background = Color(0xFFF6F4EC)
    ),
    // L. Fjord dawn: slate blue with a soft coral on cool white
    Accent(
        lightPrimary = Color(0xFF2A4A7C), lightContainer = Color(0xFFDFE8F3), onLightContainer = Color(0xFF0E2140),
        darkPrimary = Color(0xFFA9C4EE), darkContainer = Color(0xFF1B2F4F), onDarkContainer = Color(0xFFD8E5F8),
        heroLight = listOf(Color(0xFF2A4A7C), Color(0xFF2A4A7C)), heroDark = listOf(Color(0xFF2A4A7C), Color(0xFF1F3860)),
        highlight = Color(0xFFFFB59B), highlightText = Color(0xFF9A3F20), background = Color(0xFFF3F6FA)
    ),
    // M. Midnight and gold: deep navy with gold (at its best in the dark look)
    Accent(
        lightPrimary = Color(0xFF1D2A40), lightContainer = Color(0xFFE4E9F1), onLightContainer = Color(0xFF0E1726),
        darkPrimary = Color(0xFFE7C766), darkContainer = Color(0xFF162030), onDarkContainer = Color(0xFFEBD38A),
        heroLight = listOf(Color(0xFF1D2A40), Color(0xFF1D2A40)), heroDark = listOf(Color(0xFF1D2A40), Color(0xFF1D2A40)),
        highlight = Color(0xFFE7C766), highlightText = Color(0xFF7A5C00), background = Color(0xFFF5F6F9)
    )
)

val ACCENT_NAMES = listOf(
    R.string.accent_fjord, R.string.accent_highland, R.string.accent_sunrise,
    R.string.accent_royal, R.string.accent_ocean, R.string.accent_sky, R.string.accent_charcoal, R.string.accent_emerald, R.string.accent_logo, R.string.accent_oromo,
    R.string.accent_highland_gold, R.string.accent_fjord_dawn, R.string.accent_midnight
)

/** True when the app is showing its dark look: the person's choice, or the phone's setting if they chose "follow phone". */
@Composable
fun isAppDark(): Boolean = when (ThemeSettings.mode) {
    ThemeMode.System -> isSystemInDarkTheme()
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
}

@Composable
fun ChurchTheme(content: @Composable () -> Unit) {
    val dark = isAppDark()
    val accent = ACCENTS[ThemeSettings.effectiveAccent]
    val scheme = if (dark) {
        DarkColors.copy(
            primary = accent.darkPrimary,
            onPrimary = Color(0xFF0B1220),
            primaryContainer = accent.darkContainer,
            onPrimaryContainer = accent.onDarkContainer,
            secondary = lerp(accent.highlight, Color.White, 0.35f),
            secondaryContainer = accent.darkContainer,
            onSecondaryContainer = accent.onDarkContainer,
            surface = lerp(DarkColors.surface, accent.darkContainer, 0.25f),
            surfaceContainer = lerp(DarkColors.surfaceContainer, accent.darkContainer, 0.25f),
            surfaceContainerHigh = lerp(DarkColors.surfaceContainerHigh, accent.darkContainer, 0.25f),
            surfaceContainerHighest = lerp(DarkColors.surfaceContainerHighest, accent.darkContainer, 0.25f),
            surfaceVariant = lerp(DarkColors.surfaceVariant, accent.darkContainer, 0.50f)
        )
    } else {
        // As in the picture: a warm page, white cards with a thin outline, the palette's light colour for tiles.
        LightColors.copy(
            primary = accent.lightPrimary,
            onPrimary = Color.White,
            primaryContainer = accent.lightContainer,
            onPrimaryContainer = accent.lightPrimary,
            secondary = accent.highlightText,
            secondaryContainer = accent.lightContainer,
            onSecondaryContainer = accent.onLightContainer,
            background = accent.background,
            surface = Color.White,
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = Color.White,
            surfaceContainer = Color.White,
            surfaceContainerHigh = Color.White,
            surfaceContainerHighest = Color.White,
            surfaceVariant = lerp(accent.background, accent.lightContainer, 0.6f),
            outline = Color(0xFFD9DDE5),
            outlineVariant = Color(0xFFE6E8ED)
        )
    }
    // The "Text size" choice (Appearance) scales every text in the app.
    val context = LocalContext.current
    remember { TextSize.load(context) }
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, density.fontScale * TextSize.factor)
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = AppTypographyFont,
            shapes = AppShapes,
            content = content
        )
    }
}

/** The palette's second colour (gold in Fjord and gold), for date tiles and small highlights. */
@Composable
fun highlightColor(): Color = ACCENTS[ThemeSettings.effectiveAccent].highlight

/** The same colour, readable as small text. */
@Composable
fun highlightTextColor(): Color {
    val accent = ACCENTS[ThemeSettings.effectiveAccent]
    return if (isAppDark()) lerp(accent.highlight, Color.White, 0.35f) else accent.highlightText
}

/** The gradient used behind the church name and the sign-in header. It follows the accent colour. */
@Composable
fun heroBrush(): Brush {
    val accent = ACCENTS[ThemeSettings.effectiveAccent]
    return Brush.linearGradient(if (isAppDark()) accent.heroDark else accent.heroLight)
}

@Composable
fun Category.color(): Color {
    val dark = isAppDark()
    return when (this) {
        Category.Service -> if (dark) Color(0xFF8AB4FF) else Color(0xFF2563EB)
        Category.Event -> if (dark) Color(0xFF34D399) else Color(0xFF059669)
        Category.Prayer -> if (dark) Color(0xFFA78BFA) else Color(0xFF7C3AED)
        Category.Community -> if (dark) Color(0xFFFBBF24) else Color(0xFFD97706)
        Category.Urgent -> if (dark) Color(0xFFF87171) else Color(0xFFDC2626)
    }
}

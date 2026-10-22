package org.yourchurch.app.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.yourchurch.app.R
import org.yourchurch.app.data.BrandingStore
import org.yourchurch.app.ui.theme.ACCENTS
import org.yourchurch.app.ui.theme.ACCENT_NAMES
import org.yourchurch.app.ui.theme.Accent
import org.yourchurch.app.ui.theme.TextSize
import org.yourchurch.app.ui.theme.ThemeMode
import org.yourchurch.app.ui.theme.ThemeSettings
import org.yourchurch.app.ui.theme.currentAccent
import org.yourchurch.app.ui.theme.hslColor
import org.yourchurch.app.ui.theme.isAppDark
import org.yourchurch.app.ui.theme.toHsl

/**
 * Appearance, on one calm sheet: a small picture of the app that changes as you choose, then (1) light or dark, (2) text size,
 * (3) colours (ready-made combinations, or slide to any colour of your own), (4) the page background (slide to any colour). The
 * rarely used things (a different colour for each section, and "back to the standard look") are under "More options".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceDialog(onDismiss: () -> Unit, isAdmin: Boolean = false) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var forChurch by remember { mutableStateOf(false) }      // leaders: choosing the colours for the whole church
    var showMore by remember { mutableStateOf(false) }
    var ownOpen by remember { mutableStateOf(ThemeSettings.usesCustom) }
    val darkLook = isAppDark()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.appearance_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
            }

            // A small picture of the app: the top card, a page with a card, the bottom bar. It follows every choice below.
            AppearancePreview()

            // 1. Light or dark
            Section(stringResource(R.string.appearance_look)) {
                val labels = listOf(R.string.theme_system, R.string.theme_light, R.string.theme_dark)
                Segments(
                    count = 3,
                    selected = ThemeMode.entries.indexOf(ThemeSettings.mode),
                    onSelect = { ThemeSettings.setMode(context, ThemeMode.entries[it]) }
                ) { i, chosen ->
                    Text(
                        stringResource(labels[i]),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal,
                        color = if (chosen) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                }
            }

            // 2. Text size
            Section(stringResource(R.string.text_size_title)) {
                val labels = listOf(R.string.text_size_normal, R.string.text_size_large, R.string.text_size_xlarge)
                val sizes = listOf(14.sp, 18.sp, 22.sp)
                Segments(count = 3, selected = TextSize.level, onSelect = { TextSize.set(context, it) }) { i, chosen ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Aa",
                            fontSize = sizes[i],
                            fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal,
                            color = if (chosen) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            stringResource(labels[i]),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (chosen) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }

            // 3. Colours
            Section(stringResource(R.string.accent_title)) {
                if (isAdmin) {
                    // Leaders choose the colours for the whole church, or just for themselves.
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !forChurch, onClick = { forChurch = false }, label = { Text(stringResource(R.string.appearance_for_me)) })
                        FilterChip(selected = forChurch, onClick = { forChurch = true }, label = { Text(stringResource(R.string.appearance_for_church)) })
                    }
                    if (forChurch) {
                        Text(
                            stringResource(R.string.appearance_church_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (!forChurch) {
                    // The church's own colours: the standard choice.
                    val church = ACCENTS[(BrandingStore.churchPalette ?: 0).coerceIn(0, ACCENTS.lastIndex)]
                    val shape = RoundedCornerShape(14.dp)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .border(
                                if (ThemeSettings.followChurch) 2.dp else 0.5.dp,
                                if (ThemeSettings.followChurch) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                shape
                            )
                            .clickable(role = Role.RadioButton) {
                                ThemeSettings.useChurchColours(context)
                                ownOpen = false
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PaletteDot(church, selected = false, onClick = null, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.appearance_church_colours), style = MaterialTheme.typography.titleSmall)
                            Text(
                                stringResource(R.string.appearance_church_colours_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        stringResource(R.string.appearance_own_colours),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // The ready-made combinations as small round dots, six in a row; each shows its main and its second colour.
                val selectedIndex =
                    if (forChurch) (BrandingStore.churchPalette ?: 0)
                    else if (ThemeSettings.followChurch || ThemeSettings.usesCustom) -1
                    else ThemeSettings.accent
                ACCENTS.withIndex().chunked(6).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { (i, palette) ->
                            PaletteDot(
                                palette = palette,
                                selected = selectedIndex == i,
                                onClick = {
                                    if (forChurch) scope.launch { BrandingStore.setPalette(i) } else ThemeSettings.setAccent(context, i)
                                    ownOpen = false
                                },
                                modifier = Modifier.weight(1f).aspectRatio(1f),
                                description = stringResource(ACCENT_NAMES[i])
                            )
                        }
                        repeat(6 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                val shownIndex = (if (forChurch || ThemeSettings.followChurch) BrandingStore.churchPalette ?: 0 else ThemeSettings.accent).coerceIn(0, ACCENTS.lastIndex)
                Text(
                    if (!forChurch && ThemeSettings.usesCustom) stringResource(R.string.appearance_custom_title) else stringResource(ACCENT_NAMES[shownIndex]),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!forChurch) {
                    // Your own colour: slide to any colour (it is only for you; the church colours are chosen from the dots).
                    val shape = RoundedCornerShape(14.dp)
                    val own = ThemeSettings.usesCustom
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .border(
                                if (own) 2.dp else 0.5.dp,
                                if (own) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                shape
                            )
                            .clickable(role = Role.RadioButton) {
                                if (!ThemeSettings.usesCustom) ThemeSettings.setCustomMain(context, currentAccent().lightPrimary.toArgb())
                                ownOpen = true
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(36.dp).clip(CircleShape).background(Brush.sweepGradient(rainbowColors())))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.appearance_custom_title), style = MaterialTheme.typography.titleSmall)
                            Text(
                                stringResource(R.string.appearance_custom_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (ownOpen && ThemeSettings.usesCustom) {
                        ColorSpectrum(
                            start = Color(ThemeSettings.customMain),
                            minLightness = 0.18f, maxLightness = 0.45f, minSaturation = 0.25f, maxSaturation = 0.95f,
                            onColor = { ThemeSettings.setCustomMain(context, it.toArgb()) }
                        )
                    }
                }
            }

            // 4. The page background (not the top card, not the bottom bar): slide to any colour, or automatic
            Section(stringResource(R.string.pagebg_title), hint = stringResource(R.string.pagebg_desc)) {
                val pageNow = if (darkLook) ThemeSettings.pageDarkColor else ThemeSettings.pageLightColor
                FilterChip(
                    selected = pageNow == 0,
                    onClick = { ThemeSettings.setPageColor(context, darkLook, 0) },
                    label = { Text(stringResource(R.string.appearance_automatic)) }
                )
                // The light look and the dark look each have their own colour, and their own limits: a light page stays light and a
                // dark page stays dark, so the text can always be read.
                key(darkLook) {
                    ColorSpectrum(
                        start = if (pageNow != 0) Color(pageNow) else MaterialTheme.colorScheme.background,
                        minLightness = if (darkLook) 0f else 0.72f,
                        maxLightness = if (darkLook) 0.26f else 1f,
                        minSaturation = 0f,
                        maxSaturation = if (darkLook) 0.7f else 0.8f,
                        onColor = { ThemeSettings.setPageColor(context, darkLook, it.toArgb()) }
                    )
                }
            }

            // More options: the things most people never touch.
            Column {
                TextButton(onClick = { showMore = !showMore }) {
                    Text(stringResource(R.string.appearance_more) + if (showMore) "  \u25B4" else "  \u25BE")
                }
                if (showMore) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(stringResource(R.string.appearance_sections), style = MaterialTheme.typography.titleSmall)
                            Text(
                                stringResource(R.string.appearance_sections_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = ThemeSettings.colorful, onCheckedChange = { ThemeSettings.setColorful(context, it) })
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            ThemeSettings.setMode(context, ThemeMode.System)
                            ThemeSettings.useChurchColours(context)
                            ThemeSettings.setColorful(context, false)
                            ThemeSettings.setPageColor(context, false, 0)
                            ThemeSettings.setPageColor(context, true, 0)
                            TextSize.set(context, 0)
                            ownOpen = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.appearance_reset)) }
                }
            }
        }
    }
}

/** The colours of the rainbow, for the colour strip and the round "your own colour" button. */
private fun rainbowColors(): List<Color> = (0..6).map { hslColor(it * 60f, 1f, 0.5f) }

/**
 * Three strips to slide on: the colour (the whole rainbow), its strength (grey to full colour) and light to dark. Together they can
 * make any colour. The round handle shows the colour as it is now. [onColor] is called each time a strip is touched. The limits keep
 * the colour in a range where the text on it can still be read.
 */
@Composable
private fun ColorSpectrum(
    start: Color,
    minLightness: Float,
    maxLightness: Float,
    minSaturation: Float,
    maxSaturation: Float,
    onColor: (Color) -> Unit
) {
    val first = remember { start.toHsl() }
    var hue by remember { mutableFloatStateOf(first[0]) }
    var saturation by remember { mutableFloatStateOf(first[1].coerceIn(minSaturation, maxSaturation)) }
    var lightness by remember { mutableFloatStateOf(first[2].coerceIn(minLightness, maxLightness)) }
    val now = hslColor(hue, saturation, lightness)
    val satRange = (maxSaturation - minSaturation).coerceAtLeast(0.001f)
    val lightRange = (maxLightness - minLightness).coerceAtLeast(0.001f)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column {
            Text(stringResource(R.string.appearance_slider_colour), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            GradientSlider(
                fraction = hue / 360f,
                thumbColor = now,
                brush = Brush.horizontalGradient(rainbowColors()),
                onFraction = {
                    hue = it * 360f
                    onColor(hslColor(hue, saturation, lightness))
                }
            )
        }
        Column {
            Text(stringResource(R.string.appearance_slider_strength), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            GradientSlider(
                fraction = (saturation - minSaturation) / satRange,
                thumbColor = now,
                brush = Brush.horizontalGradient(listOf(hslColor(hue, minSaturation, lightness), hslColor(hue, maxSaturation, lightness))),
                onFraction = {
                    saturation = minSaturation + it * satRange
                    onColor(hslColor(hue, saturation, lightness))
                }
            )
        }
        Column {
            Text(stringResource(R.string.appearance_slider_shade), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            GradientSlider(
                fraction = (lightness - minLightness) / lightRange,
                thumbColor = now,
                brush = Brush.horizontalGradient(
                    listOf(
                        hslColor(hue, saturation, minLightness),
                        hslColor(hue, saturation, (minLightness + maxLightness) / 2f),
                        hslColor(hue, saturation, maxLightness)
                    )
                ),
                onFraction = {
                    lightness = minLightness + it * lightRange
                    onColor(hslColor(hue, saturation, lightness))
                }
            )
        }
    }
}

private fun fractionAt(x: Float, width: Float, pad: Float): Float = ((x - pad) / (width - 2f * pad)).coerceIn(0f, 1f)

/** A strip painted with [brush]; tap it or drag the round handle (which shows [thumbColor]) to choose a place on it. */
@Composable
private fun GradientSlider(fraction: Float, thumbColor: Color, brush: Brush, onFraction: (Float) -> Unit) {
    val current by rememberUpdatedState(onFraction)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .pointerInput(Unit) {
                detectTapGestures { offset -> current(fractionAt(offset.x, size.width.toFloat(), size.height / 2f)) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    current(fractionAt(change.position.x, size.width.toFloat(), size.height / 2f))
                }
            }
    ) {
        drawRect(brush = brush)
        val pad = size.height / 2f
        val center = Offset(pad + fraction.coerceIn(0f, 1f) * (size.width - 2f * pad), size.height / 2f)
        drawCircle(Color.White, radius = size.height / 2f - 2.dp.toPx(), center = center)
        drawCircle(thumbColor, radius = size.height / 2f - 6.dp.toPx(), center = center)
        drawCircle(Color.Black.copy(alpha = 0.25f), radius = size.height / 2f - 2.dp.toPx(), center = center, style = Stroke(width = 1.5.dp.toPx()))
    }
}

/** A title (and a short hint) above a group of choices. */
@Composable
private fun Section(title: String, hint: String? = null, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (hint != null) {
                Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        content()
    }
}

/** Two or three buttons side by side with one chosen. [content] draws what is inside each. */
@Composable
private fun Segments(count: Int, selected: Int, onSelect: (Int) -> Unit, content: @Composable (index: Int, chosen: Boolean) -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(Modifier.fillMaxWidth().clip(shape).border(1.dp, MaterialTheme.colorScheme.outline, shape)) {
        for (i in 0 until count) {
            val chosen = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .background(if (chosen) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                    .clickable(role = Role.RadioButton) { onSelect(i) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) { content(i, chosen) }
        }
    }
}

/** A round colour combination: the main colour and the second colour side by side. The chosen one has a ring. */
@Composable
private fun PaletteDot(palette: Accent, selected: Boolean, onClick: (() -> Unit)?, modifier: Modifier, description: String? = null) {
    Box(
        modifier
            .clip(CircleShape)
            .border(
                if (selected) 3.dp else 1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                CircleShape
            )
            .then(if (onClick != null) Modifier.clickable(role = Role.RadioButton, onClick = onClick) else Modifier)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
    ) {
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxHeight().background(palette.lightPrimary))
            Box(Modifier.weight(1f).fillMaxHeight().background(palette.highlight))
        }
    }
}

/** A tiny copy of the app made with the colours in use now: the top card, a page with a card, and the bottom bar. */
@Composable
private fun AppearancePreview() {
    val scheme = MaterialTheme.colorScheme
    val navBar = if (isAppDark()) scheme.surfaceVariant else lerp(scheme.surface, scheme.primary, 0.10f)
    val shape = RoundedCornerShape(18.dp)
    Column(Modifier.fillMaxWidth().clip(shape).border(1.dp, scheme.outline, shape).background(scheme.background)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(10.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(scheme.primaryContainer)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(22.dp).clip(CircleShape).background(scheme.primary))
            Spacer(Modifier.width(8.dp))
            Box(Modifier.height(8.dp).weight(1f).clip(RoundedCornerShape(4.dp)).background(scheme.onPrimaryContainer.copy(alpha = 0.55f)))
        }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(scheme.surface)
                .border(0.5.dp, scheme.outline, RoundedCornerShape(12.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(stringResource(R.string.appearance_preview_title), style = MaterialTheme.typography.titleSmall, color = scheme.onSurface)
            Text(stringResource(R.string.appearance_preview_text), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().background(navBar).padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            repeat(4) { i ->
                Box(
                    Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(if (i == 0) scheme.primary else scheme.onSurfaceVariant.copy(alpha = 0.4f))
                )
            }
        }
    }
}

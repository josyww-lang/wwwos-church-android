package org.yourchurch.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.yourchurch.app.R
import org.yourchurch.app.data.BrandingStore
import org.yourchurch.app.ui.theme.ACCENTS
import org.yourchurch.app.ui.theme.ACCENT_NAMES
import org.yourchurch.app.ui.theme.Accent
import org.yourchurch.app.ui.theme.TextSize
import org.yourchurch.app.ui.theme.ThemeMode
import org.yourchurch.app.ui.theme.ThemeSettings

/** Light or dark, one of the colour combinations, and the text size. */
@Composable
fun AppearanceDialog(onDismiss: () -> Unit, isAdmin: Boolean = false) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var forChurch by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.appearance_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                // A different colour family for each section (Home shortcuts, Church sections, More).
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(stringResource(R.string.appearance_sections), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.appearance_sections_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = ThemeSettings.colorful, onCheckedChange = { ThemeSettings.setColorful(context, it) })
                }
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.accent_title), style = MaterialTheme.typography.titleMedium)
                if (isAdmin) {
                    // Leaders choose the colours for the whole church, or just for themselves.
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !forChurch, onClick = { forChurch = false }, label = { Text(stringResource(R.string.appearance_for_me)) })
                        FilterChip(selected = forChurch, onClick = { forChurch = true }, label = { Text(stringResource(R.string.appearance_for_church)) })
                    }
                    if (forChurch) {
                        Text(
                            stringResource(R.string.appearance_church_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                if (!forChurch) {
                    // "Church colours": follow whatever the leaders chose.
                    val church = ACCENTS[(BrandingStore.churchPalette ?: 0).coerceIn(0, ACCENTS.lastIndex)]
                    PaletteCard(
                        title = stringResource(R.string.appearance_church_colours),
                        subtitle = stringResource(R.string.appearance_church_colours_desc),
                        palette = church,
                        selected = ThemeSettings.followChurch,
                        onClick = { ThemeSettings.useChurchColours(context) }
                    )
                }
                // Each choice shows its four colours together: main, second, light and page.
                ACCENTS.forEachIndexed { i, palette ->
                    val selected = if (forChurch) (BrandingStore.churchPalette ?: 0) == i
                    else !ThemeSettings.followChurch && ThemeSettings.accent == i
                    PaletteCard(
                        title = stringResource(ACCENT_NAMES[i]),
                        subtitle = null,
                        palette = palette,
                        selected = selected,
                        onClick = {
                            if (forChurch) scope.launch { BrandingStore.setPalette(i) } else ThemeSettings.setAccent(context, i)
                        }
                    )
                }

                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.theme_title), style = MaterialTheme.typography.titleMedium)
                listOf(
                    ThemeMode.System to R.string.theme_system,
                    ThemeMode.Light to R.string.theme_light,
                    ThemeMode.Dark to R.string.theme_dark
                ).forEach { (mode, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = ThemeSettings.mode == mode,
                                role = Role.RadioButton,
                                onClick = { ThemeSettings.setMode(context, mode) }
                            )
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = ThemeSettings.mode == mode, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(label))
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.text_size_title), style = MaterialTheme.typography.titleMedium)
                listOf(
                    0 to R.string.text_size_normal,
                    1 to R.string.text_size_large,
                    2 to R.string.text_size_xlarge
                ).forEach { (level, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = TextSize.level == level,
                                role = Role.RadioButton,
                                onClick = { TextSize.set(context, level) }
                            )
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = TextSize.level == level, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(label))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } }
    )
}


@Composable
private fun PaletteCard(title: String, subtitle: String?, palette: Accent, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(shape)
            .border(
                if (selected) 2.dp else 0.5.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape
            )
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(12.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().height(32.dp).clip(RoundedCornerShape(8.dp))) {
            Box(Modifier.weight(2f).fillMaxHeight().background(palette.lightPrimary))
            Box(Modifier.weight(1f).fillMaxHeight().background(palette.highlight))
            Box(Modifier.weight(1f).fillMaxHeight().background(palette.lightContainer))
            Box(Modifier.weight(1f).fillMaxHeight().background(palette.background))
        }
    }
}

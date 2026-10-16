package org.yourchurch.app.ui.community

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.yourchurch.app.R
import org.yourchurch.app.ui.common.CrossIcon
import org.yourchurch.app.ui.theme.ToneKind
import org.yourchurch.app.ui.theme.tone

internal class SectionButtonDef(val icon: ImageVector, @StringRes val label: Int, val kind: ToneKind)

/** The five sections of the Church tab, in order. Home shows buttons for these with the same icons and names. */
internal val CHURCH_BUTTONS: List<SectionButtonDef> by lazy {
    listOf(
        SectionButtonDef(Icons.Outlined.Campaign, R.string.title_announcements, ToneKind.Blue),
        SectionButtonDef(Icons.Outlined.Event, R.string.community_events, ToneKind.Green),
        SectionButtonDef(Icons.Outlined.PlayCircle, R.string.sermons_title, ToneKind.Coral),
        SectionButtonDef(Icons.Outlined.Favorite, R.string.section_serve, ToneKind.Teal),
        SectionButtonDef(CrossIcon, R.string.community_prayer, ToneKind.Purple)
    )
}

/** One big, lifted button. The open section is filled with its colour. */
@Composable
internal fun SectionButton(def: SectionButtonDef, selected: Boolean, modifier: Modifier = Modifier, badge: Int = 0, onClick: () -> Unit) {
    val colors = tone(def.kind)
    val shape = RoundedCornerShape(18.dp)
    val background = if (selected) colors.accent else lerp(colors.container, colors.accent, 0.14f)
    val foreground = if (selected) colors.onAccent else colors.accent
    Box(modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(88.dp)
                .shadow(if (selected) 2.dp else 5.dp, shape)
                .clip(shape)
                .background(background)
                .clickable(role = Role.Tab, onClick = onClick)
                .padding(horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(if (selected) Color.White.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surface, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(def.icon, contentDescription = null, tint = foreground, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(def.label),
                style = MaterialTheme.typography.labelLarge,
                color = foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
        if (badge > 0) CountBadge(badge, Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 8.dp))
    }
}

/** A small red number: how many new things are waiting in a section. */
@Composable
internal fun CountBadge(count: Int, modifier: Modifier = Modifier) {
    Badge(modifier = modifier, containerColor = Color(0xFFC62828), contentColor = Color.White) {
        Text(if (count > 9) "9+" else count.toString())
    }
}

/** The Church buttons in two rows: three, then two. */
internal val CHURCH_KEYS = listOf("news", "events", "sermons", "serve", "pray")

@Composable
internal fun ChurchButtonGrid(selected: Int, badges: Map<String, Int>, onSelect: (Int) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (i in 0..2) SectionButton(CHURCH_BUTTONS[i], selected == i, Modifier.weight(1f), badges[CHURCH_KEYS[i]] ?: 0) { onSelect(i) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (i in 3..4) SectionButton(CHURCH_BUTTONS[i], selected == i, Modifier.weight(1f), badges[CHURCH_KEYS[i]] ?: 0) { onSelect(i) }
        }
    }
}

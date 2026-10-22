package org.yourchurch.app.ui.community

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.yourchurch.app.data.SectionSeen
import org.yourchurch.app.ui.announcements.AnnouncementsScreen
import org.yourchurch.app.ui.common.AppHeader
import org.yourchurch.app.ui.sermons.SermonsScreen
import org.yourchurch.app.ui.theme.ToneKind
import org.yourchurch.app.ui.theme.tone

/**
 * The Church tab: announcements, events, serving and prayer, switched with the buttons under the title.
 * [section] says which one is open (Home's shortcuts can jump straight to one).
 */
@Composable
fun ChurchHub(
    uid: String,
    myName: String,
    isAdmin: Boolean,
    accountMenu: @Composable () -> Unit,
    section: Int,
    onSectionChange: (Int) -> Unit,
    /** How many new things are waiting in each section (key: news, events, sermons, serve, pray). */
    badges: Map<String, Int> = emptyMap(),
    /** Hides the bottom menu while a full-screen page (an event, or a form) is open. */
    onFullScreen: (Boolean) -> Unit
) {
    var inner by remember { mutableStateOf(false) }

    // Opening a section clears its number; so does leaving it (so what you posted yourself never counts).
    val context = LocalContext.current
    LaunchedEffect(section) { SectionSeen.markSeen(context, CHURCH_KEYS[section]) }
    DisposableEffect(section) { onDispose { SectionSeen.markSeen(context, CHURCH_KEYS[section]) } }

    Column(Modifier.fillMaxSize()) {
        if (!inner) {
            HubHeader(accountMenu)
            ChurchButtonGrid(selected = section, badges = badges, onSelect = onSectionChange)
        }

        when (section) {
            0 -> AnnouncementsScreen(
                uid = uid,
                isAdmin = isAdmin,
                accountMenu = accountMenu,
                showHeader = false,
                onEditing = { inner = it; onFullScreen(it) }
            )
            1 -> EventsPane(
                uid = uid,
                myName = myName,
                isAdmin = isAdmin,
                onFullScreen = { inner = it; onFullScreen(it) }
            )
            // Sermon audio: members listen here, and leaders upload with the New sermon button.
            2 -> SermonsScreen(uid = uid, myName = myName, isAdmin = isAdmin, accountMenu = accountMenu, showHeader = false)
            3 -> ServingPane(
                uid = uid,
                myName = myName,
                isAdmin = isAdmin,
                onFullScreen = { inner = it; onFullScreen(it) }
            )
            else -> PrayerPane(uid = uid, myName = myName, isAdmin = isAdmin)
        }
    }
}

/** The header shared by the Church, Word and More tabs (the same one Home uses). */
@Composable
internal fun HubHeader(accountMenu: @Composable () -> Unit) {
    AppHeader(accountMenu = accountMenu)
}

/** The section buttons under the title. They scroll sideways if they don't all fit. */
@Composable
internal fun SectionTabs(
    labels: List<String>,
    selected: Int,
    kinds: List<ToneKind> = emptyList(),
    /** Bigger text and buttons (used in Chat). */
    large: Boolean = false,
    onSelect: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 17.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        labels.forEachIndexed { i, label ->
            val isSelected = i == selected
            val colors = tone(kinds.getOrElse(i) { ToneKind.Primary })
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (isSelected) colors.accent else colors.container)
                    .clickable(role = Role.Tab, onClick = { onSelect(i) })
                    .padding(horizontal = if (large) 20.dp else 16.dp, vertical = if (large) 11.dp else 9.dp)
            ) {
                Text(
                    label,
                    style = if (large) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    color = if (isSelected) colors.onAccent else colors.accent
                )
            }
        }
    }
}

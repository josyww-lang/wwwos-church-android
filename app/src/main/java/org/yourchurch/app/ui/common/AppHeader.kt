package org.yourchurch.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.yourchurch.app.data.AuthViewModel

/** Where the logo sits in the top card. The start screen's logo shrinks and flies to exactly this place when the app has loaded. */
val HEADER_LOGO_SIZE = 60.dp
val HEADER_LOGO_START = 6.dp
/** The gap between the bottom of the status bar (where the icons of other apps' notifications are) and the logo row. */
private val HEADER_TOP_PAD = 16.dp

/** The middle of the logo, measured down from the bottom of the status bar: the top gap + 35 (half of the 70 inside the card). */
val HEADER_LOGO_CENTER_BELOW_STATUS_BAR = HEADER_TOP_PAD + 35.dp

/**
 * The header card is exactly this tall on every page, so nothing moves when you change tabs: the top gap, the 70 for the
 * logo row, 8, and (if wanted) some extra room under it.
 */
private val HEADER_EXTRA = 0.dp
val HEADER_HEIGHT = HEADER_TOP_PAD + 70.dp + 8.dp + HEADER_EXTRA

/** True inside the main screen, which draws the header card once, on top of the pages. */
val LocalHeaderHosted = compositionLocalOf { false }

private val EmptyTrailing: @Composable RowScope.() -> Unit = {}

/**
 * What the pages tell the main screen's header: that a header is wanted, and which extra buttons go in it. The buttons
 * are kept behind a stable holder, so a page redrawing never makes the header (and so the whole screen) redraw in a loop.
 */
object HeaderHost {
    var count by mutableIntStateOf(0)
    var trailingSource by mutableStateOf<State<@Composable RowScope.() -> Unit>?>(null)
}

/**
 * The top of a page. Inside the main screen it only keeps the space free (and says which extra buttons it wants);
 * the real header card is drawn once, on top, and never moves or fades when the page changes.
 */
@Composable
fun AppHeader(
    accountMenu: @Composable () -> Unit,
    @Suppress("UNUSED_PARAMETER") trailingCount: Int = 0,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    if (LocalHeaderHosted.current) {
        val latest = rememberUpdatedState(trailing)
        DisposableEffect(Unit) {
            HeaderHost.count++
            HeaderHost.trailingSource = latest
            onDispose {
                HeaderHost.count--
                if (HeaderHost.trailingSource === latest) HeaderHost.trailingSource = null
            }
        }
        Spacer(Modifier.fillMaxWidth().statusBarsPadding().height(HEADER_HEIGHT))
    } else {
        AppHeaderBar(accountMenu = accountMenu, trailing = trailing)
    }
}

/** A light card across the top, with its lower corners rounded: logo, church name and profile. */
@Composable
fun AppHeaderBar(
    accountMenu: @Composable () -> Unit,
    trailing: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier
) {
    // Leaders can tap the logo to change it.
    val authVm: AuthViewModel = viewModel()
    val isAdmin = authVm.state.collectAsStateWithLifecycle().value.isAdmin
    var showLogo by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(4.dp, shape)
            .background(MaterialTheme.colorScheme.primaryContainer, shape)
            .statusBarsPadding()
            .height(HEADER_HEIGHT)
            .padding(start = HEADER_LOGO_START, end = 2.dp, top = HEADER_TOP_PAD, bottom = 8.dp + HEADER_EXTRA),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.clip(CircleShape).clickable(enabled = isAdmin) { showLogo = true }) {
            ChurchLogo(size = HEADER_LOGO_SIZE)
        }
        // The name gets all the room that is left. The logo and the profile picture were made bigger by taking the extra
        // room from the gaps, so the name has exactly the same width as before: 6 + 60 + 4 + 4 + 52 + 2 = 128 (was 12 + 52 + 6 + 6 + 48 + 4).
        ChurchName(Modifier.weight(1f).padding(horizontal = 4.dp), startSize = 26f)
        trailing()
        accountMenu()
    }
    if (showLogo) LogoDialog(onDismiss = { showLogo = false })
}

/** The header card for the main screen: drawn once, on top of the pages, only while a page wants one. */
@Composable
fun HostedHeader(accountMenu: @Composable () -> Unit, modifier: Modifier = Modifier) {
    if (HeaderHost.count > 0) {
        val trailing = HeaderHost.trailingSource?.value ?: EmptyTrailing
        AppHeaderBar(accountMenu = accountMenu, trailing = trailing, modifier = modifier)
    }
}

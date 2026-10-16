package org.yourchurch.app.ui.community

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.yourchurch.app.ui.common.AppHeader

/** Where a Home shortcut leads. */
enum class HomeTarget { Announcements, Events, Serve, Prayer, Give, Sermons, Chat, Contact, Reading, Study }

/** The Home tab: the header, the LIVE NOW banner when we are streaming, and today's picture of church life. */
@Composable
fun HomeScreen(
    uid: String,
    myName: String,
    isAdmin: Boolean,
    accountMenu: @Composable () -> Unit,
    onNavigate: (HomeTarget) -> Unit,
    badges: Map<String, Int> = emptyMap()
) {
    Column(Modifier.fillMaxSize()) {
        AppHeader(accountMenu = accountMenu)
        HomePane(uid = uid, myName = myName, isAdmin = isAdmin, onNavigate = onNavigate, badges = badges)
    }
}

package org.yourchurch.app.ui.community

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.yourchurch.app.R
import org.yourchurch.app.data.AuthError
import org.yourchurch.app.data.LiveViewModel
import org.yourchurch.app.data.ReadReceipts
import org.yourchurch.app.ui.common.AppearanceDialog
import org.yourchurch.app.ui.common.ConnectionCheckDialog
import org.yourchurch.app.ui.common.DeleteAccountDialog
import org.yourchurch.app.ui.common.LanguageDialog
import org.yourchurch.app.ui.common.NotificationCheckDialog
import org.yourchurch.app.ui.common.NotificationSettingsDialog
import org.yourchurch.app.ui.common.SearchUi
import org.yourchurch.app.ui.common.WhatsNewDialog
import org.yourchurch.app.ui.giving.GiveScreen
import org.yourchurch.app.ui.theme.ToneKind
import org.yourchurch.app.ui.theme.tone

/**
 * The More tab: everything that is not needed every day. [route] says which page is open
 * ("contact", "give", "feedback"), or null for the list.
 */
@Composable
fun MoreScreen(
    uid: String,
    myName: String,
    isAdmin: Boolean,
    isOwner: Boolean,
    accountMenu: @Composable () -> Unit,
    route: String?,
    onRouteChange: (String?) -> Unit,
    onFullScreen: (Boolean) -> Unit,
    deleting: Boolean,
    deleteError: AuthError?,
    onDeleteAccount: (String) -> Unit,
    onClearDeleteError: () -> Unit
) {
    val context = LocalContext.current
    var showAppearance by remember { mutableStateOf(false) }
    var showLanguage by remember { mutableStateOf(false) }
    var showNotifCheck by remember { mutableStateOf(false) }
    var showNotifSettings by remember { mutableStateOf(false) }
    var showConnection by remember { mutableStateOf(false) }
    var showLive by remember { mutableStateOf(false) }
    var showWhatsNew by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var receiptsOn by remember { mutableStateOf(ReadReceipts.isEnabled(context)) }
    val liveVm: LiveViewModel = viewModel(key = "live-$uid")
    val liveInfo by liveVm.info.collectAsStateWithLifecycle()
    var inner by remember { mutableStateOf(false) }

    if (route != null) {
        BackHandler { onRouteChange(null) }
        Column(Modifier.fillMaxSize()) {
            if (!inner) {
                val title = when (route) {
                    "contact" -> stringResource(R.string.section_contact)
                    "feedback" -> stringResource(R.string.section_feedback)
                    "dashboard" -> stringResource(R.string.dashboard_title)
                    "visitors" -> stringResource(R.string.visitors_title)
                    "about" -> stringResource(R.string.about_title)
                    "leaders" -> stringResource(R.string.roles_title)
                    "verses" -> stringResource(R.string.verses_title)
                    else -> null  // the Give page has its own title
                }
                SubBar(title = title, onBack = { onRouteChange(null) })
            }
            when (route) {
                "contact" -> ContactPane(
                    uid = uid,
                    isAdmin = isAdmin,
                    onFullScreen = { inner = it; onFullScreen(it) }
                )
                "give" -> GiveScreen(uid = uid, isAdmin = isAdmin)
                "dashboard" -> LeaderDashboard()
                "visitors" -> VisitorsPane()
                "about" -> AboutPane(isAdmin = isAdmin)
                "leaders" -> LeadersPane(isOwner = isOwner)
                "verses" -> VerseBankPane()
                else -> FeedbackPane(uid = uid, myName = myName, isAdmin = isAdmin)
            }
        }
    } else {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            HubHeader(accountMenu)
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GroupLabel(R.string.more_group_church)
                MoreRow(Icons.Outlined.Search, R.string.search_title, ToneKind.Blue) { SearchUi.visible = true }
                MoreRow(Icons.Outlined.Info, R.string.about_title, ToneKind.Teal) { onRouteChange("about") }
                MoreRow(Icons.Outlined.Directions, R.string.section_contact, ToneKind.Blue) { onRouteChange("contact") }
                MoreRow(Icons.Outlined.CardGiftcard, R.string.tab_give, ToneKind.Gold) { onRouteChange("give") }
                MoreRow(Icons.Outlined.ChatBubbleOutline, R.string.section_feedback, ToneKind.Teal) { onRouteChange("feedback") }
                if (isAdmin) MoreRow(Icons.Outlined.AdminPanelSettings, R.string.roles_title, ToneKind.Rose) { onRouteChange("leaders") }
                if (isAdmin) MoreRow(Icons.Outlined.ManageAccounts, R.string.dashboard_title, ToneKind.Slate) { onRouteChange("dashboard") }
                if (isAdmin) MoreRow(Icons.Outlined.Person, R.string.visitors_title, ToneKind.Rose) { onRouteChange("visitors") }
                if (isAdmin) MoreRow(Icons.Outlined.MenuBook, R.string.verses_title, ToneKind.Purple) { onRouteChange("verses") }
                if (isAdmin) MoreRow(Icons.Outlined.Videocam, R.string.live_settings, ToneKind.Coral) { showLive = true }

                GroupLabel(R.string.more_group_settings)
                MoreRow(Icons.Outlined.Palette, R.string.appearance_title, ToneKind.Purple) { showAppearance = true }
                MoreRow(Icons.Outlined.Language, R.string.language, ToneKind.Green) { showLanguage = true }
                MoreRow(Icons.Outlined.Notifications, R.string.notif_settings_title, ToneKind.Coral) { showNotifSettings = true }
                MoreSwitchRow(
                    icon = Icons.Outlined.DoneAll,
                    label = R.string.read_receipts,
                    hint = R.string.read_receipts_hint,
                    kind = ToneKind.Teal,
                    checked = receiptsOn,
                    onChange = { receiptsOn = it; ReadReceipts.setEnabled(context, it) }
                )
                MoreRow(Icons.Outlined.Info, R.string.privacy_policy, ToneKind.Slate) {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(R.string.privacy_url))))
                    }
                }

                GroupLabel(R.string.more_group_help)
                MoreRow(Icons.Outlined.AutoAwesome, R.string.whatsnew_title, ToneKind.Gold) { showWhatsNew = true }
                MoreRow(Icons.Outlined.Notifications, R.string.notif_check_title, ToneKind.Slate) { showNotifCheck = true }
                MoreRow(Icons.Outlined.Settings, R.string.cc_title, ToneKind.Slate) { showConnection = true }

                GroupLabel(R.string.more_group_account)
                MoreRow(Icons.Outlined.DeleteForever, R.string.account_delete, ToneKind.Rose, danger = true) { showDelete = true }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showAppearance) AppearanceDialog(onDismiss = { showAppearance = false }, isAdmin = isAdmin)
    if (showLanguage) LanguageDialog(onDismiss = { showLanguage = false })
    if (showConnection) ConnectionCheckDialog(onDismiss = { showConnection = false })
    if (showWhatsNew) WhatsNewDialog(onDismiss = { showWhatsNew = false })
    if (showLive) LiveSettingsDialog(info = liveInfo, vm = liveVm, onDismiss = { showLive = false })
    if (showDelete) {
        DeleteAccountDialog(
            working = deleting,
            error = deleteError,
            onConfirm = onDeleteAccount,
            onDismiss = { showDelete = false; onClearDeleteError() }
        )
    }
    if (showNotifCheck) NotificationCheckDialog(uid = uid, onDismiss = { showNotifCheck = false })
    if (showNotifSettings) {
        NotificationSettingsDialog(
            uid = uid,
            onCheckPhone = { showNotifSettings = false; showNotifCheck = true },
            onDismiss = { showNotifSettings = false }
        )
    }
}

@Composable
private fun GroupLabel(@StringRes text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp)
    )
}

@Composable
private fun MoreRow(icon: ImageVector, @StringRes label: Int, kind: ToneKind, danger: Boolean = false, onClick: () -> Unit) {
    val colors = tone(kind)
    val tint = if (danger) MaterialTheme.colorScheme.error else colors.accent
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp, pressedElevation = 1.dp),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(40.dp).background(if (danger) MaterialTheme.colorScheme.errorContainer else colors.container, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = tint)
            }
            Spacer(Modifier.width(14.dp))
            Text(
                stringResource(label),
                style = MaterialTheme.typography.titleMedium,
                color = if (danger) MaterialTheme.colorScheme.error else Color.Unspecified,
                modifier = Modifier.weight(1f)
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** A row with a switch: tapping it flips the switch. */
@Composable
private fun MoreSwitchRow(
    icon: ImageVector,
    @StringRes label: Int,
    @StringRes hint: Int,
    kind: ToneKind,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    val colors = tone(kind)
    Card(
        onClick = { onChange(!checked) },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp, pressedElevation = 1.dp),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(40.dp).background(colors.container, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = colors.accent)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Text(stringResource(label), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = null)
        }
    }
}

/** A back arrow (and a title) above a page opened from the More list. */
@Composable
private fun SubBar(title: String?, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.close))
        }
        if (title != null) Text(title, style = MaterialTheme.typography.titleLarge)
    }
}

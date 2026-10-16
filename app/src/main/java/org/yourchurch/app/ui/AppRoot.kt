package org.yourchurch.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import org.yourchurch.app.Notifications
import org.yourchurch.app.PendingRoom
import org.yourchurch.app.PendingTarget
import org.yourchurch.app.R
import org.yourchurch.app.SafetySetup
import org.yourchurch.app.data.AuthState
import org.yourchurch.app.data.AuthViewModel
import org.yourchurch.app.data.BadgesViewModel
import org.yourchurch.app.data.BrandingStore
import org.yourchurch.app.data.ChatReadStore
import org.yourchurch.app.data.ChatViewModel
import org.yourchurch.app.data.NotificationPrefs
import org.yourchurch.app.data.PushTokens
import org.yourchurch.app.data.SectionSeen
import org.yourchurch.app.ui.auth.SignInScreen
import org.yourchurch.app.ui.auth.VerifyEmailScreen
import org.yourchurch.app.ui.chat.ChatScreen
import org.yourchurch.app.ui.common.AccountMenu
import org.yourchurch.app.ui.common.ChatCircleIcon
import org.yourchurch.app.ui.common.ChurchIcon
import org.yourchurch.app.ui.common.CompleteProfileDialog
import org.yourchurch.app.ui.common.HostedHeader
import org.yourchurch.app.ui.common.LoadingLogo
import org.yourchurch.app.ui.common.LocalHeaderHosted
import org.yourchurch.app.ui.common.OfflineBanner
import org.yourchurch.app.ui.common.Onboarding
import org.yourchurch.app.ui.common.ProfileProblemBanner
import org.yourchurch.app.ui.common.SearchUi
import org.yourchurch.app.ui.common.WelcomeScreen
import org.yourchurch.app.ui.common.WhatsNew
import org.yourchurch.app.ui.common.WhatsNewDialog
import org.yourchurch.app.ui.common.rememberIsOnline
import org.yourchurch.app.ui.community.ChurchHub
import org.yourchurch.app.ui.community.HomeScreen
import org.yourchurch.app.ui.community.HomeTarget
import org.yourchurch.app.ui.community.MoreScreen
import org.yourchurch.app.ui.community.SearchDialog
import org.yourchurch.app.ui.community.WordHub
import org.yourchurch.app.ui.theme.isAppDark

@Composable
fun AppRoot(authVm: AuthViewModel = viewModel()) {
    val state by authVm.state.collectAsStateWithLifecycle()

    val context = LocalContext.current
    LaunchedEffect(state.user?.uid) { SafetySetup.identify(state.user?.uid) }
    LaunchedEffect(Unit) { BrandingStore.start() }
    LaunchedEffect(Unit) {
        Notifications.createChannel(context)
        NotificationPrefs.load(context)
    }
    // Keep notification topics in step with who is signed in (and in which language).
    val signedIn = state.user != null && state.emailVerified
    LaunchedEffect(signedIn, state.ready) {
        if (state.ready) Notifications.sync(context, signedIn)
    }

    // Tell the notification server where to send this person's chat notifications.
    val uidForPush = state.user?.uid
    LaunchedEffect(signedIn, state.ready, uidForPush) {
        if (state.ready && signedIn && uidForPush != null && Notifications.isEnabled(context)) {
            runCatching { PushTokens.register(uidForPush) }
            runCatching { NotificationPrefs.sync(uidForPush) }
        }
    }

    // The logo animation is shown for at least a moment, so it can zoom in and sway before the first page appears.
    // The start: the logo is on screen from the first moment, stays still for a very short moment, pops out to full size,
    // and then moves gently until the first page is ready.
    val warmStart = remember { SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime() > WARM_START_MS }
    var splashDone by rememberSaveable { mutableStateOf(false) }
    var overlayGone by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!splashDone) {
            // A warm start (the app was already running, for example opened again from the recent apps): a short look only.
            delay(if (warmStart) WARM_SPLASH_MS else SPLASH_HOLD_MS + SPLASH_POP_MS + SPLASH_MOVE_MS)
            splashDone = true
        }
    }
    Box(Modifier.fillMaxSize()) {
    when {
        !state.ready -> Box(Modifier.fillMaxSize())
        state.user == null -> SignInScreen(authVm)
        !state.emailVerified -> VerifyEmailScreen(authVm)
        else -> {
            var welcomeDone by remember { mutableStateOf(Onboarding.isDone(context)) }
            if (!welcomeDone) {
                WelcomeScreen(onDone = { Onboarding.markDone(context); WhatsNew.markSeen(context); welcomeDone = true })
            } else {
                MainScaffold(state, authVm)
            }
        }
    }
        // The logo covers the page while it loads. When the app is ready it shrinks and flies to its place in the top card
        // (or just fades away where there is no top card, like the sign-in page), and the page shows underneath.
        if (!overlayGone) {
            Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }) {
                LoadingLogo(
                    holdMs = if (warmStart) 0L else SPLASH_HOLD_MS,
                    leaving = state.ready && splashDone,
                    flyToHeader = state.user != null && state.emailVerified && Onboarding.isDone(context),
                    onFinished = { overlayGone = true }
                )
            }
        }
    }
}

private enum class Tab(@StringRes val label: Int, val icon: ImageVector) {
    Home(R.string.tab_home, Icons.Outlined.Home),
    Church(R.string.tab_updates, ChurchIcon),
    Chat(R.string.tab_chat, ChatCircleIcon),
    Word(R.string.tab_word, Icons.Outlined.MenuBook),
    More(R.string.tab_more, Icons.Outlined.MoreHoriz)
}

@Composable
private fun MainScaffold(auth: AuthState, authVm: AuthViewModel) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var hideBottomBar by remember { mutableStateOf(false) }
    // Which section the Church tab shows, and which page the More tab has open (so Home can jump straight to them).
    var churchSection by rememberSaveable { mutableIntStateOf(0) }
    var wordSection by rememberSaveable { mutableIntStateOf(0) }
    var moreRoute by rememberSaveable { mutableStateOf<String?>(null) }

    // Ask once for permission to show notifications (needed on Android 13 and newer).
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(auth.user?.uid) {
        if (Build.VERSION.SDK_INT >= 33 &&
            Notifications.isEnabled(context) &&
            !Notifications.askedForPermission(context) &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Notifications.markAskedForPermission(context)
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val user = auth.user ?: return

    // The chat data is loaded here as well, so the Chat tab can show a badge when something new has arrived.
    val badgeVm: BadgesViewModel = viewModel(key = "badges-${user.uid}")
    val badgeItems by badgeVm.state.collectAsStateWithLifecycle()
    // New things in each Church section since this phone last opened it (what you posted yourself is not counted).
    val sectionBadges = remember(badgeItems, SectionSeen.version) {
        badgeItems.mapValues { (key, list) ->
            val seen = SectionSeen.lastSeen(context, key)
            list.count { it.createdAt > seen && it.authorId != user.uid }
        }
    }
    val chatVm: ChatViewModel = viewModel(key = "chat-${user.uid}")
    val chatState by chatVm.state.collectAsStateWithLifecycle()
    LaunchedEffect(user.uid, auth.displayName, auth.isAdmin) {
        chatVm.start(user.uid, auth.displayName.ifBlank { user.email ?: "" }, auth.isAdmin)
    }
    val unreadChats = remember(chatState.myRooms, chatState.muted, ChatReadStore.version) {
        chatState.myRooms.count { room ->
            val last = room.lastMessageAt?.time ?: 0L
            room.id !in chatState.muted && last > ChatReadStore.lastRead(context, user.uid, room.id) && room.lastMessageBy != user.uid
        }
    }

    // Tapping a chat notification opens the Chat tab.
    val pendingRoom = PendingRoom.roomId
    LaunchedEffect(pendingRoom) {
        if (pendingRoom != null) selected = Tab.Chat.ordinal
    }

    val accountMenu: @Composable () -> Unit = {
        AccountMenu(
            auth = auth,
            onSaveProfile = authVm::saveProfile,
            onSignOut = authVm::signOut
        )
    }

    // Home's shortcuts open the right section of the right tab.
    val navigate: (HomeTarget) -> Unit = { target ->
        when (target) {
            HomeTarget.Announcements -> { churchSection = 0; selected = Tab.Church.ordinal }
            HomeTarget.Events -> { churchSection = 1; selected = Tab.Church.ordinal }
            HomeTarget.Sermons -> { churchSection = 2; selected = Tab.Church.ordinal }
            HomeTarget.Serve -> { churchSection = 3; selected = Tab.Church.ordinal }
            HomeTarget.Prayer -> { churchSection = 4; selected = Tab.Church.ordinal }
            HomeTarget.Give -> { moreRoute = "give"; selected = Tab.More.ordinal }
            HomeTarget.Chat -> selected = Tab.Chat.ordinal
            HomeTarget.Contact -> { moreRoute = "contact"; selected = Tab.More.ordinal }
            HomeTarget.Reading -> { wordSection = 2; selected = Tab.Word.ordinal }
            HomeTarget.Study -> { wordSection = 1; selected = Tab.Word.ordinal }
        }
    }

    // The search page, opened from the magnifying glass at the top of every page.
    if (SearchUi.visible) {
        SearchDialog(
            isAdmin = auth.isAdmin,
            onNavigate = { navigate(it) },
            onDismiss = { SearchUi.visible = false }
        )
    }

    // A tapped notification about giving, sermons, announcements or events opens that section.
    val pendingTarget = PendingTarget.target
    LaunchedEffect(pendingTarget) {
        when (pendingTarget) {
            "give" -> navigate(HomeTarget.Give)
            "sermons" -> navigate(HomeTarget.Sermons)
            "news" -> navigate(HomeTarget.Announcements)
            "events" -> navigate(HomeTarget.Events)
        }
        if (pendingTarget != null) PendingTarget.target = null
    }

    // The back button on any tab leads home first, and only leaves the app from Home.
    BackHandler(enabled = selected != Tab.Home.ordinal && !hideBottomBar) { selected = Tab.Home.ordinal }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (!hideBottomBar) Column {
              HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline)
              // The bar has a colour of its own, so it is clearly different from the page above it.
              val navBarColor = if (isAppDark()) MaterialTheme.colorScheme.surfaceVariant
              else lerp(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.primary, 0.10f)
              val haptic = LocalHapticFeedback.current
              NavigationBar(containerColor = navBarColor, tonalElevation = 0.dp) {
                Tab.entries.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selected == index,
                        onClick = {
                            if (index == Tab.More.ordinal && selected == index) moreRoute = null
                            if (selected != index) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            selected = index
                        },
                        icon = {
                            val count = when (tab) {
                                Tab.Chat -> unreadChats
                                Tab.Church -> sectionBadges.values.sum()
                                else -> 0
                            }
                            // The open tab gets a soft pill behind its icon, in a colour of its own (it fades between tabs).
                            val pill by animateColorAsState(
                                if (selected == index) tabPill(tab, isAppDark(), lerp(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primary, 0.22f))
                                else Color.Transparent,
                                animationSpec = tween(180),
                                label = "pill"
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(pill)
                                    .padding(horizontal = 18.dp, vertical = 5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (count > 0) {
                                    BadgedBox(
                                        badge = { Badge { Text(if (count > 9) "9+" else count.toString()) } }
                                    ) { Icon(tab.icon, contentDescription = null, modifier = Modifier.size(30.dp)) }
                                } else {
                                    Icon(tab.icon, contentDescription = null, modifier = Modifier.size(30.dp))
                                }
                            }
                        },
                        label = {
                            Text(
                                stringResource(tab.label),
                                maxLines = 1,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontSize = 16.sp,
                                    fontWeight = if (selected == index) FontWeight.Bold else FontWeight.SemiBold
                                )
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = Color.Transparent,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
              }
        }
    ) { padding ->
        val online = rememberIsOnline()
        Column(Modifier.padding(padding)) {
          Box(Modifier.weight(1f)) {
            CompositionLocalProvider(LocalHeaderHosted provides true) {
            AnimatedContent(
                targetState = selected,
                modifier = Modifier.fillMaxSize(),
                // The old page fades out, then the new one fades in (no two pages on top of each other).
                transitionSpec = { fadeIn(tween(140, delayMillis = 70)) togetherWith fadeOut(tween(70)) },
                label = "tab"
            ) { tabIndex ->
            when (Tab.entries[tabIndex]) {
                Tab.Home -> HomeScreen(
                    uid = user.uid,
                    myName = auth.displayName.ifBlank { user.email ?: "" },
                    isAdmin = auth.isAdmin,
                    accountMenu = accountMenu,
                    onNavigate = navigate,
                    badges = sectionBadges
                )
                Tab.Church -> ChurchHub(
                    uid = user.uid,
                    myName = auth.displayName.ifBlank { user.email ?: "" },
                    isAdmin = auth.isAdmin,
                    accountMenu = accountMenu,
                    section = churchSection,
                    onSectionChange = { churchSection = it },
                    badges = sectionBadges,
                    onFullScreen = { hideBottomBar = it }
                )
                Tab.Chat -> ChatScreen(
                    uid = user.uid,
                    myName = auth.displayName.ifBlank { user.email ?: "" },
                    myPhone = auth.phone,
                    isAdmin = auth.isAdmin,
                    isOwner = auth.isOwner,
                    accountMenu = accountMenu,
                    onFullScreen = { hideBottomBar = it }
                )
                Tab.Word -> WordHub(
                    uid = user.uid,
                    myName = auth.displayName.ifBlank { user.email ?: "" },
                    isAdmin = auth.isAdmin,
                    accountMenu = accountMenu,
                    section = wordSection,
                    onSectionChange = { wordSection = it }
                )
                Tab.More -> MoreScreen(
                    uid = user.uid,
                    myName = auth.displayName.ifBlank { user.email ?: "" },
                    isAdmin = auth.isAdmin,
                    isOwner = auth.isOwner,
                    accountMenu = accountMenu,
                    route = moreRoute,
                    onRouteChange = { moreRoute = it },
                    deleting = auth.deleting,
                    deleteError = auth.deleteError,
                    onDeleteAccount = authVm::deleteAccount,
                    onClearDeleteError = authVm::clearDeleteError,
                    onFullScreen = { hideBottomBar = it }
                )
            }
            }
            }
            // The header card is drawn once, here, on top of the pages: it never moves or fades when the tab changes.
            Box(Modifier.align(Alignment.TopCenter)) { HostedHeader(accountMenu = accountMenu) }
          }
          if (online && auth.loadError != null) ProfileProblemBanner(auth.loadError, onRetry = authVm::retryProfile)
          if (!online) OfflineBanner()
        }
    }

    // Ask for a name (required) and a phone number (optional for people who joined before phone numbers existed).
    val settings = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    var phoneSkipped by remember(user.uid) {
        mutableStateOf(settings.getBoolean("phone_skipped_${user.uid}", false))
    }
    val needsProfile = auth.profileLoaded &&
        (auth.displayName.isBlank() || (auth.phone.isBlank() && !phoneSkipped))
    // "What's new", once after an update (not for someone who has just installed the app).
    var showWhatsNew by remember { mutableStateOf(WhatsNew.shouldShow(context)) }
    if (showWhatsNew && !needsProfile) {
        WhatsNewDialog(onDismiss = { WhatsNew.markSeen(context); showWhatsNew = false })
    }
    if (needsProfile) {
        CompleteProfileDialog(
            initialName = auth.displayName,
            initialPhone = auth.phone,
            onSave = { name, phone ->
                authVm.saveProfile(name, phone)
                if (phone.isBlank()) {
                    settings.edit().putBoolean("phone_skipped_${user.uid}", true).apply()
                    phoneSkipped = true
                }
            },
            onSkipPhone = {
                settings.edit().putBoolean("phone_skipped_${user.uid}", true).apply()
                phoneSkipped = true
            },
            onSignOut = authVm::signOut
        )
    }
}


/** The soft colour behind the open tab's icon: a different one for each tab. */
private fun tabPill(tab: Tab, dark: Boolean, home: Color): Color = when (tab) {
    Tab.Home -> home
    Tab.Church -> if (dark) Color(0xFF12433A) else Color(0xFFBFE6DC)
    Tab.Chat -> if (dark) Color(0xFF1B3556) else Color(0xFFC6DAF8)
    Tab.Word -> if (dark) Color(0xFF5A2A16) else Color(0xFFF8D3BF)
    Tab.More -> if (dark) Color(0xFF2A323E) else Color(0xFFD3D9E2)
}

/**
 * The logo pops out at once (no waiting), the pop takes about 0.6 seconds, and then it moves for 3 seconds (milliseconds below).
 * It stays until the app has loaded, then shrinks into the top card while the home page opens.
 */
private const val SPLASH_HOLD_MS = 0L
private const val SPLASH_POP_MS = 600L
private const val SPLASH_MOVE_MS = 3000L
/** An app that started more than this long ago was already running: it only shows the logo briefly. */
private const val WARM_START_MS = 8000L
private const val WARM_SPLASH_MS = 900L

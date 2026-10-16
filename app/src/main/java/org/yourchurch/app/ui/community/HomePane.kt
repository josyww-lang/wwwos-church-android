package org.yourchurch.app.ui.community

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.yourchurch.app.LocaleManager
import org.yourchurch.app.R
import org.yourchurch.app.data.AnnouncementsViewModel
import org.yourchurch.app.data.ChurchInfoViewModel
import org.yourchurch.app.data.EventsViewModel
import org.yourchurch.app.data.PlansViewModel
import org.yourchurch.app.data.ReadingPlan
import org.yourchurch.app.data.VerseViewModel
import org.yourchurch.app.data.streakOf
import org.yourchurch.app.data.toLocalDate
import org.yourchurch.app.data.todayIndex
import org.yourchurch.app.ui.common.ChatCircleIcon
import org.yourchurch.app.ui.common.CrossIcon
import org.yourchurch.app.ui.theme.ToneKind
import org.yourchurch.app.ui.theme.heroBrush
import org.yourchurch.app.ui.theme.highlightColor
import org.yourchurch.app.ui.theme.highlightTextColor
import org.yourchurch.app.ui.theme.tone
import org.yourchurch.app.widget.WidgetSync

private class Shortcut(val icon: ImageVector, @StringRes val label: Int, val target: HomeTarget, val kind: ToneKind)

/** Every shortcut a person can pin on Home. */
private val SHORTCUTS: Map<String, Shortcut> by lazy {
    linkedMapOf(
        "give" to Shortcut(Icons.Outlined.CardGiftcard, R.string.tab_give, HomeTarget.Give, ToneKind.Gold),
        "pray" to Shortcut(CrossIcon, R.string.community_prayer, HomeTarget.Prayer, ToneKind.Purple),
        "serve" to Shortcut(Icons.Outlined.Favorite, R.string.section_serve, HomeTarget.Serve, ToneKind.Teal),
        "sermons" to Shortcut(Icons.Outlined.PlayCircle, R.string.sermons_title, HomeTarget.Sermons, ToneKind.Coral),
        "events" to Shortcut(Icons.Outlined.Event, R.string.community_events, HomeTarget.Events, ToneKind.Green),
        "news" to Shortcut(Icons.Outlined.Campaign, R.string.title_announcements, HomeTarget.Announcements, ToneKind.Blue),
        "chat" to Shortcut(ChatCircleIcon, R.string.tab_chat, HomeTarget.Chat, ToneKind.Blue),
        "visit" to Shortcut(Icons.Outlined.Directions, R.string.section_contact, HomeTarget.Contact, ToneKind.Slate)
    )
}

/**
 * The Home page, as in the design: a welcome card with the Sunday countdown and the verse of the week,
 * four shortcuts (press and hold to change them), today's announcement and the next event.
 * Pull down to refresh.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomePane(
    uid: String,
    myName: String,
    isAdmin: Boolean,
    onNavigate: (HomeTarget) -> Unit,
    /** How many new things are waiting in each section (key: news, events, sermons, serve, pray). */
    badges: Map<String, Int> = emptyMap(),
    plansVm: PlansViewModel = viewModel(key = "plans-$uid"),
    verseVm: VerseViewModel = viewModel(key = "verse"),
    infoVm: ChurchInfoViewModel = viewModel(key = "church-$uid"),
    eventsVm: EventsViewModel = viewModel(key = "events-$uid"),
    announcementsVm: AnnouncementsViewModel = viewModel(key = uid)
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val info = infoVm.state.collectAsStateWithLifecycle().value.info
    val events = eventsVm.state.collectAsStateWithLifecycle().value
    val announcements = announcementsVm.state.collectAsStateWithLifecycle().value
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { announcementsVm.start() }
    LaunchedEffect(Unit) { Favorites.load(context) }

    var refreshing by remember { mutableStateOf(false) }
    var editingShortcuts by remember { mutableStateOf(false) }
    // "Coming up" shows this week first; one tap shows the whole month.
    var thisWeek by rememberSaveable { mutableStateOf(true) }
    val weekEnd = remember {
        LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).atTime(23, 59, 59)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    val language = LocaleManager.languageCode(context)
    val firstName = myName.trim().substringBefore(' ').substringBefore('@').replaceFirstChar { it.uppercase() }
    val welcome = info.welcomeFor(language)
    val dailyVerse by verseVm.verse.collectAsStateWithLifecycle()
    // Today's verse from the church's own list, in the person's language (English if that language is empty).
    val bankVerse by verseVm.bank.collectAsStateWithLifecycle()
    val bankPair = bankVerse?.forLanguage(language)
    val verse =
        bankPair?.let { (text, reference) -> if (reference.isBlank()) text else text + "  \u2014  " + reference }
            ?: dailyVerse?.let { it.text + "  \u2014  " + it.reference }
            ?: stringResource(R.string.home_default_verse)
    val nextEvent = remember(events.events) {
        val cutoff = System.currentTimeMillis() - 2 * 3_600_000L
        events.events.filter { it.start.time >= cutoff }.minByOrNull { it.start.time }
    }
    val comingUp = remember(announcements.upcoming, events.events, nextEvent) {
        val now = System.currentTimeMillis()
        // Only what falls in this month; the rest is in the Church tab.
        val monthEnd = YearMonth.now().atEndOfMonth().atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val items = mutableListOf<ComingItem>()
        announcements.upcoming.forEach { items += ComingItem(false, it.id, it.title, it.date.time) }
        events.events.filter { it.start.time > now && it.id != nextEvent?.id }
            .forEach { items += ComingItem(true, it.id, it.title, it.start.time) }
        items.filter { it.time <= monthEnd }.sortedBy { it.time }.take(10)
    }
    val plansState by plansVm.state.collectAsStateWithLifecycle()
    LaunchedEffect(uid, isAdmin, myName) { plansVm.start(uid, myName, isAdmin) }
    // Keep the home-screen widget up to date with what this page shows.
    val appName = stringResource(R.string.church_name)
    val labelReading = stringResource(R.string.reading_today)
    val labelEvent = stringResource(R.string.home_next_event)
    val labelVerse = stringResource(R.string.home_verse_day)
    LaunchedEffect(plansState.plan, events.events, verse, labelReading) {
        val upcoming = events.events.filter { it.start.time >= System.currentTimeMillis() - 2 * 3_600_000L }.sortedBy { it.start }
        WidgetSync.save(context, appName, Triple(labelReading, labelEvent, labelVerse), plansState.plan, upcoming, verse)
    }
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greeting = stringResource(
        when {
            hour < 12 -> R.string.home_good_morning
            hour < 18 -> R.string.home_good_afternoon
            else -> R.string.home_good_evening
        }
    )

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            scope.launch {
                refreshing = true
                announcementsVm.start()
                delay(900)
                refreshing = false
            }
        },
        modifier = Modifier.fillMaxSize()
    ) {
      BoxWithConstraints(Modifier.fillMaxSize()) {
        // The page uses the whole screen: the cards grow with it (and the page scrolls on a small phone).
        val tileRows = ((Favorites.keys.size + 3) / 4).coerceAtLeast(1)
        val heroMin = maxOf(200.dp, maxHeight * (if (tileRows > 1) 0.32f else 0.38f))
        val tileHeight = maxOf(88.dp, maxHeight * (if (tileRows > 1) 0.115f else 0.15f))
        val cardMin = maxOf(88.dp, maxHeight * 0.14f)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 6.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Live now (red) while we stream; otherwise a calm strip with the next service and the YouTube link
            LiveBanner(uid = uid, isAdmin = isAdmin, outerPadding = 0.dp)

            // Welcome card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = heroMin)
                    .shadow(6.dp, RoundedCornerShape(28.dp))
                    .clip(RoundedCornerShape(28.dp))
                    .background(heroBrush())
                    .padding(horizontal = 24.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(greeting, style = MaterialTheme.typography.titleMedium.copy(fontSize = 19.sp), color = Color.White.copy(alpha = 0.9f))
                Text(
                    if (firstName.isNotBlank()) stringResource(R.string.home_welcome, firstName) else stringResource(R.string.home_welcome_default),
                    style = MaterialTheme.typography.headlineMedium.copy(fontSize = 36.sp, lineHeight = 44.sp),
                    color = Color.White
                )
                if (welcome.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(welcome, style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.92f))
                }
                if (verse.isNotBlank()) {
                    Spacer(Modifier.height(16.dp))
                    // The verse sits on a panel of its own colour (a warm tint of the second colour), so it stands out.
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(highlightColor().copy(alpha = 0.22f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Text(
                            stringResource(R.string.home_verse_day),
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                            color = Color(0xFFF1D98A)
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(verse, style = MaterialTheme.typography.bodyLarge, color = Color.White)
                    }
                }
            }

            // Shortcuts (press and hold to change them): rows of four
            Favorites.keys.mapNotNull { key -> SHORTCUTS[key]?.let { key to it } }.chunked(4).forEach { rowOfTiles ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    rowOfTiles.forEach { (key, shortcut) ->
                        HomeTile(
                            icon = shortcut.icon,
                            label = shortcut.label,
                            kind = shortcut.kind,
                            badge = badges[key] ?: 0,
                            modifier = Modifier.weight(1f).height(tileHeight),
                            onLongClick = { editingShortcuts = true },
                            onClick = { onNavigate(shortcut.target) }
                        )
                    }
                    repeat(4 - rowOfTiles.size) { Spacer(Modifier.weight(1f)) }
                }
            }

            // Today: every announcement for today, each on its own card
            if (announcements.today.isEmpty()) {
                InfoCard(
                    heading = stringResource(R.string.home_today),
                    title = stringResource(R.string.home_no_announcement),
                    detail = "",
                    minHeight = cardMin,
                    onClick = { onNavigate(HomeTarget.Announcements) }
                )
            } else {
                announcements.today.take(3).forEach { today ->
                    InfoCard(
                        heading = stringResource(R.string.home_today) + if (today.time.isNotBlank()) "  \u00B7  " + today.time else "",
                        title = today.title,
                        detail = today.message,
                        minHeight = cardMin,
                        onClick = { onNavigate(HomeTarget.Announcements) }
                    )
                }
            }

            // Today's Bible reading, while there is a plan running
            plansState.plan?.let { plan ->
                val index = plan.todayIndex()
                if (index < plan.days.size) {
                    ReadingCard(
                        plan = plan,
                        index = index,
                        done = plansState.done,
                        minHeight = cardMin,
                        onToggle = { plansVm.toggleDay(index) },
                        onClick = { onNavigate(HomeTarget.Reading) }
                    )
                }
            }

            // Next event
            EventCard(event = nextEvent, minHeight = cardMin, onClick = { onNavigate(HomeTarget.Events) })

            // Coming up: this week or this month. Scroll down to see it.
            if (comingUp.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                    Text(stringResource(R.string.home_coming_up), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    FilterChip(selected = thisWeek, onClick = { thisWeek = true }, label = { Text(stringResource(R.string.home_this_week)) })
                    Spacer(Modifier.width(8.dp))
                    FilterChip(selected = !thisWeek, onClick = { thisWeek = false }, label = { Text(stringResource(R.string.home_this_month)) })
                }
                val shown = if (thisWeek) comingUp.filter { it.time <= weekEnd } else comingUp
                if (shown.isEmpty()) {
                    Text(
                        stringResource(R.string.home_nothing_week),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                shown.forEach { item ->
                    ComingUpCard(item = item, onClick = { onNavigate(if (item.isEvent) HomeTarget.Events else HomeTarget.Announcements) })
                }
                TextButton(onClick = { onNavigate(HomeTarget.Announcements) }) { Text(stringResource(R.string.home_see_all)) }
            }
            Spacer(Modifier.height(12.dp))
        }
      }
    }

    if (editingShortcuts) {
        FavoritesDialog(
            options = Favorites.ALL.mapNotNull { key -> SHORTCUTS[key]?.let { key to it.label } },
            onDismiss = { editingShortcuts = false }
        )
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeTile(
    icon: ImageVector,
    @StringRes label: Int,
    kind: ToneKind,
    badge: Int,
    modifier: Modifier,
    onLongClick: () -> Unit,
    onClick: () -> Unit
) {
    val colors = tone(kind)
    val shape = RoundedCornerShape(18.dp)
    Box(modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .shadow(4.dp, shape)
                .clip(shape)
                .background(lerp(colors.container, colors.accent, 0.14f))
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = 4.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier.size(46.dp).background(MaterialTheme.colorScheme.surface, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(label),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                color = colors.accent,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (badge > 0) CountBadge(badge, Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 8.dp))
    }
}

@Composable
private fun InfoCard(
    heading: String,
    title: String,
    detail: String,
    minHeight: Dp,
    icon: ImageVector = Icons.Outlined.Campaign,
    onClick: () -> Unit
) {
    val gold = highlightColor()
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        // A warm, light tint of the palette's second colour makes today's announcement stand out.
        colors = CardDefaults.cardColors(containerColor = lerp(MaterialTheme.colorScheme.surface, gold, 0.06f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp, pressedElevation = 1.dp),
        border = BorderStroke(1.dp, gold.copy(alpha = 0.28f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            Modifier.fillMaxWidth().heightIn(min = minHeight).padding(horizontal = 20.dp, vertical = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(46.dp).background(gold.copy(alpha = 0.22f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = highlightTextColor())
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(heading.uppercase(), style = MaterialTheme.typography.labelMedium, color = highlightTextColor())
                    Spacer(Modifier.height(2.dp))
                    Text(title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 19.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (detail.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            detail,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/** The next event, with its date as a gold tile (the palette's second colour). */
@Composable
private fun EventCard(event: org.yourchurch.app.data.Event?, minHeight: Dp, onClick: () -> Unit) {
    val context = LocalContext.current
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp, pressedElevation = 1.dp),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = minHeight).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (event != null) {
                Column(
                    modifier = Modifier
                        .background(highlightColor(), RoundedCornerShape(14.dp))
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        SimpleDateFormat("d", Locale.getDefault()).format(event.start),
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 30.sp, lineHeight = 34.sp),
                        color = Color(0xFF1B2430)
                    )
                    Text(
                        SimpleDateFormat("MMM", Locale.getDefault()).format(event.start),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color(0xFF1B2430)
                    )
                }
                Spacer(Modifier.width(16.dp))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Event, contentDescription = null, tint = highlightTextColor(), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.home_next_event),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    event?.title ?: stringResource(R.string.home_no_event),
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 19.sp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (event != null) {
                    val time = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT, Locale.getDefault()).format(event.start)
                    Text(
                        time + (if (event.location.isNotBlank()) "  \u00B7  ${event.location}" else ""),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (event != null) {
                // One tap: put it in the phone's calendar.
                IconButton(onClick = { addToCalendar(context, event) }) {
                    Icon(Icons.Outlined.CalendarMonth, contentDescription = stringResource(R.string.event_add_calendar), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}


/** Today's reading from the plan, with a button to tick it off. Tapping the card opens the plan. */
@Composable
private fun ReadingCard(plan: ReadingPlan, index: Int, done: Set<Int>, minHeight: Dp, onToggle: () -> Unit, onClick: () -> Unit) {
    val isDone = index in done
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = lerp(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.primaryContainer, 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp, pressedElevation = 1.dp),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(min = minHeight).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(stringResource(R.string.reading_today).uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            if (index < 0) {
                Text(
                    stringResource(R.string.reading_starts, plan.startDate.toLocalDate().format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault()))),
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 19.sp)
                )
                Text(plan.title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(plan.days[index], style = MaterialTheme.typography.titleMedium.copy(fontSize = 19.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    plan.title + "  \u00B7  " + stringResource(R.string.plans_day, index + 1),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val streak = streakOf(done, index)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Text(
                        stringResource(R.string.reading_progress, done.size, plan.days.size) +
                            if (streak > 1) "  \u00B7  " + stringResource(R.string.reading_streak, streak) else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    if (isDone) {
                        OutlinedButton(onClick = onToggle) {
                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.reading_done))
                        }
                    } else {
                        Button(onClick = onToggle) { Text(stringResource(R.string.reading_mark)) }
                    }
                }
            }
        }
    }
}


private class ComingItem(val isEvent: Boolean, val id: String, val title: String, val time: Long)

/** One planned announcement or event, with its icon, the date and the title. */
@Composable
private fun ComingUpCard(item: ComingItem, onClick: () -> Unit) {
    val date = SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(item.time))
    val label = if (item.isEvent) {
        date + "  \u00B7  " + java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT, Locale.getDefault()).format(Date(item.time))
    } else date
    WordCard(onClick = onClick) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        if (item.isEvent) highlightColor().copy(alpha = 0.25f) else MaterialTheme.colorScheme.primaryContainer,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (item.isEvent) Icons.Outlined.Event else Icons.Outlined.Campaign,
                    contentDescription = null,
                    tint = if (item.isEvent) highlightTextColor() else MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

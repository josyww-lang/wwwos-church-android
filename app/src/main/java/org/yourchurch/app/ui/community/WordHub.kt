package org.yourchurch.app.ui.community

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.yourchurch.app.LocaleManager
import org.yourchurch.app.PendingReading
import org.yourchurch.app.R
import org.yourchurch.app.data.ContributorViewModel
import org.yourchurch.app.data.NoteSeed
import org.yourchurch.app.data.PlansViewModel
import org.yourchurch.app.data.WordViewModel
import org.yourchurch.app.ui.theme.ToneKind

/**
 * The Study tab (it was the Word tab). Bible: the Bible reader, open at once. Library: folders with lessons, PDFs, audio
 * and voice notes. Plans: the reading plan everyone follows. Notes: the person's own private notes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordHub(
    uid: String,
    myName: String,
    isAdmin: Boolean,
    accountMenu: @Composable () -> Unit,
    section: Int,
    onSectionChange: (Int) -> Unit,
    onFullScreen: (Boolean) -> Unit = {},
    vm: WordViewModel = viewModel(key = "word-$uid"),
    contributorVm: ContributorViewModel = viewModel(key = "contributor-$uid")
) {
    // Leaders, and the members the leaders allowed, can add Study content and reading plans.
    LaunchedEffect(uid) { contributorVm.start(uid) }
    val isContributor by contributorVm.can.collectAsStateWithLifecycle()
    val canAdd = isAdmin || isContributor
    val context = LocalContext.current
    val language = LocaleManager.languageCode(context)
    val plansVm: PlansViewModel = viewModel(key = "plans-$uid")
    var readerTarget by remember { mutableStateOf<String?>(null) }
    var showLinks by remember { mutableStateOf(false) }      // leaders: the links to other Bible apps
    var notesFull by remember { mutableStateOf(false) }      // the big sticky note in Notes fills the whole screen
    var readerLang by remember { mutableStateOf<String?>(null) }
    // Opened from a reading plan: which day (counted from 0), so the reader can offer "Mark as read" at the end of a chapter.
    var planDay by remember { mutableStateOf<Int?>(null) }
    val plansState by plansVm.state.collectAsStateWithLifecycle()
    // A verse was tapped (the verse of the day, its notification, or the page Highlights): open the Bible there.
    val pending = PendingReading.request
    LaunchedEffect(pending) {
        if (pending != null) {
            readerTarget = pending.ref.takeIf { it.isNotBlank() }
            readerLang = pending.lang
            planDay = null
            onSectionChange(0)
            PendingReading.request = null
        }
    }
    var noteSeed by remember { mutableStateOf<NoteSeed?>(null) }
    // Full-screen reading (the button at the right of the reader's top bar): the top card, the tabs and the bottom bar are hidden,
    // so the text has the whole screen. It starts normal each time; the Back button leaves it.
    var focus by rememberSaveable { mutableStateOf(false) }
    val focusActive = focus && section == 0
    val chromeHidden = focusActive || (notesFull && section == 3)
    LaunchedEffect(chromeHidden) { onFullScreen(chromeHidden) }
    DisposableEffect(Unit) { onDispose { onFullScreen(false) } }
    Column(Modifier.fillMaxSize()) {
        if (!chromeHidden) {
        HubHeader(accountMenu)
        SectionTabs(
            labels = listOf(
                stringResource(R.string.word_read),
                stringResource(R.string.word_study),
                stringResource(R.string.word_plans),
                stringResource(R.string.word_notes)
            ),
            selected = section,
            kinds = listOf(ToneKind.Blue, ToneKind.Coral, ToneKind.Teal, ToneKind.Gold),
            large = true,
            onSelect = onSectionChange
        )
        }
        when (section) {
            // The Bible tab opens the Bible at once (no page in between).
            0 -> BibleReaderScreen(
                language = language,
                target = readerTarget,
                targetLang = readerLang,
                planDay = planDay,
                planDone = planDay?.let { it in plansState.done } == true,
                onMarkRead = { planDay?.let { plansVm.markRead(it) } },
                onTargetUsed = {
                    readerTarget = null
                    readerLang = null
                },
                onNote = { reference, quote ->
                    noteSeed = NoteSeed(reference = reference, body = quote + "\n\n")
                    onSectionChange(3)
                },
                onOpenLinks = if (isAdmin) ({ showLinks = true }) else null,
                focusMode = focusActive,
                onToggleFocus = { focus = !focus }
            )
            1 -> LibraryPane(uid = uid, isAdmin = isAdmin, canAdd = canAdd, vm = vm)
            2 -> PlansPane(
                uid = uid,
                myName = myName,
                isAdmin = isAdmin,
                canAdd = canAdd,
                onRead = { reading, day ->
                    readerTarget = reading
                    readerLang = null
                    planDay = day
                    onSectionChange(0)
                },
                onReflect = { reading, day ->
                    noteSeed = NoteSeed(reference = reading, day = day)
                    onSectionChange(3)
                }
            )
            else -> MyNotesPane(
                uid = uid,
                seed = noteSeed,
                onSeedHandled = { noteSeed = null },
                onSavedFromSeed = { day -> plansVm.markReflected(day) },
                onFullScreen = { notesFull = it },
                onOpenVerse = { ref ->
                    readerTarget = ref
                    readerLang = null
                    planDay = null
                    onSectionChange(0)
                }
            )
        }
    }
    // Leaders: the links to other Bible apps (opened from the button in the books panel of the Bible).
    if (showLinks) {
        ModalBottomSheet(
            onDismissRequest = { showLinks = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Box(Modifier.fillMaxWidth().fillMaxHeight(0.85f)) { ReadPane(uid = uid, isAdmin = isAdmin, vm = vm) }
        }
    }
}

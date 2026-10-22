package org.yourchurch.app.ui.community

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
 * The Study tab (it was the Word tab). Read: open any Bible in another app. Library: folders with lessons, PDFs, audio
 * and voice notes. Plans: the reading plan everyone follows. Notes: the person's own private notes.
 */
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
    var readerOpen by rememberSaveable { mutableStateOf(false) }
    var readerTarget by remember { mutableStateOf<String?>(null) }
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
            readerOpen = true
            onSectionChange(0)
            PendingReading.request = null
        }
    }
    var noteSeed by remember { mutableStateOf<NoteSeed?>(null) }
    // Full-screen reading: the top card, the tabs and the bottom bar are hidden, so the text has the whole screen.
    val readerPrefs = remember { context.getSharedPreferences("reader", Context.MODE_PRIVATE) }
    var focus by rememberSaveable { mutableStateOf(readerPrefs.getBoolean("focus", true)) }
    val focusActive = focus && readerOpen && section == 0
    LaunchedEffect(focusActive) { onFullScreen(focusActive) }
    DisposableEffect(Unit) { onDispose { onFullScreen(false) } }
    Column(Modifier.fillMaxSize()) {
        if (!focusActive) {
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
            0 -> if (readerOpen) {
                BibleReaderScreen(
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
                    onClose = {
                        readerOpen = false
                        planDay = null
                    },
                    onNote = { reference, quote ->
                        noteSeed = NoteSeed(reference = reference, body = quote + "\n\n")
                        readerOpen = false
                        onSectionChange(3)
                    },
                    focusMode = focusActive,
                    onToggleFocus = {
                        focus = !focus
                        readerPrefs.edit().putBoolean("focus", focus).apply()
                    }
                )
            } else {
                ReadPane(uid = uid, isAdmin = isAdmin, vm = vm, onOpenReader = {
                    planDay = null
                    readerOpen = true
                })
            }
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
                    readerOpen = true
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
                onOpenVerse = { ref ->
                    readerTarget = ref
                    readerLang = null
                    planDay = null
                    readerOpen = true
                    onSectionChange(0)
                }
            )
        }
    }
}

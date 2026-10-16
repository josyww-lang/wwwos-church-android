package org.yourchurch.app.ui.community

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.yourchurch.app.R
import org.yourchurch.app.data.ContributorViewModel
import org.yourchurch.app.data.WordViewModel
import org.yourchurch.app.ui.theme.ToneKind

/**
 * The Word tab. Read: open any Bible in another app. Study: folders with lessons, PDFs, audio and voice notes.
 * Plans: the reading plan everyone follows.
 */
@Composable
fun WordHub(
    uid: String,
    myName: String,
    isAdmin: Boolean,
    accountMenu: @Composable () -> Unit,
    section: Int,
    onSectionChange: (Int) -> Unit,
    vm: WordViewModel = viewModel(key = "word-$uid"),
    contributorVm: ContributorViewModel = viewModel(key = "contributor-$uid")
) {
    // Leaders, and the members the leaders allowed, can add Study content and reading plans.
    LaunchedEffect(uid) { contributorVm.start(uid) }
    val isContributor by contributorVm.can.collectAsStateWithLifecycle()
    val canAdd = isAdmin || isContributor
    Column(Modifier.fillMaxSize()) {
        HubHeader(accountMenu)
        SectionTabs(
            labels = listOf(
                stringResource(R.string.word_read),
                stringResource(R.string.word_study),
                stringResource(R.string.word_plans)
            ),
            selected = section,
            kinds = listOf(ToneKind.Blue, ToneKind.Coral, ToneKind.Teal),
            large = true,
            onSelect = onSectionChange
        )
        when (section) {
            0 -> ReadPane(uid = uid, isAdmin = isAdmin, vm = vm)
            1 -> LibraryPane(uid = uid, isAdmin = isAdmin, canAdd = canAdd, vm = vm)
            else -> PlansPane(uid = uid, myName = myName, isAdmin = isAdmin, canAdd = canAdd)
        }
    }
}

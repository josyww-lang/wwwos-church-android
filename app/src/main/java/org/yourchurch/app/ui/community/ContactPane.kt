@file:OptIn(ExperimentalMaterial3Api::class)

package org.yourchurch.app.ui.community

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.yourchurch.app.R
import org.yourchurch.app.data.ChurchInfo
import org.yourchurch.app.data.ChurchInfoViewModel

/** Address, phone, email, website and service times. Tap a line to open the map, dialer, mail or web. */
@Composable
fun ContactPane(
    uid: String,
    isAdmin: Boolean,
    onFullScreen: (Boolean) -> Unit,
    vm: ChurchInfoViewModel = viewModel(key = "church-$uid")
) {
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }

    LaunchedEffect(editing) { onFullScreen(editing) }
    DisposableEffect(Unit) { onDispose { onFullScreen(false) } }
    BackHandler(enabled = editing) { editing = false }

    if (editing) {
        ContactForm(initial = state.info, onSave = { vm.save(it) }, onClose = { editing = false })
        return
    }

    val info = state.info
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.section_contact),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f)
            )
            if (isAdmin) {
                IconButton(onClick = { editing = true }) {
                    Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.contact_edit))
                }
            }
        }

        when {
            state.loading -> CircularProgressIndicator(Modifier.padding(top = 16.dp))
            !info.hasAnyContact -> Text(
                stringResource(if (isAdmin) R.string.contact_empty_admin else R.string.contact_empty_member),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            else -> {
                // Service times: a section of their own, first.
                if (info.serviceTimes.isNotBlank()) {
                    SectionLabel(R.string.home_service_times)
                    ServiceTimesCard(info.serviceTimes)
                }
                SectionLabel(R.string.contact_section_contact)
                // The three things people usually want first.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { openDialer(context, info.phone) }, enabled = info.phone.isNotBlank(), modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 6.dp)) {
                        Icon(Icons.Outlined.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.home_call), maxLines = 1)
                    }
                    OutlinedButton(onClick = { openDirections(context, info.address) }, enabled = info.address.isNotBlank(), modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 6.dp)) {
                        Icon(Icons.Outlined.Directions, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.home_directions), maxLines = 1)
                    }
                    OutlinedButton(onClick = { openMail(context, info.email) }, enabled = info.email.isNotBlank(), modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 6.dp)) {
                        Icon(Icons.Outlined.Email, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.home_email), maxLines = 1)
                    }
                }
                if (info.address.isNotBlank()) ContactLine(Icons.Outlined.Place, R.string.contact_address, info.address) { openDirections(context, info.address) }
                if (info.phone.isNotBlank()) ContactLine(Icons.Outlined.Call, R.string.contact_phone, info.phone) { openDialer(context, info.phone) }
                if (info.email.isNotBlank()) ContactLine(Icons.Outlined.Email, R.string.contact_email, info.email) { openMail(context, info.email) }
                if (info.website.isNotBlank() || info.facebook.isNotBlank() || info.youtube.isNotBlank()) SectionLabel(R.string.contact_section_online)
                if (info.website.isNotBlank()) ContactLine(Icons.Outlined.Language, R.string.contact_website, info.website) { openWebsite(context, info.website) }
                if (info.facebook.isNotBlank()) ContactLine(Icons.Outlined.Link, R.string.contact_facebook, info.facebook) { openWebsite(context, info.facebook) }
                if (info.youtube.isNotBlank()) ContactLine(Icons.Outlined.Link, R.string.contact_youtube, info.youtube) { openWebsite(context, info.youtube) }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SectionLabel(label: Int) {
    Text(
        stringResource(label),
        style = MaterialTheme.typography.titleMedium.copy(fontSize = 19.sp),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 6.dp)
    )
}

/** The service times, in a card of their own. */
@Composable
private fun ServiceTimesCard(text: String) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp, pressedElevation = 1.dp),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(14.dp))
            Text(text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun ContactLine(icon: ImageVector, label: Int, value: String, onClick: (() -> Unit)?) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp, pressedElevation = 1.dp),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(14.dp))
            Column {
                Text(stringResource(label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun ContactForm(initial: ChurchInfo, onSave: suspend (ChurchInfo) -> Result<Unit>, onClose: () -> Unit) {
    var address by rememberSaveable { mutableStateOf(initial.address) }
    var phone by rememberSaveable { mutableStateOf(initial.phone) }
    var email by rememberSaveable { mutableStateOf(initial.email) }
    var website by rememberSaveable { mutableStateOf(initial.website) }
    var facebook by rememberSaveable { mutableStateOf(initial.facebook) }
    var youtube by rememberSaveable { mutableStateOf(initial.youtube) }
    var services by rememberSaveable { mutableStateOf(initial.serviceTimes) }
    var welcomeEn by rememberSaveable { mutableStateOf(initial.welcomeEn) }
    var welcomeNb by rememberSaveable { mutableStateOf(initial.welcomeNb) }
    var welcomeOm by rememberSaveable { mutableStateOf(initial.welcomeOm) }
    var appLink by rememberSaveable { mutableStateOf(initial.appLink) }
    var saving by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    @Composable
    fun field(value: String, label: Int, max: Int, lines: Int = 1, onChange: (String) -> Unit) {
        OutlinedTextField(
            value = value,
            onValueChange = { onChange(it.take(max)) },
            label = { Text(stringResource(label)) },
            singleLine = lines == 1,
            minLines = lines,
            enabled = !saving,
            modifier = Modifier.fillMaxWidth()
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.contact_edit), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onClose, enabled = !saving) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.close))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            field(address, R.string.contact_address, 200, 2) { address = it }
            field(phone, R.string.contact_phone, 30) { phone = it }
            field(email, R.string.contact_email, 120) { email = it }
            field(website, R.string.contact_website, 200) { website = it }
            field(facebook, R.string.contact_facebook, 200) { facebook = it }
            field(youtube, R.string.contact_youtube, 200) { youtube = it }
            field(services, R.string.contact_f_services, 400, 3) { services = it }
            field(welcomeEn, R.string.contact_f_welcome_en, 600, 3) { welcomeEn = it }
            field(welcomeNb, R.string.contact_f_welcome_nb, 600, 3) { welcomeNb = it }
            field(welcomeOm, R.string.contact_f_welcome_om, 600, 3) { welcomeOm = it }
            field(appLink, R.string.contact_f_applink, 300) { appLink = it }

            if (failed) Text(stringResource(R.string.err_save_failed), color = MaterialTheme.colorScheme.error)

            Button(
                onClick = {
                    failed = false
                    saving = true
                    scope.launch {
                        val result = onSave(
                            initial.copy(
                                address = address.trim(), phone = phone.trim(), email = email.trim(),
                                website = website.trim(), facebook = facebook.trim(), youtube = youtube.trim(),
                                serviceTimes = services.trim(),
                                welcomeEn = welcomeEn.trim(), welcomeNb = welcomeNb.trim(), welcomeOm = welcomeOm.trim(),
                                // A link must start with https:// so that people can open it.
                                appLink = appLink.trim().let { if (it.isNotEmpty() && !it.startsWith("http", ignoreCase = true)) "https://$it" else it }
                            )
                        )
                        saving = false
                        result.onSuccess { onClose() }.onFailure { failed = true }
                    }
                },
                enabled = !saving,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                if (saving) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text(stringResource(R.string.save), fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

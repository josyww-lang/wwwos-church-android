package org.yourchurch.app.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.yourchurch.app.R
import org.yourchurch.app.data.AuthViewModel
import org.yourchurch.app.ui.theme.heroBrush

/** Shown after sign-up (and to older accounts) until the email address is verified. */
@Composable
fun VerifyEmailScreen(vm: AuthViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val email = state.user?.email ?: ""

    // When the person comes back from their email app, check automatically.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.checkVerified(manual = false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(heroBrush(), RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier.size(60.dp).background(Color.White.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Email, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
            }
            Text(stringResource(R.string.verify_title), style = MaterialTheme.typography.headlineMedium, color = Color.White)
        }

        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                stringResource(
                    if (state.verificationSent) R.string.verify_text_sent else R.string.verify_text_needed,
                    email
                ),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                stringResource(R.string.verify_spam_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (state.verifyNotYet) {
                Text(stringResource(R.string.verify_not_yet), color = MaterialTheme.colorScheme.error)
            }
            state.verifyError?.let {
                Text(stringResource(it.messageRes), color = MaterialTheme.colorScheme.error)
            }

            Button(
                onClick = { vm.checkVerified(manual = true) },
                enabled = !state.verifyWorking,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) {
                if (state.verifyWorking) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.verify_check_button), fontWeight = FontWeight.Medium)
                }
            }

            OutlinedButton(
                onClick = { vm.sendVerification() },
                enabled = !state.verifyWorking,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) {
                Text(stringResource(if (state.verificationSent) R.string.verify_resend else R.string.verify_send))
            }

            TextButton(onClick = { vm.signOut() }) { Text(stringResource(R.string.sign_out)) }
        }
    }
}

package org.yourchurch.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import com.google.firebase.auth.FirebaseAuth
import org.yourchurch.app.PendingTarget
import org.yourchurch.app.ui.AppRoot
import org.yourchurch.app.ui.theme.ChurchTheme
import org.yourchurch.app.ui.theme.ThemeSettings
import org.yourchurch.app.ui.theme.isAppDark

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleManager.wrap(newBase))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openRoomFrom(intent)
    }

    override fun onStart() {
        super.onStart()
        AppState.foreground = true
    }

    override fun onStop() {
        AppState.foreground = false
        super.onStop()
    }

    /** A chat notification carries the id of the conversation to open. */
    private fun openRoomFrom(intent: Intent?) {
        intent?.getStringExtra("roomId")?.takeIf { it.isNotBlank() }?.let { PendingRoom.roomId = it }
        intent?.getStringExtra("target")?.takeIf { it.isNotBlank() }?.let { PendingTarget.target = it }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The logo only belongs to the start screen: from here on the window is the plain soft background.
        window.setBackgroundDrawableResource(R.color.app_window_background)
        SafetySetup.init(applicationContext)
        SermonQueue.load(applicationContext)
        openRoomFrom(intent)
        // Firebase sends its emails (verification, password reset) in this language when it supports it.
        FirebaseAuth.getInstance().setLanguageCode(LocaleManager.languageCode(this))
        ThemeSettings.load(this)
        enableEdgeToEdge()
        setContent {
            ChurchTheme {
                // Keep the clock and battery icons readable when the app's theme differs from the phone's.
                val dark = isAppDark()
                DisposableEffect(dark) {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.auto(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        ) { dark },
                        navigationBarStyle = SystemBarStyle.auto(
                            android.graphics.Color.argb(0xe6, 0xFF, 0xFF, 0xFF),
                            android.graphics.Color.argb(0x80, 0x1b, 0x1b, 0x1b)
                        ) { dark }
                    )
                    onDispose { }
                }
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppRoot()
                }
            }
        }
    }
}

package org.yourchurch.app

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.yourchurch.app.data.PushTokens

/**
 * Receives messages while the app is open. (When the app is closed, Android shows them by itself.)
 */
class ChurchMessagingService : FirebaseMessagingService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Firebase gave this phone a new address: tell the notification server. */
    override fun onNewToken(token: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (!Notifications.isEnabled(this)) return
        scope.launch { runCatching { PushTokens.register(uid) } }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val roomId = message.data["roomId"]
        // Don't notify about a conversation that is open on screen right now.
        if (AppState.foreground && roomId != null && roomId == ChatUiState.openRoomId) return
        val notification = message.notification ?: return
        Notifications.show(this, notification.title, notification.body, roomId, message.data["target"])
    }
}

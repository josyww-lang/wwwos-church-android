package org.yourchurch.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Set when someone taps a chat notification, so the app can open that conversation. */
object PendingRoom {
    var roomId: String? by mutableStateOf(null)
}

/** Set when someone taps a notification about a section ("give", "sermons", "news", "events"), so the app can open it. */
object PendingTarget {
    var target: String? by mutableStateOf(null)
}

/** Whether the app is on screen right now. */
object AppState {
    @Volatile
    var foreground: Boolean = false
}

/** The conversation open on screen right now, so we don't notify you about a chat you are looking at. */
object ChatUiState {
    @Volatile
    var openRoomId: String? = null
}

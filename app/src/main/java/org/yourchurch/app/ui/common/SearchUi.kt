package org.yourchurch.app.ui.common

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Whether the search page is open. The magnifying glass at the top of every page opens it. */
object SearchUi {
    var visible by mutableStateOf(false)
}

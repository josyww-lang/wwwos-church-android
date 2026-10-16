package org.yourchurch.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.yourchurch.app.ui.theme.groupColor

/** A round badge with the group's first letter, in the group's colour. One place for every list and header. */
@Composable
fun GroupAvatar(name: String, colorIndex: Int, size: Dp = 40.dp) {
    val tinted = colorIndex != 0
    Box(
        modifier = Modifier
            .size(size)
            .background(if (tinted) groupColor(colorIndex) else MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            name.trim().take(1).uppercase().ifEmpty { "?" },
            style = if (size >= 44.dp) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
            color = if (tinted) Color.White else MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

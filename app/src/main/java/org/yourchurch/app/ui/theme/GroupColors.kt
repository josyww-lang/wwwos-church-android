package org.yourchurch.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Colours a group can wear. Number 0 follows the app's own colour; the others are fixed.
 * The number is saved with the group, so everyone in it sees the same colour.
 */
val GROUP_COLORS: List<Color?> = listOf(
    null,
    Color(0xFF2563EB), // blue
    Color(0xFF15803D), // green
    Color(0xFF0F766E), // teal
    Color(0xFF6D28D9), // purple
    Color(0xFFC2185B), // pink
    Color(0xFFC62828), // red
    Color(0xFFD9480F), // orange
    Color(0xFFB7791F), // gold
    Color(0xFF475569), // slate
    Color(0xFF7C4A2D)  // brown
)

@Composable
fun groupColor(index: Int): Color = GROUP_COLORS.getOrNull(index) ?: MaterialTheme.colorScheme.primary

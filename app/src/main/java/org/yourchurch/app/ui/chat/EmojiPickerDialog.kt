package org.yourchurch.app.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.yourchurch.app.R

private val MORE_EMOJI = listOf(
    "🙏", "❤️", "👍", "🙌", "😊", "😢", "😂", "😮", "😍", "🥰", "😇", "🤗",
    "👏", "💪", "🔥", "✨", "🎉", "🕊️", "✝️", "📖", "🙇", "🤝", "💙", "💛",
    "💜", "🧡", "🌟", "☀️", "🌈", "🎶", "🍞", "🕯️", "😔", "😭", "🤔", "😅",
    "👌", "✅", "💯", "😌", "🤲", "🙋", "👋", "😴"
)

/** A grid of emoji to react with. */
@Composable
fun EmojiPickerDialog(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chat_emoji_more)) },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                modifier = Modifier.heightIn(max = 320.dp)
            ) {
                items(MORE_EMOJI) { emoji ->
                    Text(
                        emoji,
                        fontSize = 26.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { onPick(emoji) }
                            .padding(6.dp)
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

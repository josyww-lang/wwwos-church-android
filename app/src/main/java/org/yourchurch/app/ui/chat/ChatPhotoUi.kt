package org.yourchurch.app.ui.chat

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.yourchurch.app.R
import org.yourchurch.app.data.ChatImages
import org.yourchurch.app.data.ChatMessage

/** The small preview of a photo inside a message. Tap to open the full picture. */
@Composable
fun PhotoThumb(message: ChatMessage, onClick: (() -> Unit)?) {
    val bitmap = remember(message.id, message.thumb) {
        message.thumb?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
    }
    val shape = RoundedCornerShape(12.dp)
    val clickable = if (onClick != null) Modifier.clickable { onClick() } else Modifier
    if (bitmap != null) {
        val ratio = (bitmap.width.toFloat() / bitmap.height).coerceIn(0.6f, 1.8f)
        Image(
            bitmap = bitmap,
            contentDescription = stringResource(R.string.chat_photo),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .padding(vertical = 4.dp)
                .widthIn(max = 240.dp)
                .fillMaxWidth()
                .aspectRatio(ratio)
                .clip(shape)
                .then(clickable)
        )
    } else {
        Box(
            modifier = Modifier
                .padding(vertical = 4.dp)
                .widthIn(max = 240.dp)
                .fillMaxWidth()
                .aspectRatio(1.2f)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .then(clickable),
            contentAlignment = Alignment.Center
        ) { Text("\uD83D\uDCF7", style = MaterialTheme.typography.headlineMedium) }
    }
}

/** Shows the picked picture, with a box for a caption, before it is sent. */
@Composable
fun PhotoPreviewDialog(uri: Uri, onSend: (String) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    var caption by rememberSaveable { mutableStateOf("") }
    val bitmap by produceState<Bitmap?>(null, uri) { value = ChatImages.preview(context, uri) }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.chat_photo_send_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 320.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val shown = bitmap
                    if (shown == null) {
                        CircularProgressIndicator()
                    } else {
                        Image(
                            bitmap = shown.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))
                        )
                    }
                }
                OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it.take(500) },
                    placeholder = { Text(stringResource(R.string.chat_photo_caption)) },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSend(caption) }, enabled = bitmap != null) {
                Text(stringResource(R.string.chat_send))
            }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) } }
    )
}

/** The full picture, filling the screen. Pinch to zoom. */
@Composable
fun PhotoViewerDialog(message: ChatMessage, onDismiss: () -> Unit) {
    val thumb = remember(message.id) {
        message.thumb?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
    }
    var full by remember { mutableStateOf<Bitmap?>(null) }
    var finished by remember { mutableStateOf(false) }
    LaunchedEffect(message.imagePath) {
        full = ChatImages.loadFull(message.imagePath)
        finished = true
    }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            val shown = full ?: thumb
            if (shown != null) {
                Image(
                    bitmap = shown.asImageBitmap(),
                    contentDescription = stringResource(R.string.chat_photo),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 5f)
                                offset = if (scale == 1f) Offset.Zero else offset + pan
                            }
                        }
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        }
                )
            }
            if (!finished) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White
                )
            } else if (full == null) {
                Text(
                    stringResource(R.string.chat_photo_failed),
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp)
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp)
            ) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close), tint = Color.White)
            }
            if (message.text.isNotBlank()) {
                Text(
                    message.text,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .fillMaxWidth()
                        .padding(16.dp)
                )
            }
        }
    }
}

package org.yourchurch.app.ui.common

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import org.yourchurch.app.R
import org.yourchurch.app.data.AvatarStore

/** A person's profile picture, or their first initial in a coloured circle if they have none. */
@Composable
fun PersonAvatar(uid: String, name: String, size: Dp, modifier: Modifier = Modifier, viewable: Boolean = false) {
    var showViewer by remember { mutableStateOf(false) }
    LaunchedEffect(uid) { AvatarStore.ensure(uid) }
    val bitmap = AvatarStore.bitmaps[uid]
    if (bitmap != null) {
        val image = remember(bitmap) { bitmap.asImageBitmap() }
        Image(
            bitmap = image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .then(if (viewable) Modifier.clickable { showViewer = true } else Modifier)
        )
        if (showViewer) AvatarViewerDialog(image = image, name = name, onDismiss = { showViewer = false })
    } else {
        Box(
            modifier = modifier.size(size).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                name.trim().take(1).uppercase().ifEmpty { "?" },
                style = if (size >= 56.dp) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

/** The icon in the top corner that opens the account menu: my picture if I have one. */
@Composable
fun AccountIcon(uid: String, description: String, size: Dp = 40.dp) {
    LaunchedEffect(uid) { AvatarStore.ensure(uid) }
    val bitmap = AvatarStore.bitmaps[uid]
    if (bitmap != null) {
        val image = remember(bitmap) { bitmap.asImageBitmap() }
        Image(
            bitmap = image,
            contentDescription = description,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).clip(CircleShape)
        )
    } else {
        Icon(
            Icons.Outlined.AccountCircle,
            contentDescription = description,
            modifier = Modifier.size(size),
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

/** My picture, with buttons to add, change or remove it. Used inside "My profile". */
@Composable
fun ProfilePhotoSection(uid: String, name: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var working by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var cropSource by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(uid) { AvatarStore.ensure(uid) }
    val hasPhoto = AvatarStore.bitmaps.containsKey(uid)

    // The system photo picker: no permission needed.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            working = true
            failed = false
            scope.launch {
                // Load the photo, then let the person move and zoom it before it is saved.
                val bitmap = AvatarStore.loadForCrop(context, uri)
                working = false
                if (bitmap == null) failed = true else cropSource = bitmap
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            PersonAvatar(uid = uid, name = name, size = 72.dp, viewable = true)
            Column {
                OutlinedButton(
                    onClick = {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    enabled = !working
                ) {
                    Text(stringResource(if (hasPhoto) R.string.photo_change else R.string.photo_add))
                }
                if (hasPhoto) {
                    TextButton(
                        onClick = {
                            working = true
                            failed = false
                            scope.launch {
                                val ok = AvatarStore.delete(uid)
                                working = false
                                failed = !ok
                            }
                        },
                        enabled = !working
                    ) {
                        Text(stringResource(R.string.photo_remove), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            if (working) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        }
        if (failed) {
            Text(
                stringResource(R.string.photo_failed),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Text(
            stringResource(R.string.photo_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    cropSource?.let { source ->
        PhotoCropDialog(
            source = source,
            onCancel = { cropSource = null },
            onDone = { square ->
                cropSource = null
                working = true
                failed = false
                scope.launch {
                    val ok = AvatarStore.uploadFromBitmap(uid, square)
                    working = false
                    failed = !ok
                }
            }
        )
    }
}

/** A profile picture, as big as the screen allows. Tap anywhere to close it. */
@Composable
private fun AvatarViewerDialog(image: ImageBitmap, name: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black).clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = image,
                contentDescription = name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth().align(Alignment.TopStart).statusBarsPadding().padding(start = 20.dp, top = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(name, color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, contentDescription = null, tint = Color.White)
                }
            }
        }
    }
}

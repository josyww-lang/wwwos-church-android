package org.yourchurch.app.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.yourchurch.app.data.BrandingStore

/**
 * The church's logo. Until there is one, a dashed circle marked "logo" holds its place.
 * To add it: put the picture in app/src/main/res/drawable and name it church_logo.png (or .webp). Nothing else to change.
 */
@Composable
fun ChurchLogo(size: Dp = 44.dp) {
    val uploaded = BrandingStore.logo
    if (uploaded != null) {
        // The logo a leader uploaded in the app.
        Image(bitmap = uploaded, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(size))
        return
    }
    val context = LocalContext.current
    val resId = remember { context.resources.getIdentifier("church_logo", "drawable", context.packageName) }
    if (resId != 0) {
        Image(painter = painterResource(resId), contentDescription = null, modifier = Modifier.size(size))
    } else {
        val color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
        Box(
            modifier = Modifier
                .size(size)
                .drawBehind {
                    drawCircle(
                        color = color,
                        style = Stroke(
                            width = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 5.dp.toPx()))
                        )
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Text("logo", style = MaterialTheme.typography.labelMedium, color = color)
        }
    }
}

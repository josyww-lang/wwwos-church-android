package org.yourchurch.app.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.yourchurch.app.R
import org.yourchurch.app.data.BrandingStore

/**
 * The church's name: large, in capital letters, centred, and in the language of the app
 * (it changes with the language picker). Used at the top of the screens so the name looks the same everywhere.
 */
@Composable
fun ChurchName(
    modifier: Modifier = Modifier.fillMaxWidth(),
    color: Color = MaterialTheme.colorScheme.primary,
    style: TextStyle = MaterialTheme.typography.titleMedium,
    maxLines: Int = 2,
    textAlign: TextAlign = TextAlign.Center,
    startSize: Float = 24f
) {
    val name = stringResource(R.string.church_name).uppercase()
    val measurer = rememberTextMeasurer()
    // Always two centred rows, in every language. The biggest letters that fit the room are worked out before the
    // name is drawn (not by shrinking it step by step), so the name looks the same each time a page opens.
    BoxWithConstraints(modifier) {
        val bounded = constraints.hasBoundedWidth
        val widthPx = constraints.maxWidth
        val size = remember(name, widthPx, bounded, startSize, style, maxLines) {
            if (bounded) fittingSize(measurer, name, style, widthPx, startSize, maxLines) else startSize
        }
        Text(
            text = name,
            modifier = Modifier.fillMaxWidth(),
            style = style.copy(fontSize = size.sp, lineHeight = (size * 1.28f).sp),
            color = color,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.6.sp,
            textAlign = textAlign,
            maxLines = maxLines,
            softWrap = true,
            overflow = TextOverflow.Clip
        )
    }
}

/** The biggest font size (down to 10) at which [text] fits in [widthPx] on at most [maxLines] lines. */
private fun fittingSize(measurer: TextMeasurer, text: String, style: TextStyle, widthPx: Int, start: Float, maxLines: Int): Float {
    var size = start
    while (size > 10f) {
        val result = measurer.measure(
            text = AnnotatedString(text),
            style = style.copy(
                fontSize = size.sp,
                lineHeight = (size * 1.28f).sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.6.sp
            ),
            constraints = Constraints(maxWidth = widthPx),
            maxLines = maxLines,
            softWrap = true
        )
        if (!result.hasVisualOverflow) return size
        size -= 0.5f
    }
    return size
}

/**
 * The church's emblem on the sign-in screen: the logo a leader uploaded (tap the logo at the top of any page), or a
 * simple cross until there is one.
 */
@Composable
fun ChurchEmblem(size: Dp = 64.dp, tint: Color = Color.White) {
    val uploaded = BrandingStore.logo
    if (uploaded != null) {
        Box(Modifier.size(size).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
            Image(bitmap = uploaded, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(size * 0.8f))
        }
        return
    }
    // The church's logo, built into the app.
    val context = LocalContext.current
    val logoRes = remember { context.resources.getIdentifier("church_logo", "drawable", context.packageName) }
    if (logoRes != 0) {
        Box(Modifier.size(size).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
            Image(painter = painterResource(logoRes), contentDescription = null, modifier = Modifier.size(size * 0.92f))
        }
        return
    }
    Box(
        modifier = Modifier.size(size).background(tint.copy(alpha = 0.2f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.size(width = size * 0.1f, height = size * 0.5f).background(tint, RoundedCornerShape(2.dp)))
        Box(
            Modifier
                .size(width = size * 0.33f, height = size * 0.1f)
                .offset(y = -(size * 0.08f))
                .background(tint, RoundedCornerShape(2.dp))
        )
    }
}

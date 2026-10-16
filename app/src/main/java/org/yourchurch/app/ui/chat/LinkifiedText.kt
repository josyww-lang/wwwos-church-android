package org.yourchurch.app.ui.chat

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle

private val urlPattern = Regex("https?://[^\\s]+")

/** Message text where web links (http:// or https://) can be tapped to open them. */
@Composable
fun LinkifiedText(text: String, color: Color, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val annotated = remember(text) {
        buildAnnotatedString {
            var last = 0
            for (match in urlPattern.findAll(text)) {
                append(text.substring(last, match.range.first))
                // Leave trailing punctuation outside the link, like the full stop at the end of a sentence.
                val link = match.value.trimEnd('.', ',', ')', '!', '?', ';', ':')
                pushStringAnnotation("URL", link)
                withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) { append(link) }
                pop()
                append(match.value.substring(link.length))
                last = match.range.last + 1
            }
            append(text.substring(last))
        }
    }
    ClickableText(
        text = annotated,
        style = MaterialTheme.typography.bodyLarge.copy(color = color),
        modifier = modifier,
        onClick = { offset ->
            annotated.getStringAnnotations("URL", offset, offset).firstOrNull()?.let { link ->
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link.item))) }
            }
        }
    )
}

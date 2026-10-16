package org.yourchurch.app.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.delay
import org.yourchurch.app.R

/** The size (in dp) of the logo on the system's own start screen, which is on screen from the moment the icon is tapped. */
private const val SYSTEM_SPLASH_LOGO_DP = 179f

/**
 * Shown while the app starts. It continues from the system's start screen (the same logo, the same background, the same
 * size and place), so there is never a blank screen. The logo stays as it is for [holdMs], then pops out to the biggest
 * size with a soft bounce, the church name fades in right under it, and the logo moves gently back and forth until
 * everything has loaded.
 *
 * When the app is ready ([leaving]) the background fades away to show the page, and the logo shrinks and flies to its
 * place in the top card ([flyToHeader]); where there is no top card (the sign-in page, for example) it fades away instead.
 * [onFinished] is called when it has finished.
 */
@Composable
fun LoadingLogo(
    holdMs: Long = 0L,
    leaving: Boolean = false,
    flyToHeader: Boolean = false,
    onFinished: () -> Unit = {}
) {
    val sway = rememberInfiniteTransition(label = "sway")
    val angle by sway.animateFloat(
        initialValue = -3.5f,
        targetValue = 3.5f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "angle"
    )
    val breathe by sway.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.015f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathe"
    )
    val density = LocalDensity.current
    val statusTop = with(density) { WindowInsets.statusBars.getTop(density).toDp() }
    val background = colorResource(R.color.splash_background)
    // 0 = the logo screen as it is, 1 = finished (the logo is in the top card and the page is fully visible).
    val exit = remember { Animatable(0f) }
    LaunchedEffect(leaving) {
        if (leaving) {
            withFrameNanos { }   // the page underneath has been built and drawn once; now the flight starts smoothly
            exit.animateTo(1f, animationSpec = tween(700, easing = FastOutSlowInEasing))
            onFinished()
        }
    }
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().drawBehind { drawRect(background.copy(alpha = 1f - exit.value)) }
    ) {
        // As big as the screen allows (a little room is left for the bounce), with the name under it.
        val size = minOf(maxWidth * 0.9f, maxHeight * 0.62f)
        val logoTop = (maxHeight - size) / 2
        val zoom = remember { Animatable((SYSTEM_SPLASH_LOGO_DP / size.value).coerceIn(0.3f, 0.95f)) }
        val nameAlpha = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            // The logo starts as the phone's own start screen has it (same size and place), then pops out (after holdMs, 0 = at once).
            delay(holdMs)
            zoom.animateTo(1f, animationSpec = spring(dampingRatio = 0.62f, stiffness = 420f))
        }
        LaunchedEffect(Unit) {
            delay(holdMs + 450)
            nameAlpha.animateTo(1f, animationSpec = tween(600))
        }
        // Where the logo flies to: its place in the top card (the middle of the logo, from the middle of the screen).
        val dx = HEADER_LOGO_START + HEADER_LOGO_SIZE / 2 - maxWidth / 2
        val dy = statusTop + HEADER_LOGO_CENTER_BELOW_STATUS_BAR - maxHeight / 2
        val targetScale = HEADER_LOGO_SIZE / size
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = logoTop)
                .graphicsLayer {
                    // The movement starts once the logo has reached its full size.
                    val settled = ((zoom.value - 0.9f) / 0.1f).coerceIn(0f, 1f)
                    val base = zoom.value * (1f + (breathe - 1f) * settled)
                    val e = exit.value
                    if (flyToHeader) {
                        val s = lerp(base, targetScale, e)
                        scaleX = s
                        scaleY = s
                        rotationZ = angle * settled * (1f - e)
                        translationX = dx.toPx() * e
                        translationY = dy.toPx() * e
                    } else {
                        scaleX = base
                        scaleY = base
                        rotationZ = angle * settled
                        alpha = 1f - e
                    }
                }
        ) { ChurchLogo(size = size) }
        // The name sits close under the logo, and fades away first when the logo leaves.
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = logoTop + size + 6.dp)
                .graphicsLayer { alpha = nameAlpha.value * (1f - exit.value * 2.5f).coerceIn(0f, 1f) }
        ) {
            ChurchName(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp),
                color = colorResource(R.color.splash_text),
                startSize = 22f
            )
        }
    }
}

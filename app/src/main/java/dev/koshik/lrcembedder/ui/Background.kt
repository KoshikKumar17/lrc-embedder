package dev.koshik.lrcembedder.ui

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import dev.koshik.lrcembedder.ui.theme.Ink
import dev.koshik.lrcembedder.ui.theme.Palette

/** Soft gradient with a few drifting glow orbs behind the whole app. */
@Composable
fun AnimatedGradientBackground(
    palette: Palette,
    dark: Boolean,
    animated: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val base = if (dark) Ink else Color.White
    val mix = if (dark) 0.22f else 0.28f
    val ca = lerp(base, palette.a, mix)
    val cb = lerp(base, palette.b, mix)
    val cc = lerp(base, palette.c, mix)

    val t = rememberInfiniteTransition(label = "bg")
    val c1 by t.animateColor(
        initialValue = ca, targetValue = cb,
        animationSpec = infiniteRepeatable(tween(7000, easing = LinearEasing), RepeatMode.Reverse),
        label = "c1"
    )
    val c2 by t.animateColor(
        initialValue = cb, targetValue = cc,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Reverse),
        label = "c2"
    )
    val c3 by t.animateColor(
        initialValue = cc, targetValue = ca,
        animationSpec = infiniteRepeatable(tween(11000, easing = LinearEasing), RepeatMode.Reverse),
        label = "c3"
    )
    val shift by t.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(12000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "shift"
    )
    val drift by t.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(16000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "drift"
    )
    val orbAlpha = if (dark) 0.34f else 0.30f

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                // state is only read when animated, so a static background never redraws
                val s = if (animated) shift else 0.5f
                val d = if (animated) drift else 0.5f
                val g1 = if (animated) c1 else ca
                val g2 = if (animated) c2 else cb
                val g3 = if (animated) c3 else cc

                drawRect(
                    Brush.linearGradient(
                        colors = listOf(g1, g2, g3),
                        start = Offset(size.width * s * 0.5f, 0f),
                        end = Offset(size.width * (1f - s * 0.5f), size.height)
                    )
                )
                val r = size.minDimension * 0.75f
                val o1 = Offset(size.width * (0.15f + 0.6f * d), size.height * 0.12f)
                val o2 = Offset(size.width * (0.9f - 0.6f * d), size.height * 0.55f)
                val o3 = Offset(size.width * (0.3f + 0.4f * s), size.height * 0.95f)
                drawCircle(
                    brush = Brush.radialGradient(listOf(palette.a.copy(alpha = orbAlpha), Color.Transparent), center = o1, radius = r),
                    radius = r, center = o1
                )
                drawCircle(
                    brush = Brush.radialGradient(listOf(palette.b.copy(alpha = orbAlpha * 0.8f), Color.Transparent), center = o2, radius = r),
                    radius = r, center = o2
                )
                drawCircle(
                    brush = Brush.radialGradient(listOf(palette.c.copy(alpha = orbAlpha * 0.6f), Color.Transparent), center = o3, radius = r),
                    radius = r, center = o3
                )
            },
        content = content
    )
}

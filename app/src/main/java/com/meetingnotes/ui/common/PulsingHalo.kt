package com.meetingnotes.ui.common

import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 中身([content])のまわりに、静的なリング + ゆっくり1本広がる波紋を描く。
 * 録音ボタン・カウントダウンなど「録音の合図」用。控えめ設定。
 * OS のアニメーションが無効(`ANIMATOR_DURATION_SCALE == 0`)なら静的リングだけにする。
 */
@Composable
fun PulsingHalo(
    color: Color,
    modifier: Modifier = Modifier,
    ringWidth: Dp = 2.dp,
    ringGap: Dp = 4.dp,
    maxScale: Float = 1.3f,
    maxAlpha: Float = 0.22f,
    durationMillis: Int = 3000,
    staticRingAlpha: Float = 0.30f,
    content: @Composable BoxScope.() -> Unit
) {
    val context = LocalContext.current
    val animationsEnabled = remember {
        Settings.Global.getFloat(
            context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f
        ) != 0f
    }

    val progress by if (animationsEnabled) {
        val transition = rememberInfiniteTransition(label = "halo")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "haloProgress"
        )
    } else {
        remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    }

    Box(
        modifier = modifier.drawBehind {
            val base = size.minDimension / 2f
            val gapPx = ringGap.toPx()
            val strokePx = ringWidth.toPx()

            // 静的リング(常時)
            drawCircle(
                color = color.copy(alpha = staticRingAlpha),
                radius = base + gapPx,
                center = Offset(size.width / 2f, size.height / 2f),
                style = Stroke(width = strokePx)
            )
            // 広がる波紋(1本)
            if (animationsEnabled && progress > 0f) {
                val r = (base + gapPx) * (1f + progress * (maxScale - 1f))
                drawCircle(
                    color = color.copy(alpha = maxAlpha * (1f - progress)),
                    radius = r,
                    center = Offset(size.width / 2f, size.height / 2f),
                    style = Stroke(width = strokePx)
                )
            }
        },
        contentAlignment = Alignment.Center,
        content = content
    )
}

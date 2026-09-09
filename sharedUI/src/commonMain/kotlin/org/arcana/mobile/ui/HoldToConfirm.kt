package org.arcana.mobile.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.arcana.mobile.theme.Arcana
import org.arcana.mobile.theme.ArcanaShapes
import org.arcana.mobile.theme.Clay
import org.arcana.mobile.theme.ClayDeep
import org.arcana.mobile.theme.Dur
import org.arcana.mobile.theme.Ease
import org.arcana.mobile.theme.Stone

internal fun holdCompleted(ringValue: Float): Boolean = ringValue >= 1f

/**
 * A pill you hold. The well fills a ring over [holdMs] with a rising haptic;
 * releasing early cancels; completing fires the reject haptic and [onConfirm].
 */
@Composable
fun HoldToConfirm(
    label: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = Clay,
    accentColor: Color = ClayDeep,
    holdMs: Int = 700,
) {
    val haptics = rememberHaptics()
    val ring = remember { Animatable(0f) }
    var holding by remember { mutableStateOf(false) }

    LaunchedEffect(holding) {
        if (holding) {
            val rampJob = launch { while (true) { haptics.ramp(); delay(90) } }
            ring.animateTo(1f, tween(holdMs, easing = LinearEasing))
            rampJob.cancel()
            if (holdCompleted(ring.value)) { haptics.reject(); onConfirm() }
        } else {
            ring.animateTo(0f, tween(Dur.Short, easing = Ease.Exit))
        }
    }

    Row(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .controlShadow(ArcanaShapes.Pill)
            .clip(ArcanaShapes.Pill)
            .background(containerColor)
            .innerHighlight(ArcanaShapes.Pill)
            .then(
                if (enabled) Modifier.pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            holding = true
                            tryAwaitRelease()
                            holding = false
                        },
                    )
                } else Modifier,
            )
            .padding(start = 24.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label.uppercase(),
            modifier = Modifier.opticallyCentredCaps(fontSize = 14.sp, letterSpacingEm = 0.14f),
            style = TextStyle(fontFamily = Arcana.fonts.display, fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 0.14.em, color = Stone),
        )
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(44.dp)) {
                val stroke = 3.dp.toPx()
                drawCircle(color = Stone.copy(alpha = 0.25f), style = Stroke(stroke))
                drawArc(
                    color = Stone,
                    startAngle = -90f,
                    sweepAngle = 360f * ring.value,
                    useCenter = false,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
            StrokeIcon(icon = ArcanaIcons.Close, size = 16.dp, tint = Stone, contentDescription = "Hold to $label")
        }
    }
}

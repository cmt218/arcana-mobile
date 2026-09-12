package org.arcana.mobile.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.arcana.mobile.theme.Arcana
import org.arcana.mobile.theme.Ash
import org.arcana.mobile.theme.Dur
import org.arcana.mobile.theme.Ink
import org.arcana.mobile.theme.Moss
import org.arcana.mobile.theme.Springs
import org.arcana.mobile.theme.Stone
import kotlin.math.roundToInt

private val SEGMENT_LABEL_SIZE = 12.sp
private const val SEGMENT_TRACKING_EM = 0.10f

/**
 * Equal-width segments in one outlined pill; a Moss highlight glides to the
 * selection. Labels render ALL CAPS. Fire the selection haptic from [onSelect]
 * only when the index actually changed.
 */
@Composable
fun SegmentedControl(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .border(1.dp, Ash, CircleShape),
    ) {
        val density = LocalDensity.current
        val segmentWidth = maxWidth / labels.size
        val segmentPx = with(density) { segmentWidth.toPx() }
        val highlight by animateFloatAsState(
            targetValue = selectedIndex * segmentPx,
            animationSpec = Springs.Settle,
            label = "segmentHighlight",
        )
        // matchParentSize, not fillMaxHeight on the constraints: the pill's
        // height comes from the labels below, and the incoming height may be
        // unbounded in a scrolling parent.
        Box(modifier = Modifier.matchParentSize()) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(highlight.roundToInt(), 0) }
                    .width(segmentWidth)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(Moss),
            )
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            labels.forEachIndexed { index, label ->
                val source = remember { MutableInteractionSource() }
                val selected = index == selectedIndex
                val color by animateColorAsState(
                    targetValue = if (selected) Stone else Ink,
                    animationSpec = tween(Dur.Short),
                    label = "segmentLabel",
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .pressable(source, pressedScale = 0.97f)
                        .clickable(
                            interactionSource = source,
                            indication = null,
                            role = Role.Tab,
                            onClick = { onSelect(index) },
                        )
                        .semantics { this.selected = selected }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label.uppercase(),
                        modifier = Modifier.opticallyCentredCaps(SEGMENT_LABEL_SIZE, SEGMENT_TRACKING_EM),
                        maxLines = 1,
                        softWrap = false,
                        style = TextStyle(
                            fontFamily = Arcana.fonts.display,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = SEGMENT_LABEL_SIZE,
                            letterSpacing = SEGMENT_TRACKING_EM.em,
                            color = color,
                        ),
                    )
                }
            }
        }
    }
}

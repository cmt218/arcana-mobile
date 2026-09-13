package org.arcana.mobile.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.datetime.LocalDate
import org.arcana.mobile.theme.Outline
import org.arcana.mobile.theme.Arcana
import org.arcana.mobile.theme.ArcanaShapes
import org.arcana.mobile.theme.Ash
import org.arcana.mobile.theme.Ink
import org.arcana.mobile.theme.Lime
import org.arcana.mobile.theme.Mist
import org.arcana.mobile.theme.Moss
import org.arcana.mobile.theme.Surface
import org.arcana.mobile.theme.Springs
import org.arcana.mobile.theme.Stone
import org.arcana.mobile.ui.controlShadow
import org.arcana.mobile.ui.innerHighlight
import org.arcana.mobile.ui.opticallyCentredCaps
import org.arcana.mobile.ui.pressable
import org.arcana.mobile.ui.softShadow

/** X of the selection pill for a fractional page [position] (0 = first chip). */
internal fun railPillOffsetPx(position: Float, chipWidthPx: Float, gapPx: Float): Float =
    position * (chipWidthPx + gapPx)

/** Scroll offset that keeps chip [index] fully visible with a margin, or the current offset if it already is. */
internal fun railScrollTargetPx(
    index: Int,
    chipWidthPx: Float,
    gapPx: Float,
    viewportPx: Float,
    currentScrollPx: Float,
    marginPx: Float,
    contentStartPx: Float,
): Float = railScrollTargetPx(index.toFloat(), chipWidthPx, gapPx, viewportPx, currentScrollPx, marginPx, contentStartPx)

/** Fractional-[index] overload: lets the rail track the pill continuously
 *  mid-drag instead of only snapping to a whole chip once the pager settles. */
internal fun railScrollTargetPx(
    index: Float,
    chipWidthPx: Float,
    gapPx: Float,
    viewportPx: Float,
    currentScrollPx: Float,
    marginPx: Float,
    contentStartPx: Float,
): Float {
    val start = contentStartPx + index * (chipWidthPx + gapPx)
    val end = start + chipWidthPx
    return when {
        end + marginPx > currentScrollPx + viewportPx -> end - viewportPx + marginPx
        start - marginPx < currentScrollPx -> maxOf(0f, start - marginPx)
        else -> currentScrollPx
    }
}

private val CHIP_WIDTH = 56.dp
private val CHIP_HEIGHT = 64.dp
private val CHIP_GAP = 8.dp
private val RAIL_EDGE_MARGIN = 24.dp

@Composable
internal fun DayRail(
    days: List<LocalDate>,
    position: () -> Float,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val chipPx = with(density) { CHIP_WIDTH.toPx() }
    val gapPx = with(density) { CHIP_GAP.toPx() }
    val edgePx = with(density) { RAIL_EDGE_MARGIN.toPx() }
    var viewportPx by remember { mutableFloatStateOf(0f) }
    // Read here, not at the ScheduleScreen call site: that scoped a per-frame
    // recompose to the whole pinned header instead of just this rail's chips.
    val currentPosition = position()
    // One source per chip, shared by its ChipFill and DayChip, so a press
    // scales the fill and the labels together instead of the labels alone.
    val pressSources = remember(days.size) { List(days.size) { MutableInteractionSource() } }

    LaunchedEffect(selectedIndex, viewportPx) {
        if (viewportPx == 0f) return@LaunchedEffect
        val target = railScrollTargetPx(
            selectedIndex, chipPx, gapPx, viewportPx, scrollState.value.toFloat(),
            marginPx = edgePx, contentStartPx = edgePx,
        )
        if (target != scrollState.value.toFloat()) {
            scrollState.animateScrollTo(target.roundToInt(), animationSpec = Springs.Settle)
        }
    }
    // Tracks the pill continuously so it can't drift off mid-drag (the settle
    // effect above only catches up after). Same MutatePriority as that effect,
    // so a live finger on the rail itself still wins the MutatorMutex.
    LaunchedEffect(scrollState) {
        snapshotFlow { position() }.collect { p ->
            if (viewportPx == 0f) return@collect
            val target = railScrollTargetPx(
                p, chipPx, gapPx, viewportPx, scrollState.value.toFloat(),
                marginPx = edgePx, contentStartPx = edgePx,
            )
            if (target != scrollState.value.toFloat()) {
                try {
                    scrollState.scrollTo(target.roundToInt())
                } catch (_: CancellationException) {
                    // A higher-priority scroll (e.g. a finger on the rail) cancels only
                    // this call via MutatorMutex, not this effect: skip the frame.
                    // ensureActive rethrows if this LaunchedEffect is the one dying.
                    coroutineContext.ensureActive()
                }
            }
        }
    }

    Box(
        modifier
            .fillMaxWidth()
            .onSizeChanged { viewportPx = it.width.toFloat() }
            .horizontalScroll(scrollState)
            .padding(horizontal = RAIL_EDGE_MARGIN),
    ) {
        // Fills, pill, then labels: three stacked layers, not one. The pill
        // must draw above every fill or a fading chip occludes it mid-drag.
        Row(horizontalArrangement = Arrangement.spacedBy(CHIP_GAP)) {
            days.forEachIndexed { i, _ ->
                ChipFill(
                    coverage = (1f - abs(i - currentPosition)).coerceIn(0f, 1f),
                    source = pressSources[i],
                )
            }
        }
        Box(
            Modifier
                .offset { IntOffset(railPillOffsetPx(currentPosition, chipPx, gapPx).roundToInt(), 0) }
                .size(width = CHIP_WIDTH, height = CHIP_HEIGHT)
                .controlShadow(ArcanaShapes.Card)
                .clip(ArcanaShapes.Card)
                .background(Moss)
                .innerHighlight(ArcanaShapes.Card),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(CHIP_GAP)) {
            days.forEachIndexed { i, date ->
                DayChip(
                    date = date,
                    label = if (i == 0) "TODAY" else "",
                    coverage = (1f - abs(i - currentPosition)).coerceIn(0f, 1f),
                    source = pressSources[i],
                    onClick = { onSelect(i) },
                )
            }
        }
    }
}

@Composable
private fun ChipFill(coverage: Float, source: MutableInteractionSource) {
    val fillAlpha = 1f - coverage
    Box(Modifier.size(width = CHIP_WIDTH, height = CHIP_HEIGHT).pressable(source)) {
        // Split from the fill below so it can fade to nothing on its own;
        // the shared softShadow() has a fixed alpha.
        Box(Modifier.matchParentSize().alpha(fillAlpha).softShadow(ArcanaShapes.Card))
        Box(
            Modifier
                .matchParentSize()
                .clip(ArcanaShapes.Card)
                .background(Surface.copy(alpha = Surface.alpha * fillAlpha))
                .border(1.dp, Outline.copy(alpha = Outline.alpha * fillAlpha), ArcanaShapes.Card),
        )
    }
}

/** Overlap with the pill: 0 plain, 1 fully under it. Continuous so the pill is
 *  never hidden mid-drag. */
@Composable
private fun DayChip(
    date: LocalDate,
    label: String,
    coverage: Float,
    source: MutableInteractionSource,
    onClick: () -> Unit,
) {
    val labelColor = lerp(Ash, Lime, coverage)
    val numberColor = lerp(Ink, Stone, coverage)
    Column(
        modifier = Modifier
            .size(width = CHIP_WIDTH, height = CHIP_HEIGHT)
            .clip(ArcanaShapes.Card)
            .pressable(source)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(top = 8.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label.ifEmpty { date.weekdayAbbr() },
            maxLines = 1, softWrap = false,
            modifier = Modifier.opticallyCentredCaps(fontSize = 10.sp, letterSpacingEm = 0.20f),
            style = TextStyle(
                fontFamily = Arcana.fonts.body,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                letterSpacing = 0.20.em,
                color = labelColor,
            ),
        )
        Text(
            text = date.day.toString(),
            maxLines = 1, softWrap = false,
            style = TextStyle(
                fontFamily = Arcana.fonts.display,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                letterSpacing = (-0.02).em,
                color = numberColor,
            ),
        )
    }
}

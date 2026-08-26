package org.arcana.mobile.booking

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.arcana.mobile.data.SpotDto
import org.arcana.mobile.theme.Mist
import org.arcana.mobile.theme.Moss
import org.arcana.mobile.theme.Mist2
import org.arcana.mobile.theme.Paper
import org.arcana.mobile.theme.Stone
import org.arcana.mobile.theme.Wood
import org.arcana.mobile.ui.ArcanaIcons
import org.arcana.mobile.ui.Caption
import org.arcana.mobile.ui.IconCircle
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

// A circle big enough to hold a full station label ("DF-26") at a readable size.
private val NODE_SIZE = 46.dp
private const val NODE_LABEL_SIZE = 12
private const val MAX_CONTENT_PX = 12000f

/**
 * Full-screen, pinch-to-zoom / drag-to-pan room map — the comprehensive view
 * behind the sheet's expand affordance. Keeps the SAME orientation as the inline
 * map (no transpose). Each spot is a labeled circle (station type + number, e.g.
 * "T-1" / "DF-26") like Mariana Tek's own web map: outlined = open, shaded =
 * taken, Burnt Nectar = selected.
 *
 * Circles are laid out at a fixed readable size (so labels always fit — the whole
 * map scales via a transform, never clipping text), and the room is drawn larger
 * than the screen when dense; the initial zoom fits it. Gestures are handled at
 * the container level: pinch/pan works anywhere (even over a spot) and a tap
 * selects the nearest spot and closes. The X closes without changing selection.
 */
@Composable
fun SpotMapFullScreen(
    spots: List<SpotDto>,
    selected: SpotDto?,
    onSelect: (SpotDto) -> Unit,
    onClose: () -> Unit,
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(Modifier.fillMaxSize().background(Stone)) {
            // Top bar with the close control (kept out of the map so it never overlaps).
            Box(Modifier.fillMaxWidth().padding(16.dp)) {
                IconCircle(
                    icon = ArcanaIcons.Close,
                    diameter = 40,
                    iconSize = 18,
                    background = Paper,
                    borderColor = Mist,
                    contentColor = Wood,
                    onClick = onClose,
                    contentDescription = "Close room map",
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }

            // The map viewport — clipped so panned/zoomed content stays inside.
            BoxWithConstraints(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clipToBounds(),
            ) {
                val density = LocalDensity.current
                val layout = remember(spots) { normalizeSpots(spots) }
                if (layout.spots.isEmpty()) return@BoxWithConstraints

                val vpW = with(density) { maxWidth.toPx() }
                val vpH = with(density) { maxHeight.toPx() }
                val nodePx = with(density) { NODE_SIZE.toPx() }

                // Content box preserves the room's aspect while guaranteeing that no
                // two circles (fixed NODE_SIZE) overlap. Size-independent — depends
                // only on the layout and node size.
                val (contentWpx, contentHpx) = remember(spots, nodePx) {
                    contentSize(layout, nodePx)
                }
                val contentWDp = with(density) { contentWpx.toDp() }
                val contentHDp = with(density) { contentHpx.toDp() }

                // Open with the room's WIDTH matching the screen (pan vertically for
                // taller rooms); allow zooming out to the whole room and well in.
                val fitWidth = vpW / contentWpx
                val fitWhole = min(vpW / contentWpx, vpH / contentHpx)
                val minScale = fitWhole
                val maxScale = max(fitWidth * 4f, 1f)

                // Initialize (and re-fit on a real size change) from a LaunchedEffect
                // so scale/offset use the SETTLED viewport size — computing them in a
                // remember initializer can capture a stale first-composition size and
                // open the map at the wrong zoom.
                var scale by remember(spots) { mutableStateOf(Float.NaN) }
                var offset by remember(spots) { mutableStateOf(Offset.Zero) }
                LaunchedEffect(spots, contentWpx, contentHpx, vpW, vpH) {
                    scale = fitWidth
                    offset = Offset(
                        clampAxis((vpW - contentWpx * fitWidth) / 2f, contentWpx * fitWidth, vpW),
                        clampAxis((vpH - contentHpx * fitWidth) / 2f, contentHpx * fitWidth, vpH),
                    )
                }
                if (scale.isNaN()) return@BoxWithConstraints // wait one frame for init

                // Node centers in content-px, for tap hit-testing (same placement
                // math as SpotScatter: inset by half a node on each edge).
                val centers = remember(spots, contentWpx, contentHpx) {
                    layout.spots.map { ns ->
                        SpotCenter(
                            spot = ns.spot,
                            cx = ns.nx * (contentWpx - nodePx) + nodePx / 2f,
                            cy = ns.ny * (contentHpx - nodePx) + nodePx / 2f,
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        // Pinch/drag anywhere — nodes are NOT individually clickable,
                        // so a finger over a spot never blocks the zoom gesture.
                        .pointerInput(spots, contentWpx, contentHpx) {
                            detectTransformGestures { centroid, pan, zoom, _ ->
                                val newScale = (scale * zoom).coerceIn(minScale, maxScale)
                                val k = newScale / scale
                                // Keep the pinch centroid stationary, then apply pan.
                                val nx = centroid.x - (centroid.x - offset.x) * k + pan.x
                                val ny = centroid.y - (centroid.y - offset.y) * k + pan.y
                                scale = newScale
                                offset = Offset(
                                    clampAxis(nx, contentWpx * newScale, vpW),
                                    clampAxis(ny, contentHpx * newScale, vpH),
                                )
                            }
                        }
                        .pointerInput(spots, contentWpx, contentHpx) {
                            detectTapGestures { pos ->
                                val lx = (pos.x - offset.x) / scale
                                val ly = (pos.y - offset.y) / scale
                                val hit = centers.minByOrNull {
                                    hypot((it.cx - lx).toDouble(), (it.cy - ly).toDouble())
                                } ?: return@detectTapGestures
                                val d = hypot((hit.cx - lx).toDouble(), (hit.cy - ly).toDouble())
                                val selectable = hit.spot.status == "available" || hit.spot.id == selected?.id
                                if (d <= nodePx / 2f * 1.3f && selectable) onSelect(hit.spot)
                            }
                        },
                ) {
                    Box(
                        Modifier
                            // Anchor the (viewport-overflowing) content at TOP-LEFT
                            // instead of letting the parent center it — our transform
                            // offset is computed from a (0,0) origin, so a centered
                            // placement would push the left half off-screen.
                            .wrapContentSize(align = Alignment.TopStart, unbounded = true)
                            // requiredSize (not size) so the content keeps its true
                            // dimensions instead of being clamped to the viewport —
                            // otherwise wide rows get crushed and overlap.
                            .requiredSize(contentWDp, contentHDp)
                            .graphicsLayer {
                                transformOrigin = TransformOrigin(0f, 0f)
                                scaleX = scale
                                scaleY = scale
                                translationX = offset.x
                                translationY = offset.y
                            },
                    ) {
                        SpotScatter(
                            width = contentWDp,
                            height = contentHDp,
                            positions = layout.spots.map { Offset(it.nx, it.ny) },
                        ) {
                            layout.spots.forEach { ns ->
                                val s = ns.spot
                                val isSel = s.id == selected?.id
                                val selectable = s.status == "available" || isSel
                                // Non-interactive here — the container handles taps
                                // so pinch/pan works even with a finger over a spot.
                                SpotDot(
                                    label = s.label,
                                    selected = isSel,
                                    selectable = selectable,
                                    size = NODE_SIZE,
                                    showLabel = true,
                                    labelSize = NODE_LABEL_SIZE,
                                )
                            }
                        }
                    }
                }
            }

            SpotMapLegend(Modifier.fillMaxWidth().padding(16.dp))
        }
    }
}

private data class SpotCenter(val spot: SpotDto, val cx: Float, val cy: Float)

/**
 * Content-box size (px) that preserves the room's aspect ([SpotLayout.bboxAspect])
 * while ensuring fixed-size circles never overlap. Sizing is driven by the closest
 * pair of spots in aspect-corrected 2D space (NOT the per-axis gap): two spots that
 * share an x but sit on different rows are far apart in 2D and must not inflate the
 * canvas — only genuine same-row neighbors constrain it. The canvas is scaled so the
 * closest pair sits exactly one node apart (touching, never overlapping).
 */
private fun contentSize(layout: SpotLayout, nodePx: Float): Pair<Float, Float> {
    val aspect = layout.bboxAspect
    val pts = layout.spots
    var minMetric = Float.MAX_VALUE
    for (i in pts.indices) {
        for (j in i + 1 until pts.size) {
            val dx = (pts[i].nx - pts[j].nx) * aspect // aspect-correct x into real proportions
            val dy = pts[i].ny - pts[j].ny
            val m = sqrt(dx * dx + dy * dy)
            if (m > 1e-4f && m < minMetric) minMetric = m
        }
    }
    if (minMetric == Float.MAX_VALUE) minMetric = 1f // 0 or 1 spot
    var h = (nodePx * 1.05f) / minMetric // 5% breathing room between closest circles
    var w = h * aspect
    if (w > MAX_CONTENT_PX) { w = MAX_CONTENT_PX; h = w / aspect }
    if (h > MAX_CONTENT_PX) { h = MAX_CONTENT_PX; w = h * aspect }
    return w to h
}

/** Clamp a transform offset so scaled content stays within the viewport (centered when smaller). */
private fun clampAxis(value: Float, scaledContent: Float, viewport: Float): Float =
    if (scaledContent <= viewport) (viewport - scaledContent) / 2f
    else value.coerceIn(viewport - scaledContent, 0f)

@Composable
private fun SpotMapLegend(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        LegendSwatch(fill = Paper, border = Mist)
        Caption("Open", size = 12, color = Wood)
        Spacer(Modifier.width(12.dp))
        LegendSwatch(fill = Mist2, border = null)
        Caption("Taken", size = 12, color = Wood)
        Spacer(Modifier.width(12.dp))
        LegendSwatch(fill = Moss, border = null)
        Caption("Yours", size = 12, color = Wood)
    }
}

@Composable
private fun LegendSwatch(fill: Color, border: Color?) {
    Box(
        Modifier
            .padding(end = 6.dp)
            .size(14.dp)
            .clip(CircleShape)
            .background(fill)
            .then(if (border != null) Modifier.border(1.dp, border, CircleShape) else Modifier),
    )
}

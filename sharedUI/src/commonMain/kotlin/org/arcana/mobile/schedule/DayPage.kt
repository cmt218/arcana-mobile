package org.arcana.mobile.schedule

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.time.Instant
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toLocalDateTime
import org.arcana.mobile.networking.ErrorType
import org.arcana.mobile.theme.Ash
import org.arcana.mobile.theme.Charcoal
import org.arcana.mobile.theme.Dur
import org.arcana.mobile.theme.Ease
import org.arcana.mobile.theme.InkAlpha10
import org.arcana.mobile.theme.Mist
import org.arcana.mobile.ui.BodyText
import org.arcana.mobile.ui.DotMatrixLoaderCompact
import org.arcana.mobile.ui.InlineError
import org.arcana.mobile.ui.LocalFloatingBarInset
import org.arcana.mobile.ui.SectionRule
import org.arcana.mobile.ui.ShimmerBox
import org.arcana.mobile.ui.shimmerBrush
import org.arcana.mobile.ui.staticShimmerBrush

private const val SKELETON_ROWS = 6
private const val STAGGER_ROWS = 8
private const val STAGGER_STEP_MS = 16
private const val STAGGER_TOTAL_MS = STAGGER_ROWS * STAGGER_STEP_MS + Dur.Short
private val RISE_DISTANCE = 10.dp

/**
 * One day of the Book tab, as its own scrollable list — a page in the
 * Schedule pager. [header] carries the pinned title/rail/filters content.
 */
@Composable
internal fun DayPage(
    date: LocalDate,
    dayState: DayState?,
    dayError: ErrorType?,
    dayRetrying: Boolean,
    refreshingFilters: Boolean,
    bookedSessions: Map<Int, String>,
    brandNames: Map<Int, String> = emptyMap(),
    isCurrent: Boolean,
    onOpenClassDetail: (Int) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    // Null when there is nothing to pin above the list. A zero-height header item
    // would make the LazyColumn report firstVisibleItemIndex=1 at the true top, so
    // canScrollBackward (and the jump-to-top arrow) would read as scrolled.
    header: (@Composable () -> Unit)?,
) {
    val dayLoaded = dayState?.loaded == true
    val sessions = dayState?.sessions.orEmpty()
    val byBand = remember(sessions) {
        sessions.groupBy {
            Instant.parse(it.startAt).toLocalDateTime(sessionTimeZone(it.location.timezone)).time.timeBand()
        }
    }
    val activeBands = remember(byBand) { TimeBand.values().filter { byBand[it]?.isNotEmpty() == true } }
    // Stagger order derived from band sizes, not a per-session map: keeps this
    // O(bands) rather than O(total sessions) on every page append.
    val bandStart: List<Int> = remember(byBand) {
        var next = 0
        activeBands.map { band -> next.also { next += byBand[band]?.size ?: 0 } }
    }
    val listAlpha = if (refreshingFilters) 0.6f else 1f
    val listState = rememberLazyListState()

    // Page-level Animatable, read inside each row's graphicsLayer: a snapshot-state
    // gate would freeze at first composition, before this effect's dispatched body
    // ever runs (LazyColumn subcomposes items during measure, not after).
    val staggerElapsed = remember { Animatable(0f) }
    LaunchedEffect(isCurrent, dayLoaded) {
        if (!isCurrent || !dayLoaded || staggerElapsed.value > 0f) return@LaunchedEffect
        try {
            staggerElapsed.animateTo(STAGGER_TOTAL_MS.toFloat(), tween(STAGGER_TOTAL_MS, easing = LinearEasing))
        } finally {
            // NonCancellable: a bare snapTo here would itself throw on the same
            // cancellation that reached this finally, leaving rows stuck mid-rise.
            withContext(NonCancellable) { staggerElapsed.snapTo(STAGGER_TOTAL_MS.toFloat()) }
        }
    }

    // onLoadMore/onRetry act on the ViewModel's SELECTED day, so only the
    // current page may reach them — gate the collector itself, not just the UI.
    LaunchedEffect(listState, isCurrent) {
        if (!isCurrent) return@LaunchedEffect
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index to
                listState.layoutInfo.totalItemsCount
        }
            .distinctUntilChanged()
            .collect { (lastVisible, totalCount) ->
                if (lastVisible != null && lastVisible >= totalCount - LOAD_MORE_LOOKAHEAD) {
                    onLoadMore()
                }
            }
    }

    // One shimmer transition per page, not one per box, and only for the page
    // the member can see — a non-current page kept alive by
    // beyondViewportPageCount gets a frozen brush instead of an infinite one.
    val skeletonBrush = when {
        dayError != null || dayLoaded -> null
        isCurrent -> shimmerBrush()
        else -> staticShimmerBrush()
    }

    Box(Modifier.fillMaxSize()) {
    val scrollScope = rememberCoroutineScope()
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp + LocalFloatingBarInset.current),
    ) {
        header?.let { item("page-header") { it() } }
        if (dayError != null) {
            item("day-error") {
                InlineError(
                    type = dayError,
                    onRetry = { if (isCurrent) onRetry() },
                    retrying = dayRetrying,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                        .alpha(listAlpha),
                )
            }
        } else if (!dayLoaded) {
            items(SKELETON_ROWS, key = { "skeleton-$it" }) { i ->
                SkeletonClassRow(
                    modifier = Modifier.padding(horizontal = 24.dp).alpha(listAlpha),
                    fill = 0.55f + (i % 3) * 0.15f,
                    brush = skeletonBrush,
                )
            }
        } else if (sessions.isEmpty()) {
            item("empty") {
                Column(
                    modifier = Modifier
                        .padding(start = 24.dp, end = 24.dp, top = 16.dp)
                        .alpha(listAlpha),
                ) {
                    BodyText(
                        text = "No classes match your filters for this day.",
                        size = 14, color = Charcoal,
                    )
                }
            }
        } else {
            activeBands.forEachIndexed { bandIdx, band ->
                item("band-header-$band") {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 24.dp)
                            .alpha(listAlpha),
                    ) {
                        if (bandIdx > 0) Spacer(Modifier.height(24.dp))
                        SectionRule(label = band.label)
                        Spacer(Modifier.height(8.dp))
                    }
                }
                itemsIndexed(
                    items = byBand[band].orEmpty(),
                    key = { _, session -> "row-${session.id}" },
                ) { indexInBand, session ->
                    val order = bandStart[bandIdx] + indexInBand
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 24.dp)
                            .alpha(listAlpha)
                            .riseIn(staggerElapsed, order),
                    ) {
                        ClassRow(
                            session,
                            onClick = { onOpenClassDetail(session.id) },
                            bookedStatus = bookedSessions[session.id],
                            brandName = brandNames[session.location.id],
                            // null clock ⇒ the capacity bar draws at its final fill.
                            barClock = null,
                        )
                    }
                }
            }
            if (dayState.nextCursor != null) {
                item("load-more") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp)
                            .alpha(listAlpha),
                        contentAlignment = Alignment.Center,
                    ) {
                        DotMatrixLoaderCompact()
                    }
                }
            } else {
                item("end-of-day") {
                    EndOfListMarker(
                        text = "That's everything for ${titleCase(date.dayOfWeek.name)}",
                        modifier = Modifier.alpha(listAlpha),
                    )
                }
            }
        }
    }
        // Jump-to-top affordance: appears once the day list is scrolled past its
        // first item; a tap returns to the top. No jump-to-bottom here — the list
        // paginates, so reaching the true end would force loading every page.
        ScrollJumpChevron(
            pointsDown = false,
            visible = isCurrent && listState.canScrollBackward,
            onClick = { scrollScope.launch { listState.animateScrollToItem(0) } },
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
        )
    }
}

@Composable
internal fun SkeletonClassRow(modifier: Modifier = Modifier, fill: Float, brush: Brush?) {
    Row(modifier = modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.Top) {
        Column(modifier = Modifier.width(SCHEDULE_TIME_COL_WIDTH)) {
            ShimmerBox(Modifier.size(width = 44.dp, height = 18.dp), shape = RoundedCornerShape(4.dp), brush = brush)
            Spacer(Modifier.height(6.dp))
            ShimmerBox(Modifier.size(width = 30.dp, height = 8.dp), shape = RoundedCornerShape(4.dp), brush = brush)
        }
        Box(Modifier.width(3.dp).height(64.dp).background(InkAlpha10, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            ShimmerBox(Modifier.fillMaxWidth(0.5f).height(9.dp), shape = RoundedCornerShape(4.dp), brush = brush)
            Spacer(Modifier.height(8.dp))
            ShimmerBox(Modifier.fillMaxWidth(fill).height(16.dp), shape = RoundedCornerShape(4.dp), brush = brush)
            Spacer(Modifier.height(8.dp))
            ShimmerBox(Modifier.fillMaxWidth(0.35f).height(9.dp), shape = RoundedCornerShape(4.dp), brush = brush)
        }
    }
}

/** Rises 10 dp with a fade, driven by the page's shared [elapsed] clock and
 *  sampled in the draw phase: a row's progress is a pure function of the
 *  current elapsed value, so a late or re-drawn row never replays. */
@Composable
private fun Modifier.riseIn(elapsed: Animatable<Float, *>, order: Int): Modifier {
    if (order >= STAGGER_ROWS) return this
    val risePx = with(LocalDensity.current) { RISE_DISTANCE.toPx() }
    return graphicsLayer {
        val t = Ease.Emphasized.transform(
            ((elapsed.value - order * STAGGER_STEP_MS) / Dur.Short).coerceIn(0f, 1f),
        )
        alpha = t
        translationY = risePx * (1f - t)
    }
}

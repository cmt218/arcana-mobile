@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package org.arcana.mobile.search

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sqrt
import kotlin.time.Instant
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toLocalDateTime
import org.arcana.mobile.data.ScheduleSessionDto
import org.arcana.mobile.schedule.ClassRow
import org.arcana.mobile.schedule.EndOfListMarker
import org.arcana.mobile.schedule.sessionTimeZone
import org.arcana.mobile.theme.Arcana
import org.arcana.mobile.theme.Atmosphere
import org.arcana.mobile.theme.Ink
import org.arcana.mobile.theme.Mist
import org.arcana.mobile.theme.Moss
import org.arcana.mobile.theme.Paper
import androidx.compose.ui.graphics.lerp
import org.arcana.mobile.theme.Stone
import org.arcana.mobile.ui.ArcanaIcons
import org.arcana.mobile.ui.ArcanaTextField
import org.arcana.mobile.ui.BodyText
import org.arcana.mobile.ui.Caption
import org.arcana.mobile.ui.DotMatrixLoaderCompact
import org.arcana.mobile.ui.FilterChip
import org.arcana.mobile.ui.IconCircle
import org.arcana.mobile.ui.InlineError
import org.arcana.mobile.ui.LocalFloatingBarInset
import org.arcana.mobile.ui.Overline
import org.arcana.mobile.ui.StrokeIcon
import org.arcana.mobile.ui.safeContentPadding
import org.koin.compose.viewmodel.koinViewModel

private const val LOAD_MORE_LOOKAHEAD = 6

/** Full-screen search takeover over the Book tab. Enters as a container
 *  transform: a Stone surface clipped to the entry pill's bounds
 *  ([originInRoot]) expands to fill the screen while the Book tab stays
 *  visible beneath (the NavHost holds it mounted for the duration); closing
 *  reverses the reveal before popping. */
@Composable
fun SearchScreen(
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = koinViewModel(),
    originInRoot: Rect? = null,
    onOpenClassDetail: (Int) -> Unit = {},
    onClose: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val query by viewModel.query.collectAsState()
    val keyboard = LocalSoftwareKeyboardController.current

    val revealProgress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        revealProgress.animateTo(1f, tween(SEARCH_REVEAL_MS, easing = SearchRevealEase))
    }
    val scope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }
    fun animatedClose() {
        if (closing) return
        closing = true
        keyboard?.hide()
        scope.launch {
            revealProgress.animateTo(0f, tween(SEARCH_REVEAL_CLOSE_MS, easing = SearchRevealEase))
            onClose()
        }
    }
    BackHandler { animatedClose() }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val fullWidth = constraints.maxWidth.toFloat()
        val fullHeight = constraints.maxHeight.toFloat()
        val fallbackOrigin = with(density) {
            Rect(fullWidth - 180.dp.toPx(), 56.dp.toPx(), fullWidth - 16.dp.toPx(), 100.dp.toPx())
        }
        val progress = revealProgress.value
        // Stadium-to-circle morph: the rect stays maximally rounded
        // (corner = half the short side), so it starts as the pill's own
        // oval and grows as a CIRCLE past the screen corners (end radius =
        // the farthest corner's distance from the pill centre) — never a
        // rounded square.
        val start = originInRoot ?: fallbackOrigin
        val center = start.center
        val dx = maxOf(center.x, fullWidth - center.x)
        val dy = maxOf(center.y, fullHeight - center.y)
        val endRadius = sqrt(dx * dx + dy * dy)
        val end = Rect(
            center.x - endRadius, center.y - endRadius,
            center.x + endRadius, center.y + endRadius,
        )
        val revealRect = lerp(start, end, progress)
        val revealShape = GenericShape { _, _ ->
            addRoundRect(RoundRect(revealRect, CornerRadius(revealRect.minDimension / 2f)))
        }

        // Dim the Book tab beneath the growing oval — the bright surface
        // against a darkening surround is what makes the edge legible.
        Box(
            Modifier
                .matchParentSize()
                .background(Ink.copy(alpha = SEARCH_SCRIM_MAX_ALPHA * progress)),
        )
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    shadowElevation = 16.dp.toPx() * (1f - progress)
                    shape = revealShape
                    clip = true
                }
                // Paper is the entry pill's fill: starting there makes the
                // oval read as the pill itself growing, settling to Stone.
                .background(lerp(Paper, Stone, progress)),
        ) {
            Atmosphere(Modifier.matchParentSize().graphicsLayer { alpha = progress })
            Column(
                Modifier
                    .fillMaxSize()
                    .safeContentPadding()
                    .imePadding()
                    // Content joins partway through the reveal so the oval
                    // reads as growing first, then filling with the screen.
                    .graphicsLayer { alpha = ((progress - 0.35f) / 0.65f).coerceIn(0f, 1f) },
            ) {
                // Close sits top-LEFT, matching ClassDetail/StudioSelection.
                Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 16.dp)) {
                    IconCircle(
                        icon = ArcanaIcons.Close,
                        diameter = 36,
                        iconSize = 16,
                        borderColor = Mist,
                        contentColor = Ink,
                        onClick = ::animatedClose,
                        contentDescription = "Close search",
                    )
                }
                // No auto-focus: the keyboard appears when the member taps the
                // field, so the recents/results are never born half-covered.
                ArcanaTextField(
                    label = "SEARCH",
                    value = query,
                    onValueChange = viewModel::onQueryChanged,
                    placeholder = "Classes, studios, instructors",
                    imeAction = ImeAction.Search,
                    onImeAction = { keyboard?.hide() },
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .padding(top = 16.dp)
                        .fillMaxWidth(),
                    trailing = if (query.isEmpty()) null else {
                        {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .clickable { viewModel.onQueryChanged("") },
                                contentAlignment = Alignment.Center,
                            ) {
                                StrokeIcon(
                                    icon = ArcanaIcons.Close,
                                    size = 14.dp,
                                    tint = Ink,
                                    contentDescription = "Clear search",
                                )
                            }
                        }
                    },
                )

                when (val s = state) {
                    is SearchUiState.Idle -> IdleContent(
                        recents = s.recents,
                        onRecentTapped = viewModel::onRecentTapped,
                        onClearRecents = viewModel::onClearRecents,
                    )
                    is SearchUiState.Searching -> Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        DotMatrixLoaderCompact()
                    }
                    is SearchUiState.NoResults -> NoResultsContent(s.query)
                    is SearchUiState.Error -> InlineError(
                        type = s.type,
                        onRetry = viewModel::retry,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 24.dp),
                    )
                    is SearchUiState.Results -> ResultsContent(
                        results = s,
                        query = query,
                        onScope = viewModel::onScope,
                        onRemoveScope = viewModel::onRemoveScope,
                        onLoadMore = viewModel::loadMore,
                        onScrollStarted = { keyboard?.hide() },
                        onResultTapped = { position, id ->
                            viewModel.onResultTapped(position)
                            onOpenClassDetail(id)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun IdleContent(
    recents: List<String>,
    onRecentTapped: (String) -> Unit,
    onClearRecents: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 24.dp, end = 24.dp, top = 20.dp,
            bottom = 24.dp + LocalFloatingBarInset.current,
        ),
    ) {
        if (recents.isEmpty()) {
            item("hint") {
                Caption(
                    text = "Search classes, studios, instructors, or neighborhoods.",
                    size = 13, color = Ink,
                )
            }
        } else {
            item("recent-header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Overline(
                        text = "RECENT",
                        size = 11, color = Ink,
                        modifier = Modifier.weight(1f),
                    )
                    // Padding inside the clickable clears the 40dp hit-area
                    // floor despite the 11sp label (house pattern — see the
                    // Schedule favorites nudge CTA).
                    Overline(
                        text = "CLEAR",
                        size = 11, color = Moss,
                        modifier = Modifier
                            .clickable(onClick = onClearRecents)
                            .padding(vertical = 12.dp)
                            .padding(start = 12.dp),
                    )
                }
            }
            items(recents.size, key = { i -> "recent-${recents[i]}" }) { i ->
                BodyText(
                    text = recents[i],
                    size = 15, color = Ink,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onRecentTapped(recents[i]) }
                        .padding(vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun NoResultsContent(query: String) {
    Column(Modifier.padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(24.dp))
        BodyText(
            text = "No classes found for \"$query\".",
            size = 15, color = Ink,
        )
        Spacer(Modifier.height(8.dp))
        Caption(
            text = "Try a class type, studio, instructor, or neighborhood.",
            size = 13, color = Ink,
        )
    }
}

@Composable
private fun ResultsContent(
    results: SearchUiState.Results,
    query: String,
    onScope: (SearchScope) -> Unit,
    onRemoveScope: (SearchScope) -> Unit,
    onLoadMore: () -> Unit,
    onScrollStarted: () -> Unit,
    onResultTapped: (position: Int, sessionId: Int) -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(listState) {
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
    // Scrolling the results is the member reaching for content — drop the
    // keyboard out of the way.
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { scrolling -> if (scrolling) onScrollStarted() }
    }

    // Group chronologically-ordered sessions into day sections, each keyed on
    // the session's own location-local date (same rule as the schedule).
    val byDay: List<Pair<LocalDate, List<IndexedValue<ScheduleSessionDto>>>> =
        remember(results.sessions) {
            results.sessions.withIndex()
                .groupBy { (_, session) ->
                    Instant.parse(session.startAt)
                        .toLocalDateTime(sessionTimeZone(session.location.timezone))
                        .date
                }
                .toList()
                .sortedBy { (date, _) -> date }
        }

    // Active chips already explain why every row is here — captions would
    // just echo them.
    val reasons: List<String?> = remember(results.sessions, query, results.activeScopes) {
        if (results.activeScopes.isNotEmpty()) List(results.sessions.size) { null }
        else searchMatchReasons(results.sessions, query)
    }

    val listAlpha = if (results.refreshing) 0.6f else 1f

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp + LocalFloatingBarInset.current),
    ) {
        if (results.activeScopes.isNotEmpty() || results.chips.isNotEmpty()) {
            item("chips") {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp)
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    results.activeScopes.forEach { active ->
                        FilterChip(label = active.label, onRemove = { onRemoveScope(active) })
                    }
                    results.chips.forEach { chip ->
                        ScopeChip(scope = chip, onClick = { onScope(chip) })
                    }
                }
            }
        }

        if (results.refreshing) {
            item("refreshing") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    DotMatrixLoaderCompact()
                }
            }
        }

        if (results.sessions.isEmpty()) {
            item("chips-no-classes") {
                Box(Modifier.padding(horizontal = 24.dp, vertical = 24.dp)) {
                    BodyText(
                        text = "No upcoming classes match. Tap a result above to browse it.",
                        size = 15, color = Ink,
                    )
                }
            }
        }

        byDay.forEach { (date, sessions) ->
            item("day-${date}") {
                Overline(
                    text = dayHeaderLabel(date),
                    size = 11, color = Ink,
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .padding(top = 20.dp, bottom = 4.dp)
                        .alpha(listAlpha),
                )
            }
            items(sessions.size, key = { i -> "row-${sessions[i].value.id}" }) { i ->
                val (index, session) = sessions[i]
                Column(
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .alpha(listAlpha),
                ) {
                    ClassRow(
                        session = session,
                        onClick = { onResultTapped(index, session.id) },
                    )
                    reasons.getOrNull(index)?.let { reason ->
                        Caption(
                            text = reason,
                            size = 11, color = Ink,
                            modifier = Modifier.padding(bottom = 8.dp).alpha(0.6f),
                        )
                    }
                }
            }
        }

        if (results.loadingMore) {
            item("loading-more") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    DotMatrixLoaderCompact()
                }
            }
        } else if (results.nextCursor == null && results.sessions.isNotEmpty()) {
            item("end-of-results") {
                EndOfListMarker(
                    text = "That's every match",
                    modifier = Modifier.alpha(listAlpha),
                )
            }
        }
    }
}

/** Tappable pick-a-scope pill. Title-case labels (names), so no all-caps
 *  optical centring applies here. */
@Composable
private fun ScopeChip(scope: SearchScope, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .border(1.dp, Mist, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = scope.label,
            style = TextStyle(
                fontFamily = Arcana.fonts.body,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                color = Ink,
            ),
        )
        Caption(
            text = when (scope) {
                is SearchScope.Studio -> "Studio"
                is SearchScope.Location -> "Location"
                is SearchScope.Instructor -> "Instructor"
            },
            size = 11, color = Moss,
        )
    }
}

private fun dayHeaderLabel(date: LocalDate): String {
    val dow = date.dayOfWeek.name.take(3)
    val month = date.month.name.take(3)
    return "$dow $month ${date.day}"
}

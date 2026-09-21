@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package org.arcana.mobile.review

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import org.arcana.mobile.data.FeedbackScopeDto
import org.arcana.mobile.data.ReviewDto
import org.arcana.mobile.schedule.EndOfListMarker
import org.arcana.mobile.schedule.JumpToTop
import org.arcana.mobile.theme.Atmosphere
import org.arcana.mobile.theme.Charcoal
import org.arcana.mobile.theme.Ink
import org.arcana.mobile.theme.Moss
import org.arcana.mobile.theme.Outline
import org.arcana.mobile.theme.Surface
import org.arcana.mobile.theme.Wood
import org.arcana.mobile.ui.ArcanaIcons
import org.arcana.mobile.ui.ArcanaPullToRefreshBox
import org.arcana.mobile.ui.BodyText
import org.arcana.mobile.ui.Caption
import org.arcana.mobile.ui.ErrorCopy
import org.arcana.mobile.ui.ErrorSnackbar
import org.arcana.mobile.ui.FullScreenError
import org.arcana.mobile.ui.Heading2
import org.arcana.mobile.ui.IconCircle
import org.arcana.mobile.ui.InlineError
import org.arcana.mobile.ui.Overline
import org.arcana.mobile.ui.ShimmerBox
import org.arcana.mobile.ui.TextLink
import org.arcana.mobile.ui.TonalWellRule
import org.arcana.mobile.ui.TonalWellShape
import org.arcana.mobile.ui.TransientSurface
import org.arcana.mobile.ui.chromeBottom
import org.arcana.mobile.ui.pressable
import org.arcana.mobile.ui.safeContentPadding
import org.arcana.mobile.ui.safeHorizontalPadding
import org.arcana.mobile.ui.tonalWell
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private const val LOAD_MORE_LOOKAHEAD = 10
private const val EMPTY = "No feedback here yet."
private const val ATTRIBUTION = "Arcana member"
private const val END_OF_FEED = "That's every review"

private fun allLocationsLabel(count: Int) = "All locations · $count reviews"

/** Member feedback for one scope (spec 5.4): stats header, anonymous items,
 *  keyset infinite scroll. Tapping an item opens its studio page. */
@Composable
fun FeedbackFeedScreen(
    scopeType: String,
    scopeValue: String,
    label: String,
    source: String,
    onClose: () -> Unit,
    onOpenStudio: (String) -> Unit,
    onOpenFeed: (FeedbackScope) -> Unit,
) {
    val vm = koinViewModel<FeedbackFeedViewModel>(key = "feed-$scopeType-$scopeValue") {
        parametersOf(scopeType, scopeValue, source)
    }
    LaunchedEffect(Unit) { vm.onOpened() }
    val state by vm.uiState.collectAsState()
    val retrying by vm.retrying.collectAsState()
    val isRefreshing by vm.isRefreshing.collectAsState()
    val refreshFailed by vm.refreshFailed.collectAsState()
    BackHandler { onClose() }

    var headerBottom by remember { mutableStateOf(0.dp) }
    Box(modifier = Modifier.fillMaxSize()) {
        Atmosphere()
        // Under the header, on the whole screen: see FullScreenError.
        (state as? FeedbackFeedUiState.Error)?.let {
            FullScreenError(type = it.type, onRetry = vm::retry, retrying = retrying, topInset = headerBottom)
        }
        Column(
            modifier = Modifier.fillMaxSize().safeContentPadding().padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Column(modifier = Modifier.chromeBottom { headerBottom = it }) {
            IconCircle(
                icon = ArcanaIcons.Close,
                diameter = 38,
                iconSize = 18,
                background = Surface,
                borderColor = Outline,
                contentColor = Ink,
                onClick = onClose,
                contentDescription = "Close feedback",
            )
            Spacer(Modifier.height(16.dp))
            val header = (state as? FeedbackFeedUiState.Success)?.scope?.label?.takeIf { it.isNotBlank() }
                ?: label.ifBlank { FeedbackScope.All.label }
            Heading2(header, size = 26, color = Wood)
            // Until a scoped feed loads, say what the page under the name is.
            if (state !is FeedbackFeedUiState.Success && scopeType != FeedbackScope.All.type) {
                Spacer(Modifier.height(6.dp))
                Caption(FeedbackScope.All.label, size = 12, color = Charcoal)
            }
            // A location feed is one lens on its brand's: offer every location at once.
            (state as? FeedbackFeedUiState.Success)?.scope?.let { scope ->
                val brand = scope.brand
                if (brand != null && brand.count > scope.count) {
                    Spacer(Modifier.height(12.dp))
                    TextLink(
                        label = allLocationsLabel(brand.count),
                        onClick = { onOpenFeed(FeedbackScope.brand(brand.slug, brand.name)) },
                        color = Moss,
                        underline = false,
                    )
                }
            }
            }
            if (state !is FeedbackFeedUiState.Error) {
            Spacer(Modifier.height(12.dp))
            ArcanaPullToRefreshBox(isRefreshing = isRefreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
                when (val s = state) {
                    FeedbackFeedUiState.Loading -> SkeletonList(listPadding())
                    is FeedbackFeedUiState.Error -> Unit // drawn full screen above
                    is FeedbackFeedUiState.Success -> ItemList(
                        state = s,
                        contentPadding = listPadding(),
                        onLoadMore = vm::loadMore,
                        onRetryPage = vm::retryLoadMore,
                        onOpen = { item ->
                            vm.onItemTapped()
                            onOpenStudio(item.brand.slug)
                        },
                    )
                }
            }
            }
        }
        TransientSurface(
            visible = refreshFailed,
            modifier = Modifier.align(Alignment.BottomCenter).safeHorizontalPadding().padding(bottom = 16.dp),
        ) {
            ErrorSnackbar(
                text = ErrorCopy.REFRESH_FAILED,
                onRetry = { vm.dismissRefreshFailed(); vm.refresh() },
                onDismiss = vm::dismissRefreshFailed,
            )
        }
    }
}

// The cards already end 12dp clear of whatever follows, so the footer stays tight.
private val END_MARKER_PADDING = PaddingValues(top = 8.dp)

@Composable
private fun listPadding(): PaddingValues =
    PaddingValues(bottom = 8.dp + WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding())

/**
 * The all-studios feed as a panel inside another screen (Discover's third
 * lens): no header and no back handling of its own. A cold-load failure draws
 * nothing here; the host shows it full screen from [vm], centred on the screen
 * rather than in the space under its own header.
 */
@Composable
fun FeedbackFeedPanel(
    vm: FeedbackFeedViewModel,
    onOpenStudio: (String) -> Unit,
    bottomPadding: Dp,
    modifier: Modifier = Modifier,
) {
    val state by vm.uiState.collectAsState()
    val isRefreshing by vm.isRefreshing.collectAsState()
    LaunchedEffect(vm) { vm.onOpened() }
    if (state is FeedbackFeedUiState.Error) return
    val padding = PaddingValues(bottom = bottomPadding)
    ArcanaPullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = vm::refresh,
        modifier = modifier.fillMaxSize().padding(horizontal = 20.dp),
    ) {
        when (val s = state) {
            FeedbackFeedUiState.Loading -> SkeletonList(padding)
            is FeedbackFeedUiState.Error -> Unit
            is FeedbackFeedUiState.Success -> ItemList(
                state = s,
                contentPadding = padding,
                onLoadMore = vm::loadMore,
                onRetryPage = vm::retryLoadMore,
                onOpen = { item ->
                    vm.onItemTapped()
                    onOpenStudio(item.brand.slug)
                },
            )
        }
    }
}

/** The all-studios feed's view model, for a host that embeds [FeedbackFeedPanel]. */
@Composable
fun allFeedbackViewModel(source: String): FeedbackFeedViewModel =
    koinViewModel(key = "feed-panel-${FeedbackScope.All.type}") { parametersOf(FeedbackScope.All.type, "", source) }

@Composable
private fun ItemList(
    state: FeedbackFeedUiState.Success,
    contentPadding: PaddingValues,
    onLoadMore: () -> Unit,
    onRetryPage: () -> Unit,
    onOpen: (ReviewDto) -> Unit,
) {
    val listState = rememberLazyListState()
    val loadMore by rememberUpdatedState(onLoadMore)
    LaunchedEffect(listState) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index to listState.layoutInfo.totalItemsCount
        }
            .distinctUntilChanged()
            .collect { (lastVisible, totalCount) ->
                if (lastVisible != null && lastVisible >= totalCount - LOAD_MORE_LOOKAHEAD) loadMore()
            }
    }
    Box(modifier = Modifier.fillMaxSize()) {
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = contentPadding) {
        if (state.items.isEmpty()) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 48.dp, bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) { BodyText(EMPTY, size = 16, color = Ink) }
            }
            return@LazyColumn
        }
        if (showsAverages(state.scope)) {
            item(key = "averages") {
                AveragesCard(state.scope)
                Spacer(Modifier.height(12.dp))
            }
        }
        items(state.items, key = { "r-${it.id}" }) { item ->
            FeedbackCard(item = item, onClick = { onOpen(item) })
            Spacer(Modifier.height(12.dp))
        }
        if (state.loadingMore) item(key = "more") { SkeletonRow() }
        state.pageError?.let { type ->
            item(key = "page-error") {
                InlineError(type = type, onRetry = onRetryPage, modifier = Modifier.padding(vertical = 8.dp))
            }
        }
        if (state.nextCursor == null && !state.loadingMore && state.pageError == null) {
            item(key = "end") { EndOfListMarker(text = END_OF_FEED, contentPadding = END_MARKER_PADDING) }
        }
    }
    JumpToTop(listState)
    }
}

/** The scope's averages in the same well and the same five columns as the
 *  reviews under it, so the summary and every card read down one grid. */
@Composable
private fun AveragesCard(scope: FeedbackScopeDto) {
    Column(modifier = Modifier.fillMaxWidth().tonalWell().padding(16.dp)) {
        Overline(averagesHeading(scope.count), size = 10, color = Moss)
        Spacer(Modifier.height(14.dp))
        ReviewAverageStrip(scope)
    }
}

/** One review, in the tonal well every review surface shares: when and who,
 *  where, what, the comment when there is one, then the answers in the five
 *  fixed columns of [ReviewStatStrip]. */
@Composable
private fun FeedbackCard(item: ReviewDto, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val where = listOf(item.brand.name, item.location.name).filter { it.isNotBlank() }.joinToString(" · ")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pressable(source, pressedScale = 0.99f)
            .tonalWell()
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(16.dp),
    ) {
        Overline("${reviewDateLabel(item.createdAt)} · $ATTRIBUTION", size = 10, color = Charcoal)
        Spacer(Modifier.height(8.dp))
        BodyText(where, size = 16, color = Wood, weight = FontWeight.Medium)
        Spacer(Modifier.height(2.dp))
        Caption(classWithInstructor(item.classType.label, item.instructor?.name), size = 12, color = Charcoal, maxLines = 2)
        if (item.comment.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            BodyText(item.comment, size = 15, color = Ink)
        }
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(TonalWellRule))
        Spacer(Modifier.height(12.dp))
        ReviewStatStrip(item)
    }
}

@Composable
private fun SkeletonList(contentPadding: PaddingValues) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item { Spacer(Modifier.height(12.dp)) }
        repeat(5) { item { SkeletonRow() } }
    }
}

@Composable
private fun SkeletonRow() {
    ShimmerBox(modifier = Modifier.padding(bottom = 12.dp).fillMaxWidth().height(148.dp), shape = TonalWellShape)
}

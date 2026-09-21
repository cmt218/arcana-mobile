package org.arcana.mobile.discover

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.arcana.mobile.data.DiscoverStudioDto
import org.arcana.mobile.review.FeedbackFeedPanel
import org.arcana.mobile.review.FeedbackFeedUiState
import org.arcana.mobile.review.allFeedbackViewModel
import org.arcana.mobile.theme.*
import org.arcana.mobile.ui.ArcanaIcons
import org.arcana.mobile.ui.ArcanaPullToRefreshBox
import org.arcana.mobile.ui.BodyText
import org.arcana.mobile.ui.Caption
import org.arcana.mobile.ui.CategoryIcons
import org.arcana.mobile.ui.CircleMonogram
import org.arcana.mobile.ui.ErrorCopy
import org.arcana.mobile.ui.ErrorSnackbar
import org.arcana.mobile.ui.FilterChip
import org.arcana.mobile.ui.FilterDoneButton
import org.arcana.mobile.ui.FilterPill
import org.arcana.mobile.ui.FloatingFilterPanel
import org.arcana.mobile.ui.FlowChipRow
import org.arcana.mobile.ui.FullScreenError
import org.arcana.mobile.ui.Heading2
import org.arcana.mobile.ui.LocalFloatingBarInset
import org.arcana.mobile.ui.Overline
import org.arcana.mobile.ui.SegmentedControl
import org.arcana.mobile.ui.ShimmerBox
import org.arcana.mobile.ui.cardShadow
import org.arcana.mobile.ui.StrokeIcon
import org.arcana.mobile.ui.StudioLocationRow
import org.arcana.mobile.ui.TextLink
import org.arcana.mobile.ui.TransientSurface
import org.arcana.mobile.ui.filterPanelEnter
import org.arcana.mobile.ui.filterPanelExit
import org.arcana.mobile.schedule.JumpToTop
import org.arcana.mobile.ui.chromeBottom
import org.arcana.mobile.ui.pressable
import org.arcana.mobile.ui.rememberHaptics
import org.arcana.mobile.ui.safeContentPadding
import org.arcana.mobile.ui.safeHorizontalPadding
import org.arcana.mobile.ui.studioColorFor
import org.koin.compose.viewmodel.koinViewModel

private const val SECTION_MODALITIES = "mod"
private const val SECTION_NEIGHBORHOODS = "hood"
private const val MAX_CARD_CATEGORIES = 3
private const val MAX_CARD_NEIGHBORHOODS = 3
private const val POPOVER_HEIGHT_FRACTION = 0.62f
private val PIN_CARD_GAP = 12.dp

/**
 * The Discover tab: three lenses on the same studios. Studios is the A to Z
 * list, Map the same studios as pins, Feedback what members said about them.
 * The modality and neighborhood filters narrow the list and the map together.
 */
@Composable
fun DiscoverScreen(onOpenStudio: (slug: String, source: String, locationId: Int?) -> Unit) {
    val vm = koinViewModel<DiscoverViewModel>()
    LaunchedEffect(Unit) { vm.onOpened() }
    val state by vm.uiState.collectAsState()
    val mode by vm.mode.collectAsState()
    val selectedPinId by vm.selectedPinId.collectAsState()
    val isRefreshing by vm.isRefreshing.collectAsState()
    val refreshFailed by vm.refreshFailed.collectAsState()
    val retrying by vm.retrying.collectAsState()
    var expandedSection by remember { mutableStateOf("") }
    val haptics = rememberHaptics()
    // Built on first use: a member who never opens Feedback never pays for its fetch.
    val feedVm = if (mode == DiscoverMode.Feedback) allFeedbackViewModel(source = "discover") else null
    val feedState = feedVm?.uiState?.collectAsState()?.value
    val feedRetrying = feedVm?.retrying?.collectAsState()?.value ?: false
    val feedRefreshFailed = feedVm?.refreshFailed?.collectAsState()?.value ?: false

    var headerBottom by remember { mutableStateOf(0.dp) }
    // What the selected pin's card covers, so the refresh toast rises clear of it.
    var pinCardCover by remember { mutableStateOf(0.dp) }
    Box(modifier = Modifier.fillMaxSize()) {
        Atmosphere(drifting = mode != DiscoverMode.Map)
        // Under the header, on the whole screen (as on Home and Book): see FullScreenError.
        if (mode == DiscoverMode.Feedback) {
            (feedState as? FeedbackFeedUiState.Error)?.let {
                FullScreenError(type = it.type, onRetry = { feedVm?.retry() }, retrying = feedRetrying, topInset = headerBottom)
            }
        } else {
            (state as? DiscoverUiState.Error)?.let {
                FullScreenError(type = it.type, onRetry = vm::retry, retrying = retrying, topInset = headerBottom)
            }
        }
        Column(modifier = Modifier.fillMaxSize().safeContentPadding()) {
            Column(modifier = Modifier.chromeBottom { headerBottom = it }) {
                Heading2("Discover", size = 26, color = Wood, modifier = Modifier.padding(horizontal = 24.dp).padding(top = 16.dp))
                Spacer(Modifier.height(14.dp))
                SegmentedControl(
                    labels = DiscoverMode.entries.map { it.label },
                    selectedIndex = mode.ordinal,
                    onSelect = { index ->
                        val next = DiscoverMode.entries[index]
                        if (next != mode) {
                            haptics.selection()
                            expandedSection = ""
                            vm.setMode(next)
                        }
                    },
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
            Spacer(Modifier.height(14.dp))
            if (mode == DiscoverMode.Feedback) {
                feedVm?.let {
                    FeedbackFeedPanel(
                        vm = it,
                        onOpenStudio = { slug -> onOpenStudio(slug, "feed", null) },
                        bottomPadding = 24.dp + LocalFloatingBarInset.current,
                    )
                }
            } else when (val s = state) {
                DiscoverUiState.Loading -> SkeletonList()
                is DiscoverUiState.Error -> Unit // drawn full screen above
                is DiscoverUiState.Success -> {
                    FilterControls(
                        state = s,
                        expandedSection = expandedSection,
                        onExpandedSectionChange = { expandedSection = it },
                        onRemoveCategory = { haptics.toggle(false); vm.toggleCategory(it) },
                        onRemoveNeighborhood = { haptics.toggle(false); vm.toggleNeighborhood(it) },
                    )
                    Spacer(Modifier.height(12.dp))
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val popoverMaxHeight = maxHeight * POPOVER_HEIGHT_FRACTION
                        if (mode == DiscoverMode.Map) {
                            MapPanel(
                                state = s,
                                camera = vm.mapCamera,
                                selectedPinId = selectedPinId,
                                onPinTapped = { haptics.selection(); vm.selectPin(it) },
                                onMapTapped = vm::clearPin,
                                onOpenStudio = { pin -> onOpenStudio(pin.brandSlug, "map", pin.locationId) },
                                onClearFilters = vm::clearFilters,
                                onCardCover = { pinCardCover = it },
                            )
                        } else {
                            ArcanaPullToRefreshBox(
                                isRefreshing = isRefreshing,
                                onRefresh = vm::refresh,
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                StudioList(
                                    state = s,
                                    onOpenStudio = { slug -> onOpenStudio(slug, "directory", null) },
                                    onClearFilters = vm::clearFilters,
                                )
                            }
                        }
                        FilterPopover(
                            state = s,
                            expandedSection = expandedSection,
                            onExpandedSectionChange = { expandedSection = it },
                            popoverMaxHeight = popoverMaxHeight,
                            onToggleCategory = { slug ->
                                haptics.toggle(slug !in s.selectedCategories)
                                vm.toggleCategory(slug)
                            },
                            onToggleNeighborhood = { name ->
                                haptics.toggle(name !in s.selectedNeighborhoods)
                                vm.toggleNeighborhood(name)
                            },
                        )
                    }
                }
            }
        }
        val failed = if (mode == DiscoverMode.Feedback) feedRefreshFailed else refreshFailed
        TransientSurface(
            visible = failed,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .safeHorizontalPadding()
                .padding(bottom = 16.dp + LocalFloatingBarInset.current + if (mode == DiscoverMode.Map) pinCardCover else 0.dp),
        ) {
            ErrorSnackbar(
                text = ErrorCopy.REFRESH_FAILED,
                onRetry = {
                    if (mode == DiscoverMode.Feedback) {
                        feedVm?.dismissRefreshFailed()
                        feedVm?.refresh()
                    } else {
                        vm.dismissRefreshFailed()
                        vm.refresh()
                    }
                },
                onDismiss = { if (mode == DiscoverMode.Feedback) feedVm?.dismissRefreshFailed() else vm.dismissRefreshFailed() },
            )
        }
    }
}

// ── Map ───────────────────────────────────────────────────────────────────────

/** The studios as pins, edge to edge under the filters, with the selected pin's card over it. */
@Composable
private fun MapPanel(
    state: DiscoverUiState.Success,
    camera: MapCameraMemory,
    selectedPinId: Int?,
    onPinTapped: (Int) -> Unit,
    onMapTapped: () -> Unit,
    onOpenStudio: (DiscoverPin) -> Unit,
    onClearFilters: () -> Unit,
    onCardCover: (Dp) -> Unit,
) {
    val selected = state.pins.firstOrNull { it.locationId == selectedPinId }
    // The card outlives the selection by its exit animation, still showing the pin it had.
    var shown by remember { mutableStateOf(selected) }
    if (selected != null) shown = selected
    val barInset = LocalFloatingBarInset.current
    var cardHeight by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    val cardCover = if (selected != null) cardHeight + PIN_CARD_GAP else 0.dp
    LaunchedEffect(cardCover) { onCardCover(cardCover) }
    Box(modifier = Modifier.fillMaxSize()) {
        StudioMap(
            pins = state.pins,
            selectedPinId = selectedPinId,
            pinsEpoch = state.pinsEpoch,
            focusEpoch = state.focusEpoch,
            camera = camera,
            onPinTapped = onPinTapped,
            onMapTapped = onMapTapped,
            modifier = Modifier.fillMaxSize(),
            bottomInset = barInset + cardCover,
        )
        // The map keeps the atmosphere's edge: one rule where the chrome ends.
        Box(Modifier.fillMaxWidth().height(1.dp).background(MossLight.copy(alpha = 0.42f)))
        if (state.pins.isEmpty()) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .cardShadow(ArcanaShapes.Card)
                    .clip(ArcanaShapes.Card)
                    .background(Surface)
                    .border(1.dp, Mist, ArcanaShapes.Card)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BodyText(if (state.studios.isEmpty()) "No studios match those filters." else "Nothing to pin for those filters.", size = 14, color = Ink)
                Spacer(Modifier.height(8.dp))
                TextLink(label = "Clear filters", onClick = onClearFilters, color = Moss, underline = false)
            }
        }
        AnimatedVisibility(
            visible = selected != null,
            enter = slideInVertically(tween(Dur.Medium)) { it / 2 } + fadeIn(tween(Dur.Short)),
            exit = slideOutVertically(tween(Dur.Short)) { it / 2 } + fadeOut(tween(Dur.Short)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp)
                .padding(bottom = barInset + PIN_CARD_GAP),
        ) {
            shown?.let { pin ->
                PinCard(
                    pin = pin,
                    onClick = { onOpenStudio(pin) },
                    modifier = Modifier.onSizeChanged { cardHeight = with(density) { it.height.toDp() } },
                )
            }
        }
    }
}

/** The selected pin, as the list's studio row on a lifted card: the card is the control. */
@Composable
private fun PinCard(pin: DiscoverPin, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val source = remember { MutableInteractionSource() }
    val color = studioColorFor(pin.primaryColor)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .pressable(source, pressedScale = 0.99f)
            .cardShadow(ArcanaShapes.Card)
            .clip(ArcanaShapes.Card)
            .background(Surface)
            .border(1.dp, Mist, ArcanaShapes.Card)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.14f))
                .border(1.5.dp, color.copy(alpha = 0.33f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            CircleMonogram(text = pin.monogram, fontSize = 15, color = color)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            BodyText(text = pin.brandName, size = 16, color = Wood, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Caption(text = pinPlaceLine(pin), size = 12, color = Ash, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (pin.address.isNotBlank()) {
                Caption(text = pin.address, size = 12, color = Ash, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        // decorative — the card is the control and the name labels it.
        StrokeIcon(icon = ArcanaIcons.ChevronRight, size = 16.dp, tint = Ash2)
    }
}

// ── Filters ───────────────────────────────────────────────────────────────────

@Composable
private fun FilterControls(
    state: DiscoverUiState.Success,
    expandedSection: String,
    onExpandedSectionChange: (String) -> Unit,
    onRemoveCategory: (String) -> Unit,
    onRemoveNeighborhood: (String) -> Unit,
) {
    val categoryNames = state.categories.associate { it.slug to it.name }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterPill(
                label = "MODALITIES",
                active = state.selectedCategories.isNotEmpty(),
                modifier = Modifier.weight(1f),
                onClick = {
                    onExpandedSectionChange(if (expandedSection == SECTION_MODALITIES) "" else SECTION_MODALITIES)
                },
            )
            FilterPill(
                label = "NEIGHBORHOODS",
                active = state.selectedNeighborhoods.isNotEmpty(),
                modifier = Modifier.weight(1f),
                onClick = {
                    onExpandedSectionChange(if (expandedSection == SECTION_NEIGHBORHOODS) "" else SECTION_NEIGHBORHOODS)
                },
            )
        }
        if (state.selectedCategories.isNotEmpty() || state.selectedNeighborhoods.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            FlowChipRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                state.selectedCategories.forEach { slug ->
                    FilterChip(label = categoryNames[slug] ?: slug, onRemove = { onRemoveCategory(slug) })
                }
                state.selectedNeighborhoods.forEach { name ->
                    FilterChip(label = name, onRemove = { onRemoveNeighborhood(name) })
                }
            }
        }
    }
}

/** The Book tab's popover card, anchored under the pills over the list. */
@Composable
private fun BoxScope.FilterPopover(
    state: DiscoverUiState.Success,
    expandedSection: String,
    onExpandedSectionChange: (String) -> Unit,
    popoverMaxHeight: androidx.compose.ui.unit.Dp,
    onToggleCategory: (String) -> Unit,
    onToggleNeighborhood: (String) -> Unit,
) {
    val catcherSource = remember { MutableInteractionSource() }
    if (expandedSection != "") {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(interactionSource = catcherSource, indication = null) { onExpandedSectionChange("") },
        )
    }
    Box(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)) {
        AnimatedVisibility(
            visible = expandedSection == SECTION_MODALITIES,
            enter = filterPanelEnter,
            exit = filterPanelExit,
        ) {
            FloatingFilterPanel(maxHeight = popoverMaxHeight, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                state.categories.forEach { category ->
                    StudioLocationRow(
                        label = category.name,
                        checked = category.slug in state.selectedCategories,
                        implied = false,
                        onTap = { onToggleCategory(category.slug) },
                    )
                }
                FilterDoneButton(
                    onClick = { onExpandedSectionChange("") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        }
        AnimatedVisibility(
            visible = expandedSection == SECTION_NEIGHBORHOODS,
            enter = filterPanelEnter,
            exit = filterPanelExit,
        ) {
            FloatingFilterPanel(maxHeight = popoverMaxHeight, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                state.neighborhoods.forEach { name ->
                    StudioLocationRow(
                        label = name,
                        checked = name in state.selectedNeighborhoods,
                        implied = false,
                        onTap = { onToggleNeighborhood(name) },
                    )
                }
                FilterDoneButton(
                    onClick = { onExpandedSectionChange("") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        }
    }
}

// ── List ──────────────────────────────────────────────────────────────────────

@Composable
private fun StudioList(
    state: DiscoverUiState.Success,
    onOpenStudio: (String) -> Unit,
    onClearFilters: () -> Unit,
) {
    val dim by animateFloatAsState(
        targetValue = if (state.refreshingFilters) 0.5f else 1f,
        animationSpec = tween(Dur.Short),
        label = "discoverDim",
    )
    val listState = rememberLazyListState()
    Box(modifier = Modifier.fillMaxSize()) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().graphicsLayer { alpha = dim },
        contentPadding = PaddingValues(bottom = 24.dp + LocalFloatingBarInset.current),
    ) {
        if (state.studios.isEmpty()) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    BodyText("No studios match those filters.", size = 16, color = Ink)
                    Spacer(Modifier.height(12.dp))
                    TextLink(label = "Clear filters", onClick = onClearFilters, color = Moss, underline = false)
                }
            }
            return@LazyColumn
        }
        items(state.studios, key = { it.slug }) { studio ->
            StudioCard(studio = studio, onClick = { onOpenStudio(studio.slug) })
        }
    }
    JumpToTop(listState)
    }
}

@Composable
private fun StudioCard(studio: DiscoverStudioDto, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val color = studioColorFor(studio.primaryColor)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressable(source, pressedScale = 0.99f)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.14f))
                .border(1.5.dp, color.copy(alpha = 0.33f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            CircleMonogram(text = monogramFor(studio.name), fontSize = 16, color = color)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BodyText(text = studio.name, size = 16, color = Wood, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (studio.categories.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    studio.categories.take(MAX_CARD_CATEGORIES).forEach { category ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            // decorative — the category name follows.
                            StrokeIcon(icon = CategoryIcons.iconFor(category.slug), size = 12.dp, tint = Charcoal)
                            Overline(text = category.name, size = 10, color = Charcoal)
                        }
                    }
                }
            }
            Caption(text = placeLine(studio), size = 12, color = Charcoal, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        // decorative — the card is the control and the name labels it.
        StrokeIcon(icon = ArcanaIcons.ChevronRight, size = 16.dp, tint = Ash2)
    }
}

@Composable
private fun SkeletonList() {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ShimmerBox(modifier = Modifier.weight(1f).height(40.dp), shape = ArcanaShapes.Pill)
            ShimmerBox(modifier = Modifier.weight(1f).height(40.dp), shape = ArcanaShapes.Pill)
        }
        Spacer(Modifier.height(16.dp))
        repeat(6) {
            ShimmerBox(
                modifier = Modifier.padding(vertical = 6.dp).fillMaxWidth().height(72.dp),
                shape = ArcanaShapes.Chip,
            )
        }
    }
}

/** "Flatiron, Tribeca · 2 locations"; neighborhoods beyond three fold into "+N". */
internal fun placeLine(studio: DiscoverStudioDto): String {
    val hoods = studio.neighborhoods.take(MAX_CARD_NEIGHBORHOODS).joinToString(", ") +
        (studio.neighborhoods.size - MAX_CARD_NEIGHBORHOODS).takeIf { it > 0 }?.let { " +$it" }.orEmpty()
    val count = when (studio.locationCount) {
        1 -> "1 location"
        else -> "${studio.locationCount} locations"
    }
    return if (hoods.isBlank()) count else "$hoods · $count"
}

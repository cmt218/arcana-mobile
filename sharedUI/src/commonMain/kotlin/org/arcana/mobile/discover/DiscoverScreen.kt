package org.arcana.mobile.discover

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.arcana.mobile.data.DiscoverStudioDto
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
import org.arcana.mobile.ui.ShimmerBox
import org.arcana.mobile.ui.StrokeIcon
import org.arcana.mobile.ui.StudioLocationRow
import org.arcana.mobile.ui.TextLink
import org.arcana.mobile.ui.TransientSurface
import org.arcana.mobile.ui.filterPanelEnter
import org.arcana.mobile.ui.filterPanelExit
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

/** The Discover tab: every studio, A to Z, narrowed by modality and neighborhood. */
@Composable
fun DiscoverScreen(onOpenStudio: (String) -> Unit) {
    val vm = koinViewModel<DiscoverViewModel>()
    LaunchedEffect(Unit) { vm.onOpened() }
    val state by vm.uiState.collectAsState()
    val isRefreshing by vm.isRefreshing.collectAsState()
    val refreshFailed by vm.refreshFailed.collectAsState()
    val retrying by vm.retrying.collectAsState()
    var expandedSection by remember { mutableStateOf("") }
    val haptics = rememberHaptics()

    Box(modifier = Modifier.fillMaxSize()) {
        Atmosphere()
        Column(modifier = Modifier.fillMaxSize().safeContentPadding()) {
            Heading2("Discover", size = 26, color = Wood, modifier = Modifier.padding(horizontal = 24.dp).padding(top = 16.dp))
            Spacer(Modifier.height(16.dp))
            when (val s = state) {
                DiscoverUiState.Loading -> SkeletonList()
                is DiscoverUiState.Error -> FullScreenError(type = s.type, onRetry = vm::retry, retrying = retrying)
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
                        ArcanaPullToRefreshBox(
                            isRefreshing = isRefreshing,
                            onRefresh = vm::refresh,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            StudioList(state = s, onOpenStudio = onOpenStudio, onClearFilters = vm::clearFilters)
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
        TransientSurface(
            visible = refreshFailed,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .safeHorizontalPadding()
                .padding(bottom = 16.dp + LocalFloatingBarInset.current),
        ) {
            ErrorSnackbar(
                text = ErrorCopy.REFRESH_FAILED,
                onRetry = {
                    vm.dismissRefreshFailed()
                    vm.refresh()
                },
                onDismiss = vm::dismissRefreshFailed,
            )
        }
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
    LazyColumn(
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
                            StrokeIcon(icon = CategoryIcons.iconFor(category.slug), size = 12.dp, tint = Ash)
                            Overline(text = category.name, size = 10, color = Ash)
                        }
                    }
                }
            }
            Caption(text = placeLine(studio), size = 12, color = Ash, maxLines = 1, overflow = TextOverflow.Ellipsis)
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

/** Up to two initials from the name's first letters, ignoring punctuation. */
internal fun monogramFor(name: String): String {
    val words = name.split(' ').map { w -> w.trimStart { !it.isLetterOrDigit() } }.filter { it.isNotEmpty() }
    val initials = words.take(2).map { it.first().uppercaseChar() }.joinToString("")
    return initials.ifEmpty { "?" }
}

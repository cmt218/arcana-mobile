package org.arcana.mobile.studios

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import org.arcana.mobile.theme.Ash
import org.arcana.mobile.theme.Ink
import org.arcana.mobile.theme.Mist
import org.arcana.mobile.theme.Moss
import org.arcana.mobile.theme.Stone
import org.arcana.mobile.theme.Warning
import org.arcana.mobile.ui.AccentText
import org.arcana.mobile.ui.ArcanaIcons
import org.arcana.mobile.ui.Caption
import org.arcana.mobile.ui.Display
import org.arcana.mobile.ui.DotMatrixLoader
import org.arcana.mobile.ui.FullScreenError
import org.arcana.mobile.ui.IconCircle
import org.arcana.mobile.ui.Overline
import org.arcana.mobile.ui.PrimaryCta
import org.arcana.mobile.ui.StrokeIcon
import org.arcana.mobile.ui.StudioAccordionCard
import org.arcana.mobile.ui.StudioLocationRow
import org.arcana.mobile.ui.studioLocationLabel
import org.arcana.mobile.ui.safeBottomBarPadding
import org.arcana.mobile.ui.safeContentPadding
import org.koin.compose.viewmodel.koinViewModel

/**
 * Favorites manager — favorite whole Studios or individual locations.
 * Launched from Profile ("Manage") and the Schedule empty-favorites prompt.
 * [onClose] dismisses it; a successful save also closes.
 */
@Composable
fun StudioSelectionScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = koinViewModel<StudioSelectionViewModel>()
    val state by viewModel.uiState.collectAsState()
    val retrying by viewModel.retrying.collectAsState()

    val saved = (state as? StudioSelectionUiState.Ready)?.saved == true
    LaunchedEffect(saved) { if (saved) onClose() }

    Box(modifier = modifier.fillMaxSize().background(Stone)) {
        Column(
            modifier = Modifier.fillMaxSize().safeContentPadding(),
        ) {
            // Top bar — close only.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconCircle(
                    icon = ArcanaIcons.Close,
                    diameter = 36,
                    iconSize = 16,
                    borderColor = Mist,
                    contentColor = Ink,
                    onClick = onClose,
                    contentDescription = "Close studio selection",
                )
            }

            when (val s = state) {
                is StudioSelectionUiState.Loading -> Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    DotMatrixLoader()
                }
                is StudioSelectionUiState.Error -> FullScreenError(
                    type = s.type,
                    onRetry = viewModel::retry,
                    retrying = retrying,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
                is StudioSelectionUiState.Ready -> ReadyContent(
                    state = s,
                    viewModel = viewModel,
                )
            }
        }

        // Sticky CTA — fades the bottom of the scroll under the bar.
        if (state is StudioSelectionUiState.Ready) {
            val s = state as StudioSelectionUiState.Ready
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(listOf(Color.Transparent, Stone, Stone))
                    )
                    .safeBottomBarPadding()
                    .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val saveError = s.error
                if (saveError != null) {
                    Caption(text = saveError, size = 12, color = Warning, maxLines = 3)
                }
                PrimaryCta(
                    label = if (s.saving) "Saving…" else "Save favorites",
                    enabled = !s.saving,
                    onClick = viewModel::save,
                )
            }
        }
    }
}

@Composable
private fun ReadyContent(
    state: StudioSelectionUiState.Ready,
    viewModel: StudioSelectionViewModel,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 128.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // Title
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Overline(text = "Your favorites", color = Moss)
            Display(text = "Make it\nyours.", size = 48, color = Ink)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AccentText(
                    text = "Save the places you keep coming back to.",
                    size = 18,
                    color = Ash,
                )
                AccentText(text = "Change anytime.", size = 18, color = Moss)
            }
        }

        // Studio list
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.studios.forEach { studio ->
                val chosen = studio.slug in state.selectedStudioSlugs
                val expanded = studio.slug in state.expandedStudioSlugs
                // Expanding a card near the bottom of the screen would reveal
                // its locations underneath the sticky Save bar with no visible
                // change. On expansion, ask the scroller to reveal the card +
                // its locations, extending the request by the CTA's height so
                // they land above the bar (the overlay isn't a real inset, so
                // the scroll container doesn't know about it). Anchoring on
                // card + locations together means a taller-than-viewport list
                // pins the card header to the top instead of scrolling past it.
                // Already-visible expansions are a no-op — bringIntoView only
                // scrolls the minimum needed.
                val bringIntoViewRequester = remember { BringIntoViewRequester() }
                var groupSize by remember { mutableStateOf(IntSize.Zero) }
                val ctaAllowancePx = with(LocalDensity.current) { STICKY_CTA_REVEAL_ALLOWANCE.toPx() }
                Column(
                    modifier = Modifier
                        .bringIntoViewRequester(bringIntoViewRequester)
                        .onSizeChanged { groupSize = it },
                ) {
                    StudioAccordionCard(
                        name = studio.name,
                        locationCount = studio.locations.size,
                        chosen = chosen,
                        expanded = expanded,
                        selectedLocationCount = studio.locations.count { it.id in state.selectedLocationIds },
                        onToggle = { viewModel.toggleStudio(studio.slug) },
                        onToggleExpanded = { viewModel.toggleExpanded(studio.slug) },
                    )
                    if (expanded) {
                        Column(
                            modifier = Modifier.padding(start = 32.dp, top = 12.dp, bottom = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            studio.locations.forEach { location ->
                                StudioLocationRow(
                                    label = studioLocationLabel(studio.name, location.name),
                                    checked = location.id in state.selectedLocationIds || chosen,
                                    implied = chosen,
                                    onTap = { viewModel.toggleLocation(studio.slug, location.id) },
                                )
                            }
                        }
                    }
                }
                LaunchedEffect(expanded, groupSize) {
                    if (expanded && groupSize != IntSize.Zero) {
                        bringIntoViewRequester.bringIntoView(
                            Rect(
                                0f,
                                0f,
                                groupSize.width.toFloat(),
                                groupSize.height + ctaAllowancePx,
                            )
                        )
                    }
                }
            }
        }
    }
}

/** How far past an expanded card's bottom edge the reveal-scroll reaches —
 *  covers the sticky Save bar overlay. Matches the scroll content's 128.dp
 *  bottom padding, so the request never overshoots the scroll range. */
private val STICKY_CTA_REVEAL_ALLOWANCE = 128.dp


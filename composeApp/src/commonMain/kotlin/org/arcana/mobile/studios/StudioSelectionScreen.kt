package org.arcana.mobile.studios

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.arcana.mobile.data.StudioDto
import org.arcana.mobile.theme.Arcana
import org.arcana.mobile.theme.Ash
import org.arcana.mobile.theme.Ink
import org.arcana.mobile.theme.Lime
import org.arcana.mobile.theme.Mist
import org.arcana.mobile.theme.Moss
import org.arcana.mobile.theme.Paper
import org.arcana.mobile.theme.Stone
import org.arcana.mobile.theme.StoneAlpha55
import org.arcana.mobile.theme.Warning
import org.arcana.mobile.ui.AccentText
import org.arcana.mobile.ui.ArcanaIcons
import org.arcana.mobile.ui.BodyText
import org.arcana.mobile.ui.Caption
import org.arcana.mobile.ui.Display
import org.arcana.mobile.ui.DotField
import org.arcana.mobile.ui.DotMatrixLoader
import org.arcana.mobile.ui.Heading2
import org.arcana.mobile.ui.IconCircle
import org.arcana.mobile.ui.Overline
import org.arcana.mobile.ui.PrimaryCta
import org.arcana.mobile.ui.StrokeIcon
import org.arcana.mobile.ui.safeBottomBarPadding
import org.arcana.mobile.ui.safeContentPadding
import org.koin.compose.viewmodel.koinViewModel

/**
 * Favorites manager — favorite whole Partners or individual locations.
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
                )
            }

            when (val s = state) {
                is StudioSelectionUiState.Loading -> Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    DotMatrixLoader()
                }
                is StudioSelectionUiState.Error -> ErrorBlock(
                    message = s.message,
                    onRetry = viewModel::retry,
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
                if (s.error != null) {
                    Caption(text = s.error, size = 12, color = Warning)
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

        // Partner list
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
                    PartnerCard(
                        studio = studio,
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
                                LocationRow(
                                    label = locationLabel(studio.name, location.name),
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

@Composable
private fun ErrorBlock(message: String, onRetry: () -> Unit) {
    Column(modifier = Modifier.padding(24.dp)) {
        Heading2(text = "Couldn't load Partners", size = 22, color = Ink)
        Spacer(Modifier.height(8.dp))
        BodyText(text = message, size = 14, color = Ash)
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(Ink)
                .clickable(onClick = onRetry)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Overline(text = "RETRY", size = 12, color = Stone)
        }
    }
}

/** Display-friendly location label: the brand prefix is stripped so a row
 *  reads "Williamsburg", not "YO BK Williamsburg" under the YO BK card.
 *  Mirrors `LocationBriefDto.shortLabel()` in ScheduleViewModel. */
private fun locationLabel(studioName: String, locationName: String): String {
    val raw = locationName.removePrefix(studioName).trim()
        .removePrefix("·").trim()
        .removePrefix("-").trim()
    return raw.ifEmpty { locationName }
}

@Composable
private fun PartnerCard(
    studio: StudioDto,
    chosen: Boolean,
    expanded: Boolean,
    selectedLocationCount: Int,
    onToggle: () -> Unit,
    onToggleExpanded: () -> Unit,
) {
    // Some-but-not-all selection: individual locations are favorited without
    // the whole Partner. Surfaced on the collapsed card via a partial check
    // ring + a "N of M locations" overline so the selection isn't invisible.
    val partial = !chosen && selectedLocationCount > 0
    // Tap model: the card body expands/collapses the location list; ONLY the
    // check circle selects/deselects the whole Partner. Selection is the
    // higher-consequence action, so it gets the deliberate, smaller target.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (chosen) Ink else Paper)
            .border(1.dp, if (chosen) Ink else Mist, RoundedCornerShape(16.dp))
            .clickable(onClick = onToggleExpanded),
    ) {
        if (chosen) {
            DotField(modifier = Modifier.matchParentSize(), color = Lime, alpha = 0.08f, spacing = 14)
        }
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Check / empty marker — filled check for whole-Partner selection,
            // Lime ring + dot for a partial (some-locations) selection. Its
            // own 40dp tap target, separate from the card's expand/collapse.
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onToggle),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .then(
                            when {
                                chosen -> Modifier.background(Lime)
                                partial -> Modifier.border(2.dp, Lime, CircleShape)
                                else -> Modifier.border(2.dp, Mist, CircleShape)
                            }
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (chosen) {
                        StrokeIcon(ArcanaIcons.Check, size = 18.dp, tint = Ink)
                    } else if (partial) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(Lime))
                    }
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = studio.name.uppercase(),
                    style = TextStyle(
                        fontFamily = Arcana.fonts.display,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        letterSpacing = (-0.02).em,
                        color = if (chosen) Stone else Ink,
                    ),
                )
                Spacer(Modifier.height(4.dp))
                val count = studio.locations.size
                val locationsWord = if (count == 1) "location" else "locations"
                Overline(
                    text = if (partial) {
                        "$selectedLocationCount of $count $locationsWord"
                    } else {
                        "$count $locationsWord"
                    },
                    size = 10,
                    color = when {
                        chosen -> StoneAlpha55
                        partial -> Moss
                        else -> Ash
                    },
                )
            }
            // Expansion chevron — visual affordance for the card's tap action
            // (the whole card body expands/collapses; this mirrors it).
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onToggleExpanded),
                contentAlignment = Alignment.Center,
            ) {
                StrokeIcon(
                    icon = ArcanaIcons.ChevronDown,
                    size = 18.dp,
                    tint = if (chosen) Lime else Moss,
                    modifier = Modifier.rotate(if (expanded) 180f else 0f),
                )
            }
        }
    }
}

/**
 * Expanded location row. [implied] means the whole Partner is selected — the
 * check renders at reduced opacity to hint that tapping narrows to just
 * this location.
 */
@Composable
private fun LocationRow(
    label: String,
    checked: Boolean,
    implied: Boolean,
    onTap: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onTap)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val checkTint = if (implied) Lime.copy(alpha = 0.45f) else Lime
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .then(
                    if (checked) Modifier.background(checkTint)
                    else Modifier.border(2.dp, Mist, CircleShape)
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                StrokeIcon(ArcanaIcons.Check, size = 12.dp, tint = Ink)
            }
        }
        BodyText(text = label, size = 14, color = Ink)
    }
}

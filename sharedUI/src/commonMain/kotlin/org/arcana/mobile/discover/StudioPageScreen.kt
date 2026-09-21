@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package org.arcana.mobile.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.arcana.mobile.booking.bookingCancelCopy
import org.arcana.mobile.data.StudioClassTypeDto
import org.arcana.mobile.data.StudioInstructorDto
import org.arcana.mobile.data.StudioPageDto
import org.arcana.mobile.data.StudioPageLocationDto
import org.arcana.mobile.schedule.ScrollJumpChevron
import org.arcana.mobile.theme.*
import org.arcana.mobile.ui.AccentText
import org.arcana.mobile.ui.AddressRow
import org.arcana.mobile.ui.ArcanaIcons
import org.arcana.mobile.ui.ArcanaSheet
import org.arcana.mobile.ui.InstructorSheet
import org.arcana.mobile.review.FeedbackScope
import org.arcana.mobile.ui.BodyText
import org.arcana.mobile.ui.Caption
import org.arcana.mobile.ui.CategoryIcons
import org.arcana.mobile.ui.CircleMonogram
import org.arcana.mobile.ui.FullScreenError
import org.arcana.mobile.ui.Heading2
import org.arcana.mobile.ui.Heading3
import org.arcana.mobile.ui.IconCircle
import org.arcana.mobile.ui.Overline
import org.arcana.mobile.ui.PrimaryCta
import org.arcana.mobile.ui.ShimmerBox
import org.arcana.mobile.ui.StrokeIcon
import org.arcana.mobile.ui.StudioLocationRow
import org.arcana.mobile.ui.TextLink
import org.arcana.mobile.ui.pressable
import org.arcana.mobile.ui.rememberHaptics
import org.arcana.mobile.ui.safeContentPadding
import org.arcana.mobile.ui.studioColorFor
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.rotate
import org.arcana.mobile.review.MembersSayRow
import org.arcana.mobile.theme.Dur
import org.arcana.mobile.theme.Mist
import org.arcana.mobile.theme.Surface
import org.arcana.mobile.ui.FilterPill
import org.arcana.mobile.ui.cardShadow
import org.arcana.mobile.ui.SectionHeading

private const val CLASS_DESCRIPTION_COLLAPSED_LINES = 2
private val CARD_GAP = 10.dp
private const val SECTION_PREVIEW_COUNT = 5
private const val LOCATION_LIST_HEIGHT_FRACTION = 0.5f
private val CTA_CLEARANCE = 120.dp

/** A brand's page from Discover: header, about, good to know, classes,
 *  instructors, locations, and a sticky Book button. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioPageScreen(
    brandSlug: String,
    source: String,
    /** The location the member came from (a map pin), if any. */
    fromLocationId: Int? = null,
    onClose: () -> Unit,
    onSeeSchedule: () -> Unit,
    /** Opens the member feedback feed; the string is the telemetry source. */
    onOpenFeedback: (FeedbackScope, String) -> Unit = { _, _ -> },
    /** Shows a location (by id) on Discover's map; null where there is no way there. */
    onShowOnMap: ((Int) -> Unit)? = null,
) {
    val vm = koinViewModel<StudioPageViewModel> { parametersOf(brandSlug, source, fromLocationId ?: 0) }
    val state by vm.uiState.collectAsState()
    val retrying by vm.retrying.collectAsState()
    val haptics = rememberHaptics()
    var favoritesSheetOpen by remember { mutableStateOf(false) }
    var instructor by remember { mutableStateOf<StudioInstructorDto?>(null) }
    // The back gesture pops exactly like the X; an open sheet owns back itself.
    BackHandler(enabled = !favoritesSheetOpen && instructor == null) { onClose() }

    Box(modifier = Modifier.fillMaxSize()) {
        Atmosphere()
        when (val s = state) {
            StudioPageUiState.Loading -> Column(Modifier.fillMaxSize().safeContentPadding().padding(20.dp)) {
                CloseButton(onClose)
                Spacer(Modifier.height(24.dp))
                ShimmerBox(Modifier.fillMaxWidth().height(96.dp), shape = ArcanaShapes.Card)
                Spacer(Modifier.height(24.dp))
                repeat(3) {
                    ShimmerBox(Modifier.padding(vertical = 6.dp).fillMaxWidth().height(64.dp), shape = ArcanaShapes.Chip)
                }
            }
            is StudioPageUiState.Error -> Box(Modifier.fillMaxSize()) {
                FullScreenError(type = s.type, onRetry = vm::retry, retrying = retrying)
                Box(Modifier.safeContentPadding().padding(20.dp)) { CloseButton(onClose) }
            }
            is StudioPageUiState.Success -> {
                LaunchedEffect(s.isFavorite) { if (s.isFavorite) favoritesSheetOpen = false }
                StudioPageContent(
                    state = s,
                    onClose = onClose,
                    onFavorite = {
                        when (vm.onFavoriteTapped()) {
                            FavoriteTapResult.Added -> haptics.toggle(true)
                            FavoriteTapResult.Sheet -> favoritesSheetOpen = true
                            FavoriteTapResult.NoOp -> Unit
                        }
                    },
                    onInstructor = { picked ->
                        vm.onInstructorTapped(picked.profileId)
                        instructor = picked
                    },
                    onOpenFeedback = onOpenFeedback,
                    onShowOnMap = onShowOnMap,
                )
                // Floats over the list like class detail's CTA: no fade behind it.
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                ) {
                    PrimaryCta(
                        label = "Book",
                        onClick = {
                            haptics.selection()
                            vm.requestSchedule()
                            onSeeSchedule()
                        },
                    )
                }
                if (favoritesSheetOpen) {
                    FavoriteLocationsSheet(
                        state = s,
                        onSave = { picks ->
                            haptics.toggle(true)
                            vm.saveFavorites(picks)
                        },
                        onDismiss = { favoritesSheetOpen = false },
                    )
                }
            }
        }
    }
    instructor?.let { picked ->
        InstructorSheet(
            name = picked.name,
            bio = picked.bio,
            reviewCount = picked.reviewCount,
            onSeeFeedback = {
                instructor = null
                onOpenFeedback(FeedbackScope.instructor(picked.profileId, picked.name), "instructor_sheet")
            },
            onDismiss = { instructor = null },
        )
    }
}

@Composable
private fun CloseButton(onClose: () -> Unit) {
    IconCircle(
        icon = ArcanaIcons.Close,
        diameter = 38,
        iconSize = 18,
        background = Surface,
        borderColor = Outline,
        contentColor = Ink,
        onClick = onClose,
        contentDescription = "Close studio",
    )
}

@Composable
private fun StudioPageContent(
    state: StudioPageUiState.Success,
    onClose: () -> Unit,
    onFavorite: () -> Unit,
    onInstructor: (StudioInstructorDto) -> Unit,
    onOpenFeedback: (FeedbackScope, String) -> Unit,
    onShowOnMap: ((Int) -> Unit)?,
) {
    val page = state.page
    val color = studioColorFor(page.primaryColor)
    var allClasses by remember(page.slug) { mutableStateOf(false) }
    var allInstructors by remember(page.slug) { mutableStateOf(false) }
    val onToggleAllClasses = { allClasses = !allClasses }
    val onToggleAllInstructors = { allInstructors = !allInstructors }
    LazyColumn(
        modifier = Modifier.fillMaxSize().safeContentPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = CTA_CLEARANCE),
    ) {
        item { CloseButton(onClose) }
        item {
            Spacer(Modifier.height(20.dp))
            Header(page = page, color = color, state = state, onFavorite = onFavorite)
        }
        // Only once someone has said something. No inline averages.
        if (page.reviewCount > 0) {
            item {
                Spacer(Modifier.height(20.dp))
                MembersSayRow(
                    count = page.reviewCount,
                    onClick = { onOpenFeedback(FeedbackScope.brand(page.slug, page.name), "studio_page") },
                )
            }
        }
        // Reached from a map pin: that location leads, and the Book button opens on it.
        state.fromLocation?.let { location ->
            item(key = "from-location") {
                Section("This location")
                LocationRow(page = page, location = location, onOpenFeedback = onOpenFeedback, onShowOnMap = onShowOnMap)
            }
        }
        if (page.bio.isNotBlank()) {
            item {
                Section("About")
                BodyText(text = page.bio, size = 14, color = Graphite)
            }
        }
        val cutoff = page.cancellationCutoffMinutes
        if (page.goodToKnow.isNotBlank() || page.amenities.isNotEmpty() || cutoff != null) {
            item {
                Section("Good to know")
                if (page.goodToKnow.isNotBlank()) {
                    BodyText(text = page.goodToKnow, size = 14, color = Graphite)
                    Spacer(Modifier.height(12.dp))
                }
                if (page.amenities.isNotEmpty()) {
                    AmenityRow(page)
                    Spacer(Modifier.height(12.dp))
                }
                if (cutoff != null) {
                    Text(
                        text = bookingCancelCopy(cutoff),
                        style = TextStyle(fontFamily = Arcana.fonts.body, fontSize = 13.sp, color = Graphite),
                    )
                }
            }
        }
        if (page.classTypes.isNotEmpty()) {
            item { Section("Classes") }
            val shown = if (allClasses) page.classTypes else page.classTypes.take(SECTION_PREVIEW_COUNT)
            items(shown, key = { "class-${it.key}" }) { ClassTypeCard(it) }
            showAllItem("classes", page.classTypes.size, allClasses, onToggleAllClasses)
        }
        if (page.instructors.isNotEmpty()) {
            item { Section("Instructors") }
            val shown = if (allInstructors) page.instructors else page.instructors.take(SECTION_PREVIEW_COUNT)
            items(shown, key = { "instructor-${it.profileId}" }) { person ->
                InstructorRow(person = person, color = color, onClick = { onInstructor(person) })
            }
            showAllItem("instructors", page.instructors.size, allInstructors, onToggleAllInstructors)
        }
        if (page.locations.isNotEmpty()) {
            item { Section("Locations") }
            items(page.locations, key = { "location-${it.id}" }) { location ->
                LocationRow(page = page, location = location, onOpenFeedback = onOpenFeedback, onShowOnMap = onShowOnMap)
            }
        }
    }
}

@Composable
private fun LocationRow(
    page: StudioPageDto,
    location: StudioPageLocationDto,
    onOpenFeedback: (FeedbackScope, String) -> Unit,
    onShowOnMap: ((Int) -> Unit)?,
) {
    Column(modifier = Modifier.padding(bottom = CARD_GAP).pageCard().padding(horizontal = 16.dp, vertical = 14.dp)) {
        AddressRow(
            name = location.name,
            businessName = "${page.name} ${location.name}",
            address = location.address,
            latitude = location.latitude,
            longitude = location.longitude,
            surface = "studio_page",
            overline = location.neighborhood.takeIf { it.isNotBlank() },
            onShowOnMap = onShowOnMap?.takeIf { location.onMap }?.let { show -> { show(location.id) } },
        )
        if (location.reviewCount > 0) {
            Spacer(Modifier.height(12.dp))
            MembersSayRow(
                count = location.reviewCount,
                onClick = {
                    onOpenFeedback(
                        FeedbackScope.location(location.id, "${page.name} · ${location.name}"),
                        "studio_location",
                    )
                },
            )
        }
    }
}

/** "Show all 12 classes" under a section previewing its first five rows. */
private fun LazyListScope.showAllItem(noun: String, total: Int, expanded: Boolean, onToggle: () -> Unit) {
    if (total <= SECTION_PREVIEW_COUNT) return
    item(key = "show-all-$noun") {
        TextLink(
            label = if (expanded) "Show fewer" else "Show all $total $noun",
            onClick = onToggle,
            color = Moss,
            underline = false,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(32.dp))
    SectionHeading(title)
    Spacer(Modifier.height(16.dp))
}

/** The lifted card every tappable row on this page sits in. */
private fun Modifier.pageCard(): Modifier =
    fillMaxWidth()
        .cardShadow(ArcanaShapes.Card)
        .clip(ArcanaShapes.Card)
        .background(Surface)
        .border(1.dp, Mist, ArcanaShapes.Card)

@Composable
private fun Header(page: StudioPageDto, color: Color, state: StudioPageUiState.Success, onFavorite: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(ArcanaShapes.Card)
                .background(color.copy(alpha = 0.14f))
                .border(1.5.dp, color.copy(alpha = 0.33f), ArcanaShapes.Card),
            contentAlignment = Alignment.Center,
        ) {
            CircleMonogram(text = monogramFor(page.name), fontSize = 20, color = color)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Heading2(text = page.name, size = 22, color = Wood)
            if (page.categories.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    page.categories.take(3).forEach { category ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            // decorative — the category name follows.
                            StrokeIcon(icon = CategoryIcons.iconFor(category.slug), size = 12.dp, tint = Charcoal)
                            Overline(text = category.name, size = 10, color = Charcoal)
                        }
                    }
                }
            }
            Caption(
                text = placeLine(
                    org.arcana.mobile.data.DiscoverStudioDto(
                        slug = page.slug, name = page.name,
                        neighborhoods = page.neighborhoods, locationCount = page.locationCount,
                    ),
                ),
                size = 12, color = Charcoal, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
    }
    if (page.tagline.isNotBlank()) {
        Spacer(Modifier.height(16.dp))
        AccentText(text = page.tagline, size = 20, color = Ink)
    }
    Spacer(Modifier.height(16.dp))
    when {
        state.savingFavorites -> Caption("Saving…", size = 13, color = Charcoal)
        state.isFavorite -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            // decorative — the label names the state.
            StrokeIcon(icon = ArcanaIcons.Check, size = 14.dp, tint = Moss)
            Overline(text = "In your favorites", size = 11, color = Moss)
        }
        else -> FilterPill(
            label = "ADD TO FAVORITES",
            active = false,
            onClick = onFavorite,
            // decorative — the label names the action.
            leading = { StrokeIcon(icon = ArcanaIcons.Bookmark, size = 14.dp, tint = Moss) },
        )
    }
    state.favoritesError?.let {
        Spacer(Modifier.height(6.dp))
        Caption("Couldn't save your favorites. Try again.", size = 12, color = BurntNectar)
    }
}

@Composable
private fun AmenityRow(page: StudioPageDto) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        page.amenities.take(4).forEach { amenity ->
            Box(
                modifier = Modifier
                    .clip(ArcanaShapes.Pill)
                    .border(1.dp, Outline, ArcanaShapes.Pill)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Caption(text = amenity.label, size = 11, color = Ink)
            }
        }
    }
}

/** One class the studio runs. The chevron says there is more to read; a tap opens it in place. */
@Composable
private fun ClassTypeCard(classType: StudioClassTypeDto) {
    var expanded by rememberSaveable(classType.key) { mutableStateOf(false) }
    val source = remember { MutableInteractionSource() }
    val expandable = classType.description.isNotBlank()
    val turn by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(Dur.Short),
        label = "classCardChevron",
    )
    Column(
        modifier = Modifier
            .padding(bottom = CARD_GAP)
            .then(if (expandable) Modifier.pressable(source, pressedScale = 0.99f) else Modifier)
            .pageCard()
            .then(
                if (expandable) Modifier.clickable(
                    interactionSource = source,
                    indication = null,
                    onClickLabel = if (expanded) "Show less" else "Show more",
                ) { expanded = !expanded } else Modifier
            )
            .animateContentSize(tween(Dur.Short))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Heading3(text = classType.label, size = 16, color = Ink, modifier = Modifier.weight(1f))
            if (expandable) {
                // decorative — the card's click label says what a tap does.
                StrokeIcon(icon = ArcanaIcons.ChevronDown, size = 16.dp, tint = Moss, modifier = Modifier.rotate(turn))
            }
        }
        if (expandable) {
            Spacer(Modifier.height(6.dp))
            BodyText(
                text = classType.description,
                size = 13,
                color = Graphite,
                maxLines = if (expanded) Int.MAX_VALUE else CLASS_DESCRIPTION_COLLAPSED_LINES,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun InstructorRow(person: StudioInstructorDto, color: Color, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .padding(bottom = CARD_GAP)
            .pressable(source, pressedScale = 0.99f)
            .pageCard()
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(CircleShape).background(Mist2),
            contentAlignment = Alignment.Center,
        ) {
            CircleMonogram(text = monogramFor(person.name), fontSize = 14, color = color)
        }
        Heading3(text = person.name, size = 16, color = Ink, modifier = Modifier.weight(1f))
        // decorative — the row is the control and the name labels it.
        StrokeIcon(icon = ArcanaIcons.ChevronRight, size = 16.dp, tint = Moss)
    }
}

/** Multi-location brands: pick the locations to add. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FavoriteLocationsSheet(
    state: StudioPageUiState.Success,
    onSave: (Set<Int>) -> Unit,
    onDismiss: () -> Unit,
) {
    val locations = state.page.locations
    var picks by remember { mutableStateOf(state.favoriteLocationIds) }
    val allPicked = picks.size == locations.size
    ArcanaSheet(onDismissRequest = onDismiss) {
        BoxWithConstraints {
        val listMaxHeight = maxHeight * LOCATION_LIST_HEIGHT_FRACTION
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Heading3(text = "Add to favorites", size = 20, color = Ink)
            Spacer(Modifier.height(4.dp))
            Caption(text = "Pick the ${state.page.name} locations you want in your favorites.", size = 13, color = Ash, maxLines = 2)
            Spacer(Modifier.height(12.dp))
            // A fixed cap, not weight: a weighted list re-measures while the
            // sheet settles and the whole sheet jumps under the finger. The
            // list also swallows its own overscroll so a fling that reaches the
            // top never carries on into the sheet and flicks it shut; the
            // handle, the scrim and back still dismiss.
            val keepSheetStill = remember {
                object : NestedScrollConnection {
                    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource) = available
                    override suspend fun onPostFling(consumed: Velocity, available: Velocity) = available
                }
            }
            val scroll = rememberScrollState()
            val scrollScope = rememberCoroutineScope()
            Box {
                Column(
                    modifier = Modifier
                        .heightIn(max = listMaxHeight)
                        .nestedScroll(keepSheetStill)
                        .verticalScroll(scroll),
                ) {
                    StudioLocationRow(
                        label = "All locations",
                        checked = allPicked,
                        implied = false,
                        onTap = { picks = if (allPicked) emptySet() else locations.map { it.id }.toSet() },
                    )
                    locations.forEach { location ->
                        StudioLocationRow(
                            label = location.name,
                            checked = location.id in picks,
                            implied = false,
                            onTap = { picks = if (location.id in picks) picks - location.id else picks + location.id },
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
                // Same jump affordances as the Book tab's filter card: each shows
                // only while the list can still scroll that way.
                ScrollJumpChevron(
                    pointsDown = false,
                    visible = scroll.canScrollBackward,
                    onClick = { scrollScope.launch { scroll.animateScrollTo(0) } },
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 4.dp),
                )
                ScrollJumpChevron(
                    pointsDown = true,
                    visible = scroll.canScrollForward,
                    onClick = { scrollScope.launch { scroll.animateScrollTo(scroll.maxValue) } },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp),
                )
            }
            Spacer(Modifier.height(20.dp))
            PrimaryCta(
                label = if (state.savingFavorites) "Saving…" else "Save",
                onClick = { onSave(picks) },
                enabled = picks.isNotEmpty() && !state.savingFavorites,
            )
            state.favoritesError?.let {
                Spacer(Modifier.height(12.dp))
                Caption("Couldn't save your favorites. Try again.", size = 13, color = BurntNectar)
            }
        }
        }
    }
}

/** "What members say · 6 reviews" */

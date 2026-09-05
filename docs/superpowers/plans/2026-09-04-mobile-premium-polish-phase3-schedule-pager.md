# Premium Polish Phase 3: Schedule Pager Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The Book tab's day list becomes a `HorizontalPager` the finger drags, the day rail's selection pill glides with the page offset and auto-scrolls into view, unloaded days show skeleton rows, rows rise in with a short stagger after a settle, and a tick haptic fires per page boundary.

**Architecture:** The screen's single `LazyColumn` splits into a pinned header (month title, search pill, day rail, filter section) and a `HorizontalPager` whose pages are per-day `LazyColumn`s. The ViewModel stays the source of truth for `selectedDate`; the pager and the ViewModel are kept in sync in both directions by two `LaunchedEffect`s. The rail's Moss pill becomes one element positioned by `currentPage + currentPageOffsetFraction`. Nothing about loading, paging within a day, or filters changes.

**Tech Stack:** `androidx.compose.foundation.pager.HorizontalPager`, `rememberPagerState`, `snapshotFlow`, phase 0 tokens, `ShimmerBox` (existing).

Global constraints: see `2026-09-04-mobile-premium-polish.md`. Branch: `feature/polish-3-schedule-pager` from `main` after phase 0 merges.

---

## File Structure

**Create**
| File | Responsibility |
|---|---|
| `sharedUI/src/commonMain/kotlin/org/arcana/mobile/schedule/DayRail.kt` | `DayRail(days, selectedIndex, pageOffset, onSelect)` with the gliding pill. |
| `sharedUI/src/commonMain/kotlin/org/arcana/mobile/schedule/DayPage.kt` | `DayPage(...)`: one day's `LazyColumn` (moved from `SuccessContent`), `SkeletonClassRow`, the row stagger. |
| `sharedUI/src/commonTest/kotlin/org/arcana/mobile/schedule/DayRailMathTest.kt` | Pill position and rail auto-scroll math. |

**Modify**
| File | Change |
|---|---|
| `schedule/ScheduleScreen.kt:306-630` (`SuccessContent`) | Header pinned, pager added, day swipe gesture and day fade removed, `DayChip` removed (moved into `DayRail`). |
| `docs/regression/inventory.md` | SCHED entries for swipe, rail and skeletons. |

`ScheduleViewModel` is unchanged: `selectDay(date, method)` and `ScheduleUiState.Success.days/selectedDate/dayStates` already carry everything the pager needs.

---

### Task 1: Rail math

**Files:**
- Create: `sharedUI/src/commonMain/kotlin/org/arcana/mobile/schedule/DayRail.kt` (the math part; the composable is Task 2)
- Test: `sharedUI/src/commonTest/kotlin/org/arcana/mobile/schedule/DayRailMathTest.kt`

**Interfaces:**
- Produces: `internal fun railPillOffsetPx(position: Float, chipWidthPx: Float, gapPx: Float): Float` and `internal fun railScrollTargetPx(index: Int, chipWidthPx: Float, gapPx: Float, viewportPx: Float, currentScrollPx: Float): Float`.

- [ ] **Step 1: Write the failing test**

```kotlin
package org.arcana.mobile.schedule

import kotlin.test.Test
import kotlin.test.assertEquals

class DayRailMathTest {
    private val chip = 56f
    private val gap = 8f

    @Test
    fun pill_sits_on_the_chip_at_whole_positions() {
        assertEquals(0f, railPillOffsetPx(0f, chip, gap))
        assertEquals(64f, railPillOffsetPx(1f, chip, gap))
        assertEquals(192f, railPillOffsetPx(3f, chip, gap))
    }

    @Test
    fun pill_is_halfway_between_chips_at_a_half_page() {
        assertEquals(32f, railPillOffsetPx(0.5f, chip, gap))
    }

    @Test
    fun scroll_target_keeps_a_visible_chip_where_it_is() {
        // viewport 300 shows chips 0..3 fully; chip 2 needs no scroll.
        assertEquals(0f, railScrollTargetPx(2, chip, gap, viewportPx = 300f, currentScrollPx = 0f))
    }

    @Test
    fun scroll_target_brings_a_hidden_chip_to_the_edge_with_a_margin() {
        // chip 6 starts at 384 and ends at 440; viewport 300 at scroll 0 ends at 300.
        // Target scroll = chipEnd - viewport + margin(24) = 164.
        assertEquals(164f, railScrollTargetPx(6, chip, gap, viewportPx = 300f, currentScrollPx = 0f))
        // Scrolling back: chip 0 hidden to the left at scroll 164 → target = chipStart - margin, floored at 0.
        assertEquals(0f, railScrollTargetPx(0, chip, gap, viewportPx = 300f, currentScrollPx = 164f))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :sharedUI:testDebugUnitTest --tests "org.arcana.mobile.schedule.DayRailMathTest"`
Expected: FAIL to compile, `Unresolved reference: railPillOffsetPx`.

- [ ] **Step 3: Write the math**

```kotlin
package org.arcana.mobile.schedule

private const val RAIL_EDGE_MARGIN_PX = 24f

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
): Float {
    val start = index * (chipWidthPx + gapPx)
    val end = start + chipWidthPx
    return when {
        end + RAIL_EDGE_MARGIN_PX > currentScrollPx + viewportPx -> end - viewportPx + RAIL_EDGE_MARGIN_PX
        start - RAIL_EDGE_MARGIN_PX < currentScrollPx -> maxOf(0f, start - RAIL_EDGE_MARGIN_PX)
        else -> currentScrollPx
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :sharedUI:testDebugUnitTest --tests "org.arcana.mobile.schedule.DayRailMathTest"`
Expected: PASS (4 tests).

---

### Task 2: `DayRail` with the gliding pill

**Files:**
- Modify: `sharedUI/src/commonMain/kotlin/org/arcana/mobile/schedule/DayRail.kt` (add the composables)
- Modify: `sharedUI/src/commonMain/kotlin/org/arcana/mobile/schedule/ScheduleScreen.kt:693-734` (delete `DayChip`)

**Interfaces:**
- Consumes: `controlShadow`, `softShadow`, `ArcanaShapes.Card` (phase 0), `weekdayAbbr()` (existing private extension in `ScheduleScreen.kt`; make it `internal`).
- Produces: `@Composable internal fun DayRail(days: List<LocalDate>, position: Float, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier)`.

- [ ] **Step 1: Write the rail**

Append to `DayRail.kt`:

```kotlin
@Composable
internal fun DayRail(
    days: List<LocalDate>,
    position: Float,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val chipPx = with(density) { CHIP_WIDTH.toPx() }
    val gapPx = with(density) { CHIP_GAP.toPx() }
    var viewportPx by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(selectedIndex, viewportPx) {
        if (viewportPx == 0f) return@LaunchedEffect
        val target = railScrollTargetPx(selectedIndex, chipPx, gapPx, viewportPx, scrollState.value.toFloat())
        if (target != scrollState.value.toFloat()) scrollState.animateScrollTo(target.roundToInt())
    }

    Box(
        modifier
            .fillMaxWidth()
            .onSizeChanged { viewportPx = it.width.toFloat() }
            .horizontalScroll(scrollState)
            .padding(horizontal = 24.dp),
    ) {
        Box(
            Modifier
                .offset { IntOffset(railPillOffsetPx(position, chipPx, gapPx).roundToInt(), 0) }
                .size(width = CHIP_WIDTH, height = CHIP_HEIGHT)
                .controlShadow(ArcanaShapes.Card)
                .clip(ArcanaShapes.Card)
                .background(Moss),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(CHIP_GAP)) {
            days.forEachIndexed { i, date ->
                DayChip(
                    date = date,
                    label = if (i == 0) "TODAY" else "",
                    active = i == selectedIndex,
                    onClick = { onSelect(i) },
                )
            }
        }
    }
}

private val CHIP_WIDTH = 56.dp
private val CHIP_HEIGHT = 64.dp
private val CHIP_GAP = 8.dp

@Composable
private fun DayChip(
    date: LocalDate,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val labelColor by animateColorAsState(if (active) Lime else Ash, tween(Dur.Short), label = "dayLabel")
    val numberColor by animateColorAsState(if (active) Stone else Ink, tween(Dur.Short), label = "dayNumber")
    val source = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .size(width = CHIP_WIDTH, height = CHIP_HEIGHT)
            .pressable(source)
            .then(if (active) Modifier else Modifier.softShadow(ArcanaShapes.Card))
            .clip(ArcanaShapes.Card)
            .background(if (active) Color.Transparent else Paper)
            .border(1.dp, if (active) Color.Transparent else Mist, ArcanaShapes.Card)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(top = 8.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label.ifEmpty { date.weekdayAbbr() },
            maxLines = 1, softWrap = false,
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
```

Imports for `DayRail.kt`: `androidx.compose.animation.animateColorAsState`, `androidx.compose.animation.core.tween`, `androidx.compose.foundation.*` (`background`, `border`, `clickable`, `horizontalScroll`, `rememberScrollState`, `interaction.MutableInteractionSource`, layout `Arrangement`, `Box`, `Column`, `Row`, `fillMaxWidth`, `offset`, `padding`, `size`), `androidx.compose.material3.Text`, runtime (`Composable`, `LaunchedEffect`, `getValue`, `mutableFloatStateOf`, `remember`, `setValue`), `androidx.compose.ui.Alignment`, `Modifier`, `draw.clip`, `graphics.Color`, `layout.onSizeChanged`, `platform.LocalDensity`, `text.TextStyle`, `text.font.FontWeight`, `unit.IntOffset`, `unit.dp`, `unit.em`, `unit.sp`, `kotlinx.datetime.LocalDate`, `org.arcana.mobile.theme.*` (`Arcana`, `ArcanaShapes`, `Ash`, `Dur`, `Ink`, `Lime`, `Mist`, `Moss`, `Paper`, `Stone`), `org.arcana.mobile.ui.controlShadow`, `pressable`, `softShadow`, `kotlin.math.roundToInt`.

Delete `DayChip` from `ScheduleScreen.kt:693-734` and change `weekdayAbbr()`'s visibility to `internal` (it is a private extension near the display helpers; keep it where it is).

- [ ] **Step 2: Compile both targets**

Run the two compile commands. Expected: BUILD SUCCESSFUL (the old rail in `SuccessContent` still compiles against `DayRail` only after Task 4; until then `DayChip`'s removal will fail the build, so do Task 2 Step 1 and Task 4 in the same working session, compiling once after Task 4. If you must compile in between, leave `DayChip` in place until Task 4 and delete it there.)

---

### Task 3: `DayPage` with skeletons and the stagger

**Files:**
- Create: `sharedUI/src/commonMain/kotlin/org/arcana/mobile/schedule/DayPage.kt`

**Interfaces:**
- Consumes: `ShimmerBox` (`ui/Shimmer.kt`), `ClassRow`, `EndOfListMarker`, `InlineError`, `DotMatrixLoaderCompact`, `SectionRule`, `timeBand()`/`TimeBand`, `sessionTimeZone(...)` (all existing in `ScheduleScreen.kt`; make the private ones `internal`), `Dur`, `Ease`, `LocalFloatingBarInset`.
- Produces: `@Composable internal fun DayPage(date: LocalDate, dayState: DayState?, dayError: ErrorType?, dayRetrying: Boolean, refreshingFilters: Boolean, bookedSessions: Map<Int, String>, isCurrent: Boolean, onOpenClassDetail: (Int) -> Unit, onRetry: () -> Unit, onLoadMore: () -> Unit, header: @Composable () -> Unit)`.

- [ ] **Step 1: Write the page**

```kotlin
package org.arcana.mobile.schedule

/** One day of the Book tab: the list body that used to live in SuccessContent, per page. */
@Composable
internal fun DayPage(
    date: LocalDate,
    dayState: DayState?,
    dayError: ErrorType?,
    dayRetrying: Boolean,
    refreshingFilters: Boolean,
    bookedSessions: Map<Int, String>,
    isCurrent: Boolean,
    onOpenClassDetail: (Int) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    header: @Composable () -> Unit,
) {
    val dayLoaded = dayState?.loaded == true
    val sessions = dayState?.sessions.orEmpty()
    val byBand = remember(sessions) {
        sessions.groupBy {
            Instant.parse(it.startAt).toLocalDateTime(sessionTimeZone(it.location.timezone)).time.timeBand()
        }
    }
    val activeBands = remember(byBand) { TimeBand.values().filter { byBand[it]?.isNotEmpty() == true } }
    val listAlpha = if (refreshingFilters) 0.6f else 1f
    val listState = rememberLazyListState()

    LaunchedEffect(listState, isCurrent) {
        if (!isCurrent) return@LaunchedEffect
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index to listState.layoutInfo.totalItemsCount
        }.distinctUntilChanged().collect { (lastVisible, totalCount) ->
            if (lastVisible != null && lastVisible >= totalCount - LOAD_MORE_LOOKAHEAD) onLoadMore()
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp + LocalFloatingBarInset.current),
    ) {
        item("page-header") { header() }
        if (dayError != null) {
            item("day-error") {
                InlineError(
                    type = dayError, onRetry = onRetry, retrying = dayRetrying,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp).alpha(listAlpha),
                )
            }
        } else if (!dayLoaded) {
            items(SKELETON_ROWS, key = { "skeleton-$it" }) { i ->
                SkeletonClassRow(Modifier.padding(horizontal = 24.dp).alpha(listAlpha), fill = 0.55f + (i % 3) * 0.15f)
            }
        } else if (sessions.isEmpty()) {
            item("empty") {
                Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp).alpha(listAlpha)) {
                    BodyText(text = "No classes match your filters for this day.", size = 14, color = Ash)
                }
            }
        } else {
            var rowIndex = 0
            activeBands.forEachIndexed { bandIdx, band ->
                item("band-header-$band") {
                    Column(Modifier.padding(horizontal = 24.dp).alpha(listAlpha)) {
                        if (bandIdx > 0) Spacer(Modifier.height(24.dp))
                        SectionRule(label = band.label)
                        Spacer(Modifier.height(8.dp))
                    }
                }
                val rows = byBand[band].orEmpty()
                items(items = rows, key = { session -> "row-${session.id}" }) { session ->
                    val order = rowIndex++
                    Box(
                        Modifier
                            .padding(horizontal = 24.dp)
                            .alpha(listAlpha)
                            .riseIn(enabled = isCurrent && order < STAGGER_ROWS, delayMs = order * STAGGER_STEP_MS, key = date),
                    ) {
                        ClassRow(session, onClick = { onOpenClassDetail(session.id) }, bookedStatus = bookedSessions[session.id])
                    }
                }
            }
            if (dayState?.nextCursor != null) {
                item("load-more") {
                    Box(Modifier.fillMaxWidth().padding(vertical = 16.dp).alpha(listAlpha), contentAlignment = Alignment.Center) {
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
}

private const val SKELETON_ROWS = 6
private const val STAGGER_ROWS = 8
private const val STAGGER_STEP_MS = 16

/** A row-shaped shimmer: time block, the Moss rule, three lines. */
@Composable
internal fun SkeletonClassRow(modifier: Modifier = Modifier, fill: Float) {
    Row(modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.width(SCHEDULE_TIME_COL_WIDTH)) {
            ShimmerBox(Modifier.size(width = 44.dp, height = 18.dp), shape = RoundedCornerShape(4.dp))
            Spacer(Modifier.height(6.dp))
            ShimmerBox(Modifier.size(width = 30.dp, height = 8.dp), shape = RoundedCornerShape(4.dp))
        }
        Box(Modifier.width(3.dp).height(64.dp).background(Mist, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            ShimmerBox(Modifier.fillMaxWidth(0.5f).height(9.dp), shape = RoundedCornerShape(4.dp))
            Spacer(Modifier.height(8.dp))
            ShimmerBox(Modifier.fillMaxWidth(fill).height(16.dp), shape = RoundedCornerShape(4.dp))
            Spacer(Modifier.height(8.dp))
            ShimmerBox(Modifier.fillMaxWidth(0.35f).height(9.dp), shape = RoundedCornerShape(4.dp))
        }
    }
}

/** Rises 10 dp with a fade the first time [key] becomes current; no-op afterwards and for rows past the stagger. */
@Composable
private fun Modifier.riseIn(enabled: Boolean, delayMs: Int, key: Any): Modifier {
    if (!enabled) return this
    val progress = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        delay(delayMs.toLong())
        progress.animateTo(1f, tween(Dur.Short, easing = Ease.Emphasized))
    }
    val density = LocalDensity.current
    return graphicsLayer {
        alpha = progress.value
        translationY = with(density) { 10.dp.toPx() } * (1f - progress.value)
    }
}
```

Check `ShimmerBox`'s actual signature in `ui/Shimmer.kt:46` (it takes a `shape` argument per the inventory; if the parameter is named differently, match it). `SCHEDULE_TIME_COL_WIDTH`, `LOAD_MORE_LOOKAHEAD`, `titleCase`, `TimeBand`, `timeBand()`, `sessionTimeZone`, `ClassRow`, `EndOfListMarker` are in `ScheduleScreen.kt`; change each from `private` to `internal` so `DayPage.kt` in the same package can use them. Imports follow the identifiers used (`kotlinx.coroutines.delay`, `kotlinx.coroutines.flow.distinctUntilChanged`, `androidx.compose.animation.core.Animatable`, `androidx.compose.foundation.lazy.*`, `androidx.compose.runtime.snapshotFlow`, `androidx.compose.ui.draw.alpha`, `androidx.compose.ui.graphics.graphicsLayer`, etc.).

- [ ] **Step 2: Compile (together with Task 4)**

See Task 4 Step 3.

---

### Task 4: The pager in `SuccessContent`

**Files:**
- Modify: `sharedUI/src/commonMain/kotlin/org/arcana/mobile/schedule/ScheduleScreen.kt:306-630`

**Interfaces:**
- Consumes: `DayRail` (Task 2), `DayPage` (Task 3), `rememberHaptics` (phase 0).

- [ ] **Step 1: Replace the body of `SuccessContent`**

Keep the function signature and the first block (selectedDate, `searchPillBounds`, `nudgeDismissed`). Delete: the `dayState`/`byBand`/`activeBands` block, the `dayFade` block, `listAlpha`, the `listState` + load-more `LaunchedEffect`, the `daySwipe` gesture, and the entire `LazyColumn`. Replace them with:

```kotlin
    val haptics = rememberHaptics()
    val selectedIndex = state.days.indexOf(selectedDate).coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = selectedIndex) { state.days.size }

    // Pager → ViewModel: a settled swipe selects the day (and loads it if needed).
    // selectDay no-ops on the already-selected date, so a chip-driven scroll
    // settling on its own target does not double-fire.
    LaunchedEffect(pagerState, state.days) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                val date = state.days.getOrNull(page) ?: return@collect
                viewModel.selectDay(date, method = "swipe")
            }
    }
    // ViewModel → pager: a chip tap (or a restored selection) scrolls the pager.
    LaunchedEffect(selectedIndex) {
        if (pagerState.currentPage != selectedIndex && !pagerState.isScrollInProgress) {
            pagerState.animateScrollToPage(selectedIndex, animationSpec = Springs.settle())
        }
    }
    // A tick per page boundary crossed by the finger.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.drop(1).collect { haptics.tick() }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.padding(start = 24.dp, end = 16.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Display(text = "${titleCase(selectedDate.month.name)}.", size = 56, color = Ink)
            SearchEntryPill(
                onClick = { onOpenSearch(searchPillBounds) },
                onBounds = { searchPillBounds = it },
                modifier = Modifier.weight(1f).padding(start = 12.dp).offset(y = (-5).dp),
            )
        }
        Spacer(Modifier.height(20.dp))
        DayRail(
            days = state.days,
            position = pagerState.currentPage + pagerState.currentPageOffsetFraction,
            selectedIndex = selectedIndex,
            onSelect = { i -> viewModel.selectDay(state.days[i]) },
        )
        Spacer(Modifier.height(16.dp))
        ScheduleFilterSection(state = state, viewModel = viewModel, onManageFavorites = onManageFavorites)
        if (state.refreshingFilters) {
            Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                DotMatrixLoaderCompact()
            }
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
            key = { page -> state.days[page].toString() },
        ) { page ->
            val date = state.days[page]
            DayPage(
                date = date,
                dayState = state.dayStates[date],
                dayError = if (date == selectedDate) state.dayError else null,
                dayRetrying = state.dayRetrying,
                refreshingFilters = state.refreshingFilters,
                bookedSessions = state.bookedSessions,
                isCurrent = page == pagerState.settledPage,
                onOpenClassDetail = onOpenClassDetail,
                onRetry = viewModel::retryDay,
                onLoadMore = viewModel::loadMore,
                header = {
                    if (state.favoritesKnown && !state.hasFavorites && !nudgeDismissed) {
                        FavoritesNudge(onManageFavorites = onManageFavorites, onDismiss = { nudgeDismissed = true })
                    }
                },
            )
        }
    }
```

Move the favourites nudge `Row` (the body of the old `item("favorites-nudge")`) into a private `@Composable fun FavoritesNudge(onManageFavorites: () -> Unit, onDismiss: () -> Unit)` in `ScheduleScreen.kt`, wrapped in `TransientSurface` if phase 2 has merged (otherwise plain). The compact filter loader that used to pin "between chips and list" (`ScheduleScreen.kt:512-523`) is the `if (state.refreshingFilters)` block above; delete the old one.

Remove `DAY_SWIPE_THRESHOLD`, `DAY_FADE_FROM`, `DAY_FADE_MS` and the `dayAfterSwipe` import if no longer referenced (keep `dayAfterSwipe` in `:sharedLogic`; it has tests).

- [ ] **Step 2: Add imports**

`androidx.compose.foundation.pager.HorizontalPager`, `androidx.compose.foundation.pager.rememberPagerState`, `kotlinx.coroutines.flow.drop`, `org.arcana.mobile.theme.Springs`, `org.arcana.mobile.ui.rememberHaptics`. Remove the imports the deleted code used (`detectHorizontalDragGestures`, `Animatable`, `rememberLazyListState` if unused).

- [ ] **Step 3: Compile both targets and run all tests**

Run:
```
./gradlew :sharedLogic:compileDebugKotlinAndroid :sharedUI:compileDebugKotlinAndroid
./gradlew :sharedLogic:compileKotlinIosSimulatorArm64 :sharedUI:compileKotlinIosSimulatorArm64
./gradlew :sharedUI:testDebugUnitTest :sharedLogic:testDebugUnitTest
```
Expected: BUILD SUCCESSFUL; all tests pass.

- [ ] **Step 4: Verify on both simulators**

Book tab. Drag the list left: the next day's page follows the finger, the Moss pill slides along the rail proportionally, a tick fires per boundary (device only), the page settles on a spring, rows rise in with a stagger. Swipe to a day not yet loaded: skeleton rows show until page 1 lands. Tap a chip four days away: the pager animates there and the rail scrolls the chip into view. Pull to refresh still works on the current page. Filter changes still dim the list and show the compact loader. Load-more still fires when scrolling to the bottom of a long day (Thursday on the PERF account has 40+ classes). Screenshot mid-drag and settled on each platform.

- [ ] **Step 5: The telemetry contract**

`ScheduleViewModel.selectDay(..., method = "swipe")` still fires `scheduleDayChanged` with `method = "swipe"` for pager settles and `"chip_tap"` for chips. Confirm in the Debug build's telemetry echo (see `docs/analytics-qa-checklist.md`) that one event fires per settled page, not one per boundary crossed during a fling.

---

### Task 5: Inventory and report

- [ ] **Step 1: Update `docs/regression/inventory.md`**

SCHED entries: the day-swipe entry's **Steps/Expected** describe the pager (follow the finger, settle, pill glides, tick), the day-rail entry gains auto-scroll and the sliding pill, and a new entry `SCHED-<next> — Unloaded day shows skeleton rows`. Run `tools/regression/self_audit.sh`; expected `FINDINGS: 0`.

- [ ] **Step 2: Leave the tree uncommitted and report**

Files touched, both-platform screenshots, the telemetry check, and the regression read: the schedule screen's structure changed (pinned header, pager), `ScheduleViewModel` untouched, no booking paths.

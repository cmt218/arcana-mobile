# Premium Polish Phase 2: Navigation Motion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Details present like a sheet that became a page (rise 12% with a fade while the screen beneath recedes and dims), sheets get the `ArcanaSheet` wrapper with a receding parent, the Android tab bar floats, and the two transient surfaces (error snackbar, favourites nudge) animate in and out.

**Architecture:** One `theme/NavTransitions.kt` holding the four transition lambdas, applied to both NavHosts (`App.kt` on Android, `shell/TabRoots.kt` on iOS) so the two platforms share one definition. `ui/ArcanaSheet.kt` wraps Material's `ModalBottomSheet` with our surface, scrim and handle, and exposes a `recede` state the calling screen applies to its content. The floating bar is `ArcanaTabBar` styling plus `LocalFloatingBarInset` on Android.

**Tech Stack:** navigation-compose 2.10.0-alpha02 (`AnimatedContentTransitionScope<NavBackStackEntry>`, `slideInVertically`, `scaleOut`), material3 1.12.0-alpha03 `ModalBottomSheet`, `AnimatedVisibility`.

Global constraints: see `2026-09-04-mobile-premium-polish.md`. Branch: `feature/polish-2-navigation` from `main` after phase 0 merges.

---

## File Structure

**Create**
| File | Responsibility |
|---|---|
| `sharedUI/src/commonMain/kotlin/org/arcana/mobile/theme/NavTransitions.kt` | `detailEnter()`, `detailExit()`, `detailPopEnter()`, `detailPopExit()`, `isTabToTab()`. |
| `sharedUI/src/commonMain/kotlin/org/arcana/mobile/ui/ArcanaSheet.kt` | `ArcanaSheet(...)`, `Modifier.recedeBehindSheet(open)`. |
| `sharedUI/src/commonMain/kotlin/org/arcana/mobile/ui/Transient.kt` | `TransientSurface(visible) { }` enter/exit for snackbar and nudge. |

**Modify**
| File | Change |
|---|---|
| `App.kt:319-322` (after phase 0) | Use `NavTransitions`. |
| `shell/TabRoots.kt:236-239` | Same. |
| `App.kt` `MainScaffold` | Tab bar floats: `Scaffold` no longer reserves it; `LocalFloatingBarInset` provided. |
| `ui/TabBar.kt` | Floating Paper pill styling. |
| `booking/BookingSheet.kt:73-75`, `schedule/ClassDetailScreen.kt:606-608` | `ModalBottomSheet` → `ArcanaSheet`. |
| `schedule/ClassDetailScreen.kt` root | `recedeBehindSheet(sheetOpen || cancelSheetOpen)`. |
| `ui/ErrorState.kt:301-340` `ErrorSnackbar` call sites, `schedule/ScheduleScreen.kt` nudge item | Wrapped in `TransientSurface`. |
| `docs/regression/inventory.md` | NAV entries. |

---

### Task 1: The detail push-and-recede

**Files:**
- Create: `sharedUI/src/commonMain/kotlin/org/arcana/mobile/theme/NavTransitions.kt`
- Modify: `sharedUI/src/commonMain/kotlin/org/arcana/mobile/App.kt` (the NavHost transition lambdas and the `isTabToTab` helpers added in phase 0)
- Modify: `sharedUI/src/iosMain/kotlin/org/arcana/mobile/shell/TabRoots.kt:236-239`

**Interfaces:**
- Consumes: `Dur`, `Ease` (phase 0).
- Produces: `object NavTransitions { fun AnimatedContentTransitionScope<NavBackStackEntry>.enter(): EnterTransition; fun ...exit(): ExitTransition; fun ...popEnter(): EnterTransition; fun ...popExit(): ExitTransition }`.

- [ ] **Step 1: Write the transitions**

```kotlin
package org.arcana.mobile.theme

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import org.arcana.mobile.navigation.ArcanaDestination
import androidx.navigation.NavDestination.Companion.hasRoute

/**
 * One definition for both NavHosts. Tabs swap instantly (the bar's dot carries
 * the motion). Every other destination presents like a sheet that became a
 * page: it rises from 12% of the height with a fade while the screen beneath
 * recedes to 96%, and leaves the same way, faster.
 */
object NavTransitions {
    private const val RISE_FRACTION = 0.12f
    private const val RECEDE_SCALE = 0.96f

    fun isTabRoot(dest: NavDestination): Boolean =
        dest.hasRoute<ArcanaDestination.Home>() ||
            dest.hasRoute<ArcanaDestination.Schedule>() ||
            dest.hasRoute<ArcanaDestination.Profile>()

    private fun AnimatedContentTransitionScope<NavBackStackEntry>.tabToTab() =
        isTabRoot(initialState.destination) && isTabRoot(targetState.destination)

    fun AnimatedContentTransitionScope<NavBackStackEntry>.enter(): EnterTransition =
        if (tabToTab()) EnterTransition.None
        else slideInVertically(tween(Dur.Medium, easing = Ease.Emphasized)) { (it * RISE_FRACTION).toInt() } +
            fadeIn(tween(Dur.Medium, easing = Ease.Emphasized))

    fun AnimatedContentTransitionScope<NavBackStackEntry>.exit(): ExitTransition =
        if (tabToTab()) ExitTransition.None
        else scaleOut(tween(Dur.Medium, easing = Ease.Emphasized), targetScale = RECEDE_SCALE) +
            fadeOut(tween(Dur.Medium, easing = Ease.Emphasized), targetAlpha = 0.88f)

    fun AnimatedContentTransitionScope<NavBackStackEntry>.popEnter(): EnterTransition =
        if (tabToTab()) EnterTransition.None
        else scaleIn(tween(Dur.Short, easing = Ease.Exit), initialScale = RECEDE_SCALE) +
            fadeIn(tween(Dur.Short, easing = Ease.Exit), initialAlpha = 0.88f)

    fun AnimatedContentTransitionScope<NavBackStackEntry>.popExit(): ExitTransition =
        if (tabToTab()) ExitTransition.None
        else slideOutVertically(tween(Dur.Short, easing = Ease.Exit)) { (it * RISE_FRACTION).toInt() } +
            fadeOut(tween(Dur.Short, easing = Ease.Exit))
}
```

The 12% dim of the receding screen is `targetAlpha = 0.88f` on its fade: the atmosphere behind the NavHost (Stone on the Scaffold, or the tab root's Stone box on iOS) shows through at 12%, which reads as a dim without a scrim composable.

- [ ] **Step 2: Apply on Android**

In `App.kt`, replace the four transition lambdas on the `NavHost` (phase 0 left them as `isTabToTab()` checks) with:

```kotlin
            enterTransition = { with(NavTransitions) { enter() } },
            exitTransition = { with(NavTransitions) { exit() } },
            popEnterTransition = { with(NavTransitions) { popEnter() } },
            popExitTransition = { with(NavTransitions) { popExit() } },
```

Delete the phase-0 `isTabToTab` and `isTabRoot` private helpers from `App.kt` (they now live in `NavTransitions`). Keep the Schedule and Search per-destination overrides at `App.kt:330-342` and `:361-365` exactly as they are: the Search reveal owns its own transition.

- [ ] **Step 3: Apply on iOS**

In `shell/TabRoots.kt:236-239`, replace the four `fadeIn/fadeOut(tween(150))` lambdas with the same four `with(NavTransitions) { ... }` lines, and import `org.arcana.mobile.theme.NavTransitions`. The Schedule and Search overrides at `TabRoots.kt:93-104` and `:121-125` stay.

- [ ] **Step 4: Compile both targets**

Run:
```
./gradlew :sharedUI:compileDebugKotlinAndroid :sharedUI:compileKotlinIosSimulatorArm64
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Verify on both simulators**

Book tab → tap a class: the detail rises and fades in over 340 ms while the list shrinks slightly and dims; × or back: the detail drops and the list returns over 200 ms. Home → SEE ALL (My bookings), You → the settings gear (Edit profile), You → Concierge: same. Book → search pill: the oval reveal is unchanged. Tab to tab: instant. Screenshot a mid-transition frame of the class-detail push on each platform.

---

### Task 2: `ArcanaSheet`

**Files:**
- Create: `sharedUI/src/commonMain/kotlin/org/arcana/mobile/ui/ArcanaSheet.kt`
- Modify: `sharedUI/src/commonMain/kotlin/org/arcana/mobile/booking/BookingSheet.kt:73-75`
- Modify: `sharedUI/src/commonMain/kotlin/org/arcana/mobile/schedule/ClassDetailScreen.kt:606-608` and the screen root

**Interfaces:**
- Consumes: `ArcanaShapes.Sheet`, `Dur`, `Ease` (phase 0).
- Produces: `@Composable fun ArcanaSheet(onDismissRequest: () -> Unit, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit)`; `@Composable fun Modifier.recedeBehindSheet(open: Boolean): Modifier`.

- [ ] **Step 1: Write the wrapper**

```kotlin
package org.arcana.mobile.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import org.arcana.mobile.theme.ArcanaShapes
import org.arcana.mobile.theme.Dur
import org.arcana.mobile.theme.Ease
import org.arcana.mobile.theme.Ink
import org.arcana.mobile.theme.Mist
import org.arcana.mobile.theme.Paper

private const val SCRIM_ALPHA = 0.40f
private const val RECEDE_SCALE = 0.94f
private val RECEDE_RADIUS = 26.dp

/**
 * The app's one sheet: Paper surface on [ArcanaShapes.Sheet] corners, a Mist
 * handle, an Ink scrim. Always fully expanded. The screen that opens it
 * applies [recedeBehindSheet] to its own content.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArcanaSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        shape = ArcanaShapes.Sheet,
        containerColor = Paper,
        scrimColor = Ink.copy(alpha = SCRIM_ALPHA),
        dragHandle = { SheetHandle() },
        content = content,
    )
}

@Composable
private fun SheetHandle() {
    Box(
        Modifier
            .padding(top = 10.dp, bottom = 14.dp)
            .width(36.dp)
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Mist),
    )
}

/** Scales the screen content to 94% with rounded corners while a sheet is open. */
@Composable
fun Modifier.recedeBehindSheet(open: Boolean): Modifier {
    val scale by animateFloatAsState(
        targetValue = if (open) RECEDE_SCALE else 1f,
        animationSpec = tween(Dur.Medium, easing = Ease.Emphasized),
        label = "recede",
    )
    val radius = with(androidx.compose.ui.platform.LocalDensity.current) { RECEDE_RADIUS.toPx() }
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
        val progress = (1f - scale) / (1f - RECEDE_SCALE)
        shape = RoundedCornerShape(radius * progress)
        clip = progress > 0f
    }
}
```

- [ ] **Step 2: Use it in the booking sheet**

In `BookingSheet.kt`, replace lines 73-75:

```kotlin
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var spotMapExpanded by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Stone) {
```

with:

```kotlin
    var spotMapExpanded by remember { mutableStateOf(false) }
    ArcanaSheet(onDismissRequest = onDismiss) {
```

Remove the now-unused imports (`ModalBottomSheet`, `rememberModalBottomSheetState`, `ExperimentalMaterial3Api` opt-in if nothing else needs it) and import `org.arcana.mobile.ui.ArcanaSheet`. The `Heading3`/`BodyText` colours inside stay as they are; they were designed on Stone and read the same on Paper.

- [ ] **Step 3: Use it in the cancel sheet**

In `ClassDetailScreen.kt` `CancelBookingSheet` (lines 606-608), replace:

```kotlin
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val submitting = cancelState is CancelState.Submitting
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Stone) {
```

with:

```kotlin
    val submitting = cancelState is CancelState.Submitting
    ArcanaSheet(onDismissRequest = onDismiss) {
```

- [ ] **Step 4: Recede the detail behind either sheet**

At the Class detail root `Box` (the one that gained `Atmosphere()` in phase 1, around `ClassDetailScreen.kt:227`), the content that should recede is everything except the atmosphere. Wrap the existing content (the pull-to-refresh box and the sticky CTA column) in:

```kotlin
Box(Modifier.fillMaxSize().recedeBehindSheet(open = sheetOpen || cancelSheetOpen)) {
    // existing content
}
```

`sheetOpen` already exists at the call site (`if (sheetOpen)` at `:549`); find the cancel sheet's open flag near `:598` (it is the boolean the `CancelBookingSheet` call is gated on) and use its name. The `Atmosphere()` stays outside the receding box so the surface does not scale.

- [ ] **Step 5: Compile both targets and verify**

Run the two compile commands. On both simulators: open a bookable class, tap the CTA: the Paper sheet rises with the Mist handle, the page behind scales to 94% with rounded corners under an Ink scrim; tap the scrim: it dismisses and the page returns. Cancel path: on a booked class (the PERF account has a booking, or book one on staging), tap the CTA, the cancel sheet behaves the same. Do not confirm the cancel. Screenshot the open state on each platform.

---

### Task 3: The floating Android tab bar

**Files:**
- Modify: `sharedUI/src/commonMain/kotlin/org/arcana/mobile/ui/TabBar.kt`
- Modify: `sharedUI/src/commonMain/kotlin/org/arcana/mobile/App.kt` `MainScaffold`

**Interfaces:**
- Consumes: `barShadow`, `ArcanaShapes.Pill` (phase 0), `LocalFloatingBarInset` (existing).

- [ ] **Step 1: Restyle the bar**

In `ArcanaTabBar` (phase 0 version), replace the outer `Column`'s modifier chain

```kotlin
        modifier = modifier
            .fillMaxWidth()
            .background(Stone)
            .drawBehind { ... hairline ... },
```

with a floating pill:

```kotlin
        modifier = modifier
            .safeBottomBarPadding()
            .padding(start = BAR_SIDE_INSET, end = BAR_SIDE_INSET, bottom = BAR_BOTTOM_INSET)
            .fillMaxWidth()
            .barShadow(ArcanaShapes.Pill)
            .clip(ArcanaShapes.Pill)
            .background(Paper.copy(alpha = BAR_ALPHA))
            .border(1.dp, Mist, ArcanaShapes.Pill),
```

and change the inner `Box`'s modifier to drop `safeBottomBarPadding()` (now on the outer) and use `padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)` so the pill is 58 dp tall. Add the constants:

```kotlin
private val BAR_SIDE_INSET = 12.dp
private val BAR_BOTTOM_INSET = 10.dp
private const val BAR_ALPHA = 0.92f
/** The bar's full height including its insets; tab roots pad their scrollables by this. */
val FLOATING_BAR_INSET = 58.dp + 10.dp + 12.dp
```

Imports: `androidx.compose.foundation.border`, `org.arcana.mobile.theme.Paper`, `org.arcana.mobile.ui.barShadow` (same package, no import needed), `org.arcana.mobile.theme.ArcanaShapes`. Remove the hairline `drawBehind` and its `Offset` import if unused. Update the KDoc's first line to "Bottom navigation, Android only: a floating Paper pill over the atmosphere."

- [ ] **Step 2: Let content flow under it**

In `App.kt` `MainScaffold`, the `Scaffold` reserves the bar's height through `innerPadding`. Change to overlay the bar and provide the inset:

Replace

```kotlin
    Scaffold(
        containerColor = Stone,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (selectedTab != null) {
                ArcanaTabBar(...)
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = ArcanaDestination.Home,
            modifier = Modifier.padding(innerPadding),
```

with

```kotlin
    CompositionLocalProvider(LocalFloatingBarInset provides FLOATING_BAR_INSET) {
    Box(Modifier.fillMaxSize().background(Stone)) {
        NavHost(
            navController = navController,
            startDestination = ArcanaDestination.Home,
            modifier = Modifier.fillMaxSize(),
```

close the `Box` after the `NavHost` block, and add after the NavHost (still inside the `Box`):

```kotlin
        if (selectedTab != null) {
            ArcanaTabBar(
                active = selectedTab,
                onSelect = { tab ->
                    telemetry.tabTapped(tab.name.lowercase(), fromScreen = screenName)
                    navController.navigateToTab(tab)
                },
                avatarInitials = avatarInitials,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
    }
```

Imports: `androidx.compose.runtime.CompositionLocalProvider`, `org.arcana.mobile.ui.LocalFloatingBarInset`, `org.arcana.mobile.ui.FLOATING_BAR_INSET`, `androidx.compose.ui.Alignment`, `androidx.compose.foundation.background`, `androidx.compose.foundation.layout.Box`. Remove the `Scaffold` and `WindowInsets` imports if unused. The three tab roots already pad their scrollables by `LocalFloatingBarInset.current` (`ScheduleScreen.kt:398`, and the equivalents in `HomeScreen.kt` and `ProfileScreen.kt`; confirm each with `grep -n LocalFloatingBarInset`), which is why this is safe.

- [ ] **Step 3: Compile and verify on Android**

Run the compile commands. On the emulator: the bar floats 10 dp above the gesture area, content scrolls beneath it, the last item of each tab root can still scroll clear of it (scroll to the bottom of Book and You). On a pushed detail the bar is absent as before. Screenshot Home and the bottom of Book. Run `android layout --pretty` and confirm HOME / BOOK / YOU are still exposed. On iOS nothing changes; confirm by launching once.

---

### Task 4: Transient surfaces animate

**Files:**
- Create: `sharedUI/src/commonMain/kotlin/org/arcana/mobile/ui/Transient.kt`
- Modify: every `ErrorSnackbar(` call site (`ScheduleScreen.kt:255`, `HomeScreen.kt:324`, `ProfileScreen.kt:391`, `ClassDetailScreen.kt:501`) and the favourites nudge item (`ScheduleScreen.kt`, the `item("favorites-nudge")` block)

**Interfaces:**
- Produces: `@Composable fun TransientSurface(visible: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit)`.

- [ ] **Step 1: Write it**

```kotlin
package org.arcana.mobile.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.arcana.mobile.theme.Dur
import org.arcana.mobile.theme.Ease

/** Snackbars and nudges rise 8 dp into place and drop out, on the app's curves. */
@Composable
fun TransientSurface(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(Dur.Short, easing = Ease.Emphasized)) +
            slideInVertically(tween(Dur.Short, easing = Ease.Emphasized)) { it / 4 },
        exit = fadeOut(tween(Dur.Quick, easing = Ease.Exit)) +
            slideOutVertically(tween(Dur.Quick, easing = Ease.Exit)) { it / 4 },
    ) { content() }
}
```

- [ ] **Step 2: Wrap each snackbar**

Each call site is inside an `if (<flag>) { ErrorSnackbar(...) }`. Change the pattern to `TransientSurface(visible = <flag>) { ErrorSnackbar(...) }`, keeping the snackbar's own modifier arguments. Where the snackbar sits in a `LazyColumn` `item`, keep the `item` and put `TransientSurface` inside it.

- [ ] **Step 3: Wrap the favourites nudge**

In `ScheduleScreen.kt`, the nudge is `if (state.favoritesKnown && !state.hasFavorites && !nudgeDismissed) { item("favorites-nudge") { ... } }`. Keep the `item` always present and move the condition inside: `item("favorites-nudge") { TransientSurface(visible = state.favoritesKnown && !state.hasFavorites && !nudgeDismissed) { Spacer(...); Row(...) } }`, so dismissing it animates out instead of vanishing.

- [ ] **Step 4: Compile and verify**

Compile both targets. On the emulator with the server stopped (`tools/regression/error-state-harness.sh kill-server`, read `docs/regression/error-states-qa.md` first, and run `preflight`), pull to refresh on Book: the snackbar rises in; dismiss: it drops out. Restart the server afterwards (`error-state-harness.sh` documents the restore). Dismiss the favourites nudge with the PERF account: it animates out.

---

### Task 5: Inventory and report

- [ ] **Step 1: Update `docs/regression/inventory.md`**

Under Navigation & Shell: update the class-detail open/close entry's **Expected** to describe the rise-and-recede and the drop-back; add "Sheets present on a Paper surface with a Mist handle over an Ink scrim; the page behind scales to 94%" to the booking sheet and cancel sheet entries; update the tab bar entry for the floating pill (Android). Under Error States: add "snackbar slides in and out" to the refresh-failed entries. Run `tools/regression/self_audit.sh`; expected `FINDINGS: 0`.

- [ ] **Step 2: Leave the tree uncommitted and report**

Files touched, both-platform screenshots (push mid-transition, sheet open, floating bar, snackbar), and the regression read: presentation only; the booking sheet's content and confirm wiring are byte-for-byte the same inside the new wrapper.

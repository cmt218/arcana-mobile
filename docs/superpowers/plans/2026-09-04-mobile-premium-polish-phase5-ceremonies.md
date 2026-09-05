# Premium Polish Phase 5: Gestures Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A trial, behind one switch: the booking sheet's CONFIRM becomes slide-to-book, the cancel sheet's CANCEL BOOKING becomes hold-to-cancel, and a successful booking confirms in place (the well springs into a check, the label reads BOOKED or REQUESTED, the confirm haptic fires, the sticky CTA pulses once). No overlay, no takeover. With the switch off, both stay taps with the press response, and the confirm and reject haptics still fire. The booking ViewModel and every request it makes are untouched.

**Architecture:** `ui/SlideToConfirm.kt` and `ui/HoldToConfirm.kt` are self-contained gesture controls that call a plain `onConfirm: () -> Unit`, so they drop in where `PrimaryCta` was. `BookingGestures.enabled` and `accessibilityServicesActive()` choose between gesture and tap. The confirmation is state the controls already render (`completed`), held on screen for half a second before the sheet closes.

**Tech Stack:** `androidx.compose.foundation.gestures.AnchoredDraggableState` / `Modifier.anchoredDraggable`, `Modifier.pointerInput` + `detectTapGestures` for hold, phase 0 haptics and springs.

Global constraints: see `2026-09-04-mobile-premium-polish.md`. Branch: `feature/polish-5-gestures` from `main` after phases 0, 2 and 4 merge.

---

## File Structure

**Create**
| File | Responsibility |
|---|---|
| `sharedUI/src/commonMain/kotlin/org/arcana/mobile/booking/BookingGestures.kt` | `object BookingGestures { const val enabled = true }`, `@Composable fun useBookingGestures(): Boolean`. |
| `sharedUI/src/commonMain/kotlin/org/arcana/mobile/ui/AccessibilityPlatform.kt` + android/ios actuals | `expect fun accessibilityServicesActive(): Boolean`. |
| `sharedUI/src/commonMain/kotlin/org/arcana/mobile/ui/SlideToConfirm.kt` | The slide control. |
| `sharedUI/src/commonTest/kotlin/org/arcana/mobile/ui/SlideToConfirmMathTest.kt` | Arm threshold and label fade math. |
| `sharedUI/src/commonMain/kotlin/org/arcana/mobile/ui/HoldToConfirm.kt` | The hold control. |

**Modify**
| File | Change |
|---|---|
| `booking/BookingSheet.kt:160-167` | CONFIRM → `SlideToConfirm` when gestures are on; the sheet holds half a second in the completed state. |
| `schedule/ClassDetailScreen.kt:627-636` | CANCEL BOOKING → `HoldToConfirm` when gestures are on. |
| `schedule/ClassDetailScreen.kt` (where `submit is BookingSubmit.Booked` is observed, ~478; `StickyReserveCta` arrow well ~1168) | Confirm and reject haptics; one Lime pulse of the CTA well on success. |
| `docs/regression/inventory.md` | BOOK entries for the gestures and the in-place confirmation, with the switch documented. |

---

### Task 1: The switch and the accessibility gate

**Files:**
- Create: `sharedUI/src/commonMain/kotlin/org/arcana/mobile/booking/BookingGestures.kt`
- Create: `sharedUI/src/commonMain/kotlin/org/arcana/mobile/ui/AccessibilityPlatform.kt`, `sharedUI/src/androidMain/.../ui/AccessibilityPlatform.android.kt`, `sharedUI/src/iosMain/.../ui/AccessibilityPlatform.ios.kt`

**Interfaces:**
- Produces: `object BookingGestures { const val enabled: Boolean }`; `@Composable fun useBookingGestures(): Boolean` (= `enabled && !accessibilityServicesActive()`); `expect fun accessibilityServicesActive(): Boolean`.

- [ ] **Step 1: Common**

```kotlin
package org.arcana.mobile.booking

import androidx.compose.runtime.Composable
import org.arcana.mobile.ui.accessibilityServicesActive

/** The trial switch. Off = plain taps with the press response and haptics. */
object BookingGestures {
    const val enabled = true
}

@Composable
fun useBookingGestures(): Boolean = BookingGestures.enabled && !accessibilityServicesActive()
```

```kotlin
package org.arcana.mobile.ui

/** True when a screen reader or switch access is driving the UI; gesture controls then render as taps. */
expect fun accessibilityServicesActive(): Boolean
```

- [ ] **Step 2: Android actual**

```kotlin
package org.arcana.mobile.ui

import android.content.Context
import android.view.accessibility.AccessibilityManager
import org.arcana.mobile.SharedAndroidContext

actual fun accessibilityServicesActive(): Boolean {
    val am = SharedAndroidContext.context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
    return am?.isTouchExplorationEnabled == true
}
```

`SharedAndroidContext` is the app-context holder `ArcanaApplication` sets (see `Platform.android.kt` for how the other actuals read it; match its accessor name exactly).

- [ ] **Step 3: iOS actual**

```kotlin
package org.arcana.mobile.ui

import platform.UIKit.UIAccessibilityIsSwitchControlRunning
import platform.UIKit.UIAccessibilityIsVoiceOverRunning

actual fun accessibilityServicesActive(): Boolean =
    UIAccessibilityIsVoiceOverRunning() || UIAccessibilityIsSwitchControlRunning()
```

- [ ] **Step 4: Compile both targets**

Run the two compile commands. Expected: BUILD SUCCESSFUL.

---

### Task 2: Slide to confirm

**Files:**
- Create: `sharedUI/src/commonMain/kotlin/org/arcana/mobile/ui/SlideToConfirm.kt`
- Test: `sharedUI/src/commonTest/kotlin/org/arcana/mobile/ui/SlideToConfirmMathTest.kt`

**Interfaces:**
- Produces: `internal fun slideArmed(progress: Float): Boolean` (progress ≥ 0.85), `internal fun slideLabelAlpha(progress: Float): Float`, and `@Composable fun SlideToConfirm(label: String, subLabel: String?, onConfirm: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, containerColor: Color = Moss, accentColor: Color = Lime, completed: Boolean = false, completedLabel: String = "BOOKED")`.

- [ ] **Step 1: Write the failing test**

```kotlin
package org.arcana.mobile.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SlideToConfirmMathTest {
    @Test
    fun arms_at_eighty_five_percent() {
        assertFalse(slideArmed(0.84f))
        assertTrue(slideArmed(0.85f))
        assertTrue(slideArmed(1f))
    }

    @Test
    fun label_fades_out_by_two_thirds_of_the_travel() {
        assertEquals(1f, slideLabelAlpha(0f))
        assertTrue(slideLabelAlpha(0.3f) in 0.4f..0.6f)
        assertEquals(0f, slideLabelAlpha(0.7f))
        assertEquals(0f, slideLabelAlpha(1f))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :sharedUI:testDebugUnitTest --tests "org.arcana.mobile.ui.SlideToConfirmMathTest"`
Expected: FAIL to compile.

- [ ] **Step 3: Write the control**

```kotlin
package org.arcana.mobile.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.arcana.mobile.theme.Arcana
import org.arcana.mobile.theme.ArcanaShapes
import org.arcana.mobile.theme.Dur
import org.arcana.mobile.theme.Ink
import org.arcana.mobile.theme.Lime
import org.arcana.mobile.theme.Moss
import org.arcana.mobile.theme.Springs
import org.arcana.mobile.theme.Stone
import kotlin.math.roundToInt

private const val ARM_AT = 0.85f
private const val LABEL_FADE_END = 0.7f
private val TRACK_HEIGHT = 56.dp
private val THUMB = 44.dp
private val THUMB_INSET = 6.dp

internal fun slideArmed(progress: Float): Boolean = progress >= ARM_AT
internal fun slideLabelAlpha(progress: Float): Float = (1f - progress / LABEL_FADE_END).coerceIn(0f, 1f)

private enum class SlideAnchor { Start, End }

/**
 * A pill whose accent well is a thumb. Drag it to the end: at 85% the control
 * arms (threshold haptic, the well turns Stone); release then confirms.
 * Release earlier and it springs home. Renders the [completedLabel] once
 * [completed] is true.
 */
@Composable
fun SlideToConfirm(
    label: String,
    subLabel: String?,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = Moss,
    accentColor: Color = Lime,
    completed: Boolean = false,
    completedLabel: String = "BOOKED",
) {
    val density = LocalDensity.current
    val haptics = rememberHaptics()
    var trackWidthPx by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val travelPx = with(density) { (trackWidthPx - (THUMB + THUMB_INSET * 2).roundToPx()).coerceAtLeast(1) }.toFloat()
    val state = remember(travelPx) {
        AnchoredDraggableState(
            initialValue = SlideAnchor.Start,
            anchors = DraggableAnchors { SlideAnchor.Start at 0f; SlideAnchor.End at travelPx },
            positionalThreshold = { distance -> distance * ARM_AT },
            velocityThreshold = { Float.POSITIVE_INFINITY },
            snapAnimationSpec = Springs.Settle,
            decayAnimationSpec = androidx.compose.animation.core.exponentialDecay(),
        )
    }
    val progress = (state.requireOffset() / travelPx).coerceIn(0f, 1f)
    val armed = slideArmed(progress)
    LaunchedEffect(armed) { if (armed && !completed) haptics.threshold() }
    LaunchedEffect(state.settledValue) {
        if (state.settledValue == SlideAnchor.End && !completed) onConfirm()
    }

    val wellColor by animateColorAsState(if (armed || completed) Stone else accentColor, tween(Dur.Quick), label = "well")
    val labelAlpha = if (completed) 1f else slideLabelAlpha(progress)

    Box(
        modifier
            .fillMaxWidth()
            .height(TRACK_HEIGHT)
            .onSizeChanged { trackWidthPx = it.width }
            .controlShadow(ArcanaShapes.Pill)
            .clip(ArcanaShapes.Pill)
            .background(containerColor)
            .innerHighlight(ArcanaShapes.Pill),
    ) {
        // Accent fill that follows the thumb.
        Box(
            Modifier
                .fillMaxHeight()
                .width(with(density) { (state.requireOffset() + (THUMB + THUMB_INSET * 2).toPx()).toDp() })
                .background(accentColor.copy(alpha = if (completed) 0f else 0.95f)),
        )
        Column(
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = 22.dp)
                .graphicsLayer { alpha = labelAlpha },
        ) {
            Text(
                text = (if (completed) completedLabel else label).uppercase(),
                maxLines = 1,
                modifier = Modifier.opticallyCentredCaps(fontSize = 14.sp, letterSpacingEm = 0.10f),
                style = TextStyle(fontFamily = Arcana.fonts.display, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, letterSpacing = 0.10.em, color = Stone),
            )
            if (subLabel != null && !completed) {
                Text(
                    text = subLabel,
                    maxLines = 1,
                    style = TextStyle(fontFamily = Arcana.fonts.body, fontWeight = FontWeight.Medium, fontSize = 9.sp, letterSpacing = 0.10.em, color = Stone.copy(alpha = 0.67f)),
                )
            }
        }
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = THUMB_INSET)
                .offset { IntOffset(state.requireOffset().roundToInt(), 0) }
                .size(THUMB)
                .clip(CircleShape)
                .background(wellColor)
                .then(if (enabled && !completed) Modifier.anchoredDraggable(state, Orientation.Horizontal) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            StrokeIcon(
                icon = if (completed) ArcanaIcons.Check else ArcanaIcons.ArrowRight,
                size = 18.dp,
                tint = Ink,
                contentDescription = if (completed) null else "Slide to $label",
            )
        }
    }
}
```

Check `ArcanaIcons` for the check glyph's exact name (`StudioAccordion` uses a check icon; reuse that member). If `AnchoredDraggableState`'s constructor in foundation 1.12.0 differs (the `decayAnimationSpec` parameter moved to `anchoredDraggable`'s `flingBehavior` in some versions), match the signature in the IDE; the behaviour to preserve is: positional threshold 85%, no velocity fling, settle spring `Springs.Settle`.

- [ ] **Step 4: Run test to verify it passes; compile both targets**

Run: `./gradlew :sharedUI:testDebugUnitTest --tests "org.arcana.mobile.ui.SlideToConfirmMathTest"` then the two compile commands. Expected: PASS; BUILD SUCCESSFUL.

---

### Task 3: Hold to confirm

**Files:**
- Create: `sharedUI/src/commonMain/kotlin/org/arcana/mobile/ui/HoldToConfirm.kt`

**Interfaces:**
- Produces: `@Composable fun HoldToConfirm(label: String, onConfirm: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, containerColor: Color = Clay, accentColor: Color = ClayDeep, holdMs: Int = 700)`.

- [ ] **Step 1: Write it**

```kotlin
package org.arcana.mobile.ui

/**
 * A pill you hold. The well fills a ring over [holdMs] with a rising haptic;
 * releasing early cancels; completing fires the reject haptic and [onConfirm].
 */
@Composable
fun HoldToConfirm(
    label: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = Clay,
    accentColor: Color = ClayDeep,
    holdMs: Int = 700,
) {
    val haptics = rememberHaptics()
    val ring = remember { Animatable(0f) }
    var holding by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(holding) {
        if (holding) {
            val rampJob = launch { while (true) { haptics.ramp(); delay(90) } }
            ring.animateTo(1f, tween(holdMs, easing = LinearEasing))
            rampJob.cancel()
            if (ring.value >= 1f) { haptics.reject(); onConfirm() }
        } else {
            ring.animateTo(0f, tween(Dur.Short, easing = Ease.Exit))
        }
    }

    Row(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .controlShadow(ArcanaShapes.Pill)
            .clip(ArcanaShapes.Pill)
            .background(containerColor)
            .innerHighlight(ArcanaShapes.Pill)
            .then(
                if (enabled) Modifier.pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            holding = true
                            tryAwaitRelease()
                            holding = false
                        },
                    )
                } else Modifier
            )
            .padding(start = 24.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label.uppercase(),
            modifier = Modifier.opticallyCentredCaps(fontSize = 14.sp, letterSpacingEm = 0.14f),
            style = TextStyle(fontFamily = Arcana.fonts.display, fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 0.14.em, color = Stone),
        )
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(44.dp)) {
                val stroke = 3.dp.toPx()
                drawCircle(color = Stone.copy(alpha = 0.25f), style = Stroke(stroke))
                drawArc(
                    color = Stone, startAngle = -90f, sweepAngle = 360f * ring.value, useCenter = false,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
            StrokeIcon(icon = ArcanaIcons.Close, size = 16.dp, tint = Stone, contentDescription = "Hold to $label")
        }
    }
}
```

Imports follow the identifiers (`androidx.compose.foundation.Canvas`, `androidx.compose.foundation.gestures.detectTapGestures`, `androidx.compose.ui.input.pointer.pointerInput`, `androidx.compose.ui.graphics.drawscope.Stroke`, `androidx.compose.ui.graphics.StrokeCap`, `androidx.compose.animation.core.LinearEasing`, `kotlinx.coroutines.delay`, `kotlinx.coroutines.launch`, theme `Clay`, `ClayDeep`, `Ease`, `Dur`).

- [ ] **Step 2: Compile both targets**

Expected: BUILD SUCCESSFUL.

---

### Task 4: In-place confirmation

**Files:**
- Modify: `sharedUI/src/commonMain/kotlin/org/arcana/mobile/ui/SlideToConfirm.kt` (Task 2's control)
- Modify: `sharedUI/src/commonMain/kotlin/org/arcana/mobile/schedule/ClassDetailScreen.kt` (`StickyReserveCta`, the arrow well ~1168, and where `submit` is observed ~478)

**Interfaces:**
- Consumes: `Springs.Kick`, `rememberHaptics` (phase 0).
- Produces: `SlideToConfirm(completed = true)` plays the check morph; `StickyReserveCta` gains `pulse: Boolean = false`.

- [ ] **Step 1: The well springs into a check**

In `SlideToConfirm`, the thumb already swaps its glyph when `completed`. Make the swap a spring pop: add

```kotlin
    val pop by animateFloatAsState(
        targetValue = if (completed) 1f else 0f,
        animationSpec = Springs.Kick,
        label = "completedPop",
    )
```

and on the thumb `Box`, before `.size(THUMB)`, add `.graphicsLayer { val s = 1f + 0.18f * pop * (1f - pop) * 4f; scaleX = s; scaleY = s }` (a 1.0 → 1.18 → 1.0 pop as `pop` runs 0 → 1). Keep the thumb pinned at the end anchor while `completed` (the `anchoredDraggable` modifier is already removed in that state; also set `LaunchedEffect(completed) { if (completed) state.snapTo(SlideAnchor.End) }` so a `completed` control that was never dragged, e.g. after rotation, still shows the well at the end).

- [ ] **Step 2: The sticky CTA pulses once**

Add `pulse: Boolean = false` to `StickyReserveCta`'s parameters. Inside it:

```kotlin
    val glow = remember { Animatable(0f) }
    LaunchedEffect(pulse) {
        if (!pulse) return@LaunchedEffect
        glow.animateTo(1f, tween(Dur.Short, easing = Ease.Emphasized))
        glow.animateTo(0f, tween(Dur.Medium, easing = Ease.Exit))
    }
```

and on the arrow-well `Box` (~1168) add `.graphicsLayer { val s = 1f + 0.12f * glow.value; scaleX = s; scaleY = s }` and draw the glow behind it with `.drawBehind { drawCircle(Lime.copy(alpha = 0.35f * glow.value), radius = size.minDimension * (0.5f + 0.35f * glow.value)) }` placed before `.clip(CircleShape)`. Total 540 ms, one time.

- [ ] **Step 3: Drive it from the booking state**

Where `ClassDetailScreen` observes `submit` (~478), add:

```kotlin
    val haptics = rememberHaptics()
    val justBooked = submit is BookingSubmit.Booked
    LaunchedEffect(submit) {
        when (submit) {
            is BookingSubmit.Booked -> haptics.confirm()
            is BookingSubmit.Failed -> haptics.reject()
            else -> Unit
        }
    }
```

and pass `pulse = justBooked` to `StickyReserveCta`. The label already flips to CONFIRMED ✓ / REQUESTED ✓ through `classDetailCtaLabel`; the pulse lands as the label changes. For cancellation, where `CancelState` is observed, fire `haptics.reject()` on its success value the same way.

- [ ] **Step 4: Compile both targets**

Run the two compile commands. Expected: BUILD SUCCESSFUL.

---
### Task 5: Wire the gestures into the sheets

**Files:**
- Modify: `sharedUI/src/commonMain/kotlin/org/arcana/mobile/booking/BookingSheet.kt:160-167`
- Modify: `sharedUI/src/commonMain/kotlin/org/arcana/mobile/schedule/ClassDetailScreen.kt:627-636`

- [ ] **Step 1: The booking sheet's confirm**

Replace the `PrimaryCta(label = if (submitting) "BOOKING…" else "CONFIRM", ...)` block with:

```kotlin
            if (useBookingGestures()) {
                SlideToConfirm(
                    label = if (submitting) "BOOKING…" else "SLIDE TO BOOK",
                    subLabel = null,
                    onConfirm = onConfirm,
                    enabled = confirmEnabled && !submitting,
                    completed = booked,
                    completedLabel = if (bookedStatus == "requested") "REQUESTED" else "BOOKED",
                )
            } else {
                PrimaryCta(
                    label = if (submitting) "BOOKING…" else "CONFIRM",
                    onClick = onConfirm,
                    enabled = confirmEnabled && !submitting,
                    trailing = if (submitting) { { CtaSpinner() } } else null,
                )
            }
```

`onConfirm` is the same lambda the tap used; nothing about the ViewModel changes. `BookingSheet` gains two parameters, `booked: Boolean = false` and `bookedStatus: String? = null`, which `ClassDetailScreen` passes as `submit is BookingSubmit.Booked` and `existing?.status`. While `submitting`, the slide control is disabled and its label reads BOOKING….

**Hold the sheet for the check.** Today the sheet unmounts the instant the ViewModel reports `Booked`. So the completed morph is visible, keep it mounted half a second longer, in `ClassDetailScreen` where the sheet is gated (`if (sheetOpen)` ~549):

```kotlin
    var holdForConfirm by remember { mutableStateOf(false) }
    LaunchedEffect(submit) {
        if (submit is BookingSubmit.Booked && useGestures) {
            holdForConfirm = true
            delay(CONFIRM_HOLD_MS)
            holdForConfirm = false
        }
    }
    if (sheetOpen || holdForConfirm) { BookingSheet(...) }
```

with `private const val CONFIRM_HOLD_MS = 550L` and `val useGestures = useBookingGestures()` read once at the screen's top. During the hold the sheet's `onDismiss` is a no-op (`onDismiss = { if (!holdForConfirm) bookingVm.dismissSheet() }`, using whatever the existing dismiss lambda calls). With gestures off nothing changes: the sheet closes immediately as today.

- [ ] **Step 2: The cancel sheet's confirm**

Replace the Clay `PrimaryCta` at `:627-636` with:

```kotlin
            if (useBookingGestures()) {
                HoldToConfirm(
                    label = if (submitting) "CANCELLING…" else "HOLD TO CANCEL",
                    onConfirm = onConfirm,
                    enabled = !submitting,
                )
            } else {
                PrimaryCta(
                    label = if (submitting) "CANCELLING…" else "CANCEL BOOKING",
                    onClick = onConfirm,
                    enabled = !submitting,
                    containerColor = Clay,
                    accentColor = ClayDeep,
                    trailing = if (submitting) { { CtaSpinner() } } else null,
                )
            }
```

- [ ] **Step 3: The haptics on the tap path too**

Where `ClassDetailScreen` observes `submit`: on transition to `BookingSubmit.Booked` call `haptics.confirm()`; on `BookingSubmit.Failed` call `haptics.reject()`; on `CancelState` success call `haptics.reject()`. Use one `LaunchedEffect(submit)` keyed on the state value so each fires once. `val haptics = rememberHaptics()` at the screen's top.

- [ ] **Step 4: Compile both targets and run all tests**

Expected: BUILD SUCCESSFUL; tests pass.

- [ ] **Step 5: Verify on device, against staging**

Point the app at staging via the 10-tap dev switcher (`docs/regression/runbook.md` describes the account and the sandbox studio). On the physical Pixel 9 Pro: open a bookable sandbox class, tap the CTA, slide the well: the fill follows, the label fades, at 85% the well turns Stone with a firm haptic; release: the well pops into a check, the label reads BOOKED, the confirm haptic fires, the sheet closes about half a second later, and the sticky CTA's well pulses Lime once as its label becomes CONFIRMED ✓. Then tap the CTA again, hold the Clay well: the ring fills with rising ticks; release early: nothing happens; hold through: the cancel completes with the reject haptic. Repeat on the iOS simulator for visuals (no haptics there). Then flip `BookingGestures.enabled = false`, rebuild, and confirm both flows are plain taps with the press response and that the confirm and reject haptics still fire on the Pixel. Leave the switch `true`.

- [ ] **Step 6: Accessibility fallback**

On the Pixel, enable TalkBack, open the sheet: it renders the tap CTA, announced as "Confirm, button". Disable TalkBack afterwards.

---

### Task 6: Inventory and report

- [ ] **Step 1: Update `docs/regression/inventory.md`**

BOOK entries: the confirm step describes slide-to-book (with the switch and the accessibility fallback noted), the cancel step describes hold-to-cancel, and a new entry `BOOK-<next> — In-place booking confirmation` (Steps: complete a booking; Expected: the slide well becomes a check and the label reads BOOKED or REQUESTED, the sheet closes about half a second later, the sticky CTA's Lime well pulses once as its label becomes CONFIRMED ✓ or REQUESTED ✓; no overlay). Run `tools/regression/self_audit.sh`; expected `FINDINGS: 0`.

- [ ] **Step 2: Leave the tree uncommitted and report**

Files touched, device screenshots (slide mid-travel, armed, the check state, the CTA pulse, the hold ring), the switch-off verification, and the regression read, stated loudly: this branch touches the booking sheet and the cancel sheet's confirm controls, but not `BookingViewModel`, not `ArcanaApiClient`, and not any request; both confirms call the same lambdas the taps called. Ask Cole and Felicia to try the trial on their own devices before deciding whether the switch ships on.

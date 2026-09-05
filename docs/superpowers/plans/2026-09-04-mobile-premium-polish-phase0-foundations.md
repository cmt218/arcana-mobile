# Premium Polish Phase 0: Foundations Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Land the tokens and primitives every later phase composes from (`Motion.kt`, `Shapes.kt`, `Depth.kt`, `Pressable.kt`, `Haptics.kt`) and apply them to `PrimaryCta` and the Android tab bar so the branch has a visible result: pressable CTAs, a Lime dot that springs between tabs, an icon bounce, a selection haptic, and instant tab screens.

**Architecture:** Pure value objects in `theme/`, composable modifier extensions in `ui/` (the pattern `ui/Insets.kt` already uses), one `expect/actual` for haptics under `sharedUI/src/{commonMain,androidMain,iosMain}`. Nothing in `:sharedLogic` changes.

**Tech Stack:** Compose Multiplatform 1.12.0 (`androidx.compose.ui.draw.dropShadow` / `innerShadow`, `animateFloatAsState`, `spring`), kotlin.test in `sharedUI/src/commonTest`.

Global constraints: see `2026-09-04-mobile-premium-polish.md` §Global Constraints. Branch: `feature/polish-0-foundations` from `main`, in its own worktree.

---

## File Structure

**Create**
| File | Responsibility |
|---|---|
| `sharedUI/src/commonMain/kotlin/org/arcana/mobile/theme/Motion.kt` | `Ease`, `Dur`, `Springs` tokens. |
| `sharedUI/src/commonTest/kotlin/org/arcana/mobile/theme/MotionTest.kt` | Locks the token values and ordering. |
| `sharedUI/src/commonMain/kotlin/org/arcana/mobile/theme/Shapes.kt` | `ArcanaShapes` radii. |
| `sharedUI/src/commonMain/kotlin/org/arcana/mobile/ui/Depth.kt` | `controlShadow`, `cardShadow`, `barShadow`, `softShadow`, `innerHighlight`. |
| `sharedUI/src/commonMain/kotlin/org/arcana/mobile/ui/Pressable.kt` | `Modifier.pressable`, `Color.pressedShade`. |
| `sharedUI/src/commonTest/kotlin/org/arcana/mobile/ui/PressableTest.kt` | `pressedShade` darkens without changing hue family. |
| `sharedUI/src/commonMain/kotlin/org/arcana/mobile/ui/Haptics.kt` | `Haptics` interface, `NoHaptics`, `expect rememberHaptics()`. |
| `sharedUI/src/androidMain/kotlin/org/arcana/mobile/ui/Haptics.android.kt` | View-feedback-constant actual. |
| `sharedUI/src/iosMain/kotlin/org/arcana/mobile/ui/Haptics.ios.kt` | UIKit feedback-generator actual. |

**Modify**
| File | Change |
|---|---|
| `sharedUI/src/commonMain/kotlin/org/arcana/mobile/ui/Buttons.kt:49-101` | `PrimaryCta` gains press scale, fill darken, Lime-well kick; ripple removed. |
| `sharedUI/src/commonMain/kotlin/org/arcana/mobile/ui/TabBar.kt` | One travelling dot, icon bounce, selection haptic. |
| `sharedUI/src/commonMain/kotlin/org/arcana/mobile/App.kt:319-322` | Tab-to-tab transitions become `None`. |
| `docs/regression/inventory.md` | NAV entries for the dot animation and instant swap; a note on PrimaryCta press response. |

---

### Task 1: Motion tokens

**Files:**
- Create: `sharedUI/src/commonMain/kotlin/org/arcana/mobile/theme/Motion.kt`
- Test: `sharedUI/src/commonTest/kotlin/org/arcana/mobile/theme/MotionTest.kt`

**Interfaces:**
- Produces: `object Ease { val Emphasized: CubicBezierEasing; val Exit: CubicBezierEasing }`, `object Dur { const val Quick = 120; const val Short = 200; const val Medium = 340; const val Long = 480 }`, `object Springs { val Snappy: SpringSpec<Float>; val Settle: SpringSpec<Float>; val Kick: SpringSpec<Float>; fun <T> snappy(): SpringSpec<T>; fun <T> settle(): SpringSpec<T>; fun <T> kick(): SpringSpec<T> }`. Later tasks use `tween(Dur.Medium, easing = Ease.Emphasized)` and `Springs.snappy<Dp>()`.

- [ ] **Step 1: Write the failing test**

```kotlin
package org.arcana.mobile.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MotionTest {
    @Test
    fun durations_are_ordered_quick_to_long() {
        assertTrue(Dur.Quick < Dur.Short && Dur.Short < Dur.Medium && Dur.Medium < Dur.Long)
        assertEquals(120, Dur.Quick)
        assertEquals(340, Dur.Medium)
    }

    @Test
    fun emphasized_easing_starts_at_zero_and_ends_at_one() {
        assertEquals(0f, Ease.Emphasized.transform(0f))
        assertEquals(1f, Ease.Emphasized.transform(1f))
        assertEquals(0f, Ease.Exit.transform(0f))
        assertEquals(1f, Ease.Exit.transform(1f))
    }

    @Test
    fun emphasized_easing_decelerates() {
        // The search reveal's curve: past halfway it is already most of the way there.
        assertTrue(Ease.Emphasized.transform(0.5f) > 0.8f)
    }

    @Test
    fun kick_is_the_only_bouncy_spring() {
        assertTrue(Springs.Kick.dampingRatio < 0.7f)
        assertTrue(Springs.Snappy.dampingRatio >= 0.8f)
        assertTrue(Springs.Settle.dampingRatio >= 0.8f)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :sharedUI:testDebugUnitTest --tests "org.arcana.mobile.theme.MotionTest"`
Expected: FAIL to compile, `Unresolved reference: Dur`.

- [ ] **Step 3: Write the tokens**

```kotlin
package org.arcana.mobile.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring

/** The app's one motion hand. Every duration, easing and spring is drawn from here. */
object Ease {
    /** Everything entering. The Material emphasised curve; the search reveal already uses it. */
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    /** Everything leaving: fast out, no lingering. */
    val Exit = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
}

object Dur {
    const val Quick = 120
    const val Short = 200
    const val Medium = 340
    const val Long = 480
}

object Springs {
    private const val SNAPPY_DAMPING = 0.85f
    private const val SETTLE_DAMPING = 0.90f
    private const val KICK_DAMPING = 0.65f

    /** Press scale, chip pops, the tab indicator. */
    val Snappy: SpringSpec<Float> = snappy()
    /** Pager settle, sheet settle, the slide thumb returning. */
    val Settle: SpringSpec<Float> = settle()
    /** The one bouncy spring: the Lime well and the confirmation check only. */
    val Kick: SpringSpec<Float> = kick()

    fun <T> snappy(): SpringSpec<T> = spring(dampingRatio = SNAPPY_DAMPING, stiffness = Spring.StiffnessMedium)
    fun <T> settle(): SpringSpec<T> = spring(dampingRatio = SETTLE_DAMPING, stiffness = Spring.StiffnessMediumLow)
    fun <T> kick(): SpringSpec<T> = spring(dampingRatio = KICK_DAMPING, stiffness = Spring.StiffnessMedium)
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :sharedUI:testDebugUnitTest --tests "org.arcana.mobile.theme.MotionTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Verify both targets compile**

Run:
```
./gradlew :sharedUI:compileDebugKotlinAndroid :sharedUI:compileKotlinIosSimulatorArm64
```
Expected: BUILD SUCCESSFUL.

---

### Task 2: Shape and depth tokens

**Files:**
- Create: `sharedUI/src/commonMain/kotlin/org/arcana/mobile/theme/Shapes.kt`
- Create: `sharedUI/src/commonMain/kotlin/org/arcana/mobile/ui/Depth.kt`

**Interfaces:**
- Produces: `object ArcanaShapes { val Chip; val Card; val Hero; val Sheet; val Pill }` (all `Shape`), and `Modifier.controlShadow(shape)`, `Modifier.cardShadow(shape)`, `Modifier.barShadow(shape)`, `Modifier.softShadow(shape)`, `Modifier.innerHighlight(shape)`.

- [ ] **Step 1: Write the shapes**

```kotlin
package org.arcana.mobile.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/** The five radii the app uses. Eleven literals collapsed to these. */
object ArcanaShapes {
    val Chip: Shape = RoundedCornerShape(12.dp)
    val Card: Shape = RoundedCornerShape(16.dp)
    val Hero: Shape = RoundedCornerShape(14.dp)
    val Sheet: Shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    val Pill: Shape = CircleShape
}
```

- [ ] **Step 2: Write the depth modifiers**

```kotlin
package org.arcana.mobile.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import org.arcana.mobile.theme.Ink
import org.arcana.mobile.theme.Stone

/** Warm Ink shadows so controls sit on the atmosphere instead of being drawn into it. */

/** Moss and Ink pills, the selected day chip. Pair with [innerHighlight] on dark fills. */
fun Modifier.controlShadow(shape: Shape): Modifier = dropShadow(shape) {
    radius = 18.dp
    offset = DpOffset(0.dp, 6.dp)
    color = Ink.copy(alpha = 0.14f)
}

/** Home cards, the detail hero, the favourites nudge, sheets. */
fun Modifier.cardShadow(shape: Shape): Modifier = dropShadow(shape) {
    radius = 24.dp
    offset = DpOffset(0.dp, 10.dp)
    color = Ink.copy(alpha = 0.08f)
}

/** The floating Android tab bar. */
fun Modifier.barShadow(shape: Shape): Modifier = dropShadow(shape) {
    radius = 24.dp
    offset = DpOffset(0.dp, 8.dp)
    color = Ink.copy(alpha = 0.12f)
}

/** Paper pills and chips: a whisper, so they read as lying on the surface. */
fun Modifier.softShadow(shape: Shape): Modifier = dropShadow(shape) {
    radius = 8.dp
    offset = DpOffset(0.dp, 2.dp)
    color = Ink.copy(alpha = 0.05f)
}

/** One-pixel Stone highlight along the top edge of a dark fill. */
fun Modifier.innerHighlight(shape: Shape): Modifier = innerShadow(shape) {
    radius = 0.dp
    spread = 1.dp
    offset = DpOffset(0.dp, 1.dp)
    color = Stone.copy(alpha = 0.10f)
}
```

- [ ] **Step 3: Compile both targets**

Run:
```
./gradlew :sharedUI:compileDebugKotlinAndroid :sharedUI:compileKotlinIosSimulatorArm64
```
Expected: BUILD SUCCESSFUL. If `dropShadow` does not resolve, the block overload's scope properties differ from the ones above; open `androidx.compose.ui.draw.DropShadowScope` in the IDE (it is in `androidx.compose.ui:ui:1.12.0`) and match its property names exactly. Do not fall back to `Modifier.shadow(elevation)`; it cannot express blur and offset separately.

---

### Task 3: The press response

**Files:**
- Create: `sharedUI/src/commonMain/kotlin/org/arcana/mobile/ui/Pressable.kt`
- Test: `sharedUI/src/commonTest/kotlin/org/arcana/mobile/ui/PressableTest.kt`

**Interfaces:**
- Produces: `fun Color.pressedShade(): Color`; `@Composable fun Modifier.pressable(interactionSource: MutableInteractionSource, enabled: Boolean = true, pressedScale: Float = 0.97f): Modifier`; `@Composable fun rememberPressed(interactionSource: MutableInteractionSource): State<Boolean>`.
- Convention for callers: create `val source = remember { MutableInteractionSource() }`, pass it to `clickable(interactionSource = source, indication = null, ...)` and to `.pressable(source)`.

- [ ] **Step 1: Write the failing test**

```kotlin
package org.arcana.mobile.ui

import androidx.compose.ui.graphics.luminance
import org.arcana.mobile.theme.Clay
import org.arcana.mobile.theme.Moss
import kotlin.test.Test
import kotlin.test.assertTrue

class PressableTest {
    @Test
    fun pressed_shade_is_darker_than_the_fill() {
        assertTrue(Moss.pressedShade().luminance() < Moss.luminance())
        assertTrue(Clay.pressedShade().luminance() < Clay.luminance())
    }

    @Test
    fun pressed_shade_keeps_the_hue_family() {
        // Moss stays green: green channel still dominates red and blue.
        val shade = Moss.pressedShade()
        assertTrue(shade.green > shade.red && shade.green > shade.blue)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :sharedUI:testDebugUnitTest --tests "org.arcana.mobile.ui.PressableTest"`
Expected: FAIL to compile, `Unresolved reference: pressedShade`.

- [ ] **Step 3: Write the implementation**

```kotlin
package org.arcana.mobile.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import org.arcana.mobile.theme.Ink
import org.arcana.mobile.theme.Springs

private const val PRESSED_SHADE = 0.12f

/** The fill a control shows while pressed: the same colour, a step toward Ink. */
fun Color.pressedShade(): Color = lerp(this, Ink, PRESSED_SHADE)

@Composable
fun rememberPressed(interactionSource: MutableInteractionSource): State<Boolean> =
    interactionSource.collectIsPressedAsState()

/**
 * Scales the control to [pressedScale] while pressed and back on release, on
 * [Springs.Snappy]. Replaces the ripple: pair with
 * `clickable(interactionSource = source, indication = null)`.
 */
@Composable
fun Modifier.pressable(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
    pressedScale: Float = 0.97f,
): Modifier {
    val pressed by rememberPressed(interactionSource)
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) pressedScale else 1f,
        animationSpec = Springs.Snappy,
        label = "pressScale",
    )
    return graphicsLayer { scaleX = scale; scaleY = scale }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :sharedUI:testDebugUnitTest --tests "org.arcana.mobile.ui.PressableTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Compile both targets**

Run: `./gradlew :sharedUI:compileDebugKotlinAndroid :sharedUI:compileKotlinIosSimulatorArm64`
Expected: BUILD SUCCESSFUL.

---

### Task 4: Haptics

**Files:**
- Create: `sharedUI/src/commonMain/kotlin/org/arcana/mobile/ui/Haptics.kt`
- Create: `sharedUI/src/androidMain/kotlin/org/arcana/mobile/ui/Haptics.android.kt`
- Create: `sharedUI/src/iosMain/kotlin/org/arcana/mobile/ui/Haptics.ios.kt`

**Interfaces:**
- Produces: `interface Haptics { fun selection(); fun tick(); fun toggle(on: Boolean); fun threshold(); fun confirm(); fun reject(); fun boundary(); fun ramp() }`, `object NoHaptics : Haptics`, `@Composable expect fun rememberHaptics(): Haptics`.
- Callers do `val haptics = rememberHaptics()` in composition and call verbs from click and gesture handlers. Never from a `LaunchedEffect` that could fire on recomposition.

- [ ] **Step 1: Write the common contract**

```kotlin
package org.arcana.mobile.ui

import androidx.compose.runtime.Composable

/** The phone's vocabulary, matched to the screen's. Eight verbs, used consistently. */
interface Haptics {
    /** Tab change, day chip tap, spot pick. */
    fun selection()
    /** Each page boundary while scrubbing days. Very light, repeatable. */
    fun tick()
    /** Filter chip and favourite on / off. Two distinct weights. */
    fun toggle(on: Boolean)
    /** Slide-to-book arms, pull-to-refresh triggers, a sheet passes its dismiss point. */
    fun threshold()
    /** Booking succeeds, profile saved. */
    fun confirm()
    /** Booking fails, hold-to-cancel completes. */
    fun reject()
    /** Overscroll at the end of a list. Soft. */
    fun boundary()
    /** Hold-to-cancel while holding; the caller repeats it on a timer. */
    fun ramp()
}

object NoHaptics : Haptics {
    override fun selection() = Unit
    override fun tick() = Unit
    override fun toggle(on: Boolean) = Unit
    override fun threshold() = Unit
    override fun confirm() = Unit
    override fun reject() = Unit
    override fun boundary() = Unit
    override fun ramp() = Unit
}

@Composable
expect fun rememberHaptics(): Haptics
```

- [ ] **Step 2: Write the Android actual**

`View.performHapticFeedback` needs no permission and respects the system haptics setting. Constants are gated by API level; older levels fall back to the nearest older constant.

```kotlin
package org.arcana.mobile.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

@Composable
actual fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { ViewHaptics(view) }
}

private class ViewHaptics(private val view: View) : Haptics {
    private fun perform(constant: Int) { view.performHapticFeedback(constant) }
    private val api = Build.VERSION.SDK_INT

    override fun selection() = perform(HapticFeedbackConstants.CLOCK_TICK)
    override fun tick() = perform(
        if (api >= 34) HapticFeedbackConstants.SEGMENT_FREQUENT_TICK else HapticFeedbackConstants.CLOCK_TICK
    )
    override fun toggle(on: Boolean) = perform(
        when {
            api >= 34 && on -> HapticFeedbackConstants.TOGGLE_ON
            api >= 34 -> HapticFeedbackConstants.TOGGLE_OFF
            else -> HapticFeedbackConstants.KEYBOARD_TAP
        }
    )
    override fun threshold() = perform(
        if (api >= 34) HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE else HapticFeedbackConstants.LONG_PRESS
    )
    override fun confirm() = perform(
        if (api >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.KEYBOARD_TAP
    )
    override fun reject() = perform(
        if (api >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
    )
    override fun boundary() = perform(
        if (api >= 30) HapticFeedbackConstants.GESTURE_END else HapticFeedbackConstants.CLOCK_TICK
    )
    override fun ramp() = tick()
}
```

- [ ] **Step 3: Write the iOS actual**

Generators are created once per composition and `prepare()`d so the first impact is not late.

```kotlin
package org.arcana.mobile.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyleLight
import platform.UIKit.UIImpactFeedbackStyleRigid
import platform.UIKit.UIImpactFeedbackStyleSoft
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackTypeSuccess
import platform.UIKit.UINotificationFeedbackTypeWarning
import platform.UIKit.UISelectionFeedbackGenerator

@Composable
actual fun rememberHaptics(): Haptics = remember { UiKitHaptics() }

private class UiKitHaptics : Haptics {
    private val selectionGen = UISelectionFeedbackGenerator().also { it.prepare() }
    private val light = UIImpactFeedbackGenerator(style = UIImpactFeedbackStyleLight).also { it.prepare() }
    private val soft = UIImpactFeedbackGenerator(style = UIImpactFeedbackStyleSoft).also { it.prepare() }
    private val rigid = UIImpactFeedbackGenerator(style = UIImpactFeedbackStyleRigid).also { it.prepare() }
    private val notification = UINotificationFeedbackGenerator().also { it.prepare() }

    override fun selection() = selectionGen.selectionChanged()
    override fun tick() = light.impactOccurredWithIntensity(0.5)
    override fun toggle(on: Boolean) = if (on) light.impactOccurredWithIntensity(0.8) else soft.impactOccurredWithIntensity(0.6)
    override fun threshold() = rigid.impactOccurredWithIntensity(1.0)
    override fun confirm() = notification.notificationOccurred(UINotificationFeedbackTypeSuccess)
    override fun reject() = notification.notificationOccurred(UINotificationFeedbackTypeWarning)
    override fun boundary() = soft.impactOccurredWithIntensity(0.5)
    override fun ramp() = light.impactOccurredWithIntensity(0.4)
}
```

- [ ] **Step 4: Compile both targets**

Run:
```
./gradlew :sharedUI:compileDebugKotlinAndroid :sharedUI:compileKotlinIosSimulatorArm64
```
Expected: BUILD SUCCESSFUL. If a UIKit symbol does not resolve under Kotlin/Native, check the exact cinterop name with `grep -r "UIImpactFeedbackStyle" ~/.konan/` and adjust the import; the enum constants are top-level `platform.UIKit` vals.

- [ ] **Step 5: Verify on device, not simulator**

Neither simulator produces haptics. Record in the phase report that haptic verbs were exercised on the physical Pixel 9 Pro (`adb` debug build) and, if available, an iPhone. If no iPhone is available, say so plainly in the report rather than implying it was checked.

---

### Task 5: PrimaryCta gets the press response

**Files:**
- Modify: `sharedUI/src/commonMain/kotlin/org/arcana/mobile/ui/Buttons.kt:49-101`

**Interfaces:**
- Consumes: `pressable`, `rememberPressed`, `pressedShade` (Task 3), `Springs.Kick` (Task 1), `controlShadow`, `innerHighlight` (Task 2), `ArcanaShapes.Pill` (Task 2).
- Produces: `PrimaryCta` keeps its public signature. Every existing call site is unchanged.

- [ ] **Step 1: Replace the `PrimaryCta` body**

Replace lines 49-101 of `Buttons.kt` with:

```kotlin
@Composable
fun PrimaryCta(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = Moss,
    accentColor: Color = Lime,
    trailing: (@Composable () -> Unit)? = null,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by rememberPressed(source)
    val fill by animateColorAsState(
        targetValue = when {
            !enabled -> Ash2
            pressed -> containerColor.pressedShade()
            else -> containerColor
        },
        animationSpec = tween(Dur.Quick),
        label = "ctaFill",
    )
    val kick by animateDpAsState(
        targetValue = if (pressed && enabled) 2.dp else 0.dp,
        animationSpec = Springs.kick(),
        label = "ctaKick",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .pressable(source, enabled)
            .then(if (enabled) Modifier.controlShadow(ArcanaShapes.Pill) else Modifier)
            .clip(ArcanaShapes.Pill)
            .background(fill)
            .then(if (enabled) Modifier.innerHighlight(ArcanaShapes.Pill) else Modifier)
            .clickable(enabled = enabled, interactionSource = source, indication = null, onClick = onClick)
            .padding(start = 24.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label.uppercase(),
            modifier = Modifier.opticallyCentredCaps(
                fontSize = 14.sp,
                letterSpacingEm = 0.14f,
            ),
            style = TextStyle(
                fontFamily = Arcana.fonts.display,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                letterSpacing = 0.14.em,
                color = Stone,
            ),
        )
        if (trailing != null) {
            Box(modifier = Modifier.padding(end = TRAILING_SLOT_END_INSET)) {
                trailing()
            }
        } else {
            Box(
                modifier = Modifier
                    .offset(x = kick)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (enabled) accentColor else Stone.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center,
            ) {
                // decorative — the CTA's own label is the accessible name.
                StrokeIcon(icon = ArcanaIcons.ArrowRight, size = 18.dp, tint = Ink)
            }
        }
    }
}
```

Add the imports the new body needs at the top of `Buttons.kt`:

```kotlin
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import org.arcana.mobile.theme.ArcanaShapes
import org.arcana.mobile.theme.Dur
import org.arcana.mobile.theme.Springs
```

Delete the now-unused `Modifier.clip(CircleShape)` import only if nothing else in the file uses `CircleShape` (the arrow well still does; keep it).

- [ ] **Step 2: Compile both targets and run the UI tests**

Run:
```
./gradlew :sharedUI:compileDebugKotlinAndroid :sharedUI:compileKotlinIosSimulatorArm64
./gradlew :sharedUI:testDebugUnitTest
```
Expected: BUILD SUCCESSFUL; all existing tests pass (the CTA label test in `schedule/ClassDetailCtaLabelTest.kt` is untouched).

- [ ] **Step 3: Verify on both simulators**

Build and launch (`./gradlew :androidApp:installDebug` for the emulator; the iOS app from Xcode or the iOS Simulator MCP `build` + `launch`). On the sign-in screen, press and hold SIGN IN: the pill scales to 97% and darkens; release: it springs back and the Lime well kicks right and settles. Screenshot the pressed state on each platform for the report. Then run `tools/regression/measure_centering.py <shot.png> 283b15 3 <x0 y0 x1 y1>` on the idle pill: under 0.5 pt on both axes (the label and nudge did not change, this is a guard).

---

### Task 6: The travelling dot, the icon bounce, instant tabs

**Files:**
- Modify: `sharedUI/src/commonMain/kotlin/org/arcana/mobile/ui/TabBar.kt`
- Modify: `sharedUI/src/commonMain/kotlin/org/arcana/mobile/App.kt:319-322`
- Modify: `docs/regression/inventory.md`

**Interfaces:**
- Consumes: `Springs.Snappy` (Task 1), `rememberHaptics` (Task 4).
- Produces: `ArcanaTabBar` keeps its signature. The iOS shell is untouched (native bar).

- [ ] **Step 1: Rewrite `ArcanaTabBar` with one dot**

Replace the whole of `TabBar.kt` below the `ArcanaTab` enum (lines 49-168) with:

```kotlin
/**
 * Bottom navigation, Android only (iOS uses the native SwiftUI bar). Stone
 * surface, hairline top. One Lime dot travels between the three items on a
 * spring; the active icon bounces; the active item reads in Moss. The bar
 * fills its whole slot including the gesture-nav inset.
 */
@Composable
fun ArcanaTabBar(
    active: ArcanaTab,
    onSelect: (ArcanaTab) -> Unit,
    avatarInitials: String,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val dotPosition by animateFloatAsState(
        targetValue = active.ordinal.toFloat(),
        animationSpec = Springs.Snappy,
        label = "tabDot",
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Stone)
            .drawBehind {
                drawLine(
                    color = Mist,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx(),
                )
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .safeBottomBarPadding()
                .padding(start = 8.dp, end = 8.dp, top = 10.dp, bottom = 12.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                ArcanaTab.entries.forEach { tab ->
                    TabItem(
                        tab = tab,
                        active = tab == active,
                        avatarInitials = avatarInitials,
                        onClick = {
                            if (tab != active) haptics.selection()
                            onSelect(tab)
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            TravellingDot(position = dotPosition, count = ArcanaTab.entries.size)
        }
    }
}

/** The Lime dot, drawn once and placed at the centre of item [position] (fractional while moving). */
@Composable
private fun TravellingDot(position: Float, count: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(DOT_SIZE)
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = DOT_SIZE.roundToPx()))
                layout(constraints.maxWidth, placeable.height) {
                    val slot = constraints.maxWidth / count.toFloat()
                    val x = ((position + 0.5f) * slot - placeable.width / 2f).roundToInt()
                    placeable.placeRelative(x, 0)
                }
            }
            .size(DOT_SIZE)
            .clip(CircleShape)
            .background(Lime),
    )
}

private val DOT_SIZE = 4.dp

@Composable
private fun TabItem(
    tab: ArcanaTab,
    active: Boolean,
    avatarInitials: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint by animateColorAsState(if (active) Moss else Ash, tween(Dur.Short), label = "tabTint")
    val bounce by animateFloatAsState(
        targetValue = if (active) 1.06f else 1f,
        animationSpec = Springs.Snappy,
        label = "tabBounce",
    )
    Column(
        modifier = modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        // Reserves the dot's row; the dot itself is drawn once by TravellingDot.
        Spacer(Modifier.size(DOT_SIZE))
        Box(Modifier.graphicsLayer { scaleX = bounce; scaleY = bounce }) {
            if (tab.isAvatar) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(if (active) Moss else Mist2)
                        .then(
                            if (active) Modifier.border(1.5.dp, Lime, CircleShape) else Modifier
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = avatarInitials,
                        // Match the profile hero: trim the line box and nudge the
                        // all-caps initials down ~0.09em so they sit dead-center.
                        modifier = Modifier.offset(y = 1.dp),
                        style = TextStyle(
                            fontFamily = Arcana.fonts.display,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            lineHeight = 10.sp,
                            lineHeightStyle = LineHeightStyle(
                                alignment = LineHeightStyle.Alignment.Center,
                                trim = LineHeightStyle.Trim.Both,
                            ),
                            letterSpacing = 0.02.em,
                            color = if (active) Lime else Ash,
                        ),
                    )
                }
            } else {
                // decorative — the visible tab label below is the accessible name,
                // so describing the glyph too would double the announcement.
                StrokeIcon(icon = tab.icon, size = 22.dp, tint = tint)
            }
        }
        Text(
            text = tab.label.uppercase(),
            style = TextStyle(
                fontFamily = Arcana.fonts.body,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                fontSize = 10.sp,
                letterSpacing = 0.18.em,
                color = tint,
            ),
        )
    }
}
```

Add these imports to `TabBar.kt`:

```kotlin
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import org.arcana.mobile.theme.Dur
import org.arcana.mobile.theme.Springs
import kotlin.math.roundToInt
```

- [ ] **Step 2: Make tab-to-tab transitions instant on Android**

In `App.kt`, replace lines 319-322 (the four NavHost transition lambdas) with:

```kotlin
            enterTransition = { if (isTabToTab()) EnterTransition.None else fadeIn(tween(Dur.Quick)) },
            exitTransition = { if (isTabToTab()) ExitTransition.None else fadeOut(tween(Dur.Quick)) },
            popEnterTransition = { if (isTabToTab()) EnterTransition.None else fadeIn(tween(Dur.Quick)) },
            popExitTransition = { if (isTabToTab()) ExitTransition.None else fadeOut(tween(Dur.Quick)) },
```

and add, next to `currentScreenName` near the bottom of `App.kt`:

```kotlin
/** True when both ends of a transition are tab roots: tabs swap instantly, the bar's dot carries the motion. */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabToTab(): Boolean =
    isTabRoot(initialState.destination) && isTabRoot(targetState.destination)

private fun isTabRoot(dest: NavDestination): Boolean =
    dest.hasRoute<ArcanaDestination.Home>() ||
        dest.hasRoute<ArcanaDestination.Schedule>() ||
        dest.hasRoute<ArcanaDestination.Profile>()
```

with imports `androidx.compose.animation.AnimatedContentTransitionScope`, `androidx.navigation.NavBackStackEntry`, `org.arcana.mobile.theme.Dur`. The non-tab fades stay for now; phase 2 replaces them with the push-and-recede.

- [ ] **Step 3: Compile both targets**

Run:
```
./gradlew :sharedUI:compileDebugKotlinAndroid :sharedUI:compileKotlinIosSimulatorArm64
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Verify on the Android emulator**

Install the debug build, tap Home → Book → You → Home. The dot glides between items with a small overshoot and the icon scales up; screens swap with no fade. Capture a screenshot mid-travel with `adb exec-out screencap -p` immediately after `adb shell input tap` (the spring takes ~320 ms; a second capture 150 ms later shows the dot between slots). Then run `android layout --pretty` and confirm the three tab items still expose their labels (HOME, BOOK, YOU) as before.

- [ ] **Step 5: Verify on the iOS simulator**

Build and launch. Confirm nothing about the native tab bar changed and that `PrimaryCta` (Task 5) presses correctly here too. Note in the report that the dot and instant swap are Android-only by design.

- [ ] **Step 6: Update the regression inventory**

In `docs/regression/inventory.md`, under Navigation & Shell, update the tab-bar entry's **Expected** to: "Tapping a tab swaps the screen instantly; the Lime indicator dot travels to the tapped item on a spring and the icon bounces (Android). iOS uses the native bar." Under whichever entry covers the primary CTA (Auth SIGN IN is the simplest), add to **Expected**: "Pressing the CTA scales it to 97% and darkens the fill; release springs back with a small kick of the Lime well." Then run `tools/regression/self_audit.sh` and confirm `FINDINGS: 0`.

- [ ] **Step 7: Leave the tree uncommitted and report**

Report to Cole: files touched (the nine above), how to test (Steps 4 and 5), regression read: `:sharedUI` presentation only, no booking or logic paths, Android tab transitions changed from a 150 ms fade to none.

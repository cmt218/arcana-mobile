# Premium Polish Phase 1: Atmosphere Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace every flat Stone root with the living atmosphere of record (spec §Atmosphere: Felicia's O at Quiet), drawn by `MeshGradientPainter`, at 30 fps, still under Reduce Motion and Low Power Mode, with a plain-brush fallback below Android 10, and prove it costs no frames.

**Architecture:** Pure math in `theme/AtmosphereMath.kt` (seeds, control-point positions, the sixteen colours) so the spec's numbers are unit-tested; one composable `theme/Atmosphere.kt` that owns the frame loop, the painter, the vignette and the gates; two tiny `expect/actual`s for the platform gates. Screens replace `.background(Stone)` with `Atmosphere()` placed under their content.

**Tech Stack:** `androidx.compose.ui.graphics.MeshGradientPainter` (in `androidx.compose.ui:ui:1.12.0`; present in the CMP 1.12.0 iOS klib with `setVertex` and `hasBicubicColor`), `androidx.compose.ui.preferredFrameRate`, `withFrameNanos`, `LifecycleResumeEffect` (lifecycle-runtime-compose 2.11.0).

Global constraints: see `2026-09-04-mobile-premium-polish.md`. Branch: `feature/polish-1-atmosphere` from `main` after phase 0 merges.

---

## File Structure

**Create**
| File | Responsibility |
|---|---|
| `sharedUI/src/commonMain/kotlin/org/arcana/mobile/theme/AtmosphereMath.kt` | Colours, seeds, `controlPoint(...)`. Pure. |
| `sharedUI/src/commonTest/kotlin/org/arcana/mobile/theme/AtmosphereMathTest.kt` | Locks the recipe. |
| `sharedUI/src/commonMain/kotlin/org/arcana/mobile/theme/Atmosphere.kt` | The composable: frame loop, painter, vignette, gates, fallback. |
| `sharedUI/src/commonMain/kotlin/org/arcana/mobile/theme/AtmospherePlatform.kt` | `expect fun meshGradientSupported(): Boolean`, `@Composable expect fun systemAllowsAmbientMotion(): Boolean`. |
| `sharedUI/src/androidMain/kotlin/org/arcana/mobile/theme/AtmospherePlatform.android.kt` | API 29 gate; power-save and animator-scale checks. |
| `sharedUI/src/iosMain/kotlin/org/arcana/mobile/theme/AtmospherePlatform.ios.kt` | Reduce Motion and Low Power Mode checks. |
| `tools/regression/atmosphere_sample.py` | Pixel sampler used by the parity check. |

**Modify**
| File | Change |
|---|---|
| `sharedUI/src/commonMain/kotlin/org/arcana/mobile/theme/AmbientGlow.kt` | Delete (unused; replaced). |
| `home/HomeScreen.kt:135`, `schedule/ScheduleScreen.kt:233`, `schedule/ClassDetailScreen.kt:227`, `profile/ProfileScreen.kt:194,424`, `profile/EditProfileScreen.kt:144,377`, `auth/AuthScreen.kt:101`, `auth/PasswordResetRequestScreen.kt:62`, `signup/SignupSurveyScreen.kt:81`, `signup/SignupCompletionScreen.kt:151,445,500`, `studios/StudioSelectionScreen.kt:76`, `concierge/ConciergeRequestScreen.kt:72,141`, `booking/MyBookingsScreen.kt:41`, `search/SearchScreen.kt:167` | `.background(Stone)` → `Atmosphere()` under the content. |
| `schedule/ClassDetailScreen.kt:1102-1111,1184` | The sticky CTA's Stone slab and fade become translucent so the surface shows through. |
| `docs/regression/inventory.md` | Every screen entry's **Expected** gains "on the living atmosphere"; a new LAUNCH/PERF note for the still fallback. |

---

### Task 1: The recipe as pure math

**Files:**
- Create: `sharedUI/src/commonMain/kotlin/org/arcana/mobile/theme/AtmosphereMath.kt`
- Test: `sharedUI/src/commonTest/kotlin/org/arcana/mobile/theme/AtmosphereMathTest.kt`

**Interfaces:**
- Produces: `const val ATMOSPHERE_GRID = 4`; `val ATMOSPHERE_COLORS: List<Color>` (16, row-major); `data class PointSeed(val periodX: Float, val periodY: Float, val phaseX: Float, val phaseY: Float)`; `fun atmosphereSeeds(random: Random): List<PointSeed>`; `fun atmosphereControlPoint(row: Int, col: Int, timeSeconds: Float, seed: PointSeed): Offset` (normalised 0..1); `const val ATMOSPHERE_AMPLITUDE = 0.15f`; `val ATMOSPHERE_VIGNETTE: Color`, `const val ATMOSPHERE_VIGNETTE_ALPHA = 0.06f`.

- [ ] **Step 1: Write the failing test**

```kotlin
package org.arcana.mobile.theme

import androidx.compose.ui.geometry.Offset
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AtmosphereMathTest {
    private val seeds = atmosphereSeeds(Random(7))

    @Test
    fun sixteen_colours_in_the_spec_order() {
        assertEquals(16, ATMOSPHERE_COLORS.size)
        assertEquals(Color(0xFFEEEDDC), ATMOSPHERE_COLORS[0])
        assertEquals(Color(0xFFC5CCA6), ATMOSPHERE_COLORS[5])
        assertEquals(Color(0xFFD3D5AB), ATMOSPHERE_COLORS[6])
        assertEquals(Color(0xFFEBEBD4), ATMOSPHERE_COLORS[7])
    }

    @Test
    fun corners_never_move() {
        for (t in listOf(0f, 1.7f, 33f)) {
            assertEquals(Offset(0f, 0f), atmosphereControlPoint(0, 0, t, seeds[0]))
            assertEquals(Offset(1f, 1f), atmosphereControlPoint(3, 3, t, seeds[15]))
        }
    }

    @Test
    fun edge_points_slide_only_along_their_edge() {
        for (t in listOf(0f, 2.3f, 9f)) {
            val top = atmosphereControlPoint(0, 1, t, seeds[1])
            assertEquals(0f, top.y)
            val left = atmosphereControlPoint(2, 0, t, seeds[8])
            assertEquals(0f, left.x)
        }
    }

    @Test
    fun interior_points_stay_within_amplitude_of_their_base() {
        for (t in 0 until 200) {
            val p = atmosphereControlPoint(1, 2, t / 10f, seeds[6])
            assertTrue(kotlin.math.abs(p.x - 2f / 3f) <= ATMOSPHERE_AMPLITUDE + 1e-5f)
            assertTrue(kotlin.math.abs(p.y - 1f / 3f) <= ATMOSPHERE_AMPLITUDE + 1e-5f)
        }
    }

    @Test
    fun seeds_scale_the_base_periods_within_the_spec_band() {
        for (s in seeds) {
            assertTrue(s.periodX in 6.0f * 0.8f..6.0f * 1.4f)
            assertTrue(s.periodY in 7.5f * 0.8f..7.5f * 1.4f)
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :sharedUI:testDebugUnitTest --tests "org.arcana.mobile.theme.AtmosphereMathTest"`
Expected: FAIL to compile, `Unresolved reference: atmosphereSeeds`.

- [ ] **Step 3: Write the math**

```kotlin
package org.arcana.mobile.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/*
 * The atmosphere of record (spec 2026-09-04 §Atmosphere): a 4×4 mesh whose
 * four interior points drift, whose edge points slide along their edge, and
 * whose corners are fixed. Colours are Stone mixed with Lime, LimeDeep and an
 * olive (MossLight + Lime), already at the chosen presence.
 */
const val ATMOSPHERE_GRID = 4
const val ATMOSPHERE_AMPLITUDE = 0.15f
private const val EDGE_AMPLITUDE_FACTOR = 0.6f
private const val BASE_PERIOD_X = 6.0f
private const val BASE_PERIOD_Y = 7.5f
private const val PERIOD_SCALE_MIN = 0.8f
private const val PERIOD_SCALE_MAX = 1.4f

private val LimeWhisper = Color(0xFFEEEDDC)   // Stone + Lime 10.8%
private val LimeTint = Color(0xFFEBEBD4)      // Stone + Lime 15.6%
private val OliveShade = Color(0xFFC5CCA6)    // Stone + Olive(MossLight+Lime 50%) 39%
private val LimeDeepShade = Color(0xFFD3D5AB) // Stone + LimeDeep 36%

val ATMOSPHERE_COLORS: List<Color> = listOf(
    LimeWhisper, LimeTint, LimeTint, LimeWhisper,
    LimeTint, OliveShade, LimeDeepShade, LimeTint,
    LimeTint, LimeDeepShade, OliveShade, LimeTint,
    LimeWhisper, LimeTint, LimeTint, LimeWhisper,
)

val ATMOSPHERE_VIGNETTE = Color(0xFFC6CA91)   // Stone + LimeDeep 50%
const val ATMOSPHERE_VIGNETTE_ALPHA = 0.06f

data class PointSeed(val periodX: Float, val periodY: Float, val phaseX: Float, val phaseY: Float)

fun atmosphereSeeds(random: Random): List<PointSeed> = List(ATMOSPHERE_GRID * ATMOSPHERE_GRID) {
    PointSeed(
        periodX = BASE_PERIOD_X * random.nextFloat(PERIOD_SCALE_MIN, PERIOD_SCALE_MAX),
        periodY = BASE_PERIOD_Y * random.nextFloat(PERIOD_SCALE_MIN, PERIOD_SCALE_MAX),
        phaseX = random.nextFloat() * TWO_PI,
        phaseY = random.nextFloat() * TWO_PI,
    )
}

private const val TWO_PI = (2 * PI).toFloat()
private fun Random.nextFloat(from: Float, until: Float) = from + nextFloat() * (until - from)

/** Normalised (0..1) position of control point ([row], [col]) at [timeSeconds]. */
fun atmosphereControlPoint(row: Int, col: Int, timeSeconds: Float, seed: PointSeed): Offset {
    val last = ATMOSPHERE_GRID - 1
    val baseX = col / last.toFloat()
    val baseY = row / last.toFloat()
    val onVerticalEdge = col == 0 || col == last
    val onHorizontalEdge = row == 0 || row == last
    if (onVerticalEdge && onHorizontalEdge) return Offset(baseX, baseY)
    val dx = ATMOSPHERE_AMPLITUDE * sin(TWO_PI * timeSeconds / seed.periodX + seed.phaseX)
    val dy = ATMOSPHERE_AMPLITUDE * cos(TWO_PI * timeSeconds / seed.periodY + seed.phaseY)
    return when {
        onVerticalEdge -> Offset(baseX, baseY + dy * EDGE_AMPLITUDE_FACTOR)
        onHorizontalEdge -> Offset(baseX + dx * EDGE_AMPLITUDE_FACTOR, baseY)
        else -> Offset(baseX + dx, baseY + dy)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :sharedUI:testDebugUnitTest --tests "org.arcana.mobile.theme.AtmosphereMathTest"`
Expected: PASS (5 tests).

- [ ] **Step 5: Compile the iOS target too**

Run: `./gradlew :sharedUI:compileKotlinIosSimulatorArm64`
Expected: BUILD SUCCESSFUL.

---

### Task 2: Platform gates

**Files:**
- Create: `sharedUI/src/commonMain/kotlin/org/arcana/mobile/theme/AtmospherePlatform.kt`
- Create: `sharedUI/src/androidMain/kotlin/org/arcana/mobile/theme/AtmospherePlatform.android.kt`
- Create: `sharedUI/src/iosMain/kotlin/org/arcana/mobile/theme/AtmospherePlatform.ios.kt`

**Interfaces:**
- Produces: `expect fun meshGradientSupported(): Boolean`; `@Composable expect fun systemAllowsAmbientMotion(): Boolean` (false under Reduce Motion, Low Power Mode, or animations disabled).

- [ ] **Step 1: Common declarations**

```kotlin
package org.arcana.mobile.theme

import androidx.compose.runtime.Composable

/** False where the mesh path is unproven (Android below 10 draws vertices in software). */
expect fun meshGradientSupported(): Boolean

/** False under Reduce Motion, Low Power Mode, or animations turned off. Read at composition. */
@Composable
expect fun systemAllowsAmbientMotion(): Boolean
```

- [ ] **Step 2: Android actual**

```kotlin
package org.arcana.mobile.theme

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

actual fun meshGradientSupported(): Boolean = Build.VERSION.SDK_INT >= 29

@Composable
actual fun systemAllowsAmbientMotion(): Boolean {
    val context = LocalContext.current
    val powerSave = (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode == true
    val animatorScale = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    return !powerSave && animatorScale > 0f
}
```

- [ ] **Step 3: iOS actual**

```kotlin
package org.arcana.mobile.theme

import androidx.compose.runtime.Composable
import platform.Foundation.NSProcessInfo
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled

actual fun meshGradientSupported(): Boolean = true

@Composable
actual fun systemAllowsAmbientMotion(): Boolean =
    !UIAccessibilityIsReduceMotionEnabled() && !NSProcessInfo.processInfo.lowPowerModeEnabled
```

- [ ] **Step 4: Compile both targets**

Run: `./gradlew :sharedUI:compileDebugKotlinAndroid :sharedUI:compileKotlinIosSimulatorArm64`
Expected: BUILD SUCCESSFUL.

---

### Task 3: The `Atmosphere` composable

**Files:**
- Create: `sharedUI/src/commonMain/kotlin/org/arcana/mobile/theme/Atmosphere.kt`
- Delete: `sharedUI/src/commonMain/kotlin/org/arcana/mobile/theme/AmbientGlow.kt`

**Interfaces:**
- Consumes: Task 1 math, Task 2 gates.
- Produces: `@Composable fun Atmosphere(modifier: Modifier = Modifier)`. Fills its parent; place it as the first child of a `Box` so content draws over it.

- [ ] **Step 1: Write the composable**

```kotlin
package org.arcana.mobile.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.MeshGradientPainter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.preferredFrameRate
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlin.random.Random

private const val FRAME_RATE = 30f

/**
 * The living surface every Stone screen sits on. Draws the spec's 4×4 mesh in
 * its own layer at a preferred 30 fps, drifts only while resumed and while the
 * system allows ambient motion, and falls back to a still brush where the mesh
 * is unproven.
 */
@Composable
fun Atmosphere(modifier: Modifier = Modifier) {
    val seeds = remember { atmosphereSeeds(Random.Default) }
    val motionAllowed = systemAllowsAmbientMotion()
    var resumed by remember { mutableStateOf(true) }
    LifecycleResumeEffect(Unit) {
        resumed = true
        onPauseOrDispose { resumed = false }
    }
    var time by remember { mutableFloatStateOf(0f) }
    val running = motionAllowed && resumed
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { now -> time = (now - start) / 1_000_000_000f }
        }
    }

    val vignette = Modifier.drawWithContent {
        drawContent()
        val long = maxOf(size.width, size.height)
        drawRect(
            Brush.radialGradient(
                0.3f to ATMOSPHERE_VIGNETTE.copy(alpha = 0f),
                1f to ATMOSPHERE_VIGNETTE.copy(alpha = ATMOSPHERE_VIGNETTE_ALPHA),
                center = Offset(size.width / 2f, size.height / 2f),
                radius = long * 0.78f,
            )
        )
    }

    if (!meshGradientSupported()) {
        Box(
            modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Stone, ATMOSPHERE_COLORS[1], Stone)))
                .then(vignette),
        )
        return
    }

    val painter = remember(seeds) {
        MeshGradientPainter(rows = ATMOSPHERE_GRID - 1, columns = ATMOSPHERE_GRID - 1, hasBicubicColor = false) {
            val t = time
            for (row in 0 until ATMOSPHERE_GRID) for (col in 0 until ATMOSPHERE_GRID) {
                val i = row * ATMOSPHERE_GRID + col
                setVertex(
                    row = row,
                    column = col,
                    position = atmosphereControlPoint(row, col, t, seeds[i]),
                    color = ATMOSPHERE_COLORS[i],
                )
            }
        }
    }
    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer()
            .preferredFrameRate(FRAME_RATE)
            .background(Stone)
            .paint(painter)
            .then(vignette),
    )
}
```

The painter's block runs on every draw and reads `time`, a snapshot state, so each new frame invalidates only this layer. If `MeshGradientPainter`'s constructor or `setVertex` parameters differ from the above, open `androidx.compose.ui.graphics.MeshGradientPainter` in the IDE (artifact `androidx.compose.ui:ui:1.12.0`; on iOS the same symbols are in the CMP klib) and match them exactly; do not switch to a different rendering approach.

- [ ] **Step 2: Delete `AmbientGlow.kt`**

Run: `git rm sharedUI/src/commonMain/kotlin/org/arcana/mobile/theme/AmbientGlow.kt` (it has no call sites; confirm with `grep -rn AmbientNectarGlow sharedUI/src` returning only the file itself before removal).

- [ ] **Step 3: Compile both targets**

Run:
```
./gradlew :sharedUI:compileDebugKotlinAndroid :sharedUI:compileKotlinIosSimulatorArm64
```
Expected: BUILD SUCCESSFUL. A `@RememberInComposition` warning on the painter is expected to be absent because it is wrapped in `remember`.

---

### Task 4: Parity check on Home, both platforms

**Files:**
- Modify: `sharedUI/src/commonMain/kotlin/org/arcana/mobile/home/HomeScreen.kt:135`
- Create: `tools/regression/atmosphere_sample.py`

**Interfaces:**
- Consumes: `Atmosphere()` (Task 3).

- [ ] **Step 1: Put the atmosphere under Home**

At `HomeScreen.kt:135` the root is `Box(modifier = Modifier.fillMaxSize().background(Stone))` (or the equivalent `.background(Stone)` on the screen root; read the surrounding ten lines first). Change it to a `Box(Modifier.fillMaxSize())` whose first child is `Atmosphere()` and whose existing content follows unchanged:

```kotlin
Box(modifier = Modifier.fillMaxSize()) {
    Atmosphere()
    // existing content, unchanged
}
```

If the root was a `Column` or `LazyColumn` with `.background(Stone)`, wrap it in the `Box` above and drop the background modifier. Import `org.arcana.mobile.theme.Atmosphere`.

- [ ] **Step 2: Write the sampler**

```python
#!/usr/bin/env python3
"""Sample a screenshot's background band for the atmosphere spec.

Usage: atmosphere_sample.py <shot.png> <x0 y0 x1 y1>
Crop a region with no UI on it (e.g. the empty area under the Home cards).
Passes when: luminance stays within 205..245, no pixel has red > green + 2
(the brown check), and no pixel exceeds luminance 250 (the white check).
"""
import sys
from PIL import Image

path, x0, y0, x1, y1 = sys.argv[1], *map(int, sys.argv[2:6])
px = Image.open(path).convert("RGB").crop((x0, y0, x1, y1)).getdata()
lums = [0.299 * r + 0.587 * g + 0.114 * b for r, g, b in px]
brown = sum(1 for r, g, b in px if r > g + 2)
white = sum(1 for l in lums if l > 250)
lo, hi = min(lums), max(lums)
print(f"luminance {lo:.0f}..{hi:.0f}  brown px {brown}  white px {white}  n {len(lums)}")
ok = 205 <= lo and hi <= 245 and brown == 0 and white == 0
print("PASS" if ok else "FAIL")
sys.exit(0 if ok else 1)
```

- [ ] **Step 3: Run both simulators and capture**

Android: `./gradlew :androidApp:installDebug`, open the app, `adb exec-out screencap -p > /tmp/atm-android.png`. iOS: build and launch on the iPhone 17 Pro simulator, `xcrun simctl io booted screenshot /tmp/atm-ios.png`. Run the sampler on a UI-free crop of each (on Home, the band below the credits card). Expected: `PASS` on both. Then take a second capture 3 seconds later on each and confirm the crop differs (`python3 -c "from PIL import ImageChops, Image; print(ImageChops.difference(Image.open('a.png'), Image.open('b.png')).getbbox())"` prints a box, not `None`): the surface is moving.

- [ ] **Step 4: Compare with the prototype by eye**

Open Felicia's path page, card O, Speed ×1, Presence Quiet, Overlay Bare, beside the simulator. The green should sit in the middle and breathe there; the rim pale lime; nothing white, grey or brown. If the simulator shows creases where two points approach, set `hasBicubicColor = true` in Task 3 and re-check; if it then shows ridges (bands brighter or darker than any control colour), return to `false` and reduce `ATMOSPHERE_AMPLITUDE` to 0.12f. Record which setting shipped in the report. Keep a screenshot pair (prototype and each platform) for the report.

---

### Task 5: Every Stone root

**Files:**
- Modify: the screen list in File Structure.

**Interfaces:**
- Consumes: `Atmosphere()`.

- [ ] **Step 1: Apply the same wrap to each screen**

For each of these, find the root `.background(Stone)` at the cited line and apply the Task 4 Step 1 pattern (a `Box` with `Atmosphere()` first, content unchanged, background modifier removed):

`schedule/ScheduleScreen.kt:233`, `schedule/ClassDetailScreen.kt:227`, `profile/EditProfileScreen.kt:144` and `:377`, `auth/AuthScreen.kt:101`, `auth/PasswordResetRequestScreen.kt:62`, `signup/SignupSurveyScreen.kt:81`, `signup/SignupCompletionScreen.kt:151`, `:445` and `:500`, `studios/StudioSelectionScreen.kt:76`, `concierge/ConciergeRequestScreen.kt:72` and `:141`, `booking/MyBookingsScreen.kt:41`.

Where a screen already wraps content in a `Box` (Class detail does, for the sticky CTA), add `Atmosphere()` as the first child instead of nesting another `Box`.

- [ ] **Step 2: The You screen keeps its Ink hero**

`profile/ProfileScreen.kt:194` is `Box(modifier = Modifier.fillMaxSize().background(Stone))` with an Ink strip at `:199` and `StoneWrap` items at `:424`. Change the root to `Box(Modifier.fillMaxSize())`, add `Atmosphere()` as its first child (before the Ink strip, so the strip covers the top 55% and the atmosphere shows below it), and make `StoneWrap` transparent: replace its `.background(Stone)` with nothing (the helper can remain as a padding wrapper). Confirm the top overscroll still reads Ink and the bottom overscroll now shows the atmosphere, on iOS.

- [ ] **Step 3: Search's reveal surface**

`search/SearchScreen.kt:167` paints `lerp(Paper, Stone, progress)` inside the clipped reveal box. Keep that background and add, as the first child inside that box:

```kotlin
Atmosphere(Modifier.matchParentSize().graphicsLayer { alpha = progress })
```

so the surface fades in with the oval and the Paper-to-Stone lerp still reads as the pill growing. Import `androidx.compose.ui.graphics.graphicsLayer` if absent.

- [ ] **Step 4: The sticky CTA's slab**

In `ClassDetailScreen.kt` `StickyReserveCta`, the 40 dp fade at `:1102-1111` and the slab at `:1113` and the home-indicator strip at `:1184` paint opaque Stone, which would read as a flat band over the surface. Change all three to `Stone.copy(alpha = 0.92f)` (the fade's end colour, the slab background, and the strip background). The snackbar offset arithmetic above it is unaffected.

- [ ] **Step 5: Compile both targets and run all UI tests**

Run:
```
./gradlew :sharedUI:compileDebugKotlinAndroid :sharedUI:compileKotlinIosSimulatorArm64
./gradlew :sharedUI:testDebugUnitTest
```
Expected: BUILD SUCCESSFUL; tests pass.

- [ ] **Step 6: Walk every screen on both simulators**

Home, Book, You, Search (open and close, the oval still reads as the pill growing), a class detail, My bookings, Studio selection, Edit profile, Concierge, and the signed-out Auth screen (sign out from You, then sign back in with the test account). On each: the surface is present, text stays legible on the olive points (Ink on `#C5CCA6` is 8.7:1), and nothing flashes flat Stone on entry. Screenshot each for the report.

---

### Task 6: The frame budget

**Files:**
- No code. Uses `docs/perf/scroll_protocol.sh` and `docs/perf/analyze_frames.py`.

- [ ] **Step 1: Read the protocol**

Read `docs/perf/README.md` end to end. It defines the flick and slow-drag recordings on the Book tab and the dropped-frame analysis that produced the 1.9% baseline.

- [ ] **Step 2: Record with the atmosphere on**

Run the protocol on the iPhone 17 Pro simulator with this branch. Record the flick and slow-drag numbers.

- [ ] **Step 3: Record with the atmosphere off**

Temporarily make `Atmosphere()` return after drawing a plain `Box(modifier.fillMaxSize().background(Stone))` (a one-line early return at the top of the composable), rebuild, and run the same protocol. Revert the early return afterwards; confirm with `git diff` that only the intended files remain changed.

- [ ] **Step 4: Compare**

Expected: on-versus-off within the protocol's round-to-round spread (the README quotes permutation p-values; use the same test). If the atmosphere costs frames, first confirm `preferredFrameRate(30f)` is on the layer (a 120 Hz simulator device shows the difference most), then reduce the mesh to `hasBicubicColor = false` if it was true, then report the numbers to Cole before changing the recipe. Do not silently lower the amplitude to pass.

- [ ] **Step 5: The Android 8 fallback**

Create or start an API 26 emulator (`android emulator list`; create one with the Android CLI if none exists), install the debug build, open Home. Expected: a still vertical Stone-to-lime-tint-to-Stone brush with the vignette, no crash, no black surface. Screenshot for the report. Then, as a stretch, temporarily force `meshGradientSupported()` to true on that emulator and note whether the mesh renders correctly in software; if it does, record it as evidence to lift the gate in a later phase, but leave the gate at 29 in this branch.

- [ ] **Step 6: Reduce Motion and Low Power Mode**

iOS simulator: Settings → Accessibility → Motion → Reduce Motion on; relaunch; the surface is present and still. Android emulator: Developer options → Animator duration scale → off; relaunch; still. Turn both back on afterwards.

---

### Task 7: Inventory and report

- [ ] **Step 1: Update `docs/regression/inventory.md`**

For every screen touched in Task 5, append to its **Expected**: "Background is the living atmosphere (green held in the middle, breathing slowly); still under Reduce Motion or Low Power Mode." Add one new entry under Launch & Session or Platform-Specific: `PLAT-<next id> — Atmosphere still fallback` with Steps "Enable Reduce Motion (iOS) or set animator scale to off (Android), launch" and Expected "Surface renders with no drift; no crash on Android 8 (plain brush)". Run `tools/regression/self_audit.sh`; expected `FINDINGS: 0`.

- [ ] **Step 2: Leave the tree uncommitted and report**

Report: the files touched, both-platform screenshots of every screen, the perf numbers on and off, the API 26 screenshot, which `hasBicubicColor` shipped, and the regression read: presentation only, `:sharedLogic` untouched, no booking paths.

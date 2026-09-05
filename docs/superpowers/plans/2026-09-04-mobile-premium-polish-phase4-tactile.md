# Premium Polish Phase 4: Tactile Pass Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Every control answers when pressed and sits on the atmosphere with depth: pressable and shadows on all pills, chips, wells and cards; animated selection everywhere; the accordion chevron rotates; filter panels animate their size; capacity bars animate their width; cards and pills become translucent Paper (decision H) so they sit in the surface rather than on it.

**Architecture:** Mechanical application of phase 0's `pressable`, `pressedShade`, `controlShadow`, `softShadow`, `cardShadow`, `innerHighlight` and `ArcanaShapes` across `ui/` and the screens, plus `animateColorAsState`, `animateContentSize` and `animateFloatAsState` at the existing instant state flips the inventory found. One new token, `Surface.Translucent`, for the Paper-at-72% fill.

**Tech Stack:** phase 0 primitives, `androidx.compose.animation.animateContentSize`, `Modifier.rotate`.

Global constraints: see `2026-09-04-mobile-premium-polish.md`. Branch: `feature/polish-4-tactile` from `main` after phases 0 and 1 merge (the translucent surfaces need the atmosphere under them).

---

## File Structure

**Create**
| File | Responsibility |
|---|---|
| `sharedUI/src/commonMain/kotlin/org/arcana/mobile/theme/Surfaces.kt` | `val PaperTranslucent: Color` and `val PaperTranslucentAlpha`. |

**Modify**
| File | Change |
|---|---|
| `ui/Buttons.kt` `TextLink`, `IconCircle` | Press alpha / press scale. |
| `ui/FilterChip.kt` | Press scale, Control shadow, translucent fill on the × well. |
| `ui/ErrorState.kt` `RetryButton`, `InlineError` | Pressable + Control shadow; Card shadow + translucent Paper. |
| `ui/StudioAccordion.kt` | Chevron rotation animates; card pressable; Card shadow; translucent Paper. |
| `schedule/ScheduleScreen.kt` `FilterPill` (~1258), `ScheduleFilterSection` panels (782-886), scope toggle (934-1041), capacity pips (1402, 1453) | Pressable, animated colours, `animateContentSize`, animated pip fill. |
| `schedule/ClassDetailScreen.kt` hero card (711-720), capacity pips (994), `StickyReserveCta` (1132-1176) | Card shadow, animated pips, pressable CTA. |
| `home/HomeScreen.kt` cards (175, 208, 461, 687) | Card shadow; translucent Paper on Paper cards. |
| `schedule/ScheduleScreen.kt` favourites nudge card, search entry pill (632-663) | Translucent Paper, Soft shadow, pressable. |
| `docs/regression/inventory.md` | Notes on press response per control. |

---

### Task 1: The translucent surface token

**Files:**
- Create: `sharedUI/src/commonMain/kotlin/org/arcana/mobile/theme/Surfaces.kt`

**Interfaces:**
- Produces: `val PaperTranslucent: Color` (Paper at 72%). Every card, pill and sheet fill in this phase reads `PaperTranslucent`, never `Paper` directly, so reverting to opaque is a one-line change to this file. No runtime switch: the phase report shows both founders the result and asks; if they prefer opaque, the value becomes `Paper` before the PR merges.

- [ ] **Step 1: Write the token**

```kotlin
package org.arcana.mobile.theme

/** Decision H: cards, pills and sheets as Paper at 72% so they sit in the atmosphere rather than on it. */
private const val PAPER_TRANSLUCENT_ALPHA = 0.72f
val PaperTranslucent = Paper.copy(alpha = PAPER_TRANSLUCENT_ALPHA)
```

- [ ] **Step 2: Compile both targets**

Run the two compile commands. Expected: BUILD SUCCESSFUL.

---

### Task 2: The `ui/` primitives

**Files:**
- Modify: `ui/Buttons.kt` (`TextLink`, `IconCircle`), `ui/FilterChip.kt`, `ui/ErrorState.kt` (`RetryButton`, `InlineError`), `ui/StudioAccordion.kt`

- [ ] **Step 1: `TextLink` presses with alpha**

In `TextLink`, replace `modifier = modifier.clickable(onClick = onClick)` on the `Row` with:

```kotlin
    val source = remember { MutableInteractionSource() }
    val pressed by rememberPressed(source)
    val alpha by animateFloatAsState(if (pressed) 0.7f else 1f, Springs.Snappy, label = "linkAlpha")
    Row(
        modifier = modifier
            .graphicsLayer { this.alpha = alpha }
            .clickable(interactionSource = source, indication = null, onClick = onClick),
```

- [ ] **Step 2: `IconCircle` presses with scale**

In `IconCircle`, replace `.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)` with:

```kotlin
            .then(
                if (onClick != null) {
                    val source = remember { MutableInteractionSource() }
                    Modifier
                        .pressable(source, pressedScale = 0.94f)
                        .clickable(interactionSource = source, indication = null, onClick = onClick)
                } else Modifier
            )
```

(`remember` inside a `then` block is a composable call in a composable function; keep it above the `Box` as `val source = remember { MutableInteractionSource() }` if the linter objects, and use `if (onClick != null) Modifier.pressable(source, 0.94f).clickable(...) else Modifier`.) Add `.softShadow(CircleShape)` after `.size(diameter.dp)` when `background != Color.Transparent`, so filled wells get depth and outlined ones stay flat.

- [ ] **Step 3: `FilterChip`**

The chip's `Row` gains `.controlShadow(ArcanaShapes.Pill)` before `.clip(CircleShape)`, and the × well's `.clickable(onClick = onRemove)` becomes a pressable click (same `source` pattern as above, `pressedScale = 0.9f`).

- [ ] **Step 4: `RetryButton` and `InlineError`**

`RetryButton` (`ErrorState.kt:127`): add `.controlShadow(RoundedCornerShape(14.dp))` before its clip and make its clickable pressable (fill darkens with `Moss.pressedShade()` via `animateColorAsState`, exactly as `PrimaryCta` does in phase 0). `InlineError` (`:263`): `.background(Paper)` → `.background(PaperTranslucent)` and add `.cardShadow(RoundedCornerShape(14.dp))` before the clip.

- [ ] **Step 5: `StudioAccordion`**

`StudioAccordion.kt:155` rotates the chevron with `Modifier.rotate(if (expanded) 180f else 0f)`. Replace with:

```kotlin
    val chevron by animateFloatAsState(if (expanded) 180f else 0f, tween(Dur.Short, easing = Ease.Emphasized), label = "chevron")
    ... Modifier.rotate(chevron)
```

The card (`:72-74`): `.background(if (chosen) Ink else Paper)` → chosen stays `Ink`, unchosen becomes `PaperTranslucent`, animated with `animateColorAsState(tween(Dur.Short))`; add `.cardShadow(RoundedCornerShape(16.dp))` before the clip; the whole card's click becomes pressable at 0.99f. The 40 dp check well's click becomes pressable at 0.9f.

- [ ] **Step 6: Compile both targets and run UI tests**

Run the compile commands and `./gradlew :sharedUI:testDebugUnitTest`. Expected: BUILD SUCCESSFUL, tests pass.

---

### Task 3: Schedule controls

**Files:**
- Modify: `schedule/ScheduleScreen.kt`

- [ ] **Step 1: `FilterPill` (~line 1258)**

The pill's fill flips instantly between Ink and outlined. Replace its background/border colours with `animateColorAsState(tween(Dur.Short))` values, add `.controlShadow(ArcanaShapes.Pill)` when filled and `.softShadow(ArcanaShapes.Pill)` with `PaperTranslucent` fill when outlined, and make the click pressable (0.97f) with `indication = null`.

- [ ] **Step 2: Filter panels animate their size (782-886)**

Each `if (expandedSection == "...") { Panel() }` becomes:

```kotlin
AnimatedVisibility(
    visible = expandedSection == "time",
    enter = expandVertically(tween(Dur.Medium, easing = Ease.Emphasized)) + fadeIn(tween(Dur.Short)),
    exit = shrinkVertically(tween(Dur.Short, easing = Ease.Exit)) + fadeOut(tween(Dur.Quick)),
) { TimePanel(...) }
```

for each of the four sections at lines 782, 810, 877 and 886 (use each section's own key string). Wrap the section's outer `Column` in `.animateContentSize(tween(Dur.Medium, easing = Ease.Emphasized))` so the list below moves smoothly.

- [ ] **Step 3: Scope toggle (934-1041)**

It already tracks the finger with `Animatable` and settles with the default spring. Change the three `offset.animateTo(...)` calls at `:983`, `:1021` and `:1031` to pass `Springs.Settle` explicitly, and give the Ink highlight (`:1001`) `.controlShadow(ArcanaShapes.Pill)`.

- [ ] **Step 4: Capacity pips (1402, 1453) and the row's availability bar**

Wherever a pip or bar draws its filled fraction, animate it on first composition:

```kotlin
val fill = remember { Animatable(0f) }
LaunchedEffect(target) { fill.animateTo(target, tween(Dur.Medium, easing = Ease.Emphasized)) }
```

and use `fill.value` for the drawn fraction. Keyed on the session id via `remember(session.id)` so scrolling does not replay it for the same row.

- [ ] **Step 5: Search entry pill (632-663) and the favourites nudge card**

`SearchEntryPill`: `Paper` → `PaperTranslucent`, add `.softShadow(CircleShape)`, click pressable at 0.96f. Nudge card: `.background(Paper)` → `PaperTranslucent`, add `.cardShadow(RoundedCornerShape(16.dp))`; the CHOOSE FAVORITES `TextLink` already presses (Task 2).

- [ ] **Step 6: Compile and verify on both simulators**

Book tab: press each pill and chip (they scale and darken), expand Time and Modalities (they open and close smoothly, the list below follows), toggle the scope (settles on the spring), the capacity bars fill in on first appearance. Screenshot the expanded filter section.

---

### Task 4: Class detail and Home

**Files:**
- Modify: `schedule/ClassDetailScreen.kt`, `home/HomeScreen.kt`

- [ ] **Step 1: Detail hero card (711-720)**

Add `.cardShadow(RoundedCornerShape(14.dp))` before the clip. Keep the studio-colour gradient and border.

- [ ] **Step 2: Detail capacity pips (994)**

Same animated fill as Task 3 Step 4.

- [ ] **Step 3: Sticky CTA (1132-1176)**

The pill `Row` with `.clickable(enabled = enabled, onClick = onClick)`: add the `PrimaryCta` press pattern (source, `pressable(source, enabled)`, `controlShadow(RoundedCornerShape(22.dp))` when enabled, fill via `animateColorAsState` to `pillColor.pressedShade()` while pressed, `indication = null`). The arrow well kicks 2 dp like `PrimaryCta`.

- [ ] **Step 4: Home cards (175, 208, 461, 687)**

Add `.cardShadow(RoundedCornerShape(20.dp))` to each; the two Paper cards (`:175`, `:208`) use `PaperTranslucent`; the Moss next-booking card (`:461`) and the Ink credits card (`:687`) keep their fills and gain `innerHighlight(RoundedCornerShape(20.dp))`. Any tappable card gets the pressable pattern at 0.98f.

- [ ] **Step 5: Compile and verify on both simulators**

Home: cards lift off the surface, the Paper ones let the atmosphere show through faintly. Class detail: hero and CTA have depth; the CTA presses. Screenshot Home and a class detail on each platform. Run `tools/regression/measure_centering.py` on the sticky CTA pill (fill `283b15`) and on ALL STUDIOS (fill `161812`): under 0.5 pt.

---

### Task 5: Inventory and report

- [ ] **Step 1: Update `docs/regression/inventory.md`**

Add one line to the **Expected** of each affected control's entry: "presses scale to 97% with a darkened fill" (pills, CTA), "expands and collapses smoothly" (filter panels), "capacity bar fills in on appearance", "cards are translucent Paper with a soft shadow". Run `tools/regression/self_audit.sh`; expected `FINDINGS: 0`.

- [ ] **Step 2: Leave the tree uncommitted and report**

Files touched, both-platform screenshots including one pair of the same screen with `PaperTranslucent` and with opaque `Paper` (build the opaque pair by temporarily setting the token to `Paper`, capturing, and reverting), the centring measurements, and the regression read: presentation only; no logic, no navigation, no booking paths. Call decision H out explicitly in the report and wait for Cole's answer before the PR goes up; if opaque wins, set the token to `Paper` in the same branch.

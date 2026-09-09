@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package org.arcana.mobile.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import org.arcana.mobile.schedule.DayRail
import org.arcana.mobile.schedule.SkeletonClassRow
import org.arcana.mobile.schedule.titleCase
import org.arcana.mobile.theme.ArcanaShapes
import org.arcana.mobile.theme.Ash
import org.arcana.mobile.theme.Ash2
import org.arcana.mobile.theme.Atmosphere
import org.arcana.mobile.theme.BurntNectar
import org.arcana.mobile.theme.Charcoal
import org.arcana.mobile.theme.Clay
import org.arcana.mobile.theme.ClayDeep
import org.arcana.mobile.theme.Danger
import org.arcana.mobile.theme.Dur
import org.arcana.mobile.theme.Ease
import org.arcana.mobile.theme.Graphite
import org.arcana.mobile.theme.Info
import org.arcana.mobile.theme.Ink
import org.arcana.mobile.theme.InkAlpha04
import org.arcana.mobile.theme.InkAlpha08
import org.arcana.mobile.theme.InkAlpha10
import org.arcana.mobile.theme.Lime
import org.arcana.mobile.theme.LimeBright
import org.arcana.mobile.theme.LimeDeep
import org.arcana.mobile.theme.Mist
import org.arcana.mobile.theme.Mist2
import org.arcana.mobile.theme.Moss
import org.arcana.mobile.theme.MossDeep
import org.arcana.mobile.theme.MossLight
import org.arcana.mobile.theme.Paper
import org.arcana.mobile.theme.Surface
import org.arcana.mobile.theme.Plate
import org.arcana.mobile.theme.Springs
import org.arcana.mobile.theme.Stone
import org.arcana.mobile.theme.Stone2
import org.arcana.mobile.theme.StoneAlpha10
import org.arcana.mobile.theme.StoneAlpha18
import org.arcana.mobile.theme.StoneAlpha55
import org.arcana.mobile.theme.StoneAlpha65
import org.arcana.mobile.theme.StoneAlpha72
import org.arcana.mobile.theme.Success
import org.arcana.mobile.theme.Warning
import org.arcana.mobile.theme.Wood
import org.arcana.mobile.theme.systemAllowsAmbientMotion
import org.arcana.mobile.ui.AccentText
import org.arcana.mobile.ui.ArcanaIcons
import org.arcana.mobile.ui.ArcanaSheet
import org.arcana.mobile.ui.ArcanaTab
import org.arcana.mobile.ui.ArcanaTabBar
import org.arcana.mobile.ui.BodyText
import org.arcana.mobile.ui.Caption
import org.arcana.mobile.ui.Display
import org.arcana.mobile.ui.DotMatrixLoader
import org.arcana.mobile.ui.DotMatrixLoaderCompact
import org.arcana.mobile.ui.FilterChip
import org.arcana.mobile.ui.Heading2
import org.arcana.mobile.ui.Heading3
import org.arcana.mobile.ui.HoldToConfirm
import org.arcana.mobile.ui.IconCircle
import org.arcana.mobile.ui.Overline
import org.arcana.mobile.ui.PrimaryCta
import org.arcana.mobile.ui.SectionRule
import org.arcana.mobile.ui.SlideToConfirm
import org.arcana.mobile.ui.StudioAccordionCard
import org.arcana.mobile.ui.TextLink
import org.arcana.mobile.ui.cardShadow
import org.arcana.mobile.ui.barShadow
import org.arcana.mobile.ui.controlShadow
import org.arcana.mobile.ui.innerHighlight
import org.arcana.mobile.ui.opticallyCentredCaps
import org.arcana.mobile.ui.pressable
import org.arcana.mobile.ui.recedeBehindSheet
import org.arcana.mobile.ui.rememberHaptics
import org.arcana.mobile.ui.safeContentPadding
import org.arcana.mobile.ui.shimmerBrush
import org.arcana.mobile.ui.softShadow

/**
 * Founders-only reference for the design system: every colour token, type
 * style, radius, shadow, control, motion spec and haptic verb on one page.
 * Reached from Developer Settings, itself behind the 10-tap wordmark gesture,
 * so it has no entry point in a store build.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun DesignSystemScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    BackHandler { onBack() }
    var demoTab by remember { mutableStateOf(ArcanaTab.Home) }
    var sheetOpen by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Atmosphere()
        Box(Modifier.fillMaxSize().recedeBehindSheet(sheetOpen)) {
            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .safeContentPadding(),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item { Header(onBack) }

                item { Section("Colours") }
                COLOUR_GROUPS.forEach { group ->
                    item { GroupLabel(group.name) }
                    items(group.tokens) { token -> ColourRow(token.first, token.second) }
                }

                item { Section("Type") }
                item { TypeSample("Display · 44sp") { Display("Display") } }
                item { TypeSample("Heading2 · 28sp") { Heading2("Heading two") } }
                item { TypeSample("Heading3 · 22sp") { Heading3("Heading three") } }
                item { TypeSample("Overline · 11sp") { Overline("Overline") } }
                item { TypeSample("BodyText · 15sp") { BodyText("The quick brown fox jumps over the lazy dog.") } }
                item { TypeSample("AccentText · 20sp") { AccentText("Momentum, not stillness.") } }
                item { TypeSample("Caption · 12sp") { Caption("Caption") } }

                item { Section("Shapes") }
                item { ShapesRow() }

                item { Section("Depth") }
                item { DepthGrid() }

                item { Section("Atmosphere") }
                item { AtmosphereSample() }

                item { Section("Controls") }
                item { ControlsBlock() }

                item { Section("Tactile") }
                item { TactileBlock() }

                item { Section("Gestures") }
                item { GesturesSample() }

                item { Section("Motion") }
                item { MotionTrack("Springs.Snappy", "damping 0.85 · stiffness medium", Springs.Snappy) }
                item { MotionTrack("Springs.Settle", "damping 0.90 · stiffness medium low", Springs.Settle) }
                item { MotionTrack("Springs.Kick", "damping 0.65 · stiffness medium", Springs.Kick) }
                item {
                    MotionTrack(
                        name = "tween(Dur.Medium, Ease.Emphasized)",
                        values = "340ms · cubic 0.2 0 0 1",
                        spec = tween(Dur.Medium, easing = Ease.Emphasized),
                    )
                }
                item {
                    MotionTrack(
                        name = "tween(Dur.Short, Ease.Exit)",
                        values = "200ms · cubic 0.3 0 0.8 0.15",
                        spec = tween(Dur.Short, easing = Ease.Exit),
                    )
                }

                item { Section("Navigation") }
                item { NavigationSample(onOpenSheet = { sheetOpen = true }) }

                item { Section("Schedule") }
                item { ScheduleSample() }

                item { Section("Tab bar") }
                item { TabBarSample(active = demoTab, onSelect = { demoTab = it }) }

                item { Section("Haptics") }
                item { HapticsGrid() }

                item { Section("Loaders") }
                item { LoadersRow() }
            }
        }
        // Sibling of the receding Box, not inside it: the sheet itself must stay full scale.
        if (sheetOpen) {
            ArcanaSheet(onDismissRequest = { sheetOpen = false }) {
                Heading3(text = "A sheet, opened", modifier = Modifier.padding(horizontal = 24.dp))
                BodyText(
                    text = "Surface over the atmosphere, a Mist handle, a 40% Ink scrim. This page " +
                        "is the only way to see it on Android without signing in.",
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun Header(onBack: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        IconCircle(
            icon = ArcanaIcons.Close,
            borderColor = Mist,
            onClick = onBack,
            contentDescription = "Back to developer settings",
        )
        Display(text = "Design system", size = 36)
        Overline(text = "Phase 0 · foundations", color = Ash)
    }
}

@Composable
private fun Section(label: String) {
    SectionRule(label = label, accent = true, modifier = Modifier.padding(top = 20.dp))
}

@Composable
private fun GroupLabel(label: String) {
    Overline(text = label, size = 10, color = Ash2, modifier = Modifier.padding(top = 8.dp))
}

// ---- Colours ----

private class ColourGroup(val name: String, val tokens: List<Pair<String, Color>>)

private val COLOUR_GROUPS = listOf(
    ColourGroup(
        "Brand primaries",
        listOf(
            "Lime" to Lime,
            "Moss" to Moss,
            "Stone" to Stone,
            "Wood" to Wood,
            "BurntNectar" to BurntNectar,
        ),
    ),
    ColourGroup(
        "Derived greens",
        listOf(
            "LimeBright" to LimeBright,
            "LimeDeep" to LimeDeep,
            "MossDeep" to MossDeep,
            "MossLight" to MossLight,
        ),
    ),
    ColourGroup(
        "Stone tones",
        listOf(
            "Stone2" to Stone2,
            "Paper" to Paper,
        ),
    ),
    ColourGroup(
        "Ink and structure",
        listOf(
            "Ink" to Ink,
            "Graphite" to Graphite,
            "Charcoal" to Charcoal,
        ),
    ),
    ColourGroup(
        "Warm neutrals",
        listOf(
            "Ash" to Ash,
            "Ash2" to Ash2,
            "Mist" to Mist,
            "Mist2" to Mist2,
            "Plate" to Plate,
        ),
    ),
    ColourGroup(
        "Functional",
        listOf(
            "Danger" to Danger,
            "Warning" to Warning,
            "Clay" to Clay,
            "ClayDeep" to ClayDeep,
            "Success" to Success,
            "Info" to Info,
        ),
    ),
    ColourGroup(
        "Translucent helpers",
        listOf(
            "InkAlpha10" to InkAlpha10,
            "InkAlpha08" to InkAlpha08,
            "InkAlpha04" to InkAlpha04,
            "StoneAlpha72" to StoneAlpha72,
            "StoneAlpha65" to StoneAlpha65,
            "StoneAlpha55" to StoneAlpha55,
            "StoneAlpha18" to StoneAlpha18,
            "StoneAlpha10" to StoneAlpha10,
            "Surface" to Surface,
        ),
    ),
)

private fun channelHex(value: Float): String =
    ((value * 255f) + 0.5f).toInt().coerceIn(0, 255).toString(16).uppercase().padStart(2, '0')

/** `#RRGGBB`, or `#AARRGGBB` when the token carries alpha. */
private fun hexOf(color: Color): String {
    val rgb = channelHex(color.red) + channelHex(color.green) + channelHex(color.blue)
    return if (color.alpha >= 1f) "#$rgb" else "#${channelHex(color.alpha)}$rgb"
}

@Composable
private fun ColourRow(name: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(color)
                .border(1.dp, Mist, CircleShape),
        )
        Caption(text = name, size = 14, color = Ink, modifier = Modifier.weight(1f))
        Overline(text = hexOf(color), size = 10, color = Ash)
    }
}

// ---- Type ----

@Composable
private fun TypeSample(label: String, sample: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Caption(text = label, color = Ash2)
        sample()
    }
}

// ---- Shapes ----

@Composable
private fun ShapesRow() {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ShapeTile("Chip", "12dp", ArcanaShapes.Chip, Modifier.weight(1f))
        ShapeTile("Card", "16dp", ArcanaShapes.Card, Modifier.weight(1f))
        ShapeTile("Hero", "14dp", ArcanaShapes.Hero, Modifier.weight(1f))
        ShapeTile("Sheet", "24dp top", ArcanaShapes.Sheet, Modifier.weight(1f))
        ShapeTile("Pill", "full", ArcanaShapes.Pill, Modifier.weight(1f))
    }
}

@Composable
private fun ShapeTile(name: String, radius: String, shape: Shape, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(shape)
                .background(Paper)
                .border(1.dp, Mist, shape),
        )
        Caption(text = name, color = Ink)
        Caption(text = radius, size = 10, color = Ash2)
    }
}

// ---- Depth ----

@Composable
private fun DepthGrid() {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            DepthTile("softShadow", "8dp blur · y 2 · ink 5%", Modifier.softShadow(ArcanaShapes.Card), Modifier.weight(1f))
            DepthTile("controlShadow", "18dp blur · y 6 · ink 14%", Modifier.controlShadow(ArcanaShapes.Card), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            DepthTile("cardShadow", "24dp blur · y 10 · ink 8%", Modifier.cardShadow(ArcanaShapes.Card), Modifier.weight(1f))
            DepthTile("barShadow", "24dp blur · y 8 · ink 12%", Modifier.barShadow(ArcanaShapes.Card), Modifier.weight(1f))
        }
        DepthTile(
            name = "controlShadow + innerHighlight",
            values = "stone 10% along the top edge of a dark fill",
            shadow = Modifier.controlShadow(ArcanaShapes.Card),
            fill = Moss,
            highlight = true,
        )
    }
}

@Composable
private fun DepthTile(
    name: String,
    values: String,
    shadow: Modifier,
    modifier: Modifier = Modifier,
    fill: Color = Paper,
    highlight: Boolean = false,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .then(shadow)
                .clip(ArcanaShapes.Card)
                .background(fill)
                .then(if (highlight) Modifier.innerHighlight(ArcanaShapes.Card) else Modifier),
        )
        Caption(text = name, color = Ink)
        Caption(text = values, size = 10, color = Ash2)
    }
}

// ---- Atmosphere ----

@Composable
private fun AtmosphereSample() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(ArcanaShapes.Card)
                .border(1.dp, Mist, ArcanaShapes.Card),
        ) {
            Atmosphere()
        }
        Caption(
            text = "16-point mesh · #D8DBB6 #CED4A4 #C2CA86 #9AA662 · amplitude 0.15 · " +
                "periods 6.0s and 7.5s scaled 0.8-1.4 · vignette 10%",
            size = 12,
            color = Ash,
            maxLines = 4,
        )
        val motion = systemAllowsAmbientMotion()
        Caption(
            text = when {
                !motion -> "Still · Reduce Motion, Low Power Mode or animations off"
                else -> "Live mesh · drifting"
            },
            size = 12,
            color = Ash2,
            maxLines = 2,
        )
    }
}

// ---- Controls ----

@Composable
private fun ControlsBlock() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Caption(text = "PrimaryCta", color = Ash2)
        PrimaryCta(label = "Sign in", onClick = {})
        Caption(text = "PrimaryCta · disabled", color = Ash2)
        PrimaryCta(label = "Sign in", onClick = {}, enabled = false)
        Caption(text = "PrimaryCta · Clay cancel variant", color = Ash2)
        PrimaryCta(
            label = "Cancel reservation",
            onClick = {},
            containerColor = Clay,
            accentColor = ClayDeep,
        )
        Caption(
            text = "Press and hold: scales to 97%, darkens, the well kicks on release.",
            size = 12,
            color = Ash,
            maxLines = 2,
        )

        Spacer(Modifier.height(4.dp))
        Caption(text = "IconCircle filled and outlined · TextLink", color = Ash2)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            IconCircle(
                icon = ArcanaIcons.Check,
                background = Moss,
                contentColor = Stone,
                onClick = {},
                contentDescription = "Filled icon circle sample",
            )
            IconCircle(
                icon = ArcanaIcons.Share,
                borderColor = Mist,
                onClick = {},
                contentDescription = "Outlined icon circle sample",
            )
            TextLink(label = "Text link", onClick = {})
        }

        Spacer(Modifier.height(4.dp))
        Caption(text = "FilterChip", color = Ash2)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(label = "Morning", onRemove = {})
            FilterChip(label = "Strength", onRemove = {})
        }
    }
}

// ---- Tactile ----

private val TACTILE_ATMOSPHERE_HEIGHT = 180.dp
private const val TACTILE_PILL_LABEL_SIZE = 12
private const val TACTILE_PILL_LABEL_TRACKING_EM = 0.22f

@Composable
private fun TactileBlock() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Caption(
            text = "Surface: every card, pill and sheet renders on this opaque warm off-white, " +
                "a hair greener than Paper so it belongs to the atmosphere, replacing the 72% " +
                "asked to make.",
            size = 12,
            color = Ash,
            maxLines = 3,
        )

        GroupLabel("Cards on the atmosphere")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(TACTILE_ATMOSPHERE_HEIGHT)
                .clip(ArcanaShapes.Card)
                .border(1.dp, Mist, ArcanaShapes.Card),
        ) {
            Atmosphere()
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // cardShadow draws a FILLED silhouette, not a ring, so the middle
                // tile alone carries a faint Ink veil inside its translucent fill;
                // the third tile isolates the fill alpha with no shadow at all.
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TactileCardTile(Paper, Modifier.cardShadow(ArcanaShapes.Card), Modifier.weight(1f))
                    TactileCardTile(Surface, Modifier.cardShadow(ArcanaShapes.Card), Modifier.weight(1f))
                    TactileCardTile(Surface, Modifier, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TactilePillTile(Surface, Modifier.weight(1f))
                    TactilePillTile(Paper, Modifier.weight(1f))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Caption(
                text = "Paper · opaque · cardShadow", size = 10, color = Ash2,
                maxLines = 2, modifier = Modifier.weight(1f),
            )
            Caption(
                text = "Surface · opaque · cardShadow (ships)", size = 10, color = Ash2,
                maxLines = 2, modifier = Modifier.weight(1f),
            )
            Caption(
                text = "Surface · opaque · no shadow", size = 10, color = Ash2,
                maxLines = 2, modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Caption(text = "Surface · opaque", size = 10, color = Ash2, modifier = Modifier.weight(1f))
            Caption(text = "Paper · opaque", size = 10, color = Ash2, modifier = Modifier.weight(1f))
        }

        Spacer(Modifier.height(4.dp))
        GroupLabel("FilterPill-style pill, outlined and filled")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TactileFilterPillSample(label = "MODALITIES", active = false, modifier = Modifier.weight(1f))
            TactileFilterPillSample(label = "TIME", active = true, modifier = Modifier.weight(1f))
        }

        Spacer(Modifier.height(4.dp))
        GroupLabel("StudioAccordionCard")
        var tactileChosen by remember { mutableStateOf(false) }
        var tactileExpanded by remember { mutableStateOf(false) }
        StudioAccordionCard(
            name = "Fulcrum Strength",
            locationCount = 3,
            chosen = tactileChosen,
            expanded = tactileExpanded,
            selectedLocationCount = if (tactileChosen) 3 else 0,
            onToggle = { tactileChosen = !tactileChosen },
            onToggleExpanded = { tactileExpanded = !tactileExpanded },
        )
    }
}

@Composable
private fun TactileCardTile(fill: Color, shadow: Modifier, modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(72.dp)
            .then(shadow)
            .clip(ArcanaShapes.Card)
            .background(fill)
            .border(1.dp, Mist, ArcanaShapes.Card),
    )
}

@Composable
private fun TactilePillTile(fill: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(44.dp)
            .softShadow(CircleShape)
            .clip(CircleShape)
            .background(fill)
            .border(1.dp, Mist, CircleShape),
    )
}

@Composable
private fun TactileFilterPillSample(label: String, active: Boolean, modifier: Modifier = Modifier) {
    val source = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .pressable(source, pressedScale = 0.97f)
            .then(if (active) Modifier.controlShadow(ArcanaShapes.Pill) else Modifier.softShadow(ArcanaShapes.Pill))
            .clip(CircleShape)
            .background(if (active) Ink else Surface)
            .border(1.dp, if (active) Ink else Mist, CircleShape)
            .clickable(interactionSource = source, indication = null, onClick = {})
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Overline(
            text = label,
            modifier = Modifier.opticallyCentredCaps(TACTILE_PILL_LABEL_SIZE.sp, TACTILE_PILL_LABEL_TRACKING_EM),
            size = TACTILE_PILL_LABEL_SIZE,
            color = if (active) Stone else Ink,
        )
    }
}

// ---- Gestures ----

@Composable
private fun GesturesSample() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Caption(
            text = "Demo only: no ViewModel here, so the slide never books. onConfirm " +
                "flashes the completed state and resets after a beat.",
            size = 12,
            color = Ash,
            maxLines = 3,
        )
        GroupLabel("Slide to book")
        var demoBooked by remember { mutableStateOf(false) }
        LaunchedEffect(demoBooked) { if (demoBooked) { delay(1400); demoBooked = false } }
        SlideToConfirm(
            label = "SLIDE TO BOOK",
            subLabel = null,
            onConfirm = { demoBooked = true },
            completed = demoBooked,
            modifier = Modifier.padding(horizontal = 24.dp),
        )

        Spacer(Modifier.height(4.dp))
        GroupLabel("Hold to cancel")
        var demoCancelled by remember { mutableStateOf(false) }
        LaunchedEffect(demoCancelled) { if (demoCancelled) { delay(1400); demoCancelled = false } }
        HoldToConfirm(
            label = if (demoCancelled) "CANCELLED" else "HOLD TO CANCEL",
            onConfirm = { demoCancelled = true },
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}

// ---- Motion ----

private val MOTION_DOT = 8.dp

@Composable
private fun MotionTrack(name: String, values: String, spec: AnimationSpec<Float>) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Caption(text = "$name · $values", color = Ash2, maxLines = 2)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(ArcanaShapes.Chip)
                .background(Mist2)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    scope.launch {
                        progress.animateTo(if (progress.targetValue == 0f) 1f else 0f, spec)
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    // Placement reads the animation, so each frame relayouts the
                    // dot instead of recomposing the track.
                    .layout { measurable, constraints ->
                        val dot = measurable.measure(constraints.copy(minWidth = 0))
                        layout(constraints.maxWidth, dot.height) {
                            dot.placeRelative(((constraints.maxWidth - dot.width) * progress.value).roundToInt(), 0)
                        }
                    }
                    .size(MOTION_DOT)
                    .clip(CircleShape)
                    .background(Lime),
            )
        }
    }
}

// ---- Navigation ----

@Composable
private fun NavigationSample(onOpenSheet: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GroupLabel("Sheet")
        PrimaryCta(label = "Open a sheet", onClick = onOpenSheet)
        GroupLabel("Push and pop")
        Caption(
            text = "Push: rises from 12% of the height + fades in, 340ms Ease.Emphasized · " +
                "the screen beneath scales out to 96% on the same curve.",
            size = 12,
            color = Ash,
            maxLines = Int.MAX_VALUE,
        )
        Caption(
            text = "Pop: the screen beneath scales back from 96%, 200ms Ease.Emphasized · " +
                "this screen slides down 12% + fades out, 200ms Ease.Exit.",
            size = 12,
            color = Ash,
            maxLines = Int.MAX_VALUE,
        )
        Caption(
            text = "The screen beneath only scales: the spec's 12% Ink dim is not implemented.",
            size = 12,
            color = Ash,
            maxLines = Int.MAX_VALUE,
        )
    }
}

// ---- Schedule ----

private const val SCHEDULE_SAMPLE_DAYS = 7

// Mirrors ScheduleScreen's two-way pager/rail binding without a ViewModel:
// the only place this is reachable on Android, which has no signed-in session.
@Composable
private fun ScheduleSample() {
    val days = remember {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        (0 until SCHEDULE_SAMPLE_DAYS).map { today.plus(it, DateTimeUnit.DAY) }
    }
    var selectedIndex by remember { mutableStateOf(0) }
    val pagerState = rememberPagerState(initialPage = 0) { days.size }

    LaunchedEffect(selectedIndex) {
        if (pagerState.currentPage != selectedIndex) {
            pagerState.animateScrollToPage(selectedIndex, animationSpec = Springs.Settle)
        }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { selectedIndex = it }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GroupLabel("Day rail + pager")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ArcanaShapes.Card)
                .background(Paper)
                .border(1.dp, Mist, ArcanaShapes.Card)
                .padding(vertical = 12.dp),
        ) {
            DayRail(
                days = days,
                position = { pagerState.currentPage + pagerState.currentPageOffsetFraction },
                selectedIndex = selectedIndex,
                onSelect = { i -> selectedIndex = i },
            )
            Spacer(Modifier.height(12.dp))
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth().height(120.dp)) { page ->
                val date = days.getOrNull(page) ?: return@HorizontalPager
                Box(Modifier.fillMaxSize().padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
                    Caption(text = "${titleCase(date.dayOfWeek.name)} · ${date.day}", color = Ink)
                }
            }
        }
        GroupLabel("Skeleton rows")
        val skeletonBrush = shimmerBrush()
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SkeletonClassRow(fill = 0.62f, brush = skeletonBrush)
            SkeletonClassRow(fill = 0.42f, brush = skeletonBrush)
        }
    }
}

// ---- Tab bar ----

@Composable
private fun TabBarSample(active: ArcanaTab, onSelect: (ArcanaTab) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(ArcanaShapes.Card)
                .border(1.dp, Mist, ArcanaShapes.Card),
        ) {
            ArcanaTabBar(active = active, onSelect = onSelect, avatarInitials = "PH")
        }
        Caption(
            text = "Android renders this bar in the app; iOS uses the native bar.",
            size = 12,
            color = Ash,
            maxLines = 2,
        )
    }
}

// ---- Haptics ----

private const val RAMP_PULSES = 5
private const val RAMP_STEP_MS = 90L

private class HapticSample(val label: String, val fire: () -> Unit)

@Composable
private fun HapticsGrid() {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val samples = remember(haptics, scope) {
        listOf(
            HapticSample("selection") { haptics.selection() },
            HapticSample("tick") { haptics.tick() },
            HapticSample("toggle on") { haptics.toggle(true) },
            HapticSample("toggle off") { haptics.toggle(false) },
            HapticSample("threshold") { haptics.threshold() },
            HapticSample("confirm") { haptics.confirm() },
            HapticSample("reject") { haptics.reject() },
            HapticSample("boundary") { haptics.boundary() },
            HapticSample("ramp") {
                scope.launch { repeat(RAMP_PULSES) { haptics.ramp(); delay(RAMP_STEP_MS) } }
            },
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        samples.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { HapticPill(it, Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Caption(
            text = "Simulators produce no haptics; feel these on a phone.",
            size = 12,
            color = Ash,
            maxLines = 2,
        )
    }
}

@Composable
private fun HapticPill(sample: HapticSample, modifier: Modifier = Modifier) {
    val source = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .pressable(source)
            .softShadow(ArcanaShapes.Pill)
            .clip(ArcanaShapes.Pill)
            .background(Paper)
            .border(1.dp, Mist, ArcanaShapes.Pill)
            .clickable(interactionSource = source, indication = null, onClick = sample.fire)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Caption(text = sample.label, size = 12, color = Ink)
    }
}

// ---- Loaders ----

@Composable
private fun LoadersRow() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DotMatrixLoader()
            Caption(text = "DotMatrixLoader", size = 10, color = Ash2)
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DotMatrixLoaderCompact()
            Caption(text = "DotMatrixLoaderCompact", size = 10, color = Ash2)
        }
    }
}

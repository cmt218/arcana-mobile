package org.arcana.mobile.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.arcana.mobile.data.FavoriteLocationDto
import org.arcana.mobile.data.FavoritesDto
import org.arcana.mobile.networking.ArcanaApiClient
import org.arcana.mobile.theme.Arcana
import org.arcana.mobile.theme.Ash
import org.arcana.mobile.theme.Ash2
import org.arcana.mobile.theme.Danger
import org.arcana.mobile.theme.Ink
import org.arcana.mobile.theme.Lime
import org.arcana.mobile.theme.Mist
import org.arcana.mobile.theme.Mist2
import org.arcana.mobile.theme.Moss
import org.arcana.mobile.theme.MossDeep
import org.arcana.mobile.theme.Paper
import org.arcana.mobile.theme.Stone
import org.arcana.mobile.theme.StoneAlpha18
import org.arcana.mobile.theme.StoneAlpha55
import org.arcana.mobile.ui.AccentText
import org.arcana.mobile.ui.ArcanaIcons
import org.arcana.mobile.ui.BodyText
import org.arcana.mobile.ui.CircleMonogram
import org.arcana.mobile.ui.Display
import org.arcana.mobile.ui.DotField
import org.arcana.mobile.ui.IconCircle
import org.arcana.mobile.ui.Overline
import org.arcana.mobile.ui.SectionRule
import org.arcana.mobile.ui.ShimmerBox
import org.arcana.mobile.ui.StrokeIcon
import org.arcana.mobile.ui.TextLink
import org.arcana.mobile.ui.safeContentPadding
import org.arcana.mobile.ui.safeHorizontalPadding
import org.jetbrains.compose.resources.DrawableResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Row labels for the "Your favorites" section: whole-Studio favorites first
 * (by name), then location-grain favorites as "STUDIO — LOCATION" with the
 * brand prefix stripped from the location name (mirrors `shortLabel()` in
 * ScheduleViewModel).
 */
private fun favoriteRowLabels(favorites: FavoritesDto): List<String> =
    favorites.studios.map { it.name.uppercase() } +
        favorites.locations.map { it.rowLabel().uppercase() }

private fun FavoriteLocationDto.rowLabel(): String {
    val raw = name.removePrefix(studioName).trim()
        .removePrefix("·").trim()
        .removePrefix("-").trim()
    return "$studioName — ${raw.ifEmpty { name }}"
}

private data class AccountItem(
    val icon: DrawableResource,
    val label: String,
    val right: String,
    val rightColor: Color,
    val onClick: (() -> Unit)? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onManageStudios: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val apiClient = koinInject<ArcanaApiClient>()
    val vm = koinViewModel<ProfileViewModel>()
    val state by vm.uiState.collectAsState()
    val refreshing by vm.isRefreshing.collectAsState()
    val favorites by vm.favorites.collectAsState()

    LaunchedEffect(Unit) { vm.load() }

    val accountItems = listOf(
        AccountItem(ArcanaIcons.Bell, "Notifications", "ON · 06:00", Ash),
        AccountItem(ArcanaIcons.Card, "Membership", "$540 / MO", Ash),
        AccountItem(ArcanaIcons.Support, "Concierge", "LIVE · 24/7", Ash),
    )

    // The body Stone fills everything; an Ink strip sits behind the top of
    // the screen so the full-bleed hero AND any iOS overscroll above it both
    // read as ink. The LazyColumn itself has a transparent background, so
    // wherever items don't reach (empty trailing space, or below the last
    // item) the Stone box bleeds through cleanly.
    Box(modifier = modifier.fillMaxSize().background(Stone)) {
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.55f)
                .background(Ink),
        )
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = vm::refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
        // Profile hero — full-bleed ink that extends behind the status bar.
        item { ProfileHero(state) }

        // YOUR FAVORITES header
        stoneItem {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 28.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Overline(text = "Your favorites", color = Moss)
                TextLink(label = "Manage", onClick = onManageStudios, underline = false)
            }
        }
        stoneItem { Spacer(Modifier.height(16.dp)) }
        val favoriteLabels = favorites?.let(::favoriteRowLabels)
        when {
            // Not loaded yet — shimmer a single row-sized placeholder,
            // matching the hero's shimmer treatment.
            favoriteLabels == null -> stoneItem {
                ShimmerBox(
                    modifier = Modifier
                        .padding(start = 24.dp, end = 24.dp, bottom = 8.dp)
                        .fillMaxWidth()
                        .height(64.dp),
                    shape = RoundedCornerShape(16.dp),
                )
            }
            // Loaded, member has none yet.
            favoriteLabels.isEmpty() -> stoneItem {
                BodyText(
                    text = "No favorites yet",
                    size = 14,
                    color = Ash,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
                )
            }
            else -> itemsIndexed(items = favoriteLabels) { idx, label ->
                StoneWrap {
                    FavoriteRow(
                        label = label,
                        idx = idx + 1,
                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
                    )
                }
            }
        }
        stoneItem {
            BodyText(
                text = "Favorites shape your schedule. Change anytime.",
                size = 12,
                color = Ash,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp),
            )
        }

        // ACCOUNT section
        stoneItem {
            SectionRule(
                label = "Account",
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 8.dp),
            )
        }
        items(items = accountItems) { item ->
            StoneWrap {
                AccountRow(item, modifier = Modifier.padding(horizontal = 24.dp))
            }
        }

        // SIGN OUT
        stoneItem {
            Row(
                modifier = Modifier
                    .padding(start = 24.dp, end = 24.dp, top = 32.dp)
                    .fillMaxWidth()
                    .drawTopRule()
                    .clickable { apiClient.logout() }
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StrokeIcon(ArcanaIcons.Logout, size = 16.dp, tint = Danger)
                Overline(text = "Sign out", size = 12, color = Danger)
            }
        }

        // Manifesto footer
        stoneItem {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AccentText(text = "Earned, never given.", size = 18, color = Ash)
                Overline(text = "Arcana · v2.4.0", size = 10, color = Ash2)
            }
        }
        }
        }
    }
}

/**
 * Wraps a LazyColumn item in a Stone-backed Box that also respects horizontal
 * display cutouts. Keeps the page's body content reading as Stone while the
 * full-bleed hero (and any iOS top-overscroll) reads as Ink, courtesy of the
 * Ink strip painted behind the LazyColumn in [ProfileScreen].
 */
private fun androidx.compose.foundation.lazy.LazyListScope.stoneItem(
    content: @Composable BoxScope.() -> Unit,
) = item {
    StoneWrap(content)
}

@Composable
private fun StoneWrap(content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Stone)
            .safeHorizontalPadding(),
        content = content,
    )
}

/**
 * Full-bleed Ink hero. Accepts [ProfileUiState] so it can shimmer the
 * data-driven fields (avatar initials, full name, member number, stats)
 * while serving the static chrome (dot-field, settings icon, structural layout)
 * immediately regardless of load state.
 */
@Composable
private fun ProfileHero(state: ProfileUiState) {
    val success = state as? ProfileUiState.Success

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Ink),
    ) {
        DotField(modifier = Modifier.matchParentSize(), color = Lime, alpha = 0.08f, spacing = 16)
        Column(
            modifier = Modifier
                .safeContentPadding()
                .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 32.dp),
        ) {
            // Top bar within hero
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Member number — shimmer while loading
                if (success != null) {
                    val memberLabel = if (success.memberNumber != null)
                        "Member · No. ${success.memberNumber}"
                    else
                        "Member"
                    Overline(text = memberLabel, size = 10, color = Lime)
                } else {
                    ShimmerBox(
                        modifier = Modifier
                            .width(140.dp)
                            .height(12.dp),
                        shape = RoundedCornerShape(4.dp),
                    )
                }
                IconCircle(
                    icon = ArcanaIcons.Settings,
                    diameter = 36,
                    iconSize = 16,
                    borderColor = StoneAlpha18,
                    contentColor = Stone,
                )
            }

            Spacer(Modifier.height(16.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Avatar — shimmer while loading
                if (success != null) {
                    Box(contentAlignment = Alignment.BottomEnd) {
                        Box(
                            modifier = Modifier
                                .size(116.dp)
                                .clip(CircleShape)
                                .background(MossDeep)
                                .border(2.dp, Lime, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = success.initials,
                                // All-caps initials sit optically high because the font
                                // reserves descent space the glyphs never use. Trim the
                                // line box, then nudge down ~0.09em to seat the caps on
                                // the circle's true center.
                                modifier = Modifier.offset(y = 4.dp),
                                style = TextStyle(
                                    fontFamily = Arcana.fonts.display,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 46.sp,
                                    lineHeight = 46.sp,
                                    lineHeightStyle = LineHeightStyle(
                                        alignment = LineHeightStyle.Alignment.Center,
                                        trim = LineHeightStyle.Trim.Both,
                                    ),
                                    letterSpacing = (-0.02).em,
                                    color = Lime,
                                ),
                            )
                        }
                        Box(
                            modifier = Modifier
                                .padding(end = 4.dp, bottom = 4.dp)
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(Ink)
                                .padding(4.dp)
                                .clip(CircleShape)
                                .background(Lime)
                        )
                    }
                } else {
                    ShimmerBox(
                        modifier = Modifier.size(116.dp),
                        shape = CircleShape,
                    )
                }

                // Full name — shimmer while loading
                if (success != null) {
                    Display(text = success.fullName, size = 36, color = Stone)
                } else {
                    ShimmerBox(
                        modifier = Modifier
                            .width(200.dp)
                            .height(36.dp),
                        shape = RoundedCornerShape(6.dp),
                    )
                }

                // Membership line: tier name + title-cased status — shimmer while loading
                if (success != null) {
                    val statusLabel = success.status.replaceFirstChar { it.uppercase() }
                    Overline(
                        text = "${success.tierName} · $statusLabel",
                        size = 10,
                        color = StoneAlpha55,
                    )
                } else {
                    ShimmerBox(
                        modifier = Modifier
                            .width(120.dp)
                            .height(10.dp),
                        shape = RoundedCornerShape(4.dp),
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // Stats row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawTopRule(StoneAlpha18)
                    .drawBottomRule(StoneAlpha18),
            ) {
                StatCell(
                    value = success?.lifetimeSessions?.toString(),
                    label = "Sessions",
                    modifier = Modifier.weight(1f),
                )
                Box(Modifier.width(1.dp).height(84.dp).background(StoneAlpha18))
                StatCell(
                    value = success?.weekStreak?.let { it.toString().padStart(2, '0') },
                    label = "Week streak",
                    modifier = Modifier.weight(1f),
                )
                Box(Modifier.width(1.dp).height(84.dp).background(StoneAlpha18))
                // Credits label: "Cap" when we have period data, otherwise "Credits".
                val creditsLabel = if (success?.creditsGranted != null) "Cap" else "Credits"
                val creditsValue = success?.creditsRemaining?.toString()
                StatCell(
                    value = creditsValue,
                    label = creditsLabel,
                    modifier = Modifier.weight(1f),
                )
            }

            // Error caption — unobtrusive, below stats
            if (state is ProfileUiState.Error) {
                Spacer(Modifier.height(8.dp))
                BodyText(
                    text = "Could not load profile.",
                    size = 12,
                    color = StoneAlpha55,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
        }
    }
}

/**
 * A stat cell that accepts a nullable [value]: null triggers a shimmer
 * placeholder sized to match the display-number text block.
 */
@Composable
private fun StatCell(value: String?, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (value != null) {
            Text(
                text = value,
                style = TextStyle(
                    fontFamily = Arcana.fonts.display,
                    fontWeight = FontWeight.Bold,
                    fontSize = 36.sp,
                    letterSpacing = (-0.02).em,
                    color = Lime,
                ),
            )
        } else {
            ShimmerBox(
                modifier = Modifier
                    .width(56.dp)
                    .height(36.dp),
                shape = RoundedCornerShape(6.dp),
            )
        }
        Overline(text = label, size = 10, color = StoneAlpha55)
    }
}

/**
 * A single favorite in the "Your favorites" section. [label] is the
 * pre-formatted row text from [favoriteRowLabels] (Studio name, or
 * "STUDIO — LOCATION" for location-grain favorites); [idx] is the 1-based
 * position rendered in the Moss number badge.
 */
@Composable
private fun FavoriteRow(label: String, idx: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Paper)
            .border(1.dp, Mist, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Moss),
            contentAlignment = Alignment.Center,
        ) {
            CircleMonogram(text = idx.toString().padStart(2, '0'), fontSize = 14, color = Lime)
        }
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = TextStyle(
                fontFamily = Arcana.fonts.display,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                letterSpacing = (-0.01).em,
                color = Ink,
            ),
        )
        StrokeIcon(ArcanaIcons.ChevronRight, size = 16.dp, tint = Ash)
    }
}

@Composable
private fun AccountRow(item: AccountItem, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (item.onClick != null) Modifier.clickable(onClick = item.onClick) else Modifier)
            .drawBottomRule()
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Mist2),
            contentAlignment = Alignment.Center,
        ) {
            StrokeIcon(item.icon, size = 18.dp, tint = Ink)
        }
        BodyText(text = item.label, size = 16, color = Ink, modifier = Modifier.weight(1f))
        Overline(text = item.right, size = 10, color = item.rightColor)
        StrokeIcon(ArcanaIcons.ChevronRight, size = 16.dp, tint = Ash2)
    }
}

// Hairline rules drawn at the row edges.
private fun Modifier.drawTopRule(color: Color = Mist): Modifier = this.drawBehind {
    drawLine(
        color = color,
        start = Offset(0f, 0f),
        end = Offset(size.width, 0f),
        strokeWidth = 1.dp.toPx(),
    )
}

private fun Modifier.drawBottomRule(color: Color = Mist): Modifier = this.drawBehind {
    drawLine(
        color = color,
        start = Offset(0f, size.height),
        end = Offset(size.width, size.height),
        strokeWidth = 1.dp.toPx(),
    )
}

package org.arcana.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.arcana.mobile.theme.Arcana
import org.arcana.mobile.theme.Ash
import org.arcana.mobile.theme.Lime
import org.arcana.mobile.theme.Mist
import org.arcana.mobile.theme.Mist2
import org.arcana.mobile.theme.Moss
import org.arcana.mobile.theme.Stone
import org.jetbrains.compose.resources.DrawableResource

/** The three primary destinations. Profile renders as the member's avatar. */
enum class ArcanaTab(val label: String, val icon: DrawableResource, val isAvatar: Boolean = false) {
    Home("Home", ArcanaIcons.Home),
    Schedule("Schedule", ArcanaIcons.Calendar),
    Profile("You", ArcanaIcons.User, isAvatar = true),
}

/**
 * Bottom navigation — Stone surface, hairline top. Active tab reads in Moss
 * with a Lime indicator dot above the icon; the Profile tab is the member's
 * avatar chip rather than a generic figure.
 *
 * The Stone background fills the whole bottom-bar slot, including the gesture-
 * nav safe-area inset, so nothing behind the Scaffold bleeds through under
 * the visible row.
 */
@Composable
fun ArcanaTabBar(
    active: ArcanaTab,
    onSelect: (ArcanaTab) -> Unit,
    avatarInitials: String,
    modifier: Modifier = Modifier,
) {
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .safeBottomBarPadding()
                .padding(start = 8.dp, end = 8.dp, top = 10.dp, bottom = 12.dp),
        ) {
            ArcanaTab.entries.forEach { tab ->
                TabItem(
                    tab = tab,
                    active = tab == active,
                    avatarInitials = avatarInitials,
                    onClick = { onSelect(tab) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun TabItem(
    tab: ArcanaTab,
    active: Boolean,
    avatarInitials: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = if (active) Moss else Ash
    Column(
        modifier = modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        // Lime indicator dot
        Box(
            Modifier
                .size(4.dp)
                .clip(CircleShape)
                .background(if (active) Lime else Color.Transparent)
        )
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
                    style = TextStyle(
                        fontFamily = Arcana.fonts.display,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        letterSpacing = 0.02.em,
                        color = if (active) Lime else Ash,
                    ),
                )
            }
        } else {
            StrokeIcon(icon = tab.icon, size = 22.dp, tint = tint)
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

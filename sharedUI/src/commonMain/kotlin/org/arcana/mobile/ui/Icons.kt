package org.arcana.mobile.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import arcana.sharedui.generated.resources.Res
import arcana.sharedui.generated.resources.icon_arrow_right
import arcana.sharedui.generated.resources.icon_arrow_up_right
import arcana.sharedui.generated.resources.icon_bell
import arcana.sharedui.generated.resources.icon_bookmark
import arcana.sharedui.generated.resources.icon_calendar
import arcana.sharedui.generated.resources.icon_card
import arcana.sharedui.generated.resources.icon_check
import arcana.sharedui.generated.resources.icon_chevron_down
import arcana.sharedui.generated.resources.icon_chevron_right
import arcana.sharedui.generated.resources.icon_clock
import arcana.sharedui.generated.resources.icon_close
import arcana.sharedui.generated.resources.icon_filter
import arcana.sharedui.generated.resources.icon_home
import arcana.sharedui.generated.resources.icon_logout
import arcana.sharedui.generated.resources.icon_pin
import arcana.sharedui.generated.resources.icon_refresh
import arcana.sharedui.generated.resources.icon_settings
import arcana.sharedui.generated.resources.icon_share
import arcana.sharedui.generated.resources.icon_support
import arcana.sharedui.generated.resources.icon_fullscreen
import arcana.sharedui.generated.resources.icon_swap
import arcana.sharedui.generated.resources.icon_user
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * The Arcana stroke-icon set. Each icon is a 24×24 vector drawable in
 * `composeResources/drawable/icon_*.xml` with a baked 1.8 stroke. Render via
 * [StrokeIcon] (or any Compose `Icon`) and pass a [tint] — the drawable's
 * stroke color is replaced by the tint at draw time.
 *
 * To add an icon: drop the .xml file in `composeResources/drawable/`, then
 * expose it here. Same flow works on Android and iOS via Compose Resources.
 */
object ArcanaIcons {
    val Home: DrawableResource get() = Res.drawable.icon_home
    val Calendar: DrawableResource get() = Res.drawable.icon_calendar
    val User: DrawableResource get() = Res.drawable.icon_user
    val Clock: DrawableResource get() = Res.drawable.icon_clock
    val Pin: DrawableResource get() = Res.drawable.icon_pin
    val ArrowRight: DrawableResource get() = Res.drawable.icon_arrow_right
    val ArrowUpRight: DrawableResource get() = Res.drawable.icon_arrow_up_right
    val Check: DrawableResource get() = Res.drawable.icon_check
    val Close: DrawableResource get() = Res.drawable.icon_close
    val ChevronDown: DrawableResource get() = Res.drawable.icon_chevron_down
    val ChevronRight: DrawableResource get() = Res.drawable.icon_chevron_right
    val Filter: DrawableResource get() = Res.drawable.icon_filter
    val Bell: DrawableResource get() = Res.drawable.icon_bell
    val Card: DrawableResource get() = Res.drawable.icon_card
    val Refresh: DrawableResource get() = Res.drawable.icon_refresh
    val Logout: DrawableResource get() = Res.drawable.icon_logout
    val Settings: DrawableResource get() = Res.drawable.icon_settings
    val Share: DrawableResource get() = Res.drawable.icon_share
    val Bookmark: DrawableResource get() = Res.drawable.icon_bookmark
    val Support: DrawableResource get() = Res.drawable.icon_support
    val Swap: DrawableResource get() = Res.drawable.icon_swap
    val Expand: DrawableResource get() = Res.drawable.icon_fullscreen
}

/**
 * Renders an [ArcanaIcons] entry (or any [DrawableResource]) at [size], tinted
 * via [tint]. The XML's baked stroke is replaced by the tint at draw time, so
 * call sites only need to pass the color they want.
 *
 * **[contentDescription] rule — do not label every icon.** Pass a description
 * only when the icon *is* the whole control (an icon-only button with nothing
 * else to name it). Leave it null when the icon sits beside a visible text
 * label or inside an already-labeled clickable: Compose merges descendant
 * semantics into the clickable ancestor, so a description there makes TalkBack
 * announce the label twice ("Continue, arrow right"). Decorative call sites in
 * this repo carry a short `// decorative` comment so the null is obviously
 * deliberate rather than an oversight.
 *
 * Describe the **action**, not the glyph ("Remove Barry's filter", not "Close
 * icon"), and never include the role — TalkBack announces "button" itself.
 */
@Composable
fun StrokeIcon(
    icon: DrawableResource,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    tint: Color = LocalContentColor.current,
    contentDescription: String? = null,
) {
    Icon(
        painter = painterResource(icon),
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier.size(size),
    )
}

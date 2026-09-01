package org.arcana.mobile.search

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn

/** The Search entrance is a container transform: the screen clips itself to
 *  the entry pill's bounds and expands to full screen (see SearchScreen's
 *  reveal). Navigation's own transition is a near-invisible fade whose only
 *  job is to HOLD both screens mounted for the reveal's duration, so the
 *  Book tab stays visible beneath the growing oval. */
internal const val SEARCH_REVEAL_MS = 340
internal const val SEARCH_REVEAL_CLOSE_MS = 240

// The Book tab and Search share a Stone background, so the growing oval's
// edge needs help to read: the tab dims beneath it (scrim), the rim carries
// a soft shadow that fades as it reaches full screen, and the surface
// starts as Paper (the pill's own fill) before settling to Stone.
internal const val SEARCH_SCRIM_MAX_ALPHA = 0.22f

internal val SearchRevealEase = CubicBezierEasing(0.2f, 0f, 0f, 1f)

internal fun searchHoldEnterTransition(): EnterTransition =
    fadeIn(tween(SEARCH_REVEAL_MS), initialAlpha = 0.99f)

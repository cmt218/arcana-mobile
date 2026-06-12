package org.arcana.mobile.theme

import androidx.compose.ui.graphics.Color

/**
 * Arcana palette. The five primaries are the brand identity doc's source of truth
 * (https://docs.google.com/document/d/1ZsEZLi61TEmt9suTYcunbh5u4sxTibK3TYMA3oN_pjc).
 * Derived variants (Bright / Deep / Light, Stone2, Paper) are HSL-style shifts
 * of the primary they descend from.
 */

// ---- Brand primaries (source of truth — brand identity doc) ----
val Lime        = Color(0xFFB6C24F) // signal — active, success, focus
val Moss        = Color(0xFF3C5D1A) // foundation — primary CTAs, deep surfaces
val Stone       = Color(0xFFF5F2ED) // primary background
val Wood        = Color(0xFF3B2415) // sophisticated dark accent
val BurntNectar = Color(0xFFF65713) // accent — sparingly

// ---- Derived greens ----
val LimeBright  = Color(0xFFC9D560) // hover / lit state
val LimeDeep    = Color(0xFF96A235) // pressed
val MossDeep    = Color(0xFF2A4214) // splash background — exact value from design handoff
val MossLight   = Color(0xFF537F26)

// ---- Derived stone tones ----
val Stone2      = Color(0xFFEAE6DE)
val Paper       = Color(0xFFFAF8F3) // lifted surface

// ---- Ink / structure ----
val Ink         = Color(0xFF161812) // primary text — warm near-black
val Graphite    = Color(0xFF2A2C24)
val Charcoal    = Color(0xFF3F4338)

// ---- Warm neutrals ----
val Ash         = Color(0xFF6B6E5F) // secondary text
val Ash2        = Color(0xFF9B9F8F) // tertiary / muted text
val Mist        = Color(0xFFD8D7C7) // dividers, input hairlines
val Mist2       = Color(0xFFEBEADA) // subtle fills

// ---- Functional ----
val Danger      = Color(0xFFB23A2A)
val Warning     = Color(0xFFD89B2A)
val Clay        = Color(0xFFB5503F) // destructive-action red, gentle not alarm
val ClayDeep    = Color(0xFF8F3D2F) // destructive-action red, gentle not alarm (arrow well)
val Success     = Lime
val Info        = MossLight

// ---- Translucent helpers (Stone over dark surfaces) ----
val InkAlpha10   = Color(0x1A161812)
val InkAlpha08   = Color(0x14161812)
val StoneAlpha72 = Color(0xB8F5F2ED)
val StoneAlpha65 = Color(0xA6F5F2ED)
val StoneAlpha55 = Color(0x8CF5F2ED)
val StoneAlpha18 = Color(0x2EF5F2ED)
val StoneAlpha10 = Color(0x1AF5F2ED)

package org.arcana.mobile.ui

import org.jetbrains.compose.resources.DrawableResource

/**
 * Category slug → glyph. The label always comes from the server; an unknown
 * slug renders the default glyph. The ten bespoke stroke icons are a
 * design-system deliverable: until they land every slug maps to the default,
 * so add entries here as the drawables arrive.
 */
object CategoryIcons {
    private val bySlug: Map<String, DrawableResource> = emptyMap()

    fun iconFor(slug: String): DrawableResource = bySlug[slug] ?: ArcanaIcons.Category
}

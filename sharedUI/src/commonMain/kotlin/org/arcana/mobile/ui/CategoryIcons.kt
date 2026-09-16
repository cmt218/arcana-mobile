package org.arcana.mobile.ui

import org.jetbrains.compose.resources.DrawableResource

/**
 * Category slug -> glyph for the server's curated modality categories. The
 * label always comes from the server; an unknown slug renders the default
 * glyph, so a new category is never a crash, just a star until it gets an icon.
 */
object CategoryIcons {
    private val bySlug: Map<String, DrawableResource> = mapOf(
        "strength" to ArcanaIcons.CatStrength,
        "sculpt" to ArcanaIcons.CatSculpt,
        "pilates" to ArcanaIcons.CatPilates,
        "reformer" to ArcanaIcons.CatReformer,
        "yoga" to ArcanaIcons.CatYoga,
        "barre" to ArcanaIcons.CatBarre,
        "hiit-bootcamp" to ArcanaIcons.CatHiit,
        "cycle" to ArcanaIcons.CatCycle,
        "dance" to ArcanaIcons.CatDance,
        "run" to ArcanaIcons.CatRun,
        "boxing" to ArcanaIcons.CatBoxing,
    )

    fun iconFor(slug: String): DrawableResource = bySlug[slug.lowercase()] ?: ArcanaIcons.Category
}

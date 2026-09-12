package org.arcana.mobile.networking

import org.arcana.mobile.data.DiscoverDirectoryDto
import org.arcana.mobile.data.StudioPageDto

interface DiscoverApi {
    /** Every brand, A to Z, narrowed by category slugs and neighborhood names
     *  (each repeated as its own query parameter; empty = no narrowing). */
    suspend fun fetchDirectory(categories: Set<String> = emptySet(), neighborhoods: Set<String> = emptySet()): DiscoverDirectoryDto
    suspend fun fetchStudioPage(brandSlug: String): StudioPageDto
}

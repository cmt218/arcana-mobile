package org.cadence.mobile

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
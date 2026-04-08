package org.arcana.mobile

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform

expect fun getBaseUrl(): String

expect fun logWarning(tag: String, message: String)
package org.cadence.mobile.networking

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.cadence.mobile.data.ClassDto
import org.cadence.mobile.getBaseUrl

val cadenceClient = HttpClient {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
}

suspend fun fetchClasses(): List<ClassDto> {
    return cadenceClient.get("${getBaseUrl()}/api/classes/").body()
}

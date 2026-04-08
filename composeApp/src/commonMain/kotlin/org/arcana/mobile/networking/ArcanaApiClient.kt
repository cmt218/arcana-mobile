package org.arcana.mobile.networking

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodedPath
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import org.arcana.mobile.auth.TokenStorage
import org.arcana.mobile.data.ClassDto
import org.arcana.mobile.data.LoginRequest
import org.arcana.mobile.data.RefreshRequest
import org.arcana.mobile.data.RefreshTokenResponse
import org.arcana.mobile.data.RegisterRequest
import org.arcana.mobile.data.TokenResponse
import org.arcana.mobile.getBaseUrl

class ArcanaApiClient(private val tokenStorage: TokenStorage) {

    private val _isAuthenticated = MutableStateFlow(tokenStorage.isLoggedIn)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(Auth) {
            bearer {
                loadTokens {
                    val access = tokenStorage.accessToken ?: return@loadTokens null
                    val refresh = tokenStorage.refreshToken ?: return@loadTokens null
                    BearerTokens(access, refresh)
                }
                refreshTokens {
                    val refresh = tokenStorage.refreshToken
                        ?: run {
                            tokenStorage.clear()
                            _isAuthenticated.value = false
                            return@refreshTokens null
                        }
                    try {
                        val tokens = client.post("${getBaseUrl()}/api/auth/token/refresh/") {
                            contentType(ContentType.Application.Json)
                            setBody(RefreshRequest(refresh))
                            markAsRefreshTokenRequest()
                        }.body<RefreshTokenResponse>()
                        tokenStorage.accessToken = tokens.access
                        tokens.refresh?.let { tokenStorage.refreshToken = it }
                        BearerTokens(tokens.access, tokenStorage.refreshToken ?: return@refreshTokens null)
                    } catch (e: Exception) {
                        tokenStorage.clear()
                        _isAuthenticated.value = false
                        null
                    }
                }
                sendWithoutRequest { request ->
                    !request.url.encodedPath.contains("token") &&
                        !request.url.encodedPath.contains("register")
                }
            }
        }
    }

    suspend fun login(email: String, password: String) {
        val tokens = client.post("${getBaseUrl()}/api/auth/token/") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(email, password))
        }.body<TokenResponse>()
        tokenStorage.accessToken = tokens.access
        tokenStorage.refreshToken = tokens.refresh
        _isAuthenticated.value = true
    }

    suspend fun register(email: String, password: String) {
        val tokens = client.post("${getBaseUrl()}/api/auth/register/") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(email, password))
        }.body<TokenResponse>()
        tokenStorage.accessToken = tokens.access
        tokenStorage.refreshToken = tokens.refresh
        _isAuthenticated.value = true
    }

    suspend fun fetchClasses(): List<ClassDto> {
        return client.get("${getBaseUrl()}/api/classes/").body()
    }

    fun logout() {
        tokenStorage.clear()
        _isAuthenticated.value = false
    }
}

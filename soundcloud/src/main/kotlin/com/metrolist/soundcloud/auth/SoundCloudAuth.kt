package com.metrolist.soundcloud.auth

import com.metrolist.soundcloud.models.SoundCloudTokenResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.contentType
import io.ktor.http.formUrlEncode
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

object SoundCloudAuth {
    const val AUTH_BASE_URL = "https://secure.soundcloud.com"
    const val AUTHORIZE_URL = "$AUTH_BASE_URL/authorize"
    const val TOKEN_URL = "$AUTH_BASE_URL/oauth/token"

    data class PkceSession(
        val clientId: String,
        val redirectUri: String,
        val codeVerifier: String,
        val state: String,
    ) {
        fun authorizationUrl(): String {
            val challenge = codeChallenge(codeVerifier)
            return buildString {
                append(AUTHORIZE_URL)
                append("?client_id=").append(clientId)
                append("&redirect_uri=").append(redirectUri)
                append("&response_type=code")
                append("&code_challenge=").append(challenge)
                append("&code_challenge_method=S256")
                append("&state=").append(state)
            }
        }
    }

    fun newPkceSession(
        clientId: String,
        redirectUri: String,
    ): PkceSession = PkceSession(
        clientId = clientId,
        redirectUri = redirectUri,
        codeVerifier = randomString(128),
        state = randomString(64),
    )

    fun extractAuthorizationCode(
        callbackUrl: String,
        expectedState: String,
    ): String {
        val uri = java.net.URI(callbackUrl)
        val params = Parameters.build {
            uri.query.orEmpty()
                .split("&")
                .filter { it.contains("=") }
                .forEach { pair ->
                    val parts = pair.split("=", limit = 2)
                    append(parts[0], java.net.URLDecoder.decode(parts[1], StandardCharsets.UTF_8))
                }
        }
        val state = params["state"] ?: error("Missing state")
        require(state == expectedState) { "Invalid state" }
        return params["code"] ?: error("Missing authorization code")
    }

    suspend fun exchangeCode(
        httpClient: HttpClient,
        clientId: String,
        clientSecret: String,
        redirectUri: String,
        codeVerifier: String,
        code: String,
    ): SoundCloudTokenResponse {
        val body = Parameters.build {
            append("client_id", clientId)
            append("client_secret", clientSecret)
            append("grant_type", "authorization_code")
            append("redirect_uri", redirectUri)
            append("code_verifier", codeVerifier)
            append("code", code)
        }.formUrlEncode()

        val response = httpClient.post(TOKEN_URL) {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(body)
        }
        if (response.status !in listOf(HttpStatusCode.OK, HttpStatusCode.Created)) {
            error("SoundCloud token exchange failed: ${response.status.value} ${response.bodyAsText()}")
        }
        return response.body()
    }

    suspend fun refreshToken(
        httpClient: HttpClient,
        clientId: String,
        clientSecret: String,
        refreshToken: String,
    ): SoundCloudTokenResponse {
        val body = Parameters.build {
            append("client_id", clientId)
            append("client_secret", clientSecret)
            append("grant_type", "refresh_token")
            append("refresh_token", refreshToken)
        }.formUrlEncode()

        val response = httpClient.post(TOKEN_URL) {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(body)
        }
        if (response.status !in listOf(HttpStatusCode.OK, HttpStatusCode.Created)) {
            error("SoundCloud token refresh failed: ${response.status.value} ${response.bodyAsText()}")
        }
        return response.body()
    }

    private fun randomString(length: Int): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        val random = SecureRandom()
        return buildString(length) {
            repeat(length) {
                append(chars[random.nextInt(chars.length)])
            }
        }
    }

    private fun codeChallenge(codeVerifier: String): String {
        val hashed = MessageDigest.getInstance("SHA-256")
            .digest(codeVerifier.toByteArray(StandardCharsets.UTF_8))
        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(hashed)
    }
}

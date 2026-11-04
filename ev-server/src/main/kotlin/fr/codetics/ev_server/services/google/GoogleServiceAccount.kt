// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

package fr.codetics.ev_server.services.google

import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Duration
import java.time.Instant
import java.util.Base64
import tools.jackson.databind.ObjectMapper

class GoogleServiceAccount
private constructor(
    val clientEmail: String,
    val projectId: String?,
    private val tokenUri: String,
    private val key: PrivateKey,
    private val json: ObjectMapper,
) {
    private val encoder = Base64.getUrlEncoder().withoutPadding()

    fun sign(claims: Map<String, Any?>): String {
        val header = encoder.encodeToString(json.writeValueAsBytes(mapOf("alg" to "RS256", "typ" to "JWT")))
        val body = encoder.encodeToString(json.writeValueAsBytes(claims))
        val signer = Signature.getInstance("SHA256withRSA")
        signer.initSign(key)
        signer.update("$header.$body".toByteArray())
        return "$header.$body.${encoder.encodeToString(signer.sign())}"
    }

    fun assertion(scope: String, now: Instant) =
        sign(
            mapOf(
                "iss" to clientEmail,
                "scope" to scope,
                "aud" to tokenUri,
                "iat" to now.epochSecond,
                "exp" to now.plus(ASSERTION_LIFETIME).epochSecond,
            )
        )

    fun accessToken(scope: String, now: Instant, client: HttpClient): String {
        val form = "grant_type=${encode(GRANT)}&assertion=${encode(assertion(scope, now))}"
        val request =
            HttpRequest.newBuilder(URI(tokenUri))
                .timeout(Duration.ofSeconds(15))
                .header("content-type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() in 200..299) { "Google a refusé le compte de service : ${response.statusCode()}" }
        return json.readTree(response.body()).path("access_token").asText()
    }

    private fun encode(value: String) = URLEncoder.encode(value, Charsets.UTF_8)

    companion object {
        private const val GRANT = "urn:ietf:params:oauth:grant-type:jwt-bearer"
        private val ASSERTION_LIFETIME: Duration = Duration.ofMinutes(50)

        fun read(value: String, json: ObjectMapper): GoogleServiceAccount {
            val tree = json.readTree(material(value))
            val pem = tree.path("private_key").asText()
            val encoded =
                pem.replace("\\n", "\n")
                    .lineSequence()
                    .filterNot { it.startsWith("-----") }
                    .joinToString("")
                    .filterNot(Char::isWhitespace)
            return GoogleServiceAccount(
                clientEmail = tree.path("client_email").asText(),
                projectId = tree.path("project_id").asText().ifBlank { null },
                tokenUri = tree.path("token_uri").asText().ifBlank { "https://oauth2.googleapis.com/token" },
                key = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(encoded))),
                json = json,
            )
        }

        private fun material(value: String): String {
            val trimmed = value.trim()
            if (trimmed.startsWith("{")) return trimmed
            val path = runCatching { Path.of(trimmed) }.getOrNull()
            return if (path != null && Files.isRegularFile(path)) Files.readString(path)
            else String(Base64.getMimeDecoder().decode(trimmed))
        }
    }
}

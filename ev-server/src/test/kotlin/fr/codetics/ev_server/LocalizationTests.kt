// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

package fr.codetics.ev_server

import fr.codetics.ev_server.core.I18n
import fr.codetics.ev_server.models.entities.TenantEntity
import fr.codetics.ev_server.models.entities.UserEntity
import fr.codetics.ev_server.models.people.UserRole
import fr.codetics.ev_server.repositories.TenantRepository
import fr.codetics.ev_server.repositories.UserRepository
import java.util.Locale
import java.util.Properties
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

class MessageBundleTests {
    private fun bundle(suffix: String): Properties =
        Properties().apply {
            requireNotNull(MessageBundleTests::class.java.getResourceAsStream("/messages$suffix.properties")).reader(Charsets.UTF_8).use(::load)
        }

    @Test
    fun `the three bundles share the same keys and placeholders`() {
        val french = bundle("")
        val placeholders = Regex("\\{\\d+}")
        for (suffix in listOf("_en", "_es")) {
            val other = bundle(suffix)
            assertEquals(french.stringPropertyNames(), other.stringPropertyNames(), suffix)
            french.stringPropertyNames().forEach { key ->
                assertEquals(
                    placeholders.findAll(french.getProperty(key)).map { it.value }.toSet(),
                    placeholders.findAll(other.getProperty(key)).map { it.value }.toSet(),
                    "$suffix $key",
                )
            }
        }
    }

    @Test
    fun `messages avoid ascii apostrophes that MessageFormat would swallow`() {
        for (suffix in listOf("", "_en", "_es")) {
            bundle(suffix).forEach { key, value -> assertTrue("'" !in value.toString(), "$suffix $key") }
        }
    }

    @Test
    fun `unsupported languages fall back to french`() {
        assertEquals(Locale.FRENCH, I18n.of("de"))
        assertEquals("es", I18n.of("es-ES").language)
        assertEquals("Session introuvable.", I18n.get(I18n.of("de"), "sessions.not_found"))
        assertEquals("Session not found.", I18n.get(Locale.ENGLISH, "sessions.not_found"))
    }
}

@ActiveProfiles("local")
@SpringBootTest(properties = ["app.bootstrap.email=", "app.mail-enabled=false"])
class LocalizationTests
@Autowired
constructor(
    private val context: WebApplicationContext,
    private val tenants: TenantRepository,
    private val users: UserRepository,
    private val passwords: PasswordEncoder,
) {
    private lateinit var mvc: MockMvc
    private lateinit var tenant: TenantEntity
    private lateinit var driver: UserEntity

    @BeforeEach
    fun prepare() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply<DefaultMockMvcBuilder>(springSecurity()).build()
        tenant = tenants.save(TenantEntity(slug = "i18n-${UUID.randomUUID()}", name = "Locale network"))
        driver =
            users.save(
                UserEntity(
                    tenantId = tenant.id,
                    email = "driver@example.test",
                    displayName = "Driver",
                    passwordHash = passwords.encode("a-long-password-for-testing"),
                    role = UserRole.DRIVER,
                    emailVerified = true,
                )
            )
    }

    private fun signIn(password: String, language: String?, email: String = driver.email) =
        mvc.perform(
            post("/api/v1/auth/app/signin")
                .with(csrf())
                .apply { language?.let { header(HttpHeaders.ACCEPT_LANGUAGE, it) } }
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"tenant":"${tenant.slug}","email":"$email","password":"$password"}""")
        )

    @Test
    fun `problem details follow the Accept-Language header`() {
        signIn("wrong-password-for-testing", null)
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.detail").value("Identifiants incorrects."))
        signIn("wrong-password-for-testing", "en-GB,en;q=0.9")
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.detail").value("Incorrect credentials."))
        signIn("wrong-password-for-testing", "es")
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.detail").value("Credenciales incorrectas."))
        signIn("wrong-password-for-testing", "de-DE")
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.detail").value("Identifiants incorrects."))
    }

    @Test
    fun `an unverified address is flagged with a stable code`() {
        users.save(
            UserEntity(
                tenantId = tenant.id,
                email = "pending@example.test",
                displayName = "Pending",
                passwordHash = passwords.encode("a-long-password-for-testing"),
                role = UserRole.DRIVER,
            )
        )
        signIn("a-long-password-for-testing", "es", "pending@example.test")
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("email_not_verified"))
            .andExpect(jsonPath("$.detail").value("Verifique su dirección de correo electrónico antes de iniciar sesión."))
    }

    @Test
    fun `unauthenticated requests get a localized problem`() {
        mvc.perform(get("/api/v1/account").header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.detail").value("Authentication required."))
    }

    @Test
    fun `the account locale follows sign-in and can be changed`() {
        val token =
            signIn("a-long-password-for-testing", "es")
                .andExpect(status().isOk)
                .andReturn()
                .response
                .contentAsString
                .let { Regex("\"token\"\\s*:\\s*\"([^\"]+)\"").find(it)!!.groupValues[1] }
        assertEquals("es", users.findById(driver.id).orElseThrow().locale)

        mvc.perform(get("/api/v1/account").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.locale").value("es"))

        mvc.perform(
                put("/api/v1/account")
                    .with(csrf())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    .header(HttpHeaders.ACCEPT_LANGUAGE, "en")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"Driver","locale":"de"}""")
            )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.detail").value("Unsupported language."))

        mvc.perform(
                put("/api/v1/account")
                    .with(csrf())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"Driver","locale":"en"}""")
            )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.locale").value("en"))
        assertEquals("en", users.findById(driver.id).orElseThrow().locale)
    }
}

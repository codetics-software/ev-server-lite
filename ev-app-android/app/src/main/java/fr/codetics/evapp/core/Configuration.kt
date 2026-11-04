// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

package fr.codetics.evapp.core

import androidx.annotation.StringRes
import java.net.URI

data class AppConfiguration(
    val apiUrl: String,
    val portalUrl: String,
    val workspaceSlug: String,
    val brandName: String,
    val supportEmail: String,
    val privacyPolicyUrl: String,
    val mapStyleUrl: String,
    val googleMapsApiKey: String? = null,
    val firebase: FirebaseConfiguration? = null,
)

data class FirebaseConfiguration(
    val projectId: String,
    val applicationId: String,
    val apiKey: String,
    val senderId: String,
)

sealed class ConfigurationIssue(val key: String) {
    data object MissingFile : ConfigurationIssue("AppConfiguration.properties")

    class Placeholder(key: String) : ConfigurationIssue(key)

    class InvalidValue(key: String) : ConfigurationIssue(key)

    class InsecureUrl(key: String) : ConfigurationIssue(key)

    override fun equals(other: Any?) = other is ConfigurationIssue && other::class == this::class && other.key == key

    override fun hashCode() = 31 * this::class.hashCode() + key.hashCode()

    override fun toString() = "${this::class.simpleName}($key)"
}

sealed interface ConfigurationOutcome {
    data class Valid(val configuration: AppConfiguration) : ConfigurationOutcome

    data class Invalid(val issues: List<ConfigurationIssue>) : ConfigurationOutcome
}

object ConfigurationValidator {
    private val urlKeys = listOf("API_URL", "PORTAL_URL", "PRIVACY_POLICY_URL", "MAP_STYLE_URL")
    private val textKeys = listOf("WORKSPACE_SLUG", "BRAND_NAME", "SUPPORT_EMAIL")
    private val firebaseKeys =
        listOf("FIREBASE_PROJECT_ID", "FIREBASE_APPLICATION_ID", "FIREBASE_API_KEY", "FIREBASE_SENDER_ID")
    private val slug = Regex("[a-z0-9][a-z0-9-]{0,62}")
    private val email = Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")
    private val reservedDomains = listOf("example.com", "example.org", "example.net", "exemple.fr")
    private val localHosts = setOf("localhost", "127.0.0.1", "10.0.2.2")

    fun validate(values: Map<String, String>, allowInsecureLocalhost: Boolean): ConfigurationOutcome {
        val issues = mutableListOf<ConfigurationIssue>()
        val accepted = mutableMapOf<String, String>()
        for (key in urlKeys + textKeys) {
            val value = values[key]?.trim().orEmpty()
            when {
                value.isEmpty() -> issues += ConfigurationIssue.InvalidValue(key)
                isPlaceholder(value) -> issues += ConfigurationIssue.Placeholder(key)
                key in urlKeys ->
                    url(key, value, allowInsecureLocalhost)?.let { issues += it } ?: accepted.put(key, value)
                else -> accepted[key] = value
            }
        }
        accepted["WORKSPACE_SLUG"]?.takeUnless(slug::matches)?.let {
            issues += ConfigurationIssue.InvalidValue("WORKSPACE_SLUG")
        }
        accepted["SUPPORT_EMAIL"]?.takeUnless(email::matches)?.let {
            issues += ConfigurationIssue.InvalidValue("SUPPORT_EMAIL")
        }
        val googleMapsKey = values["GOOGLE_MAPS_API_KEY"]?.trim().orEmpty()
        if (isPlaceholder(googleMapsKey)) issues += ConfigurationIssue.Placeholder("GOOGLE_MAPS_API_KEY")
        val firebase = firebaseKeys.associateWith { values[it]?.trim().orEmpty() }
        firebase.filterValues(::isPlaceholder).keys.forEach { issues += ConfigurationIssue.Placeholder(it) }
        if (firebase.values.any(String::isNotEmpty) && firebase.values.any(String::isEmpty)) {
            firebase.filterValues(String::isEmpty).keys.forEach { issues += ConfigurationIssue.InvalidValue(it) }
        }
        if (issues.isNotEmpty()) return ConfigurationOutcome.Invalid(issues)
        return ConfigurationOutcome.Valid(
            AppConfiguration(
                apiUrl = accepted.getValue("API_URL"),
                portalUrl = accepted.getValue("PORTAL_URL"),
                workspaceSlug = accepted.getValue("WORKSPACE_SLUG"),
                brandName = accepted.getValue("BRAND_NAME"),
                supportEmail = accepted.getValue("SUPPORT_EMAIL"),
                privacyPolicyUrl = accepted.getValue("PRIVACY_POLICY_URL"),
                mapStyleUrl = accepted.getValue("MAP_STYLE_URL"),
                googleMapsApiKey = googleMapsKey.ifEmpty { null },
                firebase =
                    firebase.takeIf { all -> all.values.all(String::isNotEmpty) }?.let {
                        FirebaseConfiguration(
                            projectId = it.getValue("FIREBASE_PROJECT_ID"),
                            applicationId = it.getValue("FIREBASE_APPLICATION_ID"),
                            apiKey = it.getValue("FIREBASE_API_KEY"),
                            senderId = it.getValue("FIREBASE_SENDER_ID"),
                        )
                    },
            )
        )
    }

    private fun url(key: String, value: String, allowInsecureLocalhost: Boolean): ConfigurationIssue? {
        val uri = runCatching { URI(value) }.getOrNull()
        val scheme = uri?.scheme?.lowercase()
        val host = uri?.host?.lowercase()
        if (scheme == null || host.isNullOrEmpty()) return ConfigurationIssue.InvalidValue(key)
        if (reservedDomains.any { host == it || host.endsWith(".$it") }) return ConfigurationIssue.InvalidValue(key)
        if (scheme == "https") return null
        if (scheme == "http" && allowInsecureLocalhost && host in localHosts) return null
        return ConfigurationIssue.InsecureUrl(key)
    }

    private fun isPlaceholder(value: String) =
        value.contains("__") || value.contains("<") ||
            value.uppercase().let { it.contains("PLACEHOLDER") || it.contains("CHANGE_ME") }
}

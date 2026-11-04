// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

package fr.codetics.evapp.core

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

object JsonCoding {
    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    fun <T> decode(strategy: DeserializationStrategy<T>, text: String): T =
        json.decodeFromJsonElement(strategy, normalize(json.parseToJsonElement(text)))

    fun normalize(element: JsonElement): JsonElement =
        when (element) {
            is JsonObject -> JsonObject(element.entries.associate { (key, value) -> camelCase(key) to normalize(value) })
            is JsonArray -> JsonArray(element.map(::normalize))
            else -> element
        }

    private val snake = Regex("[a-z0-9]+(_[a-z0-9]+)+")

    fun camelCase(key: String): String {
        if (!snake.matches(key)) return key
        val parts = key.split('_')
        return parts.first() + parts.drop(1).joinToString("") { part -> part.replaceFirstChar(Char::uppercase) }
    }
}

// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

package fr.codetics.evapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors =
    lightColorScheme(
        primary = BrandGreen,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFB6F2C4),
        onPrimaryContainer = Color(0xFF00210C),
        secondary = Color(0xFF4F6354),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFD2E8D5),
        onSecondaryContainer = Color(0xFF0D1F13),
        tertiary = Color(0xFF3F6600),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFC3EF7E),
        onTertiaryContainer = Color(0xFF102000),
        background = Color(0xFFF6FBF4),
        onBackground = Color(0xFF181D19),
        surface = Color(0xFFF6FBF4),
        onSurface = Color(0xFF181D19),
        surfaceVariant = Color(0xFFDDE5DB),
        onSurfaceVariant = Color(0xFF414941),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFF0F5EE),
        surfaceContainer = Color(0xFFEAEFE8),
        surfaceContainerHigh = Color(0xFFE4EAE3),
        surfaceContainerHighest = Color(0xFFDFE4DD),
        outline = Color(0xFF717970),
        outlineVariant = Color(0xFFC1C9BF),
    )

private val DarkColors =
    darkColorScheme(
        primary = Color(0xFF7BDB8F),
        onPrimary = Color(0xFF00391A),
        primaryContainer = Color(0xFF005227),
        onPrimaryContainer = Color(0xFFB6F2C4),
        secondary = Color(0xFFB6CCBA),
        onSecondary = Color(0xFF223527),
        secondaryContainer = Color(0xFF384B3D),
        onSecondaryContainer = Color(0xFFD2E8D5),
        tertiary = Color(0xFFA8D265),
        onTertiary = Color(0xFF203600),
        tertiaryContainer = Color(0xFF304F00),
        onTertiaryContainer = Color(0xFFC3EF7E),
        background = Color(0xFF101410),
        onBackground = Color(0xFFDFE4DD),
        surface = Color(0xFF101410),
        onSurface = Color(0xFFDFE4DD),
        surfaceVariant = Color(0xFF414941),
        onSurfaceVariant = Color(0xFFC1C9BF),
        surfaceContainerLowest = Color(0xFF0B0F0B),
        surfaceContainerLow = Color(0xFF181D19),
        surfaceContainer = Color(0xFF1C211D),
        surfaceContainerHigh = Color(0xFF262B27),
        surfaceContainerHighest = Color(0xFF313631),
        outline = Color(0xFF8B938A),
        outlineVariant = Color(0xFF414941),
    )

@Composable
fun EVAppTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content,
    )
}

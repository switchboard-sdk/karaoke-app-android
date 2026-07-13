package com.synervoz.karaokeapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Brand colors ported from res/values/colors.xml.
private val GreenNormal = Color(0xFF008941)
private val GreenDark = Color(0xFF378246)
private val GreenLight = Color(0xFF65BE76)
private val Purple500 = Color(0xFF6200EE)
private val Purple700 = Color(0xFF3700B3)

private val KaraokeLightColors = lightColorScheme(
    primary = GreenNormal,
    onPrimary = Color.White,
    primaryContainer = GreenDark,
    secondary = Purple500,
)

private val KaraokeDarkColors = darkColorScheme(
    primary = GreenLight,
    onPrimary = Color.Black,
    primaryContainer = GreenDark,
    secondary = Purple700,
)

@Composable
fun KaraokeAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) KaraokeDarkColors else KaraokeLightColors,
        content = content,
    )
}

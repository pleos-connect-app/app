package com.gyeongroital.gallaemallae.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight

@Composable
fun GallaemallaeTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (dark) {
        darkColorScheme(
            background = Tokens.iviColorBackground, surface = Tokens.iviColorSurface,
            surfaceVariant = Tokens.iviColorSurfaceRaised, onSurface = Tokens.iviColorOnSurface,
            onBackground = Tokens.iviColorOnSurface, onSurfaceVariant = Tokens.iviColorOnSurfaceVariant,
            primary = Tokens.iviColorPrimary, onPrimary = Tokens.iviColorOnPrimary, error = Tokens.iviColorError,
        )
    } else {
        lightColorScheme(
            background = Tokens.iviColorBackgroundLight, surface = Tokens.iviColorSurfaceLight,
            surfaceVariant = Tokens.iviColorSurfaceRaisedLight, onSurface = Tokens.iviColorOnSurfaceLight,
            onBackground = Tokens.iviColorOnSurfaceLight, onSurfaceVariant = Tokens.iviColorOnSurfaceVariantLight,
            primary = Tokens.iviColorPrimaryLight,
        )
    }
    // 폰트 패밀리는 시스템 기본(Pleos 시스템 폰트 Asta Sans)을 그대로 쓴다.
    val typography = Typography(
        displaySmall = TextStyle(fontSize = Tokens.iviFontSizeDisplay, fontWeight = FontWeight.Bold),
        titleLarge = TextStyle(fontSize = Tokens.iviFontSizeTitle, fontWeight = FontWeight.SemiBold),
        bodyLarge = TextStyle(fontSize = Tokens.iviFontSizeBody),
        labelLarge = TextStyle(fontSize = Tokens.iviFontSizeBody, fontWeight = FontWeight.SemiBold),
        bodyMedium = TextStyle(fontSize = Tokens.iviFontSizeCaption),
    )
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}

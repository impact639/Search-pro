package com.buguagaoshu.scan.search.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = IndigoPrimary,
    onPrimary = IndigoOnPrimary,
    primaryContainer = IndigoPrimaryContainer,
    onPrimaryContainer = IndigoOnPrimaryContainer,
    secondary = IndigoSecondary,
    onSecondary = IndigoOnSecondary,
    secondaryContainer = IndigoSecondaryContainer,
    onSecondaryContainer = IndigoOnSecondaryContainer,
    tertiary = IndigoTertiary,
    onTertiary = IndigoOnTertiary,
    tertiaryContainer = IndigoTertiaryContainer,
    onTertiaryContainer = IndigoOnTertiaryContainer,
    error = IndigoError,
    onError = IndigoOnError,
    errorContainer = IndigoErrorContainer,
    onErrorContainer = IndigoOnErrorContainer,
    background = IndigoBackground,
    onBackground = IndigoOnBackground,
    surface = IndigoSurface,
    onSurface = IndigoOnSurface,
    surfaceVariant = IndigoSurfaceVariant,
    onSurfaceVariant = IndigoOnSurfaceVariant,
    outline = IndigoOutline,
    outlineVariant = IndigoOutlineVariant,
)

private val DarkColors = darkColorScheme(
    primary = IndigoPrimaryDark,
    onPrimary = IndigoOnPrimaryDark,
    primaryContainer = IndigoPrimaryContainerDark,
    onPrimaryContainer = IndigoOnPrimaryContainerDark,
    secondary = IndigoSecondaryDark,
    onSecondary = IndigoOnSecondaryDark,
    secondaryContainer = IndigoSecondaryContainerDark,
    onSecondaryContainer = IndigoOnSecondaryContainerDark,
    tertiary = IndigoTertiaryDark,
    onTertiary = IndigoOnTertiaryDark,
    tertiaryContainer = IndigoTertiaryContainerDark,
    onTertiaryContainer = IndigoOnTertiaryContainerDark,
    error = IndigoErrorDark,
    onError = IndigoOnErrorDark,
    errorContainer = IndigoErrorContainerDark,
    onErrorContainer = IndigoOnErrorContainerDark,
    background = IndigoBackgroundDark,
    onBackground = IndigoOnBackgroundDark,
    surface = IndigoSurfaceDark,
    onSurface = IndigoOnSurfaceDark,
    surfaceVariant = IndigoSurfaceVariantDark,
    onSurfaceVariant = IndigoOnSurfaceVariantDark,
    outline = IndigoOutlineDark,
    outlineVariant = IndigoOutlineVariantDark,
)

@Composable
fun SearchTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // 默认关闭动态取色，保证品牌靛蓝主色始终一致；需要跟随系统壁纸时可改为 true
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

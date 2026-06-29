package com.flowfuel.app.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalSpacing = staticCompositionLocalOf { FFSpacing() }
private val LocalElevation = staticCompositionLocalOf { FFElevation() }
private val LocalMotion = staticCompositionLocalOf { FFMotion() }
private val LocalExtraShapes = staticCompositionLocalOf { FFExtraShapes() }
private val LocalNumericTypography = staticCompositionLocalOf { FFNumericTypography() }
private val LocalSemanticColors = staticCompositionLocalOf { LightSemanticColors }

object FFTheme {
    val spacing: FFSpacing
        @Composable @ReadOnlyComposable get() = LocalSpacing.current
    val elevation: FFElevation
        @Composable @ReadOnlyComposable get() = LocalElevation.current
    val motion: FFMotion
        @Composable @ReadOnlyComposable get() = LocalMotion.current
    val extraShapes: FFExtraShapes
        @Composable @ReadOnlyComposable get() = LocalExtraShapes.current
    val numericTypography: FFNumericTypography
        @Composable @ReadOnlyComposable get() = LocalNumericTypography.current
    val semanticColors: FFSemanticColors
        @Composable @ReadOnlyComposable get() = LocalSemanticColors.current
    val alpha: FFAlpha get() = FFAlpha
}

@Composable
fun FlowFuelTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) FFDarkColorScheme else FFLightColorScheme
    val semantic = if (darkTheme) DarkSemanticColors else LightSemanticColors

    CompositionLocalProvider(
        LocalSpacing provides FFSpacing(),
        LocalElevation provides FFElevation(),
        LocalMotion provides FFMotion(),
        LocalExtraShapes provides FFExtraShapes(),
        LocalNumericTypography provides FFNumericTypography(),
        LocalSemanticColors provides semantic,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = FFTypography,
            shapes = FFShapes,
            content = content,
        )
    }
}

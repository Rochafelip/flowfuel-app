package com.flowfuel.app.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

object FFColors {
    // primary → grafite (slate-700); verde migra para brandGreen em FFExtraColors
    val PrimaryLight = Color(0xFF334155)
    val OnPrimaryLight = Color(0xFFFFFFFF)
    val PrimaryContainerLight = Color(0xFFECFDF5)  // emerald-50 — hero card suave
    val OnPrimaryContainerLight = Color(0xFF064E3B)  // emerald-900

    val SecondaryLight = Color(0xFF1E4D8C)
    val OnSecondaryLight = Color(0xFFFFFFFF)
    val SecondaryContainerLight = Color(0xFFD6E3FF)
    val OnSecondaryContainerLight = Color(0xFF001A41)

    val TertiaryLight = Color(0xFFF2A007)
    val OnTertiaryLight = Color(0xFF1A1100)
    val TertiaryContainerLight = Color(0xFFFFDEA8)
    val OnTertiaryContainerLight = Color(0xFF291800)

    val ErrorLight = Color(0xFFB3261E)
    val OnErrorLight = Color(0xFFFFFFFF)
    val ErrorContainerLight = Color(0xFFF9DEDC)
    val OnErrorContainerLight = Color(0xFF410E0B)

    val BackgroundLight = Color(0xFFF8FAFC)  // slate-50
    val OnBackgroundLight = Color(0xFF0F172A)  // slate-900
    val SurfaceLight = Color(0xFFFFFFFF)
    val OnSurfaceLight = Color(0xFF0F172A)
    val SurfaceVariantLight = Color(0xFFF1F5F9)  // slate-100
    val OnSurfaceVariantLight = Color(0xFF475569)  // slate-600
    val OutlineLight = Color(0xFF64748B)  // slate-500
    val OutlineVariantLight = Color(0xFFCBD5E1)  // slate-300

    // SurfaceContainer — light (slate-tinted)
    val SurfaceContainerLowestLight  = Color(0xFFFFFFFF)
    val SurfaceContainerLowLight     = Color(0xFFF4F6FA)
    val SurfaceContainerLight        = Color(0xFFEEF0F5)
    val SurfaceContainerHighLight    = Color(0xFFE8EBF0)
    val SurfaceContainerHighestLight = Color(0xFFE2E5EC)

    // primary → grafite claro (slate-400) no dark mode
    val PrimaryDark = Color(0xFF94A3B8)
    val OnPrimaryDark = Color(0xFF0F172A)
    val PrimaryContainerDark = Color(0xFF065F46)  // emerald-800
    val OnPrimaryContainerDark = Color(0xFFA7F3D0)  // emerald-200

    val SecondaryDark = Color(0xFFAAC7FF)
    val OnSecondaryDark = Color(0xFF002F69)
    val SecondaryContainerDark = Color(0xFF004494)
    val OnSecondaryContainerDark = Color(0xFFD6E3FF)

    val TertiaryDark = Color(0xFFFFCE7E)
    val OnTertiaryDark = Color(0xFF452B00)
    val TertiaryContainerDark = Color(0xFF624000)
    val OnTertiaryContainerDark = Color(0xFFFFDEA8)

    val ErrorDark = Color(0xFFF2B8B5)
    val OnErrorDark = Color(0xFF601410)
    val ErrorContainerDark = Color(0xFF8C1D18)
    val OnErrorContainerDark = Color(0xFFF9DEDC)

    val BackgroundDark = Color(0xFF0F172A)  // slate-900
    val OnBackgroundDark = Color(0xFFE2E8F0)  // slate-200
    val SurfaceDark = Color(0xFF1E293B)  // slate-800
    val OnSurfaceDark = Color(0xFFE2E8F0)
    val SurfaceVariantDark = Color(0xFF2D3D52)
    val OnSurfaceVariantDark = Color(0xFF94A3B8)  // slate-400
    val OutlineDark = Color(0xFF64748B)  // slate-500
    val OutlineVariantDark = Color(0xFF475569)  // slate-600

    // SurfaceContainer — dark (slate-tinted)
    val SurfaceContainerLowestDark   = Color(0xFF080E1A)
    val SurfaceContainerLowDark      = Color(0xFF121B2B)
    val SurfaceContainerDark         = Color(0xFF1A2538)
    val SurfaceContainerHighDark     = Color(0xFF1F2D3E)
    val SurfaceContainerHighestDark  = Color(0xFF253547)
}

object FFExtraColors {
    val SuccessLight = Color(0xFF2E7D32)
    val OnSuccessLight = Color(0xFFFFFFFF)
    val WarningLight = Color(0xFFE5A100)
    val OnWarningLight = Color(0xFF1A1100)

    val SuccessDark = Color(0xFF8FD89A)
    val OnSuccessDark = Color(0xFF003910)
    val WarningDark = Color(0xFFFFCE7E)
    val OnWarningDark = Color(0xFF452B00)

    val InfoLight   = Color(0xFF0055CC)
    val OnInfoLight = Color(0xFFFFFFFF)

    val InfoDark    = Color(0xFF7BAAF7)
    val OnInfoDark  = Color(0xFF002D6E)

    // Verde de identidade da marca — mantido como acento em ambos os modos
    val BrandGreenLight = Color(0xFF0B6E4F)
    val BrandGreenContainerLight = Color(0xFFECFDF5)  // emerald-50
    val BrandGreenDark = Color(0xFF34D399)  // emerald-400
    val BrandGreenContainerDark = Color(0xFF064E3B)  // emerald-900
}

data class FFSemanticColors(
    val success: Color,
    val onSuccess: Color,
    val warning: Color,
    val onWarning: Color,
    val info: Color,
    val onInfo: Color,
    val brandGreen: Color,
    val brandGreenContainer: Color,
)

val LightSemanticColors = FFSemanticColors(
    success = FFExtraColors.SuccessLight,
    onSuccess = FFExtraColors.OnSuccessLight,
    warning = FFExtraColors.WarningLight,
    onWarning = FFExtraColors.OnWarningLight,
    info = FFExtraColors.InfoLight,
    onInfo = FFExtraColors.OnInfoLight,
    brandGreen = FFExtraColors.BrandGreenLight,
    brandGreenContainer = FFExtraColors.BrandGreenContainerLight,
)

val DarkSemanticColors = FFSemanticColors(
    success = FFExtraColors.SuccessDark,
    onSuccess = FFExtraColors.OnSuccessDark,
    warning = FFExtraColors.WarningDark,
    onWarning = FFExtraColors.OnWarningDark,
    info = FFExtraColors.InfoDark,
    onInfo = FFExtraColors.OnInfoDark,
    brandGreen = FFExtraColors.BrandGreenDark,
    brandGreenContainer = FFExtraColors.BrandGreenContainerDark,
)

object FFAlpha {
    const val medium = 0.74f
    const val subtle = 0.12f
}

val FFLightColorScheme: ColorScheme = lightColorScheme(
    primary = FFColors.PrimaryLight,
    onPrimary = FFColors.OnPrimaryLight,
    primaryContainer = FFColors.PrimaryContainerLight,
    onPrimaryContainer = FFColors.OnPrimaryContainerLight,
    secondary = FFColors.SecondaryLight,
    onSecondary = FFColors.OnSecondaryLight,
    secondaryContainer = FFColors.SecondaryContainerLight,
    onSecondaryContainer = FFColors.OnSecondaryContainerLight,
    tertiary = FFColors.TertiaryLight,
    onTertiary = FFColors.OnTertiaryLight,
    tertiaryContainer = FFColors.TertiaryContainerLight,
    onTertiaryContainer = FFColors.OnTertiaryContainerLight,
    error = FFColors.ErrorLight,
    onError = FFColors.OnErrorLight,
    errorContainer = FFColors.ErrorContainerLight,
    onErrorContainer = FFColors.OnErrorContainerLight,
    background = FFColors.BackgroundLight,
    onBackground = FFColors.OnBackgroundLight,
    surface = FFColors.SurfaceLight,
    onSurface = FFColors.OnSurfaceLight,
    surfaceVariant = FFColors.SurfaceVariantLight,
    onSurfaceVariant = FFColors.OnSurfaceVariantLight,
    outline = FFColors.OutlineLight,
    outlineVariant = FFColors.OutlineVariantLight,
    surfaceContainerLowest = FFColors.SurfaceContainerLowestLight,
    surfaceContainerLow = FFColors.SurfaceContainerLowLight,
    surfaceContainer = FFColors.SurfaceContainerLight,
    surfaceContainerHigh = FFColors.SurfaceContainerHighLight,
    surfaceContainerHighest = FFColors.SurfaceContainerHighestLight,
)

val FFDarkColorScheme: ColorScheme = darkColorScheme(
    primary = FFColors.PrimaryDark,
    onPrimary = FFColors.OnPrimaryDark,
    primaryContainer = FFColors.PrimaryContainerDark,
    onPrimaryContainer = FFColors.OnPrimaryContainerDark,
    secondary = FFColors.SecondaryDark,
    onSecondary = FFColors.OnSecondaryDark,
    secondaryContainer = FFColors.SecondaryContainerDark,
    onSecondaryContainer = FFColors.OnSecondaryContainerDark,
    tertiary = FFColors.TertiaryDark,
    onTertiary = FFColors.OnTertiaryDark,
    tertiaryContainer = FFColors.TertiaryContainerDark,
    onTertiaryContainer = FFColors.OnTertiaryContainerDark,
    error = FFColors.ErrorDark,
    onError = FFColors.OnErrorDark,
    errorContainer = FFColors.ErrorContainerDark,
    onErrorContainer = FFColors.OnErrorContainerDark,
    background = FFColors.BackgroundDark,
    onBackground = FFColors.OnBackgroundDark,
    surface = FFColors.SurfaceDark,
    onSurface = FFColors.OnSurfaceDark,
    surfaceVariant = FFColors.SurfaceVariantDark,
    onSurfaceVariant = FFColors.OnSurfaceVariantDark,
    outline = FFColors.OutlineDark,
    outlineVariant = FFColors.OutlineVariantDark,
    surfaceContainerLowest = FFColors.SurfaceContainerLowestDark,
    surfaceContainerLow = FFColors.SurfaceContainerLowDark,
    surfaceContainer = FFColors.SurfaceContainerDark,
    surfaceContainerHigh = FFColors.SurfaceContainerHighDark,
    surfaceContainerHighest = FFColors.SurfaceContainerHighestDark,
)

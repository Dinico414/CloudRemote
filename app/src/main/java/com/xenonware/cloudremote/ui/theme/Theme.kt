package com.xenonware.cloudremote.ui.theme

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme.Companion.expressive
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat

data class ExtendedMaterialColorScheme(
    val inverseError: Color,
    val inverseOnError: Color,
    val inverseErrorContainer: Color,
    val inverseOnErrorContainer: Color,
    val label: Color,
)

data class GreenExtendedMaterialColorScheme(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val tertiary: Color,
    val onTertiary: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color,
    val error: Color,
    val onError: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    val outlineVariant: Color,
    val scrim: Color,
    val inverseSurface: Color,
    val inverseOnSurface: Color,
    val inversePrimary: Color,
    val surfaceDim: Color,
    val surfaceBright: Color,
    val surfaceContainerLowest: Color,
    val surfaceContainerLow: Color,
    val surfaceContainer: Color,
    val surfaceContainerHigh: Color,
    val surfaceContainerHighest: Color,
)

data class RedExtendedMaterialColorScheme(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val tertiary: Color,
    val onTertiary: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color,
    val error: Color,
    val onError: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    val outlineVariant: Color,
    val scrim: Color,
    val inverseSurface: Color,
    val inverseOnSurface: Color,
    val inversePrimary: Color,
    val surfaceDim: Color,
    val surfaceBright: Color,
    val surfaceContainerLowest: Color,
    val surfaceContainerLow: Color,
    val surfaceContainer: Color,
    val surfaceContainerHigh: Color,
    val surfaceContainerHighest: Color,
)


val LocalExtendedMaterialColorScheme = staticCompositionLocalOf<ExtendedMaterialColorScheme> {
    error("No ExtendedMaterialColorScheme provided. Did you forget to wrap your Composable in XenonTheme?")
}

val LocalGreenMaterialColorScheme = staticCompositionLocalOf<GreenExtendedMaterialColorScheme> {
    error("No ExtendedMaterialColorScheme provided. Did you forget to wrap your Composable in XenonTheme?")
}

val LocalRedMaterialColorScheme = staticCompositionLocalOf<RedExtendedMaterialColorScheme> {
    error("No ExtendedMaterialColorScheme provided. Did you forget to wrap your Composable in XenonTheme?")
}

val LocalIsDarkTheme = staticCompositionLocalOf<Boolean> {
    error("No IsDarkTheme provided")
}

private val DarkColorScheme = darkColorScheme(
    primary = primaryDark,
    onPrimary = onPrimaryDark,
    primaryContainer = primaryContainerDark,
    onPrimaryContainer = onPrimaryContainerDark,
    secondary = secondaryDark,
    onSecondary = onSecondaryDark,
    secondaryContainer = secondaryContainerDark,
    onSecondaryContainer = onSecondaryContainerDark,
    tertiary = tertiaryDark,
    onTertiary = onTertiaryDark,
    tertiaryContainer = tertiaryContainerDark,
    onTertiaryContainer = onTertiaryContainerDark,
    error = errorDark,
    onError = onErrorDark,
    errorContainer = errorContainerDark,
    onErrorContainer = onErrorContainerDark,
    background = backgroundDark,
    onBackground = onBackgroundDark,
    surface = surfaceDark,
    onSurface = onSurfaceDark,
    surfaceVariant = surfaceVariantDark,
    onSurfaceVariant = onSurfaceVariantDark,
    outline = outlineDark,
    outlineVariant = outlineVariantDark,
    scrim = scrimDark,
    inverseSurface = inverseSurfaceDark,
    inverseOnSurface = inverseOnSurfaceDark,
    inversePrimary = inversePrimaryDark,
    surfaceDim = surfaceDimDark,
    surfaceBright = surfaceBrightDark,
    surfaceContainerLowest = surfaceContainerLowestDark,
    surfaceContainerLow = surfaceContainerLowDark,
    surfaceContainer = surfaceContainerDark,
    surfaceContainerHigh = surfaceContainerHighDark,
    surfaceContainerHighest = surfaceContainerHighestDark
)

private val LightColorScheme = lightColorScheme(
    primary = primaryLight,
    onPrimary = onPrimaryLight,
    primaryContainer = primaryContainerLight,
    onPrimaryContainer = onPrimaryContainerLight,
    secondary = secondaryLight,
    onSecondary = onSecondaryLight,
    secondaryContainer = secondaryContainerLight,
    onSecondaryContainer = onSecondaryContainerLight,
    tertiary = tertiaryLight,
    onTertiary = onTertiaryLight,
    tertiaryContainer = tertiaryContainerLight,
    onTertiaryContainer = onTertiaryContainerLight,
    error = errorLight,
    onError = onErrorLight,
    errorContainer = errorContainerLight,
    onErrorContainer = onErrorContainerLight,
    background = backgroundLight,
    onBackground = onBackgroundLight,
    surface = surfaceLight,
    onSurface = onSurfaceLight,
    surfaceVariant = surfaceVariantLight,
    onSurfaceVariant = onSurfaceVariantLight,
    outline = outlineLight,
    outlineVariant = outlineVariantLight,
    scrim = scrimLight,
    inverseSurface = inverseSurfaceLight,
    inverseOnSurface = inverseOnSurfaceLight,
    inversePrimary = inversePrimaryLight,
    surfaceDim = surfaceDimLight,
    surfaceBright = surfaceBrightLight,
    surfaceContainerLowest = surfaceContainerLowestLight,
    surfaceContainerLow = surfaceContainerLowLight,
    surfaceContainer = surfaceContainerLight,
    surfaceContainerHigh = surfaceContainerHighLight,
    surfaceContainerHighest = surfaceContainerHighestLight
)
private val GreenDarkColorScheme = darkColorScheme(
    primary = greenPrimaryDark,
    onPrimary = greenOnPrimaryDark,
    primaryContainer = greenPrimaryContainerDark,
    onPrimaryContainer = greenOnPrimaryContainerDark,
    secondary = greenSecondaryDark,
    onSecondary = greenOnSecondaryDark,
    secondaryContainer = greenSecondaryContainerDark,
    onSecondaryContainer = greenOnSecondaryContainerDark,
    tertiary = greenTertiaryDark,
    onTertiary = greenOnTertiaryDark,
    tertiaryContainer = greenTertiaryContainerDark,
    onTertiaryContainer = greenOnTertiaryContainerDark,
    error = greenErrorDark,
    onError = greenOnErrorDark,
    errorContainer = greenErrorContainerDark,
    onErrorContainer = greenOnErrorContainerDark,
    background = greenBackgroundDark,
    onBackground = greenOnBackgroundDark,
    surface = greenSurfaceDark,
    onSurface = greenOnSurfaceDark,
    surfaceVariant = greenSurfaceVariantDark,
    onSurfaceVariant = greenOnSurfaceVariantDark,
    outline = greenOutlineDark,
    outlineVariant = greenOutlineVariantDark,
    scrim = greenScrimDark,
    inverseSurface = greenInverseSurfaceDark,
    inverseOnSurface = greenInverseOnSurfaceDark,
    inversePrimary = greenInversePrimaryDark,
    surfaceDim = greenSurfaceDimDark,
    surfaceBright = greenSurfaceBrightDark,
    surfaceContainerLowest = greenSurfaceContainerLowestDark,
    surfaceContainerLow = greenSurfaceContainerLowDark,
    surfaceContainer = greenSurfaceContainerDark,
    surfaceContainerHigh = greenSurfaceContainerHighDark,
    surfaceContainerHighest = greenSurfaceContainerHighestDark
)

private val GreenLightColorScheme = lightColorScheme(
    primary = greenPrimaryLight,
    onPrimary = greenOnPrimaryLight,
    primaryContainer = greenPrimaryContainerLight,
    onPrimaryContainer = greenOnPrimaryContainerLight,
    secondary = greenSecondaryLight,
    onSecondary = greenOnSecondaryLight,
    secondaryContainer = greenSecondaryContainerLight,
    onSecondaryContainer = greenOnSecondaryContainerLight,
    tertiary = greenTertiaryLight,
    onTertiary = greenOnTertiaryLight,
    tertiaryContainer = greenTertiaryContainerLight,
    onTertiaryContainer = greenOnTertiaryContainerLight,
    error = greenErrorLight,
    onError = greenOnErrorLight,
    errorContainer = greenErrorContainerLight,
    onErrorContainer = greenOnErrorContainerLight,
    background = greenBackgroundLight,
    onBackground = greenOnBackgroundLight,
    surface = greenSurfaceLight,
    onSurface = greenOnSurfaceLight,
    surfaceVariant = greenSurfaceVariantLight,
    onSurfaceVariant = greenOnSurfaceVariantLight,
    outline = greenOutlineLight,
    outlineVariant = greenOutlineVariantLight,
    scrim = greenScrimLight,
    inverseSurface = greenInverseSurfaceLight,
    inverseOnSurface = greenInverseOnSurfaceLight,
    inversePrimary = greenInversePrimaryLight,
    surfaceDim = greenSurfaceDimLight,
    surfaceBright = greenSurfaceBrightLight,
    surfaceContainerLowest = greenSurfaceContainerLowestLight,
    surfaceContainerLow = greenSurfaceContainerLowLight,
    surfaceContainer = greenSurfaceContainerLight,
    surfaceContainerHigh = greenSurfaceContainerHighLight,
    surfaceContainerHighest = greenSurfaceContainerHighestLight
)

private val RedDarkColorScheme = darkColorScheme(
    primary = redPrimaryDark,
    onPrimary = redOnPrimaryDark,
    primaryContainer = redPrimaryContainerDark,
    onPrimaryContainer = redOnPrimaryContainerDark,
    secondary = redSecondaryDark,
    onSecondary = redOnSecondaryDark,
    secondaryContainer = redSecondaryContainerDark,
    onSecondaryContainer = redOnSecondaryContainerDark,
    tertiary = redTertiaryDark,
    onTertiary = redOnTertiaryDark,
    tertiaryContainer = redTertiaryContainerDark,
    onTertiaryContainer = redOnTertiaryContainerDark,
    error = redErrorDark,
    onError = redOnErrorDark,
    errorContainer = redErrorContainerDark,
    onErrorContainer = redOnErrorContainerDark,
    background = redBackgroundDark,
    onBackground = redOnBackgroundDark,
    surface = redSurfaceDark,
    onSurface = redOnSurfaceDark,
    surfaceVariant = redSurfaceVariantDark,
    onSurfaceVariant = redOnSurfaceVariantDark,
    outline = redOutlineDark,
    outlineVariant = redOutlineVariantDark,
    scrim = redScrimDark,
    inverseSurface = redInverseSurfaceDark,
    inverseOnSurface = redInverseOnSurfaceDark,
    inversePrimary = redInversePrimaryDark,
    surfaceDim = redSurfaceDimDark,
    surfaceBright = redSurfaceBrightDark,
    surfaceContainerLowest = redSurfaceContainerLowestDark,
    surfaceContainerLow = redSurfaceContainerLowDark,
    surfaceContainer = redSurfaceContainerDark,
    surfaceContainerHigh = redSurfaceContainerHighDark,
    surfaceContainerHighest = redSurfaceContainerHighestDark
)

private val RedLightColorScheme = lightColorScheme(
    primary = redPrimaryLight,
    onPrimary = redOnPrimaryLight,
    primaryContainer = redPrimaryContainerLight,
    onPrimaryContainer = redOnPrimaryContainerLight,
    secondary = redSecondaryLight,
    onSecondary = redOnSecondaryLight,
    secondaryContainer = redSecondaryContainerLight,
    onSecondaryContainer = redOnSecondaryContainerLight,
    tertiary = redTertiaryLight,
    onTertiary = redOnTertiaryLight,
    tertiaryContainer = redTertiaryContainerLight,
    onTertiaryContainer = redOnTertiaryContainerLight,
    error = redErrorLight,
    onError = redOnErrorLight,
    errorContainer = redErrorContainerLight,
    onErrorContainer = redOnErrorContainerLight,
    background = redBackgroundLight,
    onBackground = redOnBackgroundLight,
    surface = redSurfaceLight,
    onSurface = redOnSurfaceLight,
    surfaceVariant = redSurfaceVariantLight,
    onSurfaceVariant = redOnSurfaceVariantLight,
    outline = redOutlineLight,
    outlineVariant = redOutlineVariantLight,
    scrim = redScrimLight,
    inverseSurface = redInverseSurfaceLight,
    inverseOnSurface = redInverseOnSurfaceLight,
    inversePrimary = redInversePrimaryLight,
    surfaceDim = redSurfaceDimLight,
    surfaceBright = redSurfaceBrightLight,
    surfaceContainerLowest = redSurfaceContainerLowestLight,
    surfaceContainerLow = redSurfaceContainerLowLight,
    surfaceContainer = redSurfaceContainerLight,
    surfaceContainerHigh = redSurfaceContainerHighLight,
    surfaceContainerHighest = redSurfaceContainerHighestLight
)

fun Color.adjustTone(targetTone: Float): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(this.toArgb(), hsl)
    hsl[2] = targetTone.coerceIn(0f, 100f) / 100f
    return Color(ColorUtils.HSLToColor(hsl))
}

fun ColorScheme.toBlackedOut(): ColorScheme {
    return this.copy(
        background = Color.Black,
        surfaceContainer = Color.Black,
        surfaceBright = surfaceDim,
        surfaceDim = surfaceDim.adjustTone(2f)
    )
}

fun ColorScheme.toCoverMode(): ColorScheme {
    return this.copy(
        background = Color.Black, surfaceContainer = Color.Black, surfaceBright = Color.Black
    )
}

@SuppressLint("ObsoleteSdkInt")
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun XenonTheme(
    darkTheme: Boolean,
    useBlackedOutDarkTheme: Boolean = false,
    isCoverMode: Boolean = false,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current

    val baseColorScheme: ColorScheme = if (darkTheme) {
        val baseDarkScheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            dynamicDarkColorScheme(context)
        } else {
            DarkColorScheme
        }
        when {
            isCoverMode -> baseDarkScheme.toCoverMode()
            useBlackedOutDarkTheme -> baseDarkScheme.toBlackedOut()
            else -> baseDarkScheme
        }
    } else {
        if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            dynamicLightColorScheme(context)
        } else {
            LightColorScheme
        }
    }

    val extendedColorScheme = remember(darkTheme) {
        if (darkTheme) {
            ExtendedMaterialColorScheme(
                inverseError = inverseErrorDark,
                inverseOnError = inverseOnErrorDark,
                inverseErrorContainer = inverseErrorContainerDark,
                inverseOnErrorContainer = inverseOnErrorContainerDark,
                label = labelDark,
            )
        } else {
            ExtendedMaterialColorScheme(
                inverseError = inverseErrorLight,
                inverseOnError = inverseOnErrorLight,
                inverseErrorContainer = inverseErrorContainerLight,
                inverseOnErrorContainer = inverseOnErrorContainerLight,
                label = labelLight,
            )
        }
    }
    val greenColorScheme = remember(darkTheme) {
        if (darkTheme) {
            GreenExtendedMaterialColorScheme(
                primary = greenPrimaryDark,
                onPrimary = greenOnPrimaryDark,
                primaryContainer = greenPrimaryContainerDark,
                onPrimaryContainer = greenOnPrimaryContainerDark,
                secondary = greenSecondaryDark,
                onSecondary = greenOnSecondaryDark,
                secondaryContainer = greenSecondaryContainerDark,
                onSecondaryContainer = greenOnSecondaryContainerDark,
                tertiary = greenTertiaryDark,
                onTertiary = greenOnTertiaryDark,
                tertiaryContainer = greenTertiaryContainerDark,
                onTertiaryContainer = greenOnTertiaryContainerDark,
                error = greenErrorDark,
                onError = greenOnErrorDark,
                errorContainer = greenErrorContainerDark,
                onErrorContainer = greenOnErrorContainerDark,
                background = greenBackgroundDark,
                onBackground = greenOnBackgroundDark,
                surface = greenSurfaceDark,
                onSurface = greenOnSurfaceDark,
                surfaceVariant = greenSurfaceVariantDark,
                onSurfaceVariant = greenOnSurfaceVariantDark,
                outline = greenOutlineDark,
                outlineVariant = greenOutlineVariantDark,
                scrim = greenScrimDark,
                inverseSurface = greenInverseSurfaceDark,
                inverseOnSurface = greenInverseOnSurfaceDark,
                inversePrimary = greenInversePrimaryDark,
                surfaceDim = greenSurfaceDimDark,
                surfaceBright = greenSurfaceBrightDark,
                surfaceContainerLowest = greenSurfaceContainerLowestDark,
                surfaceContainerLow = greenSurfaceContainerLowDark,
                surfaceContainer = greenSurfaceContainerDark,
                surfaceContainerHigh = greenSurfaceContainerHighDark,
                surfaceContainerHighest = greenSurfaceContainerHighestDark,
            )
        } else {
            GreenExtendedMaterialColorScheme(
                primary = greenPrimaryLight,
                onPrimary = greenOnPrimaryLight,
                primaryContainer = greenPrimaryContainerLight,
                onPrimaryContainer = greenOnPrimaryContainerLight,
                secondary = greenSecondaryLight,
                onSecondary = greenOnSecondaryLight,
                secondaryContainer = greenSecondaryContainerLight,
                onSecondaryContainer = greenOnSecondaryContainerLight,
                tertiary = greenTertiaryLight,
                onTertiary = greenOnTertiaryLight,
                tertiaryContainer = greenTertiaryContainerLight,
                onTertiaryContainer = greenOnTertiaryContainerLight,
                error = greenErrorLight,
                onError = greenOnErrorLight,
                errorContainer = greenErrorContainerLight,
                onErrorContainer = greenOnErrorContainerLight,
                background = greenBackgroundLight,
                onBackground = greenOnBackgroundLight,
                surface = greenSurfaceLight,
                onSurface = greenOnSurfaceLight,
                surfaceVariant = greenSurfaceVariantLight,
                onSurfaceVariant = greenOnSurfaceVariantLight,
                outline = greenOutlineLight,
                outlineVariant = greenOutlineVariantLight,
                scrim = greenScrimLight,
                inverseSurface = greenInverseSurfaceLight,
                inverseOnSurface = greenInverseOnSurfaceLight,
                inversePrimary = greenInversePrimaryLight,
                surfaceDim = greenSurfaceDimLight,
                surfaceBright = greenSurfaceBrightLight,
                surfaceContainerLowest = greenSurfaceContainerLowestLight,
                surfaceContainerLow = greenSurfaceContainerLowLight,
                surfaceContainer = greenSurfaceContainerLight,
                surfaceContainerHigh = greenSurfaceContainerHighLight,
                surfaceContainerHighest = greenSurfaceContainerHighestLight,
            )
        }
    }
    val redColorScheme = remember(darkTheme) {
        if (darkTheme) {
            RedExtendedMaterialColorScheme(
                primary = redPrimaryDark,
                onPrimary = redOnPrimaryDark,
                primaryContainer = redPrimaryContainerDark,
                onPrimaryContainer = redOnPrimaryContainerDark,
                secondary = redSecondaryDark,
                onSecondary = redOnSecondaryDark,
                secondaryContainer = redSecondaryContainerDark,
                onSecondaryContainer = redOnSecondaryContainerDark,
                tertiary = redTertiaryDark,
                onTertiary = redOnTertiaryDark,
                tertiaryContainer = redTertiaryContainerDark,
                onTertiaryContainer = redOnTertiaryContainerDark,
                error = redErrorDark,
                onError = redOnErrorDark,
                errorContainer = redErrorContainerDark,
                onErrorContainer = redOnErrorContainerDark,
                background = redBackgroundDark,
                onBackground = redOnBackgroundDark,
                surface = redSurfaceDark,
                onSurface = redOnSurfaceDark,
                surfaceVariant = redSurfaceVariantDark,
                onSurfaceVariant = redOnSurfaceVariantDark,
                outline = redOutlineDark,
                outlineVariant = redOutlineVariantDark,
                scrim = redScrimDark,
                inverseSurface = redInverseSurfaceDark,
                inverseOnSurface = redInverseOnSurfaceDark,
                inversePrimary = redInversePrimaryDark,
                surfaceDim = redSurfaceDimDark,
                surfaceBright = redSurfaceBrightDark,
                surfaceContainerLowest = redSurfaceContainerLowestDark,
                surfaceContainerLow = redSurfaceContainerLowDark,
                surfaceContainer = redSurfaceContainerDark,
                surfaceContainerHigh = redSurfaceContainerHighDark,
                surfaceContainerHighest = redSurfaceContainerHighestDark,
            )
        } else {
            RedExtendedMaterialColorScheme(
                primary = redPrimaryLight,
                onPrimary = redOnPrimaryLight,
                primaryContainer = redPrimaryContainerLight,
                onPrimaryContainer = redOnPrimaryContainerLight,
                secondary = redSecondaryLight,
                onSecondary = redOnSecondaryLight,
                secondaryContainer = redSecondaryContainerLight,
                onSecondaryContainer = redOnSecondaryContainerLight,
                tertiary = redTertiaryLight,
                onTertiary = redOnTertiaryLight,
                tertiaryContainer = redTertiaryContainerLight,
                onTertiaryContainer = redOnTertiaryContainerLight,
                error = redErrorLight,
                onError = redOnErrorLight,
                errorContainer = redErrorContainerLight,
                onErrorContainer = redOnErrorContainerLight,
                background = redBackgroundLight,
                onBackground = redOnBackgroundLight,
                surface = redSurfaceLight,
                onSurface = redOnSurfaceLight,
                surfaceVariant = redSurfaceVariantLight,
                onSurfaceVariant = redOnSurfaceVariantLight,
                outline = redOutlineLight,
                outlineVariant = redOutlineVariantLight,
                scrim = redScrimLight,
                inverseSurface = redInverseSurfaceLight,
                inverseOnSurface = redInverseOnSurfaceLight,
                inversePrimary = redInversePrimaryLight,
                surfaceDim = redSurfaceDimLight,
                surfaceBright = redSurfaceBrightLight,
                surfaceContainerLowest = redSurfaceContainerLowestLight,
                surfaceContainerLow = redSurfaceContainerLowLight,
                surfaceContainer = redSurfaceContainerLight,
                surfaceContainerHigh = redSurfaceContainerHighLight,
                surfaceContainerHighest = redSurfaceContainerHighestLight
            )
        }
    }


    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.setDecorFitsSystemWindows(window, false)

            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(
        LocalExtendedMaterialColorScheme provides extendedColorScheme,
        LocalGreenMaterialColorScheme provides greenColorScheme,
        LocalRedMaterialColorScheme provides redColorScheme,
        LocalIsDarkTheme provides darkTheme
    ) {
        MaterialTheme(
            colorScheme = baseColorScheme,
            typography = Typography,
            motionScheme = expressive(),
            content = content
        )
    }
}

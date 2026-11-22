package tea.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

private val LocalNarrator = staticCompositionLocalOf { LightNarrator }
private val LocalTeaType = staticCompositionLocalOf<TeaTypography> { error("No TeaTheme") }
private val LocalTeaFonts = staticCompositionLocalOf<TeaFonts> { error("No TeaTheme") }
private val LocalCompact = staticCompositionLocalOf { false }
private val LocalLargeText = staticCompositionLocalOf { false }

/** Font scale multiplier applied when `largeText` is on (plan §8.4). */
const val LARGE_TEXT_SCALE = 1.25f

/**
 * Root of every ThisIsTea screen. Provides the narrator palette, type scale, fonts, persona
 * styles, and a MaterialTheme mapped onto them (so the few material3 pieces match).
 *
 * @param dark narrator dark palette. Spider stays dark in both modes.
 * @param largeText multiplies the platform font scale by [LARGE_TEXT_SCALE].
 */
@Composable
fun TeaTheme(
    dark: Boolean = isSystemInDarkTheme(),
    largeText: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = if (dark) DarkNarrator else LightNarrator
    val fonts = rememberTeaFonts()
    val personas = remember(fonts, dark) { personaStyles(fonts, dark) }
    val density = LocalDensity.current
    val scaledDensity = remember(density, largeText) {
        if (largeText) Density(density.density, density.fontScale * LARGE_TEXT_SCALE) else density
    }
    BoxWithConstraints {
        val compact = maxWidth < TeaSize.compactBreakpoint
        val type = remember(fonts, compact) { teaTypography(fonts, compact) }
        val scheme = if (dark) {
            darkColorScheme(
                primary = colors.ink, onPrimary = colors.paper,
                secondary = colors.go, onSecondary = colors.onGo,
                background = colors.paper, onBackground = colors.ink,
                surface = colors.card, onSurface = colors.ink,
                surfaceVariant = colors.paper, onSurfaceVariant = colors.inkSoft,
                surfaceContainer = colors.card, surfaceContainerHigh = colors.card,
                outline = colors.line, outlineVariant = colors.rule,
                error = colors.bad, onError = Color.White,
            )
        } else {
            lightColorScheme(
                primary = colors.ink, onPrimary = colors.paper,
                secondary = colors.go, onSecondary = colors.onGo,
                background = colors.paper, onBackground = colors.ink,
                surface = colors.card, onSurface = colors.ink,
                surfaceVariant = colors.paper, onSurfaceVariant = colors.inkSoft,
                surfaceContainer = colors.card, surfaceContainerHigh = colors.card,
                outline = colors.line, outlineVariant = colors.rule,
                error = colors.bad, onError = Color.White,
            )
        }
        val typography = Typography(
            displayLarge = type.h1, displayMedium = type.h2, displaySmall = type.h3,
            headlineLarge = type.h2, headlineMedium = type.h3, headlineSmall = type.title,
            titleLarge = type.title, titleMedium = type.label, titleSmall = type.smallStrong,
            bodyLarge = type.body, bodyMedium = type.body, bodySmall = type.small,
            labelLarge = type.label, labelMedium = type.smallStrong, labelSmall = type.xs,
        )
        CompositionLocalProvider(
            LocalDensity provides scaledDensity,
            LocalNarrator provides colors,
            LocalTeaType provides type,
            LocalTeaFonts provides fonts,
            LocalPersonaStyles provides personas,
            LocalCompact provides compact,
            LocalLargeText provides largeText,
        ) {
            MaterialTheme(colorScheme = scheme, typography = typography) {
                CompositionLocalProvider(
                    LocalContentColor provides colors.ink,
                    LocalTextStyle provides type.body,
                    LocalTextSelectionColors provides TextSelectionColors(
                        handleColor = colors.focus,
                        backgroundColor = colors.focus.copy(alpha = 0.35f),
                    ),
                    content = content,
                )
            }
        }
    }
}

/** Accessors for the current theme (use inside [TeaTheme]). */
object TeaTheme {
    val colors: NarratorColors
        @Composable @ReadOnlyComposable get() = LocalNarrator.current
    val type: TeaTypography
        @Composable @ReadOnlyComposable get() = LocalTeaType.current
    val fonts: TeaFonts
        @Composable @ReadOnlyComposable get() = LocalTeaFonts.current
    val personas: PersonaStyles
        @Composable @ReadOnlyComposable get() = LocalPersonaStyles.current
    val isDark: Boolean
        @Composable @ReadOnlyComposable get() = LocalNarrator.current.isDark
    /** Available width < 600dp: single column, compact heading sizes. */
    val isCompact: Boolean
        @Composable @ReadOnlyComposable get() = LocalCompact.current
    val isLargeText: Boolean
        @Composable @ReadOnlyComposable get() = LocalLargeText.current
}

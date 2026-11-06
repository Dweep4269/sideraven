/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import mozilla.components.compose.base.theme.acornPrivateColorScheme
import org.mozilla.fenix.theme.FirefoxTheme
import mozilla.components.compose.base.theme.Theme

/*
 * Zen Visual Language tokens. Restrained strategy: neutrals plus one accent,
 * reserved for the Active Tab Indicator, focus, drop targets and primary actions.
 * Values are starting points; DESIGN.md records the final ones.
 */

private val ZenDark: ColorScheme =
    darkColorScheme(
        primary = Color(0xFFF0A987), // the one accent
        onPrimary = Color(0xFF3A1D10),
        primaryContainer = Color(0xFF5A3322),
        onPrimaryContainer = Color(0xFFFFDBCB),
        inversePrimary = Color(0xFF9A4E2F),
        secondary = Color(0xFFC9C5CE),
        onSecondary = Color(0xFF2F2E34),
        secondaryContainer = Color(0xFF3A3940),
        onSecondaryContainer = Color(0xFFE6E3EA),
        tertiary = Color(0xFFB8C7D9),
        onTertiary = Color(0xFF22303F),
        tertiaryContainer = Color(0xFF394656),
        onTertiaryContainer = Color(0xFFD6E3F5),
        background = Color(0xFF161519),
        onBackground = Color(0xFFECEAF0),
        surface = Color(0xFF1C1B20), // the frame: Sidebar + frame surface (Fenix's toolbar colour)
        onSurface = Color(0xFFECEAF0),
        surfaceVariant = Color(0xFF2C2B31),
        onSurfaceVariant = Color(0xFFA8A6AE),
        surfaceTint = Color(0xFFF0A987),
        inverseSurface = Color(0xFFECEAF0),
        inverseOnSurface = Color(0xFF2F2E34),
        outline = Color(0xFF5C5A62),
        outlineVariant = Color(0xFF34333A), // Divider
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFF3A393F),
        surfaceDim = Color(0xFF161519),
        surfaceContainerLowest = Color(0xFF121115),
        surfaceContainerLow = Color(0xFF1A191E),
        surfaceContainer = Color(0xFF242329), // pressed/hovered pill
        surfaceContainerHigh = Color(0xFF2C2B31), // selected tab pill
        surfaceContainerHighest = Color(0xFF36353B),
    )

private val ZenLight: ColorScheme =
    lightColorScheme(
        primary = Color(0xFFA9502D), // darker than the dark-theme accent so white text keeps 4.5:1
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFFFDBCB),
        onPrimaryContainer = Color(0xFF3A1D10),
        inversePrimary = Color(0xFFF0A987),
        secondary = Color(0xFF5E5C66),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFE4E3E8),
        onSecondaryContainer = Color(0xFF1D1C21),
        tertiary = Color(0xFF4E5D6E),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFD6E3F5),
        onTertiaryContainer = Color(0xFF0B1C2B),
        background = Color(0xFFF6F5F8),
        onBackground = Color(0xFF1D1C21),
        surface = Color(0xFFEEEDF0),
        onSurface = Color(0xFF1D1C21),
        surfaceVariant = Color(0xFFE4E3E8),
        onSurfaceVariant = Color(0xFF5E5C66),
        surfaceTint = Color(0xFFA9502D),
        inverseSurface = Color(0xFF2F2E34),
        inverseOnSurface = Color(0xFFF2F0F5),
        outline = Color(0xFF8A8891),
        outlineVariant = Color(0xFFD9D7DE),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFFFFFFFF),
        surfaceDim = Color(0xFFD9D7DE),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF6F5F8),
        surfaceContainer = Color(0xFFE4E3E8),
        surfaceContainerHigh = Color(0xFFFFFFFF),
        surfaceContainerHighest = Color(0xFFDCDBE0),
    )

/** The Zen colour scheme (every Material role set, so nothing falls back to Material's purple). */
fun zenColorScheme(dark: Boolean): ColorScheme = if (dark) ZenDark else ZenLight

/** One corner scale: small for items, medium for panels and the frame. */
private val ZenShapes =
    Shapes(
        small = RoundedCornerShape(10.dp),
        medium = RoundedCornerShape(14.dp),
    )

/** Motion tokens from the spec's motion plan. Never ease-in on UI. */
object SideravenMotion {
    /** Entrances and state changes. */
    val EaseOutStrong: Easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

    /** Movement of something already on screen. */
    val EaseInOutStrong: Easing = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)

    /** Old selection fill fading out. */
    const val SELECTION_FADE_MS = 120

    /** A new tab entering the Tab List (placement + fade). */
    const val ITEM_ENTER_MS = 180

    /** Other tabs sliding to close the gap after a close, or to make room. */
    const val ITEM_PLACEMENT_MS = 200

    /** Sidebar width change between Persistent and Collapsed. */
    const val MODE_CHANGE_MS = 220

    /** Auto-Hide reveal and dismissal (drawer-sized motion). */
    const val REVEAL_MS = 250
    const val DISMISS_MS = 200

    /** How far (dp) the open tray may spring past its edge before settling back. */
    const val TRAY_OVERSHOOT_DP = 16

    /** Gesture hand-off and list placement: critically damped, carries velocity, never bounces (≈0.3s). */
    const val SETTLE_STIFFNESS = androidx.compose.animation.core.Spring.StiffnessMediumLow

    /** Throwing something off screen after a committed swipe: fast, still no bounce. */
    const val THROW_STIFFNESS = androidx.compose.animation.core.Spring.StiffnessMedium

    /**
     * Damping of a gesture let go short of acting (a swiped tab, the tray, a dragged item put back):
     * below 1, so it passes its place a little and settles, like something physical.
     */
    const val SPRING_SETTLE_DAMPING = 0.7f

    /** Disclosure chevrons (Inactive Tabs, Desktop Tabs, Folders). */
    const val CHEVRON_ROTATE_MS = 180

    /** Scrim opacity when the Sidebar is fully revealed. */
    const val SCRIM_ALPHA = 0.32f
}

/**
 * Wraps Sideraven UI in the Zen palette, following the system dark/light setting. Private Mode
 * uses Fenix's private colours instead, so it always reads as private.
 */
@Composable
fun SideravenTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    isPrivate: Boolean = false,
    spaceAccent: String? = null,
    // Pure black (Sideraven Settings): only in the dark theme, and never for Private, which keeps Fenix's colours.
    pureBlack: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme =
            when {
                isPrivate -> acornPrivateColorScheme()
                // The current Space's accent tints the Active Tab Indicator and the Space header.
                spaceAccent != null -> zenColorScheme(darkTheme).withSpaceAccent(spaceAccent, darkTheme)
                darkTheme -> ZenDark
                else -> ZenLight
            }.let { if (pureBlack && darkTheme && !isPrivate) it.pureBlack() else it },
        shapes = ZenShapes,
        content = content,
    )
}

/**
 * Fenix's theme with the Zen Visual Language applied on top, for upstream screens restyled
 * through theming only (the Tab Tray). Private keeps Fenix's private colours.
 */
@Composable
fun ZenFirefoxTheme(
    theme: Theme,
    content: @Composable () -> Unit,
) {
    FirefoxTheme(theme = theme) {
        if (theme == Theme.Private) {
            content()
        } else {
            MaterialTheme(
                colorScheme = zenColorScheme(dark = theme == Theme.Dark),
                shapes = ZenShapes,
                typography = MaterialTheme.typography,
                content = content,
            )
        }
    }
}

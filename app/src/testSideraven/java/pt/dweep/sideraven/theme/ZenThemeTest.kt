/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import mozilla.components.compose.base.theme.Theme
import kotlin.math.max
import kotlin.math.min

/** The Zen scheme is complete and the Tab Tray wrapper applies it (Private excepted). */
@RunWith(AndroidJUnit4::class)
class ZenThemeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun contrast(
        a: Color,
        b: Color,
    ): Float {
        val l1 = a.luminance() + 0.05f
        val l2 = b.luminance() + 0.05f
        return max(l1, l2) / min(l1, l2)
    }

    private fun roles(scheme: ColorScheme) =
        listOf(
            scheme.primary, scheme.secondary, scheme.tertiary, scheme.secondaryContainer, scheme.primaryContainer,
            scheme.surfaceVariant, scheme.background, scheme.outline,
            scheme.surfaceContainerHighest, scheme.inverseSurface,
        )

    @Test
    fun `THEN no role falls back to Material's default purple`() {
        listOf(true to darkColorScheme(), false to lightColorScheme()).forEach { (dark, baseline) ->
            roles(zenColorScheme(dark)).zip(roles(baseline)).forEach { (zen, material) -> assertNotEquals(material, zen) }
        }
    }

    @Test
    fun `THEN text on surfaces meets 4_5 to 1 contrast in both variants`() {
        listOf(true, false).forEach { dark ->
            val scheme = zenColorScheme(dark)
            assertTrue(contrast(scheme.onSurface, scheme.surface) >= 4.5f)
            assertTrue(contrast(scheme.onSurfaceVariant, scheme.surface) >= 4.5f)
            assertTrue(contrast(scheme.onSurface, scheme.surfaceContainerHigh) >= 4.5f)
            assertTrue(contrast(scheme.onPrimary, scheme.primary) >= 4.5f)
        }
    }

    @Test
    fun `WHEN the Tab Tray is themed THEN it gets the Zen scheme, except in Private Mode`() {
        var normal: Color? = null
        var private: Color? = null
        composeRule.setContent {
            ZenFirefoxTheme(theme = Theme.Dark) { normal = MaterialTheme.colorScheme.surface }
            ZenFirefoxTheme(theme = Theme.Private) { private = MaterialTheme.colorScheme.surface }
        }

        assertEquals(zenColorScheme(dark = true).surface, normal)
        assertNotEquals(zenColorScheme(dark = true).surface, private)
    }
}

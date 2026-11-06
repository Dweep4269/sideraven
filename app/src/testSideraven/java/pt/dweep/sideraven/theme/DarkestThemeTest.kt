/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.theme

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.fenix.utils.Settings

/** Fenix's Darkest theme replaces Sideraven's own Pure black switch. */
@RunWith(AndroidJUnit4::class)
class DarkestThemeTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val sidebarPrefs = context.getSharedPreferences("sideraven_sidebar", Context.MODE_PRIVATE)

    @Test
    fun `THEN Fenix offers its Darkest theme`() {
        assertTrue(Settings(context).enableOledTheme)
    }

    @Test
    fun `GIVEN Pure black was on in 1_1 WHEN Sideraven starts THEN Fenix's Darkest theme is chosen, once`() {
        sidebarPrefs.edit().putBoolean("pure_black", true).commit()
        val settings = Settings(context)
        settings.shouldUseDarkTheme = true

        DarkestTheme.migratePureBlack(context, settings)

        assertTrue(settings.shouldUseOledTheme)
        assertFalse(settings.shouldUseDarkTheme)
        assertFalse(sidebarPrefs.contains("pure_black"))

        // Choosing another theme afterwards sticks.
        settings.shouldUseOledTheme = false
        settings.shouldUseLightTheme = true
        DarkestTheme.migratePureBlack(context, settings)
        assertFalse(settings.shouldUseOledTheme)
    }

    @Test
    fun `GIVEN Pure black was off THEN the chosen theme is left alone`() {
        sidebarPrefs.edit().putBoolean("pure_black", false).commit()
        val settings = Settings(context)
        settings.shouldUseDarkTheme = true

        DarkestTheme.migratePureBlack(context, settings)

        assertTrue(settings.shouldUseDarkTheme)
        assertFalse(settings.shouldUseOledTheme)
    }
}

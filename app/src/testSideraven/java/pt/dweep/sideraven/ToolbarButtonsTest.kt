/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Fenix's toolbar beside the Sidebar. */
@RunWith(AndroidJUnit4::class)
class ToolbarButtonsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @After
    fun reset() {
        SidebarGate.sidebarSurface.value = null
    }

    @Test
    fun `THEN the tab counter slot shows without a Sidebar, and with one only in Auto-Hide with the handle off`() {
        assertTrue(SidebarGate.showsTabCounter(SidebarTier.None, null, flapEnabled = true))
        assertFalse(SidebarGate.showsTabCounter(SidebarTier.Tablet, SidebarMode.Persistent, flapEnabled = false))
        assertFalse(SidebarGate.showsTabCounter(SidebarTier.Tablet, SidebarMode.Collapsed, flapEnabled = false))
        assertFalse(SidebarGate.showsTabCounter(SidebarTier.Tablet, SidebarMode.AutoHide, flapEnabled = true))
        assertTrue(SidebarGate.showsTabCounter(SidebarTier.Tablet, SidebarMode.AutoHide, flapEnabled = false))
    }

    @Test
    fun `THEN a New Tab quick action is hidden beside a Sidebar, and other quick actions stay`() {
        assertTrue(SidebarGate.hidesShortcut(SidebarTier.Tablet, isNewTab = true))
        assertFalse(SidebarGate.hidesShortcut(SidebarTier.Tablet, isNewTab = false))
        assertFalse(SidebarGate.hidesShortcut(SidebarTier.None, isNewTab = true))
    }

    @Test
    fun `GIVEN Tab counter and New tab in the toolbar THEN beside a Sidebar both show, and the counter is the real one`() {
        assertTrue(SidebarGate.showsTabCounter(SidebarTier.Tablet, SidebarMode.Persistent, flapEnabled = false, tabButtons = true))
        assertTrue(SidebarGate.showsTabCounter(SidebarTier.Tablet, SidebarMode.Collapsed, flapEnabled = true, tabButtons = true))
        assertFalse(SidebarGate.hidesShortcut(SidebarTier.Tablet, isNewTab = true, tabButtons = true))
        assertFalse(SidebarGate.showsSidebarButton(SidebarTier.Tablet, tabButtons = true))
        assertTrue(SidebarGate.showsSidebarButton(SidebarTier.Tablet, tabButtons = false))
        assertFalse(SidebarGate.showsSidebarButton(SidebarTier.None, tabButtons = false))
    }

    @Test
    fun `THEN the Tab counter and New tab switch is off by default and shows only with the Sidebar and toolbar layout`() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = InMemorySidebarModeStore()
        store.addressBarLayout.set(AddressBarLayout.Sidebar)
        val screen = androidx.preference.PreferenceManager(context).createPreferenceScreen(context)
        val switch = pt.dweep.sideraven.settings.SidebarPreferences.bindToolbarTabButtons(context, screen, store)

        assertFalse(store.toolbarTabButtons.value)
        assertFalse(switch.isVisible)

        pt.dweep.sideraven.settings.SidebarPreferences.bindLayout(context, screen, store) { switch.isVisible = it == AddressBarLayout.Top }
            .choose(AddressBarLayout.Top)
        assertTrue(switch.isVisible)
    }

    @Test
    fun `GIVEN a Sidebar shown THEN the toolbar takes its surface colour, and otherwise keeps Fenix's`() {
        val fenix = lightColorScheme(surface = Color.White)
        var surface = Color.Unspecified
        composeRule.setContent { surface = SidebarToolbarColors.scheme(fenix).surface }
        assertEquals(Color.White, surface)

        SidebarGate.sidebarSurface.value = Color.DarkGray.toArgb()
        composeRule.waitForIdle()

        assertEquals(Color.DarkGray, surface)
    }
}

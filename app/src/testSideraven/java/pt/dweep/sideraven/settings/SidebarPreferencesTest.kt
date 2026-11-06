/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.settings

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import pt.dweep.sideraven.AddressBarLayout
import pt.dweep.sideraven.InMemorySidebarModeStore
import pt.dweep.sideraven.SidebarMode
import pt.dweep.sideraven.SidebarSide
import pt.dweep.sideraven.SidebarTier
import pt.dweep.sideraven.SpacesShown

/** Sideraven's choices: picture pickers for the big ones, segmented buttons and value rows for the rest. */
@RunWith(AndroidJUnit4::class)
class SidebarPreferencesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val group = PreferenceManager(context).createPreferenceScreen(context)

    @Test
    fun `GIVEN a tablet THEN all three modes are offered and the current one is chosen`() {
        val picker = SidebarPreferences.bindMode(context, group, SidebarTier.Tablet, InMemorySidebarModeStore())
        assertEquals(listOf(SidebarMode.Persistent, SidebarMode.Collapsed, SidebarMode.AutoHide), picker.offered)
        assertEquals(SidebarMode.Persistent, picker.selected)
    }

    @Test
    fun `GIVEN a large phone THEN Persistent is not offered and a Persistent choice shows as Collapsed`() {
        val picker = SidebarPreferences.bindMode(context, group, SidebarTier.LargePhone, InMemorySidebarModeStore(SidebarMode.Persistent))
        assertEquals(listOf(SidebarMode.Collapsed, SidebarMode.AutoHide), picker.offered)
        assertEquals(SidebarMode.Collapsed, picker.selected)
    }

    @Test
    fun `WHEN a mode is picked THEN it becomes the stored choice, as with the Toggle`() {
        val store = InMemorySidebarModeStore()
        SidebarPreferences.bindMode(context, group, SidebarTier.Tablet, store).choose(SidebarMode.AutoHide)
        assertEquals(SidebarMode.AutoHide, store.chosenMode.value)
    }

    @Test
    fun `WHEN the address bar is moved to the Sidebar THEN the layout store follows`() {
        val store = InMemorySidebarModeStore()
        SidebarPreferences.bindLayout(context, group, store).choose(AddressBarLayout.Sidebar)
        assertEquals(AddressBarLayout.Sidebar, store.addressBarLayout.value)
    }

    @Test
    fun `WHEN Spaces are shown as the icon row THEN the store follows, and the Header was the default`() {
        val store = InMemorySidebarModeStore()
        assertEquals(SpacesShown.Header, store.spacesShown.value)

        SidebarPreferences.bindSpacesShown(context, group, store).choose(SpacesShown.IconRow)

        assertEquals(SpacesShown.IconRow, store.spacesShown.value)
    }

    @Test
    fun `WHEN the screen is shown again THEN each row shows the stored value without writing it`() {        val store = InMemorySidebarModeStore()
        val side = SidebarPreferences.bindSide(context, group, store)
        store.sidebarSide.set(SidebarSide.Right)

        // Filled again (onResume): the same row, now showing what's stored.
        val again = SidebarPreferences.bindSide(context, group, store)
        assertEquals(side, again)
        assertEquals(SidebarSide.Right, again.selected)
        assertEquals(1, group.preferenceCount)
    }
}

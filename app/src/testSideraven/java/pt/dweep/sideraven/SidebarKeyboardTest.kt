/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import android.view.KeyEvent
import kotlinx.coroutines.flow.MutableStateFlow
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.selector.findTab
import mozilla.components.browser.state.state.createTab
import mozilla.components.browser.state.store.BrowserStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** Keyboard shortcuts. */
class SidebarKeyboardTest {
    private fun key(
        keyCode: Int,
        ctrl: Boolean = true,
        shift: Boolean = false,
        essentials: Int = 3,
    ) = SidebarKeyboard.intentFor(keyCode, ctrl = ctrl, shift = shift, alt = false, meta = false, essentialsShown = essentials)

    @Test
    fun `THEN Ctrl+Shift+S cycles Sidebar Modes`() {
        assertEquals(SidebarIntent.KeyboardToggleMode, key(KeyEvent.KEYCODE_S, shift = true))
    }

    @Test
    fun `THEN Ctrl+L opens the current tab URL editor like tapping the address bar`() {
        assertEquals(SidebarIntent.OpenSearch(newTab = false), key(KeyEvent.KEYCODE_L))
        assertEquals(SidebarIntent.OpenSearch(newTab = false), key(KeyEvent.KEYCODE_L, essentials = 0))
        assertNull(key(KeyEvent.KEYCODE_L, ctrl = false))
        assertNull(key(KeyEvent.KEYCODE_L, shift = true))
        assertNull(key(KeyEvent.KEYCODE_L, essentials = -1))
        assertNull(SidebarKeyboard.intentFor(KeyEvent.KEYCODE_L, ctrl = true, shift = false, alt = true, meta = false, essentialsShown = 0))
        assertNull(SidebarKeyboard.intentFor(KeyEvent.KEYCODE_L, ctrl = true, shift = false, alt = false, meta = true, essentialsShown = 0))
    }

    @Test
    fun `THEN Ctrl+1 to Ctrl+9 select the matching Essential only when it exists`() {
        assertEquals(SidebarIntent.SelectEssentialAt(0), key(KeyEvent.KEYCODE_1))
        assertEquals(SidebarIntent.SelectEssentialAt(2), key(KeyEvent.KEYCODE_3))
        assertNull(key(KeyEvent.KEYCODE_4))
        assertNull(key(KeyEvent.KEYCODE_9, essentials = 0))
    }

    @Test
    fun `THEN other keys, and every key while no Sidebar is shown, are left to Fenix`() {
        assertNull(key(KeyEvent.KEYCODE_S))
        // Ctrl+T is New Tab; Ctrl+R stays with Fenix.
        assertNull(key(KeyEvent.KEYCODE_R))
        assertNull(key(KeyEvent.KEYCODE_1, ctrl = false))
        assertNull(key(KeyEvent.KEYCODE_S, shift = true, essentials = -1))
    }

    @Test
    fun `WHEN Ctrl+number is handled THEN the Essential is selected`() {
        val store =
            BrowserStore().apply {
                dispatch(TabListAction.AddTabAction(createTab("https://a.example", id = "a"), select = true))
                dispatch(TabListAction.AddTabAction(createTab("https://b.example", id = "b")))
            }
        val essentials = Essentials(InMemoryEssentialsRepository(), MutableStateFlow(emptyMap()))
        essentials.pin(store.state.findTab("b")!!)
        val handler = SidebarIntentHandler(selectTab = { store.dispatch(TabListAction.SelectTabAction(it)) }, openNewTab = {})
        val state =
            store.state.toSidebarUiState(
                isPrivate = false,
                tier = SidebarTier.Tablet,
                screen = SidebarScreen.Browser,
                essentials = essentials.essentials.value,
                essentialLinks = essentials.linkedTabs.value,
            )

        handler.handle(SidebarIntent.SelectEssentialAt(0), state)

        assertEquals("b", store.state.selectedTabId)
    }

    @Test
    fun `WHEN the mode changes from the keyboard THEN it doesn't animate`() {
        val modeStore = InMemorySidebarModeStore()
        var animate = true
        val handler =
            SidebarIntentHandler(
                selectTab = {},
                openNewTab = {},
                setChosenMode = modeStore.chosenMode::set,
                setAnimateModeChanges = { animate = it },
            )
        val state = BrowserStore().state.toSidebarUiState(isPrivate = false, tier = SidebarTier.Tablet, screen = SidebarScreen.Home)

        handler.handle(SidebarIntent.KeyboardToggleMode, state)

        assertFalse(animate)
        assertEquals(SidebarMode.Collapsed, modeStore.chosenMode.value)
    }
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import android.view.KeyEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.browser.state.state.createTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserKeyboardTest {
    private fun key(code: Int, ctrl: Boolean = false, shift: Boolean = false, alt: Boolean = false, meta: Boolean = false) =
        BrowserKeyboard.commandFor(code, ctrl, shift, alt, meta)

    @Test
    fun `Ctrl Tab cycles forward and Shift reverses it`() {
        assertEquals(BrowserShortcut.NextTab, key(KeyEvent.KEYCODE_TAB, ctrl = true))
        assertEquals(BrowserShortcut.PreviousTab, key(KeyEvent.KEYCODE_TAB, ctrl = true, shift = true))
    }

    @Test
    fun `Alt arrows navigate history while text selection and Space shortcuts remain untouched`() {
        assertEquals(BrowserShortcut.Back, key(KeyEvent.KEYCODE_DPAD_LEFT, alt = true))
        assertEquals(BrowserShortcut.Forward, key(KeyEvent.KEYCODE_DPAD_RIGHT, alt = true))
        assertNull(key(KeyEvent.KEYCODE_DPAD_LEFT, ctrl = true, alt = true))
        assertNull(key(KeyEvent.KEYCODE_DPAD_LEFT, shift = true, alt = true))
        assertNull(key(KeyEvent.KEYCODE_TAB))
        assertNull(key(KeyEvent.KEYCODE_TAB, ctrl = true, alt = true))
        assertNull(key(KeyEvent.KEYCODE_TAB, ctrl = true, meta = true))
    }

    @Test
    fun `tab cycle follows display order and keeps browsing modes separate`() {
        val state = BrowserState(tabs = listOf(createTab("https://a.example", id = "a"), createTab("https://private.example", id = "p", private = true), createTab("https://b.example", id = "b")))
        assertEquals(listOf("b", "a"), BrowserKeyboard.tabIdsFor(state, isPrivate = false))
        assertEquals(listOf("p"), BrowserKeyboard.tabIdsFor(state, isPrivate = true))
    }

    @Test
    fun `current Space and global Essentials are eligible but other Space pins are not`() {
        val spaces = SpacesData(
            spaces = listOf(Space("work", "Work", "💼"), Space("home", "Home", "🏠")),
            currentSpaceId = "work",
            assignments = mapOf("a" to "work", "b" to "home", "essential" to "home", "foreign-pin-tab" to "work"),
            pinned = listOf(PinnedTab("pin", "home", "https://pin.example", "Pin")),
        )
        val state = BrowserState(tabs = listOf("a", "b", "essential", "foreign-pin-tab").map { createTab("https://$it.example", id = it) })
        assertEquals(listOf("essential", "a"), BrowserKeyboard.tabIdsFor(state, false, spaces, setOf("essential"), mapOf("pin" to "foreign-pin-tab")))
    }

    @Test
    fun `cycling wraps in either direction and never creates a tab`() {
        val selected = mutableListOf<String>()
        var shown = 0
        val actions = BrowserKeyboardActions(selectTab = { selected += it }, showBrowser = { shown++ })
        val state = BrowserState(tabs = listOf(createTab("https://a.example", id = "a"), createTab("https://b.example", id = "b")), selectedTabId = "a")
        BrowserKeyboard.perform(BrowserShortcut.NextTab, state, false, listOf("b", "a"), actions)
        BrowserKeyboard.perform(BrowserShortcut.PreviousTab, state.copy(selectedTabId = "b"), false, listOf("b", "a"), actions)
        assertEquals(listOf("b", "a"), selected)
        assertEquals(2, shown)
    }

    @Test
    fun `no selected tab chooses the directional end and an empty list is harmless`() {
        val selected = mutableListOf<String>()
        val actions = BrowserKeyboardActions(selectTab = { selected += it })
        val state = BrowserState(tabs = listOf(createTab("https://a.example", id = "a"), createTab("https://b.example", id = "b")))
        BrowserKeyboard.perform(BrowserShortcut.NextTab, state, false, listOf("b", "a"), actions)
        BrowserKeyboard.perform(BrowserShortcut.PreviousTab, state, false, listOf("b", "a"), actions)
        BrowserKeyboard.perform(BrowserShortcut.NextTab, BrowserState(), false, emptyList(), actions)
        assertEquals(listOf("b", "a"), selected)
    }

    @Test
    fun `a single tab stays selected without duplicate selection`() {
        val selected = mutableListOf<String>()
        var shown = 0
        val state = BrowserState(tabs = listOf(createTab("https://a.example", id = "a")), selectedTabId = "a")
        BrowserKeyboard.perform(BrowserShortcut.NextTab, state, false, listOf("a"), BrowserKeyboardActions(selectTab = { selected += it }, showBrowser = { shown++ }))
        assertEquals(emptyList<String>(), selected)
        assertEquals(1, shown)
    }

    @Test
    fun `history keys use the selected tabs available history only`() {
        val back = mutableListOf<String>()
        val forward = mutableListOf<String>()
        val tab = createTab("https://a.example", id = "a").let { it.copy(content = it.content.copy(canGoBack = true, canGoForward = true)) }
        val state = BrowserState(tabs = listOf(tab), selectedTabId = "a")
        val actions = BrowserKeyboardActions(goBack = { back += it }, goForward = { forward += it })
        BrowserKeyboard.perform(BrowserShortcut.Back, state, false, emptyList(), actions)
        BrowserKeyboard.perform(BrowserShortcut.Forward, state, false, emptyList(), actions)
        BrowserKeyboard.perform(BrowserShortcut.Back, state, true, emptyList(), actions)
        BrowserKeyboard.perform(BrowserShortcut.Forward, state.copy(tabs = listOf(tab.copy(content = tab.content.copy(canGoForward = false)))), false, emptyList(), actions)
        assertEquals(listOf("a"), back)
        assertEquals(listOf("a"), forward)
    }
}

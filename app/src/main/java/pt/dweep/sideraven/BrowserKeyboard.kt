/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import android.view.KeyEvent
import mozilla.components.browser.state.state.BrowserState
import org.mozilla.fenix.HomeActivity
import org.mozilla.fenix.R
import org.mozilla.fenix.ext.components
import org.mozilla.fenix.ext.openToBrowser

enum class BrowserShortcut { NextTab, PreviousTab, Back, Forward }

/** Browser operations kept separate from key decoding and tab eligibility. */
data class BrowserKeyboardActions(
    val selectTab: (String) -> Unit = {},
    val goBack: (String) -> Unit = {},
    val goForward: (String) -> Unit = {},
    val showBrowser: () -> Unit = {},
)

/** Browser-level shortcuts work on phones as well as with the Sidebar shown. */
object BrowserKeyboard {
    fun commandFor(keyCode: Int, ctrl: Boolean, shift: Boolean, alt: Boolean, meta: Boolean): BrowserShortcut? =
        when {
            meta -> null
            ctrl && !alt && keyCode == KeyEvent.KEYCODE_TAB -> if (shift) BrowserShortcut.PreviousTab else BrowserShortcut.NextTab
            alt && !ctrl && !shift && keyCode == KeyEvent.KEYCODE_DPAD_LEFT -> BrowserShortcut.Back
            alt && !ctrl && !shift && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT -> BrowserShortcut.Forward
            else -> null
        }

    /** Stable Tab List order, including open Essentials and pins, without crossing Spaces or privacy modes. */
    fun tabIdsFor(
        state: BrowserState,
        isPrivate: Boolean,
        spaces: SpacesData? = null,
        essentialTabIds: Set<String> = emptySet(),
        pinnedLinks: Map<String, String> = emptyMap(),
    ): List<String> {
        val pinSpaces = spaces?.pinned.orEmpty().associate { it.id to it.spaceId }
        val pinnedTabSpaces = pinnedLinks.mapNotNull { (pin, tab) -> pinSpaces[pin]?.let { tab to it } }.toMap()
        return state.tabs.asReversed().filter { tab ->
            tab.content.private == isPrivate &&
                (isPrivate || spaces == null || tab.id in essentialTabIds ||
                    (pinnedTabSpaces[tab.id] ?: spaces.spaceOf(tab.id)) == spaces.current.id)
        }.map { it.id }
    }

    fun perform(command: BrowserShortcut, state: BrowserState, isPrivate: Boolean, tabIds: List<String>, actions: BrowserKeyboardActions) {
        when (command) {
            BrowserShortcut.NextTab, BrowserShortcut.PreviousTab -> {
                if (tabIds.isEmpty()) return
                val current = tabIds.indexOf(state.selectedTabId)
                val next = if (current == -1) {
                    if (command == BrowserShortcut.NextTab) 0 else tabIds.lastIndex
                } else {
                    Math.floorMod(current + if (command == BrowserShortcut.NextTab) 1 else -1, tabIds.size)
                }
                val target = tabIds[next]
                if (target != state.selectedTabId) actions.selectTab(target)
                actions.showBrowser()
            }
            BrowserShortcut.Back, BrowserShortcut.Forward -> {
                val tab = state.tabs.firstOrNull { it.id == state.selectedTabId && it.content.private == isPrivate } ?: return
                if (command == BrowserShortcut.Back && tab.content.canGoBack) actions.goBack(tab.id)
                if (command == BrowserShortcut.Forward && tab.content.canGoForward) actions.goForward(tab.id)
            }
        }
    }

    /** Run before web content sees the event; leave Settings, dialogs and Custom Tabs alone. */
    fun handle(activity: HomeActivity, event: KeyEvent): Boolean {
        val command = commandFor(event.keyCode, event.isCtrlPressed, event.isShiftPressed, event.isAltPressed, event.isMetaPressed)
            ?: return false
        if (!SidebarGate.hostsSidebar(activity)) return false
        val nav = sidebarNavController(activity) ?: return false
        val destination = nav.currentDestination?.id
        if (destination != R.id.browserFragment && destination != R.id.homeFragment) return false
        // Consume releases and held-key repeats, so they cannot also move focus or switch twice.
        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount > 0) return true
        if (destination == R.id.homeFragment && (command == BrowserShortcut.Back || command == BrowserShortcut.Forward)) return true
        val components = activity.components
        val state = components.core.store.state
        // The current Space still applies when a narrow window hides the Sidebar.
        val spaces = Spaces.get(activity)
        val ids = tabIdsFor(state, activity.browsingModeManager.mode.isPrivate, spaces.data, Essentials.processLinks.value.values.toSet(), spaces.pinnedLinks.value)
        perform(
            command, state, activity.browsingModeManager.mode.isPrivate, ids,
            BrowserKeyboardActions(
                selectTab = { components.useCases.tabsUseCases.selectTab(it) },
                goBack = { components.useCases.sessionUseCases.goBack(it) },
                goForward = { components.useCases.sessionUseCases.goForward(it) },
                showBrowser = { nav.openToBrowser() },
            ),
        )
        return true
    }
}

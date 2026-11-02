/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import androidx.test.ext.junit.runners.AndroidJUnit4
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.state.createTab
import mozilla.components.browser.state.store.BrowserStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import pt.dweep.sideraven.sync.DesktopTab

/** Desktop Tabs in a synced Space's Tab List. */
@RunWith(AndroidJUnit4::class)
class DesktopTabsTest {
    private val spaces = Spaces(InMemorySpacesRepository())
    private val store = BrowserStore(middleware = listOf(SpacesMiddleware { spaces }))
    private val selected = mutableListOf<String>()
    private val opened = mutableListOf<String>()
    private val expanded = mutableListOf<Boolean>()
    private val handler =
        SidebarIntentHandler(
            selectTab = { selected += it },
            openNewTab = {},
            openUrl = { opened += it },
            setDesktopTabsExpanded = { expanded += it },
        )

    private val desktopTabs = { space: String -> mapOf(space to listOf(DesktopTab("https://a.example", "A"), DesktopTab("https://b.example", "B"))) }

    private fun state(
        isPrivate: Boolean = false,
        tabs: Map<String, List<DesktopTab>> = desktopTabs(spaces.data.current.id),
        expandedNow: Boolean = false,
    ) = store.state.toSidebarUiState(
        isPrivate = isPrivate,
        tier = SidebarTier.Tablet,
        screen = SidebarScreen.Browser,
        spaces = spaces.data,
        desktopTabs = tabs,
        desktopTabsExpanded = expandedNow,
    )

    @Test
    fun `GIVEN desktop tabs for the current synced Space THEN the Tab List ends with them, collapsed`() {
        val group = state().desktopTabs!!

        assertEquals(listOf("A", "B"), group.tabs.map { it.title })
        assertEquals(false, group.expanded)
        assertEquals(true, state(expandedNow = true).desktopTabs!!.expanded)
    }

    @Test
    fun `GIVEN private browsing, a Local Space or no desktop tabs THEN there is no group`() {
        assertNull(state(isPrivate = true).desktopTabs)
        assertNull(state(tabs = emptyMap()).desktopTabs)
        spaces.setLocal(spaces.data.current.id, true)
        assertNull(state().desktopTabs)
    }

    @Test
    fun `WHEN a desktop tab is opened THEN an open tab of this Space with that address is selected, else it opens`() {
        store.dispatch(TabListAction.AddTabAction(createTab("https://b.example", id = "here"), select = false))

        handler.handle(SidebarIntent.OpenDesktopTab("https://b.example"), state())
        handler.handle(SidebarIntent.OpenDesktopTab("https://a.example"), state())

        assertEquals(listOf("here"), selected)
        assertEquals(listOf("https://a.example"), opened)
    }

    @Test
    fun `WHEN the group is toggled THEN its expanded state is remembered`() {
        handler.handle(SidebarIntent.ToggleDesktopTabs, state())

        assertEquals(listOf(true), expanded)
    }

    @Test
    fun `sending a phone tab passes a copy without closing or navigating it`() {
        val sent = mutableListOf<List<String>>()
        val closed = mutableListOf<String>()
        val sender = SidebarIntentHandler(
            selectTab = { selected += it }, openNewTab = {}, closeTab = { closed += it },
            sendToDesktop = { space, url, title -> sent += listOf(space, url, title) },
        )
        store.dispatch(TabListAction.AddTabAction(createTab("https://phone.example", id = "phone")))
        val ui = state().copy(zenSyncOn = true, desktopSendingOn = true)

        sender.handle(SidebarIntent.SendToDesktop("phone"), ui)

        assertEquals(listOf(listOf(spaces.data.current.id, "https://phone.example", "https://phone.example")), sent)
        assertEquals(listOf("phone"), store.state.tabs.map { it.id })
        assertEquals(emptyList<String>(), closed)
        assertEquals(emptyList<String>(), selected)
    }

    @Test
    fun `private and Local Space tabs cannot be sent even through a stale intent`() {
        val sent = mutableListOf<String>()
        val sender = SidebarIntentHandler(
            selectTab = {}, openNewTab = {}, sendToDesktop = { _, url, _ -> sent += url },
        )
        store.dispatch(TabListAction.AddTabAction(createTab("https://phone.example", id = "phone")))
        val ui = state().copy(zenSyncOn = true, desktopSendingOn = true)
        sender.handle(SidebarIntent.SendToDesktop("phone"), ui.copy(isPrivate = true))
        sender.handle(SidebarIntent.SendToDesktop("phone"), ui.copy(desktopSendingOn = false))
        spaces.setLocal(spaces.data.current.id, true)
        sender.handle(SidebarIntent.SendToDesktop("phone"), state().copy(zenSyncOn = true, desktopSendingOn = true))
        assertEquals(emptyList<String>(), sent)
    }
}

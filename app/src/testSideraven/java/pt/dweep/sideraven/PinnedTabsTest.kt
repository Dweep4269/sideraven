/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.MutableStateFlow
import mozilla.components.browser.state.action.ContentAction
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.state.createTab
import mozilla.components.browser.state.store.BrowserStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Pinned Tabs — pin to the current Space, show, select, reset, unpin. */
@RunWith(AndroidJUnit4::class)
class PinnedTabsTest {
    private val repository = InMemorySpacesRepository()
    private val spaces = Spaces(repository, pinLinks = MutableStateFlow(emptyMap()))
    private val store = BrowserStore(middleware = listOf(NewTabPositionMiddleware(), SpacesMiddleware { spaces }))
    private val refused = mutableListOf<PinResult>()
    private val loaded = mutableListOf<Pair<String, String>>()
    private var nextId = 0

    private val handler =
        SidebarIntentHandler(
            selectTab = { store.dispatch(TabListAction.SelectTabAction(it)) },
            openNewTab = {},
            loadUrlInTab = { url, tabId -> loaded += url to tabId },
            pinToSpace = { id -> store.state.tabs.firstOrNull { it.id == id }?.let { spaces.pinToSpace(it) } ?: PinResult.PrivateRefused },
            onSpacePinRefused = { refused += it },
            openPinnedTab = { pinId, url ->
                val id = "opened${nextId++}"
                store.dispatch(TabListAction.AddTabAction(createTab(url, id = id), select = true))
                spaces.linkPin(pinId, id)
            },
            unpinPinnedTab = spaces::unpinFromSpace,
        )

    private fun add(
        id: String,
        private: Boolean = false,
    ) = store.dispatch(TabListAction.AddTabAction(createTab("https://$id.example", title = id.uppercase(), id = id, private = private)))

    private fun state(isPrivate: Boolean = false) =
        store.state.toSidebarUiState(
            isPrivate = isPrivate,
            tier = SidebarTier.Tablet,
            screen = SidebarScreen.Browser,
            spaces = spaces.data,
            pinnedLinks = spaces.pinnedLinks.value,
        )

    @Test
    fun `WHEN a tab is pinned to the Space THEN it moves from the Tab List to the pinned block`() {
        add("mail")
        add("news")

        handler.handle(SidebarIntent.PinToSpace("mail"), state())

        assertEquals(listOf("MAIL"), state().pinned.map { it.title })
        assertEquals("mail", state().pinned.single().linkedTabId)
        assertEquals(listOf("news"), state().tabs.map { it.id })
    }

    @Test
    fun `THEN a Pinned Tab belongs to its Space only`() {
        add("mail")
        handler.handle(SidebarIntent.PinToSpace("mail"), state())

        spaces.create("Work", "💼")

        assertTrue(state().pinned.isEmpty())
        assertEquals(1, state().spaces.first().pinnedCount)
    }

    @Test
    fun `WHEN its tab navigates away THEN Reset sends it back to the pinned URL`() {
        add("mail")
        handler.handle(SidebarIntent.PinToSpace("mail"), state())
        store.dispatch(ContentAction.UpdateUrlAction("mail", "https://mail.example/inbox/42"))

        val pin = state().pinned.single()
        assertEquals("mail", pin.linkedTabId)
        handler.handle(SidebarIntent.ResetPinnedTab(pin.id), state())

        assertEquals(listOf("https://mail.example" to "mail"), loaded)
    }

    @Test
    fun `WHEN a pinned tab is unpinned THEN it returns to the Tab List`() {
        add("mail")
        handler.handle(SidebarIntent.PinToSpace("mail"), state())

        handler.handle(SidebarIntent.UnpinPinnedTab(state().pinned.single().id), state())

        assertTrue(state().pinned.isEmpty())
        assertEquals(listOf("mail"), state().tabs.map { it.id })
    }

    @Test
    fun `GIVEN its tab was closed WHEN the Ghost is tapped THEN a tab opens at the pinned URL and links to it`() {
        add("mail")
        handler.handle(SidebarIntent.PinToSpace("mail"), state())
        store.dispatch(TabListAction.RemoveTabAction("mail"))
        val ghost = state().pinned.single()
        assertTrue(ghost.isGhost)

        handler.handle(SidebarIntent.SelectPinnedTab(ghost.id), state())

        val pin = state().pinned.single()
        assertEquals("opened0", pin.linkedTabId)
        assertEquals("opened0", store.state.selectedTabId)
        assertTrue(state().tabs.none { it.id == "opened0" })
    }

    @Test
    fun `GIVEN a Space with 12 Pinned Tabs THEN a 13th can still be pinned`() {
        (1..12).forEach { add("t$it") }
        (1..12).forEach { handler.handle(SidebarIntent.PinToSpace("t$it"), state()) }
        add("extra")

        assertEquals(null, state().spacePinBlock)
        handler.handle(SidebarIntent.PinToSpace("extra"), state())

        assertEquals(emptyList<PinResult>(), refused)
        assertEquals(13, state().pinned.size)
    }

    @Test
    fun `THEN private tabs are never pinned`() {
        add("secret", private = true)

        handler.handle(SidebarIntent.PinToSpace("secret"), state(isPrivate = true))

        assertEquals(listOf(PinResult.PrivateRefused), refused)
        assertEquals(PinResult.PrivateRefused, state(isPrivate = true).spacePinBlock)
        assertTrue(state(isPrivate = true).pinned.isEmpty())
    }

    @Test
    fun `GIVEN a restart WHEN tabs are restored THEN Pinned Tabs re-link by exact URL in their Space`() {
        add("mail")
        handler.handle(SidebarIntent.PinToSpace("mail"), state())
        // A new process: links are gone, the stored pins remain.
        val restarted = Spaces(repository, pinLinks = MutableStateFlow(emptyMap()))
        assertNull(restarted.pinnedLinks.value.values.firstOrNull())

        restarted.relinkPinned(store.state.tabs)

        assertEquals(listOf("mail"), restarted.pinnedLinks.value.values.toList())
    }

    @Test
    fun `WHEN a Space with Pinned Tabs is deleted THEN its pins go too, and Undo brings them back`() {
        add("h")
        spaces.create("Work", "💼")
        add("mail")
        handler.handle(SidebarIntent.PinToSpace("mail"), state())
        val work = spaces.data.current.id

        val snapshot = spaces.delete(work)!!
        assertTrue(spaces.data.pinned.isEmpty())

        spaces.restore(snapshot)
        assertEquals(1, spaces.data.pinnedIn(work).size)
    }

    @Test
    fun `THEN Pinned Tabs survive storage with their custom name and extra data`() {
        add("mail")
        spaces.pinToSpace(store.state.tabs.single())
        val data = spaces.data.copy(pinned = spaces.data.pinned.map { it.copy(customName = "Inbox", extra = """{"zen":1}""") })

        assertEquals(data, SpacesCodec.decode(SpacesCodec.encode(data)))
        assertEquals("Inbox", data.pinned.single().displayName)
    }
}

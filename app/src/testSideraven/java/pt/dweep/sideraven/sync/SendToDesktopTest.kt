/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import pt.dweep.sideraven.Essentials
import pt.dweep.sideraven.InMemoryEssentialsRepository
import pt.dweep.sideraven.InMemorySpacesRepository
import pt.dweep.sideraven.Spaces

@RunWith(AndroidJUnit4::class)
class SendToDesktopTest {
    private val server = FakeSyncServer()
    private val spaces = Spaces(InMemorySpacesRepository(), pinLinks = MutableStateFlow(emptyMap()))
    private val desktop = InMemoryDesktopViewStore()
    private val queue = InMemoryDesktopSendQueue()
    private val settings = InMemoryZenSyncSettingsStore()
    private var interruptParent = false
    private var conflictParent = false
    private var interruptConfirmation = false
    private var parentWritten = false
    private val transport = SyncHttpTransport { request ->
        if (interruptConfirmation && parentWritten && request.method == "GET" && request.url.path.endsWith("/spaces")) {
            throw java.io.IOException("offline before confirmation snapshot")
        }
        if (interruptParent && request.method == "PUT" && request.url.path.endsWith("/work")) {
            throw java.io.IOException("offline after tab upload")
        }
        if (conflictParent && request.method == "PUT" && request.url.path.endsWith("/work")) {
            conflictParent = false
            server.put(ZenRecords.space("work", "Desktop renamed", children = listOf("existing", "concurrent")))
            server.put(ZenRecords.normal("concurrent", "https://concurrent.example", "work"))
        }
        server.execute(request).also {
            if (request.method == "PUT" && request.url.path.endsWith("/work")) parentWritten = true
        }
    }
    private val sync = ZenSync(
        { server.credentials }, transport, spaces,
        Essentials(InMemoryEssentialsRepository(), MutableStateFlow(emptyMap())),
        InMemorySyncBaseRepository(), settings, desktop = desktop, sends = queue,
    )

    private fun setup(enabled: Boolean? = true) = runBlocking {
        server.normalTabsPref = enabled
        server.put(ZenRecords.space("work", "Work", children = listOf("existing")))
        server.put(ZenRecords.normal("existing", "https://existing.example", "work"))
        server.put(ZenRecords.layout(listOf("work")))
        sync.firstSync(FirstSyncChoice.Replace)
    }

    @Test
    fun `sending uploads a normal tab and preserves desktop children and container`() = runBlocking {
        setup()
        val space = server.cleartext("work")!!
        space.getJSONObject("data").put("containerGuid", "container-1").put("unknown", "preserve")
        server.put(space)
        val id = sync.queueDesktopTab("work", "https://phone.example", "Phone")

        sync.sync()

        val tab = server.cleartext(id)!!.getJSONObject("data")
        assertFalse(tab.getBoolean("pinned"))
        assertFalse(tab.getBoolean("essential"))
        assertEquals("https://phone.example", tab.getString("url"))
        assertEquals("Phone", tab.getString("title"))
        assertEquals("work", tab.getString("workspaceUuid"))
        assertEquals("container-1", tab.getString("containerGuid"))
        val data = server.cleartext("work")!!.getJSONObject("data")
        assertEquals(listOf(id, "existing"), (0 until data.getJSONArray("children").length()).map { data.getJSONArray("children").getString(it) })
        assertEquals("preserve", data.getString("unknown"))
        assertTrue(queue.pending.isEmpty())
        assertEquals(listOf("https://phone.example", "https://existing.example"), desktop.view.value.tabs["work"]!!.map { it.url })
    }

    @Test
    fun `partial upload retries the same id and retains concurrent desktop changes`() = runBlocking {
        setup()
        val id = sync.queueDesktopTab("work", "https://phone.example", "Phone")
        interruptParent = true
        try { sync.sync(); fail("expected network failure") } catch (_: java.io.IOException) { }
        assertEquals(listOf(id), queue.pending.map { it.id })
        assertNotNull(server.cleartext(id))
        server.put(ZenRecords.space("work", "Office", children = listOf("existing", "new-desktop")))
        server.put(ZenRecords.normal("new-desktop", "https://new.example", "work"))
        interruptParent = false

        sync.sync()

        val data = server.cleartext("work")!!.getJSONObject("data")
        assertEquals("Office", data.getString("name"))
        assertEquals("[\"$id\",\"existing\",\"new-desktop\"]", data.getJSONArray("children").toString())
        assertTrue(queue.pending.isEmpty())
        assertEquals(1, server.ids().count { it == id })
    }

    @Test
    fun `a conditional parent conflict rereads desktop changes before appending`() = runBlocking {
        setup()
        val id = sync.queueDesktopTab("work", "https://phone.example", "Phone")
        conflictParent = true
        sync.sync()
        val data = server.cleartext("work")!!.getJSONObject("data")
        assertEquals("Desktop renamed", data.getString("name"))
        assertEquals("[\"$id\",\"existing\",\"concurrent\"]", data.getJSONArray("children").toString())
        assertTrue(queue.pending.isEmpty())
    }

    @Test
    fun `a newer desktop engine never receives queued uploads`() = runBlocking {
        setup()
        val id = sync.queueDesktopTab("work", "https://phone.example", "Phone")
        server.engineVersion = 4
        assertEquals(SyncOutcome.ReadOnly, sync.sync())
        assertNull(server.cleartext(id))
        assertTrue(sync.isDesktopSendPending(id))
    }

    @Test
    fun `instant send uploads the copy before unrelated local Space changes`() = runBlocking {
        setup()
        spaces.edit("work", "Local rename", "💼", "ember")
        val id = sync.queueDesktopTab("work", "https://phone.example", "Phone")
        sync.sendDesktopTabNow(id)
        assertEquals(id, java.net.URLDecoder.decode(server.writes.first().url.path.substringAfterLast('/'), "UTF-8"))
        assertEquals("Work", server.cleartext("work")!!.getJSONObject("data").getString("name"))
        assertTrue(queue.pending.isEmpty())
        sync.sync()
        assertEquals("Local rename", server.cleartext("work")!!.getJSONObject("data").getString("name"))
    }

    @Test
    fun `copy stays queued until the confirmation snapshot succeeds`() = runBlocking {
        setup()
        val id = sync.queueDesktopTab("work", "https://phone.example", "Phone")
        interruptConfirmation = true
        try { sync.sendDesktopTabNow(id); fail("expected confirmation read failure") } catch (_: java.io.IOException) { }
        assertNotNull(server.cleartext(id))
        assertTrue(sync.isDesktopSendPending(id))
        interruptConfirmation = false
        sync.sendDesktopTabNow(id)
        assertFalse(sync.isDesktopSendPending(id))
        assertEquals(id, desktop.view.value.tabs["work"]!!.first().id)
    }

    @Test
    fun `disabled or absent desktop capability keeps the send queued without uploading it`() = runBlocking {
        for (pref in listOf(false, null)) {
            setup(pref)
            val id = sync.queueDesktopTab("work", "https://phone.example", "Phone")
            try { sync.sync(); fail("expected capability refusal") } catch (_: SyncError) { }
            assertNull(server.cleartext(id))
            assertTrue(queue.pending.any { it.id == id })
        }
    }

    @Test
    fun `closing a sent tab on desktop is never undone by ordinary sync`() = runBlocking {
        setup()
        val id = sync.queueDesktopTab("work", "https://phone.example", "Phone")
        sync.sync()
        server.delete(id)
        server.put(ZenRecords.space("work", "Work", children = listOf("existing")))

        sync.sync()
        sync.sync()

        assertTrue(ZenWire.isTombstone(server.cleartext(id)!!))
        assertEquals(listOf("https://existing.example"), desktop.view.value.tabs["work"]!!.map { it.url })
    }

    @Test
    fun `a local Space edit after sending preserves the new desktop child`() = runBlocking {
        setup()
        val id = sync.queueDesktopTab("work", "https://phone.example", "Phone")
        sync.sync()
        spaces.edit("work", "Renamed here", "💼", "ember")
        sync.sync()
        val data = server.cleartext("work")!!.getJSONObject("data")
        assertEquals("Renamed here", data.getString("name"))
        assertEquals("[\"$id\",\"existing\"]", data.getJSONArray("children").toString())
    }

    @Test
    fun `an unavailable queued destination does not block a valid send`() = runBlocking {
        setup()
        queue.save(listOf(DesktopSend("missing", desktopSendOwner(server.credentials), "deleted-space", "https://old.example", "Old")))
        val id = sync.queueDesktopTab("work", "https://phone.example", "Phone")
        sync.sync()
        assertNotNull(server.cleartext(id))
        assertEquals(listOf("missing"), queue.pending.map { it.id })
        assertFalse(sync.isDesktopSendPending(id))
    }

    @Test
    fun `local spaces and non web addresses cannot be queued`() = runBlocking {
        setup()
        for (url in listOf("about:privatebrowsing", "file:///local", "javascript:alert(1)")) {
            try { sync.queueDesktopTab("work", url, "Private"); fail("expected URL refusal") } catch (_: SyncError) { }
        }
        spaces.setLocal("work", true)
        try { sync.queueDesktopTab("work", "https://phone.example", "Phone"); fail("expected Local Space refusal") } catch (_: SyncError) { }
        assertTrue(queue.pending.isEmpty())
    }

    @Test
    fun `a queued send is not uploaded into a different account`() = runBlocking {
        setup()
        queue.save(listOf(DesktopSend("other-account", "someone-else", "work", "https://secret.example", "Secret")))
        sync.sync()
        assertNull(server.cleartext("other-account"))
        assertEquals(listOf("other-account"), queue.pending.map { it.id })
    }

    @Test
    fun `capability can be enabled later and an empty desktop Space can receive a tab`() = runBlocking {
        setup(false)
        val id = sync.queueDesktopTab("work", "https://phone.example", "Phone")
        try { sync.sync(); fail("expected capability refusal") } catch (_: SyncError) { }
        server.normalTabsPref = true
        server.delete("existing")
        server.put(ZenRecords.space("work", "Work"))
        sync.sync()
        assertEquals("[\"$id\"]", server.cleartext("work")!!.getJSONObject("data").getJSONArray("children").toString())
        assertTrue(queue.pending.isEmpty())
    }

    @Test
    fun `a desktop tombstone wins over an interrupted send`() = runBlocking {
        setup()
        val id = sync.queueDesktopTab("work", "https://phone.example", "Phone")
        interruptParent = true
        try { sync.sync(); fail("expected network failure") } catch (_: java.io.IOException) { }
        server.delete(id)
        interruptParent = false
        sync.sync()
        assertTrue(ZenWire.isTombstone(server.cleartext(id)!!))
        assertTrue(queue.pending.isEmpty())
        assertEquals("[\"existing\"]", server.cleartext("work")!!.getJSONObject("data").getJSONArray("children").toString())
    }

    @Test
    fun `pending copies persist with the same identity and repeated queued taps do not duplicate`() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val persistent = PreferencesDesktopSendQueue.get(context)
        persistent.save(emptyList())
        try {
            val id = persistent.enqueue("account", "work", "https://phone.example", "Phone")
            val again = persistent.enqueue("account", "work", "https://phone.example", "Phone")
            assertEquals(id, again)
            // Each read decodes SharedPreferences rather than returning an in-memory list.
            assertEquals(listOf(DesktopSend(id, "account", "work", "https://phone.example", "Phone")), persistent.pending)
        } finally {
            persistent.save(emptyList())
        }
    }
}

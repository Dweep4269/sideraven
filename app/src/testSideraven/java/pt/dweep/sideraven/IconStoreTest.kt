/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import mozilla.components.browser.icons.Icon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.util.Base64

/** Icons for unloaded, closed and desktop items. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IconStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun bitmap(
        size: Int = 16,
        colour: Int = Color.RED,
    ) = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply { eraseColor(colour) }

    private fun dataUri(bitmap: Bitmap): String {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray())
    }

    @Test
    fun `THEN an open tab's icon wins, then the last real icon, then the loader's real icon`() {
        val tab = bitmap()
        val stored = bitmap()
        val loaded = bitmap()

        assertSame(tab, SidebarIcons.pick(tabIcon = tab, stored = stored, loaded = loaded))
        assertSame(stored, SidebarIcons.pick(tabIcon = null, stored = stored, loaded = loaded))
        assertSame(loaded, SidebarIcons.pick(tabIcon = null, stored = null, loaded = loaded))
        assertNull(SidebarIcons.pick(tabIcon = null, stored = null, loaded = null))
    }

    @Test
    fun `THEN a generated letter icon from Fenix's loader is never taken for a real one`() {
        assertNull(SidebarIcons.realOrNull(Icon(bitmap(), source = Icon.Source.GENERATOR)))
        assertNotNull(SidebarIcons.realOrNull(Icon(bitmap(), source = Icon.Source.DISK)))
    }

    @Test
    fun `GIVEN desktop's icon as a data URI THEN it is kept small, and only when none is known`() {
        val store = IconStore(folder.root)

        store.putDataUri("https://a.example/", dataUri(bitmap(size = 256)))
        val kept = store.get("https://a.example/")!!
        assertEquals(IconStore.MAX_SIZE_PX, kept.width)

        store.put("https://b.example/", bitmap(colour = Color.BLUE))
        store.putDataUri("https://b.example/", dataUri(bitmap(colour = Color.GREEN)))
        assertEquals(Color.BLUE, store.get("https://b.example/")!!.getPixel(0, 0))

        store.putDataUri("https://c.example/", "data:image/svg+xml;base64,PHN2Zz48L3N2Zz4=")
        assertNull(store.get("https://c.example/"))
    }

    @Test
    fun `THEN icons survive a restart, and the store stays bounded`() {
        val store = IconStore(folder.root, maxEntries = 3)
        listOf("1", "2", "3", "4").forEach { store.put("https://$it.example/", bitmap()) }

        val reopened = IconStore(folder.root, maxEntries = 3)

        assertNull(reopened.get("https://1.example/"))
        listOf("2", "3", "4").forEach { assertNotNull(reopened.get("https://$it.example/")) }
    }

    @Test
    fun `WHEN the same icon is handed in again THEN nothing is stored again`() {
        val store = IconStore(folder.root)
        val icon = bitmap()

        store.put("https://a.example/", icon)
        val after = store.version.value
        store.put("https://a.example/", icon)

        assertEquals(after, store.version.value)
    }

    // The registrable domain, as the public suffix list would give it, for these tests' hosts.
    private val siteOf: suspend (String) -> String? = { host -> host.split('.').takeLast(2).joinToString(".") }

    @Test
    fun `THEN a pin's tab gives the pin its icon on the pinned page, on the page a redirect landed on, and anywhere on the same site`() = kotlinx.coroutines.test.runTest {
        org.junit.Assert.assertTrue(SidebarIcons.onPinSite("https://news.example/", "https://news.example/", atFirstPage = false, siteOf))
        // gmail.com redirects to mail.google.com: another site, but still the page the pin opened.
        org.junit.Assert.assertTrue(SidebarIcons.onPinSite("https://gmail.com/", "https://mail.google.com/mail/u/0/", atFirstPage = true, siteOf))
        org.junit.Assert.assertTrue(SidebarIcons.onPinSite("https://reddit.com/", "https://www.reddit.com/r/android/", atFirstPage = false, siteOf))
    }

    @Test
    fun `GIVEN a pin's tab browsed to another site THEN its icon isn't the pin's`() = kotlinx.coroutines.test.runTest {
        org.junit.Assert.assertFalse(SidebarIcons.onPinSite("https://reddit.com/", "https://www.youtube.com/watch", atFirstPage = false, siteOf))
        org.junit.Assert.assertFalse(SidebarIcons.onPinSite("https://reddit.com/", "about:blank", atFirstPage = false, siteOf))
    }
}

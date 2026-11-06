/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import mozilla.components.browser.icons.Icon
import java.io.File
import java.security.MessageDigest
import java.util.Base64

/**
 * The last real favicon known for an address, so Essentials, Pinned Tabs and Desktop
 * Tabs keep their icon while no tab is open. Filled from open tabs, from Fenix's icon loader, and
 * from desktop's icons that arrive with Zen Sync. Icons are kept small, on this device only, and
 * the store is bounded: the least recently used go first.
 *
 * @param dir Where icons are kept as PNG files (null: memory only).
 */
class IconStore(
    private val dir: File?,
    private val maxEntries: Int = MAX_ENTRIES,
) {
    private val memory = LinkedHashMap<String, Bitmap>(16, 0.75f, true)

    /** The bitmap each stored icon came from, so handing in the same one again does nothing. */
    private val sources = HashMap<String, Bitmap>()
    private val changes = MutableStateFlow(0)

    // File times order the least recently used; strictly increasing, even on coarse file systems.
    private var lastTouch = 0L

    private fun nextTouch(): Long = maxOf(System.currentTimeMillis(), lastTouch + 1_000).also { lastTouch = it }

    /** Bumps whenever an icon is added, for UI that shows stored icons. */
    val version: StateFlow<Int> = changes.asStateFlow()

    init {
        dir?.mkdirs()
    }

    @Synchronized
    fun get(url: String): Bitmap? {
        memory[url]?.let { return it }
        val file = fileFor(url) ?: return null
        if (!file.exists()) return null
        val bitmap = BitmapFactory.decodeFile(file.path) ?: return null
        file.setLastModified(nextTouch())
        memory[url] = bitmap
        return bitmap
    }

    /** Keeps [bitmap] as the real icon for [url]. */
    @Synchronized
    fun put(
        url: String,
        bitmap: Bitmap,
    ) {
        if (sources[url] === bitmap || memory[url] === bitmap) return
        sources[url] = bitmap
        val small = shrink(bitmap)
        memory[url] = small
        fileFor(url)?.let { file ->
            runCatching { file.outputStream().use { small.compress(Bitmap.CompressFormat.PNG, 100, it) } }
            file.setLastModified(nextTouch())
        }
        trim()
        changes.value++
    }

    /** Desktop's icon (a `data:image/...;base64,` URI from Zen) for [url], unless one is known. */
    fun putDataUri(
        url: String,
        dataUri: String,
    ) {
        if (get(url) != null) return
        val comma = dataUri.indexOf(',')
        if (!dataUri.startsWith("data:image/") || comma == -1 || !dataUri.substring(0, comma).endsWith(";base64")) return
        val bytes = runCatching { Base64.getMimeDecoder().decode(dataUri.substring(comma + 1)) }.getOrNull() ?: return
        // SVG and other formats Android can't decode are skipped.
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return
        put(url, bitmap)
    }

    private fun shrink(bitmap: Bitmap): Bitmap {
        val largest = maxOf(bitmap.width, bitmap.height)
        if (largest <= MAX_SIZE_PX) return bitmap
        val scale = MAX_SIZE_PX.toFloat() / largest
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1), true)
    }

    private fun trim() {
        while (memory.size > maxEntries) memory.keys.first().let { memory.remove(it); sources.remove(it) }
        val files = dir?.listFiles()?.filter { it.extension == "png" } ?: return
        if (files.size > maxEntries) files.sortedBy { it.lastModified() }.take(files.size - maxEntries).forEach { it.delete() }
    }

    private fun fileFor(url: String): File? {
        val dir = dir ?: return null
        val hash = MessageDigest.getInstance("SHA-1").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(dir, "$hash.png")
    }

    companion object {
        /** Icons are kept at most this big (px); the Sidebar shows them at 20–24dp. */
        const val MAX_SIZE_PX = 64
        const val MAX_ENTRIES = 400

        @Volatile private var instance: IconStore? = null

        fun get(context: Context): IconStore =
            instance ?: synchronized(this) {
                instance ?: allowDiskReads { IconStore(File(context.applicationContext.filesDir, "sideraven_icons")) }.also { instance = it }
            }
    }
}

/** Which icon a Sidebar item shows. */
object SidebarIcons {
    /** The open tab's own icon, else the last real one known, else the loader's real one. */
    fun pick(
        tabIcon: Bitmap?,
        stored: Bitmap?,
        loaded: Bitmap?,
    ): Bitmap? = tabIcon ?: stored ?: loaded

    /**
     * Whether a pin's tab, now at [tabUrl], is still on the pin's site, so its icon is the pin's: at
     * [pinUrl] itself, on the first page the tab loaded ([atFirstPage]: where a redirect landed, as
     * gmail.com lands on mail.google.com), or anywhere on the same site ([siteOf] gives a host's
     * registrable domain).
     */
    suspend fun onPinSite(
        pinUrl: String,
        tabUrl: String,
        atFirstPage: Boolean,
        siteOf: suspend (host: String) -> String?,
    ): Boolean {
        if (tabUrl == pinUrl) return true
        val tabHost = runCatching { java.net.URI(tabUrl).host }.getOrNull() ?: return false
        if (atFirstPage) return true
        val pinHost = runCatching { java.net.URI(pinUrl).host }.getOrNull() ?: return false
        if (tabHost == pinHost) return true
        val site = siteOf(pinHost) ?: return false
        return site == siteOf(tabHost)
    }

    /** A loader result that is a real favicon, not one generated from the address's letter. */
    fun realOrNull(icon: Icon): Bitmap? = icon.bitmap.takeUnless { icon.source == Icon.Source.GENERATOR }
}

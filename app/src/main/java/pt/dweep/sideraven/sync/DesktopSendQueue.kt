/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.sync

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import pt.dweep.sideraven.allowDiskReads
import pt.dweep.sideraven.newPinId

/** A one-time copy, bound to the account that requested it. IDs survive interrupted uploads. */
data class DesktopSend(val id: String, val owner: String, val spaceId: String, val url: String, val title: String)

interface DesktopSendQueue {
    val pending: List<DesktopSend>

    fun save(pending: List<DesktopSend>)
}

class InMemoryDesktopSendQueue : DesktopSendQueue {
    override var pending: List<DesktopSend> = emptyList()
        private set

    override fun save(pending: List<DesktopSend>) { this.pending = pending }
}

/** Only used on the sync runner's IO dispatcher, under its mutex. */
class PreferencesDesktopSendQueue private constructor(context: Context) : DesktopSendQueue {
    private val prefs = context.getSharedPreferences("sideraven_desktop_sends", Context.MODE_PRIVATE)
    override val pending: List<DesktopSend>
        get() {
            val array = JSONArray(prefs.getString("pending", "[]"))
            return (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                DesktopSend(item.getString("id"), item.getString("owner"), item.getString("space"), item.getString("url"), item.getString("title"))
            }
        }

    override fun save(pending: List<DesktopSend>) {
        val array = JSONArray(pending.map { item ->
            JSONObject().put("id", item.id).put("owner", item.owner).put("space", item.spaceId).put("url", item.url).put("title", item.title)
        })
        // Persist before writing to Sync storage, including across a process crash.
        if (!prefs.edit().putString("pending", array.toString()).commit()) throw java.io.IOException("could not save desktop send")
    }

    companion object {
        @Volatile private var instance: PreferencesDesktopSendQueue? = null

        fun get(context: Context): PreferencesDesktopSendQueue =
            instance ?: synchronized(this) {
                instance ?: allowDiskReads { PreferencesDesktopSendQueue(context.applicationContext) }.also { instance = it }
            }
    }
}

internal fun desktopSendOwner(credentials: SyncCredentials): String =
    RecordCrypto.hex(RecordCrypto.sha256(credentials.scopedKey.toByteArray(Charsets.UTF_8)))

/** The same URL in the same Space is a retry while it is still queued. */
internal fun DesktopSendQueue.enqueue(owner: String, spaceId: String, url: String, title: String): String {
    pending.firstOrNull { it.owner == owner && it.spaceId == spaceId && it.url == url }?.let { return it.id }
    val item = DesktopSend(newPinId(), owner, spaceId, url, title)
    save(pending + item)
    return item.id
}

/** Exclude browser-internal pages and local files from remote copying. */
fun desktopSendableUrl(url: String): Boolean =
    runCatching { java.net.URI(url).let { it.scheme in setOf("http", "https") && !it.host.isNullOrBlank() } }.getOrDefault(false)

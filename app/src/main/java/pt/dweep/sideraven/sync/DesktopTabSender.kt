/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.sync

import org.json.JSONArray
import org.json.JSONObject

/** Uploads copies only; normal tabs remain outside the ordinary two-way merge. */
internal object DesktopTabSender {
    fun upload(connection: ZenConnection, item: DesktopSend, allowed: () -> Boolean) {
        repeat(3) {
            if (!allowed()) throw SyncError.Protocol("desktop send destination is no longer shared")
            val snapshot = connection.readSnapshot()
            // Desktop may have closed a copy whose upload acknowledgement we lost. Never revive it.
            if (item.id in snapshot.tombstones) return
            val space = snapshot.records.filterIsInstance<ZenSpace>().firstOrNull { it.uuid == item.spaceId }
                ?: throw SyncError.Protocol("desktop send destination is missing")
            if (item.id in snapshot.unreadable) throw SyncError.Protocol("desktop send record is unreadable")
            val existing = snapshot.records.firstOrNull { it.id == item.id }
            if (existing == null) {
                if (!allowed()) throw SyncError.Protocol("desktop send destination is no longer shared")
                val data = JSONObject().put("tabId", item.id).put("url", item.url).put("title", item.title)
                    .put("pinned", false).put("essential", false).put("workspaceUuid", item.spaceId)
                    .put("folderId", JSONObject.NULL).put("staticLabel", JSONObject.NULL)
                    .put("icon", JSONObject.NULL).put("hasStaticIcon", false)
                    .put("containerGuid", space.containerGuid ?: JSONObject.NULL).put("defaultContainer", space.containerGuid == null)
                if (write(connection, ZenWire.cleartext(item.id, "tab", data), 0.0) is PutResult.Conflict) return@repeat
            } else if (existing !is ZenTab || !existing.isNormalTab) {
                throw SyncError.Protocol("desktop send record identity conflict")
            } else if (existing.workspaceUuid != item.spaceId || existing.folderId != null) {
                // Desktop has already moved the uploaded copy. Leave its new placement alone.
                return
            }
            if (item.id in space.children) return
            if (!allowed()) throw SyncError.Protocol("desktop send destination is no longer shared")
            val data = JSONObject(space.data.toString()).put("children", JSONArray(listOf(item.id) + space.children))
            if (write(connection, ZenWire.cleartext(space.id, "space", data), snapshot.modified[space.id] ?: 0.0) is PutResult.Ok) return
        }
        throw SyncError.Protocol("desktop send conflict")
    }

    private fun write(connection: ZenConnection, cleartext: JSONObject, modified: Double): PutResult =
        connection.storage.write(
            SPACES_COLLECTION, cleartext.getString("id"),
            RecordCrypto.encrypt(cleartext.toString().toByteArray(Charsets.UTF_8), connection.keys), modified,
        )
}

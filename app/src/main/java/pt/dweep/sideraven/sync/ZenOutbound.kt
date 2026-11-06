/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.sync

import org.json.JSONArray
import org.json.JSONObject
import pt.dweep.sideraven.Essential
import pt.dweep.sideraven.Folder
import pt.dweep.sideraven.PinnedTab
import pt.dweep.sideraven.Space
import pt.dweep.sideraven.SpacesData

/** One record as the tablet would write it, and when the user last changed its source here. */
data class OutboundRecord(
    val cleartext: JSONObject,
    val localModified: Long,
)

/**
 * The tablet's synced items as Zen records (the reverse of [ZenProjection.fromZen]). Each record
 * starts from the data it arrived with and only changes what Sideraven models, so an item the
 * user didn't touch projects to exactly what the server has.
 */
object ZenOutbound {
    /**
     * Every record the tablet would write, by record id. [remote] gives the context Zen's records
     * need: ids Sideraven doesn't model (splits, normal tabs) keep their place in `children`.
     */
    fun project(
        local: SpacesData,
        essentials: List<Essential>,
        settings: ZenSyncSettings,
        remote: ZenSnapshot,
    ): Map<String, OutboundRecord> {
        val out = linkedMapOf<String, OutboundRecord>()
        val remoteById = remote.records.associateBy { it.id }
        val foreignIds = remote.records.filter { it is ZenSplit || (it is ZenTab && it.isNormalTab) }.map { recordKey(it) }.toSet() +
            remote.unreadable
        val synced = local.spaces.filterNot { it.isLocal }
        // What each record starts from: the data it arrived with, favicons restored.
        fun baseOf(origin: ZenOrigin?) = origin?.fullData(remoteById[origin.recordId]?.data) ?: JSONObject()

        if (settings.syncSpaces) {
            synced.forEach { space ->
                val origin = ZenOrigin.decode(space.extra)
                val pins = local.pinnedIn(space.id)
                val folders = local.foldersIn(space.id)
                val topFolders = folders.filter { it.parentId == null }.map { it.id }
                val topPins = pins.filter { it.folderId == null }.map { it.id }
                val data = baseOf(origin)
                data.put("uuid", space.id)
                if (space.name.isNotBlank() || data.has("name")) set(data, "name", space.name)
                if (space.icon.isNotBlank()) set(data, "icon", space.icon)
                // Normal tabs may have been sent since this Space's stored origin was read.
                // Keep their current desktop placement even when a newer local edit wins.
                val desktopChildren = origin?.recordId?.let { remoteById[it]?.data?.optJSONArray("children") }
                data.put("children", JSONArray(ordered(desktopChildren ?: data.optJSONArray("children"), listOf(topFolders, topPins), foreignIds)))
                // Only a colour the user changed here is sent (never one just adopted from desktop).
                val syncedAccent = origin?.let { it.accent ?: ZenColours.nearestAccent((ZenWire.decode(ZenWire.cleartext(it.recordId, "space", it.data)) as? ZenSpace)?.theme?.primaryHex) ?: pt.dweep.sideraven.DEFAULT_SPACE_ACCENT }
                if (settings.shareColours && space.accent != syncedAccent) {
                    data.put("theme", ZenColours.solidTheme(space.accent))
                }
                val id = origin?.recordId ?: space.id
                out[id] = OutboundRecord(ZenWire.cleartext(id, "space", data), space.modified)
            }
            val syncedIds = synced.map { it.id }.toSet()
            local.folders.filter { it.spaceId in syncedIds }.forEach { folder ->
                val origin = ZenOrigin.decode(folder.extra)
                val subFolders = local.folders.filter { it.parentId == folder.id }.map { it.id }
                val folderPins = local.pinned.filter { it.folderId == folder.id }.map { it.id }
                val data = baseOf(origin)
                data.put("folderId", folder.id)
                if (folder.name.isNotBlank() || data.has("name")) set(data, "name", folder.name)
                // A Folder without an icon shows the default one; don't write that back.
                if (!(folder.icon == pt.dweep.sideraven.DEFAULT_FOLDER_ICON && ZenWire.string(data, "icon") == null)) set(data, "icon", folder.icon)
                set(data, "workspaceUuid", folder.spaceId)
                set(data, "parentFolderId", folder.parentId)
                data.put("children", JSONArray(ordered(data.optJSONArray("children"), listOf(subFolders, folderPins), foreignIds)))
                val id = origin?.recordId ?: folder.id
                out[id] = OutboundRecord(ZenWire.cleartext(id, "folder", data), folder.modified)
            }
            local.pinned.filter { it.spaceId in syncedIds }.forEach { pin ->
                val origin = ZenOrigin.decode(pin.extra)
                val data = tabData(baseOf(origin), pin.id, pin.url, pin.title, pin.customName)
                set(data, "workspaceUuid", pin.spaceId)
                set(data, "folderId", pin.folderId)
                setFlag(data, "essential", false)
                val id = origin?.recordId ?: pin.id
                out[id] = OutboundRecord(ZenWire.cleartext(id, "tab", data), pin.modified)
            }
        }

        if (settings.syncEssentials) {
            essentials.forEach { essential ->
                val origin = ZenOrigin.decode(essential.extra)
                val data = tabData(baseOf(origin), essential.id, essential.url, essential.title, essential.customName)
                setFlag(data, "essential", true)
                set(data, "workspaceUuid", null)
                val id = origin?.recordId ?: essential.id
                out[id] = OutboundRecord(ZenWire.cleartext(id, "tab", data), essential.modified)
            }
        }

        if (settings.syncSpaces || settings.syncEssentials) {
            val layout = remote.layout
            val data = (layout?.data ?: JSONObject()).copyJson()
            if (settings.syncSpaces) {
                // Desktop Spaces the tablet doesn't have yet stay listed; Local Spaces never are.
                val ours = synced.map { it.id }
                // Desktop Spaces the tablet doesn't sync (Local here, or new) keep their place.
                data.put("spaces", JSONArray(ordered(data.optJSONArray("spaces"), listOf(ours), layout?.spaces.orEmpty().toSet() - ours.toSet())))
            }
            if (settings.syncEssentials) {
                val buckets = JSONObject()
                val existing = data.optJSONObject("essentials")
                val keys = (existing?.keys()?.asSequence()?.toList().orEmpty() + essentials.map { it.bucket ?: ZenProjection.DEFAULT_BUCKET }).distinct()
                keys.forEach { key ->
                    val ids = essentials.filter { (it.bucket ?: ZenProjection.DEFAULT_BUCKET) == key }.map { ZenOrigin.decode(it.extra)?.recordId ?: it.id }
                    buckets.put(key, JSONArray(ordered(existing?.optJSONArray(key), listOf(ids), remote.unreadable)))
                }
                data.put("essentials", buckets)
            }
            val modified = maxOf(local.layoutModified, essentials.maxOfOrNull { it.modified } ?: 0)
            out[LAYOUT_RECORD_ID] = OutboundRecord(ZenWire.cleartext(LAYOUT_RECORD_ID, "layout", data), modified)
        }
        return out
    }

    private fun recordKey(record: ZenRecord): String =
        when (record) {
            is ZenTab -> record.tabId
            is ZenSplit -> record.splitId
            is ZenFolder -> record.folderId
            is ZenSpace -> record.uuid
            is ZenLayout -> record.id
        }

    private fun tabData(
        base: JSONObject,
        id: String,
        url: String,
        title: String,
        customName: String?,
    ): JSONObject {
        val data = base
        data.put("tabId", id)
        set(data, "url", url)
        // A tab without a title shows its URL; don't write that back.
        if (!(title == url && ZenWire.string(data, "title") == null)) set(data, "title", title)
        setFlag(data, "pinned", true)
        if (!(customName == null && ZenWire.string(data, "staticLabel")?.isBlank() == true)) set(data, "staticLabel", customName)
        return data
    }

    /**
     * The new order of a `children`-like list made of [groups] (e.g. Folders, then pins). When each
     * group kept its own relative order, the original list stays as it was (so Zen's interleaving
     * and foreign ids survive) with new items appended; otherwise the groups in order, with
     * foreign ids kept after them.
     */
    fun ordered(
        original: JSONArray?,
        groups: List<List<String>>,
        foreign: Set<String>,
    ): List<String> {
        val before = (0 until (original?.length() ?: 0)).mapNotNull { original!!.opt(it) as? String }
        val ours = groups.flatten()
        val oursSet = ours.toSet()
        val kept = before.filter { it in oursSet || it in foreign }
        val keptSet = kept.toSet()
        val stillInOrder = groups.all { group -> kept.filter { it in group.toSet() } == group.filter { it in keptSet } }
        return if (stillInOrder) {
            kept + ours.filter { it !in keptSet }
        } else {
            ours + kept.filter { it in foreign }
        }
    }

    /** Sets [key] unless it already decodes to [value] (so untouched fields keep their exact form). */
    private fun set(
        data: JSONObject,
        key: String,
        value: String?,
    ) {
        if (ZenWire.string(data, key) == value && (value != null || data.has(key))) return
        if (value == null && !data.has(key)) return
        data.put(key, value ?: JSONObject.NULL)
    }

    private fun setFlag(
        data: JSONObject,
        key: String,
        value: Boolean,
    ) {
        val current = ZenWire.flag(data.opt(key))
        // Zen reads an absent `pinned` as true and an absent `essential` as false; keep that form.
        val effective =
            when {
                data.has(key) && !data.isNull(key) -> current
                key == "pinned" -> true
                else -> false
            }
        if (effective == value) return
        data.put(key, value)
    }

    private fun JSONObject.copyJson(): JSONObject = JSONObject(toString())
}

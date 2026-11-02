/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.sync

import android.content.Context
import org.mozilla.fenix.ext.components
import pt.dweep.sideraven.Essentials
import pt.dweep.sideraven.PinnedTab
import pt.dweep.sideraven.PreferencesEssentialsRepository
import pt.dweep.sideraven.Space
import pt.dweep.sideraven.Spaces
import pt.dweep.sideraven.newPinId

/** How a sync went: fully, or read-only because desktop's data is a newer version. */
enum class SyncOutcome { Synced, ReadOnly }

/** The First Sync choices. */
enum class FirstSyncChoice { KeepSeparate, Replace, Merge }

/**
 * What a Merge would add to Zen, shown for confirmation before anything is uploaded.
 *
 * @property matches Tablet Space name -> the desktop Space it joins.
 */
data class MergeSummary(
    val spaces: Int,
    val pinnedTabs: Int,
    val essentials: Int,
    val matches: List<Pair<String, String>>,
)

/** A computed Merge, ready to apply once the user confirms. */
class MergePreview internal constructor(
    val summary: MergeSummary,
    internal val snapshot: ZenSnapshot,
    internal val spaces: pt.dweep.sideraven.SpacesData,
    internal val essentials: List<pt.dweep.sideraven.Essential>,
    internal val held: Set<String>,
)

/**
 * Zen Sync for the app: the account, the server, and the tablet's Spaces and Essentials.
 * Every method is blocking network work: call it off the main thread.
 *
 * @param fromTabletName The Local Space that keeps tablet-only Essentials at First Sync.
 * @param followAddress Desktop gave the Essential or Pinned Tab open in a tab a new address
 * (tab id, old address, new address): see [pt.dweep.sideraven.PinnedAddressFollow].
 */
class ZenSync(
    private val account: ZenAccount,
    private val transport: SyncHttpTransport,
    private val spaces: Spaces,
    private val essentials: Essentials,
    private val base: SyncBaseRepository,
    private val settings: ZenSyncSettingsStore,
    private val fromTabletName: String = FROM_TABLET,
    private val desktop: DesktopViewStore = InMemoryDesktopViewStore(),
    private val followAddress: (tabId: String, oldUrl: String, newUrl: String) -> Unit = { _, _, _ -> },
    private val icons: pt.dweep.sideraven.IconStore? = null,
    private val sends: DesktopSendQueue = InMemoryDesktopSendQueue(),
) {
    private var activeSendOwner: String? = null

    /** Persist a one-time copy. Caller must exclude private tabs and serialize with [sync]. */
    suspend fun queueDesktopTab(spaceId: String, url: String, title: String): String {
        if (!canSendTo(spaceId) || !desktopSendableUrl(url)) throw SyncError.Protocol("desktop send unavailable")
        val credentials = account.credentials() ?: throw SyncError.Auth("signed out")
        val owner = desktopSendOwner(credentials)
        activeSendOwner = owner
        return sends.enqueue(owner, spaceId, url, title)
    }

    private fun canSendTo(spaceId: String): Boolean = settings.settings.value.let { options ->
        options.enabled && options.firstSyncDone && options.syncSpaces && spaceId !in options.desktopOnly &&
            spaces.data.spaces.any { it.id == spaceId && !it.isLocal }
    }

    fun isDesktopSendPending(id: String): Boolean = sends.pending.any { it.id == id }

    /** Read off the main thread; only expose this account's eligible pending copies. */
    fun pendingDesktopSends(): List<DesktopSend> = sends.pending.filter { it.owner == activeSendOwner && canSendTo(it.spaceId) }

    /** Prioritize this copy over unrelated Space/pin merging, without waiting for a sync timer. */
    suspend fun sendDesktopTabNow(id: String): SyncOutcome {
        val credentials = account.credentials() ?: throw SyncError.Auth("signed out")
        val owner = desktopSendOwner(credentials)
        activeSendOwner = owner
        val item = sends.pending.firstOrNull { it.id == id && it.owner == owner && canSendTo(it.spaceId) }
            ?: return SyncOutcome.Synced
        // A newly created Space has to reach desktop before a tab can be added to it.
        if (spaces.data.spaces.firstOrNull { it.id == item.spaceId }?.extra == null) return sync()
        val connection = ZenConnection.open(credentials, transport)
        if ((connection.engineVersion() ?: 0) > ZenConnection.SUPPORTED_ENGINE_VERSION) return SyncOutcome.ReadOnly
        sendPending(connection, owner, onlyId = id)
        return SyncOutcome.Synced
    }

    private fun sendPending(connection: ZenConnection, owner: String, onlyId: String? = null) {
        val pending = sends.pending.filter { it.owner == owner && canSendTo(it.spaceId) && (onlyId == null || it.id == onlyId) }
        if (pending.isEmpty()) return
        if (!connection.normalTabsEnabled()) throw SyncError.Protocol("enable zen.spaces-sync.normal-tabs on desktop to send tabs")
        var failure: Exception? = null
        val completed = mutableSetOf<String>()
        pending.forEach { item ->
            // A Space switched off or removed while offline must not block other destinations.
            // Retain its copy locally in case the user shares that Space again.
            if (!canSendTo(item.spaceId)) return@forEach
            try {
                DesktopTabSender.upload(connection, item) { canSendTo(item.spaceId) }
                completed += item.id
            } catch (e: SyncError) {
                failure = failure ?: e
            } catch (e: java.io.IOException) {
                failure = failure ?: e
            }
        }
        read(connection)
        // Keep previews durable until the confirmed snapshot is available to replace them.
        if (completed.isNotEmpty()) sends.save(sends.pending.filterNot { it.id in completed })
        failure?.let { throw it }
    }

    private suspend fun connect(): ZenConnection {
        val credentials = account.credentials() ?: throw SyncError.Auth("signed out")
        return ZenConnection.open(credentials, transport)
    }

    private val settingsLock = Any()

    /**
     * Changes the Zen Sync choices from their current value, one change at a time, so a sync
     * running in the background can never write back an older copy over a switch just made.
     */
    private fun updateSettings(transform: (ZenSyncSettings) -> ZenSyncSettings): ZenSyncSettings =
        synchronized(settingsLock) {
            val current = settings.settings.value
            transform(current).also { if (it != current) settings.save(it) }
        }

    /** Reads the server and remembers what desktop has (for the Space list). */
    private fun read(connection: ZenConnection): ZenSnapshot =
        connection.readSnapshot().also { snapshot ->
            desktop.save(DesktopView.of(snapshot))
            // Desktop's favicons, for items not open here (kept small, only when none is known).
            icons?.let { store ->
                snapshot.records.filterIsInstance<ZenTab>().forEach { tab -> tab.icon?.let { store.putDataUri(tab.url, it) } }
            }
        }

    /**
     * First Sync, "Keep tablet separate": download desktop's data; existing tablet Spaces become
     * Local Spaces, tablet-only Essentials move into a Local Space [fromTabletName]. Uploads nothing.
     */
    suspend fun firstSync(choice: FirstSyncChoice) {
        when (choice) {
            FirstSyncChoice.KeepSeparate -> keepSeparate()
            FirstSyncChoice.Replace -> replace()
            FirstSyncChoice.Merge -> applyMerge(previewMerge())
        }
    }

    /**
     * "Replace with desktop": desktop's Spaces, pins, Folders and Essentials replace the tablet's
     * (for what syncs); open tabs move into the first desktop Space. Uploads nothing.
     */
    private suspend fun replace() {
        val snapshot = read(connect())
        val options = settings.settings.value
        val imported = ZenProjection.fromZen(snapshot, options.shareColours)
        if (options.syncSpaces && imported.spaces.isNotEmpty()) {
            val first = imported.spaces.first().id
            spaces.applyRemote { data ->
                data.copy(
                    spaces = imported.spaces,
                    currentSpaceId = first,
                    pinned = imported.pinned,
                    folders = imported.folders,
                    assignments = data.assignments.mapValues { first },
                    collapsedFolders = emptySet(),
                )
            }
        }
        if (options.syncEssentials) essentials.applyRemote(imported.essentials)
        base.save(ZenProjection.baseOf(snapshot, ZenProjection.heldRecords(imported, options)))
        updateSettings { it.copy(enabled = true, firstSyncDone = true, seenSpaces = it.seenSpaces + desktopSpaceIds(snapshot)) }
    }

    /**
     * "Merge both", step 1: work out the merged result and what it would add to Zen. Tablet Spaces
     * join desktop Spaces with the same name (ignoring case and surrounding spaces); pins and
     * Essentials with the same URL become one; desktop's order and settings win. Nothing changes yet.
     */
    suspend fun previewMerge(): MergePreview {
        val snapshot = read(connect())
        val options = settings.settings.value
        val imported = ZenProjection.fromZen(snapshot, options.shareColours)
        // Tablet items that reuse desktop ids (Zen Sync turned off and on) get fresh ids first.
        val data = ZenLocalMerge.rekeyCollisions(spaces.data, ZenLocalMerge.idsOf(imported))
        fun norm(name: String) = name.trim().lowercase()
        val desktopByName = imported.spaces.associateBy { norm(it.name) }

        var merged = data
        val matches = mutableListOf<Pair<String, String>>()
        var newSpaces = 0
        var newPins = 0
        if (options.syncSpaces) {
            val idMap = mutableMapOf<String, String>()
            data.spaces.forEach { space ->
                desktopByName[norm(space.name)]?.let { desktop ->
                    idMap[space.id] = desktop.id
                    matches += space.name to desktop.name
                } ?: run { newSpaces++ }
            }
            val desktopPinUrls = imported.pinned.groupBy { it.spaceId }.mapValues { (_, pins) -> pins.map { it.url }.toSet() }
            val tabletPins =
                data.pinned.mapNotNull { pin ->
                    val target = idMap[pin.spaceId] ?: pin.spaceId
                    if (pin.url in desktopPinUrls[target].orEmpty()) null else pin.copy(spaceId = target)
                }
            newPins = tabletPins.size
            val tabletFolders = data.folders.map { it.copy(spaceId = idMap[it.spaceId] ?: it.spaceId) }
            val unmatched = data.spaces.filter { it.id !in idMap }
            merged =
                data.copy(
                    spaces = imported.spaces + unmatched,
                    currentSpaceId = idMap[data.currentSpaceId] ?: data.currentSpaceId,
                    assignments = data.assignments.mapValues { (_, space) -> idMap[space] ?: space },
                    pinned = imported.pinned + tabletPins,
                    folders = imported.folders + tabletFolders,
                )
        }
        val tabletEssentials = essentials.essentials.value
        val desktopUrls = imported.essentials.map { it.url }.toSet()
        val addedEssentials = if (options.syncEssentials) tabletEssentials.filter { it.url !in desktopUrls } else emptyList()
        val mergedEssentials = if (options.syncEssentials) imported.essentials + addedEssentials else tabletEssentials
        return MergePreview(
            MergeSummary(newSpaces, newPins, addedEssentials.size, matches),
            snapshot,
            merged,
            mergedEssentials,
            ZenProjection.heldRecords(imported, options),
        )
    }

    /** "Merge both", step 2 (after the user confirmed the summary): apply it and upload. */
    suspend fun applyMerge(preview: MergePreview) {
        val options = settings.settings.value
        spaces.applyRemote { preview.spaces }
        essentials.applyRemote(preview.essentials)
        base.save(ZenProjection.baseOf(preview.snapshot, preview.held))
        updateSettings { it.copy(enabled = true, firstSyncDone = true, seenSpaces = it.seenSpaces + desktopSpaceIds(preview.snapshot)) }
        // The tablet-only items are new to the server: a normal sync uploads them.
        sync()
    }

    private suspend fun keepSeparate() {
        val snapshot = read(connect())
        val options = settings.settings.value
        val imported = ZenProjection.fromZen(snapshot, options.shareColours)

        val tabletEssentials = essentials.essentials.value
        val desktopUrls = imported.essentials.map { it.url }.toSet()
        val tabletOnly = if (options.syncEssentials) tabletEssentials.filter { it.url !in desktopUrls } else emptyList()
        val essentialLinks = essentials.linkedTabs.value

        val fromTablet = tabletOnly.takeIf { it.isNotEmpty() }?.let { Space(newPinId(), fromTabletName, FROM_TABLET_ICON, isLocal = true) }
        val movedPins =
            fromTablet?.let { space ->
                tabletOnly.map { essential ->
                    PinnedTab(newPinId(), space.id, essential.url, essential.title, customName = essential.customName) to essentialLinks[essential.url]
                }
            }.orEmpty()

        spaces.applyRemote { current ->
            if (!options.syncSpaces && fromTablet == null) return@applyRemote current
            val data = if (options.syncSpaces) ZenLocalMerge.rekeyCollisions(current, ZenLocalMerge.idsOf(imported)) else current
            val local = data.spaces.map { it.copy(isLocal = true) }
            val desktop = if (options.syncSpaces) imported else ZenImport(emptyList(), emptyList(), emptyList(), emptyList())
            data.copy(
                spaces = desktop.spaces + local + listOfNotNull(fromTablet),
                pinned = data.pinned + desktop.pinned + movedPins.map { it.first },
                folders = data.folders + desktop.folders,
                // Open tabs of the moved Essentials now belong to the "From this tablet" Space.
                assignments = data.assignments + movedPins.mapNotNull { (_, tabId) -> tabId?.let { it to fromTablet!!.id } },
            )
        }
        movedPins.forEach { (pin, tabId) -> tabId?.let { spaces.linkPin(pin.id, it) } }
        if (options.syncEssentials) essentials.applyRemote(imported.essentials)

        base.save(ZenProjection.baseOf(snapshot, ZenProjection.heldRecords(imported, options)))
        updateSettings { it.copy(enabled = true, firstSyncDone = true, seenSpaces = it.seenSpaces + desktopSpaceIds(snapshot)) }
    }

    /** Which remote records Zen Sync covers right now (categories and Local Spaces). */
    private fun inScope(
        record: ZenRecord,
        options: ZenSyncSettings,
    ): Boolean {
        // Local Spaces and Desktop-only Spaces are both outside Zen Sync, in both directions.
        val localSpaces = spaces.data.spaces.filter { it.isLocal }.map { it.id }.toSet() + options.desktopOnly
        return when (record) {
            is ZenSpace -> options.syncSpaces && record.uuid !in localSpaces
            is ZenTab ->
                when {
                    record.isNormalTab -> false
                    record.isEssential -> options.syncEssentials
                    else -> options.syncSpaces && record.workspaceUuid !in localSpaces
                }
            is ZenFolder -> options.syncSpaces && record.workspaceUuid !in localSpaces
            is ZenLayout -> options.syncSpaces || options.syncEssentials
            is ZenSplit -> false
        }
    }

    /**
     * One two-way sync (after First Sync): upload what changed here, apply what changed on
     * desktop, the later edit winning when both changed. Writes are conditional; if desktop
     * wrote in between, the whole sync runs once more, then reports a conflict.
     */
    suspend fun sync(): SyncOutcome {
        val options = settings.settings.value
        if (!options.enabled || !options.firstSyncDone) return SyncOutcome.Synced
        slimStoredItems()
        val credentials = account.credentials() ?: throw SyncError.Auth("signed out")
        activeSendOwner = desktopSendOwner(credentials)
        val connection = ZenConnection.open(credentials, transport)
        // A newer Zen data version: keep reading what Sideraven understands, never write.
        val readOnly = (connection.engineVersion() ?: 0) > ZenConnection.SUPPORTED_ENGINE_VERSION
        repeat(2) { attempt ->
            if (syncOnce(connection, options, readOnly)) {
                if (readOnly) return SyncOutcome.ReadOnly
                sendPending(connection, desktopSendOwner(credentials))
                return SyncOutcome.Synced
            }
            if (attempt == 1) throw SyncError.Protocol("conflict")
        }
        return SyncOutcome.Synced
    }

    /**
     * Moves favicons stored inline by earlier versions out of the stored items into [ZenBlobs]. Not an
     * edit: nothing is stamped, and what Zen receives is unchanged.
     */
    private fun slimStoredItems() {
        fun slim(extra: String?) = if (ZenBlobs.holdsInline(extra)) ZenOrigin.decode(extra)?.encode() ?: extra else extra
        val stored = essentials.essentials.value
        val slimmed = stored.map { it.copy(extra = slim(it.extra)) }
        if (slimmed != stored) essentials.applyRemote(slimmed)
        spaces.applyRemote { data ->
            data.copy(
                spaces = data.spaces.map { it.copy(extra = slim(it.extra)) },
                pinned = data.pinned.map { it.copy(extra = slim(it.extra)) },
                folders = data.folders.map { it.copy(extra = slim(it.extra)) },
            )
        }
    }

    private fun desktopSpaceIds(snapshot: ZenSnapshot) = snapshot.records.filterIsInstance<ZenSpace>().map { it.uuid }.toSet()

    /**
     * Desktop Spaces this device has never met arrive switched off, marked new; Desktop-only
     * Spaces desktop has deleted are forgotten. Returns the choices to sync with.
     */
    private fun noticeDesktopSpaces(snapshot: ZenSnapshot): ZenSyncSettings {
        val desktopIds = desktopSpaceIds(snapshot)
        val here = spaces.data.spaces.map { it.id }.toSet()
        return updateSettings { options ->
            val fresh = desktopIds - options.seenSpaces - here
            options.copy(
                seenSpaces = options.seenSpaces + desktopIds,
                desktopOnly = (options.desktopOnly + fresh).intersect(desktopIds),
                newOnDesktop = (options.newOnDesktop + fresh).intersect(desktopIds),
            )
        }
    }

    /**
     * Ids of every record belonging to a Space switched off by now, on the server or on the
     * tablet: never written, whatever this sync planned before the switch.
     */
    private fun switchedOffIds(
        snapshot: ZenSnapshot,
        local: Map<String, OutboundRecord>,
    ): Set<String> {
        val away = settings.settings.value.desktopOnly
        if (away.isEmpty()) return emptySet()
        val remote =
            snapshot.records.filter { record ->
                when (record) {
                    is ZenSpace -> record.uuid in away
                    is ZenTab -> record.workspaceUuid in away
                    is ZenFolder -> record.workspaceUuid in away
                    else -> false
                }
            }.map { it.id }
        val tablet =
            local.filter { (_, record) ->
                val data = record.cleartext.optJSONObject("data")
                data != null && (ZenWire.string(data, "uuid") in away || ZenWire.string(data, "workspaceUuid") in away)
            }.keys
        return (remote + tablet).toSet()
    }

    /** Returns false when a conditional write lost a race (412). */
    private fun syncOnce(
        connection: ZenConnection,
        current: ZenSyncSettings,
        readOnly: Boolean = false,
    ): Boolean {
        val snapshot = read(connection)
        // The choices as they are now (a switch may have been made since [current] was read).
        val options = noticeDesktopSpaces(snapshot).copy(syncSpaces = current.syncSpaces, syncEssentials = current.syncEssentials)
        val remote = snapshot.records.filter { inScope(it, options) }.associate { it.id to ZenWire.cleartext(it.id, ZenProjection.kindOf(it), it.data) }
        val local = ZenOutbound.project(spaces.data, essentials.essentials.value, options, snapshot)
        val merged = ZenMerge.plan(base.base.value, local, remote, snapshot.modified)
        if (readOnly) {
            // Desktop's changes still arrive; the tablet's stay here, unsent (the base is left as it was).
            applyLocally(merged.serverAfter, options)
            return true
        }
        // A Space switched off while this sync ran is left alone on the server.
        val untouchable = switchedOffIds(snapshot, local)
        val plan = merged.copy(uploads = merged.uploads.filterKeys { it !in untouchable }, tombstones = merged.tombstones - untouchable)

        val written = mutableMapOf<String, Double>()
        plan.uploads.forEach { (id, cleartext) ->
            // Conditional on the record's server timestamp; brand-new records are written as is.
            val result = connection.storage.write(SPACES_COLLECTION, id, encrypt(connection, cleartext), snapshot.modified[id])
            if (result is PutResult.Conflict) return false
            written[id] = (result as PutResult.Ok).modified
        }
        plan.tombstones.forEach { id ->
            val result = connection.storage.write(SPACES_COLLECTION, id, encrypt(connection, ZenWire.tombstone(id)), snapshot.modified[id] ?: 0.0)
            if (result is PutResult.Conflict) return false
        }
        // A deleted pin that sat in a desktop split view leaves the split's tab list.
        snapshot.records.filterIsInstance<ZenSplit>().filter { split -> split.tabs.any { it in plan.tombstones } }.forEach { split ->
            val data = org.json.JSONObject(split.data.toString())
            data.put("tabs", org.json.JSONArray(split.tabs.filterNot { it in plan.tombstones }))
            val cleartext = ZenWire.cleartext(split.id, "split", data)
            val result = connection.storage.write(SPACES_COLLECTION, split.id, encrypt(connection, cleartext), snapshot.modified[split.id] ?: 0.0)
            if (result is PutResult.Conflict) return false
            written[split.id] = (result as PutResult.Ok).modified
        }

        val held = applyLocally(plan.serverAfter, options)
        // The last-synced state: what the server now holds, for the records the tablet holds.
        base.save(
            SyncBase(
                entries =
                    plan.serverAfter.filterKeys { it in held }.mapValues { (id, cleartext) ->
                        BaseEntry(Canonical.digest(cleartext), written[id] ?: snapshot.modified[id] ?: 0.0)
                    },
                collectionModified = (written.values + listOfNotNull(snapshot.lastModified)).maxOrNull(),
            ),
        )
        return true
    }

    private fun encrypt(
        connection: ZenConnection,
        cleartext: org.json.JSONObject,
    ): String = RecordCrypto.encrypt(cleartext.toString().toByteArray(Charsets.UTF_8), connection.keys)

    /** Rebuilds the tablet's synced items from what the server holds after the sync. */
    private fun applyLocally(
        after: Map<String, org.json.JSONObject>,
        options: ZenSyncSettings,
    ): Set<String> {
        val records = after.values.mapNotNull(ZenWire::decode)
        // Read again: a Space switched off while this sync ran must not come back.
        val away = settings.settings.value.desktopOnly
        val imported =
            ZenProjection.fromZen(ZenSnapshot(records, emptySet(), emptySet(), emptyMap(), null), options.shareColours).let { all ->
                all.copy(
                    spaces = all.spaces.filterNot { it.id in away },
                    pinned = all.pinned.filterNot { it.spaceId in away },
                    folders = all.folders.filterNot { it.spaceId in away },
                )
            }
        if (options.syncSpaces) {
            val before = spaces.data.pinned.associateBy { it.id }
            spaces.applyRemote { data -> ZenLocalMerge.spaces(data, imported, keepLocalAccents = !options.shareColours) }
            // A Pinned Tab desktop gave a new address: its open tab (linked by pin) may follow.
            spaces.data.pinned.forEach { pin ->
                val old = before[pin.id]?.url ?: return@forEach
                if (old != pin.url) spaces.pinnedLinks.value[pin.id]?.let { tabId -> followAddress(tabId, old, pin.url) }
            }
        }
        if (options.syncEssentials) {
            val current = essentials.essentials.value.associateBy { it.id }
            val updated = imported.essentials.map { e -> current[e.id]?.let { e.copy(modified = it.modified) } ?: e }
            essentials.applyRemote(updated)
            // An Essential desktop gave a new address keeps its open tab, which may follow.
            updated.forEach { essential ->
                val old = current[essential.id]?.url ?: return@forEach
                if (old != essential.url) essentials.relink(old, essential.url)?.let { tabId -> followAddress(tabId, old, essential.url) }
            }
        }
        return ZenProjection.heldRecords(imported, options)
    }

    /**
     * Marks a Space Local (or synced again). Its last-synced records are forgotten, so a Local
     * Space's changes, or its deletion, can never be taken for changes to send to desktop.
     */
    fun markLocal(
        spaceId: String,
        local: Boolean,
    ) {
        val data = spaces.data
        val space = data.spaces.firstOrNull { it.id == spaceId } ?: return
        spaces.setLocal(spaceId, local)
        if (local) {
            base.save(base.base.value.let { it.copy(entries = it.entries - recordIdsOf(space, data)) })
        }
    }

    /**
     * The "Spaces on this tablet" switch. Off, a Space desktop has becomes a Desktop-only Space:
     * it leaves this device with its Pinned Tabs and Folders (its tabs move to the current Space)
     * and is never changed on desktop; one only on this device becomes a Local Space. On, a
     * Desktop-only Space comes back at the next sync, and a Local Space is synced again.
     * The last Space on this device can't be switched off.
     */
    fun setOnTablet(
        spaceId: String,
        on: Boolean,
    ) {
        val options = updateSettings { it.copy(newOnDesktop = it.newOnDesktop - spaceId) }
        if (on) {
            if (spaceId in options.desktopOnly) {
                updateSettings { it.copy(desktopOnly = it.desktopOnly - spaceId) }
            } else {
                markLocal(spaceId, false)
            }
            return
        }
        val data = spaces.data
        val space = data.spaces.firstOrNull { it.id == spaceId } ?: return
        if (!isOnDesktop(space)) {
            markLocal(spaceId, true)
            return
        }
        if (data.spaces.size <= 1) return
        // Forget its last-synced records first, so its removal is never sent as a deletion.
        base.save(base.base.value.let { it.copy(entries = it.entries - recordIdsOf(space, data)) })
        updateSettings { it.copy(desktopOnly = it.desktopOnly + spaceId) }
        spaces.removeKeepingTabs(spaceId)
    }

    /** The new desktop Spaces have been shown: they're no longer marked new (they stay off). */
    fun acknowledgeNewSpaces() {
        updateSettings { it.copy(newOnDesktop = emptySet()) }
    }

    /** The "Spaces on this tablet" list: this device's Spaces in order, then the Desktop-only ones. */
    fun spaceChoices(): List<SpaceChoice> {
        val options = settings.settings.value
        val here =
            spaces.data.spaces.map { space ->
                SpaceChoice(space.id, space.name, space.icon, syncs = !space.isLocal, onTablet = true, onDesktop = isOnDesktop(space))
            }
        val away =
            desktop.view.value.spaces.filter { it.id in options.desktopOnly }.map {
                SpaceChoice(it.id, it.name, it.icon, syncs = false, onTablet = false, onDesktop = true, isNew = it.id in options.newOnDesktop)
            }
        return here + away
    }

    /** Whether desktop has [space]: it did at the last read, or the last sync left it on the server. */
    private fun isOnDesktop(space: Space): Boolean =
        desktop.view.value.spaces.any { it.id == space.id } ||
            (!space.isLocal && base.base.value.entries.containsKey(recordIdOf(space.extra, space.id)))

    /** An item's record id: the one it arrived with from Zen, else its own id. */
    private fun recordIdOf(
        extra: String?,
        id: String,
    ) = ZenOrigin.decode(extra)?.recordId ?: id

    /** Record ids of a Space and its Pinned Tabs and Folders. */
    private fun recordIdsOf(
        space: Space,
        data: pt.dweep.sideraven.SpacesData,
    ): Set<String> =
        (
            listOf(recordIdOf(space.extra, space.id)) +
                data.pinnedIn(space.id).map { recordIdOf(it.extra, it.id) } +
                data.foldersIn(space.id).map { recordIdOf(it.extra, it.id) }
        ).toSet()

    /** Turns Zen Sync off: both sides stay as they are, independent from now on. */
    fun turnOff() {
        updateSettings { it.copy(enabled = false, firstSyncDone = false) }
        base.save(SyncBase())
    }

    companion object {
        const val FROM_TABLET = "From this tablet"
        const val FROM_TABLET_ICON = "📱"

        @Volatile private var instance: ZenSync? = null

        fun get(context: Context): ZenSync =
            instance ?: synchronized(this) {
                ZenBlobs.useFiles(context)
                instance ?: ZenSync(
                    account = FenixZenAccount { context.components.backgroundServices.accountManager },
                    transport = UrlConnectionTransport(),
                    spaces = Spaces.get(context),
                    essentials = Essentials(PreferencesEssentialsRepository.get(context), Essentials.processLinks),
                    base = PreferencesSyncBaseRepository.get(context),
                    settings = PreferencesZenSyncSettingsStore.get(context),
                    fromTabletName = context.getString(org.mozilla.fenix.R.string.sideraven_zen_from_tablet),
                    desktop = PreferencesDesktopViewStore.get(context),
                    icons = pt.dweep.sideraven.IconStore.get(context),
                    sends = PreferencesDesktopSendQueue.get(context),
                    followAddress = { tabId, old, new ->
                        val components = context.components
                        // Tab work belongs on the main thread; the sync runs in the background.
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            pt.dweep.sideraven.PinnedAddressFollow.follow(components.core.store.state, tabId, old, new) { url, id ->
                                components.useCases.sessionUseCases.loadUrl(url, id)
                            }
                        }
                    },
                ).also { instance = it }
            }
    }
}

/** Merging desktop's data (as imported) back into the tablet's, keeping device-local state. */
internal object ZenLocalMerge {
    fun spaces(
        data: pt.dweep.sideraven.SpacesData,
        imported: ZenImport,
        keepLocalAccents: Boolean = false,
    ): pt.dweep.sideraven.SpacesData {
        val localIds = data.spaces.filter { it.isLocal }.map { it.id }.toSet()
        val old = data.spaces.associateBy { it.id }
        val synced =
            imported.spaces.map { space ->
                // Device-local state and edit times stay with the tablet's copy.
                old[space.id]?.let {
                    space.copy(
                        lastSelectedTabId = it.lastSelectedTabId,
                        modified = it.modified,
                        accent = if (keepLocalAccents) it.accent else space.accent,
                    )
                } ?: space
            }
        // Synced Spaces take Zen's order; Local Spaces keep their place among them.
        val queue = ArrayDeque(synced)
        val ordered =
            data.spaces.mapNotNull { space -> if (space.isLocal) space else queue.removeFirstOrNull() } + queue
        val oldPins = data.pinned.associateBy { it.id }
        val oldFolders = data.folders.associateBy { it.id }
        return data.copy(
            spaces = ordered.map { space -> if (keepLocalAccents && !space.isLocal) withSyncedAccent(space) else space },
            pinned = data.pinned.filter { it.spaceId in localIds } + imported.pinned.map { p -> oldPins[p.id]?.let { p.copy(modified = it.modified) } ?: p },
            folders = data.folders.filter { it.spaceId in localIds } + imported.folders.map { f -> oldFolders[f.id]?.let { f.copy(modified = it.modified) } ?: f },
        )
    }
}

/** Ids of everything an import puts on the tablet. */
internal fun ZenLocalMerge.idsOf(import: ZenImport): Set<String> =
    (import.spaces.map { it.id } + import.pinned.map { it.id } + import.folders.map { it.id }).toSet()

/**
 * Gives tablet Spaces, pins and Folders whose ids collide with [taken] (desktop's, e.g. after
 * Zen Sync was turned off and on again) fresh ids, dropping their old Zen origin, so both can
 * live side by side.
 */
internal fun ZenLocalMerge.rekeyCollisions(
    data: pt.dweep.sideraven.SpacesData,
    taken: Set<String>,
): pt.dweep.sideraven.SpacesData {
    val spaceMap = data.spaces.filter { it.id in taken }.associate { it.id to pt.dweep.sideraven.newPinId() }
    val pinMap = data.pinned.filter { it.id in taken }.associate { it.id to pt.dweep.sideraven.newPinId() }
    val folderMap = data.folders.filter { it.id in taken }.associate { it.id to pt.dweep.sideraven.newPinId() }
    if (spaceMap.isEmpty() && pinMap.isEmpty() && folderMap.isEmpty()) return data
    fun space(id: String) = spaceMap[id] ?: id
    fun folder(id: String?) = id?.let { folderMap[it] ?: it }
    return data.copy(
        spaces = data.spaces.map { s -> spaceMap[s.id]?.let { s.copy(id = it, extra = null) } ?: s },
        currentSpaceId = space(data.currentSpaceId),
        assignments = data.assignments.mapValues { (_, s) -> space(s) },
        pinned = data.pinned.map { p -> (pinMap[p.id]?.let { p.copy(id = it, extra = null) } ?: p).copy(spaceId = space(p.spaceId), folderId = folder(p.folderId)) },
        folders = data.folders.map { f -> (folderMap[f.id]?.let { f.copy(id = it, extra = null) } ?: f).copy(spaceId = space(f.spaceId), parentId = folder(f.parentId)) },
    )
}

/** A Space whose accent is the tablet's own: remembered as the synced accent, so it isn't sent as a change. */
internal fun withSyncedAccent(space: pt.dweep.sideraven.Space): pt.dweep.sideraven.Space {
    val origin = ZenOrigin.decode(space.extra) ?: return space
    return space.copy(extra = origin.copy(accent = space.accent).encode())
}

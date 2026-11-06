/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import mozilla.components.browser.state.state.TabSessionState
import org.json.JSONArray
import org.json.JSONObject

/** Pinned Tabs per Space; the same rule as the Essentials Cap. */

/**
 * A URL pinned to one Space. Like an Essential it keeps the URL it was
 * pinned at; unlike one it belongs to a single Space.
 *
 * @property folderId The Folder it sits in, or null for the Space's top level.
 * @property customName The user's name for it, shown instead of [title] when set.
 * @property extra Data Sideraven doesn't model (a JSON object as text), written back unchanged.
 * @property modified When the user last changed it here (epoch ms; 0 = never). Zen Sync's "later edit wins".
 */
data class PinnedTab(
    val id: String,
    val spaceId: String,
    val url: String,
    val title: String,
    val folderId: String? = null,
    val customName: String? = null,
    val extra: String? = null,
    val modified: Long = 0,
) {
    val displayName: String get() = customName?.takeIf { it.isNotBlank() } ?: title
}

/**
 * A Folder: a named, collapsible group of Pinned Tabs inside a Space. Folders from
 * Zen Sync may nest ([parentId]); on this device only top-level Folders are created.
 *
 * @property extra Data Sideraven doesn't model (e.g. a live folder's config), written back unchanged.
 */
data class Folder(
    val id: String,
    val spaceId: String,
    val name: String,
    val icon: String = DEFAULT_FOLDER_ICON,
    val parentId: String? = null,
    val extra: String? = null,
    val modified: Long = 0,
)

const val DEFAULT_FOLDER_ICON = "📁"

/** The accent a Space gets until the user picks one. */
const val DEFAULT_SPACE_ACCENT = "ember"

/**
 * A Space: a named group of tabs.
 *
 * @property icon An emoji, or the name of one of Zen's built-in icons.
 * @property accent A key into the Space accent palette.
 * @property lastSelectedTabId The tab to return to when switching back to this Space.
 * @property extra Data Sideraven doesn't model (a JSON object as text), written back unchanged.
 * @property isLocal A Local Space: never uploaded or deleted by Zen Sync.
 * @property modified When the user last changed its name, icon, colour or contents here (epoch ms).
 */
data class Space(
    val id: String,
    val name: String,
    val icon: String,
    val accent: String = DEFAULT_SPACE_ACCENT,
    val lastSelectedTabId: String? = null,
    val extra: String? = null,
    val isLocal: Boolean = false,
    val modified: Long = 0,
)

/**
 * Everything stored about Spaces.
 *
 * @property spaces In display order. Never empty once initialised.
 * @property currentSpaceId The Space the Sidebar shows.
 * @property assignments Tab id -> Space id, for normal-mode tabs. Tab ids survive restarts.
 * @property pinned Every Space's Pinned Tabs, in display order within each Space.
 * @property folders Every Space's Folders, in display order within each parent.
 * @property collapsedFolders Folders the user collapsed. Device-local.
 */
data class SpacesData(
    val spaces: List<Space>,
    val currentSpaceId: String,
    val assignments: Map<String, String> = emptyMap(),
    val pinned: List<PinnedTab> = emptyList(),
    val folders: List<Folder> = emptyList(),
    val collapsedFolders: Set<String> = emptySet(),
    val layoutModified: Long = 0,
) {
    /** [spaceId]'s Folders, in order. */
    fun foldersIn(spaceId: String): List<Folder> = folders.filter { it.spaceId == spaceId }

    /** [folderId] and every Folder nested inside it. */
    fun folderAndDescendants(folderId: String): Set<String> {
        val ids = mutableSetOf(folderId)
        var added = true
        while (added) {
            added = folders.filter { it.parentId in ids && it.id !in ids }.map { it.id }.let { ids.addAll(it) }
        }
        return ids
    }

    /** [spaceId]'s Pinned Tabs, in order. */
    fun pinnedIn(spaceId: String): List<PinnedTab> = pinned.filter { it.spaceId == spaceId }

    /** The ids of [spaceId]'s tabs among [tabs]: its Tab List and its Pinned Tabs' tabs, not Essential Tabs. */
    fun tabsIn(
        spaceId: String,
        tabs: List<TabSessionState>,
        essentialTabs: Set<String>,
    ): List<String> = tabs.filter { !it.content.private && it.id !in essentialTabs && spaceOf(it.id) == spaceId }.map { it.id }

    /** [tabsIn] without the Pinned Tabs' tabs ([pinnedTabs]): the Space's Tab List. */
    fun unpinnedTabsIn(
        spaceId: String,
        tabs: List<TabSessionState>,
        essentialTabs: Set<String>,
        pinnedTabs: Set<String>,
    ): List<String> = tabsIn(spaceId, tabs, essentialTabs).filter { it !in pinnedTabs }

    val current: Space get() = spaces.firstOrNull { it.id == currentSpaceId } ?: spaces.first()

    /** The Space [tabId] belongs to. A tab not yet assigned counts as the current Space's. */
    fun spaceOf(tabId: String): String = assignments[tabId]?.takeIf { id -> spaces.any { it.id == id } } ?: current.id

    /**
     * Which tab to select when switching to [spaceId]: its remembered tab if still open there,
     * else its newest tab; null when it has none (the caller opens a New Tab there).
     */
    fun switchTarget(
        spaceId: String,
        tabs: List<TabSessionState>,
        excludedTabIds: Set<String> = emptySet(),
    ): String? {
        val inSpace = tabs.filter { !it.content.private && it.id !in excludedTabIds && spaceOf(it.id) == spaceId }
        val remembered = spaces.firstOrNull { it.id == spaceId }?.lastSelectedTabId
        return inSpace.firstOrNull { it.id == remembered }?.id ?: inSpace.lastOrNull()?.id
    }

    companion object {
        const val HOME_NAME = "Home"
        const val HOME_ICON = "🏠"

        /** First run: one Space, "Home", holding every existing tab. */
        fun initial(homeName: String = HOME_NAME) =
            newPinId().let { id -> SpacesData(listOf(Space(id, homeName, HOME_ICON)), currentSpaceId = id) }
    }
}

/** Persisted Spaces. [data] is null until the first initialisation. */
interface SpacesRepository {
    val data: StateFlow<SpacesData?>

    fun save(data: SpacesData)
}

class InMemorySpacesRepository(initial: SpacesData? = null) : SpacesRepository {
    private val state = MutableStateFlow(initial)
    override val data: StateFlow<SpacesData?> = state.asStateFlow()

    override fun save(data: SpacesData) {
        state.value = data
    }
}

/** JSON form of [SpacesData]. */
internal object SpacesCodec {
    fun encode(data: SpacesData): String =
        JSONObject()
            .put("version", 1)
            .put("current", data.currentSpaceId)
            .put(
                "spaces",
                JSONArray().apply {
                    data.spaces.forEach { space ->
                        put(
                            JSONObject()
                                .put("id", space.id)
                                .put("name", space.name)
                                .put("icon", space.icon)
                                .put("accent", space.accent)
                                .put("local", space.isLocal)
                                .put("modified", space.modified)
                                .apply {
                                    space.lastSelectedTabId?.let { put("lastSelected", it) }
                                    space.extra?.let { put("extra", it) }
                                },
                        )
                    }
                },
            ).put("assignments", JSONObject(data.assignments as Map<*, *>))
            .put("collapsedFolders", JSONArray(data.collapsedFolders.toList()))
            .put("layoutModified", data.layoutModified)
            .put(
                "folders",
                JSONArray().apply {
                    data.folders.forEach { folder ->
                        put(
                            JSONObject()
                                .put("id", folder.id)
                                .put("space", folder.spaceId)
                                .put("name", folder.name)
                                .put("icon", folder.icon)
                                .put("modified", folder.modified)
                                .apply {
                                    folder.parentId?.let { put("parent", it) }
                                    folder.extra?.let { put("extra", it) }
                                },
                        )
                    }
                },
            )
            .put(
                "pinned",
                JSONArray().apply {
                    data.pinned.forEach { pin ->
                        put(
                            JSONObject()
                                .put("id", pin.id)
                                .put("space", pin.spaceId)
                                .put("url", pin.url)
                                .put("title", pin.title)
                                .put("modified", pin.modified)
                                .apply {
                                    pin.folderId?.let { put("folder", it) }
                                    pin.customName?.let { put("customName", it) }
                                    pin.extra?.let { put("extra", it) }
                                },
                        )
                    }
                },
            ).toString()

    fun decode(raw: String?): SpacesData? {
        if (raw == null) return null
        return runCatching {
            val json = JSONObject(raw)
            val list = json.getJSONArray("spaces")
            val spaces =
                (0 until list.length()).map { i ->
                    val item = list.getJSONObject(i)
                    Space(
                        id = item.getString("id"),
                        name = item.optString("name"),
                        icon = item.optString("icon"),
                        accent = item.optString("accent").ifBlank { DEFAULT_SPACE_ACCENT },
                        lastSelectedTabId = item.optString("lastSelected").ifBlank { null },
                        extra = item.optString("extra").ifBlank { null },
                        isLocal = item.optBoolean("local", false),
                        modified = item.optLong("modified", 0),
                    )
                }
            val assignmentsJson = json.optJSONObject("assignments") ?: JSONObject()
            val assignments = assignmentsJson.keys().asSequence().associateWith { assignmentsJson.getString(it) }
            val pinnedJson = json.optJSONArray("pinned") ?: JSONArray()
            val pinned =
                (0 until pinnedJson.length()).map { i ->
                    val item = pinnedJson.getJSONObject(i)
                    PinnedTab(
                        id = item.getString("id"),
                        spaceId = item.getString("space"),
                        url = item.getString("url"),
                        title = item.optString("title"),
                        folderId = item.optString("folder").ifBlank { null },
                        customName = item.optString("customName").ifBlank { null },
                        extra = item.optString("extra").ifBlank { null },
                        modified = item.optLong("modified", 0),
                    )
                }
            val foldersJson = json.optJSONArray("folders") ?: JSONArray()
            val folders =
                (0 until foldersJson.length()).map { i ->
                    val item = foldersJson.getJSONObject(i)
                    Folder(
                        id = item.getString("id"),
                        spaceId = item.getString("space"),
                        name = item.optString("name"),
                        icon = item.optString("icon").ifBlank { DEFAULT_FOLDER_ICON },
                        parentId = item.optString("parent").ifBlank { null },
                        extra = item.optString("extra").ifBlank { null },
                        modified = item.optLong("modified", 0),
                    )
                }
            val collapsedJson = json.optJSONArray("collapsedFolders") ?: JSONArray()
            val collapsed = (0 until collapsedJson.length()).map { collapsedJson.getString(it) }.toSet()
            SpacesData(
                spaces,
                json.optString("current").ifBlank { spaces.first().id },
                assignments,
                pinned,
                folders,
                collapsed,
                layoutModified = json.optLong("layoutModified", 0),
            )
                .takeIf { spaces.isNotEmpty() }
        }.getOrNull()
    }
}

/** SharedPreferences-backed repository. One per process, see [get]. */
class PreferencesSpacesRepository private constructor(context: Context) : SpacesRepository {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(SpacesCodec.decode(prefs.getString(KEY, null)))
    override val data: StateFlow<SpacesData?> = state.asStateFlow()

    override fun save(data: SpacesData) {
        prefs.edit().putString(KEY, SpacesCodec.encode(data)).apply()
        state.value = data
    }

    companion object {
        private const val PREFS = "sideraven_spaces"
        private const val KEY = "spaces"

        @Volatile private var instance: PreferencesSpacesRepository? = null

        fun get(context: Context): PreferencesSpacesRepository =
            instance ?: synchronized(this) {
                instance ?: allowDiskReads { PreferencesSpacesRepository(context.applicationContext) }.also { instance = it }
            }
    }
}

/** Spaces operations. */
class Spaces(
    private val repository: SpacesRepository,
    private val homeName: String = SpacesData.HOME_NAME,
    private val pinLinks: MutableStateFlow<Map<String, String>> = MutableStateFlow(emptyMap()),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Pinned Tab id -> its open tab id. In memory, like the tabs it points at. */
    val pinnedLinks: StateFlow<Map<String, String>> get() = pinLinks

    /**
     * Guards read-modify-write updates: the Sidebar changes Spaces on the main thread while the
     * browser-store middleware does on whichever thread dispatched the action.
     */
    private val lock = Any()

    /** Current data, creating the Home Space on first use. */
    val data: SpacesData
        get() = repository.data.value ?: synchronized(lock) {
            repository.data.value ?: SpacesData.initial(homeName).also(repository::save)
        }

    val flow: StateFlow<SpacesData?> get() = repository.data

    private fun update(transform: (SpacesData) -> SpacesData) = save(transform, stamp = true)

    /** The one save path: [stamp] records edit times (user edits do; Zen Sync's changes don't). */
    private fun save(
        transform: (SpacesData) -> SpacesData,
        stamp: Boolean,
    ) {
        synchronized(lock) {
            val current = data
            val updated = transform(current)
            if (updated != current) repository.save(if (stamp) SyncStamps.stamp(current, updated, clock()) else updated)
        }
    }

    /**
     * Applies changes that came from Zen Sync. Unlike user edits they're saved without edit times:
     * they aren't the tablet's changes.
     */
    fun applyRemote(transform: (SpacesData) -> SpacesData) = save(transform, stamp = false)

    /** A new normal tab joins its parent's Space when it has one, else the current Space. */
    fun assignNewTab(
        tab: TabSessionState,
        parent: TabSessionState?,
    ) {
        if (tab.content.private) return
        update { data ->
            val space = parent?.takeIf { !it.content.private }?.let { data.spaceOf(it.id) } ?: data.current.id
            data.copy(assignments = data.assignments + (tab.id to space))
        }
    }

    /**
     * Gives every unassigned normal tab in [tabs] to the current Space and, when [prune], forgets
     * tabs that no longer exist. Run after a session restore (prune) and after bulk restores.
     */
    fun adopt(
        tabs: List<TabSessionState>,
        prune: Boolean = false,
    ) {
        update { data ->
            val live = tabs.filterNot { it.content.private }.map { it.id }.toSet()
            val kept = if (prune) data.assignments.filterKeys { it in live } else data.assignments
            val adopted = (live - kept.keys).associateWith { data.current.id }
            data.copy(assignments = kept + adopted)
        }
    }

    /** Remembers [tabId] as its Space's last selected tab. */
    fun rememberSelection(tabId: String) {
        update { data ->
            val spaceId = data.spaceOf(tabId)
            data.copy(spaces = data.spaces.map { if (it.id == spaceId) it.copy(lastSelectedTabId = tabId) else it })
        }
    }

    /** Moves a tab to another Space (the current Space stays current). */
    fun moveTab(
        tabId: String,
        spaceId: String,
    ) {
        update { data ->
            if (data.spaces.none { it.id == spaceId }) return@update data
            data.copy(assignments = data.assignments + (tabId to spaceId))
        }
    }

    /** Whether [tab] may be pinned to the current Space right now. */
    fun canPinToSpace(tab: TabSessionState?): PinResult {
        if (tab == null || tab.content.private) return PinResult.PrivateRefused
        val pins = data.pinnedIn(data.current.id)
        return when {
            pins.any { it.url == tab.content.url } -> PinResult.AlreadyPinned
            else -> PinResult.Pinned
        }
    }

    /** Pins [tab] to the current Space and links it as that Pinned Tab's tab. */
    fun pinToSpace(
        tab: TabSessionState,
        atIndex: Int? = null,
    ): PinResult {
        val result = canPinToSpace(tab)
        if (result != PinResult.Pinned) return result
        val spaceId = data.current.id
        val pin = PinnedTab(newPinId(), spaceId, tab.content.url, tab.content.title.ifBlank { tab.content.url })
        update { data ->
            val inSpace = data.pinnedIn(spaceId)
            val others = data.pinned.filterNot { it.spaceId == spaceId }
            val placed = inSpace.toMutableList().apply { add((atIndex ?: size).coerceIn(0, size), pin) }
            data.copy(pinned = others + placed, assignments = data.assignments + (tab.id to spaceId))
        }
        linkPin(pin.id, tab.id)
        return PinResult.Pinned
    }

    /**
     * Pins a URL to the current Space (an Essential moving into the Space), linking [tabId] when
     * its tab is open.
     */
    fun pinUrl(
        url: String,
        title: String,
        tabId: String? = null,
        customName: String? = null,
    ): PinResult {
        val spaceId = data.current.id
        val pins = data.pinnedIn(spaceId)
        if (pins.any { it.url == url }) return PinResult.AlreadyPinned
        val pin = PinnedTab(newPinId(), spaceId, url, title, customName = customName)
        update { data ->
            val assignments = tabId?.let { data.assignments + (it to spaceId) } ?: data.assignments
            data.copy(pinned = data.pinned + pin, assignments = assignments)
        }
        tabId?.let { linkPin(pin.id, it) }
        return PinResult.Pinned
    }

    /** Moves a Pinned Tab to [targetPinId]'s place, joining that pin's Folder. */
    fun movePin(
        pinId: String,
        targetPinId: String,
    ) {
        if (pinId == targetPinId) return
        update { data ->
            val list = data.pinned.toMutableList()
            val from = list.indexOfFirst { it.id == pinId }
            val target = list.firstOrNull { it.id == targetPinId } ?: return@update data
            if (from == -1 || target.spaceId != list[from].spaceId) return@update data
            val pin = list.removeAt(from).copy(folderId = target.folderId)
            val to = list.indexOfFirst { it.id == targetPinId }
            // Dragging down lands after the target; dragging up lands before it.
            list.add(if (from <= to) to + 1 else to, pin)
            data.copy(pinned = list)
        }
    }

    /** Unpins a Pinned Tab; its tab, if open, stays in the Space's Tab List. */
    fun unpinFromSpace(pinId: String) {
        update { data -> data.copy(pinned = data.pinned.filterNot { it.id == pinId }) }
        pinLinks.value = pinLinks.value - pinId
    }

    /** Adds a top-level Folder to the current Space; returns it. */
    fun createFolder(
        name: String,
        icon: String = DEFAULT_FOLDER_ICON,
    ): Folder {
        val folder = Folder(newPinId(), data.current.id, name.trim(), icon)
        update { data -> data.copy(folders = data.folders + folder) }
        return folder
    }

    /** Changes a Folder's name and icon. */
    fun editFolder(
        folderId: String,
        name: String,
        icon: String,
    ) {
        if (name.isBlank()) return
        update { data -> data.copy(folders = data.folders.map { if (it.id == folderId) it.copy(name = name.trim(), icon = icon) else it }) }
    }

    /** Moves a Folder one step up ([step] -1) or down (+1) among its siblings. */
    fun moveFolder(
        folderId: String,
        step: Int,
    ) {
        update { data ->
            val folder = data.folders.firstOrNull { it.id == folderId } ?: return@update data
            val siblings = data.folders.filter { it.spaceId == folder.spaceId && it.parentId == folder.parentId }
            val index = siblings.indexOf(folder)
            val target = siblings.getOrNull(index + step) ?: return@update data
            val list = data.folders.toMutableList()
            val a = list.indexOf(folder)
            val b = list.indexOf(target)
            list[a] = target
            list[b] = folder
            data.copy(folders = list)
        }
    }

    /** Moves a Folder to [targetFolderId]'s place among its siblings (same parent only). */
    fun moveFolderTo(
        folderId: String,
        targetFolderId: String,
    ) {
        if (folderId == targetFolderId) return
        update { data ->
            val list = data.folders.toMutableList()
            val from = list.indexOfFirst { it.id == folderId }
            val target = list.firstOrNull { it.id == targetFolderId } ?: return@update data
            if (from == -1 || list[from].parentId != target.parentId || list[from].spaceId != target.spaceId) return@update data
            val folder = list.removeAt(from)
            val to = list.indexOfFirst { it.id == targetFolderId }
            list.add(if (from <= to) to + 1 else to, folder)
            data.copy(folders = list)
        }
    }

    /**
     * Deletes a Folder and the Folders nested in it. With [keepPins], their Pinned Tabs move up to
     * the deleted Folder's parent; otherwise they're unpinned.
     */
    fun deleteFolder(
        folderId: String,
        keepPins: Boolean,
    ) {
        update { data ->
            val folder = data.folders.firstOrNull { it.id == folderId } ?: return@update data
            val gone = data.folderAndDescendants(folderId)
            val pinned =
                if (keepPins) {
                    data.pinned.map { if (it.folderId in gone) it.copy(folderId = folder.parentId) else it }
                } else {
                    data.pinned.filterNot { it.folderId in gone }
                }
            data.copy(folders = data.folders.filterNot { it.id in gone }, pinned = pinned, collapsedFolders = data.collapsedFolders - gone)
        }
    }

    /** Puts a Pinned Tab into a Folder of its Space, or back to the top level (null). */
    fun moveIntoFolder(
        pinId: String,
        folderId: String?,
    ) {
        update { data ->
            val pin = data.pinned.firstOrNull { it.id == pinId } ?: return@update data
            if (folderId != null && data.folders.none { it.id == folderId && it.spaceId == pin.spaceId }) return@update data
            data.copy(pinned = data.pinned.map { if (it.id == pinId) it.copy(folderId = folderId) else it })
        }
    }

    /** Collapses or expands a Folder (remembered on this device). */
    fun toggleFolder(folderId: String) {
        update { data ->
            val collapsed = if (folderId in data.collapsedFolders) data.collapsedFolders - folderId else data.collapsedFolders + folderId
            data.copy(collapsedFolders = collapsed)
        }
    }

    /** Gives a Pinned Tab a custom name; null or blank goes back to its title. */
    fun renamePin(
        pinId: String,
        name: String?,
    ) {
        val customName = name?.trim()?.ifBlank { null }
        update { data -> data.copy(pinned = data.pinned.map { if (it.id == pinId) it.copy(customName = customName) else it }) }
    }

    fun linkPin(
        pinId: String,
        tabId: String,
    ) {
        pinLinks.value = pinLinks.value + (pinId to tabId)
    }

    /**
     * After session restore: link each Pinned Tab without a live tab to the first unlinked,
     * non-private restored tab in its Space whose URL matches exactly. [excluded] are tabs taken
     * by Essentials.
     */
    fun relinkPinned(
        tabs: List<TabSessionState>,
        excluded: Set<String> = emptySet(),
    ) {
        val current = data
        val updated = pinLinks.value.filterValues { id -> tabs.any { it.id == id } }.toMutableMap()
        current.pinned.forEach { pin ->
            if (pin.id in updated) return@forEach
            tabs.firstOrNull { tab ->
                !tab.content.private && tab.content.url == pin.url && tab.id !in updated.values && tab.id !in excluded &&
                    current.spaceOf(tab.id) == pin.spaceId
            }?.let { updated[pin.id] = it.id }
        }
        pinLinks.value = updated
    }

    /** Makes [spaceId] current. */
    fun switchTo(spaceId: String) {
        update { data -> if (data.spaces.any { it.id == spaceId }) data.copy(currentSpaceId = spaceId) else data }
    }

    /** Adds a Space at the end and makes it current; returns it. [local] makes it a Local Space. */
    fun create(
        name: String,
        icon: String,
        accent: String = DEFAULT_SPACE_ACCENT,
        local: Boolean = false,
    ): Space {
        val space = Space(newPinId(), name.trim(), icon, accent, isLocal = local)
        update { data -> data.copy(spaces = data.spaces + space, currentSpaceId = space.id) }
        return space
    }

    /** Changes a Space's name, icon and accent. */
    fun edit(
        spaceId: String,
        name: String,
        icon: String,
        accent: String,
    ) {
        if (name.isBlank()) return
        update { data ->
            data.copy(spaces = data.spaces.map { if (it.id == spaceId) it.copy(name = name.trim(), icon = icon, accent = accent) else it })
        }
    }

    /** Marks a Space as a Local Space (never synced) or back to synced. */
    fun setLocal(
        spaceId: String,
        local: Boolean,
    ) {
        update { data -> data.copy(spaces = data.spaces.map { if (it.id == spaceId) it.copy(isLocal = local) else it }) }
    }

    /** Moves a Space to [toIndex] in the Space icon row. */
    fun move(
        spaceId: String,
        toIndex: Int,
    ) {
        update { data ->
            val list = data.spaces.toMutableList()
            val index = list.indexOfFirst { it.id == spaceId }
            if (index == -1) return@update data
            val space = list.removeAt(index)
            list.add(toIndex.coerceIn(0, list.size), space)
            data.copy(spaces = list)
        }
    }

    /**
     * Deletes a Space; the current Space moves to its neighbour. Returns the data from before the
     * delete, for Undo, or null when nothing was deleted (the last Space can't be).
     * Its tabs keep their assignment so Undo brings them back into it.
     */
    fun delete(spaceId: String): SpacesData? = synchronized(lock) { deleteLocked(spaceId) }

    /**
     * Removes a Space with its Pinned Tabs and Folders, moving its tabs to the Space that becomes
     * current (Zen Sync, a Desktop-only Space). Not an edit: nothing is stamped. False for the last Space.
     */
    fun removeKeepingTabs(spaceId: String): Boolean =
        synchronized(lock) {
            val before = deleteLocked(spaceId) ?: return@synchronized false
            val current = data.currentSpaceId
            repository.save(data.copy(assignments = before.assignments.mapValues { (_, space) -> if (space == spaceId) current else space }))
            true
        }

    private fun deleteLocked(spaceId: String): SpacesData? {
        val before = data
        val index = before.spaces.indexOfFirst { it.id == spaceId }
        if (index == -1 || before.spaces.size <= 1) return null
        val remaining = before.spaces.filterNot { it.id == spaceId }
        val current =
            if (before.currentSpaceId == spaceId) remaining[(index - 1).coerceAtLeast(0)].id else before.currentSpaceId
        // Its Pinned Tabs and Folders go with it (Undo restores them from the snapshot).
        repository.save(
            before.copy(
                spaces = remaining,
                currentSpaceId = current,
                pinned = before.pinned.filterNot { it.spaceId == spaceId },
                folders = before.folders.filterNot { it.spaceId == spaceId },
            ),
        )
        return before
    }

    /** Undo: puts back the data [delete] returned. */
    fun restore(snapshot: SpacesData) {
        synchronized(lock) { repository.save(snapshot.copy(assignments = data.assignments + snapshot.assignments)) }
    }

    companion object {
        /** The key an Essential's Unloaded URL is kept under. */
        fun essentialKey(url: String) = "essential:$url"

        /** Pinned Tab links are per process, like the tabs they point at. */
        val processPinLinks = MutableStateFlow<Map<String, String>>(emptyMap())

        /** Whether this process has already re-linked restored tabs to Pinned Tabs. */
        @Volatile var restoreRelinked = false

        @Volatile private var instance: Spaces? = null

        /** The app's Spaces, shared by the Sidebar and the browser-store middleware. */
        fun get(context: Context): Spaces =
            instance ?: synchronized(this) {
                instance ?: Spaces(
                    PreferencesSpacesRepository.get(context),
                    homeName = context.getString(org.mozilla.fenix.R.string.sideraven_space_home),
                    pinLinks = processPinLinks,
                ).also { instance = it }
            }
    }
}

/**
 * Stamps edit times for Zen Sync: whatever the user changed in a Spaces update gets `modified`
 * set to now. Device-local state (current Space, assignments, last selected tabs, collapsed
 * Folders, Unloaded URLs) is never synced, so changing it stamps nothing.
 */
internal object SyncStamps {
    fun stamp(
        before: SpacesData,
        after: SpacesData,
        now: Long,
    ): SpacesData {
        val oldSpaces = before.spaces.associateBy { it.id }
        val oldPins = before.pinned.associateBy { it.id }
        val oldFolders = before.folders.associateBy { it.id }
        // A Space's contents (its pins and Folders, and their order) are part of its record.
        val touchedSpaces = mutableSetOf<String>()
        val pinned =
            after.pinned.map { pin ->
                val old = oldPins[pin.id]
                if (old == null || old.copy(modified = 0) != pin.copy(modified = 0)) {
                    touchedSpaces += pin.spaceId
                    old?.spaceId?.let(touchedSpaces::add)
                    pin.copy(modified = now)
                } else {
                    pin
                }
            }
        val folders =
            after.folders.map { folder ->
                val old = oldFolders[folder.id]
                if (old == null || old.copy(modified = 0) != folder.copy(modified = 0)) {
                    touchedSpaces += folder.spaceId
                    folder.copy(modified = now)
                } else {
                    folder
                }
            }
        (before.pinned.map { it.id } - after.pinned.map { it.id }.toSet()).forEach { id -> oldPins[id]?.spaceId?.let(touchedSpaces::add) }
        (before.folders.map { it.id } - after.folders.map { it.id }.toSet()).forEach { id -> oldFolders[id]?.spaceId?.let(touchedSpaces::add) }
        after.spaces.map { it.id }.forEach { id ->
            if (before.pinnedIn(id).map { it.id } != after.pinnedIn(id).map { it.id } ||
                before.foldersIn(id).map { it.id } != after.foldersIn(id).map { it.id }
            ) {
                touchedSpaces += id
            }
        }
        val spaces =
            after.spaces.map { space ->
                val old = oldSpaces[space.id]
                val changed = old == null || synced(old) != synced(space) || space.id in touchedSpaces
                if (changed) space.copy(modified = now) else space
            }
        val layoutModified = if (before.spaces.map { it.id } != after.spaces.map { it.id }) now else after.layoutModified
        return after.copy(spaces = spaces, pinned = pinned, folders = folders, layoutModified = layoutModified)
    }

    /** The parts of a Space that Zen Sync carries. */
    private fun synced(space: Space) = space.copy(lastSelectedTabId = null, modified = 0, isLocal = false)
}

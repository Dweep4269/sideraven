/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.sync

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import pt.dweep.sideraven.allowDiskReads

/** A Space as desktop has it, for naming Desktop-only Spaces. */
data class DesktopSpace(
    val id: String,
    val name: String,
    val icon: String,
)

/** One of desktop's open tabs (a Desktop Tab). */
data class DesktopTab(
    val url: String,
    val title: String,
    val id: String? = null,
    val isSending: Boolean = false,
)

/**
 * What Zen desktop had at the last sync, kept on this device only and never uploaded: its
 * Spaces in desktop's order, and each Space's open tabs (Desktop Tabs) when desktop shares them.
 */
data class DesktopView(
    val spaces: List<DesktopSpace> = emptyList(),
    val tabs: Map<String, List<DesktopTab>> = emptyMap(),
) {
    /** Whether desktop shares its open (normal) tabs (Zen's `zen.spaces-sync.normal-tabs`). */
    val sharesOpenTabs: Boolean get() = tabs.isNotEmpty()

    fun encode(): String =
        JSONObject()
            .put("spaces", JSONArray(spaces.map { JSONObject().put("id", it.id).put("name", it.name).put("icon", it.icon) }))
            .put(
                "tabs",
                JSONObject().apply {
                    tabs.forEach { (space, list) -> put(space, JSONArray(list.map { JSONObject().put("url", it.url).put("title", it.title).put("id", it.id ?: JSONObject.NULL) })) }
                },
            )
            .toString()

    companion object {
        fun of(snapshot: ZenSnapshot): DesktopView {
            val order = snapshot.layout?.spaces.orEmpty()
            val spaces =
                snapshot.records.filterIsInstance<ZenSpace>()
                    .sortedBy { space -> order.rank(space.uuid) }
                    .map { DesktopSpace(it.uuid, it.name.orEmpty(), it.icon.orEmpty()) }
            // Open tabs in each Space's own order (its `children`), unlisted ones after.
            val children = snapshot.records.filterIsInstance<ZenSpace>().associate { it.uuid to it.children }
            val tabs =
                snapshot.records.filterIsInstance<ZenTab>()
                    .filter { it.isNormalTab && !it.isEssential && it.workspaceUuid != null && it.url.isNotBlank() }
                    .groupBy { it.workspaceUuid!! }
                    .mapValues { (space, records) ->
                        val order = children[space].orEmpty()
                        records
                            .sortedBy { record -> order.rank(record.tabId) }
                            .map { DesktopTab(it.url, it.title?.takeIf { title -> title.isNotBlank() } ?: it.url, id = it.tabId) }
                    }
            return DesktopView(spaces, tabs)
        }

        fun decode(text: String?): DesktopView {
            val json = text?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return DesktopView()
            val spaces = json.optJSONArray("spaces") ?: JSONArray()
            val tabs = json.optJSONObject("tabs") ?: JSONObject()
            return DesktopView(
                (0 until spaces.length()).mapNotNull { spaces.optJSONObject(it) }.map {
                    DesktopSpace(it.optString("id"), it.optString("name"), it.optString("icon"))
                },
                tabs.keys().asSequence().associateWith { space ->
                    val list = tabs.optJSONArray(space) ?: JSONArray()
                    (0 until list.length()).mapNotNull { list.optJSONObject(it) }.map {
                        DesktopTab(it.optString("url"), it.optString("title"), id = ZenWire.string(it, "id"))
                    }
                },
            )
        }
    }
}

/** Shown immediately, including while a send is waiting for the current sync's mutex. */
data class DesktopSendPreview(
    val requestId: String,
    val spaceId: String,
    val url: String,
    val title: String,
    val recordId: String? = null,
)

/** Pending rows stay first until fully acknowledged; partial server records cannot duplicate them. */
fun DesktopView.withPendingSends(sending: List<DesktopSendPreview>): Map<String, List<DesktopTab>> {
    val pendingBySpace = sending.groupBy { it.spaceId }
    return (tabs.keys + pendingBySpace.keys).associateWith { space ->
        val confirmed = tabs[space].orEmpty()
        val pending = pendingBySpace[space].orEmpty()
            .distinctBy { it.recordId ?: it.requestId }
            .map { DesktopTab(it.url, it.title, id = it.recordId, isSending = true) }
        val pendingIds = pending.mapNotNull { it.id }.toSet()
        pending + confirmed.filterNot { it.id != null && it.id in pendingIds }
    }
}

/** [id]'s place in this order; unlisted ids go last. */
private fun List<String>.rank(id: String) = indexOf(id).let { if (it == -1) Int.MAX_VALUE else it }

interface DesktopViewStore {
    val view: StateFlow<DesktopView>

    fun save(view: DesktopView)
}

class InMemoryDesktopViewStore(initial: DesktopView = DesktopView()) : DesktopViewStore {
    private val state = MutableStateFlow(initial)
    override val view: StateFlow<DesktopView> = state.asStateFlow()

    override fun save(view: DesktopView) {
        state.value = view
    }
}

class PreferencesDesktopViewStore private constructor(context: Context) : DesktopViewStore {
    private val prefs = context.getSharedPreferences("sideraven_zen_desktop_view", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(DesktopView.decode(prefs.getString(KEY, null)))
    override val view: StateFlow<DesktopView> = state.asStateFlow()

    override fun save(view: DesktopView) {
        if (view == state.value) return
        prefs.edit().putString(KEY, view.encode()).apply()
        state.value = view
    }

    companion object {
        private const val KEY = "view"

        @Volatile private var instance: PreferencesDesktopViewStore? = null

        fun get(context: Context): PreferencesDesktopViewStore =
            instance ?: synchronized(this) {
                instance ?: allowDiskReads { PreferencesDesktopViewStore(context.applicationContext) }.also { instance = it }
            }
    }
}

/**
 * One row of Zen Sync's "Spaces on this tablet" list.
 *
 * @property syncs The switch: the Space is shared with desktop (on this tablet and synced).
 * @property onTablet Whether the tablet has the Space (false for a Desktop-only Space).
 * @property onDesktop Whether desktop had it at the last sync.
 * @property isNew A Space desktop added that the user hasn't decided about yet.
 */
data class SpaceChoice(
    val id: String,
    val name: String,
    val icon: String,
    val syncs: Boolean,
    val onTablet: Boolean,
    val onDesktop: Boolean,
    val isNew: Boolean = false,
)

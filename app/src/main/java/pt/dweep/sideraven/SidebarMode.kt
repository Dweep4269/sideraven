/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import org.json.JSONObject

/** The Sidebar Modes. */
enum class SidebarMode {
    Persistent,
    Collapsed,
    AutoHide,
    ;

    companion object {
        /** Modes the Toggle cycles through at [tier], in cycle order. */
        fun available(tier: SidebarTier): List<SidebarMode> =
            when (tier) {
                SidebarTier.Tablet -> listOf(Persistent, Collapsed, AutoHide)
                SidebarTier.LargePhone -> listOf(Collapsed, AutoHide)
                SidebarTier.Phone -> listOf(Collapsed, AutoHide)
                SidebarTier.None -> emptyList()
            }

        /** First-launch default: Persistent on Tablet Tier, Collapsed on Large Phone Tier. */
        fun default(tier: SidebarTier): SidebarMode? =
            when (tier) {
                SidebarTier.Tablet -> Persistent
                SidebarTier.LargePhone -> Collapsed
                SidebarTier.Phone -> AutoHide
                SidebarTier.None -> null
            }

        /**
         * The mode actually shown. A chosen mode the tier can't show falls back to Collapsed
         * *without* touching the stored choice, so it comes back when the window grows.
         */
        fun effective(
            chosen: SidebarMode?,
            tier: SidebarTier,
        ): SidebarMode? {
            val available = available(tier)
            return when {
                available.isEmpty() -> null
                chosen == null -> default(tier)
                chosen in available -> chosen
                Collapsed in available -> Collapsed
                else -> available.first()
            }
        }

        /** What the Toggle switches to from [current] at [tier]; null when there's nothing to cycle. */
        fun next(
            current: SidebarMode?,
            tier: SidebarTier,
        ): SidebarMode? {
            val available = available(tier)
            if (current == null || available.size < 2) return null
            return available[(available.indexOf(current) + 1) % available.size]
        }
    }
}

/** Which edge the Sidebar docks on (Sideraven Settings). */
enum class SidebarSide { Left, Right }

/** Which of Android's system bars Sideraven hides, for a full-screen browser (Sideraven Settings). Opt-in. */
enum class FullScreen { Off, StatusBar, Both }

/** Where new tabs appear in the Tab List (Sideraven Settings). */
enum class NewTabPosition { Top, Bottom }

/**
 * Tab List Direction: whether the Tab List starts under the Pinned Tabs and grows down, or sits on the
 * bottom edge, by the address bar, and grows up (its Inactive and Desktop Tabs sections then above the tabs).
 */
enum class TabListDirection { TopDown, BottomUp }

/** The Sidebar's width in Persistent Mode (Sideraven Settings). */
enum class SidebarWidth(
    val dp: Int,
) {
    Compact(240),
    Standard(280),
    Wide(340),
}

/** How tall tab rows are (Sideraven Settings): normal tabs, and Pinned Tabs. */
enum class TabRows(
    val rowDp: Int,
    val pinnedDp: Int,
) {
    Comfortable(48, 44),
    Compact(40, 36),
}

/** How many Essentials sit in a row of the grid (Sideraven Settings); Auto adapts to the count. */
enum class EssentialsPerRow(
    val columns: Int?,
) {
    Auto(null),
    Three(3),
    Four(4),
    Five(5),
}

/** How much the Sidebar animates (Sideraven Settings). */
enum class AnimationLevel { Full, Reduced, Off }

/** Which tab rows show a Tab Close Button (Sideraven Settings). */
enum class CloseButtons { SelectedTab, EveryTab }

/** Where the page's address lives. */
enum class AddressBarLayout {
    Top,
    Sidebar,
    ;

    /** The layout the layout button switches to. */
    val other: AddressBarLayout get() = if (this == Top) Sidebar else Top
}

/** How the Sidebar presents Spaces above their Pinned Tabs. */
enum class SpacesShown {
    /** The icon row of 1.2: every Space an icon, tap to switch. */
    IconRow,

    /** The Space Header: the current Space's name in one row, swipe to switch, tap the name to fold. */
    Header,
}

/** What New Tab opens. */
enum class NewTabBehaviour { Search, HomePage, Blank }

/**
 * One device-local setting: its current [value], a [flow] to watch, and [set] to change it.
 * Declared once in [SidebarModeStore], stored through a [SettingsBackend].
 */
class Setting<T> internal constructor(
    initial: T,
    private val save: (T) -> Unit,
) {
    private val state = MutableStateFlow(initial)
    val flow: StateFlow<T> = state.asStateFlow()
    val value: T get() = state.value

    fun set(value: T) {
        save(value)
        state.value = value
    }
}

/** Where [Setting]s are kept: SharedPreferences on a device, a map in tests. */
interface SettingsBackend {
    fun getBoolean(
        key: String,
        default: Boolean,
    ): Boolean

    fun putBoolean(
        key: String,
        value: Boolean,
    )

    fun getString(key: String): String?

    fun putString(
        key: String,
        value: String,
    )
}

/** A [SettingsBackend] in memory, for tests and previews. */
class InMemorySettingsBackend : SettingsBackend {
    private val values = mutableMapOf<String, Any>()

    override fun getBoolean(
        key: String,
        default: Boolean,
    ) = values[key] as? Boolean ?: default

    override fun putBoolean(
        key: String,
        value: Boolean,
    ) {
        values[key] = value
    }

    override fun getString(key: String) = values[key] as? String

    override fun putString(
        key: String,
        value: String,
    ) {
        values[key] = value
    }
}

/** A [SettingsBackend] over SharedPreferences. */
class PreferencesSettingsBackend(
    private val prefs: android.content.SharedPreferences,
) : SettingsBackend {
    override fun getBoolean(
        key: String,
        default: Boolean,
    ) = prefs.getBoolean(key, default)

    override fun putBoolean(
        key: String,
        value: Boolean,
    ) = prefs.edit().putBoolean(key, value).apply()

    override fun getString(key: String): String? = prefs.getString(key, null)

    override fun putString(
        key: String,
        value: String,
    ) = prefs.edit().putString(key, value).apply()
}

/**
 * The Sidebar's device-local settings (Sideraven Settings); never synced. Each is one
 * declaration: its stored key and its default.
 */
open class SidebarModeStore(
    private val backend: SettingsBackend,
) {
    /**
     * Every setting by its stored key: how to read it as a JSON value, and how to set it from one
     * (false when the value doesn't fit). Declared first: the settings below register as they're made.
     */
    private val registry = linkedMapOf<String, Entry>()

    /** How a setting reads as a JSON value and is set from one; [inBackup] false for remembered UI state. */
    private class Entry(
        val read: () -> Any?,
        val write: (Any) -> Boolean,
        val inBackup: Boolean,
    )

    /** The user's chosen mode, or null before they've chosen one. */
    val chosenMode: Setting<SidebarMode?> =
        Setting(backend.getString(KEY_MODE)?.let { stored -> SidebarMode.entries.firstOrNull { it.name == stored } }) { mode ->
            mode?.let { backend.putString(KEY_MODE, it.name) }
        }

    init {
        register(KEY_MODE, { chosenMode.value?.name }) { value -> chosenMode.setFrom(SidebarMode.entries.firstOrNull { it.name == value }) }
    }

    /** Whether the Swipe Zone is on. Off gives the whole left edge back to system back. */
    val swipeZoneEnabled = flag(KEY_SWIPE_ZONE, true)

    /** The "Show inactive tabs" Sidebar setting. */
    val showInactiveSection = flag(KEY_SHOW_INACTIVE, true)

    /** Whether the Inactive Tabs Section is expanded (remembered; collapsed by default). */
    val inactiveExpanded = flag(KEY_INACTIVE_EXPANDED, false, inBackup = false)

    /** "Sidebar on phones": phone-width windows get the Auto-Hide Sidebar (on by default) or plain Fenix. */
    val phoneSidebar = flag(KEY_PHONE_SIDEBAR, true)

    /** The Swipe Zone on phones: off by default, where the screen edge belongs to the back gesture. */
    val phoneSwipeZone = flag(KEY_PHONE_SWIPE_ZONE, false)

    /** The Swipe Zone setting in effect at [tier]: phones keep their own. */
    fun swipeZone(tier: SidebarTier): Setting<Boolean> = if (tier == SidebarTier.Phone) phoneSwipeZone else swipeZoneEnabled

    /** Whether the Sidebar Flap shows in Auto-Hide Mode. */
    val flapEnabled = flag(KEY_FLAP, true)

    /** Where the Sidebar Flap sits along the edge: 0 at the top, 1 at the bottom. */
    val flapPosition = number(KEY_FLAP_POSITION, 0.5f)

    private val arrangedEssentialsPortrait = number(KEY_ARRANGED_ESSENTIALS_PORTRAIT, NOT_ARRANGED)
    private val arrangedPinnedPortrait = number(KEY_ARRANGED_PINNED_PORTRAIT, NOT_ARRANGED)
    private val arrangedEssentialsLandscape = number(KEY_ARRANGED_ESSENTIALS_LANDSCAPE, NOT_ARRANGED)
    private val arrangedPinnedLandscape = number(KEY_ARRANGED_PINNED_LANDSCAPE, NOT_ARRANGED)

    /** The share Arrange Mode set for [section] in one orientation; negative while it isn't arranged. */
    fun arrangedShare(
        section: CollapsedSection,
        landscape: Boolean,
    ): Setting<Float> =
        when (section) {
            CollapsedSection.Essentials -> if (landscape) arrangedEssentialsLandscape else arrangedEssentialsPortrait
            CollapsedSection.Pinned -> if (landscape) arrangedPinnedLandscape else arrangedPinnedPortrait
        }

    /** Remembers [share] for [section] in one orientation; null clears it. */
    fun arrange(
        section: CollapsedSection,
        landscape: Boolean,
        share: Float?,
    ) = arrangedShare(section, landscape).set(share ?: NOT_ARRANGED)

    /** The sizes Arrange Mode set for one orientation. */
    fun arrangedShares(landscape: Boolean): ArrangedShares =
        ArrangedShares(
            essentials = arrangedShare(CollapsedSection.Essentials, landscape).value.asArranged(),
            pinned = arrangedShare(CollapsedSection.Pinned, landscape).value.asArranged(),
        )

    /** Synced Tabs' Control Place: In the menu until moved, so 1.0's Sidebar row stays as it was. */
    val syncedTabsPlace = choice(KEY_SYNCED_TABS_PLACE, ControlPlace.InMenu)

    /** The Toggle's Control Place. */
    val togglePlace = choice(KEY_TOGGLE_PLACE, ControlPlace.InSidebar)

    /** The layout button's Control Place. */
    val layoutButtonPlace = choice(KEY_LAYOUT_BUTTON_PLACE, ControlPlace.InSidebar)

    /** The Control Place setting of [control]. */
    fun controlPlace(control: OptionalControl): Setting<ControlPlace> =
        when (control) {
            OptionalControl.SyncedTabs -> syncedTabsPlace
            OptionalControl.Toggle -> togglePlace
            OptionalControl.LayoutButton -> layoutButtonPlace
        }

    /** What New Tab opens. */
    val newTabBehaviour = choice(KEY_NEW_TAB, NewTabBehaviour.Search)

    /** The chosen Address Bar Layout. */
    val addressBarLayout = choice(KEY_LAYOUT, AddressBarLayout.Sidebar)

    /** How Spaces are presented: the icon row, or the Space Header (1.2's default is the Header). */
    val spacesShown = choice(KEY_SPACES_SHOWN, SpacesShown.Header)

    /** Search Pop-up: hiding the keyboard closes it while nothing has been typed. Off by default: hiding it makes room to scroll. */
    val popupCloseOnKeyboardHide = flag(KEY_POPUP_KEYBOARD, false)

    /** Search Pop-up: a tap on the dimmed layer closes it. */
    val popupCloseOnTapOutside = flag(KEY_POPUP_OUTSIDE, true)

    /** The Sidebar's width in Persistent Mode. */
    val sidebarWidth = choice(KEY_WIDTH, SidebarWidth.Standard)

    /** Where new tabs appear in the Tab List. */
    val newTabPosition = choice(KEY_NEW_TAB_POSITION, NewTabPosition.Top)

    /** Whether the Tab List grows down from the top or up from the bottom. */
    val tabListDirection = choice(KEY_TAB_LIST_DIRECTION, TabListDirection.TopDown)

    /** Which edge the Sidebar docks on. */
    val sidebarSide = choice(KEY_SIDE, SidebarSide.Left)

    /** How tall tab rows are. */
    val tabRows = choice(KEY_TAB_ROWS, TabRows.Comfortable)

    /** The Spaces whose Pinned Tabs are folded (Pinned Fold), on this device only. */
    val foldedSpaces = text(KEY_FOLDED_SPACES, "")

    fun isFolded(spaceId: String): Boolean = spaceId in foldedSpaces.value.split(FOLD_SEPARATOR)

    /** Folds the Space's Pinned Tabs, or unfolds them. */
    fun toggleFold(spaceId: String) {
        val ids = foldedSpaces.value.split(FOLD_SEPARATOR).filter { it.isNotEmpty() }.toSet()
        foldedSpaces.set((if (spaceId in ids) ids - spaceId else ids + spaceId).joinToString(FOLD_SEPARATOR))
    }

    /** How long a tab goes unused before Auto-Sleep unloads it. */
    val sleepTimer = choice(KEY_SLEEP_TIMER, SleepTimer.Minutes30)

    /** Whether swiping a tab row left closes it (Tab Close Gesture). */
    val swipeToClose = flag(KEY_SWIPE_TO_CLOSE, true)

    /** Whether swiping the Sidebar Address Bar up opens a new tab. */
    val addressSwipeNewTab = flag(KEY_ADDRESS_SWIPE_NEW_TAB, true)

    /** Whether swiping the Sidebar Address Bar sideways switches to the next or previous tab. */
    val addressSwipeTabs = flag(KEY_ADDRESS_SWIPE_TABS, true)

    /** Whether Fenix's toolbar keeps its tab counter and New tab beside a Sidebar (issue #7). */
    val toolbarTabButtons = flag(KEY_TOOLBAR_TAB_BUTTONS, false)

    /** How many Essentials sit in a row. */
    val essentialsPerRow = choice(KEY_ESSENTIALS_PER_ROW, EssentialsPerRow.Auto)

    /** Whether the Sidebar ticks under the finger (haptic feedback). */
    val haptics = flag(KEY_HAPTICS, true)

    /** Whether the current Space's colours tint the frame (Space themes). */
    val spaceThemes = flag(KEY_SPACE_THEMES, true)

    /** Which system bars are hidden for a full-screen browser. */
    val fullScreen = choice(KEY_FULL_SCREEN, FullScreen.Off)

    /** Whether the dark theme is pure black, for OLED screens; it replaces Space themes. */

    /** The accent colour (a Space palette key); Spaces on the default colour follow it. */
    val accent = text(KEY_ACCENT, DEFAULT_SPACE_ACCENT)

    /** Whether content sits in a rounded frame beside the Sidebar. */
    val roundedFrame = flag(KEY_ROUNDED_FRAME, true)

    /** How much the Sidebar animates. */
    val animations = choice(KEY_ANIMATIONS, AnimationLevel.Full)

    /** Which tab rows show a close button. */
    val closeButtons = choice(KEY_CLOSE_BUTTONS, CloseButtons.SelectedTab)

    /** Whether the Desktop Tabs group is expanded (collapsed by default). */
    val desktopTabsExpanded = flag(KEY_DESKTOP_TABS_EXPANDED, false, inBackup = false)

    /** Every setting as JSON values, by stored key (a backup's settings); remembered UI state stays out. */
    fun exportSettings(): JSONObject = JSONObject().apply { registry.forEach { (key, entry) -> if (entry.inBackup) entry.read()?.let { put(key, it) } } }

    /** Sets each setting [json] names; keys this version doesn't know, and values of the wrong kind, are skipped. */
    fun importSettings(json: JSONObject) {
        json.keys().forEach { key -> registry[key]?.write?.invoke(json.get(key)) }
    }

    private fun register(
        key: String,
        read: () -> Any?,
        inBackup: Boolean = true,
        write: (Any) -> Boolean,
    ) {
        registry[key] = Entry(read, write, inBackup)
    }

    /** Sets [value] when there is one; whether it did. */
    private fun <T> Setting<T>.setFrom(value: T?): Boolean = value?.also(::set) != null

    private fun flag(
        key: String,
        default: Boolean,
        inBackup: Boolean = true,
    ) = Setting(backend.getBoolean(key, default)) { backend.putBoolean(key, it) }.also { setting ->
        register(key, { setting.value }, inBackup) { value -> setting.setFrom(value as? Boolean) }
    }

    private fun text(
        key: String,
        default: String,
    ) = Setting(backend.getString(key) ?: default) { backend.putString(key, it) }.also { setting ->
        register(key, { setting.value }) { value -> setting.setFrom(value as? String) }
    }

    private fun number(
        key: String,
        default: Float,
    ) = Setting(backend.getString(key)?.toFloatOrNull() ?: default) { backend.putString(key, it.toString()) }.also { setting ->
        register(key, { setting.value.toDouble() }) { value -> setting.setFrom((value as? Number)?.toFloat()) }
    }

    private inline fun <reified T : Enum<T>> choice(
        key: String,
        default: T,
    ) = Setting(backend.getString(key)?.let { stored -> enumValues<T>().firstOrNull { it.name == stored } } ?: default) {
        backend.putString(key, it.name)
    }.also { setting ->
        register(key, { setting.value.name }) { value -> setting.setFrom(enumValues<T>().firstOrNull { it.name == value }) }
    }

    private companion object {
        const val KEY_MODE = "chosen_mode"
        const val KEY_SWIPE_ZONE = "swipe_zone_enabled"
        const val KEY_SHOW_INACTIVE = "show_inactive_section"
        const val KEY_INACTIVE_EXPANDED = "inactive_expanded"
        const val KEY_FLAP = "flap_enabled"
        const val KEY_PHONE_SIDEBAR = "phone_sidebar"
        const val KEY_PHONE_SWIPE_ZONE = "phone_swipe_zone_enabled"
        const val KEY_FLAP_POSITION = "flap_position"
        const val KEY_TOGGLE_PLACE = "toggle_place"
        const val KEY_ARRANGED_ESSENTIALS_PORTRAIT = "arranged_essentials_portrait"
        const val KEY_ARRANGED_PINNED_PORTRAIT = "arranged_pinned_portrait"
        const val KEY_ARRANGED_ESSENTIALS_LANDSCAPE = "arranged_essentials_landscape"
        const val KEY_ARRANGED_PINNED_LANDSCAPE = "arranged_pinned_landscape"
        const val NOT_ARRANGED = -1f
        const val KEY_SYNCED_TABS_PLACE = "synced_tabs_place"
        const val KEY_LAYOUT_BUTTON_PLACE = "layout_button_place"
        const val KEY_NEW_TAB = "new_tab_behaviour"
        const val KEY_LAYOUT = "address_bar_layout"
        const val KEY_SPACES_SHOWN = "spaces_shown"
        const val KEY_POPUP_KEYBOARD = "popup_close_on_keyboard_hide"
        const val KEY_POPUP_OUTSIDE = "popup_close_on_tap_outside"
        const val KEY_WIDTH = "sidebar_width"
        const val KEY_NEW_TAB_POSITION = "new_tab_position"
        const val KEY_SIDE = "sidebar_side"
        const val KEY_DESKTOP_TABS_EXPANDED = "desktop_tabs_expanded"
        const val KEY_TAB_ROWS = "tab_rows"
        const val KEY_HAPTICS = "haptics"
        const val KEY_ANIMATIONS = "animations"
        const val KEY_ROUNDED_FRAME = "rounded_frame"
        const val KEY_ACCENT = "accent"
        const val KEY_ESSENTIALS_PER_ROW = "essentials_per_row"
        const val KEY_SWIPE_TO_CLOSE = "swipe_to_close"
        const val KEY_TAB_LIST_DIRECTION = "tab_list_direction"
        const val KEY_SPACE_THEMES = "space_themes"
        const val KEY_FULL_SCREEN = "full_screen"
        const val KEY_ADDRESS_SWIPE_NEW_TAB = "address_swipe_new_tab"
        const val KEY_ADDRESS_SWIPE_TABS = "address_swipe_tabs"
        const val KEY_TOOLBAR_TAB_BUTTONS = "toolbar_tab_buttons"
        const val KEY_SLEEP_TIMER = "sleep_timer"
        const val KEY_FOLDED_SPACES = "folded_spaces"
        const val FOLD_SEPARATOR = ","
        const val KEY_CLOSE_BUTTONS = "close_buttons"
    }
}

/** In-memory store, for tests and previews. */
class InMemorySidebarModeStore(initial: SidebarMode? = null) : SidebarModeStore(InMemorySettingsBackend()) {
    init {
        initial?.let { chosenMode.set(it) }
    }
}

/** The app's SharedPreferences-backed store, one per process. */
object PreferencesSidebarModeStore {
    private const val PREFS = "sideraven_sidebar"

    @Volatile private var instance: SidebarModeStore? = null

    fun get(context: Context): SidebarModeStore =
        instance ?: synchronized(this) {
            instance ?: allowDiskReads {
                SidebarModeStore(PreferencesSettingsBackend(context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)))
            }.also { instance = it }
        }
}

/** Watches a [Setting] from a composable. */
@Composable
fun <T> Setting<T>.collectAsState(): State<T> = flow.collectAsState()

/** The sizes Arrange Mode set for one orientation, as Compose state. */
@Composable
fun SidebarModeStore.collectArrangedShares(landscape: Boolean): ArrangedShares {
    val essentials by arrangedShare(CollapsedSection.Essentials, landscape).collectAsState()
    val pinned by arrangedShare(CollapsedSection.Pinned, landscape).collectAsState()
    return ArrangedShares(essentials.asArranged(), pinned.asArranged())
}

/** A stored arranged share, or null for the "not arranged" value. */
private fun Float.asArranged(): Float? = takeIf { it >= 0f }

/** Every Optional Control's Control Place, as Compose state. */
@Composable
fun SidebarModeStore.collectControlPlaces(): ControlPlaces {
    val syncedTabs by syncedTabsPlace.collectAsState()
    val toggle by togglePlace.collectAsState()
    val layoutButton by layoutButtonPlace.collectAsState()
    return ControlPlaces(syncedTabs = syncedTabs, toggle = toggle, layoutButton = layoutButton)
}

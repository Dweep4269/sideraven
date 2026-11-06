/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import android.graphics.Bitmap
import mozilla.components.browser.state.selector.getNormalOrPrivateTabs
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.browser.state.state.isActive
import mozilla.components.concept.engine.mediasession.MediaSession

/** The screens the Sidebar can appear on. Everything else (settings, history, …) is [Other]. */
enum class SidebarScreen {
    Home,
    Browser,
    Other,
    ;

    companion object {
        /**
         * The screen after navigating to a destination. Dialogs and bottom sheets (the main menu,
         * share, …) float over the screen beneath, so the Sidebar stays as it was ([previous]).
         */
        fun after(
            previous: SidebarScreen,
            isHome: Boolean,
            isBrowser: Boolean,
            isDialog: Boolean,
        ): SidebarScreen =
            when {
                isDialog -> previous
                isHome -> Home
                isBrowser -> Browser
                else -> Other
            }
    }
}

/**
 * Everything the Sidebar UI renders. Produced by [toSidebarUiState]; the UI holds no logic.
 *
 * @property isVisible Whether the Sidebar is shown at all (tier + screen gating).
 * @property isPrivate Whether the Sidebar shows the Private Browsing Mode.
 * @property tabs The Tab List, newest-first.
 * @property mode The Sidebar Mode actually shown (after tier fallback).
 * @property nextMode What the Toggle switches to; null hides the Toggle.
 * @property isRevealed In Auto-Hide Mode, whether the Sidebar is revealed over the content.
 * @property showsSwipeZone Whether the Swipe Zone reclaims its strip of the left edge right now.
 * @property essentials The Essentials Section, in display order.
 * @property pinBlock Why "Pin to Essentials" is unavailable right now; null when it's available.
 * @property animateModeChanges False after a keyboard mode change: keyboard actions don't animate.
 * @property inactive The Inactive Tabs Section, or null when it isn't shown.
 * @property showsFlap Whether the Sidebar Flap (the tray's handle) is shown.
 * @property flapPosition Where the Sidebar Flap sits along the edge: 0 at the top, 1 at the bottom.
 * @property spaces The Space icon row, in order; empty when Spaces don't apply (Private Mode).
 * @property syncProblem Whether Zen Sync's last attempt failed (a dot on the Space row).
 * @property address The Sidebar Address Bar, or null when the Sidebar shows none.
 * @property layout The Address Bar Layout in effect.
 * @property chosenLayout The layout the user chose (the layout button switches away from it).
 * @property zenSyncOn Whether Zen Sync is on (shows "Don't sync this Space" and the delete warning).
 * @property pinned The current Space's Pinned Tabs, in order.
 * @property pinnedRows The pinned block as displayed: Folders (nested ones indented) with the
 * Pinned Tabs of expanded Folders, then the Pinned Tabs outside any Folder. Empty means [pinned]
 * is shown flat.
 * @property folders The current Space's Folders, for "Move to folder".
 * @property spacePinBlock Why "Pin to space" is unavailable right now; null when it's available.
 * @property controlPlaces The Control Place of each Optional Control.
 * @property arranging Whether Arrange Mode is on (Collapsed Mode only).
 * @property arranged The sizes set in Arrange Mode for this orientation.
 */
data class SidebarUiState(
    val isVisible: Boolean,
    val isPrivate: Boolean,
    val tabs: List<SidebarTab>,
    val mode: SidebarMode = SidebarMode.Persistent,
    val nextMode: SidebarMode? = null,
    val isRevealed: Boolean = false,
    val showsSwipeZone: Boolean = false,
    val essentials: List<SidebarEssential> = emptyList(),
    val pinBlock: PinResult? = null,
    val animateModeChanges: Boolean = true,
    val inactive: SidebarInactiveSection? = null,
    val desktopTabs: SidebarDesktopTabs? = null,
    val look: SidebarLook = SidebarLook(),
    val showsFlap: Boolean = false,
    val flapPosition: Float = 0.5f,
    val controlPlaces: ControlPlaces = ControlPlaces(),
    val arranging: Boolean = false,
    /** The current Space's Pinned Tabs are folded (Pinned Fold). */
    val pinnedFolded: Boolean = false,
    val arranged: ArrangedShares = ArrangedShares(),
    val spaces: List<SidebarSpace> = emptyList(),
    val pinned: List<SidebarPinnedTab> = emptyList(),
    val syncProblem: Boolean = false,
    val zenSyncOn: Boolean = false,
    val desktopSendingOn: Boolean = false,
    val address: SidebarAddress? = null,
    val layout: AddressBarLayout = AddressBarLayout.Top,
    val chosenLayout: AddressBarLayout = AddressBarLayout.Top,
    /** Whether the layout button can move the address here (Persistent Mode, or a revealable Auto-Hide). */
    val layoutMovable: Boolean = true,
    /** The Sidebar's width in Persistent Mode (Sideraven Settings). */
    val width: SidebarWidth = SidebarWidth.Standard,
    /** Which edge the Sidebar docks on (Sideraven Settings). */
    val side: SidebarSide = SidebarSide.Left,
    val spacePinBlock: PinResult? = null,
    val pinnedRows: List<SidebarPinnedRow> = emptyList(),
    val folders: List<SidebarFolder> = emptyList(),
) {
    /**
     * The Sidebar Controls, in order. The row and the Collapsed column draw from this; the column
     * also leaves out the layout button while a mode change is still widening or narrowing the Sidebar.
     */
    val controls: List<SidebarControlKind> get() = sidebarControls(nextMode, layoutMovable, controlPlaces)

    /** Every tab in the Tab List, active and inactive. */
    val allTabs: List<SidebarTab> get() = tabs + inactive?.tabs.orEmpty()

    /** The Space whose tabs are shown, or null when Spaces don't apply. */
    val currentSpace: SidebarSpace? get() = spaces.firstOrNull { it.isCurrent }

    companion object {
        val Hidden = SidebarUiState(isVisible = false, isPrivate = false, tabs = emptyList())
    }
}

/**
 * One item in the Tab List.
 *
 * @property id Tab id in the browser store.
 * @property title Title to show; falls back to the URL while the page has no title.
 * @property url The tab's current URL.
 * @property icon The tab's favicon, if loaded.
 * @property isSelected Whether this tab carries the Active Tab Indicator.
 * @property audio Whether the tab is playing audio, muted, or silent.
 */
data class SidebarTab(
    val id: String,
    val title: String,
    val url: String,
    val icon: Bitmap?,
    val isSelected: Boolean,
    val audio: TabAudio = TabAudio.None,
    /** Sleeping: Unloaded, or not loaded yet; its page reloads when opened. */
    val sleeping: Boolean = false,
)

/** Whether content sits in a rounded frame beside the Sidebar: while it shows, if Rounded frame is on. */
val SidebarUiState.framesContent: Boolean get() = isVisible && look.roundedFrame

/** How the Sidebar's rows look and behave (Sideraven Settings). */
data class SidebarLook(
    val tabRows: TabRows = TabRows.Comfortable,
    val closeButtons: CloseButtons = CloseButtons.SelectedTab,
    val swipeToClose: Boolean = true,
    val essentialsPerRow: EssentialsPerRow = EssentialsPerRow.Auto,
    val haptics: Boolean = true,
    val animations: AnimationLevel = AnimationLevel.Full,
    val roundedFrame: Boolean = true,
    /** Whether the Tab List grows down from the top or up from the bottom (Tab List Direction). */
    val tabListDirection: TabListDirection = TabListDirection.TopDown,
    /** Swiping the Sidebar Address Bar up opens a new tab. */
    val addressSwipeNewTab: Boolean = true,
    /** Swiping the Sidebar Address Bar sideways selects the next or previous tab. */
    val addressSwipeTabs: Boolean = true,
    /** How Spaces are presented above their Pinned Tabs: the icon row or the Space Header. */
    val spacesShown: SpacesShown = SpacesShown.IconRow,
)

/** The Sidebar Controls, in the order the row (or Collapsed Mode's column) shows them. */
enum class SidebarControlKind { NewTab, BrowsingModeSwitch, SyncedTabs, Toggle, LayoutButton }

/**
 * The Sidebar Controls shown: New Tab and the Browsing Mode Switch always; Synced Tabs; the Toggle
 * where the tier has another mode to switch to ([nextMode]); and the layout button where the address
 * can move. Synced Tabs, the Toggle and the layout button only while they're In the sidebar.
 */
fun sidebarControls(
    nextMode: SidebarMode?,
    layoutMovable: Boolean,
    places: ControlPlaces = ControlPlaces(),
): List<SidebarControlKind> =
    buildList {
        add(SidebarControlKind.NewTab)
        add(SidebarControlKind.BrowsingModeSwitch)
        if (places.syncedTabs == ControlPlace.InSidebar) add(SidebarControlKind.SyncedTabs)
        if (nextMode != null && places.toggle == ControlPlace.InSidebar) add(SidebarControlKind.Toggle)
        if (layoutMovable && places.layoutButton == ControlPlace.InSidebar) add(SidebarControlKind.LayoutButton)
    }

/**
 * Desktop Tabs: the current synced Space's open tabs on Zen desktop, read-only,
 * at the end of the Tab List.
 */
data class SidebarDesktopTabs(
    val tabs: List<pt.dweep.sideraven.sync.DesktopTab>,
    val expanded: Boolean,
    /** Known icons by address; rows without one use the favicon loader. */
    val icons: Map<String, Bitmap> = emptyMap(),
)

/**
 * The Inactive Tabs Section at the bottom of the Tab List.
 *
 * @property tabs Inactive tabs, newest-first like the Tab List.
 * @property expanded Whether the section is open.
 */
data class SidebarInactiveSection(
    val tabs: List<SidebarTab>,
    val expanded: Boolean,
) {
    val count: Int get() = tabs.size
}

/** The connection state the Sidebar Address Bar shows. */
enum class AddressSecurity { Secure, Insecure, None }

/**
 * The Sidebar Address Bar.
 *
 * @property domain The page's host without "www.", or the URL for non-web pages.
 * @property isHome No page is shown (the home screen): the bar shows its placeholder.
 * @property progress Loading progress, 0-100.
 */
data class SidebarAddress(
    val domain: String,
    val security: AddressSecurity,
    val loading: Boolean,
    val progress: Int,
    val canGoBack: Boolean,
    val canGoForward: Boolean,
    val isHome: Boolean,
    val pin: AddressPin? = null,
)

/**
 * The selected tab's pin, when it is an Essential Tab or a Pinned Tab's tab: the bar then offers
 * Reset (once it has left the pinned URL) and Close (leaving a Ghost).
 */
sealed interface AddressPin {
    val navigatedAway: Boolean

    /** What the bar's Reset sends. */
    val resetIntent: SidebarIntent

    /** What the bar's Close sends. */
    val closeIntent: SidebarIntent

    data class OfEssential(val url: String, override val navigatedAway: Boolean) : AddressPin {
        override val resetIntent: SidebarIntent get() = SidebarIntent.ResetEssential(url)
        override val closeIntent: SidebarIntent get() = SidebarIntent.CloseEssentialTab(url)
    }

    data class OfPinnedTab(val pinId: String, override val navigatedAway: Boolean) : AddressPin {
        override val resetIntent: SidebarIntent get() = SidebarIntent.ResetPinnedTab(pinId)
        override val closeIntent: SidebarIntent get() = SidebarIntent.ClosePinnedTab(pinId)
    }
}

/** The Sidebar Address Bar for the selected tab on [screen]. */
fun sidebarAddress(
    tab: TabSessionState?,
    screen: SidebarScreen,
): SidebarAddress {
    if (tab == null || screen == SidebarScreen.Home) {
        return SidebarAddress("", AddressSecurity.None, loading = false, progress = 0, canGoBack = false, canGoForward = false, isHome = true)
    }
    val url = tab.content.url
    val uri = runCatching { java.net.URI(url) }.getOrNull()
    val scheme = uri?.scheme?.lowercase()
    val web = scheme == "http" || scheme == "https"
    val domain = if (web) uri.host?.removePrefix("www.").orEmpty().ifBlank { url } else url
    val security =
        when {
            !web -> AddressSecurity.None
            tab.content.securityInfo is mozilla.components.browser.state.state.SecurityInfo.Secure -> AddressSecurity.Secure
            scheme == "http" || tab.content.securityInfo is mozilla.components.browser.state.state.SecurityInfo.Insecure -> AddressSecurity.Insecure
            else -> AddressSecurity.None
        }
    return SidebarAddress(
        domain = domain,
        security = security,
        loading = tab.content.loading,
        progress = tab.content.progress,
        canGoBack = tab.content.canGoBack,
        canGoForward = tab.content.canGoForward,
        isHome = false,
    )
}

/** The pin behind [tab], when it's an Essential Tab or a Pinned Tab's tab. */
private fun addressPin(
    tab: TabSessionState,
    essentialLinks: Map<String, String>,
    pinLinks: Map<String, String>,
    spaces: SpacesData?,
): AddressPin? =
    essentialLinks.entries.firstOrNull { it.value == tab.id }?.let { (url, _) ->
        AddressPin.OfEssential(url, navigatedAway = tab.content.url != url)
    } ?: pinLinks.entries.firstOrNull { it.value == tab.id }?.let { (pinId, _) ->
        spaces?.pinned?.firstOrNull { it.id == pinId }?.url?.let { AddressPin.OfPinnedTab(pinId, navigatedAway = tab.content.url != it) }
    }

/** One Space in the Space icon row. */
data class SidebarSpace(
    val id: String,
    val name: String,
    val icon: String,
    val accent: String,
    val isCurrent: Boolean,
    val pinnedCount: Int = 0,
    val isLocal: Boolean = false,
    val unpinnedCount: Int = 0,
)

/** A Folder in the pinned block. [count] is how many Pinned Tabs it holds, nested ones included. */
data class SidebarFolder(
    val id: String,
    val name: String,
    val icon: String,
    val depth: Int,
    val expanded: Boolean,
    val count: Int,
    val parentId: String? = null,
)

/** One row of the pinned block. */
sealed interface SidebarPinnedRow {
    val depth: Int

    data class FolderRow(val folder: SidebarFolder) : SidebarPinnedRow {
        override val depth: Int get() = folder.depth
    }

    data class PinRow(val pin: SidebarPinnedTab, override val depth: Int) : SidebarPinnedRow
}

/**
 * One Pinned Tab row.
 *
 * @property title Its custom name, else the title it was pinned with.
 * @property linkedTabId Its open tab, or null for a Ghost.
 */
data class SidebarPinnedTab(
    val id: String,
    val title: String,
    val url: String,
    val icon: Bitmap?,
    val linkedTabId: String?,
    val isSelected: Boolean,
    val folderId: String? = null,
    /** Its tab is Sleeping. */
    val sleeping: Boolean = false,
) {
    val isGhost: Boolean get() = linkedTabId == null
}

/** A tab's audio indicator state. */
enum class TabAudio { None, Playing, Muted }

/**
 * Essentials per row in Persistent Mode. [EssentialsPerRow.Auto]: 1–3 Essentials stretch across
 * one row as larger tiles, 4 or more use 4 columns; a fixed
 * choice always uses that many columns.
 */
fun essentialsGridColumns(
    count: Int,
    perRow: EssentialsPerRow = EssentialsPerRow.Auto,
): Int = perRow.columns ?: count.coerceIn(1, ESSENTIALS_GRID_MAX_COLUMNS)

/** Most Essentials per row. */
const val ESSENTIALS_GRID_MAX_COLUMNS = 4

/** Rows shown before the Essentials grid scrolls by itself. */
const val ESSENTIALS_GRID_VISIBLE_ROWS = 3

/**
 * One Essential tile.
 *
 * @property linkedTabId The open Essential Tab, or null for a Ghost Essential.
 * @property icon The Essential Tab's favicon, or the last one loaded for its URL.
 * @property isSelected Whether its Essential Tab is the selected tab.
 * @property navigatedAway Whether its Essential Tab has left the pinned URL (Reset applies).
 */
data class SidebarEssential(
    val url: String,
    val title: String,
    val icon: Bitmap?,
    val linkedTabId: String?,
    val isSelected: Boolean,
    val navigatedAway: Boolean = false,
    /** Its tab is Sleeping. */
    val sleeping: Boolean = false,
) {
    val isGhost: Boolean get() = linkedTabId == null
}

/**
 * Derives the Sidebar UI state.
 *
 * The Tab List is displayed newest-first: the reverse of the browser's single tab order,
 * which the Tab Tray shows oldest-first.
 *
 * @param isPrivate The current Browsing Mode.
 * @param tier The current window's Qualifying Device tier.
 * @param screen The screen currently shown.
 * @param chosenMode The user's stored Sidebar Mode choice, if any.
 * @param isRevealed Whether the Auto-Hide Sidebar has been revealed.
 * @param swipeZoneEnabled The Swipe Zone setting.
 * @param essentials The stored Essentials, in order.
 * @param essentialLinks Essential URL -> Essential Tab id.
 * @param essentialIcons Favicons loaded for Essential URLs (used for Ghost Essentials).
 * @param inactiveTabsEnabled Fenix's inactive-tabs feature AND the Sidebar's "Show inactive tabs" setting.
 * @param inactiveExpanded Whether the Inactive Tabs Section is open.
 * @param maxActiveTime Fenix's inactivity threshold.
 * @param now The current time, for the inactivity check.
 * @param flapEnabled The Sidebar Flap setting.
 * @param flapPosition Where the Sidebar Flap was left along the edge.
 * @param controlPlaces The Control Place of each Optional Control.
 * @param arranging Whether Arrange Mode was started (it shows in Collapsed Mode only).
 * @param arranged The sizes set in Arrange Mode for this orientation.
 * @param addressBarLayout The chosen Address Bar Layout.
 * @param spaces Stored Spaces; null shows every tab (no Spaces).
 * @param pinnedLinks Pinned Tab id -> its open tab id.
 * @param pinnedIcons Favicons loaded for pinned URLs (used for Ghosts).
 */
fun BrowserState.toSidebarUiState(
    isPrivate: Boolean,
    tier: SidebarTier,
    screen: SidebarScreen,
    chosenMode: SidebarMode? = null,
    isRevealed: Boolean = false,
    swipeZoneEnabled: Boolean = true,
    essentials: List<Essential> = emptyList(),
    essentialLinks: Map<String, String> = emptyMap(),
    essentialIcons: Map<String, Bitmap> = emptyMap(),
    inactiveTabsEnabled: Boolean = false,
    inactiveExpanded: Boolean = false,
    maxActiveTime: Long = Long.MAX_VALUE,
    now: Long = System.currentTimeMillis(),
    flapEnabled: Boolean = true,
    flapPosition: Float = 0.5f,
    controlPlaces: ControlPlaces = ControlPlaces(),
    arranging: Boolean = false,
    arranged: ArrangedShares = ArrangedShares(),
    spaces: SpacesData? = null,
    addressBarLayout: AddressBarLayout = AddressBarLayout.Top,
    sidebarWidth: SidebarWidth = SidebarWidth.Standard,
    sidebarSide: SidebarSide = SidebarSide.Left,
    sleepingTabIds: Set<String> = emptySet(),
    foldedSpaces: String = "",
    pinnedLinks: Map<String, String> = emptyMap(),
    pinnedIcons: Map<String, Bitmap> = emptyMap(),
    desktopTabs: Map<String, List<pt.dweep.sideraven.sync.DesktopTab>> = emptyMap(),
    desktopTabsExpanded: Boolean = false,
    desktopTabIcons: Map<String, Bitmap> = emptyMap(),
): SidebarUiState {
    val mode = SidebarMode.effective(chosenMode, tier)
    if (mode == null || !SidebarGate.showsSidebar(tier) || screen == SidebarScreen.Other) return SidebarUiState.Hidden
    // Auto-Hide can lose Fenix's toolbar only while the Sidebar can still be pulled out.
    val revealable = flapEnabled || swipeZoneEnabled
    val layout = SidebarGate.effectiveLayout(addressBarLayout, sidebarShown = true, mode = mode, revealable = revealable)
    val liveLinks = essentialLinks.filterValues { id -> tabs.any { it.id == id } }
    // Fenix only classifies normal tabs as inactive; the selected tab never is.
    // Spaces group normal tabs only; Private Mode is one flat list.
    val spacesShown = spaces?.takeUnless { isPrivate }
    val livePinLinks = pinnedLinks.filterValues { id -> tabs.any { it.id == id } }
    val listTabs =
        getNormalOrPrivateTabs(private = isPrivate)
            .filterNot { it.id in liveLinks.values }
            // Pinned Tabs' tabs live in the pinned block, never in the Tab List.
            .filterNot { it.id in livePinLinks.values }
            .filter { spacesShown == null || spacesShown.spaceOf(it.id) == spacesShown.current.id }
            .asReversed()
    val (inactiveTabs, activeTabs) =
        if (inactiveTabsEnabled && !isPrivate) {
            listTabs.partition { it.id != selectedTabId && !it.isActive(maxActiveTime, now) }
        } else {
            emptyList<TabSessionState>() to listTabs
        }

    val pinnedList =
        spacesShown?.pinnedIn(spacesShown.current.id)?.map { pin ->
                val linkedTab = livePinLinks[pin.id]?.let { id -> tabs.firstOrNull { it.id == id } }
                SidebarPinnedTab(
                    id = pin.id,
                    title = pin.displayName,
                    url = pin.url,
                    icon = SidebarIcons.pick(linkedTab?.content?.icon, pinnedIcons[pin.url], loaded = null),
                    linkedTabId = linkedTab?.id,
                    isSelected = linkedTab != null && linkedTab.id == selectedTabId,
                    folderId = pin.folderId,
                    sleeping = linkedTab?.isSleeping(selectedTabId, sleepingTabIds) == true,
                )
            }.orEmpty()
    val pinnedById = pinnedList.associateBy { it.id }

    return SidebarUiState(
        isVisible = true,
        isPrivate = isPrivate,
        // Essential Tabs live in the Essentials Section, never in the Tab List.
        tabs = activeTabs.map { it.toSidebarTab(selectedTabId, sleepingTabIds) },
        inactive =
            inactiveTabs
                .takeIf { it.isNotEmpty() }
                ?.let { tabs -> SidebarInactiveSection(tabs.map { it.toSidebarTab(selectedTabId, sleepingTabIds) }, expanded = inactiveExpanded) },
        // Only a synced Space's, never in private browsing (a Local Space has no desktop copy).
        desktopTabs =
            spacesShown?.current?.takeUnless { it.isLocal }
                ?.let { desktopTabs[it.id] }
                ?.takeIf { it.isNotEmpty() }
                ?.let { SidebarDesktopTabs(it, desktopTabsExpanded, desktopTabIcons) },
        // Private browsing has no Essentials, as in Zen.
        essentials =
            essentials.takeUnless { isPrivate }.orEmpty().map { essential ->
                val linkedTab = liveLinks[essential.url]?.let { id -> tabs.firstOrNull { it.id == id } }
                SidebarEssential(
                    url = essential.url,
                    title = essential.displayName,
                    icon = SidebarIcons.pick(linkedTab?.content?.icon, essentialIcons[essential.url], loaded = null),
                    linkedTabId = linkedTab?.id,
                    isSelected = linkedTab != null && linkedTab.id == selectedTabId,
                    navigatedAway = linkedTab != null && linkedTab.content.url != essential.url,
                    sleeping = linkedTab?.isSleeping(selectedTabId, sleepingTabIds) == true,
                )
            },
        pinBlock =
            when {
                isPrivate -> PinResult.PrivateRefused
                essentials.size >= ESSENTIALS_CAP -> PinResult.Full
                else -> null
            },
        mode = mode,
        nextMode = SidebarMode.next(mode, tier),
        // Auto-Hide reveals over the content; Collapsed Mode does too, for the Inactive Tabs Section.
        isRevealed = (mode == SidebarMode.AutoHide || mode == SidebarMode.Collapsed) && isRevealed,
        // Only in Auto-Hide, only while hidden: elsewhere the whole left edge stays system back.
        showsSwipeZone = mode == SidebarMode.AutoHide && swipeZoneEnabled && !isRevealed,
        // The Flap is the tray's handle: at the edge while hidden, on the Sidebar's edge while open.
        showsFlap = mode == SidebarMode.AutoHide && flapEnabled,
        flapPosition = flapPosition,
        controlPlaces = controlPlaces,
        arranging = arranging && mode == SidebarMode.Collapsed,
        arranged = arranged,
        pinnedFolded = spacesShown?.let { data -> data.current.id in foldedSpaces.split(',') } == true,
        spaces =
            spacesShown?.spaces?.map {
                SidebarSpace(
                    it.id,
                    it.name,
                    it.icon,
                    it.accent,
                    isCurrent = it.id == spacesShown.current.id,
                    pinnedCount = spacesShown.pinnedIn(it.id).size,
                    isLocal = it.isLocal,
                    unpinnedCount = spacesShown.unpinnedTabsIn(it.id, tabs, liveLinks.values.toSet(), livePinLinks.values.toSet()).size,
                )
            }.orEmpty(),
        pinned = pinnedList,
        folders = spacesShown?.let { data -> sidebarFolders(data, data.current.id) }.orEmpty(),
        pinnedRows = spacesShown?.let { data -> pinnedRows(data, data.current.id) { pinId -> pinnedById[pinId] } }.orEmpty(),
        layout = layout,
        chosenLayout = addressBarLayout,
        layoutMovable = SidebarGate.movesAddress(mode, revealable),
        width = sidebarWidth,
        side = sidebarSide,
        // In the Top layout the Sidebar has no address bar (Collapsed Mode included: New Tab searches).
        address =
            selectedTabId?.let { id -> tabs.firstOrNull { it.id == id } }.let { selected ->
                val address = sidebarAddress(selected, screen)
                address.copy(pin = selected?.takeUnless { address.isHome }?.let { addressPin(it, liveLinks, livePinLinks, spaces) })
            }.takeIf { layout == AddressBarLayout.Sidebar },
        spacePinBlock =
            when {
                spacesShown == null -> PinResult.PrivateRefused
                else -> null
            },
    )
}

/** Which item Unload Others was chosen from. */
sealed interface UnloadOthersFrom {
    data class Tab(val tabId: String) : UnloadOthersFrom

    data class Essential(val url: String) : UnloadOthersFrom

    data class Pinned(val pinId: String) : UnloadOthersFrom
}

/**
 * Unload Others: the tabs to put to sleep for [from]: the other normal tabs (the
 * Sidebar state holds the current Space's, in the current Browsing Mode), the other Essential Tabs,
 * or the Space's other Pinned Tabs. Never the pressed one or the tab on screen; Ghosts and
 * sleeping tabs are skipped.
 */
fun unloadOthersTargets(
    state: SidebarUiState,
    from: UnloadOthersFrom,
): List<String> =
    when (from) {
        is UnloadOthersFrom.Tab ->
            state.allTabs
                .filter { it.id != from.tabId && !it.isSelected && !it.sleeping }
                .map { it.id }
        is UnloadOthersFrom.Essential ->
            state.essentials
                .filter { it.url != from.url && !it.isSelected && !it.sleeping }
                .mapNotNull { it.linkedTabId }
        is UnloadOthersFrom.Pinned ->
            state.pinned
                .filter { it.id != from.pinId && !it.isSelected && !it.sleeping }
                .mapNotNull { it.linkedTabId }
    }

/** Sleeping: Unloaded and not opened since; the tab on screen never is. */
private fun TabSessionState.isSleeping(
    selectedTabId: String?,
    sleepingTabIds: Set<String>,
) = id != selectedTabId && id in sleepingTabIds

private fun TabSessionState.toSidebarTab(
    selectedTabId: String?,
    sleepingTabIds: Set<String>,
) =
    SidebarTab(
        id = id,
        title = content.title.ifBlank { content.url },
        url = content.url,
        icon = content.icon,
        isSelected = id == selectedTabId,
        sleeping = isSleeping(selectedTabId, sleepingTabIds),
        audio =
            mediaSessionState.let { media ->
                when {
                    media == null -> TabAudio.None
                    media.muted -> TabAudio.Muted
                    media.playbackState == MediaSession.PlaybackState.PLAYING -> TabAudio.Playing
                    else -> TabAudio.None
                }
            },
    )

/** User intents the Sidebar UI emits. */
sealed interface SidebarIntent {
    /** A tab in the Tab List was tapped. */
    data class SelectTab(val tabId: String) : SidebarIntent

    /** The New Tab button in Sidebar Controls was tapped. */
    data object NewTab : SidebarIntent

    /** A tab was closed by the Tab Close Gesture, the Tab Close Button or its accessibility action. */
    data class CloseTab(val tabId: String) : SidebarIntent

    /** The Toggle was tapped: switch to the next Sidebar Mode. */
    data object ToggleMode : SidebarIntent

    /** Ctrl+Shift+S: switch to the next Sidebar Mode, instantly. */
    data object KeyboardToggleMode : SidebarIntent

    /** Ctrl+1…9: select the Essential at [index]. */
    data class SelectEssentialAt(val index: Int) : SidebarIntent

    /**
     * A Tab Drag moved [tabId] onto [targetTabId]'s slot in the Tab List. [movedDown] is the
     * direction *on screen*; the Tab List is newest-first, so on screen and browser order run
     * opposite ways.
     */
    data class MoveTab(val tabId: String, val targetTabId: String, val movedDown: Boolean) : SidebarIntent

    /** The audio indicator was tapped: mute or unmute the tab. */
    data class ToggleMute(val tabId: String) : SidebarIntent

    /** "Duplicate" from the Tab Context Menu. */
    data class DuplicateTab(val tabId: String) : SidebarIntent

    /** "Pin to Essentials" from the Tab Context Menu (or a drop into the Essentials Section). */
    data class PinTab(val tabId: String) : SidebarIntent

    /** An Essential was tapped: switch to its Essential Tab, or open one for a Ghost Essential. */
    data class SelectEssential(val url: String) : SidebarIntent

    /** "Unpin" from an Essential's menu. */
    data class UnpinEssential(val url: String) : SidebarIntent

    /** An Essential was dragged to [toIndex] within the Essentials Section. */
    data class MoveEssential(val url: String, val toIndex: Int) : SidebarIntent

    /** An inactive tab was dragged out of the Inactive Tabs Section: make it active again. */
    data class ReactivateTab(val tabId: String) : SidebarIntent

    /** "Reset to pinned URL": send the Essential Tab back to the Essential's URL. */
    data class ResetEssential(val url: String) : SidebarIntent

    /** The Browsing Mode Switch in Sidebar Controls. */
    data object SwitchBrowsingMode : SidebarIntent

    /** The Inactive Tabs Section header was tapped: open or close it. */
    data object ToggleInactiveSection : SidebarIntent

    /** Fold the current Space's Pinned Tabs, or unfold them. */
    data object TogglePinnedFold : SidebarIntent

    /** Opens or closes the Desktop Tabs group (remembered). */
    data object ToggleDesktopTabs : SidebarIntent

    /** "Arrange sidebar" in the Main Menu: Arrange Mode starts (Collapsed Mode only). */
    data object StartArranging : SidebarIntent

    /** A grab bar was let go: [section] may take [share] of the strip in this orientation; remembered. */
    data class ArrangeSection(val section: CollapsedSection, val share: Float) : SidebarIntent

    /** Arrange Mode's Done: it ends, keeping the sizes. */
    data object DoneArranging : SidebarIntent

    /** Arrange Mode's Reset: this orientation goes back to Section Sizing. */
    data object ResetArrangement : SidebarIntent

    /** Synced Tabs: opens Fenix's synced-tabs page (the tabs open on the user's other devices). */
    data object OpenSyncedTabs : SidebarIntent

    /** The Sidebar Flap was moved along the edge to [position] (0 top, 1 bottom); remembered. */
    data class MoveFlap(val position: Float) : SidebarIntent

    /** Opens one of the Desktop Tabs here: an open tab of this Space at [url], else a new one. */
    data class OpenDesktopTab(val url: String) : SidebarIntent

    data class SendToDesktop(val tabId: String) : SidebarIntent

    /** Collapsed Mode's inactive-tabs icon: reveal the full Sidebar with the section open. */
    data object RevealInactiveSection : SidebarIntent

    /** "Close all inactive" from the section header. */
    data object CloseAllInactive : SidebarIntent

    /** Reveal the Auto-Hide Sidebar (Sidebar Button, Swipe Zone, pointer at the edge). */
    data object Reveal : SidebarIntent

    /** Dismiss the revealed Sidebar (Scrim tap, back, swiping the Sidebar left). */
    data object Dismiss : SidebarIntent

    /** Ctrl+Alt+Left/Right: the previous ([step] -1) or next (+1) Space, wrapping, instantly. */
    data class KeyboardSwitchSpace(val step: Int) : SidebarIntent

    /** The address bar's ⋮: Fenix's main menu. */
    data object OpenMenu : SidebarIntent

    /** The address bar's lock or warning icon: Fenix's site panel. */
    data object OpenSitePanel : SidebarIntent

    /** The layout button in Sidebar Controls: switch Address Bar Layout. */
    data object ToggleAddressBarLayout : SidebarIntent

    /** The Sidebar Address Bar's back button. */
    data object GoBack : SidebarIntent

    /** The Sidebar Address Bar's forward button. */
    data object GoForward : SidebarIntent

    data object Reload : SidebarIntent

    data object StopLoading : SidebarIntent

    /** Long-press on back: the tab's history. */
    data object ShowTabHistory : SidebarIntent

    /** Open the Search Pop-up: for the current tab, or (from New Tab) for a new one. */
    data class OpenSearch(val newTab: Boolean) : SidebarIntent

    /** A Space icon was tapped: show that Space. */
    data class SwitchSpace(val spaceId: String) : SidebarIntent

    /** The New Space dialog was confirmed. */
    data class CreateSpace(val name: String, val icon: String, val accent: String = DEFAULT_SPACE_ACCENT) : SidebarIntent

    /** The Edit Space dialog was confirmed. */
    data class EditSpace(val spaceId: String, val name: String, val icon: String, val accent: String) : SidebarIntent

    /** "Delete" from a Space's menu: close its tabs (with Undo) and remove it. */
    data class DeleteSpace(val spaceId: String) : SidebarIntent

    /** "Pin to space" from the Tab Context Menu. */
    data class PinToSpace(val tabId: String) : SidebarIntent

    /** A Pinned Tab was tapped: switch to its tab, or open one for a Ghost. */
    data class SelectPinnedTab(val pinId: String) : SidebarIntent

    /** "Unpin" from a Pinned Tab's menu. */
    data class UnpinPinnedTab(val pinId: String) : SidebarIntent

    /** "Rename" from an Essential's menu; a null [name] goes back to the page title. */
    data class RenameEssential(val url: String, val name: String?) : SidebarIntent

    /** "Rename" from a Pinned Tab's menu; a null [name] goes back to the page title. */
    data class RenamePinnedTab(val pinId: String, val name: String?) : SidebarIntent

    /** "New folder" from a Space's menu. */
    data class CreateFolder(val name: String, val icon: String) : SidebarIntent

    /** A Folder's Edit dialog was confirmed. */
    data class EditFolder(val folderId: String, val name: String, val icon: String) : SidebarIntent

    /** A Folder was dragged onto another Folder's place. */
    data class MoveFolderTo(val folderId: String, val targetFolderId: String) : SidebarIntent

    /** "Move up"/"Move down" from a Folder's menu. */
    data class MoveFolder(val folderId: String, val step: Int) : SidebarIntent

    /** "Delete folder": [keepPins] keeps its Pinned Tabs (moved out), otherwise they're unpinned. */
    data class DeleteFolder(val folderId: String, val keepPins: Boolean) : SidebarIntent

    /** A Folder row was tapped: collapse or expand it. */
    data class ToggleFolder(val folderId: String) : SidebarIntent

    /** "Move to folder" from a Pinned Tab's menu; null takes it out of its Folder. */
    data class MoveIntoFolder(val pinId: String, val folderId: String?) : SidebarIntent

    /** A Pinned Tab was dragged onto another Pinned Tab's place (joining its Folder). */
    data class MovePinnedTab(val pinId: String, val targetPinId: String) : SidebarIntent

    /** A Pinned Tab was dragged into the Essentials Section. */
    data class PinnedToEssentials(val pinId: String) : SidebarIntent

    /** An Essential was dragged into the current Space's Pinned Tabs. */
    data class EssentialToSpace(val url: String) : SidebarIntent

    /** A Pinned Tab's × or "Close": close its tab, leaving a Ghost. */
    data class ClosePinnedTab(val pinId: String) : SidebarIntent

    /** "Unload" from a Pinned Tab's menu: close its tab but remember where it was. */
    data class UnloadPinnedTab(val pinId: String) : SidebarIntent

    /** "Close tab" in an Essential's menu: close its tab, leaving a Ghost. */
    data class CloseEssentialTab(val url: String) : SidebarIntent

    /** "Unload" from a normal tab's menu: the tab sleeps. */
    data class UnloadTab(val tabId: String) : SidebarIntent

    /** "Unload others" from a tab's, Essential's or Pinned Tab's menu. */
    data class UnloadOthers(val from: UnloadOthersFrom) : SidebarIntent

    /** "Unload" from an Essential's menu. */
    data class UnloadEssential(val url: String) : SidebarIntent

    /** "Unload all" from a Space's menu: Unload every Pinned Tab in it. */
    data class UnloadSpace(val spaceId: String) : SidebarIntent

    /** Close a Space's Tab List, keeping its Pinned Tabs and the Essentials (with Undo). */
    data class CloseUnpinnedTabs(val spaceId: String) : SidebarIntent

    /** "Reset to pinned URL" from a Pinned Tab's menu. */
    data class ResetPinnedTab(val pinId: String) : SidebarIntent

    /** "Move to space" from the Tab Context Menu, or a tab dropped on a Space icon. */
    data class MoveTabToSpace(val tabId: String, val spaceId: String) : SidebarIntent

    /** "Don't sync this Space" was switched in the Edit Space dialog. */
    data class SetSpaceLocal(val spaceId: String, val local: Boolean) : SidebarIntent

    /** A Space icon was dragged to [toIndex] in the Space icon row. */
    data class MoveSpace(val spaceId: String, val toIndex: Int) : SidebarIntent
}

/**
 * Turns [SidebarIntent]s into browser actions. Collaborators are injected so tests can use a
 * real in-memory browser store while the app wires Fenix's use cases.
 *
 * @param selectTab Selects a tab by id.
 * @param openNewTab Opens a new tab in the given Browsing Mode (true = private).
 * @param closeTab Removes a tab by id.
 * @param onTabClosed Follow-up after a close (offer undo; leave the browser screen when the
 * Browsing Mode has no tabs left). [wasLastTab] is true when the closed tab was the only one.
 * @param setChosenMode Stores the user's Sidebar Mode choice (the Toggle overwrites it).
 * @param setRevealed Reveals or dismisses the Auto-Hide Sidebar.
 * @param duplicateTab Duplicates a tab (history included) and selects the copy.
 * @param pinTab Pins a tab as an Essential and links it; returns the outcome.
 * @param onPinRefused Tells the user why a pin didn't happen.
 * @param openEssential Opens a new tab at an Essential's URL (linking it when not private).
 * @param unpinEssential Unpins an Essential.
 * @param loadUrlInTab Loads a URL in a given tab.
 * @param switchBrowsingMode Switches Normal/Private (true = to Private).
 * @param moveTab Moves a tab next to another in the browser's tab order.
 * @param toggleMute Mutes or unmutes a tab's media.
 * @param setAnimateModeChanges Whether the next mode change animates (false for keyboard).
 * @param setInactiveExpanded Opens or closes the Inactive Tabs Section (remembered).
 * @param closeTabs Removes several tabs at once.
 * @param moveEssential Moves an Essential within the section.
 * @param reactivateTab Marks a tab as just used, so it's no longer inactive.
 * @param navigation The Sidebar Address Bar's navigation (back, forward, reload, stop, history, search).
 * @param switchSpace Makes a Space current; returns the tab to select there, or null when the
 * Space has no tabs (a New Tab opens in it).
 * @param createSpace Adds a Space and makes it current; a Local Space while Zen Sync is on.
 * @param editSpace Changes a Space's name, icon and accent.
 * @param deleteSpace Closes a Space's tabs, removes it and offers Undo.
 * @param moveSpace Reorders the Space icon row.
 * @param setSpaceLocal Marks a Space as a Local Space or back to synced.
 * @param pinToSpace Pins a tab to the current Space and links it; returns the outcome.
 * @param onSpacePinRefused Tells the user why a Pin to space didn't happen.
 * @param openPinnedTab Opens a new tab at a Pinned Tab's URL in its Space and links it.
 * @param unpinPinnedTab Unpins a Pinned Tab.
 * @param sleepTabs Puts tabs to sleep (Unload): they stay, and their pages reload when opened.
 * @param unloadSpace Unloads every tab of a Space (its Tab List and Pinned Tabs), except the one on screen.
 * @param closeUnpinnedTabs Closes a Space's Tab List, offering Undo.
 * @param toggleFold Folds or unfolds a Space's Pinned Tabs.
 * @param renameEssential Sets or clears an Essential's custom name.
 * @param renamePinnedTab Sets or clears a Pinned Tab's custom name.
 * @param movePinnedTab Moves a Pinned Tab to another's place.
 * @param pinnedToEssentials Turns a Pinned Tab into an Essential; returns the outcome.
 * @param essentialToSpace Turns an Essential into a Pinned Tab of the current Space; returns the outcome.
 * @param folders Folder operations (create, edit, move, delete, toggle, move a pin into one).
 * @param moveTabToSpace Moves a tab to another Space; returns the tab to select in the current
 * Space when the moved tab was selected (null: keep the selection).
 * @param openUrl Opens [url] in a new tab of the current Space (a Desktop Tab).
 * @param setDesktopTabsExpanded Opens or closes the Desktop Tabs group (remembered).
 * @param setFlapPosition Remembers where the Sidebar Flap was moved along the edge.
 * @param setArranging Starts or ends Arrange Mode.
 * @param setArrangedShare Remembers a section's arranged share in this orientation; null clears it.
 * @param showBrowser Shows the browser after something was chosen (leaves Fenix's home screen).
 */
class SidebarIntentHandler(
    private val selectTab: (tabId: String) -> Unit,
    private val openNewTab: (isPrivate: Boolean) -> Unit,
    // A Space with no tabs, switched to or just made: its home page, with no search or keyboard.
    private val openSpaceHome: () -> Unit = { openNewTab(false) },
    private val closeTab: (tabId: String) -> Unit = {},
    private val onTabClosed: (isPrivate: Boolean, wasLastTab: Boolean) -> Unit = { _, _ -> },
    private val setChosenMode: (SidebarMode) -> Unit = {},
    private val setRevealed: (Boolean) -> Unit = {},
    private val duplicateTab: (tabId: String) -> Unit = {},
    private val pinTab: (tabId: String) -> PinResult = { PinResult.PrivateRefused },
    private val onPinRefused: (PinResult) -> Unit = {},
    private val openEssential: (url: String, isPrivate: Boolean) -> Unit = { _, _ -> },
    private val unpinEssential: (url: String) -> Unit = {},
    private val loadUrlInTab: (url: String, tabId: String) -> Unit = { _, _ -> },
    private val switchBrowsingMode: (toPrivate: Boolean) -> Unit = {},
    private val moveTab: (tabId: String, targetTabId: String, placeAfter: Boolean) -> Unit = { _, _, _ -> },
    private val toggleMute: (tabId: String) -> Unit = {},
    private val setAnimateModeChanges: (Boolean) -> Unit = {},
    private val setInactiveExpanded: (Boolean) -> Unit = {},
    private val closeTabs: (tabIds: List<String>) -> Unit = {},
    private val moveEssential: (url: String, toIndex: Int) -> Unit = { _, _ -> },
    private val reactivateTab: (tabId: String) -> Unit = {},
    private val navigation: AddressNavigation = AddressNavigation(),
    private val setAddressBarLayout: (AddressBarLayout) -> Unit = {},
    // Defaults to Fenix's home page so handlers built without a setting keep the old behaviour;
    // the app passes the stored setting (Search by default).
    private val newTabBehaviour: () -> NewTabBehaviour = { NewTabBehaviour.HomePage },
    private val switchSpace: (spaceId: String) -> String? = { null },
    private val createSpace: (name: String, icon: String, accent: String, local: Boolean) -> Unit = { _, _, _, _ -> },
    private val editSpace: (spaceId: String, name: String, icon: String, accent: String) -> Unit = { _, _, _, _ -> },
    private val deleteSpace: (spaceId: String) -> Unit = {},
    private val moveSpace: (spaceId: String, toIndex: Int) -> Unit = { _, _ -> },
    private val setSpaceLocal: (spaceId: String, local: Boolean) -> Unit = { _, _ -> },
    private val moveTabToSpace: (tabId: String, spaceId: String) -> String? = { _, _ -> null },
    private val pinToSpace: (tabId: String) -> PinResult = { PinResult.PrivateRefused },
    private val onSpacePinRefused: (PinResult) -> Unit = {},
    private val openPinnedTab: (pinId: String, url: String) -> Unit = { _, _ -> },
    private val unpinPinnedTab: (pinId: String) -> Unit = {},
    private val sleepTabs: (tabIds: List<String>) -> Unit = {},
    private val unloadSpace: (spaceId: String) -> Unit = {},
    private val closeUnpinnedTabs: (spaceId: String) -> Unit = {},
    private val toggleFold: (spaceId: String) -> Unit = {},
    private val renameEssential: (url: String, name: String?) -> Unit = { _, _ -> },
    private val renamePinnedTab: (pinId: String, name: String?) -> Unit = { _, _ -> },
    private val folders: FolderActions = FolderActions(),
    private val movePinnedTab: (pinId: String, targetPinId: String) -> Unit = { _, _ -> },
    private val pinnedToEssentials: (pinId: String) -> PinResult = { PinResult.PrivateRefused },
    private val essentialToSpace: (url: String) -> PinResult = { PinResult.PrivateRefused },
    private val openUrl: (url: String) -> Unit = {},
    private val setDesktopTabsExpanded: (Boolean) -> Unit = {},
    private val setFlapPosition: (Float) -> Unit = {},
    private val setArranging: (Boolean) -> Unit = {},
    private val setArrangedShare: (CollapsedSection, Float?) -> Unit = { _, _ -> },
    private val showBrowser: () -> Unit = {},
    private val sendToDesktop: (spaceId: String, url: String, title: String) -> Unit = { _, _, _ -> },
) {
    /** Handles [intent] for a Sidebar currently showing [state]. */
    fun handle(intent: SidebarIntent, state: SidebarUiState) {
        when (intent) {
            is SidebarIntent.SelectTab -> {
                selectTab(intent.tabId)
                showBrowser()
                dismissIfRevealed(state)
            }
            SidebarIntent.NewTab -> {
                when (newTabBehaviour()) {
                    // A tab only exists once something is chosen in the pop-up.
                    NewTabBehaviour.Search -> navigation.openSearch(true)
                    NewTabBehaviour.HomePage -> openNewTab(state.isPrivate)
                    NewTabBehaviour.Blank -> {
                        navigation.openBlank(state.isPrivate)
                        navigation.openSearch(false)
                    }
                }
                dismissIfRevealed(state)
            }
            is SidebarIntent.CloseTab -> {
                val wasLastTab = state.tabs.singleOrNull()?.id == intent.tabId
                closeTab(intent.tabId)
                onTabClosed(state.isPrivate, wasLastTab)
            }
            SidebarIntent.ToggleMode -> {
                setAnimateModeChanges(true)
                dismissIfRevealed(state)
                state.nextMode?.let(setChosenMode)
            }
            SidebarIntent.KeyboardToggleMode -> {
                setAnimateModeChanges(false)
                dismissIfRevealed(state)
                state.nextMode?.let(setChosenMode)
            }
            is SidebarIntent.SelectEssentialAt ->
                state.essentials.getOrNull(intent.index)?.let { handle(SidebarIntent.SelectEssential(it.url), state) }
            is SidebarIntent.DuplicateTab -> {
                duplicateTab(intent.tabId)
                dismissIfRevealed(state)
            }
            is SidebarIntent.PinTab -> {
                val result = pinTab(intent.tabId)
                if (result != PinResult.Pinned) onPinRefused(result)
            }
            is SidebarIntent.SelectEssential -> {
                val essential = state.essentials.firstOrNull { it.url == intent.url } ?: return
                val linked = essential.linkedTabId
                if (linked != null && !state.isPrivate) selectTab(linked) else openEssential(essential.url, state.isPrivate)
                showBrowser()
                dismissIfRevealed(state)
            }
            is SidebarIntent.UnpinEssential -> unpinEssential(intent.url)
            is SidebarIntent.MoveEssential -> moveEssential(intent.url, intent.toIndex)
            is SidebarIntent.ReactivateTab -> reactivateTab(intent.tabId)
            is SidebarIntent.ResetEssential -> {
                val tabId = state.essentials.firstOrNull { it.url == intent.url }?.linkedTabId ?: return
                loadUrlInTab(intent.url, tabId)
            }
            SidebarIntent.SwitchBrowsingMode -> switchBrowsingMode(!state.isPrivate)
            // Moving down on screen = earlier in browser order, so place *before* the target.
            is SidebarIntent.MoveTab -> moveTab(intent.tabId, intent.targetTabId, !intent.movedDown)
            is SidebarIntent.ToggleMute -> toggleMute(intent.tabId)
            SidebarIntent.ToggleInactiveSection -> state.inactive?.let { setInactiveExpanded(!it.expanded) }
            SidebarIntent.ToggleDesktopTabs -> state.desktopTabs?.let { setDesktopTabsExpanded(!it.expanded) }
            is SidebarIntent.MoveFlap -> setFlapPosition(intent.position.coerceIn(0f, 1f))
            SidebarIntent.StartArranging -> if (state.mode == SidebarMode.Collapsed) setArranging(true)
            SidebarIntent.DoneArranging -> setArranging(false)
            is SidebarIntent.ArrangeSection -> setArrangedShare(intent.section, intent.share.coerceIn(0f, MAX_ARRANGED_SHARE))
            SidebarIntent.ResetArrangement -> CollapsedSection.entries.forEach { setArrangedShare(it, null) }
            is SidebarIntent.OpenDesktopTab -> {
                val open = (state.tabs + state.inactive?.tabs.orEmpty()).firstOrNull { it.url == intent.url }
                if (open != null) selectTab(open.id) else openUrl(intent.url)
                showBrowser()
                dismissIfRevealed(state)
            }
            is SidebarIntent.SendToDesktop -> {
                val space = state.currentSpace
                val tab = state.allTabs.firstOrNull { it.id == intent.tabId }
                if (!state.isPrivate && state.desktopSendingOn && space != null && !space.isLocal && tab != null &&
                    pt.dweep.sideraven.sync.desktopSendableUrl(tab.url)
                ) sendToDesktop(space.id, tab.url, tab.title)
            }
            SidebarIntent.RevealInactiveSection -> {
                setInactiveExpanded(true)
                setRevealed(true)
            }
            SidebarIntent.CloseAllInactive -> {
                state.inactive?.let { section -> closeTabs(section.tabs.map { it.id }) }
                setInactiveExpanded(false)
            }
            SidebarIntent.Reveal -> if (state.mode == SidebarMode.AutoHide) setRevealed(true)
            SidebarIntent.Dismiss -> dismissIfRevealed(state)
            SidebarIntent.OpenMenu -> navigation.openMenu(state.address?.isHome ?: true)
            SidebarIntent.OpenSyncedTabs -> navigation.openSyncedTabs()
            SidebarIntent.OpenSitePanel -> if (state.address?.isHome == false) navigation.openSitePanel()
            SidebarIntent.ToggleAddressBarLayout ->
                setAddressBarLayout(state.chosenLayout.other)
            SidebarIntent.GoBack -> if (state.address?.canGoBack == true) navigation.goBack()
            SidebarIntent.GoForward -> if (state.address?.canGoForward == true) navigation.goForward()
            SidebarIntent.Reload -> navigation.reload()
            SidebarIntent.StopLoading -> navigation.stop()
            SidebarIntent.ShowTabHistory -> if (state.address?.isHome == false) navigation.showHistory()
            is SidebarIntent.OpenSearch -> navigation.openSearch(intent.newTab)
            is SidebarIntent.KeyboardSwitchSpace -> {
                val index = state.spaces.indexOfFirst { it.isCurrent }
                if (index == -1 || state.spaces.size < 2) return
                val target = state.spaces[Math.floorMod(index + intent.step, state.spaces.size)]
                // Keyboard actions never animate.
                setAnimateModeChanges(false)
                showSpace(target.id, state)
            }
            is SidebarIntent.SwitchSpace -> {
                setAnimateModeChanges(true)
                showSpace(intent.spaceId, state)
            }
            is SidebarIntent.CreateSpace -> {
                if (state.isPrivate || intent.name.isBlank()) return
                // Made here while Zen Sync is on, it stays on this device until switched on.
                createSpace(intent.name, intent.icon, intent.accent, state.zenSyncOn)
                openSpaceHome()
                dismissIfRevealed(state)
            }
            is SidebarIntent.EditSpace -> if (intent.name.isNotBlank()) editSpace(intent.spaceId, intent.name, intent.icon, intent.accent)
            // There is always a Space for tabs to live in.
            is SidebarIntent.DeleteSpace -> if (state.spaces.size > 1) deleteSpace(intent.spaceId)
            is SidebarIntent.MoveSpace -> moveSpace(intent.spaceId, intent.toIndex)
            is SidebarIntent.SetSpaceLocal -> setSpaceLocal(intent.spaceId, intent.local)
            is SidebarIntent.PinToSpace -> {
                val result = pinToSpace(intent.tabId)
                if (result != PinResult.Pinned) onSpacePinRefused(result)
            }
            is SidebarIntent.SelectPinnedTab -> {
                val pin = state.pinned.firstOrNull { it.id == intent.pinId } ?: return
                val linked = pin.linkedTabId
                if (linked != null) selectTab(linked) else openPinnedTab(pin.id, pin.url)
                showBrowser()
                dismissIfRevealed(state)
            }
            is SidebarIntent.UnpinPinnedTab -> unpinPinnedTab(intent.pinId)
            is SidebarIntent.ClosePinnedTab -> {
                val tabId = state.pinned.firstOrNull { it.id == intent.pinId }?.linkedTabId ?: return
                closeTab(tabId)
                // Undo brings the tab back, still linked to its pin.
                onTabClosed(false, state.tabs.isEmpty())
            }
            // Unload puts a tab to sleep; never the tab on screen, which would only reload.
            is SidebarIntent.UnloadPinnedTab ->
                state.pinned.firstOrNull { it.id == intent.pinId }?.takeUnless { it.isSelected }?.linkedTabId?.let { sleepTabs(listOf(it)) }
            is SidebarIntent.UnloadEssential ->
                state.essentials.firstOrNull { it.url == intent.url }?.takeUnless { it.isSelected }?.linkedTabId?.let { sleepTabs(listOf(it)) }
            is SidebarIntent.UnloadOthers -> unloadOthersTargets(state, intent.from).takeIf { it.isNotEmpty() }?.let(sleepTabs)
            is SidebarIntent.UnloadTab ->
                state.allTabs.firstOrNull { it.id == intent.tabId }?.takeUnless { it.isSelected }?.let { sleepTabs(listOf(it.id)) }
            is SidebarIntent.UnloadSpace -> unloadSpace(intent.spaceId)
            is SidebarIntent.CloseUnpinnedTabs -> closeUnpinnedTabs(intent.spaceId)
            is SidebarIntent.TogglePinnedFold -> state.currentSpace?.id?.let(toggleFold)
            is SidebarIntent.RenameEssential -> renameEssential(intent.url, intent.name)
            is SidebarIntent.RenamePinnedTab -> renamePinnedTab(intent.pinId, intent.name)
            is SidebarIntent.MovePinnedTab -> movePinnedTab(intent.pinId, intent.targetPinId)
            is SidebarIntent.PinnedToEssentials -> pinnedToEssentials(intent.pinId).takeIf { it != PinResult.Pinned }?.let(onPinRefused)
            is SidebarIntent.EssentialToSpace ->
                if (!state.isPrivate) essentialToSpace(intent.url).takeIf { it != PinResult.Pinned }?.let(onSpacePinRefused)
            is SidebarIntent.CreateFolder -> if (!state.isPrivate && intent.name.isNotBlank()) folders.create(intent.name, intent.icon)
            is SidebarIntent.EditFolder -> if (intent.name.isNotBlank()) folders.edit(intent.folderId, intent.name, intent.icon)
            is SidebarIntent.MoveFolder -> folders.move(intent.folderId, intent.step)
            is SidebarIntent.MoveFolderTo -> folders.moveTo(intent.folderId, intent.targetFolderId)
            is SidebarIntent.DeleteFolder -> folders.delete(intent.folderId, intent.keepPins)
            is SidebarIntent.ToggleFolder -> folders.toggle(intent.folderId)
            is SidebarIntent.MoveIntoFolder -> folders.moveInto(intent.pinId, intent.folderId)
            is SidebarIntent.CloseEssentialTab -> {
                val tabId = state.essentials.firstOrNull { it.url == intent.url }?.linkedTabId ?: return
                closeTab(tabId)
                onTabClosed(state.isPrivate, state.tabs.isEmpty())
            }
            is SidebarIntent.ResetPinnedTab -> {
                val pin = state.pinned.firstOrNull { it.id == intent.pinId } ?: return
                pin.linkedTabId?.let { loadUrlInTab(pin.url, it) }
            }
            is SidebarIntent.MoveTabToSpace -> {
                if (state.isPrivate || state.currentSpace?.id == intent.spaceId) return
                // The tab leaves this Space; if it was selected, the selection stays here.
                moveTabToSpace(intent.tabId, intent.spaceId)?.let(selectTab)
            }
        }
    }

    /** Makes [spaceId] current and selects its tab (or opens a New Tab there). */
    private fun showSpace(
        spaceId: String,
        state: SidebarUiState,
    ) {
        if (state.isPrivate || state.currentSpace?.id == spaceId) return
        val target = switchSpace(spaceId)
        if (target != null) {
            selectTab(target)
        } else {
            openSpaceHome()
            dismissIfRevealed(state)
        }
    }

    /** Choosing a destination lands the user straight on it: the revealed Sidebar gets out of the way. */
    private fun dismissIfRevealed(state: SidebarUiState) {
        if (state.isRevealed) setRevealed(false)
    }
}

/** The current Space's Folders, flattened in display order with their depth. */
private fun sidebarFolders(
    data: SpacesData,
    spaceId: String,
): List<SidebarFolder> {
    val folders = data.foldersIn(spaceId)
    val result = mutableListOf<SidebarFolder>()
    fun visit(
        parentId: String?,
        depth: Int,
    ) {
        folders.filter { it.parentId == parentId }.forEach { folder ->
            val count = data.pinned.count { it.folderId in data.folderAndDescendants(folder.id) }
            result +=
                SidebarFolder(
                    folder.id,
                    folder.name,
                    folder.icon,
                    depth,
                    expanded = folder.id !in data.collapsedFolders,
                    count = count,
                    parentId = folder.parentId,
                )
            visit(folder.id, depth + 1)
        }
    }
    visit(null, 0)
    return result
}

/**
 * The pinned block's rows: each Folder followed (when expanded) by its sub-Folders and Pinned
 * Tabs, then the Pinned Tabs outside any Folder. Empty when the Space has no Folders.
 */
private fun pinnedRows(
    data: SpacesData,
    spaceId: String,
    pinFor: (String) -> SidebarPinnedTab?,
): List<SidebarPinnedRow> {
    val folders = data.foldersIn(spaceId)
    if (folders.isEmpty()) return emptyList()
    val folderIds = folders.map { it.id }.toSet()
    val pins = data.pinnedIn(spaceId)
    val rows = mutableListOf<SidebarPinnedRow>()
    val all = sidebarFolders(data, spaceId).associateBy { it.id }
    fun visit(
        parentId: String?,
        depth: Int,
    ) {
        folders.filter { it.parentId == parentId }.forEach { folder ->
            val shown = all.getValue(folder.id)
            rows += SidebarPinnedRow.FolderRow(shown)
            if (shown.expanded) {
                visit(folder.id, depth + 1)
                pins.filter { it.folderId == folder.id }.forEach { pin -> pinFor(pin.id)?.let { rows += SidebarPinnedRow.PinRow(it, depth + 1) } }
            }
        }
    }
    visit(null, 0)
    // Pins outside any Folder (or in a Folder that no longer exists) sit at the top level.
    pins.filter { it.folderId == null || it.folderId !in folderIds }.forEach { pin -> pinFor(pin.id)?.let { rows += SidebarPinnedRow.PinRow(it, 0) } }
    return rows
}

/** Folder operations for [SidebarIntentHandler]; the app wires them to [Spaces]. */
class FolderActions(
    val create: (name: String, icon: String) -> Unit = { _, _ -> },
    val edit: (folderId: String, name: String, icon: String) -> Unit = { _, _, _ -> },
    val move: (folderId: String, step: Int) -> Unit = { _, _ -> },
    val moveTo: (folderId: String, targetFolderId: String) -> Unit = { _, _ -> },
    val delete: (folderId: String, keepPins: Boolean) -> Unit = { _, _ -> },
    val toggle: (folderId: String) -> Unit = {},
    val moveInto: (pinId: String, folderId: String?) -> Unit = { _, _ -> },
) {
    companion object {
        fun of(spaces: Spaces) =
            FolderActions(
                create = { name, icon -> spaces.createFolder(name, icon) },
                edit = spaces::editFolder,
                move = spaces::moveFolder,
                moveTo = spaces::moveFolderTo,
                delete = spaces::deleteFolder,
                toggle = spaces::toggleFolder,
                moveInto = spaces::moveIntoFolder,
            )
    }
}

/** What the Sidebar Address Bar does; the app wires it to Fenix's session use cases. */
class AddressNavigation(
    val goBack: () -> Unit = {},
    val goForward: () -> Unit = {},
    val reload: () -> Unit = {},
    val stop: () -> Unit = {},
    val showHistory: () -> Unit = {},
    val openSearch: (newTab: Boolean) -> Unit = {},
    /** Opens an empty tab (New Tab Behaviour "Blank page"). */
    val openBlank: (isPrivate: Boolean) -> Unit = {},
    /** Fenix's main menu (the Sidebar layout's ⋮); true on the home screen. */
    val openMenu: (onHome: Boolean) -> Unit = {},
    /** Fenix's site panel for the selected tab. */
    val openSitePanel: () -> Unit = {},
    /** Fenix's synced-tabs page. */
    val openSyncedTabs: () -> Unit = {},
)

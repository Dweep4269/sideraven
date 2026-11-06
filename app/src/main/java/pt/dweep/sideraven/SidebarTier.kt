/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import android.app.Activity
import org.mozilla.fenix.customtabs.ExternalAppBrowserActivity
import android.content.Context
import android.content.res.Configuration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Minimum window width, in dp, for any Qualifying Device tier. */
internal const val QUALIFYING_WIDTH_DP = 600

/**
 * Which Qualifying Device tier the current app window falls into.
 * Always derived from the current window, never from the physical screen.
 */
enum class SidebarTier {
    /** Window is narrower than 600dp and the phone Sidebar is off: no Sidebar, plain Fenix with the tab counter. */
    None,

    /** Window is narrower than 600dp (a phone) and the phone Sidebar is on: Auto-Hide Mode (first launch) or Collapsed. */
    Phone,

    /** Window's smallest width < 600dp but current width >= 600dp, e.g. a large phone in landscape. */
    LargePhone,

    /** Window's smallest width >= 600dp. */
    Tablet,
    ;

    companion object {
        /**
         * Resolves the tier from window metrics in dp.
         *
         * @param widthDp Current window width.
         * @param smallestWidthDp Window's smallest width (min of width and height).
         * @param phoneSidebar The "Sidebar on phones" setting.
         */
        fun resolve(widthDp: Int, smallestWidthDp: Int, phoneSidebar: Boolean): SidebarTier =
            when {
                widthDp < QUALIFYING_WIDTH_DP -> if (phoneSidebar) Phone else None
                smallestWidthDp >= QUALIFYING_WIDTH_DP -> Tablet
                else -> LargePhone
            }

        /** Resolves the tier for the window a [Configuration] describes. */
        fun from(
            configuration: Configuration,
            phoneSidebar: Boolean,
        ): SidebarTier = resolve(configuration.screenWidthDp, configuration.smallestScreenWidthDp, phoneSidebar)

        /** Resolves the tier for the window a UI [Context] belongs to. */
        fun from(context: Context): SidebarTier =
            from(context.resources.configuration, PreferencesSidebarModeStore.get(context).phoneSidebar.value)
    }
}

/**
 * Whether and how the Sidebar shows, shared by the host and the `SIDERAVEN-HOOK`s (the toolbar's
 * through [SidebarToolbar]).
 */
object SidebarGate {
    /**
     * The Address Bar Layout in effect: the Sidebar layout when chosen and the Sidebar is shown, in
     * Persistent Mode, or in Auto-Hide Mode while the Sidebar can be pulled out ([revealable]: its handle
     * or the edge swipe). Everywhere else Fenix's toolbar (the Top layout), which in Auto-Hide Mode
     * also holds the Sidebar Button.
     */
    fun effectiveLayout(
        chosen: AddressBarLayout,
        sidebarShown: Boolean,
        mode: SidebarMode?,
        revealable: Boolean,
    ): AddressBarLayout =
        if (chosen == AddressBarLayout.Sidebar && sidebarShown && movesAddress(mode, revealable)) {
            AddressBarLayout.Sidebar
        } else {
            AddressBarLayout.Top
        }

    /** Whether the Sidebar layout can apply in [mode]: the layout button is offered only where it can act. */
    fun movesAddress(
        mode: SidebarMode?,
        revealable: Boolean,
    ): Boolean = mode == SidebarMode.Persistent || (mode == SidebarMode.AutoHide && revealable)

    /** Whether Fenix's toolbar is hidden right now (the Sidebar layout is in effect); the hooks follow it. */
    val toolbarHidden = MutableStateFlow(false)

    /**
     * Whether [activity] has a Sidebar: every Fenix window except another app's tab (a Custom Tab).
     * The flows here belong to the main window, so a Custom Tab neither sets nor follows them.
     */
    fun hostsSidebar(activity: Activity?): Boolean = activity != null && activity !is ExternalAppBrowserActivity

    /** Tabs put to sleep by Unload, until they are opened again. */
    val sleepingTabs = MutableStateFlow<Set<String>>(emptySet())

    /** The frame's look, for the page corners at the toolbar's edge ([PageCornersDrawable]). */
    val frameLook = MutableStateFlow(FrameLook())

    /** Whether the Sidebar is shown for this tier: on both Qualifying Device tiers. */
    fun showsSidebar(tier: SidebarTier): Boolean = tier != SidebarTier.None

    /** Whether the Sidebar is shown in the window a UI [Context] belongs to. */
    fun showsSidebar(context: Context): Boolean = showsSidebar(SidebarTier.from(context))

    /**
     * Whether the toolbar's tab counter slot shows: the tab counter when there's no Sidebar, or
     * when [tabButtons] keeps it beside one; otherwise beside a Sidebar only in Auto-Hide Mode with
     * the Sidebar Flap off, as the Sidebar Button, so there is always a visible way to open it.
     * Elsewhere the Sidebar covers it.
     */
    fun showsTabCounter(
        tier: SidebarTier,
        chosenMode: SidebarMode?,
        flapEnabled: Boolean,
        tabButtons: Boolean = false,
    ): Boolean = !showsSidebar(tier) || tabButtons || (SidebarMode.effective(chosenMode, tier) == SidebarMode.AutoHide && !flapEnabled)

    /** [showsTabCounter] for the window a UI [Context] belongs to. */
    fun showsTabCounter(context: Context): Boolean =
        PreferencesSidebarModeStore.get(context).let { store ->
            showsTabCounter(SidebarTier.from(context), store.chosenMode.value, store.flapEnabled.value, store.toolbarTabButtons.value)
        }

    /**
     * Whether the tab counter slot is drawn as the Sidebar Button: beside a Sidebar, unless
     * [tabButtons] keeps the real tab counter (which still opens the Sidebar in Auto-Hide Mode).
     */
    fun showsSidebarButton(
        tier: SidebarTier,
        tabButtons: Boolean,
    ): Boolean = showsSidebar(tier) && !tabButtons

    fun showsSidebarButton(context: Context): Boolean =
        showsSidebarButton(SidebarTier.from(context), PreferencesSidebarModeStore.get(context).toolbarTabButtons.value)

    /**
     * Whether Fenix's quick-action button is hidden: beside a Sidebar, when it is New Tab (the Sidebar
     * has one), unless [tabButtons] keeps it.
     */
    fun hidesShortcut(
        tier: SidebarTier,
        isNewTab: Boolean,
        tabButtons: Boolean = false,
    ): Boolean = showsSidebar(tier) && isNewTab && !tabButtons

    fun hidesShortcut(
        context: Context,
        isNewTab: Boolean,
    ): Boolean = hidesShortcut(SidebarTier.from(context), isNewTab, PreferencesSidebarModeStore.get(context).toolbarTabButtons.value)

    /** The Sidebar's surface colour while it shows (null otherwise): Fenix's toolbar takes it. */
    val sidebarSurface = MutableStateFlow<Int?>(null)

    /** Emits whenever the Sidebar Mode, the Flap or the toolbar's tab buttons change, so the toolbar can rebuild its buttons. */
    fun modeChanges(context: Context): Flow<Any?> =
        PreferencesSidebarModeStore.get(context).let { store ->
            kotlinx.coroutines.flow.combine(store.chosenMode.flow, store.flapEnabled.flow, store.phoneSidebar.flow, store.toolbarTabButtons.flow) { mode, flap, phone, buttons ->
                listOf(mode, flap, phone, buttons)
            }
        }

    /** Whether the Auto-Hide Sidebar is currently revealed. One Sidebar per process. */
    val revealed = MutableStateFlow(false)

    /**
     * The tab counter's click in Auto-Hide Mode: reveal the Sidebar. Returns false when not in
     * Auto-Hide Mode, so the counter keeps opening the Tab Tray.
     */
    fun revealIfAutoHide(context: Context): Boolean {
        val tier = SidebarTier.from(context)
        val mode = SidebarMode.effective(PreferencesSidebarModeStore.get(context).chosenMode.value, tier)
        if (!showsSidebar(tier) || mode != SidebarMode.AutoHide) return false
        revealed.value = true
        return true
    }

    /**
     * Fenix's horizontal tablet Tab Strip never shows in Sideraven: wherever it would be
     * eligible, the Sidebar replaces it (suppressed, not deleted).
     */
    fun allowsTabStrip(): Boolean = false
}

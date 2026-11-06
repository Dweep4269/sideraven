/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.settings

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import androidx.preference.SwitchPreferenceCompat
import org.mozilla.fenix.R
import pt.dweep.sideraven.AddressBarLayout
import pt.dweep.sideraven.AnimationLevel
import pt.dweep.sideraven.CloseButtons
import pt.dweep.sideraven.ControlPlace
import pt.dweep.sideraven.EssentialsPerRow
import pt.dweep.sideraven.FullScreen
import pt.dweep.sideraven.NewTabBehaviour
import pt.dweep.sideraven.NewTabPosition
import pt.dweep.sideraven.OptionalControl
import pt.dweep.sideraven.PreferencesSidebarModeStore
import pt.dweep.sideraven.Setting
import pt.dweep.sideraven.SidebarGate
import pt.dweep.sideraven.SidebarMode
import pt.dweep.sideraven.SidebarModeStore
import pt.dweep.sideraven.SidebarSide
import pt.dweep.sideraven.SleepTimer
import pt.dweep.sideraven.SidebarTier
import pt.dweep.sideraven.SpacesShown
import pt.dweep.sideraven.SidebarWidth
import pt.dweep.sideraven.TabListDirection
import pt.dweep.sideraven.TabRows

/**
 * The categories of Sideraven Settings, each its own screen in [SideravenSettingsFragment]. On/off is a
 * switch; two or three short options are segmented buttons; a choice that changes the whole screen is a
 * picture picker; a longer choice is a row showing its value that opens a dialog. All but Zen Sync and
 * Backup configure the Sidebar, so they show only where there is one.
 */
object SidebarPreferences {
    const val POPUP_KEYBOARD_KEY = "sideraven_popup_keyboard"
    const val POPUP_OUTSIDE_KEY = "sideraven_popup_outside"
    const val MODE_KEY = "sideraven_sidebar_mode"
    const val PLACE_KEY_PREFIX = "sideraven_place"
    const val PHONE_SIDEBAR_KEY = "sideraven_phone_sidebar"
    const val SWIPE_ZONE_KEY = "sideraven_sidebar_swipe_zone"
    const val INACTIVE_KEY = "sideraven_sidebar_inactive"
    const val FLAP_KEY = "sideraven_sidebar_flap"
    const val NEW_TAB_KEY = "sideraven_sidebar_new_tab"
    const val LAYOUT_KEY = "sideraven_sidebar_layout"
    const val WIDTH_KEY = "sideraven_sidebar_width"
    const val SIDE_KEY = "sideraven_sidebar_side"
    const val NEW_TAB_POSITION_KEY = "sideraven_new_tab_position"
    const val TAB_LIST_DIRECTION_KEY = "sideraven_tab_list_direction"
    const val SPACES_SHOWN_KEY = "sideraven_spaces_shown"
    const val TAB_ROWS_KEY = "sideraven_tab_rows"
    const val HAPTICS_KEY = "sideraven_haptics"
    const val ANIMATIONS_KEY = "sideraven_animations"
    const val FRAME_KEY = "sideraven_rounded_frame"
    const val ACCENT_KEY = "sideraven_accent"
    const val ESSENTIALS_PER_ROW_KEY = "sideraven_essentials_per_row"
    const val SWIPE_TO_CLOSE_KEY = "sideraven_swipe_to_close"
    const val CLOSE_BUTTONS_KEY = "sideraven_close_buttons"
    const val ADDRESS_SWIPE_NEW_TAB_KEY = "sideraven_address_swipe_new_tab"
    const val ADDRESS_SWIPE_TABS_KEY = "sideraven_address_swipe_tabs"
    const val TOOLBAR_TAB_BUTTONS_KEY = "sideraven_toolbar_tab_buttons"
    const val SLEEP_TIMER_KEY = "sideraven_sleep_timer"
    const val SPACE_THEMES_KEY = "sideraven_space_themes"
    const val FULL_SCREEN_KEY = "sideraven_full_screen"

    /** Fills the screen of one [category]. Called from Sideraven Settings' onResume. */
    fun install(
        fragment: PreferenceFragmentCompat,
        category: SettingsCategory,
    ) {
        val context = fragment.requireContext()
        val screen = fragment.preferenceScreen ?: return
        install(context, screen, category, SidebarTier.from(context), PreferencesSidebarModeStore.get(context)) {
            SidebarTier.from(context)
        }
    }

    /**
     * Fills [screen] for [category] at [tier]. [currentTier] reads the tier again after a setting
     * that changes it ("Use the sidebar on phones"), so the screen can rebuild for the new one.
     */
    internal fun install(
        context: Context,
        screen: PreferenceGroup,
        category: SettingsCategory,
        tier: SidebarTier,
        store: SidebarModeStore,
        currentTier: () -> SidebarTier = { tier },
    ) {
        fun group(
            key: String,
            title: Int,
            about: Int? = null,
        ) = group(context, screen, key, title, about)
        when (category) {
            SettingsCategory.Sidebar -> {
                fun phoneSwitch() {
                    val phones = group("sideraven_group_phone", R.string.sideraven_group_phone)
                    val switch = switchFor(context, phones, PHONE_SIDEBAR_KEY, store.phoneSidebar, R.string.sideraven_settings_phone_sidebar, R.string.sideraven_settings_phone_sidebar_summary)
                    val save = switch.onPreferenceChangeListener
                    // It changes this window's tier: rebuild the screen for the new one, once the switch has settled.
                    switch.setOnPreferenceChangeListener { preference, value ->
                        (save?.onPreferenceChange(preference, value) ?: true).also {
                            Handler(Looper.getMainLooper()).post {
                                screen.removeAll()
                                install(context, screen, category, currentTier(), store, currentTier)
                            }
                        }
                    }
                }
                // A phone with the Sidebar off: only the switch that brings it back.
                if (!SidebarGate.showsSidebar(tier)) {
                    phoneSwitch()
                    return
                }
                bindMode(context, group("sideraven_group_mode", R.string.sideraven_group_mode), tier, store)
                val place = group("sideraven_group_place", R.string.sideraven_group_place)
                bindSide(context, place, store)
                bindWidth(context, place, store)
                val buttons = group("sideraven_group_buttons", R.string.sideraven_group_buttons)
                bindSpacesShown(context, buttons, store)
                bindControlPlaces(context, buttons, store)
                val opening = group("sideraven_group_opening", R.string.sideraven_group_opening)
                // Phones keep their own Swipe Zone setting (off by default); this shows the one in effect here.
                switchFor(context, opening, SWIPE_ZONE_KEY, store.swipeZone(tier), R.string.sideraven_settings_swipe_zone, R.string.sideraven_settings_swipe_zone_summary)
                switchFor(context, opening, FLAP_KEY, store.flapEnabled, R.string.sideraven_settings_flap, R.string.sideraven_settings_flap_summary)
                phoneSwitch()
            }
            SettingsCategory.Tabs -> {
                val list = group("sideraven_group_tab_list", R.string.sideraven_group_tab_list)
                val direction = bindTabListDirection(context, list, store) {}
                bindTabRows(context, list, store)
                val position = bindNewTabPosition(context, list, store)
                // Choosing Bottom up moves new tabs to the bottom too: show it.
                direction.onChosen = { position.show(store.newTabPosition.value) }
                bindNewTab(context, list, store)
                switchFor(context, list, INACTIVE_KEY, store.showInactiveSection, R.string.sideraven_settings_inactive, R.string.sideraven_settings_inactive_summary)
                val closing = group("sideraven_group_closing", R.string.sideraven_group_closing)
                bindCloseButtons(context, closing, store)
                switchFor(context, closing, SWIPE_TO_CLOSE_KEY, store.swipeToClose, R.string.sideraven_settings_swipe_close, R.string.sideraven_settings_swipe_close_summary)
                bindSleepTimer(context, closing, store)
                bindEssentialsPerRow(context, group("sideraven_group_essentials", R.string.sideraven_essentials), store)
            }
            SettingsCategory.AddressBar -> {
                val layout = group("sideraven_group_layout", R.string.sideraven_settings_layout)
                val tabButtons = bindToolbarTabButtons(context, layout, store)
                bindLayout(context, layout, store) { tabButtons.isVisible = it == AddressBarLayout.Top }
                val gestures = group("sideraven_group_gestures", R.string.sideraven_group_gestures)
                switchFor(context, gestures, ADDRESS_SWIPE_NEW_TAB_KEY, store.addressSwipeNewTab, R.string.sideraven_settings_address_swipe_new_tab, null)
                switchFor(context, gestures, ADDRESS_SWIPE_TABS_KEY, store.addressSwipeTabs, R.string.sideraven_settings_address_swipe_tabs, null)
                bindPopupSwitches(context, group("sideraven_group_popup", R.string.sideraven_settings_popup), store)
            }
            SettingsCategory.LookAndFeel -> {
                val accent = group("sideraven_group_accent", R.string.sideraven_group_accent, R.string.sideraven_group_accent_about)
                if (accent.findPreference<AccentSwatchesPreference>(ACCENT_KEY) == null) {
                    accent.addPreference(AccentSwatchesPreference(context, store.accent).apply { key = ACCENT_KEY })
                }
                val theme = group("sideraven_group_theme", R.string.sideraven_group_theme)
                switchFor(context, theme, SPACE_THEMES_KEY, store.spaceThemes, R.string.sideraven_settings_space_themes, R.string.sideraven_settings_space_themes_summary)
                switchFor(context, theme, FRAME_KEY, store.roundedFrame, R.string.sideraven_settings_frame, R.string.sideraven_settings_frame_summary)
                bindFullScreen(context, group("sideraven_group_screen", R.string.sideraven_group_screen), store)
                val feel = group("sideraven_group_feel", R.string.sideraven_group_feel)
                bindAnimations(context, feel, store)
                switchFor(context, feel, HAPTICS_KEY, store.haptics, R.string.sideraven_settings_haptics, R.string.sideraven_settings_haptics_summary)
            }
            // Their own screens: ZenSyncPreferences and BackupPreferences.
            SettingsCategory.ZenSync, SettingsCategory.Backup -> Unit
        }
    }

    /**
     * Finds or adds one headed group of a category screen. [about] explains it in a first line, only
     * where its settings can't say it themselves.
     */
    internal fun group(
        context: Context,
        screen: PreferenceGroup,
        key: String,
        title: Int,
        about: Int?,
    ): PreferenceCategory {
        val group =
            screen.findPreference(key)
                ?: PreferenceCategory(context).apply {
                    this.key = key
                    this.title = context.getString(title)
                    layoutResource = R.layout.preference_cat_style
                    isIconSpaceReserved = false
                    order = screen.preferenceCount
                    screen.addPreference(this)
                    about?.let {
                        addPreference(
                            Preference(context).apply {
                                this.key = "$key.about"
                                summary = context.getString(it)
                                isSelectable = false
                                isIconSpaceReserved = false
                                order = -1
                            },
                        )
                    }
                }
        return group
    }

    /** A boolean [Setting] as a switch: finds or adds it, shows the value and writes changes. */
    internal fun switchFor(
        context: Context,
        group: PreferenceGroup,
        key: String,
        setting: Setting<Boolean>,
        title: Int,
        summary: Int?,
    ): SwitchPreferenceCompat = switch(context, group, key, title, summary, null, setting.value, setting::set)

    /**
     * A switch for [value], writing changes through [onChange]. [summaryOff] replaces [summary] while
     * it's off (where a Sidebar button went, for example).
     */
    private fun switch(
        context: Context,
        group: PreferenceGroup,
        key: String,
        title: Int,
        summary: Int?,
        summaryOff: Int?,
        value: Boolean,
        onChange: (Boolean) -> Unit,
    ): SwitchPreferenceCompat {
        val preference =
            group.findPreference(key)
                ?: SwitchPreferenceCompat(context).apply {
                    this.key = key
                    this.title = context.getString(title)
                    summary?.let { summaryOn = context.getString(it) }
                    (summaryOff ?: summary)?.let { this.summaryOff = context.getString(it) }
                    isPersistent = false
                    isIconSpaceReserved = false
                    group.addPreference(this)
                }
        preference.isChecked = value
        preference.setOnPreferenceChangeListener { _, newValue ->
            onChange(newValue as Boolean)
            true
        }
        return preference
    }

    /** The Search Pop-up's dismiss rules: hiding the keyboard, and tapping outside. */
    internal fun bindPopupSwitches(
        context: Context,
        group: PreferenceGroup,
        store: SidebarModeStore,
    ): Pair<SwitchPreferenceCompat, SwitchPreferenceCompat> =
        switchFor(context, group, POPUP_KEYBOARD_KEY, store.popupCloseOnKeyboardHide, R.string.sideraven_settings_popup_keyboard, R.string.sideraven_settings_popup_keyboard_summary) to
            switchFor(context, group, POPUP_OUTSIDE_KEY, store.popupCloseOnTapOutside, R.string.sideraven_settings_popup_outside, null)

    /** The Sidebar modes available at [tier], as pictures; the stored choice is shown, as with the Toggle. */
    internal fun bindMode(
        context: Context,
        group: PreferenceGroup,
        tier: SidebarTier,
        store: SidebarModeStore,
    ): PicturePickerPreference<SidebarMode> {
        val right = store.sidebarSide.value == SidebarSide.Right
        val picker =
            picker(
                context,
                group,
                MODE_KEY,
                R.string.sideraven_group_mode,
                SidebarMode.entries.map { Option(it, labelFor(it), pictureFor(it, right), captionFor(it)) },
                SidebarMode.effective(store.chosenMode.value, tier) ?: SidebarMode.available(tier).first(),
                store.chosenMode::set,
            )
        picker.offered = SidebarMode.available(tier)
        return picker
    }

    fun labelFor(mode: SidebarMode): Int =
        when (mode) {
            SidebarMode.Persistent -> R.string.sideraven_mode_persistent
            SidebarMode.Collapsed -> R.string.sideraven_mode_collapsed
            SidebarMode.AutoHide -> R.string.sideraven_mode_autohide
        }

    private fun captionFor(mode: SidebarMode): Int =
        when (mode) {
            SidebarMode.Persistent -> R.string.sideraven_mode_persistent_caption
            SidebarMode.Collapsed -> R.string.sideraven_mode_collapsed_caption
            SidebarMode.AutoHide -> R.string.sideraven_mode_autohide_caption
        }

    private fun pictureFor(
        mode: SidebarMode,
        right: Boolean,
    ): Int =
        when (mode) {
            SidebarMode.Persistent -> if (right) R.drawable.sideraven_ic_sidebar_persistent_right_24 else R.drawable.sideraven_ic_sidebar_persistent_24
            SidebarMode.Collapsed -> if (right) R.drawable.sideraven_ic_sidebar_collapsed_right_24 else R.drawable.sideraven_ic_sidebar_collapsed_24
            SidebarMode.AutoHide -> if (right) R.drawable.sideraven_ic_sidebar_autohide_right_24 else R.drawable.sideraven_ic_sidebar_autohide_24
        }

    /** Where new tabs appear: the top or the bottom of the Tab List. */
    internal fun bindNewTabPosition(
        context: Context,
        group: PreferenceGroup,
        store: SidebarModeStore,
    ) = segmented(
        context,
        group,
        NEW_TAB_POSITION_KEY,
        R.string.sideraven_settings_new_tab_position,
        listOf(Option(NewTabPosition.Top, R.string.sideraven_new_tab_top), Option(NewTabPosition.Bottom, R.string.sideraven_new_tab_bottom)),
        store.newTabPosition,
    )

    /**
     * Tab List Direction: from the top, or from the bottom by the address bar. Bottom up also sends new
     * tabs to the bottom, so the newest tab is the one nearest the thumb; [onChosen] runs after a pick.
     */
    internal fun bindTabListDirection(
        context: Context,
        group: PreferenceGroup,
        store: SidebarModeStore,
        onChosen: () -> Unit,
    ): SegmentedPreference<TabListDirection> {
        val row =
            findOrAdd(group, TAB_LIST_DIRECTION_KEY, store.tabListDirection.value) {
                SegmentedPreference(
                    context,
                    listOf(
                        Option(TabListDirection.TopDown, R.string.sideraven_tab_list_top_down),
                        Option(TabListDirection.BottomUp, R.string.sideraven_tab_list_bottom_up),
                    ),
                    store.tabListDirection.value,
                    { direction ->
                        store.tabListDirection.set(direction)
                        if (direction == TabListDirection.BottomUp) store.newTabPosition.set(NewTabPosition.Bottom)
                    },
                    { if (it == TabListDirection.BottomUp) R.string.sideraven_tab_list_bottom_up_summary else null },
                ).apply { title = context.getString(R.string.sideraven_settings_tab_list_direction) }
            }
        row.onChosen = onChosen
        return row
    }

    /** Which edge the Sidebar docks on. */
    internal fun bindSide(
        context: Context,
        group: PreferenceGroup,
        store: SidebarModeStore,
    ) = segmented(
        context,
        group,
        SIDE_KEY,
        R.string.sideraven_settings_side,
        listOf(Option(SidebarSide.Left, R.string.sideraven_side_left), Option(SidebarSide.Right, R.string.sideraven_side_right)),
        store.sidebarSide,
    )

    /** The Sidebar's width: narrow, standard or wide. */
    internal fun bindWidth(
        context: Context,
        group: PreferenceGroup,
        store: SidebarModeStore,
    ) = segmented(
        context,
        group,
        WIDTH_KEY,
        R.string.sideraven_group_width,
        listOf(
            Option(SidebarWidth.Compact, R.string.sideraven_width_compact),
            Option(SidebarWidth.Standard, R.string.sideraven_width_standard),
            Option(SidebarWidth.Wide, R.string.sideraven_width_wide),
        ),
        store.sidebarWidth,
    )

    /** Buttons in the sidebar: a switch per Optional Control; off puts it in the ⋮ menu. */
    internal fun bindControlPlaces(
        context: Context,
        group: PreferenceGroup,
        store: SidebarModeStore,
    ): List<SwitchPreferenceCompat> =
        OptionalControl.entries.map { control ->
            val setting = store.controlPlace(control)
            switch(
                context = context,
                group = group,
                key = "$PLACE_KEY_PREFIX.${control.name}",
                title =
                    when (control) {
                        OptionalControl.SyncedTabs -> R.string.sideraven_button_synced_tabs
                        OptionalControl.Toggle -> R.string.sideraven_button_change_mode
                        OptionalControl.LayoutButton -> R.string.sideraven_button_move_address
                    },
                summary = null,
                summaryOff = R.string.sideraven_button_in_menu,
                value = setting.value == ControlPlace.InSidebar,
                onChange = { inSidebar -> setting.set(if (inSidebar) ControlPlace.InSidebar else ControlPlace.InMenu) },
            )
        }

    /**
     * "Spaces shown as": the 1.2 icon row, or the Space Header. The Header is the default; the icon
     * row stays one choice away, so nothing is taken away.
     */
    internal fun bindSpacesShown(
        context: Context,
        group: PreferenceGroup,
        store: SidebarModeStore,
    ) = segmented(
        context,
        group,
        SPACES_SHOWN_KEY,
        R.string.sideraven_settings_spaces_shown,
        listOf(
            Option(SpacesShown.Header, R.string.sideraven_spaces_header),
            Option(SpacesShown.IconRow, R.string.sideraven_spaces_icon_row),
        ),
        store.spacesShown,
    )

    /** How tall tab rows are. */
    internal fun bindTabRows(        context: Context,
        group: PreferenceGroup,
        store: SidebarModeStore,
    ) = segmented(
        context,
        group,
        TAB_ROWS_KEY,
        R.string.sideraven_group_rows,
        listOf(Option(TabRows.Comfortable, R.string.sideraven_tab_rows_comfortable), Option(TabRows.Compact, R.string.sideraven_tab_rows_compact)),
        store.tabRows,
    )

    /** How much the Sidebar animates; the line under the title says what the choice means. */
    internal fun bindAnimations(
        context: Context,
        group: PreferenceGroup,
        store: SidebarModeStore,
    ) = segmented(
        context,
        group,
        ANIMATIONS_KEY,
        R.string.sideraven_group_animations,
        listOf(
            Option(AnimationLevel.Full, R.string.sideraven_animations_full),
            Option(AnimationLevel.Reduced, R.string.sideraven_animations_reduced),
            Option(AnimationLevel.Off, R.string.sideraven_animations_off),
        ),
        store.animations,
    ) {
        when (it) {
            AnimationLevel.Full -> R.string.sideraven_animations_full_summary
            AnimationLevel.Reduced -> R.string.sideraven_animations_reduced_summary
            AnimationLevel.Off -> R.string.sideraven_animations_off_summary
        }
    }

    /** Which system bars are hidden: none, the status bar, or both. */
    internal fun bindFullScreen(
        context: Context,
        group: PreferenceGroup,
        store: SidebarModeStore,
    ) = segmented(
        context,
        group,
        FULL_SCREEN_KEY,
        R.string.sideraven_settings_full_screen,
        listOf(
            Option(FullScreen.Off, R.string.sideraven_full_screen_off),
            Option(FullScreen.StatusBar, R.string.sideraven_full_screen_status),
            Option(FullScreen.Both, R.string.sideraven_full_screen_both),
        ),
        store.fullScreen,
    ) {
        when (it) {
            FullScreen.Off -> null
            FullScreen.StatusBar -> R.string.sideraven_full_screen_status_summary
            FullScreen.Both -> R.string.sideraven_full_screen_both_summary
        }
    }

    /** How many Essentials sit in a row. */
    internal fun bindEssentialsPerRow(
        context: Context,
        group: PreferenceGroup,
        store: SidebarModeStore,
    ) = choiceRow(
        context,
        group,
        ESSENTIALS_PER_ROW_KEY,
        R.string.sideraven_group_per_row,
        listOf(
            Option(EssentialsPerRow.Auto, R.string.sideraven_essentials_auto),
            Option(EssentialsPerRow.Three, R.string.sideraven_essentials_three),
            Option(EssentialsPerRow.Four, R.string.sideraven_essentials_four),
            Option(EssentialsPerRow.Five, R.string.sideraven_essentials_five),
        ),
        store.essentialsPerRow,
    )

    /** A close button on every tab, or (off) only on the selected one. */
    internal fun bindCloseButtons(
        context: Context,
        group: PreferenceGroup,
        store: SidebarModeStore,
    ): SwitchPreferenceCompat =
        switch(
            context = context,
            group = group,
            key = CLOSE_BUTTONS_KEY,
            title = R.string.sideraven_settings_close_every,
            summary = null,
            summaryOff = R.string.sideraven_settings_close_every_off,
            value = store.closeButtons.value == CloseButtons.EveryTab,
            onChange = { every -> store.closeButtons.set(if (every) CloseButtons.EveryTab else CloseButtons.SelectedTab) },
        )

    /** Auto-Sleep's timer: how long a tab goes unused before it sleeps. */
    internal fun bindSleepTimer(
        context: Context,
        group: PreferenceGroup,
        store: SidebarModeStore,
    ) = choiceRow(
        context,
        group,
        SLEEP_TIMER_KEY,
        R.string.sideraven_settings_sleep_timer,
        listOf(
            Option(SleepTimer.Off, R.string.sideraven_sleep_off),
            Option(SleepTimer.Minutes15, R.string.sideraven_sleep_15m),
            Option(SleepTimer.Minutes30, R.string.sideraven_sleep_30m),
            Option(SleepTimer.Hour1, R.string.sideraven_sleep_1h),
            Option(SleepTimer.Hours6, R.string.sideraven_sleep_6h),
        ),
        store.sleepTimer,
    )

    /** "New tab opens": the Search Pop-up, Fenix's home page, or a blank page. */
    internal fun bindNewTab(
        context: Context,
        group: PreferenceGroup,
        store: SidebarModeStore,
    ) = choiceRow(
        context,
        group,
        NEW_TAB_KEY,
        R.string.sideraven_settings_new_tab,
        listOf(
            Option(NewTabBehaviour.Search, R.string.sideraven_new_tab_search),
            Option(NewTabBehaviour.HomePage, R.string.sideraven_new_tab_home),
            Option(NewTabBehaviour.Blank, R.string.sideraven_new_tab_blank),
        ),
        store.newTabBehaviour,
    )

    /** The address bar's place, as pictures: Fenix's toolbar beside the Sidebar, or only the Sidebar. */
    internal fun bindLayout(
        context: Context,
        group: PreferenceGroup,
        store: SidebarModeStore,
        onChosen: (AddressBarLayout) -> Unit = {},
    ): PicturePickerPreference<AddressBarLayout> {
        val right = store.sidebarSide.value == SidebarSide.Right
        return picker(
            context,
            group,
            LAYOUT_KEY,
            R.string.sideraven_settings_layout,
            listOf(
                Option(
                    AddressBarLayout.Top,
                    R.string.sideraven_layout_top,
                    if (right) R.drawable.sideraven_ic_address_in_toolbar_right_24 else R.drawable.sideraven_ic_address_in_toolbar_24,
                    R.string.sideraven_layout_top_caption,
                ),
                Option(
                    AddressBarLayout.Sidebar,
                    R.string.sideraven_layout_sidebar,
                    if (right) R.drawable.sideraven_ic_address_in_sidebar_right_24 else R.drawable.sideraven_ic_address_in_sidebar_24,
                    R.string.sideraven_layout_sidebar_caption,
                ),
            ),
            store.addressBarLayout.value,
        ) {
            store.addressBarLayout.set(it)
            onChosen(it)
        }
    }

    /**
     * Fenix's tab counter and New tab kept in its toolbar beside a Sidebar (issue #7). Only the
     * Sidebar and toolbar layout has that toolbar, so the switch shows only with it.
     */
    internal fun bindToolbarTabButtons(
        context: Context,
        group: PreferenceGroup,
        store: SidebarModeStore,
    ): SwitchPreferenceCompat =
        switchFor(context, group, TOOLBAR_TAB_BUTTONS_KEY, store.toolbarTabButtons, R.string.sideraven_settings_toolbar_tab_buttons, R.string.sideraven_settings_toolbar_tab_buttons_summary)
            .apply { isVisible = store.addressBarLayout.value == AddressBarLayout.Top }

    /** A [Setting] as segmented buttons: finds or adds the row and shows the stored value. */
    private fun <T> segmented(
        context: Context,
        group: PreferenceGroup,
        key: String,
        title: Int,
        options: List<Option<T>>,
        setting: Setting<T>,
        summaryFor: (T) -> Int? = { null },
    ): SegmentedPreference<T> =
        findOrAdd(group, key, setting.value) {
            SegmentedPreference(context, options, setting.value, setting::set, summaryFor).apply { this.title = context.getString(title) }
        }

    /** A choice as a picture picker: finds or adds it and shows [current]. */
    private fun <T> picker(
        context: Context,
        group: PreferenceGroup,
        key: String,
        title: Int,
        options: List<Option<T>>,
        current: T,
        onChoose: (T) -> Unit,
    ): PicturePickerPreference<T> =
        findOrAdd(group, key, current) {
            PicturePickerPreference(context, options, current, onChoose).apply { this.title = context.getString(title) }
        }

    /** A [Setting] as a row showing its value, opening a dialog: finds or adds it and shows the stored value. */
    private fun <T> choiceRow(
        context: Context,
        group: PreferenceGroup,
        key: String,
        title: Int,
        options: List<Option<T>>,
        setting: Setting<T>,
    ): ChoiceRowPreference<T> =
        findOrAdd(group, key, setting.value) {
            ChoiceRowPreference(context, options, setting.value, setting::set).apply { this.title = context.getString(title) }
        }

    /** The row keyed [key] in [group] showing [value]; [make] adds it the first time the screen is filled. */
    @Suppress("UNCHECKED_CAST")
    private fun <T, P> findOrAdd(
        group: PreferenceGroup,
        key: String,
        value: T,
        make: () -> P,
    ): P where P : Preference, P : Choice<T> {
        val existing = group.findPreference<Preference>(key) as? P
        if (existing != null) {
            existing.show(value)
            return existing
        }
        return make().apply {
            this.key = key
            group.addPreference(this)
        }
    }
}

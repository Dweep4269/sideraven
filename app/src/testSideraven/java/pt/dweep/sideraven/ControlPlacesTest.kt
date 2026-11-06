/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import mozilla.components.browser.state.state.BrowserState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import pt.dweep.sideraven.SidebarControlKind.BrowsingModeSwitch
import pt.dweep.sideraven.SidebarControlKind.LayoutButton
import pt.dweep.sideraven.SidebarControlKind.NewTab
import pt.dweep.sideraven.settings.SettingsCategory
import pt.dweep.sideraven.settings.SidebarPreferences
import pt.dweep.sideraven.theme.SideravenTheme
import pt.dweep.sideraven.ui.SidebarContent
import pt.dweep.sideraven.ui.SidebarTestTag

/**
 * Control Places: the Toggle lives In the sidebar or In the menu. In the menu, it leaves the Sidebar
 * row and the Main Menu offers "Change sidebar mode" instead.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h800dp")
class ControlPlacesTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val toggleInMenu = ControlPlaces(syncedTabs = ControlPlace.InSidebar, toggle = ControlPlace.InMenu)
    private val toggleInSidebar = ControlPlaces(syncedTabs = ControlPlace.InSidebar, toggle = ControlPlace.InSidebar)

    @Test
    fun `THEN the Toggle starts In the sidebar, and its place is in Backup`() {
        val store = InMemorySidebarModeStore()
        assertEquals(ControlPlace.InSidebar, store.controlPlace(OptionalControl.Toggle).value)

        store.controlPlace(OptionalControl.Toggle).set(ControlPlace.InMenu)
        val restored = InMemorySidebarModeStore().apply { importSettings(store.exportSettings()) }
        assertEquals(ControlPlace.InMenu, restored.controlPlace(OptionalControl.Toggle).value)
    }

    @Test
    fun `GIVEN the Toggle In the menu THEN the Sidebar row leaves it out`() {
        val state =
            BrowserState().toSidebarUiState(
                isPrivate = false,
                tier = SidebarTier.Tablet,
                screen = SidebarScreen.Browser,
                chosenMode = SidebarMode.Persistent,
                controlPlaces = ControlPlaces(toggle = ControlPlace.InMenu),
            )
        assertEquals(listOf(NewTab, BrowsingModeSwitch, LayoutButton), state.controls)
    }

    @Test
    fun `GIVEN the Toggle In the menu THEN the Collapsed column has no Toggle either`() {
        val state =
            SidebarUiState(
                isVisible = true,
                isPrivate = false,
                tabs = emptyList(),
                mode = SidebarMode.Collapsed,
                nextMode = SidebarMode.AutoHide,
                layoutMovable = false,
                controlPlaces = toggleInMenu,
            )
        composeRule.setContent { SideravenTheme { SidebarContent(state = state, onIntent = {}) } }
        composeRule.onAllNodesWithTag(SidebarTestTag.TOGGLE).assertCountEquals(0)
        composeRule.onAllNodesWithTag(SidebarTestTag.NEW_TAB).assertCountEquals(1)
    }

    @Test
    fun `GIVEN the Toggle In the menu and a Sidebar with another mode THEN the Main Menu offers Change sidebar mode`() {
        assertEquals(
            listOf(SideravenMenuEntry.ChangeSidebarMode),
            sideravenMenuEntries(toggleInMenu, SidebarMenuInfo(nextMode = SidebarMode.Collapsed)),
        )
    }

    @Test
    fun `GIVEN the Toggle In the sidebar THEN the Main Menu adds nothing, Sidebar or not`() {
        assertEquals(emptyList<SideravenMenuEntry>(), sideravenMenuEntries(toggleInSidebar, SidebarMenuInfo(nextMode = SidebarMode.Collapsed)))
        assertEquals(emptyList<SideravenMenuEntry>(), sideravenMenuEntries(toggleInSidebar, sidebar = null))
    }

    @Test
    fun `GIVEN no Sidebar, or a Sidebar with one mode THEN Change sidebar mode isn't offered`() {
        assertEquals(emptyList<SideravenMenuEntry>(), sideravenMenuEntries(toggleInMenu, sidebar = null))
        assertEquals(emptyList<SideravenMenuEntry>(), sideravenMenuEntries(toggleInMenu, SidebarMenuInfo(nextMode = null)))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `WHEN Change sidebar mode is chosen THEN the window's Sidebar receives the Toggle's intent`() =
        runTest(UnconfinedTestDispatcher()) {
            val link = SidebarMenuLink()
            val received = mutableListOf<SidebarIntent>()
            backgroundScope.launch { link.requests.collect { received += it } }

            assertTrue(link.choose(SideravenMenuEntry.ChangeSidebarMode))

            assertEquals(listOf<SidebarIntent>(SidebarIntent.ToggleMode), received)
        }

    @Test
    fun `THEN each window keeps its own link, so split-screen menus don't cross`() {
        val one = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).get()
        val two = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).get()
        val linkOne = SidebarMenu.of(one)!!
        assertTrue(linkOne === SidebarMenu.of(one))
        assertTrue(linkOne !== SidebarMenu.of(two))

        linkOne.publish(SidebarMenuInfo(nextMode = SidebarMode.Collapsed))
        assertEquals(null, SidebarMenu.of(two)!!.sidebar.value)
    }

    @Test
    fun `THEN the layout button starts In the sidebar, and its place is in Backup`() {
        val store = InMemorySidebarModeStore()
        assertEquals(ControlPlace.InSidebar, store.controlPlace(OptionalControl.LayoutButton).value)

        store.controlPlace(OptionalControl.LayoutButton).set(ControlPlace.InMenu)
        val restored = InMemorySidebarModeStore().apply { importSettings(store.exportSettings()) }
        assertEquals(ControlPlace.InMenu, restored.controlPlace(OptionalControl.LayoutButton).value)
    }

    @Test
    fun `GIVEN the layout button In the menu THEN the Sidebar row leaves it out`() {
        val state =
            BrowserState().toSidebarUiState(
                isPrivate = false,
                tier = SidebarTier.Tablet,
                screen = SidebarScreen.Browser,
                chosenMode = SidebarMode.Persistent,
                controlPlaces = ControlPlaces(layoutButton = ControlPlace.InMenu),
            )
        assertEquals(listOf(NewTab, BrowsingModeSwitch, SidebarControlKind.Toggle), state.controls)
    }

    @Test
    fun `THEN Synced Tabs starts In the menu, so a 1_0 Sidebar row is unchanged, and its place is in Backup`() {
        val store = InMemorySidebarModeStore()
        assertEquals(ControlPlace.InMenu, store.controlPlace(OptionalControl.SyncedTabs).value)
        assertEquals(
            listOf(NewTab, BrowsingModeSwitch, SidebarControlKind.Toggle, LayoutButton),
            BrowserState().toSidebarUiState(isPrivate = false, tier = SidebarTier.Tablet, screen = SidebarScreen.Browser, chosenMode = SidebarMode.Persistent).controls,
        )

        store.controlPlace(OptionalControl.SyncedTabs).set(ControlPlace.InSidebar)
        val restored = InMemorySidebarModeStore().apply { importSettings(store.exportSettings()) }
        assertEquals(ControlPlace.InSidebar, restored.controlPlace(OptionalControl.SyncedTabs).value)
    }

    @Test
    fun `GIVEN Synced Tabs In the sidebar THEN it sits after the Browsing Mode Switch`() {
        val state =
            BrowserState().toSidebarUiState(
                isPrivate = false,
                tier = SidebarTier.Tablet,
                screen = SidebarScreen.Browser,
                chosenMode = SidebarMode.Persistent,
                controlPlaces = ControlPlaces(syncedTabs = ControlPlace.InSidebar),
            )
        assertEquals(listOf(NewTab, BrowsingModeSwitch, SidebarControlKind.SyncedTabs, SidebarControlKind.Toggle, LayoutButton), state.controls)
    }

    @Test
    fun `GIVEN Synced Tabs In the menu THEN the Main Menu offers it, with a Sidebar or without one`() {
        val places = ControlPlaces(syncedTabs = ControlPlace.InMenu)
        assertEquals(listOf(SideravenMenuEntry.SyncedTabs), sideravenMenuEntries(places, sidebar = null))
        assertEquals(listOf(SideravenMenuEntry.SyncedTabs), sideravenMenuEntries(places, SidebarMenuInfo(nextMode = SidebarMode.Collapsed)))
    }

    @Test
    fun `WHEN the Synced Tabs button is tapped THEN Fenix's synced-tabs page opens`() {
        var opened = 0
        val handler = SidebarIntentHandler(selectTab = {}, openNewTab = {}, navigation = AddressNavigation(openSyncedTabs = { opened++ }))
        handler.handle(SidebarIntent.OpenSyncedTabs, SidebarUiState(isVisible = true, isPrivate = false, tabs = emptyList()))
        assertEquals(1, opened)
    }

    @Test
    fun `THEN Synced Tabs in the menu doesn't go through the Sidebar`() {
        assertEquals(false, SidebarMenuLink().choose(SideravenMenuEntry.SyncedTabs))
    }

    @Test
    fun `GIVEN the layout button In the menu and an address that can move THEN the Main Menu offers moving the address`() {
        val places = ControlPlaces(syncedTabs = ControlPlace.InSidebar, layoutButton = ControlPlace.InMenu)
        assertEquals(
            listOf(SideravenMenuEntry.MoveAddressBar),
            sideravenMenuEntries(places, SidebarMenuInfo(nextMode = SidebarMode.Collapsed, layoutMovable = true)),
        )
        // Collapsed Mode, or Auto-Hide with no way to pull the Sidebar out: the address can't move.
        assertEquals(emptyList<SideravenMenuEntry>(), sideravenMenuEntries(places, SidebarMenuInfo(nextMode = SidebarMode.AutoHide, layoutMovable = false)))
        assertEquals(emptyList<SideravenMenuEntry>(), sideravenMenuEntries(places, sidebar = null))
    }

    @Test
    fun `GIVEN both In the menu THEN the entries come in the row's order`() {
        val places = ControlPlaces(toggle = ControlPlace.InMenu, layoutButton = ControlPlace.InMenu)
        assertEquals(
            listOf(SideravenMenuEntry.SyncedTabs, SideravenMenuEntry.ChangeSidebarMode, SideravenMenuEntry.MoveAddressBar),
            sideravenMenuEntries(places, SidebarMenuInfo(nextMode = SidebarMode.Collapsed, layoutMovable = true)),
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `WHEN the address entry is chosen THEN the window's Sidebar receives the layout button's intent`() =
        runTest(UnconfinedTestDispatcher()) {
            val link = SidebarMenuLink()
            val received = mutableListOf<SidebarIntent>()
            backgroundScope.launch { link.requests.collect { received += it } }

            link.choose(SideravenMenuEntry.MoveAddressBar)

            assertEquals(listOf<SidebarIntent>(SidebarIntent.ToggleAddressBarLayout), received)
        }

    @Test
    fun `THEN the Sidebar screen has a switch per button, and turning one off moves it to the menu`() {
        val screen = PreferenceManager(context).createPreferenceScreen(context)
        val store = InMemorySidebarModeStore()
        SidebarPreferences.install(context, screen, SettingsCategory.Sidebar, SidebarTier.Tablet, store)

        val groups = (0 until screen.preferenceCount).map { screen.getPreference(it) }.filterIsInstance<PreferenceCategory>()
        val buttons = groups.single { it.title.toString() == "Buttons in the sidebar" }
        val switches = (0 until buttons.preferenceCount).map { buttons.getPreference(it) }.filterIsInstance<androidx.preference.SwitchPreferenceCompat>().sortedBy { it.order }
        // Synced Tabs first, then the Toggle, then the layout button; off says where it went.
        assertEquals(listOf("Synced tabs", "Change mode", "Move address bar"), switches.map { it.title.toString() })
        assertEquals(listOf(false, true, true), switches.map { it.isChecked })
        assertEquals("In the ⋮ menu", switches[1].summaryOff.toString())

        val toggle = switches[1]
        assertTrue(toggle.onPreferenceChangeListener!!.onPreferenceChange(toggle, false))
        assertEquals(ControlPlace.InMenu, store.controlPlace(OptionalControl.Toggle).value)
        val synced = switches[0]
        assertTrue(synced.onPreferenceChangeListener!!.onPreferenceChange(synced, true))
        assertEquals(ControlPlace.InSidebar, store.controlPlace(OptionalControl.SyncedTabs).value)
    }
}

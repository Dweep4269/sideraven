/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import pt.dweep.sideraven.SidebarDesktopTabs
import pt.dweep.sideraven.SidebarIntent
import pt.dweep.sideraven.SidebarMode
import pt.dweep.sideraven.SidebarUiState
import pt.dweep.sideraven.sync.DesktopTab
import pt.dweep.sideraven.theme.SideravenTheme

/** The "On desktop" group at the end of the Tab List. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h800dp")
class DesktopTabsUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val intents = mutableListOf<SidebarIntent>()

    private fun show(expanded: Boolean, sending: Boolean = false) {
        val state =
            SidebarUiState(
                isVisible = true,
                isPrivate = false,
                tabs = emptyList(),
                mode = SidebarMode.Persistent,
                desktopTabs = SidebarDesktopTabs(listOf(DesktopTab("https://a.example", "Alpha", isSending = sending), DesktopTab("https://b.example", "Beta")), expanded),
            )
        composeRule.setContent { SideravenTheme { SidebarContent(state = state, onIntent = { intents += it }) } }
    }

    @Test
    fun `GIVEN the group collapsed THEN only its header shows, and tapping it toggles`() {
        show(expanded = false)

        composeRule.onNodeWithText("On desktop (2)").assertExists()
        composeRule.onAllNodesWithText("Alpha").assertCountEquals(0)
        composeRule.onNodeWithTag(SidebarTestTag.DESKTOP_TABS_HEADER).performClick()

        assertEquals(listOf<SidebarIntent>(SidebarIntent.ToggleDesktopTabs), intents)
    }

    @Test
    fun `GIVEN the group expanded THEN its tabs show, and tapping one opens it`() {
        show(expanded = true)

        composeRule.onNodeWithText("Beta").assertExists()
        composeRule.onNodeWithText("Alpha").performClick()

        assertEquals(listOf<SidebarIntent>(SidebarIntent.OpenDesktopTab("https://a.example")), intents)
    }

    @Test
    fun `a pending desktop copy shows its title and a loading indicator`() {
        show(expanded = true, sending = true)
        composeRule.onNodeWithText("Alpha").assertExists()
        composeRule.onNodeWithTag(SidebarTestTag.DESKTOP_TAB_SENDING).assertExists()
    }

    @Test
    fun `a confirmed desktop copy has no loading indicator`() {
        show(expanded = true)
        composeRule.onNodeWithTag(SidebarTestTag.DESKTOP_TAB_SENDING).assertDoesNotExist()
    }
}

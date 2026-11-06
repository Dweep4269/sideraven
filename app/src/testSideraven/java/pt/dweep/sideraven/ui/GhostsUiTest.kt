/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import pt.dweep.sideraven.SidebarIntent
import pt.dweep.sideraven.SidebarMode
import pt.dweep.sideraven.SidebarPinnedTab
import pt.dweep.sideraven.SidebarSpace
import pt.dweep.sideraven.SidebarUiState
import pt.dweep.sideraven.theme.SideravenTheme

/** The selected Pinned Tab's ×, and Unload in the menus. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h800dp")
class GhostsUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val intents = mutableListOf<SidebarIntent>()

    private fun render(
        pin: SidebarPinnedTab,
        unpinned: Int = 0,
    ) {
        val state =
            SidebarUiState(
                isVisible = true,
                isPrivate = false,
                tabs = emptyList(),
                mode = SidebarMode.Persistent,
                spaces =
                    listOf(
                        SidebarSpace("h", "Home", "🏠", "ember", isCurrent = true, pinnedCount = 1, unpinnedCount = unpinned),
                        SidebarSpace("w", "Work", "💼", "blue", isCurrent = false),
                    ),
                pinned = listOf(pin),
            )
        composeRule.setContent { SideravenTheme { SidebarContent(state = state, onIntent = { intents += it }) } }
    }

    private val open = SidebarPinnedTab("p1", "Mail", "https://mail.example", icon = null, linkedTabId = "mail", isSelected = true)

    @Test
    fun `WHEN the selected Pinned Tab's close button is tapped THEN it is closed`() {
        render(open)

        composeRule.onNodeWithTag(SidebarTestTag.PINNED_CLOSE).performClick()

        assertEquals(listOf<SidebarIntent>(SidebarIntent.ClosePinnedTab("p1")), intents)
    }

    @Test
    fun `GIVEN a Ghost THEN it has no close button`() {
        render(open.copy(linkedTabId = null, isSelected = false))

        composeRule.onAllNodesWithTag(SidebarTestTag.PINNED_CLOSE).assertCountEquals(0)
    }

    @Test
    fun `WHEN Unload is chosen from a Pinned Tab's menu THEN it is Unloaded`() {
        // Not the tab on screen: Unload isn't offered for that one.
        render(open.copy(isSelected = false))

        composeRule.onNodeWithContentDescription("Mail").performTouchInput { longClick() }
        composeRule.onNodeWithText("Unload").performClick()

        assertEquals(listOf<SidebarIntent>(SidebarIntent.UnloadPinnedTab("p1")), intents)
    }

    @Test
    fun `WHEN Unload space is chosen from a Space's menu THEN the Space is Unloaded`() {
        render(open)

        composeRule.onNodeWithContentDescription("Home").performTouchInput { longClick() }
        composeRule.onNodeWithText("Unload space").performClick()

        assertEquals(listOf<SidebarIntent>(SidebarIntent.UnloadSpace("h")), intents)
    }

    @Test
    fun `WHEN Close unpinned tabs is chosen from a Space's menu THEN the Space's Tab List closes`() {
        render(open, unpinned = 3)

        composeRule.onNodeWithContentDescription("Home").performTouchInput { longClick() }
        composeRule.onNodeWithText("Close unpinned tabs").performClick()

        assertEquals(listOf<SidebarIntent>(SidebarIntent.CloseUnpinnedTabs("h")), intents)
    }

    @Test
    fun `GIVEN a Space with no unpinned tabs THEN its menu doesn't offer to close them`() {
        render(open, unpinned = 0)

        composeRule.onNodeWithContentDescription("Home").performTouchInput { longClick() }
        composeRule.onAllNodesWithText("Close unpinned tabs").assertCountEquals(0)
    }
}

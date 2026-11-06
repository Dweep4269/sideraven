/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.ui

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import pt.dweep.sideraven.PinResult
import pt.dweep.sideraven.SidebarIntent
import pt.dweep.sideraven.SidebarMode
import pt.dweep.sideraven.SidebarPinnedTab
import pt.dweep.sideraven.SidebarSpace
import pt.dweep.sideraven.SidebarTab
import pt.dweep.sideraven.SidebarUiState
import pt.dweep.sideraven.theme.SideravenTheme

/** The pinned block and "Pin to space". */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h800dp")
class PinnedTabsUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val intents = mutableListOf<SidebarIntent>()

    private fun render(
        spacePinBlock: PinResult? = null,
        spaces: List<SidebarSpace> = listOf(SidebarSpace("h", "Home", "🏠", "ember", isCurrent = true, pinnedCount = 1)),
    ) {
        val state =
            SidebarUiState(
                isVisible = true,
                isPrivate = false,
                tabs = listOf(SidebarTab("news", "News", "https://news.example", icon = null, isSelected = false)),
                mode = SidebarMode.Persistent,
                spaces = spaces,
                pinned = listOf(SidebarPinnedTab("p1", "Mail", "https://mail.example", icon = null, linkedTabId = null, isSelected = false)),
                spacePinBlock = spacePinBlock,
            )
        composeRule.setContent { SideravenTheme { SidebarContent(state = state, onIntent = { intents += it }) } }
    }

    @Test
    fun `WHEN a Pinned Tab is tapped THEN it is selected`() {
        render()

        composeRule.onNodeWithContentDescription("Mail").performClick()

        assertEquals(listOf<SidebarIntent>(SidebarIntent.SelectPinnedTab("p1")), intents)
    }

    @Test
    fun `WHEN Unpin is chosen from a Pinned Tab's menu THEN it is unpinned`() {
        render()

        composeRule.onNodeWithContentDescription("Mail").performTouchInput { longClick() }
        composeRule.onNodeWithText("Unpin").performClick()

        assertEquals(listOf<SidebarIntent>(SidebarIntent.UnpinPinnedTab("p1")), intents)
    }

    @Test
    fun `WHEN Pin to space is chosen from the Tab Context Menu THEN the tab is pinned`() {
        render()

        composeRule.onNodeWithTag(SidebarTestTag.TAB_ITEM).performTouchInput { longClick() }
        composeRule.onNodeWithTag(SidebarTestTag.PIN_SPACE_ITEM).performClick()

        assertEquals(listOf<SidebarIntent>(SidebarIntent.PinToSpace("news")), intents)
    }

    @Test
    fun `GIVEN a Space with Pinned Tabs WHEN it is deleted THEN the delete is confirmed first`() {
        render(
            spaces =
                listOf(
                    SidebarSpace("h", "Home", "🏠", "ember", isCurrent = true, pinnedCount = 1),
                    SidebarSpace("w", "Work", "💼", "blue", isCurrent = false, pinnedCount = 2),
                ),
        )

        composeRule.onNodeWithContentDescription("Work").performTouchInput { longClick() }
        composeRule.onNodeWithText("Delete space").performClick()
        assertEquals(emptyList<SidebarIntent>(), intents)
        composeRule.onNodeWithTag(SidebarTestTag.SPACE_DELETE_CONFIRM).performClick()

        assertEquals(listOf<SidebarIntent>(SidebarIntent.DeleteSpace("w")), intents)
    }
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.ui

import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import pt.dweep.sideraven.InMemorySidebarModeStore
import pt.dweep.sideraven.SidebarIntent
import pt.dweep.sideraven.SidebarLook
import pt.dweep.sideraven.SidebarMode
import pt.dweep.sideraven.SidebarPinnedTab
import pt.dweep.sideraven.SidebarSpace
import pt.dweep.sideraven.SidebarUiState
import pt.dweep.sideraven.SpacesShown
import pt.dweep.sideraven.theme.SideravenTheme

/**
 * "Spaces shown as": the Space Header option. The current Space's name is the fold control, the icon
 * lists the Spaces, and the icon row is gone — the 1.2 icon row stays one setting away.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h800dp")
class SpaceHeaderTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val intents = mutableListOf<SidebarIntent>()
    private val pins =
        listOf(
            SidebarPinnedTab("p1", "Mail", "https://mail.example", icon = null, linkedTabId = "mail", isSelected = false),
            SidebarPinnedTab("p2", "Docs", "https://docs.example", icon = null, linkedTabId = "docs", isSelected = true),
            SidebarPinnedTab("p3", "Chat", "https://chat.example", icon = null, linkedTabId = null, isSelected = false),
        )
    private val home = SidebarSpace("h", "Home", "🏠", "ember", isCurrent = true, pinnedCount = 3)
    private val work = SidebarSpace("w", "Work", "💼", "ember", isCurrent = false)

    private fun state(
        folded: Boolean = false,
        shownAs: SpacesShown = SpacesShown.Header,
    ) = SidebarUiState(
        isVisible = true,
        isPrivate = false,
        tabs = emptyList(),
        mode = SidebarMode.Persistent,
        spaces = listOf(home, work),
        pinned = pins,
        pinnedFolded = folded,
        look = SidebarLook(spacesShown = shownAs),
    )

    private fun render(state: SidebarUiState) =
        composeRule.setContent { SideravenTheme { SidebarContent(state = state, onIntent = { intents += it }, modifier = Modifier.height(800.dp)) } }

    @Test
    fun `GIVEN the Header option THEN the current Space's name is shown and the icon row is gone`() {
        render(state())

        composeRule.onNodeWithTag(SidebarTestTag.SPACE_HEADER).assertExists()
        composeRule.onAllNodesWithTag(SidebarTestTag.SPACE_ROW).assertCountEquals(0)
        composeRule.onNodeWithText("Home").assertExists()
    }

    @Test
    fun `GIVEN the icon row option THEN the Header is gone and the icons are back`() {
        render(state(shownAs = SpacesShown.IconRow))

        composeRule.onAllNodesWithTag(SidebarTestTag.SPACE_HEADER).assertCountEquals(0)
        composeRule.onNodeWithTag(SidebarTestTag.SPACE_ROW).assertExists()
    }

    @Test
    fun `WHEN the Space's name is tapped THEN its Pinned Tabs fold`() {
        render(state())

        composeRule.onNodeWithContentDescription("Fold pinned tabs").performClick()

        assertEquals(listOf<SidebarIntent>(SidebarIntent.TogglePinnedFold), intents)
    }

    @Test
    fun `GIVEN Pinned Tabs folded THEN only the Divider says how many, with the stack mark`() {
        render(state(folded = true))

        composeRule.onAllNodesWithText("3 pinned").assertCountEquals(1)
        composeRule.onNodeWithTag(SidebarTestTag.PINNED_STACK, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithContentDescription("Unfold pinned tabs").assertExists()
    }

    @Test
    fun `WHEN the Space's icon is tapped THEN every Space is listed and one can be switched to`() {
        render(state())

        composeRule.onNodeWithContentDescription("Home").performClick()
        composeRule.onNodeWithText("Work").performClick()

        assertEquals(listOf<SidebarIntent>(SidebarIntent.SwitchSpace("w")), intents)
    }

    @Test
    fun `WHEN the Space's icon is held THEN the Space's own menu opens`() {
        render(state())

        composeRule.onNodeWithContentDescription("Home").performTouchInput { longClick() }

        composeRule.onNodeWithText("Edit space").assertExists()
    }

    @Test
    fun `THEN the Divider is tall enough to hit without aiming`() {
        render(state())

        composeRule.onNodeWithTag(SidebarTestTag.PINNED_FOLD).assertHeightIsEqualTo(32.dp)
    }

    @Test
    fun `GIVEN the Header option THEN the setting still defaults to the Header`() {
        assertEquals(SpacesShown.Header, InMemorySidebarModeStore().spacesShown.value)
    }
}

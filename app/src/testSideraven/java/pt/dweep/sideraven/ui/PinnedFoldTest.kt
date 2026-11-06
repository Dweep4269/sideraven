/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.ui

import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import pt.dweep.sideraven.InMemorySidebarModeStore
import pt.dweep.sideraven.SidebarIntent
import pt.dweep.sideraven.SidebarIntentHandler
import pt.dweep.sideraven.SidebarMode
import pt.dweep.sideraven.SidebarPinnedTab
import pt.dweep.sideraven.SidebarSpace
import pt.dweep.sideraven.SidebarUiState
import pt.dweep.sideraven.theme.SideravenTheme

/** Pinned Fold: tapping the Divider under Pinned Tabs folds them away. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h800dp")
class PinnedFoldTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val intents = mutableListOf<SidebarIntent>()
    private val pins =
        listOf(
            SidebarPinnedTab("p1", "Mail", "https://mail.example", icon = null, linkedTabId = "mail", isSelected = false),
            SidebarPinnedTab("p2", "Docs", "https://docs.example", icon = null, linkedTabId = "docs", isSelected = true),
            SidebarPinnedTab("p3", "Chat", "https://chat.example", icon = null, linkedTabId = null, isSelected = false),
        )
    private val space = SidebarSpace("h", "Home", "🏠", "ember", isCurrent = true, pinnedCount = 3)

    private fun state(
        folded: Boolean,
        arranging: Boolean = false,
        mode: SidebarMode = SidebarMode.Persistent,
    ) = SidebarUiState(
        isVisible = true,
        isPrivate = false,
        tabs = emptyList(),
        mode = mode,
        spaces = listOf(space),
        pinned = pins,
        pinnedFolded = folded,
        arranging = arranging,
    )

    // A fixed height, as on a device: Collapsed Mode sizes its sections from it.
    private fun render(state: SidebarUiState) =
        composeRule.setContent { SideravenTheme { SidebarContent(state = state, onIntent = { intents += it }, modifier = Modifier.height(800.dp)) } }

    @Test
    fun `WHEN the Divider under Pinned Tabs is tapped THEN they fold`() {
        render(state(folded = false))
        composeRule.onAllNodesWithTag(SidebarTestTag.PINNED_TAB).assertCountEquals(3)

        composeRule.onNodeWithContentDescription("Fold pinned tabs").performClick()

        assertEquals(listOf<SidebarIntent>(SidebarIntent.TogglePinnedFold), intents)
    }

    @Test
    fun `GIVEN Pinned Tabs folded THEN only the one on screen shows, and the Divider says how many there are`() {
        render(state(folded = true))

        composeRule.onAllNodesWithTag(SidebarTestTag.PINNED_TAB).assertCountEquals(1)
        composeRule.onNodeWithText("3 pinned").assertExists()
        composeRule.onNodeWithContentDescription("Unfold pinned tabs").assertExists()
    }

    @Test
    fun `GIVEN Arrange Mode THEN the Divider is the grab bar and doesn't fold`() {
        render(state(folded = true, arranging = true, mode = SidebarMode.Collapsed))

        composeRule.onAllNodesWithTag(SidebarTestTag.PINNED_FOLD).assertCountEquals(0)
        composeRule.onAllNodesWithTag(SidebarTestTag.GRAB_BAR).assertCountEquals(1)
    }

    @Test
    fun `THEN each Space remembers its own fold`() {
        val store = InMemorySidebarModeStore()
        val handler = SidebarIntentHandler(selectTab = {}, openNewTab = {}, toggleFold = store::toggleFold)
        val work = space.copy(id = "w", isCurrent = false)

        handler.handle(SidebarIntent.TogglePinnedFold, state(folded = false))

        assertTrue(store.isFolded("h"))
        assertFalse(store.isFolded("w"))
        handler.handle(SidebarIntent.TogglePinnedFold, state(folded = true).copy(spaces = listOf(space.copy(isCurrent = false), work.copy(isCurrent = true))))
        assertTrue(store.isFolded("w"))
        assertTrue(store.isFolded("h"))
    }
}

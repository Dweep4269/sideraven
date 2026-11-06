/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.rightClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import pt.dweep.sideraven.SidebarIntent
import pt.dweep.sideraven.SidebarSpace
import pt.dweep.sideraven.SidebarTab
import pt.dweep.sideraven.SidebarUiState
import pt.dweep.sideraven.theme.SideravenTheme

/** Long-press/right-click open the Tab Context Menu. */
@RunWith(AndroidJUnit4::class)
class TabContextMenuUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val intents = mutableListOf<SidebarIntent>()

    private fun render(sendEnabled: Boolean = false, isPrivate: Boolean = false, isLocal: Boolean = false) {
        val state =
            SidebarUiState(
                isVisible = true,
                isPrivate = isPrivate,
                tabs = listOf(SidebarTab("1", "One", "https://one.example", icon = null, isSelected = false)),
                desktopSendingOn = sendEnabled,
                spaces = listOf(SidebarSpace("work", "Work", "💼", "ember", isCurrent = true, isLocal = isLocal)),
            )
        composeRule.setContent { SideravenTheme { SidebarContent(state = state, onIntent = { intents += it }) } }
    }

    @Test
    fun `WHEN a tab is long-pressed and released THEN the menu offers Duplicate and Close`() {
        render()

        composeRule.onNodeWithContentDescription("One").performTouchInput { longClick() }
        composeRule.onNodeWithText("Duplicate tab").performClick()

        assertEquals(listOf<SidebarIntent>(SidebarIntent.DuplicateTab("1")), intents)
    }

    @Test
    fun `WHEN Close is chosen from the menu THEN the tab closes`() {
        render()

        composeRule.onNodeWithContentDescription("One").performTouchInput { longClick() }
        composeRule.onNodeWithText("Close tab").performClick()

        assertEquals(listOf<SidebarIntent>(SidebarIntent.CloseTab("1")), intents)
    }

    @Test
    fun `a shared tab menu sends a copy to desktop`() {
        render(sendEnabled = true)
        composeRule.onNodeWithContentDescription("One").performTouchInput { longClick() }
        composeRule.onNodeWithText("Send to desktop").performClick()
        assertEquals(listOf<SidebarIntent>(SidebarIntent.SendToDesktop("1")), intents)
    }

    @Test
    fun `private tab menu never offers sending to desktop`() {
        render(sendEnabled = true, isPrivate = true)
        composeRule.onNodeWithContentDescription("One").performTouchInput { longClick() }
        composeRule.onNodeWithText("Send to desktop").assertDoesNotExist()
    }

    @Test
    fun `Local Space tab menu never offers sending to desktop`() {
        render(sendEnabled = true, isLocal = true)
        composeRule.onNodeWithContentDescription("One").performTouchInput { longClick() }
        composeRule.onNodeWithText("Send to desktop").assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `WHEN a tab is right-clicked THEN the menu opens`() {
        render()

        composeRule.onNodeWithContentDescription("One").performMouseInput { rightClick() }
        composeRule.onNodeWithText("Duplicate tab").assertExists()
    }

    @Test
    fun `THEN the menu's actions are TalkBack actions too`() {
        render()

        composeRule.onNodeWithContentDescription("One").assert(
            SemanticsMatcher("has Duplicate tab action") { node ->
                node.config.getOrNull(SemanticsActions.CustomActions).orEmpty().any { it.label == "Duplicate tab" }
            },
        )
    }
}

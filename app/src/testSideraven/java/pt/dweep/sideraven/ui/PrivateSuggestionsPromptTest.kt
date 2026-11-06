/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import pt.dweep.sideraven.theme.SideravenTheme

/** Fenix's one-time question about search suggestions in private browsing. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h800dp")
class PrivateSuggestionsPromptTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val answers = mutableListOf<Boolean>()
    private var controller: FieldController? = null

    /** The on-screen control. */
    private val edit
        get() = requireNotNull(controller?.edit) { "the field never attached" }

    private inner class FakePrompt(
        override val needed: Boolean,
    ) : PrivateSuggestionsPrompt {
        override fun answer(allow: Boolean) {
            answers += allow
        }

        override fun learnMore() = Unit
    }

    private fun show(needed: Boolean) =
        composeRule.setContent {
            SideravenTheme {
                SearchPopup(
                    request = SearchRequest(newTab = true),
                    providers = emptyList(),
                    onSubmit = {},
                    onSuggestion = {},
                    onDismiss = {},
                    options = SearchOptions(privatePrompt = FakePrompt(needed)),
                    fieldController = { controller = it },
                )
            }
        }

    @Test
    fun `GIVEN the question is due WHEN something is typed THEN it is asked, and an answer is given once`() {
        show(needed = true)
        composeRule.onAllNodesWithText("Allow").assertCountEquals(0)

        // Text arrives the control's own way (a plain set runs its autocomplete machinery).
        composeRule.runOnIdle { edit.setText("cats") }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Allow").performClick()

        assertEquals(listOf(true), answers)
        composeRule.onAllNodesWithText("Allow").assertCountEquals(0)
    }

    @Test
    fun `WHEN suggestions aren't allowed THEN that answer is given`() {
        show(needed = true)

        composeRule.runOnIdle { edit.setText("cats") }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Don’t allow").performClick()

        assertEquals(listOf(false), answers)
    }

    @Test
    fun `GIVEN the question isn't due THEN it never shows`() {
        show(needed = false)

        composeRule.runOnIdle { edit.setText("cats") }
        composeRule.waitForIdle()

        composeRule.onAllNodesWithText("Allow").assertCountEquals(0)
    }
}

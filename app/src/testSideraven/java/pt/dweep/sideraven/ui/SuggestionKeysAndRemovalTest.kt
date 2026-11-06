/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.ui

import android.view.KeyEvent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import mozilla.components.concept.awesomebar.AwesomeBar
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import pt.dweep.sideraven.theme.SideravenTheme

/** Removing a history suggestion, and moving through suggestions with the keyboard. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h800dp")
class SuggestionKeysAndRemovalTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val chosen = mutableListOf<String>()
    private val removed = mutableListOf<String?>()
    private var controller: FieldController? = null

    /** The on-screen control. */
    private val edit
        get() = requireNotNull(controller?.edit) { "the field never attached" }

    /** Two removable suggestions for anything typed, like Fenix's history. */
    private val provider =
        object : AwesomeBar.SuggestionProvider {
            override val id = "history"

            override suspend fun onInputChanged(text: String): List<AwesomeBar.SuggestionItem> =
                if (text.isBlank()) {
                    emptyList()
                } else {
                    listOf("docs", "news").map { kind ->
                        AwesomeBar.Suggestion(
                            provider = this,
                            id = kind,
                            title = "$text $kind",
                            isRemovalAllowed = true,
                            onSuggestionClicked = { chosen += "$text $kind" },
                        )
                    }
                }
        }

    private fun show() =
        composeRule.setContent {
            SideravenTheme {
                SearchPopup(
                    request = SearchRequest(newTab = true),
                    providers = listOf(provider),
                    onSubmit = {},
                    onSuggestion = { it.onSuggestionClicked?.invoke() },
                    onDismiss = {},
                    options = SearchOptions(onRemoveSuggestion = { removed += (it.suggestion as? AwesomeBar.Suggestion)?.title }),
                    fieldController = { controller = it },
                )
            }
        }

    @Test
    fun `WHEN a history suggestion is removed THEN it leaves the list and is deleted`() {
        show()
        composeRule.runOnIdle { edit.setText("kotlin") }
        composeRule.waitUntil(3_000) { composeRule.onAllNodesWithText("kotlin docs").fetchSemanticsNodes().isNotEmpty() }

        composeRule.onAllNodesWithContentDescription("Delete this history entry", useUnmergedTree = true).onFirst().performClick()
        composeRule.waitForIdle()

        assertEquals(listOf<String?>("kotlin docs"), removed)
        composeRule.onAllNodesWithText("kotlin docs").assertCountEquals(0)
    }

    @Test
    fun `WHEN Down is pressed THEN the field previews the next suggestion, and Enter opens it`() {
        show()
        composeRule.runOnIdle { edit.setText("kotlin") }
        composeRule.waitUntil(3_000) { composeRule.onAllNodesWithText("kotlin news").fetchSemanticsNodes().isNotEmpty() }

        composeRule.runOnIdle { edit.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN)) }
        composeRule.waitForIdle()
        composeRule.runOnIdle { edit.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN)) }
        composeRule.waitForIdle()
        assertEquals("kotlin news", controller?.text)

        composeRule.runOnIdle { edit.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP)) }
        composeRule.waitForIdle()
        assertEquals("kotlin docs", controller?.text)

        composeRule.runOnIdle { edit.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER)) }
        assertEquals(listOf("kotlin docs"), chosen)
    }
}

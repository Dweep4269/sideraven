/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.ui

import android.view.KeyEvent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import mozilla.components.concept.awesomebar.AwesomeBar
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import pt.dweep.sideraven.theme.SideravenTheme

/** The Search Pop-up, with fake suggestion sources; its field is the toolbar's control. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h800dp")
class SearchPopupTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val submitted = mutableListOf<String>()
    private val chosen = mutableListOf<String>()
    private var dismissed = 0
    private var controller: FieldController? = null

    /** Suggests "<text> docs" for anything typed. */
    private val provider =
        object : AwesomeBar.SuggestionProvider {
            override val id = "fake"

            override suspend fun onInputChanged(text: String): List<AwesomeBar.SuggestionItem> =
                if (text.isBlank()) {
                    emptyList()
                } else {
                    listOf(AwesomeBar.Suggestion(provider = this, title = "$text docs", onSuggestionClicked = { chosen += "$text docs" }))
                }
        }

    /** The on-screen control (the popup-side field once it has attached). */
    private val edit
        get() = requireNotNull(controller?.edit) { "the field never attached" }

    private fun show(request: SearchRequest = SearchRequest(newTab = false, initialText = "https://example.com/")) {
        composeRule.setContent {
            SideravenTheme {
                SearchPopup(
                    request = request,
                    providers = listOf(provider),
                    onSubmit = { submitted += it },
                    onSuggestion = { it.onSuggestionClicked?.invoke() },
                    onDismiss = { dismissed++ },
                    fieldController = { controller = it },
                )
            }
        }
        composeRule.waitUntil(5_000) { controller?.view != null }
    }

    @Test
    fun `WHEN it opens for the current tab THEN the field holds the current URL, all selected`() {
        show()

        assertEquals("https://example.com/", controller?.text)
        assertEquals(0, edit.selectionStart)
        assertEquals("https://example.com/".length, edit.selectionEnd)
    }

    @Test
    fun `WHEN text is typed THEN Fenix's suggestions show, and choosing one runs it`() {
        show()

        // Text arrives the control's own way (a plain set runs its autocomplete machinery).
        composeRule.runOnIdle { edit.setText("kotlin") }
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithText("kotlin docs").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithText("kotlin docs").performClick()

        assertEquals(listOf("kotlin docs"), chosen)
    }

    @Test
    fun `WHEN Enter is pressed THEN the text is submitted`() {
        show()

        composeRule.runOnIdle { edit.setText("github.com") }
        composeRule.waitForIdle()
        composeRule.runOnIdle { edit.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER)) }

        assertEquals(listOf("github.com"), submitted)
    }

    @Test
    fun `WHEN Esc is pressed THEN the pop-up closes without doing anything`() {
        show()

        composeRule.runOnIdle { edit.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ESCAPE)) }
        // The pop-up fades out first.
        composeRule.waitForIdle()

        assertEquals(1, dismissed)
        assertEquals(emptyList<String>(), submitted + chosen)
    }
}

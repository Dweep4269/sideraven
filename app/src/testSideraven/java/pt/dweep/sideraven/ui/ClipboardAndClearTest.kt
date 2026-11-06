/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import pt.dweep.sideraven.theme.SideravenTheme

/** The copied-link chip and the clear button; the field is the toolbar's control. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h800dp")
class ClipboardAndClearTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val submitted = mutableListOf<String>()
    private var controller: FieldController? = null

    /** The on-screen control. */
    private val edit
        get() = requireNotNull(controller?.edit) { "the field never attached" }

    private fun show(clipboardLink: String?) =
        composeRule.setContent {
            SideravenTheme {
                SearchPopup(
                    request = SearchRequest(newTab = true),
                    providers = emptyList(),
                    onSubmit = { submitted += it },
                    onSuggestion = {},
                    onDismiss = {},
                    options = SearchOptions(clipboardLink = clipboardLink),
                    fieldController = { controller = it },
                )
            }
        }

    @Test
    fun `GIVEN no link on the clipboard THEN there is no chip`() {
        show(clipboardLink = null)

        composeRule.onAllNodesWithTag(SidebarTestTag.SEARCH_CLIPBOARD_PASTE).assertCountEquals(0)
    }

    @Test
    fun `GIVEN a copied link WHEN Paste is tapped THEN it fills the field, and Go opens it straight away`() {
        show(clipboardLink = "https://copied.example/page")

        composeRule.onNodeWithTag(SidebarTestTag.SEARCH_CLIPBOARD_PASTE).performClick()
        assertEquals("https://copied.example/page", controller?.text)
        assertEquals(emptyList<String>(), submitted)

        composeRule.onNodeWithTag(SidebarTestTag.SEARCH_CLIPBOARD_GO).performClick()
        assertEquals(listOf("https://copied.example/page"), submitted)
    }

    @Test
    fun `WHEN the clear button is tapped THEN the field empties, and the button goes`() {
        show(clipboardLink = null)
        composeRule.onAllNodesWithTag(SidebarTestTag.SEARCH_CLEAR).assertCountEquals(0)

        // Text arrives the control's own way (a plain set runs its autocomplete machinery).
        composeRule.runOnIdle { edit.setText("cats") }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(SidebarTestTag.SEARCH_CLEAR).performClick()
        composeRule.waitForIdle()

        assertEquals("", controller?.text)
        composeRule.onAllNodesWithTag(SidebarTestTag.SEARCH_CLEAR).assertCountEquals(0)
    }
}

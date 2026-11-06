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
import mozilla.components.ui.autocomplete.InlineAutocompleteEditText
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import pt.dweep.sideraven.theme.SideravenTheme

/** QR and voice in the Search Pop-up; the field is the toolbar's control. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h800dp")
class SearchExtrasTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val submitted = mutableListOf<String>()
    private val asked = mutableListOf<String>()
    private var controller: FieldController? = null

    private inner class FakeExtras(
        override val canScanQr: Boolean = true,
        override val canUseVoice: Boolean = true,
    ) : SearchExtras {
        override fun scanQr(onResult: (String) -> Unit) {
            asked += "qr"
            onResult("https://scanned.example/page")
        }

        override fun voice(onResult: (String) -> Unit) {
            asked += "voice"
            onResult("cats in hats")
        }
    }

    private fun show(extras: SearchExtras?) =
        composeRule.setContent {
            SideravenTheme {
                SearchPopup(
                    request = SearchRequest(newTab = true),
                    providers = emptyList(),
                    onSubmit = { submitted += it },
                    onSuggestion = {},
                    onDismiss = {},
                    options = SearchOptions(extras = extras),
                    fieldController = { controller = it },
                )
            }
        }

    @Test
    fun `WHEN a QR code is scanned THEN its text fills the field, all selected and not gone`() {
        show(FakeExtras())

        composeRule.onNodeWithTag(SidebarTestTag.SEARCH_QR).performClick()

        assertEquals(listOf("qr"), asked)
        assertEquals("https://scanned.example/page", controller?.text)
        val view = requireNotNull(controller?.view) as InlineAutocompleteEditText
        assertEquals(0, view.selectionStart)
        assertEquals("https://scanned.example/page".length, view.selectionEnd)
        assertEquals(emptyList<String>(), submitted)
    }

    @Test
    fun `WHEN something is said THEN it is searched straight away`() {
        show(FakeExtras())

        composeRule.onNodeWithTag(SidebarTestTag.SEARCH_VOICE).performClick()

        assertEquals(listOf("cats in hats"), submitted)
    }

    @Test
    fun `GIVEN no camera or speech recognition THEN the buttons aren't there`() {
        show(FakeExtras(canScanQr = false, canUseVoice = false))

        composeRule.onAllNodesWithTag(SidebarTestTag.SEARCH_QR).assertCountEquals(0)
        composeRule.onAllNodesWithTag(SidebarTestTag.SEARCH_VOICE).assertCountEquals(0)
    }

    @Test
    fun `GIVEN text in the field THEN the clear button replaces them`() {
        show(FakeExtras())
        composeRule.onNodeWithTag(SidebarTestTag.SEARCH_QR).performClick()

        composeRule.onAllNodesWithTag(SidebarTestTag.SEARCH_QR).assertCountEquals(0)
        composeRule.onNodeWithTag(SidebarTestTag.SEARCH_CLEAR).assertExists()
    }
}

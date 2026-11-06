/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.ui

import android.graphics.Bitmap
import android.view.KeyEvent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import mozilla.components.browser.state.search.SearchEngine
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import pt.dweep.sideraven.theme.SideravenTheme

/** The engine chip, for one search with another engine or scope; the field is the toolbar's control. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h800dp")
class EngineChipTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val icon = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
    private val google = SearchEngine("google", "Google", icon, type = SearchEngine.Type.BUNDLED)
    private val ddg = SearchEngine("ddg", "DuckDuckGo", icon, type = SearchEngine.Type.BUNDLED)
    private val history = SearchEngine("history_search_engine_id", "History", icon, type = SearchEngine.Type.APPLICATION)

    private val providerRequests = mutableListOf<String>()
    private val submitted = mutableListOf<String>()
    private var settingsOpened = 0
    private var controller: FieldController? = null

    /** The on-screen control. */
    private val edit
        get() = requireNotNull(controller?.edit) { "the field never attached" }

    private fun show() =
        composeRule.setContent {
            SideravenTheme {
                SearchPopup(
                    request = SearchRequest(newTab = true),
                    providers = emptyList(),
                    onSubmit = { submitted += "default:$it" },
                    onSuggestion = {},
                    onDismiss = {},
                    options =
                        SearchOptions(
                            engines = listOf(google, ddg, history),
                            defaultEngine = google,
                            providersFor = { engine ->
                                providerRequests += engine.id
                                emptyList()
                            },
                            onSubmitWith = { text, engine -> submitted += "${engine.id}:$text" },
                            onSearchSettings = { settingsOpened++ },
                        ),
                    fieldController = { controller = it },
                )
            }
        }

    @Test
    fun `THEN the chip names the default engine and lists Fenix's engines, its scopes and search settings`() {
        show()

        composeRule.onNodeWithTag(SidebarTestTag.SEARCH_ENGINE_CHIP).assertContentDescriptionContains("Google", substring = true)
        composeRule.onNodeWithTag(SidebarTestTag.SEARCH_ENGINE_CHIP).performClick()
        composeRule.onNodeWithText("DuckDuckGo").assertExists()
        composeRule.onNodeWithText("History").assertExists()
        composeRule.onNodeWithText("Search settings").performClick()

        assertEquals(1, settingsOpened)
    }

    @Test
    fun `WHEN another engine is picked THEN its suggestions show and Enter searches with it, this time only`() {
        show()

        composeRule.onNodeWithTag(SidebarTestTag.SEARCH_ENGINE_CHIP).performClick()
        composeRule.onNodeWithText("DuckDuckGo").performClick()
        composeRule.runOnIdle { edit.setText("cats") }
        composeRule.waitForIdle()
        composeRule.runOnIdle { edit.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER)) }

        assertEquals(listOf("ddg"), providerRequests)
        assertEquals(listOf("ddg:cats"), submitted)
        composeRule.onNodeWithTag(SidebarTestTag.SEARCH_ENGINE_CHIP).assertContentDescriptionContains("DuckDuckGo", substring = true)
    }

    @Test
    fun `WHEN a scope is picked THEN its suggestions show, and Enter still goes through the default`() {
        show()

        composeRule.onNodeWithTag(SidebarTestTag.SEARCH_ENGINE_CHIP).performClick()
        composeRule.onNodeWithText("History").performClick()
        composeRule.runOnIdle { edit.setText("news") }
        composeRule.waitForIdle()
        composeRule.runOnIdle { edit.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER)) }

        assertEquals(listOf("history_search_engine_id"), providerRequests)
        assertEquals(listOf("default:news"), submitted)
    }
}

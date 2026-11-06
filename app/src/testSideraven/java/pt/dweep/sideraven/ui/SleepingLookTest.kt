/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.ui

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import pt.dweep.sideraven.SidebarMode
import pt.dweep.sideraven.SidebarTab
import pt.dweep.sideraven.SidebarUiState
import pt.dweep.sideraven.theme.SideravenTheme

/** A Sleeping tab is dimmed and its favicon drawn in grey. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1280dp-h800dp")
class SleepingLookTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val red = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }

    @Test
    fun `GIVEN a sleeping tab THEN its favicon is grey, while an awake tab's keeps its colour`() {
        val tabs =
            listOf(
                SidebarTab("awake", "Awake", "https://a.example", icon = red, isSelected = true),
                SidebarTab("asleep", "Asleep", "https://b.example", icon = red, isSelected = false, sleeping = true),
            )
        composeRule.setContent {
            SideravenTheme {
                SidebarContent(state = SidebarUiState(isVisible = true, isPrivate = false, tabs = tabs, mode = SidebarMode.Persistent), onIntent = {})
            }
        }
        composeRule.waitForIdle()

        val rows = composeRule.onAllNodesWithTag(SidebarTestTag.TAB_ITEM)
        assertTrue("awake tab shows red", hasRed(rows[0].captureToImage()))
        assertFalse("sleeping tab shows no red", hasRed(rows[1].captureToImage()))
    }

    private fun hasRed(image: androidx.compose.ui.graphics.ImageBitmap): Boolean {
        val pixels = image.toPixelMap()
        for (x in 0 until pixels.width) {
            for (y in 0 until pixels.height) {
                val c = pixels[x, y]
                // Red stands out from grey and from the background, even dimmed.
                if (c.red - maxOf(c.green, c.blue) > 0.3f) return true
            }
        }
        return false
    }
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopSendPreviewTest {
    @Test
    fun `pending copies appear first even before the Space has any desktop tabs`() {
        val preview = DesktopSendPreview("request", "work", "https://phone.example", "Phone")
        val empty = DesktopView().withPendingSends(listOf(preview))
        assertEquals(listOf("Phone"), empty["work"]!!.map { it.title })
        assertTrue(empty["work"]!!.first().isSending)
        val existing = DesktopView(tabs = mapOf("work" to listOf(DesktopTab("https://old.example", "Old", id = "old"))))
        assertEquals(listOf("Phone", "Old"), existing.withPendingSends(listOf(preview))["work"]!!.map { it.title })
    }

    @Test
    fun `a partial server record stays loading until the queue acknowledges the copy`() {
        val preview = DesktopSendPreview("request", "work", "https://phone.example", "Phone", recordId = "sent")
        val view = DesktopView(tabs = mapOf("work" to listOf(DesktopTab("https://phone.example", "Phone", id = "sent"))))
        val tabs = view.withPendingSends(listOf(preview))["work"]!!
        assertEquals(1, tabs.size)
        assertTrue(tabs.first().isSending)
        val confirmed = view.withPendingSends(emptyList())["work"]!!
        assertEquals(1, confirmed.size)
        assertFalse(confirmed.first().isSending)
    }

    @Test
    fun `another desktop tab with the same URL does not hide the pending copy`() {
        val preview = DesktopSendPreview("request", "work", "https://same.example", "New", recordId = "new")
        val view = DesktopView(tabs = mapOf("work" to listOf(DesktopTab("https://same.example", "Old", id = "old"))))
        val tabs = view.withPendingSends(listOf(preview))["work"]!!
        assertEquals(listOf("New", "Old"), tabs.map { it.title })
        assertTrue(tabs.first().isSending)
    }
}

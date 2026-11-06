/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.mockk
import mozilla.components.browser.state.state.MediaSessionState
import mozilla.components.browser.state.state.createTab
import mozilla.components.concept.engine.mediasession.MediaSession
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import pt.dweep.sideraven.settings.SidebarPreferences

/** Auto-Sleep: tabs unused for the Sleep Timer are unloaded on their own. */
@RunWith(AndroidJUnit4::class)
class AutoSleepTest {
    private val minute = 60_000L
    private val now = 1_000 * minute

    private fun unusedFor(
        minutes: Long,
        id: String,
        private: Boolean = false,
    ) = createTab("https://$id.example", id = id, private = private, lastAccess = now - minutes * minute)

    @Test
    fun `THEN tabs unused for longer than the Sleep Timer sleep, and newer ones don't`() {
        val tabs = listOf(unusedFor(45, "old"), unusedFor(20, "recent"), unusedFor(31, "justOver"))

        assertEquals(listOf("old", "justOver"), AutoSleep.due(tabs, selectedTabId = null, sleeping = emptySet(), SleepTimer.Minutes30, now))
        assertEquals(listOf("old", "recent", "justOver"), AutoSleep.due(tabs, null, emptySet(), SleepTimer.Minutes15, now).sortedBy { listOf("old", "recent", "justOver").indexOf(it) })
        assertEquals(emptyList<String>(), AutoSleep.due(tabs, null, emptySet(), SleepTimer.Hour1, now))
    }

    @Test
    fun `THEN the tab on screen, a tab playing media, a private tab and a sleeping tab are never slept`() {
        val playing = unusedFor(600, "music").copy(mediaSessionState = MediaSessionState(controller = mockk(relaxed = true), playbackState = MediaSession.PlaybackState.PLAYING))
        val paused = unusedFor(600, "paused").copy(mediaSessionState = MediaSessionState(controller = mockk(relaxed = true), playbackState = MediaSession.PlaybackState.PAUSED))
        val tabs = listOf(unusedFor(600, "shown"), playing, paused, unusedFor(600, "secret", private = true), unusedFor(600, "asleep"))

        assertEquals(listOf("paused"), AutoSleep.due(tabs, selectedTabId = "shown", sleeping = setOf("asleep"), SleepTimer.Minutes30, now))
    }

    @Test
    fun `GIVEN the Sleep Timer is off THEN nothing sleeps`() {
        assertEquals(emptyList<String>(), AutoSleep.due(listOf(unusedFor(10_000, "ancient")), null, emptySet(), SleepTimer.Off, now))
    }

    @Test
    fun `THEN the Sleep Timer is 30 minutes until changed in Sideraven Settings`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = InMemorySidebarModeStore()
        assertEquals(SleepTimer.Minutes30, store.sleepTimer.value)

        SidebarPreferences.bindSleepTimer(context, PreferenceManager(context).createPreferenceScreen(context), store).choose(SleepTimer.Hours6)

        assertEquals(SleepTimer.Hours6, store.sleepTimer.value)
    }
}

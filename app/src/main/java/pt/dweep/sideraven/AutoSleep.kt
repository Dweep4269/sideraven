/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.concept.engine.mediasession.MediaSession

/** How long a tab goes unused before Auto-Sleep unloads it (Sideraven Settings); Off never does. */
enum class SleepTimer(
    val minutes: Long?,
) {
    Off(null),
    Minutes15(15),
    Minutes30(30),
    Hour1(60),
    Hours6(360),
}

/** Auto-Sleep: unloading tabs that have gone unused for the [SleepTimer]. */
object AutoSleep {
    /** How often Auto-Sleep looks, while the app is in the foreground. */
    const val CHECK_EVERY_MS = 60_000L

    /**
     * The tabs to put to sleep at [now]: unused (by Fenix's last access) for longer than [timer].
     * Never the tab on screen, a tab playing sound or video, a private tab, or one already [sleeping].
     * Essential Tabs and Pinned Tabs' tabs sleep like any other.
     */
    fun due(
        tabs: List<TabSessionState>,
        selectedTabId: String?,
        sleeping: Set<String>,
        timer: SleepTimer,
        now: Long,
    ): List<String> {
        val minutes = timer.minutes ?: return emptyList()
        val before = now - minutes * 60_000L
        return tabs
            .filter { tab ->
                tab.id != selectedTabId &&
                    tab.id !in sleeping &&
                    !tab.content.private &&
                    tab.mediaSessionState?.playbackState != MediaSession.PlaybackState.PLAYING &&
                    tab.lastAccess < before
            }.map { it.id }
    }
}

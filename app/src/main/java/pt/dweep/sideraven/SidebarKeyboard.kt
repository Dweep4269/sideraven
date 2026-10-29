/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import android.view.KeyEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Sidebar keyboard shortcuts, fed from `HomeActivity.dispatchKeyEvent` (one `SIDERAVEN-HOOK`).
 * Keyboard actions never animate (they're the 100+/day tier in the motion plan).
 */
object SidebarKeyboard {
    private val intents = MutableSharedFlow<SidebarIntent>(extraBufferCapacity = 8)

    /** Shortcuts for the Sidebar to handle. */
    val shortcuts: SharedFlow<SidebarIntent> = intents.asSharedFlow()

    /** Set by the Sidebar: how many Essentials it shows, or -1 when it isn't shown. */
    @Volatile var visibleEssentials: Int = -1

    /**
     * Maps a key press to a Sidebar intent: Ctrl+Shift+S cycles Sidebar Modes; Ctrl+1…9 selects
     * Essentials 1–9; Ctrl+L edits the current URL; Ctrl+Alt+Left/Right moves to the previous/next Space (Ctrl+Shift+arrows stay
     * with text fields, where they select words). Anything else (and Essentials that don't
     * exist) is left to Fenix.
     */
    fun intentFor(
        keyCode: Int,
        ctrl: Boolean,
        shift: Boolean,
        alt: Boolean,
        meta: Boolean,
        essentialsShown: Int,
    ): SidebarIntent? {
        if (!ctrl || meta || essentialsShown < 0) return null
        if (alt) {
            return when {
                shift -> null
                keyCode == KeyEvent.KEYCODE_DPAD_LEFT -> SidebarIntent.KeyboardSwitchSpace(-1)
                keyCode == KeyEvent.KEYCODE_DPAD_RIGHT -> SidebarIntent.KeyboardSwitchSpace(1)
                else -> null
            }
        }
        return when {
            shift && keyCode == KeyEvent.KEYCODE_S -> SidebarIntent.KeyboardToggleMode
            // Use the same editor as tapping the Sidebar's address bar.
            !shift && keyCode == KeyEvent.KEYCODE_L -> SidebarIntent.OpenSearch(newTab = false)
            // Ctrl+T follows the New Tab Behaviour, like the Sidebar's New Tab button.
            !shift && keyCode == KeyEvent.KEYCODE_T -> SidebarIntent.NewTab
            !shift && keyCode in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_9 -> {
                val index = keyCode - KeyEvent.KEYCODE_1
                if (index < essentialsShown) SidebarIntent.SelectEssentialAt(index) else null
            }
            else -> null
        }
    }

    /** Returns true when the event was a Sidebar shortcut (and so is consumed). */
    fun handle(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount > 0) return false
        val intent =
            intentFor(
                keyCode = event.keyCode,
                ctrl = event.isCtrlPressed,
                shift = event.isShiftPressed,
                alt = event.isAltPressed,
                meta = event.isMetaPressed,
                essentialsShown = visibleEssentials,
            ) ?: return false
        return intents.tryEmit(intent)
    }
}

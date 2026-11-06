/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import org.mozilla.fenix.R
import org.mozilla.fenix.ext.getPreferenceKey
import org.mozilla.fenix.utils.Settings

/**
 * Fenix's Darkest (OLED) theme, which Sideraven offers and whose black the Sidebar follows. It
 * replaced 1.1's Pure black switch, which only blackened the Sidebar.
 */
object DarkestTheme {
    // 1.1's Pure black, in the Sidebar's own preferences.
    private const val SIDEBAR_PREFS = "sideraven_sidebar"
    private const val PURE_BLACK = "pure_black"

    /** A 1.1 Pure black becomes Fenix's Darkest theme, once; the old value is then removed. */
    fun migratePureBlack(
        context: Context,
        settings: Settings,
    ) {
        val sidebar = context.getSharedPreferences(SIDEBAR_PREFS, Context.MODE_PRIVATE)
        if (!sidebar.contains(PURE_BLACK)) return
        if (sidebar.getBoolean(PURE_BLACK, false)) {
            // Fenix's theme choices are separate switches, of which one is on.
            settings.shouldUseLightTheme = false
            settings.shouldUseDarkTheme = false
            settings.shouldFollowDeviceTheme = false
            settings.preferences.edit().putBoolean(context.getPreferenceKey(R.string.pref_key_auto_battery_theme), false).apply()
            settings.shouldUseOledTheme = true
        }
        sidebar.edit().remove(PURE_BLACK).apply()
    }

    /** Whether Darkest is chosen, following the setting as it changes. */
    @Composable
    fun rememberIsOn(settings: Settings): State<Boolean> {
        val on = remember(settings) { mutableStateOf(settings.shouldUseOledTheme) }
        DisposableEffect(settings) {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> on.value = settings.shouldUseOledTheme }
            settings.preferences.registerOnSharedPreferenceChangeListener(listener)
            onDispose { settings.preferences.unregisterOnSharedPreferenceChangeListener(listener) }
        }
        return on
    }
}

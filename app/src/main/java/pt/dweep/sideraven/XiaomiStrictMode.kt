/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import android.os.Build
import android.os.strictmode.DiskReadViolation
import android.os.strictmode.Violation
import androidx.annotation.RequiresApi

/** HyperOS checks its scheduler permission file from Choreographer on the UI thread. */
@RequiresApi(Build.VERSION_CODES.P)
fun isXiaomiSchedulerDiskRead(violation: Violation, isXiaomi: Boolean): Boolean =
    isXiaomi && violation is DiskReadViolation && violation.stackTrace.any {
        it.className == "miui.turbosched.TurboSchedMonitorImpl" && it.methodName == "checkFileAvailable"
    }

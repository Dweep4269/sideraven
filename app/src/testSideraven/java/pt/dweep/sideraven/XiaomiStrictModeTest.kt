/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import android.os.strictmode.DiskReadViolation
import android.os.strictmode.DiskWriteViolation
import android.os.strictmode.Violation
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import mozilla.components.support.utils.ManufacturerChecker
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.fenix.perf.ThreadPenaltyDeathWithIgnoresListener

@RunWith(AndroidJUnit4::class)
class XiaomiStrictModeTest {
    private fun listener(xiaomi: Boolean): ThreadPenaltyDeathWithIgnoresListener {
        val manufacturer = mockk<ManufacturerChecker>(relaxed = true)
        every { manufacturer.isXiaomi() } returns xiaomi
        return ThreadPenaltyDeathWithIgnoresListener(logger = mockk(relaxed = true), manufacturerChecker = manufacturer)
    }

    private fun diskRead(className: String = "miui.turbosched.TurboSchedMonitorImpl"): DiskReadViolation {
        val violation = DiskReadViolation::class.java.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        violation.stackTrace = arrayOf(StackTraceElement(className, "checkFileAvailable", "TurboSchedMonitorImpl.java", 722))
        return violation
    }

    @Test
    fun `Xiaomi scheduler disk read during a frame does not kill the app`() {
        listener(true).onThreadViolation(diskRead())
    }

    @Test(expected = RuntimeException::class)
    fun `app disk reads on Xiaomi still fail StrictMode`() {
        listener(true).onThreadViolation(diskRead("pt.dweep.sideraven.Example"))
    }

    @Test(expected = RuntimeException::class)
    fun `scheduler exception is restricted to Xiaomi devices`() {
        listener(false).onThreadViolation(diskRead())
    }

    @Test(expected = RuntimeException::class)
    fun `other scheduler violations are not ignored`() {
        val violation: Violation = DiskWriteViolation::class.java.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        violation.stackTrace = arrayOf(StackTraceElement("miui.turbosched.TurboSchedMonitorImpl", "checkFileAvailable", "TurboSchedMonitorImpl.java", 722))
        listener(true).onThreadViolation(violation)
    }
}

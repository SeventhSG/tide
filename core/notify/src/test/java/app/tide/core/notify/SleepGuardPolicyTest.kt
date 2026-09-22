package app.tide.core.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepGuardPolicyTest {

    @Test
    fun `there are exactly two wind-down reminders`() {
        assertEquals(listOf(20L, 5L), SleepGuardPolicy.WIND_DOWN_MINUTES_BEFORE)
    }

    @Test
    fun `wind-down copy states minutes plainly, and singular at one`() {
        assertEquals("Quiet hours start in 20 minutes.", SleepGuardPolicy.windDownBody(20))
        assertEquals("Quiet hours start in a minute.", SleepGuardPolicy.windDownBody(1))
    }

    @Test
    fun `an alert only fires while the phone is actually awake`() {
        assertTrue(SleepGuardPolicy.alertNeeded(isDeviceInteractive = true))
        assertFalse(
            "a phone already asleep needs no reminder to be asleep",
            SleepGuardPolicy.alertNeeded(isDeviceInteractive = false),
        )
    }

    @Test
    fun `alert copy is exact about how late it is`() {
        assertEquals(
            "Quiet hours started, and the phone is still on.",
            SleepGuardPolicy.alertBody(0),
        )
        assertEquals(
            "Quiet hours started 30 minutes ago, and the phone is still on.",
            SleepGuardPolicy.alertBody(30),
        )
    }
}

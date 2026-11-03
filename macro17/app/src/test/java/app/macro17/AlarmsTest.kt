package app.macro17

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmsTest {
    private fun al(s: Int, u: Int, e: Boolean = true) = Alarm(macroId = "m", start = s, uptime = u, enabled = e)

    @Test fun overlapIsDetectedBothWays() {
        val a = al(600, 30); val b = al(620, 30)
        assertTrue(Alarms.conflict(a, listOf(a, b))); assertTrue(Alarms.conflict(b, listOf(a, b)))
    }

    @Test fun adjacentDoesNotConflict() {
        val a = al(600, 30); val b = al(630, 10)
        assertFalse(Alarms.conflict(a, listOf(a, b)))
    }

    @Test fun wrapsPastMidnight() {
        val a = al(1430, 20); val b = al(5, 10)
        assertTrue(Alarms.conflict(b, listOf(a, b)))
        assertEquals(10, a.end)
    }

    @Test fun disabledIgnored() {
        val a = al(600, 30); val b = al(610, 30, false)
        assertFalse(Alarms.conflict(a, listOf(a, b)))
    }
}

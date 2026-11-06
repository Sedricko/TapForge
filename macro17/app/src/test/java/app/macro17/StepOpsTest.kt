package app.macro17

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StepOpsTest {
    private fun tap(goto: Int = 0) = Step(type = Type.TAP, goto = goto)
    private fun grp(n: Int, gfail: Int = 0) = Step(type = Type.GROUP, grp = n, gms = 1000, gfail = gfail)

    @Test fun duplicatingAGroupKeepsItsSize() {
        val l = listOf(grp(2), tap(), tap(), tap()).duplicated(listOf(0, 1, 2), 5000)
        assertEquals(6, l.size)
        assertEquals(2, l[3].grp) // a cópia leva só os 2 filhos copiados
        assertEquals(2, l[0].grp)
        assertTrue(l.groupsConsistent())
    }

    @Test fun duplicatedJumpsPointToTheCopies() {
        val l = listOf(tap(goto = 2), tap()).duplicated(listOf(0, 1), 5000)
        assertEquals(2, l[0].goto)
        assertEquals(4, l[2].goto) // a cópia do passo 1 salta para a cópia do passo 2
    }

    @Test fun duplicatingStopsAtTheLimit() {
        assertEquals(3, listOf(tap(), tap(), tap()).duplicated(listOf(0, 1, 2), 3).size)
        assertEquals(4, listOf(tap(), tap(), tap()).duplicated(listOf(0, 1, 2), 4).size)
    }

    @Test fun deletingManyRenumbersOnce() {
        val l = listOf(tap(goto = 4), tap(), tap(goto = 2), tap()).deleted(setOf(1, 2))
        assertEquals(listOf(2, 0), l.map { it.goto })
    }

    @Test fun deletingShrinksGroupsAndFollowsGfail() {
        val l = listOf(grp(2, gfail = 4), tap(), tap(), tap()).deleted(setOf(1))
        assertEquals(1, l[0].grp)
        assertEquals(3, l[0].gfail)
    }

    @Test fun parentAndConsistency() {
        val l = listOf(grp(1), tap(), tap())
        assertEquals(0, l.parentOf(1)); assertEquals(-1, l.parentOf(2))
        assertTrue(l.groupsConsistent())
        assertFalse(listOf(grp(5), tap()).groupsConsistent())
        assertFalse(listOf(grp(2), grp(2), tap(), tap()).groupsConsistent())
    }
}

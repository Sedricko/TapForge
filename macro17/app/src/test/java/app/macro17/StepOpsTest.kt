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
        assertEquals(7, l.size)
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

    @Test fun ungroupAppendsMarkedAtTheEndOfTheParentList() {
        val l = listOf(grp(2), tap(), tap(), tap(goto = 2)).ungrouped(0, setOf(1))
        assertEquals(1, l[0].grp)
        assertEquals(4, l[2].goto) // o marcado (antigo passo 2) foi para o fim: agora é o 4º, e o item que saltava para ele acompanha
        assertTrue(l.groupsConsistent())
    }
}

class MoveBlockTest {
    private fun tap(goto: Int = 0) = Step(type = Type.TAP, goto = goto)
    private fun grp(n: Int) = Step(type = Type.GROUP, grp = n, gms = 1000)

    @Test fun groupMovesAsABlockAndJumpsFollow() {
        val l = listOf(tap(goto = 2), grp(1), tap(), tap())
        val (out, _) = l.movedBlock(1, up = true)!!
        assertEquals(listOf(Type.GROUP, Type.TAP, Type.TAP, Type.TAP), out.map { it.type })
        assertEquals(1, out[2].goto) // o antigo passo 1 (goto 2 = o cabeçalho) agora é o 3º e aponta para o cabeçalho, que virou o 1º
        assertTrue(out.groupsConsistent())
    }

    @Test fun childStaysInsideItsGroup() {
        val l = listOf(grp(2), tap(), tap(), tap())
        assertEquals(null, l.movedBlock(1, up = true))
        assertEquals(null, l.movedBlock(2, up = false))
        assertEquals(listOf(Type.GROUP, Type.TAP, Type.TAP, Type.TAP), l.movedBlock(2, up = true)!!.first.map { it.type })
    }

    @Test fun blockedNeighbourStays() {
        val l = listOf(tap(), tap())
        assertEquals(null, l.movedBlock(1, up = true, blocked = setOf(0)))
    }
}

class DuplicateLastChildTest {
    @Test fun copyOfTheLastChildStaysInsideTheGroup() {
        val g = Step(type = Type.GROUP, grp = 2, gms = 1000)
        val l = listOf(g, Step(type = Type.TAP), Step(type = Type.TAP)).duplicated(listOf(2), 5000)
        assertEquals(3, l[0].grp)
        assertTrue(l.groupsConsistent())
    }
}

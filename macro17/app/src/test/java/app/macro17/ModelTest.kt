package app.macro17

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException

class ModelTest {
    private val tap = Step(Type.TAP, 10, 20, ms = 50)

    @Test fun validSteps() {
        assertNull(tap.validate())
        assertNull(Step(Type.SWIPE, 0, 0, 100, 100, ms = 300).validate())
        assertNull(Step(Type.WAIT, ms = 0).validate())
        assertNull(Step(Type.KEY, x1 = 4).validate())
    }

    @Test fun invalidSteps() {
        assertNotNull(Step(Type.TAP, -1, 0, ms = 50).validate())        // coordenada negativa
        assertNotNull(Step(Type.TAP, MAX_COORD + 1, 0, ms = 50).validate())
        assertNotNull(Step(Type.TAP, 1, 1, ms = 0).validate())          // gesto sem duração
        assertNotNull(Step(Type.WAIT, ms = MAX_MS + 1).validate())
        assertNotNull(Step(Type.KEY, x1 = 0).validate())                // tecla inexistente
        assertNotNull(Step(Type.KEY, x1 = 5).validate())
        assertNotNull(tap.copy(jitter = 501).validate())
    }

    @Test fun macroRules() {
        assertNull(Macro("i", "ok", 0, listOf(tap)).validate())         // 0 = infinito
        assertNotNull(Macro("i", " ", 1, listOf(tap)).validate())       // nome em branco
        assertNotNull(Macro("i", "n", -1, listOf(tap)).validate())
        assertNotNull(Macro("i", "n", MAX_LOOPS + 1, listOf(tap)).validate())
        assertNotNull(Macro("i", "n", 1, List(MAX_STEPS + 1) { tap }).validate())
        val msg = Macro("i", "n", 1, listOf(tap, tap.copy(ms = 0))).validate()
        assertEquals(true, msg!!.startsWith("passo 2:"))                // aponta o passo ruim
    }

    @Test fun readLimited() {
        assertEquals(3, Store.readLimited(ByteArrayInputStream(ByteArray(3)), 4).size)
        assertEquals(4, Store.readLimited(ByteArrayInputStream(ByteArray(4)), 4).size) // limite exato passa
        try { Store.readLimited(ByteArrayInputStream(ByteArray(5)), 4); fail() } catch (_: IOException) {}
    }
}

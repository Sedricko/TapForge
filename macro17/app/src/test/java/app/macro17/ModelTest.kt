package app.macro17

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException

class ModelTest {
    private val tap = Step(Type.TAP, 10, 20, ms = 50)

    @Test fun validSteps() {
        assertNull(tap.validate(1))
        assertNull(Step(Type.SWIPE, 0, 0, 100, 100, ms = 300).validate(1))
        assertNull(Step(Type.PINCH_IN, 100, 100, 300, 300, ms = 300).validate(1))
        assertNull(Step(Type.WAIT, ms = 0).validate(1))
        assertNull(Step(Type.KEY, x1 = 4).validate(1))
        assertNull(Step(Type.STATUS, ms = 1000, text = "oi").validate(1))
        assertNull(Step(Type.GOTO, goto = 2).validate(2))
        assertNull(Step(Type.COUNTER, counter = 3, goto = 0, cases = listOf(Case(3, 1), Case(5, 0))).validate(2))
        assertNull(Step(Type.RESET, counter = 10).validate(1))
        assertNull(Step(Type.CALL, text = "abc").validate(1))
        assertNull(Step(Type.NUMBER, 10, 10, 200, 80, ms = 3000, cmp = 3, lo = 100, hi = 900).validate(1))
        assertNull(Step(Type.NODE, ms = 3000, text = "Entrar").validate(1))
        assertNull(Step(Type.TEXT, ms = 3000, text = "Entrar|Login", cycle = 500).validate(1))                 // OCR, tela toda
        assertNull(Step(Type.TEXT, 10, 10, 300, 200, ms = 3000, text = "ok", cycle = 500).validate(1))        // OCR em área
        assertNull(Step(Type.NODE, ms = 3000, text = "id:btn_ok", exact = true, goto = 2, fail = 1).validate(2))
        assertNull(Step(Type.IMAGE, ms = 3000, text = "id").validate(1))
        assertNull(Step(Type.IMAGE, x1 = 10, y1 = 10, x2 = 200, y2 = 300, ms = 3000, text = "id", goto = 2, fail = 1).validate(2))
    }

    @Test fun invalidSteps() {
        assertNotNull(Step(Type.TAP, -1, 0, ms = 50).validate(1))              // coordenada negativa
        assertNotNull(Step(Type.TAP, MAX_COORD + 1, 0, ms = 50).validate(1))
        assertNotNull(Step(Type.TAP, 1, 1, ms = 0).validate(1))                // gesto sem duração
        assertNotNull(Step(Type.WAIT, ms = MAX_MS + 1).validate(1))
        assertNotNull(Step(Type.KEY, x1 = 0).validate(1))                      // tecla inexistente
        assertNotNull(tap.copy(jitter = 501).validate(1))
        assertNotNull(tap.copy(rnd = 100, rndMin = 200).validate(1))           // mínimo > máximo
        assertNotNull(Step(Type.PINCH_OUT, 5, 5, 5, 5, ms = 100).validate(1))  // pinça com um ponto só
        assertNotNull(Step(Type.STATUS, ms = 100, text = " ").validate(1))
        assertNotNull(Step(Type.GOTO, goto = 0).validate(3))                   // GOTO sem destino
        assertNotNull(Step(Type.GOTO, goto = 4).validate(3))                   // destino fora da lista
        assertNotNull(Step(Type.COUNTER, counter = 0).validate(1))
        assertNotNull(Step(Type.COUNTER, counter = 1, cases = listOf(Case(0, 1))).validate(2))   // limiar 0
        assertNotNull(Step(Type.COUNTER, counter = 1, cases = listOf(Case(2, 9))).validate(2))   // destino fora
        assertNotNull(Step(Type.COUNTER, counter = 1, cases = List(MAX_CASES + 1) { Case(it + 1, 0) }).validate(2))
        assertNotNull(Step(Type.CALL, text = "").validate(1))
        assertNotNull(Step(Type.IMAGE, ms = 3000, text = "i", confirm = 0).validate(1))                // confirmações fora de 1..5
        assertNotNull(Step(Type.IMAGE, ms = 3000, text = "i", confirm = 6).validate(1))
        assertNotNull(Step(Type.IMAGE, ms = 3000, text = "i", method = 2).validate(1))                 // método inexistente
        assertNotNull(Step(Type.NUMBER, 0, 0, 0, 0, ms = 3000).validate(1))                              // sem área
        assertNotNull(Step(Type.NUMBER, 50, 50, 10, 10, ms = 3000).validate(1))                          // área invertida
        assertNotNull(Step(Type.NUMBER, 10, 10, 90, 90, ms = 3000, cmp = 4).validate(1))                 // condição inexistente
        assertNotNull(Step(Type.NUMBER, 10, 10, 90, 90, ms = 3000, cmp = 3, lo = 500, hi = 100).validate(1)) // entre com máximo < mínimo
        assertNotNull(Step(Type.NUMBER, 10, 10, 90, 90, ms = 3000, lo = -1).validate(1))                 // negativo
        assertNotNull(Step(Type.NUMBER, 10, 10, 90, 90, ms = 50).validate(1))                            // prazo curto
        assertNotNull(Step(Type.TEXT, ms = 3000, text = " ", cycle = 500).validate(1))                         // OCR sem texto
        assertNotNull(Step(Type.TEXT, 10, 10, 5, 5, ms = 3000, text = "x", cycle = 500).validate(1))          // OCR com área invertida
        assertNotNull(Step(Type.TEXT, ms = 3000, text = "x", cycle = 100).validate(1))                         // OCR com intervalo curto demais
        assertNotNull(Step(Type.NODE, ms = 3000, text = " ").validate(1))                             // elemento sem texto
        assertNotNull(Step(Type.NODE, ms = 50, text = "x").validate(1))                               // prazo curto
        assertNotNull(Step(Type.NODE, ms = 3000, text = "x", fail = 9).validate(2))                   // destino fora
        assertNotNull(Step(Type.IMAGE, ms = 3000, text = "").validate(1))                            // sem recorte
        assertNotNull(Step(Type.IMAGE, ms = 3000, text = "i", thr = 10).validate(1))                // semelhança abaixo de 20%
        assertNotNull(Step(Type.IMAGE, ms = 50, text = "i").validate(1))                             // prazo curto demais
        assertNotNull(Step(Type.IMAGE, ms = 3000, text = "i", cycle = 10).validate(1))              // intervalo curto demais
        assertNotNull(Step(Type.IMAGE, x1 = 50, y1 = 50, x2 = 10, y2 = 10, ms = 3000, text = "i").validate(1)) // área invertida
        assertNotNull(Step(Type.IMAGE, ms = 3000, text = "i", fail = 5).validate(2))                // destino fora
    }

    @Test fun macroRules() {
        assertNull(Macro("i", "ok", 0, listOf(tap)).validate())                // 0 = infinito
        assertNotNull(Macro("i", " ", 1, listOf(tap)).validate())              // nome em branco
        assertNotNull(Macro("i", "n", -1, listOf(tap)).validate())
        assertNotNull(Macro("i", "n", MAX_LOOPS + 1, listOf(tap)).validate())
        assertNotNull(Macro("i", "n", 1, List(MAX_STEPS + 1) { tap }).validate())
        val msg = Macro("i", "n", 1, listOf(tap, tap.copy(ms = 0))).validate()
        assertTrue(msg!!.startsWith("passo 2:"))                               // aponta o passo ruim
        assertNotNull(Macro("i", "n", 1, listOf(Step(Type.GOTO, goto = 5), tap)).validate())      // alvo fora
    }

    // --- renumeração dos alvos ao editar a lista ---
    private val a = tap.copy(x1 = 1)
    private val b = tap.copy(x1 = 2)
    private fun jumpTo(n: Int) = Step(Type.GOTO, goto = n)

    @Test fun deleteRenumbers() {
        val steps = listOf(jumpTo(3), a, b, jumpTo(2)) // passos 1..4: 1→3, 4→2
        val out = steps.afterDelete(1)                  // apaga o passo 2
        assertEquals(3, out.size)
        assertEquals(2, out[0].goto)                    // 3 desce para 2
        assertEquals(0, out[2].goto)                    // apontava para o apagado: vira "seguir"
    }

    @Test fun swapKeepsTargets() {
        val steps = listOf(jumpTo(2), a, jumpTo(1))     // 1→2, 3→1
        val out = steps.afterSwap(0, 1)                 // troca passos 1 e 2
        assertEquals(a, out[0])
        assertEquals(1, out[1].goto)                    // o GOTO foi para a posição 2 e ainda aponta para o mesmo passo (agora 1)
        assertEquals(2, out[2].goto)                    // o passo 3 apontava para o antigo 1, agora na posição 2
    }

    @Test fun insertShiftsTargets() {
        val steps = listOf(jumpTo(2), a)                // 1→2
        val out = steps.afterInsert(1, b)               // insere na posição 2 (0-based 1)
        assertEquals(3, out.size)
        assertEquals(3, out[0].goto)                    // o passo 2 virou 3
        assertEquals(b, out[1])
    }

    @Test fun counterCasesAreRenumbered() {
        val c = Step(Type.COUNTER, goto = 3, cases = listOf(Case(2, 2), Case(4, 3)))
        val out = listOf(c, a, b).afterDelete(1)
        assertEquals(2, out[0].goto)
        assertEquals(listOf(Case(2, 0), Case(4, 2)), out[0].cases)
    }

    @Test fun imageFailTargetIsRenumbered() {
        val img = Step(Type.IMAGE, ms = 1000, text = "i", goto = 3, fail = 2)
        val out = listOf(img, a, b).afterDelete(1)       // apaga o passo 2
        assertEquals(2, out[0].goto)                      // 3 desce para 2
        assertEquals(0, out[0].fail)                      // apontava para o apagado: vira "seguir"
    }

    @Test fun readLimited() {
        assertEquals(3, Store.readLimited(ByteArrayInputStream(ByteArray(3)), 4).size)
        assertEquals(4, Store.readLimited(ByteArrayInputStream(ByteArray(4)), 4).size) // limite exato passa
        try { Store.readLimited(ByteArrayInputStream(ByteArray(5)), 4); fail() } catch (_: IOException) {}
    }
}

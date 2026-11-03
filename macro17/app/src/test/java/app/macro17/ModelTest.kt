package app.macro17

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    // --- edição da lista ---
    @Test fun insertKeepsTheNewStepsOwnTargets() {
        // passo novo no fim que salta para si mesmo (laço): continua válido depois de inserido
        val self = Step(Type.GOTO, goto = 2)
        val out = listOf(a).afterInsert(1, self)
        assertEquals(2, out[1].goto)
        assertNull(Macro("i", "n", 1, out).validate())
    }

    @Test fun duplicateCopyFollowsRenumbering() {
        val x = Step(Type.GOTO, goto = 2)                // passo 1 → 2
        val y = Step(Type.GOTO, goto = 1)                // passo 2 → 1
        val out = listOf(x, y).let { it.afterInsert(1, it[0].shiftedFrom(1)) }
        assertEquals(listOf(3, 3, 1), out.map { it.goto })   // y virou o passo 3; x e a cópia apontam para ele; y ainda volta ao 1
    }

    @Test fun swapRenumbersCountersCasesAndFailTargets() {
        val c = Step(Type.COUNTER, goto = 3, cases = listOf(Case(1, 2)))
        val img = Step(Type.IMAGE, ms = 1000, text = "i", goto = 2, fail = 3)
        val outC = listOf(c, a, b).afterSwap(1, 2)       // troca os passos 2 e 3
        assertEquals(2, outC[0].goto); assertEquals(listOf(Case(1, 3)), outC[0].cases)
        val outI = listOf(img, a, b).afterSwap(1, 2)
        assertEquals(3, outI[0].goto); assertEquals(2, outI[0].fail)
    }

    // --- JSON ---
    private fun rich(t: Type) = Step(
        t, 11, 22, 33, 44, ms = 555, delay = 3, rnd = 400, rndMin = 100, jitter = 7, skip = true, goto = 2, counter = 4,
        cases = listOf(Case(2, 1), Case(5, 2)), text = "texto ç ã", ret = false, thr = 77, cycle = 321, fail = 1, tap = false,
        exact = true, scales = true, cmp = 3, lo = 10, hi = 20, method = 1, mono = true, confirm = 3,
        dbl = true, ox = 12, oy = -34, vib = true, snd = true, minConf = 65,
    )

    @Test fun everyTypeSurvivesAJsonRoundTrip() {
        val m = Macro("id", "Minha macro", 5, Type.values().map { rich(it) })
        val back = Macro.fromJson(org.json.JSONObject(m.toJson().toString()), "id")
        assertEquals(m, back)                            // data class: todos os campos de todos os tipos
    }

    @Test fun oldJsonWithoutNewFieldsGetsDefaults() {
        val s = Step.fromJson(org.json.JSONObject("{\"t\":\"TAP\",\"x1\":5}"))
        assertEquals(5, s.x1)
        assertEquals(90, s.thr); assertEquals(200, s.cycle); assertEquals(1, s.confirm)
        assertTrue(s.tap); assertTrue(s.ret); assertEquals(1, s.counter)
    }

    @Test fun unknownTypeIsRejectedByFromJson() {
        try { Step.fromJson(org.json.JSONObject("{\"t\":\"INEXISTENTE\"}")); fail() } catch (_: IllegalArgumentException) {}
    }

    @Test fun missingTypeIsRejectedByFromJson() {
        try { Step.fromJson(org.json.JSONObject("{}")); fail() } catch (_: org.json.JSONException) {}
    }

    @Test fun counterCasesBeyondTheLimitAreTruncated() {
        val cs = org.json.JSONArray().also { arr -> repeat(MAX_CASES + 3) { arr.put(org.json.JSONObject().put("n", it + 1).put("to", 0)) } }
        val s = Step.fromJson(org.json.JSONObject().put("t", "COUNTER").put("cs", cs))
        assertEquals(MAX_CASES, s.cases.size)
    }

    @Test fun macroValidationBoundaries() {
        assertNotNull(Macro("i", " ", 1, emptyList()).validate())                       // nome em branco
        assertNotNull(Macro("i", "x".repeat(61), 1, emptyList()).validate())            // nome longo
        assertNotNull(Macro("i", "n", -1, emptyList()).validate())                       // repetições negativas
        assertNull(Macro("i", "x".repeat(60), 0, emptyList()).validate())                // 60 chars e infinito: limite exato válido
        assertNotNull(Macro("i", "n", 1, List(MAX_STEPS + 1) { Step(Type.WAIT, ms = 1) }).validate())
    }

    // --- manual oficial do base ---
    @Test fun zeroSearchTimeoutIsInfiniteAndForbidsJumps() {
        for (t in listOf(Type.NODE, Type.IMAGE, Type.TEXT, Type.NUMBER)) {
            val ok = Step(t, 0, 0, 100, 100, ms = 0, text = "x", cycle = 500, cmp = 0, lo = 1)
            assertNull("$t sem limite e sem desvios é válido", ok.validate(3))
            assertNotNull("$t sem limite com 'se achar' é inválido", ok.copy(goto = 2).validate(3))
            assertNotNull("$t sem limite com 'se não achar' é inválido", ok.copy(fail = 2).validate(3))
            assertNotNull("$t com 50 ms continua curto demais", ok.copy(ms = 50).validate(3))
        }
    }

    @Test fun screenshotKeyIsValidAndOthersStayInvalid() {
        assertNull(Step(Type.KEY, x1 = KEY_SCREENSHOT).validate(1))
        assertNull(Step(Type.KEY, x1 = 3).validate(1))
        assertNotNull(Step(Type.KEY, x1 = 5).validate(1))
        assertNotNull(Step(Type.KEY, x1 = 0).validate(1))
    }

    @Test fun tapOffsetAndConfidenceBounds() {
        assertNull(Step(Type.NODE, ms = 1000, text = "x", ox = -MAX_COORD, oy = MAX_COORD).validate(1))
        assertNotNull(Step(Type.NODE, ms = 1000, text = "x", ox = MAX_COORD + 1).validate(1))
        assertNull(Step(Type.TEXT, ms = 1000, text = "x", cycle = 500, minConf = 100).validate(1))
        assertNotNull(Step(Type.TEXT, ms = 1000, text = "x", cycle = 500, minConf = 101).validate(1))
        assertNotNull(Step(Type.TEXT, ms = 1000, text = "x", cycle = 500, minConf = -1).validate(1))
    }

    @Test fun oldJsonWithoutTheNewFieldsKeepsTheOldBehavior() {
        val s = Step.fromJson(org.json.JSONObject("{\"t\":\"NODE\",\"ms\":3000,\"tx\":\"a\",\"cy\":200}"))
        assertFalse(s.dbl); assertEquals(0, s.ox); assertEquals(0, s.oy); assertFalse(s.vib); assertFalse(s.snd); assertEquals(0, s.minConf)
    }
}

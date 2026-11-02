package app.macro17

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Portas falsas: gravam o que o executor faz, sem Android e sem esperar de verdade. */
private class Fake(
    override val width: Int = 1000,
    override val height: Int = 2000,
    val macros: Map<String, Macro> = emptyMap(),
    val rolls: ArrayDeque<Int> = ArrayDeque(),
    val failGesture: Boolean = false,
    val stopAfterGestures: Int = Int.MAX_VALUE,
) : Ports {
    val events = ArrayList<String>()
    var gestures = 0
    val sleeps = ArrayList<Long>()
    override fun sleep(ms: Long) { sleeps += ms }
    override fun gesture(strokes: List<Stroke>, ms: Int) {
        if (failGesture) throw IllegalStateException("gesto rejeitado")
        if (gestures++ >= stopAfterGestures) throw InterruptedException()
        events += "g:" + strokes.joinToString("|") { "${it.x1},${it.y1}>${it.x2},${it.y2}" } + "@$ms"
    }
    override fun key(code: Int) { events += "k:$code" }
    override fun status(text: String, ms: Int) { events += "s:$text" }
    override fun resolve(id: String) = macros[id]
    override fun nextInt(bound: Int) = if (rolls.isEmpty()) 0 else rolls.removeFirst().coerceIn(0, bound - 1)
    override fun log(line: String) { events += "l:$line" }
}

class RunnerTest {
    private fun tap(x: Int, delay: Int = 0) = Step(Type.TAP, x, 100, ms = 10, delay = delay)
    private fun macro(vararg s: Step, loops: Int = 1, id: String = "m") = Macro(id, "n", loops, s.toList())
    private fun run(m: Macro, f: Fake = Fake()): Pair<Outcome, Fake> = Runner(f).run(m) to f

    @Test fun runsStepsInOrderAndRepeats() {
        val (o, f) = run(macro(tap(1), tap(2), loops = 2))
        assertTrue(o is Outcome.Done)
        assertEquals(listOf("g:1,100>1,100@10", "g:2,100>2,100@10", "g:1,100>1,100@10", "g:2,100>2,100@10"), f.events)
    }

    @Test fun delayRunsBeforeAction() {
        val (_, f) = run(macro(tap(1, delay = 500)))
        assertEquals(500L, f.sleeps.first())   // o atraso vem antes do gesto
    }

    @Test fun zeroDelayStillYieldsOneMs() {
        val (_, f) = run(macro(tap(1)))
        assertEquals(1L, f.sleeps.first())     // laço sem espera não vira laço quente
    }

    @Test fun randomRangeReplacesFixedDelay() {
        val s = tap(1, delay = 9999).copy(rnd = 300, rndMin = 100)
        val f = Fake(rolls = ArrayDeque(listOf(50)))
        run(macro(s), f)
        assertEquals(150L, f.sleeps.first())   // 100 + 50; o fixo de 9999 é ignorado
    }

    @Test fun skipRunsOnlyTheDelay() {
        val (_, f) = run(macro(tap(1, delay = 200).copy(skip = true), tap(2)))
        assertEquals(listOf("g:2,100>2,100@10"), f.events)
        assertEquals(200L, f.sleeps.first())
    }

    @Test fun gotoJumpsAndLogs() {
        val (_, f) = run(macro(Step(Type.GOTO, goto = 3), tap(1), tap(2)))
        assertEquals(listOf("l:Passo 1 → Passo 3", "g:2,100>2,100@10"), f.events)
    }

    @Test fun gotoLoopStopsWhenInterrupted() {
        val f = Fake(stopAfterGestures = 3)
        val (o, _) = run(macro(tap(1), Step(Type.GOTO, goto = 1), loops = 1), f)
        assertTrue(o is Outcome.Stopped)
        assertEquals(3, f.events.count { it.startsWith("g:") })
    }

    private fun xs(f: Fake) = f.events.filter { it.startsWith("g:") }.map { it.substring(2, 3) }

    @Test fun counterPicksHighestReachedThresholdAndCycles() {
        // passo 1 conta; casos: 2→passo 3, 4→passo 4; sem caso, segue. Cinco voltas.
        val counter = Step(Type.COUNTER, counter = 1, goto = 0, cases = listOf(Case(2, 3), Case(4, 4)))
        val (_, f) = run(macro(counter, tap(1), tap(2), tap(3), loops = 5))
        // volta 1: n=1 sem caso → 1,2,3 · volta 2: n=2 → passo 3 → 2,3 · volta 3: n=3 (vale o caso 2) → 2,3
        // volta 4: n=4 → passo 4 → 3, e zera (alcançou o último limiar) · volta 5: n=1 → 1,2,3
        assertEquals(listOf("1", "2", "3", "2", "3", "2", "3", "3", "1", "2", "3"), xs(f))
    }

    @Test fun counterResetsAtTopThreshold() {
        val counter = Step(Type.COUNTER, counter = 2, goto = 0, cases = listOf(Case(2, 3)))
        val (_, f) = run(macro(counter, tap(1), tap(2), loops = 4))
        // n=1 → 1,2 · n=2 → passo 3 (tap 2) e zera · n=1 → 1,2 · n=2 → 2
        assertEquals(listOf("1", "2", "2", "1", "2", "2"), xs(f))
    }

    @Test fun resetZeroesCounter() {
        val m = macro(
            Step(Type.COUNTER, counter = 1, goto = 0, cases = listOf(Case(2, 4))), // n=1 não salta; n=2 salta ao 4
            Step(Type.RESET, counter = 1),
            tap(1),
            tap(2),
            loops = 3,
        )
        val (_, f) = run(m)
        // o RESET impede n de chegar a 2: nunca salta, sempre executa tap(1) e tap(2)
        assertEquals(6, f.events.count { it.startsWith("g:") })
        assertTrue(f.events.none { it.startsWith("l:") })
    }

    @Test fun pinchGeometry() {
        val f = Fake()
        run(macro(Step(Type.PINCH_IN, 100, 100, 300, 500, ms = 200), Step(Type.PINCH_OUT, 100, 100, 300, 500, ms = 200)), f)
        assertEquals("g:100,100>200,300|300,500>200,300@200", f.events[0])  // dois dedos rumo ao centro
        assertEquals("g:200,300>100,100|200,300>300,500@200", f.events[1])  // e do centro para as pontas
    }

    @Test fun jitterMovesWholeGestureByOneOffset() {
        val f = Fake(rolls = ArrayDeque(listOf(0, 20)))  // dx = 0-10 = -10 ; dy = 20-10 = +10
        run(macro(Step(Type.SWIPE, 100, 100, 200, 300, ms = 50, jitter = 10)), f)
        assertEquals("g:90,110>190,310@50", f.events.single())  // mesmo (dx,dy) nos dois pontos
    }

    @Test fun gestureIsClampedToScreen() {
        val f = Fake(width = 500, height = 500, rolls = ArrayDeque(listOf(20, 20)))
        run(macro(Step(Type.TAP, 495, 495, ms = 10, jitter = 10)), f)
        assertEquals("g:499,499>499,499@10", f.events.single())
    }

    @Test fun keyAndStatus() {
        val (_, f) = run(macro(Step(Type.KEY, x1 = 2), Step(Type.STATUS, ms = 10, text = "oi")))
        assertEquals(listOf("k:2", "s:oi"), f.events)
    }

    @Test fun gestureRejectedFailsWithReason() {
        val (o, _) = run(macro(tap(1)), Fake(failGesture = true))
        assertEquals("gesto rejeitado", (o as Outcome.Failed).why)
    }

    @Test fun emptyMacroFails() {
        assertTrue(run(Macro("m", "n", 1, emptyList())).first is Outcome.Failed)
    }

    // --- chamada de outra macro ---
    private val child = Macro("c", "filha", 1, listOf(tap(9)))

    @Test fun callReturnsToCaller() {
        val m = macro(Step(Type.CALL, text = "c", ret = true), tap(2))
        val (_, f) = run(m, Fake(macros = mapOf("c" to child)))
        assertEquals(listOf("g:9,100>9,100@10", "g:2,100>2,100@10"), f.events)  // filha primeiro, depois o passo 2 do chamador
    }

    @Test fun callWithoutReturnReplacesCaller() {
        val m = macro(Step(Type.CALL, text = "c", ret = false), tap(2))
        val (_, f) = run(m, Fake(macros = mapOf("c" to child)))
        assertEquals(listOf("g:9,100>9,100@10"), f.events)                       // o passo 2 do chamador nunca roda
    }

    @Test fun callAtEndOfLoopedCallerResumesCorrectly() {
        val m = macro(Step(Type.CALL, text = "c", ret = true), loops = 2)
        val (o, f) = run(m, Fake(macros = mapOf("c" to child)))
        assertTrue(o is Outcome.Done)
        assertEquals(2, f.events.size)                                            // uma chamada por volta
    }

    @Test fun missingCalledMacroFails() {
        val (o, _) = run(macro(Step(Type.CALL, text = "x")))
        assertEquals("macro chamada não encontrada", (o as Outcome.Failed).why)
    }

    @Test fun selfCallHitsDepthLimit() {
        val self = Macro("s", "s", 1, listOf(Step(Type.CALL, text = "s", ret = true)))
        val (o, _) = run(self, Fake(macros = mapOf("s" to self)))
        assertTrue((o as Outcome.Failed).why.startsWith("chamadas aninhadas"))
    }

    @Test fun invalidCalledMacroFails() {
        val bad = Macro("b", "ruim", 1, listOf(Step(Type.GOTO, goto = 9)))
        val (o, _) = run(macro(Step(Type.CALL, text = "b")), Fake(macros = mapOf("b" to bad)))
        assertTrue((o as Outcome.Failed).why.contains("inválida"))
    }

    @Test fun countersAreIndependentPerCalledMacro() {
        // filha: o contador só salta ao 2º acesso; se fosse compartilhado, a 2ª chamada pularia o tap 9
        val childC = Macro("c", "f", 1, listOf(Step(Type.COUNTER, counter = 1, cases = listOf(Case(2, 3))), tap(9), tap(8)))
        val m = macro(Step(Type.CALL, text = "c"), Step(Type.CALL, text = "c"))
        val (_, f) = run(m, Fake(macros = mapOf("c" to childC)))
        assertEquals(listOf("9", "8", "9", "8"), xs(f))
    }
}

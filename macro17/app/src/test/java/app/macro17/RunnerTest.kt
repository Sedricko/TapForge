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
    val frame: Gray? = null,                       // quadro que screen() devolve quando a fila está vazia
    val templates: Map<String, Gray> = emptyMap(),
) : Ports {
    val nodes = ArrayDeque<Pair<Int, Int>?>()      // resultados de findNode em ordem; esgotada = nada achado
    val queries = ArrayList<String>()
    val queue = ArrayDeque<Gray?>()                // quadros em ordem; null simula captura que falhou
    var shots = 0
    var clock = 0L
    val events = ArrayList<String>()
    var gestures = 0
    val sleeps = ArrayList<Long>()
    override fun sleep(ms: Long) { sleeps += ms; clock += ms }
    override fun now() = clock
    override fun screen(): Gray? { shots++; return if (queue.isNotEmpty()) queue.removeFirst() else frame }
    override fun template(id: String) = templates[id]
    override fun findNode(query: String, exact: Boolean): Pair<Int, Int>? { queries += "$query|$exact"; return if (nodes.isNotEmpty()) nodes.removeFirst() else null }
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

    // --- busca de imagem ---
    private fun noise(w: Int, h: Int, seed: Int): Gray { val r = kotlin.random.Random(seed); return Gray(w, h, IntArray(w * h) { r.nextInt(256) }) }
    private fun plant(hay: Gray, n: Gray, x: Int, y: Int) {
        for (j in 0 until n.h) for (i in 0 until n.w) hay.d[(y + j) * hay.w + x + i] = n.d[j * n.w + i]
    }
    private val needle = noise(30, 20, 7)
    private fun screenWith(x: Int, y: Int) = noise(200, 150, 8).also { plant(it, needle, x, y) }  // x e y pares: a busca grossa reduz por 2
    private fun image(goto: Int = 0, fail: Int = 0, ms: Int = 1000) = Step(Type.IMAGE, ms = ms, text = "t", goto = goto, fail = fail, cycle = 100, thr = 95)
    private fun fakeWith(frame: Gray?) = Fake(frame = frame, templates = mapOf("t" to needle))

    @Test fun imageFoundTapsCenter() {
        val (o, f) = run(macro(image()), fakeWith(screenWith(50, 60)))
        assertTrue(o is Outcome.Done)
        assertEquals(listOf("g:65,70>65,70@50"), f.events)        // centro do recorte: (50+15, 60+10)
    }

    @Test fun imageSuccessFollowsGotoAndFailureFollowsFail() {
        val m = macro(image(goto = 3, fail = 2), tap(1), tap(2))
        assertEquals(listOf("2"), tapXs(run(m, fakeWith(screenWith(50, 60))).second))   // achou: pula para o passo 3
        assertEquals(listOf("1", "2"), tapXs(run(m, fakeWith(noise(200, 150, 9))).second)) // não achou: vai ao passo 2
    }

    // só os gestos: um salto também grava a linha "Passo N → Passo M" em events
    private fun gestures(f: Fake) = f.events.filter { it.startsWith("g:") }

    // só os toques dos passos (10 ms); o toque do achado dura 50 ms e fica de fora
    private fun tapXs(f: Fake) = f.events.filter { it.startsWith("g:") && !it.endsWith("@50") }.map { it.substring(2, 3) }

    @Test fun imageTimesOutAfterRetrying() {
        val (_, f) = run(macro(image(ms = 500)), fakeWith(noise(200, 150, 9)))
        assertEquals(6, f.shots)                                   // 0, 100, ..., 500 ms
        assertTrue(f.events.isEmpty())                             // nada achado, nada tocado
    }

    @Test fun imageFoundOnThirdAttempt() {
        val f = fakeWith(null)
        f.queue.addAll(listOf(noise(200, 150, 9), null, screenWith(50, 60)))  // uma tela sem o recorte, uma captura que falhou
        run(macro(image()), f)
        assertEquals(3, f.shots)
        assertEquals(listOf("g:65,70>65,70@50"), f.events)
    }

    @Test fun imageSearchAreaIsCroppedAndMappedBack() {
        val area = image().copy(x1 = 100, y1 = 60, x2 = 180, y2 = 140)
        val (_, inside) = run(macro(area), fakeWith(screenWith(120, 80)))
        assertEquals(listOf("g:135,90>135,90@50"), inside.events)   // (120+15, 80+10) em coordenadas da tela inteira
        val (_, outside) = run(macro(area), fakeWith(screenWith(10, 10)))
        assertTrue(outside.events.isEmpty())                        // fora da área: não conta
    }

    @Test fun imageWithoutTapOrWithSkipStillJumps() {
        val m = macro(image(goto = 3).copy(tap = false), tap(1), tap(2))
        assertEquals(listOf("g:2,100>2,100@10"), gestures(run(m, fakeWith(screenWith(50, 60))).second))
        val m2 = macro(image(goto = 3).copy(skip = true), tap(1), tap(2))
        assertEquals(listOf("g:2,100>2,100@10"), gestures(run(m2, fakeWith(screenWith(50, 60))).second))
    }

    @Test fun imageMissingTemplateFails() {
        val (o, _) = run(macro(image()), Fake(frame = screenWith(50, 60)))
        assertTrue((o as Outcome.Failed).why.contains("imagem do passo 1"))
    }

    // --- achar por texto (árvore de acessibilidade) ---
    private fun node(goto: Int = 0, fail: Int = 0, ms: Int = 1000) = Step(Type.NODE, ms = ms, text = "Entrar", goto = goto, fail = fail, cycle = 100)

    @Test fun nodeFoundTapsItsCenter() {
        val f = Fake().also { it.nodes.add(540 to 1200) }
        val (o, _) = run(macro(node()), f)
        assertTrue(o is Outcome.Done)
        assertEquals(listOf("g:540,1200>540,1200@50"), f.events)
        assertEquals(listOf("Entrar|false"), f.queries)             // "contém" por padrão
    }

    @Test fun nodeExactFlagIsForwarded() {
        val f = Fake().also { it.nodes.add(1 to 1) }
        run(macro(node().copy(exact = true)), f)
        assertEquals(listOf("Entrar|true"), f.queries)
    }

    @Test fun nodeRetriesUntilFound() {
        val f = Fake().also { it.nodes.addAll(listOf(null, null, 300 to 400)) }  // transição, nada, achou
        run(macro(node()), f)
        assertEquals(3, f.queries.size)
        assertEquals(listOf("g:300,400>300,400@50"), f.events)
    }

    @Test fun nodeTimeoutFollowsFail() {
        val m = macro(node(goto = 3, fail = 2, ms = 500), tap(1), tap(2))
        val f = Fake()
        run(m, f)
        assertEquals(6, f.queries.size)                             // 0, 100, ..., 500 ms
        assertEquals(listOf("1", "2"), tapXs(f))                    // não achou: vai ao passo 2 e segue
    }

    @Test fun nodeFoundFollowsGotoWithoutTapWhenDisabled() {
        val m = macro(node(goto = 3).copy(tap = false), tap(1), tap(2))
        val f = Fake().also { it.nodes.add(10 to 10) }
        run(m, f)
        assertEquals(listOf("g:2,100>2,100@10"), gestures(f))       // sem toque no achado; saltou para o passo 3
    }
}

package app.macro17

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    val glyphSet: GlyphSet = GlyphSet(),
    val crashGesture: Boolean = false,             // simula falha inesperada (RuntimeException fora do contrato)
) : Ports {
    val traces = ArrayList<String>()
    override fun trace(line: String) { traces += line }
    val texts = ArrayDeque<Pair<Int, Int>?>()      // resultados de findText em ordem; esgotada = nada achado
    val textQueries = ArrayList<String>()
    val textConfs = ArrayList<Int>()
    val alerts = ArrayList<String>()
    override fun alert(vib: Boolean, snd: Boolean) { alerts += "vib=$vib snd=$snd" }
    override fun findText(query: String, exact: Boolean, area: IntArray?, minConf: Int): Pair<Int, Int>? {
        textConfs += minConf
        textQueries += "$query|$exact|${area?.joinToString(",") ?: "tela"}"
        return if (texts.isNotEmpty()) texts.removeFirst() else null
    }
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
    override fun glyphs() = glyphSet
    override fun findNode(query: String, exact: Boolean): Pair<Int, Int>? { queries += "$query|$exact"; return if (nodes.isNotEmpty()) nodes.removeFirst() else null }
    override fun gesture(strokes: List<Stroke>, ms: Int) {
        if (failGesture) throw IllegalStateException("gesto rejeitado")
        if (crashGesture) throw ArithmeticException("boom")
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

    @Test fun stopItemEndsThePlaybackEvenWithLoopsLeft() {
        val (o, f) = run(macro(tap(1), Step(Type.STOP), tap(2), loops = 3))
        assertTrue(o is Outcome.Done)
        assertEquals(listOf("g:1,100>1,100@10"), f.events) // nada depois do Parar Macro, nem nas próximas voltas
    }

    @Test fun disabledItemIsPassedWithoutDelayOrAction() {
        val (o, f) = run(macro(tap(1), tap(2, delay = 500).copy(off = true), tap(3)))
        assertTrue(o is Outcome.Done)
        assertEquals(listOf("g:1,100>1,100@10", "g:3,100>3,100@10"), f.events)
        assertTrue(f.sleeps.none { it == 500L })
    }

    @Test fun disabledFlagSurvivesJson() {
        val m = macro(tap(1).copy(off = true))
        assertTrue(Macro.fromJson(m.toJson(), "m").steps[0].off)
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

    @Test fun imageFoundOnADimmerScreen() {
        // a mesma tela com brilho e contraste menores: o SAD perderia o recorte, a correlação não
        val dimmed = screenWith(50, 60).let { f -> Gray(f.w, f.h, IntArray(f.d.size) { (f.d[it] * 5 / 10 + 40).coerceIn(0, 255) }) }
        // o método padrão é o do app de referência (correlação): acha mesmo com brilho e contraste menores
        val (_, f) = run(macro(image()), fakeWith(dimmed))
        assertEquals(listOf("g:65,70>65,70@50"), gestures(f))
        // a diferença de pixels (método 1) exige a tela idêntica: a mesma tela mais escura não conta
        assertTrue(gestures(run(macro(image().copy(method = 1)), fakeWith(dimmed)).second).isEmpty())
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
        assertTrue(gestures(f).isEmpty())                          // nada achado, nada tocado
        assertTrue(f.events.any { it.startsWith("l:Passo 1: imagem não achada") })   // e diz por quê
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
        assertTrue(gestures(outside).isEmpty())                     // fora da área: não conta
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

    // --- ler número ---
    private fun trainedSet() = GlyphSet().also { Digits.learn(TestFont.render("0123456789", 3, 220, 20), "0123456789", it) }
    private fun screenWithNumber(text: String): Gray {
        val f = Gray(300, 200, IntArray(300 * 200) { 20 })
        plant(f, TestFont.render(text, 3, 220, 20), 40, 30)
        return f
    }
    private fun number(cmp: Int, lo: Int, hi: Int = 0, goto: Int = 0, fail: Int = 0, ms: Int = 1000) =
        Step(Type.NUMBER, 30, 20, 140, 70, ms = ms, cycle = 100, cmp = cmp, lo = lo, hi = hi, goto = goto, fail = fail)
    private fun withNumber(text: String) = Fake(frame = screenWithNumber(text), glyphSet = trainedSet())

    @Test fun numberConditionTrueFollowsGoto() {
        val m = macro(number(cmp = 0, lo = 1000, goto = 3, fail = 2), tap(1), tap(2))     // 1234 ≥ 1000
        assertEquals(listOf("g:2,100>2,100@10"), gestures(run(m, withNumber("1234")).second))
    }

    @Test fun numberConditionFalseTimesOutAndFollowsFail() {
        val m = macro(number(cmp = 0, lo = 5000, goto = 3, fail = 2, ms = 300), tap(1), tap(2))   // 1234 ≥ 5000 nunca vale
        val f = withNumber("1234")
        run(m, f)
        assertEquals(4, f.shots)                                         // 0, 100, 200, 300 ms
        assertEquals(listOf("1", "2"), tapXs(f))
    }

    @Test fun numberComparisons() {
        fun ok(cmp: Int, lo: Int, hi: Int = 0): Boolean {
            val f = withNumber("1234")
            run(macro(number(cmp, lo, hi, goto = 3, fail = 2, ms = 100), tap(1), tap(2)), f)
            return tapXs(f) == listOf("2")                               // só o passo 3 roda se valeu
        }
        assertTrue(ok(0, 1234)); assertTrue(!ok(0, 1235))                // ≥
        assertTrue(ok(1, 1234)); assertTrue(!ok(1, 1233))                // ≤
        assertTrue(ok(2, 1234)); assertTrue(!ok(2, 1233))                // =
        assertTrue(ok(3, 1000, 2000)); assertTrue(!ok(3, 2000, 3000))    // entre
    }

    @Test fun numberWithoutTrainingFailsWithAHint() {
        val (o, _) = run(macro(number(0, 1)), Fake(frame = screenWithNumber("1234")))
        assertTrue((o as Outcome.Failed).why.contains("ainda não aprendeu"))
    }

    @Test fun unreadableNumberCountsAsFailure() {
        val blank = Gray(300, 200, IntArray(300 * 200) { 20 })
        val m = macro(number(cmp = 0, lo = 1, goto = 3, fail = 2, ms = 200), tap(1), tap(2))
        val f = Fake(frame = blank, glyphSet = trainedSet())
        run(m, f)
        assertEquals(listOf("1", "2"), tapXs(f))
    }

    // --- fidelidade ao app de referência e confirmação em quadros seguidos ---
    @Test fun imageFollowsTheConfiguredPercentage() {
        // 10% dos pixels do modelo bem diferentes: correlação ~0,85. Com 75% configurado acha, com 90% não
        val tpl = noise(20, 20, 31)
        val frame = noise(60, 60, 32).also { plant(it, tpl, 20, 20) }          // fundo texturizado: sem outra janela idêntica
        for (i in 0 until 40) { val k = (20 + i / 20) * 60 + 20 + i % 20; frame.d[k] = (frame.d[k] + 128) % 256 }
        fun hits(thr: Int): Boolean {
            val f = Fake(frame = frame, templates = mapOf("t" to tpl))
            run(macro(Step(Type.IMAGE, ms = 100, text = "t", thr = thr, cycle = 100)), f)
            return gestures(f).isNotEmpty()
        }
        assertTrue(hits(75)); assertTrue(!hits(90)); assertTrue(!hits(100))
    }

    @Test fun imageFailureReportsHowCloseItGot() {
        val f = fakeWith(noise(200, 150, 9))
        run(macro(image(ms = 100)), f)
        val line = f.events.single { it.startsWith("l:Passo 1: imagem não achada") }
        assertTrue(line.contains("exigido 95%"))
    }

    @Test fun confirmRequiresConsecutiveFramesAtTheSamePlace() {
        val hit = screenWith(50, 60); val miss = noise(200, 150, 9)
        val f = fakeWith(null)
        f.queue.addAll(listOf(hit, miss, hit, hit))            // achou, sumiu, achou, achou de novo
        run(macro(image().copy(confirm = 2)), f)
        assertEquals(4, f.shots)                               // só o 4º quadro fecha dois seguidos
        assertEquals(listOf("g:65,70>65,70@50"), f.events.filter { it.startsWith("g:") })
    }

    @Test fun confirmIgnoresAFrameWhereTheMatchMoved() {
        val a = screenWith(50, 60); val b = screenWith(100, 40)
        val f = fakeWith(null)
        f.queue.addAll(listOf(a, b, b))                        // em lugares diferentes não conta como confirmado
        run(macro(image().copy(confirm = 2)), f)
        assertEquals(3, f.shots)
        assertEquals(listOf("g:115,50>115,50@50"), f.events.filter { it.startsWith("g:") })
    }

    // --- ler texto da tela (OCR) ---
    private fun ocr(goto: Int = 0, fail: Int = 0, ms: Int = 1000, area: IntArray? = null) =
        Step(Type.TEXT, area?.get(0) ?: 0, area?.get(1) ?: 0, area?.get(2) ?: 0, area?.get(3) ?: 0, ms = ms, text = "Entrar|Login", cycle = 500, goto = goto, fail = fail)

    @Test fun textFoundTapsItsCenterAndForwardsTargets() {
        val f = Fake().also { it.texts.add(200 to 300) }
        val (o, _) = run(macro(ocr()), f)
        assertTrue(o is Outcome.Done)
        assertEquals(listOf("g:200,300>200,300@50"), f.events)
        assertEquals(listOf("Entrar|Login|false|tela"), f.textQueries)   // alvos intactos, sem área = tela toda
    }

    @Test fun textAreaIsForwardedOnlyWhenValid() {
        val f = Fake().also { it.texts.add(1 to 1) }
        run(macro(ocr(area = intArrayOf(10, 20, 300, 400))), f)
        assertEquals("Entrar|Login|false|10,20,300,400", f.textQueries.single())
        val g = Fake().also { it.texts.add(1 to 1) }
        run(macro(Step(Type.TEXT, 50, 50, 50, 50, ms = 1000, text = "x", cycle = 500)), g)   // área vazia (x2 <= x1) vira tela toda
        assertTrue(g.textQueries.single().endsWith("|tela"))
    }

    @Test fun textRetriesUntilFoundThenFollowsGoto() {
        val f = Fake().also { it.texts.addAll(listOf(null, null, 90 to 80)) }
        run(macro(ocr(goto = 3), tap(1), tap(2)), f)
        assertEquals(3, f.textQueries.size)
        assertEquals(listOf("g:90,80>90,80@50", "g:2,100>2,100@10"), gestures(f))   // tocou e saltou para o passo 3
    }

    @Test fun textTimeoutFollowsFail() {
        val f = Fake()
        run(macro(ocr(fail = 2, ms = 1000), tap(1), tap(2)), f)
        assertEquals(3, f.textQueries.size)                                 // 0, 500 e 1000 ms
        assertEquals(listOf("1", "2"), tapXs(f))                            // falhou: passo 2 e segue
    }

    @Test fun textSkipDoesNotTap() {
        val f = Fake().also { it.texts.add(5 to 5) }
        run(macro(ocr().copy(skip = true)), f)
        assertEquals(emptyList<String>(), gestures(f))
    }

    // --- robustez do executor ---
    @Test fun unexpectedRuntimeExceptionBecomesFailedNotACrash() {
        val (o, _) = run(macro(tap(1)), Fake(crashGesture = true))
        assertTrue(o is Outcome.Failed)
        assertTrue((o as Outcome.Failed).why.contains("ArithmeticException"))
    }

    @Test fun interruptFlagStopsAGotoLoopWithoutAnyPortHelp() {
        // Fake.sleep não lança: só a checagem Thread.interrupted() do laço pode parar este GOTO 1→1.
        val loop = Step(Type.GOTO, goto = 1)
        Thread.currentThread().interrupt()
        try {
            val (o, _) = run(macro(loop, loops = 0))
            assertTrue(o is Outcome.Stopped)
        } finally { Thread.interrupted() }                                   // não vaza o flag para outros testes
    }

    @Test fun selfCallingMacroFailsAtDepthLimit() {
        val rec = Macro("m", "n", 1, listOf(Step(Type.CALL, text = "m", ret = true)))
        val (o, _) = run(rec, Fake(macros = mapOf("m" to rec)))
        assertTrue((o as Outcome.Failed).why.contains("aninhadas"))
    }

    @Test fun callToMissingMacroFails() {
        val (o, _) = run(macro(Step(Type.CALL, text = "nao-existe")))
        assertTrue((o as Outcome.Failed).why.contains("não encontrada"))
    }

    @Test fun callReturnsAndContinuesAfterCallee() {
        val callee = Macro("c", "c", 1, listOf(tap(7)))
        val (o, f) = run(macro(tap(1), Step(Type.CALL, text = "c", ret = true), tap(2)), Fake(macros = mapOf("c" to callee)))
        assertTrue(o is Outcome.Done)
        assertEquals(listOf("1", "7", "2"), tapXs(f))
    }

    @Test fun imageSearchWithNoFrameEverTerminatesAtDeadline() {
        // screen() sempre devolve null (capturas recusadas): o prazo tem de encerrar a busca, e o passo seguir o "falhou".
        val f = Fake(frame = null, templates = mapOf("t" to needle))
        val (o, _) = run(macro(image(fail = 2, ms = 500), tap(1), tap(2)), f)
        assertTrue(o is Outcome.Done)
        assertTrue(f.clock >= 500)
        assertEquals(listOf("1", "2"), tapXs(f))
    }

    // --- rastro de diagnóstico ---
    @Test fun traceRecordsEveryExecutedStepAndSearchResults() {
        val f = Fake().also { it.nodes.add(10 to 20) }
        run(macro(tap(1), node(), tap(2)), f)
        assertEquals(3, f.traces.count { it.startsWith("passo ") && it.contains("/3 ") })     // início de cada passo
        assertTrue(f.traces.any { it == "passo 2 elemento: (10, 20)" })                       // resultado da busca
    }

    @Test fun traceReportsAMissWithoutHidingIt() {
        val f = Fake()
        run(macro(node(ms = 100)), f)
        assertTrue(f.traces.any { it == "passo 1 elemento: não achou" })
    }

    @Test fun traceShowsTheDelayActuallyUsedAndDrawsOnlyOnce() {
        // Regressão: o rastro chamava delayOf (que sorteia) e o passo chamava de novo, consumindo dois sorteios.
        val s = tap(1, delay = 9999).copy(rnd = 300, rndMin = 100)
        val f = Fake(rolls = ArrayDeque(listOf(50, 77)))
        run(macro(s), f)
        assertEquals(150L, f.sleeps.first())                       // 100 + primeiro sorteio (50)
        assertTrue(f.traces.any { it.endsWith("atraso=150ms") })   // o rastro mostra o mesmo valor
        assertEquals(1, f.rolls.size)                              // só um sorteio consumido (sobrou o 77)
    }

    // --- funções do manual oficial do base ---
    @Test fun searchWithZeroTimeoutNeverGivesUp() {
        // ms = 0: busca sem limite. 50 tentativas em vão e a 51ª acha: não pode ter desistido antes.
        val f = Fake().also { fk -> repeat(50) { fk.nodes.add(null) }; fk.nodes.add(77 to 88) }
        val (o, _) = run(macro(Step(Type.NODE, ms = 0, text = "x", cycle = 100)), f)
        assertTrue(o is Outcome.Done)
        assertEquals(51, f.queries.size)
        assertEquals(listOf("g:77,88>77,88@50"), gestures(f))
    }

    @Test fun doubleTapRepeatsAtTheSamePointEvenWithJitter() {
        val s = Step(Type.TAP, 100, 200, ms = 10, jitter = 20, dbl = true)
        val f = Fake(rolls = ArrayDeque(listOf(5, 7, 9, 11)))
        run(macro(s), f)
        val g = gestures(f)
        assertEquals(2, g.size)
        assertEquals(g[0], g[1])                                       // o jitter é sorteado uma vez só
        assertTrue(f.sleeps.contains(80L))                             // intervalo entre os dois toques
    }

    @Test fun singleTapIsUnchangedWhenDoubleIsOff() {
        val f = Fake()
        run(macro(Step(Type.TAP, 5, 6, ms = 10)), f)
        assertEquals(1, gestures(f).size)
    }

    @Test fun tapOffsetMovesTheTapAwayFromWhatWasFound() {
        val f = Fake().also { it.nodes.add(100 to 200) }
        run(macro(Step(Type.NODE, ms = 1000, text = "x", cycle = 100, ox = 10, oy = -20)), f)
        assertEquals(listOf("g:110,180>110,180@50"), gestures(f))
    }

    @Test fun tapOffsetIsClampedToTheScreen() {
        val f = Fake(width = 1000, height = 2000).also { it.nodes.add(5 to 1995) }
        run(macro(Step(Type.NODE, ms = 1000, text = "x", cycle = 100, ox = -50, oy = 500)), f)
        assertEquals(listOf("g:0,1999>0,1999@50"), gestures(f))
    }

    @Test fun doubleTapAlsoAppliesToAFoundElement() {
        val f = Fake().also { it.nodes.add(10 to 20) }
        run(macro(Step(Type.NODE, ms = 1000, text = "x", cycle = 100, dbl = true)), f)
        assertEquals(listOf("g:10,20>10,20@50", "g:10,20>10,20@50"), gestures(f))
    }

    @Test fun screenshotKeyReachesThePort() {
        val (o, f) = run(macro(Step(Type.KEY, x1 = KEY_SCREENSHOT)))
        assertTrue(o is Outcome.Done)
        assertEquals(listOf("k:9"), f.events)
    }

    @Test fun statusAlertTriggersVibrationAndSoundBeforeTheMessage() {
        val f = Fake()
        run(macro(Step(Type.STATUS, ms = 10, text = "oi", vib = true, snd = true)), f)
        assertEquals(listOf("vib=true snd=true"), f.alerts)
        assertEquals(listOf("s:oi"), f.events)
    }

    @Test fun statusWithoutAlertFlagsStaysSilent() {
        val f = Fake()
        run(macro(Step(Type.STATUS, ms = 10, text = "oi")), f)
        assertTrue(f.alerts.isEmpty())
    }

    @Test fun ocrReceivesTheMinimumConfidence() {
        val f = Fake().also { it.texts.add(1 to 1) }
        run(macro(Step(Type.TEXT, ms = 1000, text = "x", cycle = 500, minConf = 70)), f)
        assertEquals(listOf(70), f.textConfs)
    }

    // --- toque/deslizar com a imagem gravada (como o item do base) ---
    private fun tapWithImage(x: Int = 65, y: Int = 70, goto: Int = 0, fail: Int = 0, sms: Int = 1000, skip: Boolean = false) =
        Step(Type.TAP, x, y, ms = 10, delay = 300, img = "t", useImg = true, sms = sms, cycle = 100, thr = 95, goto = goto, fail = fail, skip = skip)

    @Test fun imageTapActsRelativeToWhereTheImageWasFound() {
        // O recorte foi gravado centrado em (65,70); a tela agora o mostra 40 px à direita e 10 abaixo: o toque anda junto.
        val f = fakeWith(screenWith(90, 70))                      // centro do recorte em (105,80)
        val (o, _) = run(macro(tapWithImage()), f)
        assertTrue(o is Outcome.Done)
        assertEquals(listOf("g:105,80>105,80@10"), gestures(f))
    }

    @Test fun imageTapSearchesBeforeTheDelayAndSkipsTheDelayOnAMiss() {
        val miss = fakeWith(noise(200, 150, 99))                  // a imagem não está na tela
        run(macro(tapWithImage(sms = 300), tap(1)), miss)
        assertTrue(gestures(miss).none { it.startsWith("g:65,70") })   // sem imagem não toca
        assertFalse(miss.sleeps.contains(300L))                         // e o atraso de 300 ms não é gasto
    }

    @Test fun imageTapMissFollowsFailAndHitFollowsGoto() {
        val m = { fail: Int, goto: Int -> macro(tapWithImage(goto = goto, fail = fail, sms = 300), tap(1), tap(2)) }
        val miss = fakeWith(noise(200, 150, 98))
        run(m(3, 2), miss)
        assertEquals(listOf("2"), tapXs(miss))                    // falhou: pulou para o passo 3
        val hit = fakeWith(screenWith(50, 60))
        run(m(3, 2), hit)
        assertEquals(listOf("6", "1", "2"), tapXs(hit))           // achou: tocou no item, foi para o passo 2 e seguiu
    }

    @Test fun imageTapWithSkipOnlyChecksTheImage() {
        val f = fakeWith(screenWith(50, 60))
        run(macro(tapWithImage(skip = true, goto = 3), tap(1), tap(2)), f)
        assertEquals(listOf("2"), tapXs(f))                       // não tocou no item, mas saltou ao achar
    }

    @Test fun imageTapWithMissingTemplateStillActs() {
        val f = Fake(frame = screenWith(50, 60))                  // sem o modelo "t" no aparelho
        run(macro(tapWithImage()), f)
        assertEquals(1, gestures(f).size)                         // age sem comparar, como o log diz
    }

    @Test fun imageTapWithZeroSearchTimeWaitsForever() {
        val f = Fake(templates = mapOf("t" to needle)).also { fk -> repeat(30) { fk.queue.add(noise(200, 150, 77)) }; fk.queue.add(screenWith(50, 60)) }
        run(macro(tapWithImage(sms = 0)), f)
        assertEquals(31, f.shots)
        assertEquals(1, gestures(f).size)
    }

    @Test fun swipeMovesBothEndsWithTheImage() {
        val s = Step(Type.SWIPE, 65, 70, 300, 400, ms = 10, img = "t", useImg = true, sms = 1000, cycle = 100, thr = 95)
        val f = fakeWith(screenWith(90, 70))                      // deslocamento (+40,+10)
        run(macro(s), f)
        assertEquals(listOf("g:105,80>340,410@10"), gestures(f))
    }
}

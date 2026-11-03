package app.macro17

import kotlin.math.abs

/** Uma trilha de dedo: toque é uma trilha com início = fim. */
data class Stroke(val x1: Int, val y1: Int, val x2: Int, val y2: Int)

/** Tudo que o executor precisa do mundo externo; no app é o serviço de acessibilidade, nos testes é um falso. */
interface Ports {
    val width: Int   // lidos a cada gesto: a tela pode girar entre passos
    val height: Int

    @Throws(InterruptedException::class) fun sleep(ms: Long)

    /** Bloqueia até o gesto terminar; lança IllegalStateException com o motivo se o sistema recusar. */
    @Throws(InterruptedException::class) fun gesture(strokes: List<Stroke>, ms: Int)
    fun key(code: Int)

    /** Gesto livre: um único dedo passando pelos pontos em [ms]. Padrão: só liga o primeiro ao último ponto. */
    @Throws(InterruptedException::class) fun path(points: List<Pair<Int, Int>>, ms: Int) {
        val a = points.first(); val b = points.last()
        gesture(listOf(Stroke(a.first, a.second, b.first, b.second)), ms)
    }
    @Throws(InterruptedException::class) fun status(text: String, ms: Int)

    /** Vibração e/ou som de notificação do alerta de status. Padrão: nada. */
    fun alert(vib: Boolean, snd: Boolean) {}
    fun resolve(id: String): Macro?

    /** Quadro atual da tela em tons de cinza; null se esta captura falhou de forma passiva (ex.: intervalo mínimo). */
    @Throws(InterruptedException::class) fun screen(): Gray?
    fun template(id: String): Gray?
    fun glyphs(): GlyphSet // símbolos que o leitor de números já aprendeu

    /** Casamento por correlação normalizada (método do app de referência). O padrão é o buscador em Kotlin; o Engine usa OpenCV quando carrega. */
    fun matchNcc(hay: Gray, needle: Gray, th: Float, probe: TemplateMatcher.Probe): TemplateMatcher.Hit? = TemplateMatcher.findNcc(hay, needle, th, probe)

    /**
     * Centro do primeiro texto reconhecido por OCR que casa com [query] (alvos separados por vírgula ou "|").
     * [area] = x1,y1,x2,y2 em pixels, ou null para a tela toda. Padrão: sem OCR (testes).
     */
    @Throws(InterruptedException::class) fun findText(query: String, exact: Boolean, area: IntArray?, minConf: Int): Pair<Int, Int>? = null

    /** Centro, em pixels da tela, do primeiro elemento visível que casa com [query]; null se não há (ou a janela está em transição). */
    fun findNode(query: String, exact: Boolean): Pair<Int, Int>?
    fun now(): Long // ms monotônicos, para o prazo da busca
    fun nextInt(bound: Int): Int
    fun log(line: String)

    /** Rastro de cada passo e resultado (diagnóstico). Padrão: nada. */
    fun trace(line: String) {}

    /** Avisa o painel do passo atual: volta, total de voltas (0 = infinito), passo, total de passos e nome do item. Padrão: nada. */
    fun progress(lap: Int, loops: Int, step: Int, total: Int, name: String) {}

    /** Grupo de pesquisa de imagem: enquanto ligado, as buscas dos filhos reaproveitam o mesmo quadro por 200 ms (um screenshot só). Padrão: nada. */
    fun shareFrames(on: Boolean) {}

    /** Anterior (-1) / Próximo (+1) pedidos pelo painel enquanto pausado; lê e zera. Padrão: nada. */
    fun takeJump(): Int = 0
}

sealed class Outcome {
    object Done : Outcome()
    object Stopped : Outcome()
    class Failed(val why: String) : Outcome()
}

/**
 * Mecânica do app de referência (docs/MECANICA_TOUCHMACRO.md): atraso antes da ação, salto, contador,
 * repetições da lista e chamada de outra macro com pilha.
 */
class Runner(private val p: Ports) {
    private class Frame(val macro: Macro) {
        var pc = 0
        var lap = 0
        var resume = false // voltou de uma chamada: avançar antes de executar
        val counters = IntArray(MAX_COUNTERS + 1) // por executor, como no app de referência
        class Grp(val start: Int, val end: Int, var left: Int, val shared: Boolean = false) // left = voltas que faltam (0 = infinito)
        val groups = ArrayList<Grp>()
        val baselines = HashMap<Int, Gray>() // Detecção de Mudança: imagem de referência de cada passo, capturada na primeira execução
    }

    fun run(root: Macro): Outcome = try {
        loop(root)
    } catch (e: InterruptedException) {
        Outcome.Stopped
    } catch (e: java.io.IOException) { // shell (ADB/root) que falha no meio da macro
        Outcome.Failed("shell indisponível: ${e.message ?: e.javaClass.simpleName}")
    } catch (e: IllegalStateException) {
        Outcome.Failed(e.message ?: "erro")
    } catch (e: RuntimeException) {
        // OpenCV, OCR, índices: qualquer falha inesperada vira motivo de parada em vez de derrubar o processo.
        Outcome.Failed("erro inesperado: ${e.javaClass.simpleName}${e.message?.let { ": $it" } ?: ""}")
    }

    private fun loop(root: Macro): Outcome {
        if (root.steps.isEmpty()) return Outcome.Failed("macro vazia")
        val stack = ArrayList<Frame>()
        stack.add(Frame(root))
        while (stack.isNotEmpty()) {
            if (Thread.interrupted()) throw InterruptedException()
            val f = stack[stack.lastIndex]
            if (f.resume) { f.resume = false; advance(stack, f, f.pc + 1); continue }
            val dj = p.takeJump()
            // O pedido nasce durante o passo pausado, que já avançou o ponteiro: Próximo = seguir (0), Anterior = voltar dois (-2).
            if (dj != 0) f.pc = (f.pc + dj - 1).coerceIn(0, f.macro.steps.size - 1)
            val s = f.macro.steps[f.pc]
            p.progress(f.lap + 1, f.macro.loops, f.pc + 1, f.macro.steps.size, s.name)
            if (s.off) { advance(stack, f, f.pc + 1); continue }
            val wait = delayOf(s) // uma vez só: delayOf sorteia quando o atraso é aleatório, e o rastro tem de mostrar o valor usado
            p.trace("passo ${f.pc + 1}/${f.macro.steps.size} ${s.type}" + (if (s.skip) " (pular)" else "") + " atraso=${wait}ms")

            // Como no base, um item com imagem busca PRIMEIRO e só então espera o atraso e age; sem imagem, o atraso vem antes.
            val wantsImg = s.type in Step.GESTURES && s.useImg && s.img.isNotEmpty()
            val needle = if (wantsImg) p.template(s.img) else null
            if (wantsImg && needle == null) p.log("Passo ${f.pc + 1}: imagem do item não encontrada no aparelho; age sem comparar")
            val imgHit: TemplateMatcher.Hit? = if (needle != null) search(specOf(s), needle, f.pc + 1) else null
            val imgMiss = needle != null && imgHit == null
            if (needle != null) p.trace("passo ${f.pc + 1} imagem do item: " + (if (imgHit != null) "achou em ${imgHit.x},${imgHit.y} semelhança ${(imgHit.score * 100).toInt()}%" else "não achou"))

            // Mínimo de 1 ms por passo: macro infinita sem nenhuma espera não pode virar laço quente.
            // IMAGE/NODE/TEXT/NUMBER: como no base, o atraso vem depois de achar (abaixo), não antes da busca.
            val searchFirst = s.type in Step.SEARCHES
            p.sleep(if (imgMiss || searchFirst) 1L else maxOf(1, wait).toLong())

            var jump = 0
            when (s.type) {
                Type.TAP, Type.SWIPE, Type.PINCH_IN, Type.PINCH_OUT -> if (imgMiss) jump = s.fail else {
                    if (!s.skip) {
                        // Relativo à imagem: o gesto anda junto com o deslocamento do achado em relação ao ponto gravado (mais o desvio).
                        val dx = if (imgHit != null) imgHit.x - s.x1 + s.ox else 0
                        val dy = if (imgHit != null) imgHit.y - s.y1 + s.oy else 0
                        val st = strokes(s, dx, dy) // um só sorteio de jitter: o segundo toque do clique duplo cai no mesmo ponto
                        p.gesture(st, s.ms)
                        if (s.dbl && s.type == Type.TAP) { p.sleep(DOUBLE_GAP_MS); p.gesture(st, s.ms) }
                    }
                    if (imgHit != null) jump = s.goto
                }
                Type.KEY -> if (!s.skip) { p.key(s.x1); if (s.ms > 0) p.sleep(s.ms.toLong()) } // espera depois da tecla, como no base
                Type.WAIT -> p.sleep(s.ms.toLong())
                Type.STATUS -> if (!s.skip) { if (s.vib || s.snd) p.alert(s.vib, s.snd); p.status(s.text, s.ms) }
                Type.GOTO -> jump = s.goto
                Type.CHANGE -> {
                    // Primeira execução: guarda a área como referência e segue; depois espera a área mudar (semelhança abaixo da exigida).
                    val area = { fr: Gray -> if (s.x2 > s.x1) fr.crop(s.x1, s.y1, s.x2 - s.x1, s.y2 - s.y1) else fr }
                    val base = f.baselines[f.pc]
                    if (base == null) {
                        val fr = p.screen()
                        if (fr != null) f.baselines[f.pc] = area(fr)
                        p.trace("passo ${f.pc + 1} mudança: referência capturada")
                        jump = s.goto
                    } else {
                        val hit = poll(s) { p.screen()?.let { fr -> area(fr).takeIf { c -> sameSize(c, base) && similarity(c, base) < s.thr / 100f } } }
                        p.trace("passo ${f.pc + 1} mudança: ${if (hit != null) "mudou" else "sem mudança no prazo"}")
                        if (hit != null) { f.baselines[f.pc] = hit; p.sleep(maxOf(1, wait).toLong()) }
                        jump = if (hit != null) s.goto else s.fail
                    }
                }
                Type.GESTURE -> if (!s.skip) p.path(gesturePoints(s.text), s.ms)
                Type.GROUP -> if (s.grp > 0 && f.groups.none { it.start == f.pc }) { f.groups.add(Frame.Grp(f.pc, minOf(f.pc + s.grp, f.macro.steps.size - 1), s.rep, s.mono)); if (s.mono) p.shareFrames(true) }
                Type.STOP -> return Outcome.Done // "Parar Macro" do base: encerra a reprodução aqui
                Type.RESET -> f.counters[s.counter] = 0
                Type.COUNTER -> jump = counterJump(f, s)
                Type.IMAGE -> {
                    val needle = p.template(s.text) ?: return Outcome.Failed("imagem do passo ${f.pc + 1} não encontrada no aparelho")
                    val hit = search(specOf(s), needle, f.pc + 1)
                    p.trace("passo ${f.pc + 1} imagem: " + (hit?.let { "achou em ${it.x},${it.y} semelhança ${(it.score * 100).toInt()}%" } ?: "não achou"))
                    if (hit != null) p.sleep(maxOf(1, wait).toLong())
                    if (hit != null && s.tap && !s.skip) tapAt(s, hit.x, hit.y)
                    jump = if (hit != null) s.goto else s.fail
                }
                Type.NUMBER -> {
                    val set = p.glyphs()
                    if (set.count() == 0) return Outcome.Failed("o leitor de números ainda não aprendeu nenhum dígito (Opções › Leitor de números)")
                    val read = poll(s) {
                        val frame = p.screen() ?: return@poll null
                        val ox = s.x1.coerceIn(0, frame.w)
                        val oy = s.y1.coerceIn(0, frame.h)
                        Digits.read(frame.crop(ox, oy, s.x2 - s.x1, s.y2 - s.y1), set)?.takeIf { holds(s, it) }
                    }
                    p.trace("passo ${f.pc + 1} número: ${read ?: "condição não satisfeita no prazo"}")
                    if (read != null) p.sleep(maxOf(1, wait).toLong())
                    jump = if (read != null) s.goto else s.fail
                }
                Type.NODE -> {
                    val hit = poll(s) { p.findNode(s.text, s.exact) }
                    p.trace("passo ${f.pc + 1} elemento: ${hit ?: "não achou"}")
                    if (hit != null) p.sleep(maxOf(1, wait).toLong())
                    if (hit != null && s.tap && !s.skip) tapAt(s, hit.first, hit.second)
                    jump = if (hit != null) s.goto else s.fail
                }
                Type.TEXT -> {
                    val area = if (s.x2 > s.x1 && s.y2 > s.y1) intArrayOf(s.x1, s.y1, s.x2, s.y2) else null
                    val hit = poll(s) { p.findText(s.text, s.exact, area, s.minConf) }
                    p.trace("passo ${f.pc + 1} texto: ${hit ?: "não achou"}")
                    if (hit != null) p.sleep(maxOf(1, wait).toLong())
                    if (hit != null && s.tap && !s.skip) tapAt(s, hit.first, hit.second)
                    jump = if (hit != null) s.goto else s.fail
                }
                Type.CALL -> if (!s.skip && s.text.isNotBlank()) {
                    val m = p.resolve(s.text) ?: return Outcome.Failed("macro chamada não encontrada")
                    m.validate()?.let { return Outcome.Failed("macro “${m.name}” inválida: $it") }
                    if (m.steps.isNotEmpty()) {
                        if (s.ret) {
                            if (stack.size >= MAX_DEPTH) return Outcome.Failed("chamadas aninhadas demais (máximo $MAX_DEPTH)") // desvio consciente do base (que descarta o mais antigo): recursão infinita travaria a macro
                            f.resume = true
                        } else stack.clear()
                        stack.add(Frame(m))
                        continue
                    }
                }
            }
            if (jump > 0) p.log("Passo ${f.pc + 1} → Passo $jump")
            advance(stack, f, if (jump > 0) jump - 1 else f.pc + 1, natural = jump <= 0)
        }
        return Outcome.Done
    }

    /**
     * Tenta achar [needle] na tela a cada [Step.cycle] ms até [Step.ms]. A primeira tentativa é imediata.
     * Devolve o centro do achado em coordenadas da tela inteira (a área de busca é recortada antes).
     */
    /** Parâmetros de uma busca de imagem: vêm do passo IMAGE (x1..y2, ms) ou do bloco de imagem de um toque/deslize (ax1..ay2, sms). */
    private class Spec(val x1: Int, val y1: Int, val x2: Int, val y2: Int, val ms: Int, val cycle: Int, val thr: Int, val method: Int, val mono: Boolean, val confirm: Int, val scales: Boolean)

    private fun specOf(s: Step) =
        if (s.type == Type.IMAGE) Spec(s.x1, s.y1, s.x2, s.y2, s.ms, s.cycle, s.thr, s.method, s.mono, s.confirm, s.scales)
        else if (s.fx) { val pad = 160; Spec((s.x1 - pad).coerceAtLeast(0), (s.y1 - pad).coerceAtLeast(0), s.x1 + pad, s.y1 + pad, s.sms, s.cycle, s.thr, s.method, s.mono, s.confirm, s.scales) } // posição fixa: só em volta do ponto gravado
        else Spec(s.ax1, s.ay1, s.ax2, s.ay2, s.sms, s.cycle, s.thr, s.method, s.mono, s.confirm, s.scales)

    /**
     * Procura [needle] até o prazo. Com [Spec.confirm] > 1, só vale o achado que se repete em quadros seguidos no mesmo lugar
     * (evita agir sobre um quadro de transição ou de animação). Ao falhar, diz o quanto faltou.
     */
    private fun search(sp: Spec, needle: Gray, number: Int): TemplateMatcher.Hit? {
        val probe = TemplateMatcher.Probe()
        var streak = 0
        var last: TemplateMatcher.Hit? = null
        // Método 0 = o do app de referência (correlação, limiar max(A,20) com teto 99, sucesso estritamente maior); 1 = diferença de pixels.
        val th = if (sp.method == 1) sp.thr / 100f else TemplateMatcher.baseThreshold(sp.thr)
        val hit = poll(sp.ms, sp.cycle) {
            val frame = p.screen() ?: return@poll null
            val whole = sp.x1 == 0 && sp.y1 == 0 && sp.x2 == 0 && sp.y2 == 0
            val ox = if (whole) 0 else sp.x1.coerceIn(0, frame.w)
            val oy = if (whole) 0 else sp.y1.coerceIn(0, frame.h)
            val hay = if (whole) frame else frame.crop(ox, oy, sp.x2 - sp.x1, sp.y2 - sp.y1)
            // Dentro da área, compara os pixels com o modelo e confere com a % configurada.
            val find = { n: Gray -> if (sp.method == 1) TemplateMatcher.findSqdiff(hay, n, th, sp.mono, probe) else p.matchNcc(hay, n, th, probe) }
            val h = (if (sp.scales) TemplateMatcher.scaled(needle, find) else find(needle))?.let { it.copy(x = it.x + ox, y = it.y + oy) }
            val prev = last
            if (h == null) { streak = 0; last = null; return@poll null }
            streak = if (prev != null && abs(prev.x - h.x) <= STILL_PX && abs(prev.y - h.y) <= STILL_PX) streak + 1 else 1
            last = h
            if (streak >= sp.confirm) h else null
        }
        if (hit == null) p.log(
            "Passo $number: imagem não achada. " +
                (if (probe.best <= 0f) "Nada parecido na área" else "Melhor semelhança ${(probe.best * 100).toInt()}%") +
                " (exigido ${sp.thr}%" + (if (sp.confirm > 1) ", ${sp.confirm} quadros seguidos" else "") + ")"
        )
        return hit
    }

    private fun sameSize(a: Gray, b: Gray) = a.w == b.w && a.h == b.h && a.d.isNotEmpty()

    /** 1 = idênticas, 0 = totalmente diferentes (diferença média absoluta de luminosidade). */
    private fun similarity(a: Gray, b: Gray): Float {
        var sum = 0L
        for (k in a.d.indices) sum += kotlin.math.abs(a.d[k] - b.d[k])
        return 1f - sum.toFloat() / (a.d.size * 255f)
    }

    /** A leitura satisfaz a condição do passo (≥, ≤, =, entre)? */
    private fun holds(s: Step, v: Long) = when (s.cmp) {
        0 -> v >= s.lo
        1 -> v <= s.lo
        2 -> v == s.lo.toLong()
        else -> v in s.lo..s.hi.toLong()
    }

    /** Repete [attempt] a cada [Step.cycle] ms até dar certo ou passar [Step.ms]. A primeira tentativa é imediata. */
    private fun <T : Any> poll(s: Step, attempt: () -> T?): T? = poll(s.ms, s.cycle, attempt)

    private fun <T : Any> poll(ms: Int, cycle: Int, attempt: () -> T?): T? {
        val start = p.now()
        while (true) {
            attempt()?.let { return it }
            if (ms > 0 && p.now() - start >= ms) return null // ms = 0: sem limite (só a parada interrompe)
            p.sleep(cycle.toLong())
        }
    }

    /** Toca em (x,y) mais o deslocamento do passo, sem sair da tela; clique duplo repete no mesmo ponto. */
    private fun tapAt(s: Step, x: Int, y: Int) {
        val tx = (x + s.ox).coerceIn(0, maxOf(0, p.width - 1)); val ty = (y + s.oy).coerceIn(0, maxOf(0, p.height - 1))
        val g = listOf(Stroke(tx, ty, tx, ty))
        p.gesture(g, TAP_MS)
        if (s.dbl) { p.sleep(DOUBLE_GAP_MS); p.gesture(g, TAP_MS) }
    }

    /** Fim da lista conta uma volta; atingido o limite de repetições, a macro termina e o chamador (se houver) retoma. */
    private fun advance(stack: ArrayList<Frame>, f: Frame, target: Int, natural: Boolean = true) {
        var t = target
        // Grupos: ao passar do último item, repete (voltas restantes) ou sai; um salto para fora do grupo o abandona.
        while (f.groups.isNotEmpty()) {
            val g = f.groups.last()
            if (t in g.start + 1..g.end) break
            if (natural && t == g.end + 1 && (g.left == 0 || g.left > 1)) { if (g.left > 1) g.left--; t = g.start + 1; break }
            if (f.groups.last().shared) p.shareFrames(false)
            f.groups.removeAt(f.groups.lastIndex)
        }
        advanceTo(stack, f, t)
    }

    private fun advanceTo(stack: ArrayList<Frame>, f: Frame, target: Int) {
        if (target < f.macro.steps.size) { f.pc = target; return }
        f.lap++
        if (f.macro.loops == 0 || f.lap < f.macro.loops) f.pc = 0 else stack.removeAt(stack.lastIndex)
    }

    // Faixa aleatória substitui o atraso fixo (a tela do app de referência diz que o atraso acima é ignorado).
    private fun delayOf(s: Step) = if (s.rnd > 0) s.rndMin + p.nextInt(s.rnd - s.rndMin + 1) else s.delay

    /** Soma 1 ao contador, escolhe o caso de maior limiar já alcançado e zera ao alcançar o último limiar (ciclo). */
    private fun counterJump(f: Frame, s: Step): Int {
        val n = ++f.counters[s.counter]
        val hit = s.cases.filter { n >= it.n }.maxByOrNull { it.n }
        val top = s.cases.maxOfOrNull { it.n }
        if (top != null && n >= top) f.counters[s.counter] = 0
        return hit?.to ?: s.goto
    }

    private fun strokes(s: Step, shiftX: Int = 0, shiftY: Int = 0): List<Stroke> {
        val w = p.width
        val h = p.height
        // Um único (dx,dy) para o gesto inteiro: o gesto se move, o comprimento não muda.
        val dx = if (s.jitter > 0) p.nextInt(2 * s.jitter + 1) - s.jitter else 0
        val dy = if (s.jitter > 0) p.nextInt(2 * s.jitter + 1) - s.jitter else 0
        val x1 = (s.x1 + shiftX + dx).coerceIn(0, w - 1)
        val y1 = (s.y1 + shiftY + dy).coerceIn(0, h - 1)
        val x2 = (s.x2 + shiftX + dx).coerceIn(0, w - 1)
        val y2 = (s.y2 + shiftY + dy).coerceIn(0, h - 1)
        val mx = (x1 + x2) / 2
        val my = (y1 + y2) / 2
        return when (s.type) {
            Type.TAP -> listOf(Stroke(x1, y1, x1, y1))
            Type.SWIPE -> listOf(Stroke(x1, y1, x2, y2))
            Type.PINCH_IN -> listOf(Stroke(x1, y1, mx, my), Stroke(x2, y2, mx, my))
            else -> listOf(Stroke(mx, my, x1, y1), Stroke(mx, my, x2, y2))
        }
    }

    private companion object { const val MAX_DEPTH = 8; const val TAP_MS = 50; const val DOUBLE_GAP_MS = 80L; const val STILL_PX = 3 }
}

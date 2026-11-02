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
    @Throws(InterruptedException::class) fun status(text: String, ms: Int)
    fun resolve(id: String): Macro?

    /** Quadro atual da tela em tons de cinza; null se esta captura falhou de forma passiva (ex.: intervalo mínimo). */
    @Throws(InterruptedException::class) fun screen(): Gray?
    fun template(id: String): Gray?
    fun glyphs(): GlyphSet // símbolos que o leitor de números já aprendeu

    /** Centro, em pixels da tela, do primeiro elemento visível que casa com [query]; null se não há (ou a janela está em transição). */
    fun findNode(query: String, exact: Boolean): Pair<Int, Int>?
    fun now(): Long // ms monotônicos, para o prazo da busca
    fun nextInt(bound: Int): Int
    fun log(line: String)
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
    }

    fun run(root: Macro): Outcome = try {
        loop(root)
    } catch (e: InterruptedException) {
        Outcome.Stopped
    } catch (e: IllegalStateException) {
        Outcome.Failed(e.message ?: "erro")
    }

    private fun loop(root: Macro): Outcome {
        if (root.steps.isEmpty()) return Outcome.Failed("macro vazia")
        val stack = ArrayList<Frame>()
        stack.add(Frame(root))
        while (stack.isNotEmpty()) {
            if (Thread.interrupted()) throw InterruptedException()
            val f = stack[stack.lastIndex]
            if (f.resume) { f.resume = false; advance(stack, f, f.pc + 1); continue }
            val s = f.macro.steps[f.pc]

            // Mínimo de 1 ms por passo: macro infinita sem nenhuma espera não pode virar laço quente.
            p.sleep(maxOf(1, delayOf(s)).toLong())

            var jump = 0
            when (s.type) {
                Type.TAP, Type.SWIPE, Type.PINCH_IN, Type.PINCH_OUT -> if (!s.skip) p.gesture(strokes(s), s.ms)
                Type.KEY -> if (!s.skip) p.key(s.x1)
                Type.WAIT -> p.sleep(s.ms.toLong())
                Type.STATUS -> if (!s.skip) p.status(s.text, s.ms)
                Type.GOTO -> jump = s.goto
                Type.RESET -> f.counters[s.counter] = 0
                Type.COUNTER -> jump = counterJump(f, s)
                Type.IMAGE -> {
                    val needle = p.template(s.text) ?: return Outcome.Failed("imagem do passo ${f.pc + 1} não encontrada no aparelho")
                    val hit = search(s, needle, f.pc + 1)
                    if (hit != null && s.tap && !s.skip) p.gesture(listOf(Stroke(hit.x, hit.y, hit.x, hit.y)), TAP_MS)
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
                    jump = if (read != null) s.goto else s.fail
                }
                Type.NODE -> {
                    val hit = poll(s) { p.findNode(s.text, s.exact) }
                    if (hit != null && s.tap && !s.skip) p.gesture(listOf(Stroke(hit.first, hit.second, hit.first, hit.second)), TAP_MS)
                    jump = if (hit != null) s.goto else s.fail
                }
                Type.CALL -> if (!s.skip) {
                    val m = p.resolve(s.text) ?: return Outcome.Failed("macro chamada não encontrada")
                    m.validate()?.let { return Outcome.Failed("macro “${m.name}” inválida: $it") }
                    if (m.steps.isNotEmpty()) {
                        if (s.ret) {
                            if (stack.size >= MAX_DEPTH) return Outcome.Failed("chamadas aninhadas demais (máximo $MAX_DEPTH)")
                            f.resume = true
                        } else stack.removeAt(stack.lastIndex)
                        stack.add(Frame(m))
                        continue
                    }
                }
            }
            if (jump > 0) p.log("Passo ${f.pc + 1} → Passo $jump")
            advance(stack, f, if (jump > 0) jump - 1 else f.pc + 1)
        }
        return Outcome.Done
    }

    /**
     * Tenta achar [needle] na tela a cada [Step.cycle] ms até [Step.ms]. A primeira tentativa é imediata.
     * Devolve o centro do achado em coordenadas da tela inteira (a área de busca é recortada antes).
     */
    /**
     * Procura [needle] até o prazo. Com [Step.confirm] > 1, só vale o achado que se repete em quadros seguidos no mesmo lugar
     * (evita agir sobre um quadro de transição ou de animação). Ao falhar, diz o quanto faltou.
     */
    private fun search(s: Step, needle: Gray, number: Int): TemplateMatcher.Hit? {
        val probe = TemplateMatcher.Probe()
        var streak = 0
        var last: TemplateMatcher.Hit? = null
        // Método 0 = o do app de referência (correlação, limiar max(A,20) com teto 99, sucesso estritamente maior); 1 = diferença de pixels.
        val th = if (s.method == 1) s.thr / 100f else TemplateMatcher.baseThreshold(s.thr)
        val hit = poll(s) {
            val frame = p.screen() ?: return@poll null
            val whole = s.x1 == 0 && s.y1 == 0 && s.x2 == 0 && s.y2 == 0
            val ox = if (whole) 0 else s.x1.coerceIn(0, frame.w)
            val oy = if (whole) 0 else s.y1.coerceIn(0, frame.h)
            val hay = if (whole) frame else frame.crop(ox, oy, s.x2 - s.x1, s.y2 - s.y1)
            // Dentro da área, compara os pixels com o modelo e confere com a % configurada.
            val find = { n: Gray -> if (s.method == 1) TemplateMatcher.findSqdiff(hay, n, th, s.mono, probe) else TemplateMatcher.findNcc(hay, n, th, probe) }
            val h = (if (s.scales) TemplateMatcher.scaled(needle, find) else find(needle))?.let { it.copy(x = it.x + ox, y = it.y + oy) }
            val prev = last
            if (h == null) { streak = 0; last = null; return@poll null }
            streak = if (prev != null && abs(prev.x - h.x) <= STILL_PX && abs(prev.y - h.y) <= STILL_PX) streak + 1 else 1
            last = h
            if (streak >= s.confirm) h else null
        }
        if (hit == null) p.log(
            "Passo $number: imagem não achada. " +
                (if (probe.best <= 0f) "Nada parecido na área" else "Melhor semelhança ${(probe.best * 100).toInt()}%") +
                " (exigido ${s.thr}%" + (if (s.confirm > 1) ", ${s.confirm} quadros seguidos" else "") + ")"
        )
        return hit
    }

    /** A leitura satisfaz a condição do passo (≥, ≤, =, entre)? */
    private fun holds(s: Step, v: Long) = when (s.cmp) {
        0 -> v >= s.lo
        1 -> v <= s.lo
        2 -> v == s.lo.toLong()
        else -> v in s.lo..s.hi.toLong()
    }

    /** Repete [attempt] a cada [Step.cycle] ms até dar certo ou passar [Step.ms]. A primeira tentativa é imediata. */
    private fun <T : Any> poll(s: Step, attempt: () -> T?): T? {
        val start = p.now()
        while (true) {
            attempt()?.let { return it }
            if (p.now() - start >= s.ms) return null
            p.sleep(s.cycle.toLong())
        }
    }

    /** Fim da lista conta uma volta; atingido o limite de repetições, a macro termina e o chamador (se houver) retoma. */
    private fun advance(stack: ArrayList<Frame>, f: Frame, target: Int) {
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

    private fun strokes(s: Step): List<Stroke> {
        val w = p.width
        val h = p.height
        // Um único (dx,dy) para o gesto inteiro: o gesto se move, o comprimento não muda.
        val dx = if (s.jitter > 0) p.nextInt(2 * s.jitter + 1) - s.jitter else 0
        val dy = if (s.jitter > 0) p.nextInt(2 * s.jitter + 1) - s.jitter else 0
        val x1 = (s.x1 + dx).coerceIn(0, w - 1)
        val y1 = (s.y1 + dy).coerceIn(0, h - 1)
        val x2 = (s.x2 + dx).coerceIn(0, w - 1)
        val y2 = (s.y2 + dy).coerceIn(0, h - 1)
        val mx = (x1 + x2) / 2
        val my = (y1 + y2) / 2
        return when (s.type) {
            Type.TAP -> listOf(Stroke(x1, y1, x1, y1))
            Type.SWIPE -> listOf(Stroke(x1, y1, x2, y2))
            Type.PINCH_IN -> listOf(Stroke(x1, y1, mx, my), Stroke(x2, y2, mx, my))
            else -> listOf(Stroke(mx, my, x1, y1), Stroke(mx, my, x2, y2))
        }
    }

    private companion object { const val MAX_DEPTH = 8; const val TAP_MS = 50; const val STILL_PX = 3 }
}

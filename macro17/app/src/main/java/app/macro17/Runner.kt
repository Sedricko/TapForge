package app.macro17

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

    private companion object { const val MAX_DEPTH = 8 }
}

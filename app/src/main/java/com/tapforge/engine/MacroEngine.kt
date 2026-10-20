package com.tapforge.engine

import com.tapforge.model.*
import kotlinx.coroutines.*
import kotlin.random.Random

private class StopSignal(val reason: String) : CancellationException(reason)

class MacroEngine(
    private val gestures: GestureDispatcher,
    private val vision: Vision,
    private val clock: () -> Long = System::currentTimeMillis,
    private val rng: Random = Random.Default,
) {
    private class Ctx(val vars: MutableMap<String, String>, val startedAt: Long, val macro: Macro)

    /** Variáveis ao final da última execução (útil em testes e depuração). */
    var lastVars: Map<String, String> = emptyMap()
        private set

    /** Executa o macro em loop até o limite configurado ou cancelamento. */
    suspend fun run(macro: Macro, onEvent: (EngineEvent) -> Unit = {}) {
        val ctx = Ctx(mutableMapOf(), clock(), macro)
        onEvent(EngineEvent.Started(macro.id))
        var n = 0
        try {
            while (true) {
                currentCoroutineContext().ensureActive()
                if (macro.maxIterations > 0 && n >= macro.maxIterations) { onEvent(EngineEvent.Finished("iterações")); break }
                if (timeUp(ctx)) { onEvent(EngineEvent.Finished("tempo")); break }
                exec(macro.actions, ctx)
                n++
                onEvent(EngineEvent.Iteration(n))
                if (macro.actions.isEmpty()) break
            }
        } catch (s: StopSignal) {
            onEvent(EngineEvent.Finished(s.reason))
        } catch (c: CancellationException) {
            onEvent(EngineEvent.Finished("cancelado")); throw c
        } catch (t: Throwable) {
            onEvent(EngineEvent.Failed(t.message ?: t.javaClass.simpleName))
        } finally { lastVars = ctx.vars.toMap() }
    }

    private fun timeUp(c: Ctx) = c.macro.maxRuntimeMs > 0 && clock() - c.startedAt >= c.macro.maxRuntimeMs

    private suspend fun exec(list: List<Action>, c: Ctx) {
        for (a in list) {
            currentCoroutineContext().ensureActive()
            if (timeUp(c)) throw StopSignal("tempo")
            when (a) {
                is Action.Tap -> {
                    val (dx, dy) = jit(a.jitter.posPx)
                    gestures.tap(a.x + dx, a.y + dy, a.holdMs)
                    pause(a.jitter.timeMs)
                }
                is Action.Swipe -> {
                    val (dx, dy) = jit(a.jitter.posPx)
                    gestures.swipe(a.x1 + dx, a.y1 + dy, a.x2 + dx, a.y2 + dy, a.durationMs)
                    pause(a.jitter.timeMs)
                }
                is Action.Wait -> delay((a.ms + jitter(a.jitterMs)).coerceAtLeast(0))
                is Action.Loop -> loop(a, c)
                is Action.If -> exec(if (eval(a.cond, c)) a.then else a.otherwise, c)
                is Action.FindImage -> findImage(a, c)
                is Action.ReadText -> c.vars[a.into] = vision.readText(a.region)
                is Action.SetVar -> c.vars[a.name] = a.value
                is Action.TapFound -> {
                    val x = c.vars["${a.from}.x"]?.toIntOrNull(); val y = c.vars["${a.from}.y"]?.toIntOrNull()
                    if (x != null && y != null) {
                        val (dx, dy) = jit(a.jitter.posPx)
                        gestures.tap(x + dx, y + dy, a.holdMs)
                    }
                }
                is Action.Global -> gestures.global(a.kind)
                Action.Stop -> throw StopSignal("stop")
            }
        }
    }

    private suspend fun loop(a: Action.Loop, c: Ctx) {
        if (a.count == 0 && a.untilMs == 0L) return // sem limite explícito: evita laço infinito oculto
        val start = clock(); var i = 0
        while (true) {
            if (a.count > 0 && i >= a.count) break
            if (a.untilMs > 0 && clock() - start >= a.untilMs) break
            c.vars["i"] = i.toString()
            exec(a.body, c); i++
            yield()
        }
    }

    private suspend fun findImage(a: Action.FindImage, c: Ctx) {
        val start = clock()
        var m: Match?
        while (true) {
            m = vision.findImage(a.templateId, a.threshold, a.region)
            if (m != null || a.timeoutMs <= 0 || clock() - start >= a.timeoutMs) break
            delay(100)
        }
        if (m != null) {
            c.vars["${a.into}.x"] = m.x.toString(); c.vars["${a.into}.y"] = m.y.toString()
            exec(a.onFound, c)
        } else exec(a.onNotFound, c)
    }

    private suspend fun eval(cond: Condition, c: Ctx): Boolean = when (cond) {
        Condition.Always -> true
        is Condition.Random -> rng.nextDouble() < cond.probability
        is Condition.Var -> compare(c.vars[cond.name] ?: "", cond.op, cond.value)
        is Condition.TextContains -> vision.readText(cond.region).contains(cond.text, cond.ignoreCase)
        is Condition.ImageVisible -> vision.findImage(cond.templateId, cond.threshold, cond.region) != null
        is Condition.Not -> !eval(cond.inner, c)
    }

    private fun compare(l: String, op: Op, r: String): Boolean {
        if (op == Op.CONTAINS) return l.contains(r)
        val a = l.toDoubleOrNull(); val b = r.toDoubleOrNull()
        val cmp = if (a != null && b != null) a.compareTo(b) else l.compareTo(r)
        return when (op) {
            Op.EQ -> cmp == 0; Op.NE -> cmp != 0; Op.LT -> cmp < 0
            Op.LE -> cmp <= 0; Op.GT -> cmp > 0; Op.GE -> cmp >= 0; Op.CONTAINS -> false
        }
    }

    private fun jit(px: Int): Pair<Int, Int> = if (px <= 0) 0 to 0 else rng.nextInt(-px, px + 1) to rng.nextInt(-px, px + 1)
    private fun jitter(ms: Long): Long = if (ms <= 0) 0 else rng.nextLong(-ms, ms + 1)
    private suspend fun pause(jitterMs: Long) { if (jitterMs > 0) delay(rng.nextLong(0, jitterMs + 1)) }
}

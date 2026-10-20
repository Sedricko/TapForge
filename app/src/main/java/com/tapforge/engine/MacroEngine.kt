package com.tapforge.engine

import com.tapforge.model.*
import kotlinx.coroutines.*
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

private class StopSignal(val reason: String) : CancellationException(reason)

class MacroEngine(
    private val gestures: GestureDispatcher,
    private val vision: Vision,
    private val device: Device = NoDevice,
    /** Resolve sub-macros de [Action.Call]. */
    private val macros: (String) -> Macro? = { null },
    private val clock: () -> Long = System::currentTimeMillis,
    private val rng: Random = Random.Default,
    /** Chamado antes de cada ação: pausa, passo a passo, bloqueio por app. */
    private val beforeAction: suspend (Action) -> Unit = {},
) {
    private class Ctx(val startedAt: Long, val macro: Macro, val emit: (EngineEvent) -> Unit) {
        val vars = mutableMapOf<String, String>()
        val snaps = mutableMapOf<String, IntArray>()
        var fx = 1.0; var fy = 1.0
        var depth = 0
        var warned = false
    }

    /** Variáveis ao final da última execução (útil em testes e depuração). */
    var lastVars: Map<String, String> = emptyMap()
        private set

    /** Executa o macro em loop até o limite configurado ou cancelamento. */
    suspend fun run(macro: Macro, onEvent: (EngineEvent) -> Unit = {}) {
        val ctx = Ctx(clock(), macro, onEvent)
        scaleFor(macro, ctx)
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

    // Coordenadas gravadas em outra tela são ajustadas proporcionalmente.
    private fun scaleFor(m: Macro, c: Ctx) {
        val (w, h) = device.screenSize()
        c.fx = if (m.screenW > 0 && w > 0) w.toDouble() / m.screenW else 1.0
        c.fy = if (m.screenH > 0 && h > 0) h.toDouble() / m.screenH else 1.0
    }
    private fun Ctx.x(v: Int) = (v * fx).roundToInt()
    private fun Ctx.y(v: Int) = (v * fy).roundToInt()
    private fun Ctx.reg(r: Region?) = r?.let { Region(x(it.x), y(it.y), x(it.w), y(it.h)) }

    private fun interp(s: String, c: Ctx) = VAR.replace(s) { c.vars[it.groupValues[1]] ?: "" }

    private fun warnNoCapture(c: Ctx) {
        if (!vision.available && !c.warned) {
            c.warned = true
            c.emit(EngineEvent.Log("Captura de tela desligada: imagem, texto e cor não funcionam"))
        }
    }

    private suspend fun touch(c: Ctx, strokes: List<Stroke>) {
        gestures.gesture(strokes)
        strokes.firstOrNull()?.points?.firstOrNull()?.let { c.emit(EngineEvent.Touched(it.x, it.y)) }
    }

    private suspend fun exec(list: List<Action>, c: Ctx) {
        for (a in list) {
            currentCoroutineContext().ensureActive()
            if (timeUp(c)) throw StopSignal("tempo")
            beforeAction(a)
            c.emit(EngineEvent.Step(a))
            when (a) {
                is Action.Tap -> {
                    val area = c.reg(a.area)
                    var px = c.x(a.x); var py = c.y(a.y)
                    if (area != null) { px = area.x + rng.nextInt(area.w.coerceAtLeast(1)); py = area.y + rng.nextInt(area.h.coerceAtLeast(1)) }
                    val (dx, dy) = jit(a.jitter.posPx)
                    touch(c, listOf(Stroke(listOf(Point(px + dx, py + dy)), 0, a.holdMs.coerceAtLeast(1))))
                    pause(a.jitter.timeMs)
                }
                is Action.Swipe -> {
                    val (dx, dy) = jit(a.jitter.posPx)
                    touch(c, listOf(Stroke(listOf(
                        Point(c.x(a.x1) + dx, c.y(a.y1) + dy), Point(c.x(a.x2) + dx, c.y(a.y2) + dy),
                    ), 0, a.durationMs.coerceAtLeast(1))))
                    pause(a.jitter.timeMs)
                }
                is Action.Gesture -> {
                    val (dx, dy) = jit(a.jitter.posPx)
                    touch(c, a.strokes.take(10).map { s ->
                        s.copy(points = s.points.map { Point(c.x(it.x) + dx, c.y(it.y) + dy) }, durationMs = s.durationMs.coerceAtLeast(1))
                    })
                    pause(a.jitter.timeMs)
                }
                is Action.Wait -> delay((a.ms + jitter(a.jitterMs)).coerceAtLeast(0))
                is Action.Loop -> loop(a, c)
                is Action.If -> exec(if (eval(a.cond, c)) a.then else a.otherwise, c)
                is Action.FindImage -> findImage(a, c)
                is Action.ReadText -> {
                    warnNoCapture(c)
                    val t = vision.readText(c.reg(a.region)!!)
                    c.vars[a.into] = if (a.numbersOnly) numberIn(t) else t
                }
                is Action.SetVar -> c.vars[a.name] = interp(a.value, c)
                is Action.Calc -> c.vars[a.name] = calc(c.vars[a.name], a.op, interp(a.value, c))
                is Action.TapFound -> {
                    val x = c.vars["${a.from}.x"]?.toIntOrNull(); val y = c.vars["${a.from}.y"]?.toIntOrNull()
                    if (x != null && y != null) {
                        val (dx, dy) = jit(a.jitter.posPx)
                        touch(c, listOf(Stroke(listOf(Point(x + dx, y + dy)), 0, a.holdMs.coerceAtLeast(1))))
                    }
                }
                is Action.Global -> gestures.global(a.kind)
                is Action.TypeText -> device.typeText(interp(a.text, c), a.append)
                is Action.Clipboard -> device.setClipboard(interp(a.text, c))
                is Action.OpenApp -> {
                    device.openApp(a.packageName)
                    val start = clock()
                    while (a.waitMs > 0 && device.foregroundPackage() != a.packageName && clock() - start < a.waitMs) delay(200)
                }
                is Action.WaitStable -> waitStable(a, c)
                is Action.Alert -> {
                    val msg = interp(a.message, c)
                    device.alert(msg, a.vibrateMs, a.sound)
                    if (msg.isNotBlank()) c.emit(EngineEvent.Log(msg))
                }
                is Action.Log -> c.emit(EngineEvent.Log(interp(a.message, c)))
                is Action.Call -> call(a, c)
                is Action.Retry -> retry(a, c)
                Action.Stop -> throw StopSignal("stop")
            }
        }
    }

    private suspend fun loop(a: Action.Loop, c: Ctx) {
        // Sem nenhum limite explícito: evita laço infinito oculto.
        if (a.count == 0 && a.untilMs == 0L && a.whileCond == null && a.untilCond == null) return
        val start = clock(); var i = 0
        while (true) {
            if (a.count > 0 && i >= a.count) break
            if (a.untilMs > 0 && clock() - start >= a.untilMs) break
            if (a.whileCond != null && !eval(a.whileCond, c)) break
            if (a.untilCond != null && eval(a.untilCond, c)) break
            c.vars["i"] = i.toString()
            exec(a.body, c); i++
            yield()
        }
    }

    private suspend fun findImage(a: Action.FindImage, c: Ctx) {
        warnNoCapture(c)
        val ids = listOf(a.templateId) + a.alternatives
        val start = clock()
        var found: List<Match>
        while (true) {
            found = emptyList()
            for (id in ids) {
                found = vision.find(id, a.threshold, c.reg(a.region), if (a.all) a.maxResults else 1, a.scaleTolerant)
                if (found.isNotEmpty()) break
            }
            if (found.isNotEmpty() || a.timeoutMs <= 0 || clock() - start >= a.timeoutMs) break
            delay(100)
        }
        c.vars["${a.into}.count"] = found.size.toString()
        if (found.isEmpty()) { exec(a.onNotFound, c); return }
        for (m in if (a.all) found else found.take(1)) {
            c.vars["${a.into}.x"] = m.x.toString(); c.vars["${a.into}.y"] = m.y.toString()
            c.emit(EngineEvent.Found(m))
            exec(a.onFound, c)
        }
    }

    private suspend fun waitStable(a: Action.WaitStable, c: Ctx) {
        warnNoCapture(c)
        val region = c.reg(a.region)
        var prev = vision.snapshot(region) ?: return
        val start = clock(); var stableSince = start
        while (clock() - start < a.timeoutMs) {
            delay(150)
            val cur = vision.snapshot(region) ?: return
            if (diff(prev, cur) > CHANGE) stableSince = clock()
            prev = cur
            if (clock() - stableSince >= a.stableMs) return
        }
    }

    private suspend fun call(a: Action.Call, c: Ctx) {
        val m = macros(a.macroId) ?: error("macro \"${a.macroId}\" não existe")
        check(c.depth < 8) { "sub-macros aninhados demais (máx. 8)" }
        val fx = c.fx; val fy = c.fy
        c.depth++; scaleFor(m, c)
        try { exec(m.actions, c) } finally { c.depth--; c.fx = fx; c.fy = fy }
    }

    private suspend fun retry(a: Action.Retry, c: Ctx) {
        repeat(a.times.coerceAtLeast(1)) {
            exec(a.body, c)
            if (eval(a.until, c)) return
            delay(a.delayMs)
        }
        exec(a.onFail, c)
    }

    private suspend fun eval(cond: Condition, c: Ctx): Boolean = when (cond) {
        Condition.Always -> true
        is Condition.Random -> rng.nextDouble() < cond.probability
        is Condition.Var -> compare(c.vars[cond.name] ?: "", cond.op, interp(cond.value, c))
        is Condition.TextContains -> { warnNoCapture(c); vision.readText(c.reg(cond.region)!!).contains(interp(cond.text, c), cond.ignoreCase) }
        is Condition.ImageVisible -> { warnNoCapture(c); vision.find(cond.templateId, cond.threshold, c.reg(cond.region)).isNotEmpty() }
        is Condition.Not -> !eval(cond.inner, c)
        is Condition.AllOf -> cond.conds.all { eval(it, c) }
        is Condition.AnyOf -> cond.conds.any { eval(it, c) }
        is Condition.Pixel -> { warnNoCapture(c); vision.pixel(c.x(cond.x), c.y(cond.y))?.let { colorNear(it, cond.color, cond.tolerance) } ?: false }
        is Condition.Changed -> {
            warnNoCapture(c)
            val cur = vision.snapshot(c.reg(cond.region))
            val prev = if (cur != null) c.snaps.put(cond.key, cur) else null
            cur != null && prev != null && diff(prev, cur) > CHANGE
        }
        is Condition.AppOpen -> device.foregroundPackage() == cond.packageName
    }

    private fun jit(px: Int): Pair<Int, Int> = if (px <= 0) 0 to 0 else rng.nextInt(-px, px + 1) to rng.nextInt(-px, px + 1)
    private fun jitter(ms: Long): Long = if (ms <= 0) 0 else rng.nextLong(-ms, ms + 1)
    private suspend fun pause(jitterMs: Long) { if (jitterMs > 0) delay(rng.nextLong(0, jitterMs + 1)) }

    companion object {
        private val VAR = Regex("""\$\{([^}]+)}""")
        /** Diferença média (0..255) acima da qual a tela "mudou". */
        private const val CHANGE = 3.0

        fun compare(l: String, op: Op, r: String): Boolean {
            if (op == Op.CONTAINS) return l.contains(r)
            val a = l.toDoubleOrNull(); val b = r.toDoubleOrNull()
            val cmp = if (a != null && b != null) a.compareTo(b) else l.compareTo(r)
            return when (op) {
                Op.EQ -> cmp == 0; Op.NE -> cmp != 0; Op.LT -> cmp < 0
                Op.LE -> cmp <= 0; Op.GT -> cmp > 0; Op.GE -> cmp >= 0; Op.CONTAINS -> false
            }
        }

        /** Primeiro número do texto, sem separadores: "Ouro: 1.250" -> "1250". Vazio se não houver. */
        fun numberIn(t: String): String {
            val m = Regex("""-?\d[\d.,]*""").find(t) ?: return ""
            return m.value.trimEnd('.', ',').replace(".", "").replace(",", "")
        }

        fun calc(current: String?, op: MathOp, value: String): String {
            val l = current?.toDoubleOrNull() ?: 0.0
            val r = value.toDoubleOrNull() ?: 0.0
            val out = when (op) {
                MathOp.SET -> r
                MathOp.ADD -> l + r
                MathOp.SUB -> l - r
                MathOp.MUL -> l * r
                MathOp.DIV -> if (r == 0.0) l else l / r
                MathOp.MOD -> if (r == 0.0) l else l % r
                MathOp.MIN -> minOf(l, r)
                MathOp.MAX -> maxOf(l, r)
            }
            return if (out == out.toLong().toDouble()) out.toLong().toString() else out.toString()
        }

        fun colorNear(argb: Int, hex: String, tol: Int): Boolean {
            val c = hex.removePrefix("#").toLongOrNull(16)?.toInt() ?: return false
            return abs(((argb shr 16) and 255) - ((c shr 16) and 255)) <= tol &&
                abs(((argb shr 8) and 255) - ((c shr 8) and 255)) <= tol &&
                abs((argb and 255) - (c and 255)) <= tol
        }

        fun diff(a: IntArray, b: IntArray): Double {
            if (a.size != b.size || a.isEmpty()) return 255.0
            var s = 0L
            for (i in a.indices) s += abs(a[i] - b[i])
            return s.toDouble() / a.size
        }
    }
}

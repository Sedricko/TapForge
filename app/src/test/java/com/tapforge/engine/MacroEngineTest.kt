package com.tapforge.engine

import com.tapforge.model.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

private class FakeGestures : GestureDispatcher {
    val taps = mutableListOf<Pair<Int, Int>>()
    val gestures = mutableListOf<List<Stroke>>()
    override suspend fun gesture(strokes: List<Stroke>): Boolean {
        gestures += strokes
        if (strokes.size == 1 && strokes[0].points.size == 1) taps += strokes[0].points[0].let { it.x to it.y }
        return true
    }
    override fun global(kind: GlobalKind) = true
}

private class FakeVision(
    val hits: List<Match> = emptyList(), val text: String = "",
    val px: Int? = null, val snaps: MutableList<IntArray> = mutableListOf(),
) : Vision {
    override suspend fun find(templateId: String, threshold: Float, region: Region?, max: Int, scaleTolerant: Boolean) = hits.take(max)
    override suspend fun readText(region: Region) = text
    override suspend fun pixel(x: Int, y: Int) = px
    override suspend fun snapshot(region: Region?) = snaps.removeFirstOrNull()
}

private class FakeDevice(val size: Pair<Int, Int> = 0 to 0, var fg: String? = null) : Device {
    val typed = mutableListOf<String>()
    override fun screenSize() = size
    override fun foregroundPackage() = fg
    override fun openApp(packageName: String): Boolean { fg = packageName; return true }
    override fun typeText(text: String, append: Boolean): Boolean { typed += text; return true }
}

private fun once(vararg a: Action) = Macro("m", "m", actions = a.toList(), maxIterations = 1)

class MacroEngineTest {
    @Test fun loopTapsNTimes() = runTest {
        val g = FakeGestures()
        MacroEngine(g, FakeVision()).run(once(Action.Loop(count = 3, body = listOf(Action.Tap(1, 2)))))
        assertEquals(3, g.taps.size)
    }

    @Test fun ifBranchesOnVariable() = runTest {
        val g = FakeGestures()
        MacroEngine(g, FakeVision()).run(once(
            Action.SetVar("n", "5"),
            Action.If(Condition.Var("n", Op.GT, "3"), then = listOf(Action.Tap(1, 1)), otherwise = listOf(Action.Tap(9, 9))),
        ))
        assertEquals(listOf(1 to 1), g.taps)
    }

    @Test fun findImageThenTapFound() = runTest {
        val g = FakeGestures()
        MacroEngine(g, FakeVision(hits = listOf(Match(100, 200, 0.97f)))).run(once(
            Action.FindImage("btn", onFound = listOf(Action.TapFound())),
        ))
        assertEquals(listOf(100 to 200), g.taps)
    }

    @Test fun findAllTapsEach() = runTest {
        val g = FakeGestures()
        val e = MacroEngine(g, FakeVision(hits = listOf(Match(1, 1, 1f), Match(5, 5, 1f))))
        e.run(once(Action.FindImage("b", all = true, onFound = listOf(Action.TapFound()))))
        assertEquals(listOf(1 to 1, 5 to 5), g.taps)
        assertEquals("2", e.lastVars["img.count"])
    }

    @Test fun stopEndsRun() = runTest {
        val g = FakeGestures()
        MacroEngine(g, FakeVision()).run(Macro("m", "m", actions = listOf(Action.Tap(1, 1), Action.Stop, Action.Tap(2, 2))))
        assertEquals(1, g.taps.size)
    }

    @Test fun ocrConditionUsesText() = runTest {
        val g = FakeGestures()
        MacroEngine(g, FakeVision(text = "Tudo OK")).run(once(
            Action.If(Condition.TextContains(Region(0, 0, 10, 10), "ok"), then = listOf(Action.Tap(7, 7))),
        ))
        assertEquals(listOf(7 to 7), g.taps)
    }

    @Test fun calcAndInterpolation() = runTest {
        val d = FakeDevice()
        val e = MacroEngine(FakeGestures(), FakeVision(text = "Ouro: 1.250"), d)
        e.run(once(
            Action.ReadText(Region(0, 0, 1, 1), "ouro", numbersOnly = true),
            Action.Calc("ouro", MathOp.ADD, "50"),
            Action.TypeText("tenho \${ouro}"),
        ))
        assertEquals("1300", e.lastVars["ouro"])
        assertEquals(listOf("tenho 1300"), d.typed)
    }

    @Test fun loopUntilCondition() = runTest {
        val e = MacroEngine(FakeGestures(), FakeVision())
        e.run(once(
            Action.SetVar("n", "0"),
            Action.Loop(count = 100, untilCond = Condition.Var("n", Op.GE, "4"), body = listOf(Action.Calc("n", MathOp.ADD, "1"))),
        ))
        assertEquals("4", e.lastVars["n"])
    }

    @Test fun andOrPixel() = runTest {
        val g = FakeGestures()
        MacroEngine(g, FakeVision(px = 0xFFFF0000.toInt())).run(once(
            Action.If(Condition.AllOf(listOf(Condition.Pixel(0, 0, "#F00808"), Condition.AnyOf(listOf(Condition.Not(Condition.Always), Condition.Always)))),
                then = listOf(Action.Tap(3, 3))),
            Action.If(Condition.Pixel(0, 0, "#00FF00"), then = listOf(Action.Tap(4, 4))),
        ))
        assertEquals(listOf(3 to 3), g.taps)
    }

    @Test fun retryRunsOnFailAfterTimes() = runTest {
        val g = FakeGestures()
        val e = MacroEngine(g, FakeVision())
        e.run(once(Action.Retry(Condition.ImageVisible("x"), body = listOf(Action.Tap(1, 1)), times = 3, delayMs = 0, onFail = listOf(Action.Tap(9, 9)))))
        assertEquals(listOf(1 to 1, 1 to 1, 1 to 1, 9 to 9), g.taps)
    }

    @Test fun callRunsSubMacroAndFailsOnMissing() = runTest {
        val g = FakeGestures()
        val sub = Macro("sub", "sub", actions = listOf(Action.Tap(5, 5)))
        val events = mutableListOf<EngineEvent>()
        MacroEngine(g, FakeVision(), macros = { if (it == "sub") sub else null }).run(once(Action.Call("sub"), Action.Call("nada"))) { events += it }
        assertEquals(listOf(5 to 5), g.taps)
        assertTrue(events.last() is EngineEvent.Failed)
    }

    @Test fun scalesCoordinatesToScreen() = runTest {
        val g = FakeGestures()
        val m = Macro("m", "m", actions = listOf(Action.Tap(100, 200)), maxIterations = 1, screenW = 1000, screenH = 2000)
        MacroEngine(g, FakeVision(), FakeDevice(size = 500 to 1000)).run(m)
        assertEquals(listOf(50 to 100), g.taps)
    }

    @Test fun changedDetectsDifference() = runTest {
        val g = FakeGestures()
        val v = FakeVision(snaps = mutableListOf(IntArray(4) { 0 }, IntArray(4) { 0 }, IntArray(4) { 100 }))
        MacroEngine(g, v).run(Macro("m", "m", maxIterations = 3, actions = listOf(
            Action.If(Condition.Changed(), then = listOf(Action.Tap(1, 1))),
        )))
        assertEquals(1, g.taps.size)
    }

    @Test fun openAppWaitsForForeground() = runTest {
        val d = FakeDevice()
        val g = FakeGestures()
        MacroEngine(g, FakeVision(), d).run(once(
            Action.OpenApp("com.x", waitMs = 1000),
            Action.If(Condition.AppOpen("com.x"), then = listOf(Action.Tap(2, 2))),
        ))
        assertEquals(listOf(2 to 2), g.taps)
    }

    @Test fun multiTouchGesture() = runTest {
        val g = FakeGestures()
        MacroEngine(g, FakeVision()).run(once(Action.Gesture(listOf(
            Stroke(listOf(Point(0, 0), Point(10, 0))), Stroke(listOf(Point(50, 0), Point(40, 0))),
        ))))
        assertEquals(2, g.gestures.single().size)
    }

    @Test fun helpers() {
        assertEquals("1250", MacroEngine.numberIn("Ouro: 1.250"))
        assertEquals("-3", MacroEngine.numberIn("vida -3 pts"))
        assertEquals("", MacroEngine.numberIn("nada"))
        assertEquals("2.5", MacroEngine.calc("5", MathOp.DIV, "2"))
        assertEquals("5", MacroEngine.calc("5", MathOp.DIV, "0"))
    }

    @Test fun jsonRoundTrip() {
        val json = kotlinx.serialization.json.Json { classDiscriminator = "type"; encodeDefaults = true; ignoreUnknownKeys = true }
        val m = Macro("m", "m", actions = listOf(Action.Loop(count = 2, untilCond = Condition.AnyOf(listOf(Condition.Always)), body = listOf(Action.Stop))),
            trigger = Trigger.Schedule("08:30"))
        assertEquals(m, json.decodeFromString(Macro.serializer(), json.encodeToString(Macro.serializer(), m)))
    }
}

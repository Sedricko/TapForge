package com.tapforge.engine

import com.tapforge.model.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private class FakeGestures : GestureDispatcher {
    val taps = mutableListOf<Pair<Int, Int>>()
    override suspend fun tap(x: Int, y: Int, holdMs: Long): Boolean { taps += x to y; return true }
    override suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long) = true
    override fun global(kind: GlobalKind) = true
}

private class FakeVision(val hit: Match? = null, val text: String = "") : Vision {
    override suspend fun findImage(templateId: String, threshold: Float, region: Region?) = hit
    override suspend fun readText(region: Region) = text
}

class MacroEngineTest {
    @Test fun loopTapsNTimes() = runTest {
        val g = FakeGestures()
        val m = Macro("m", "m", actions = listOf(Action.Loop(count = 3, body = listOf(Action.Tap(1, 2)))), maxIterations = 1)
        MacroEngine(g, FakeVision()).run(m)
        assertEquals(3, g.taps.size)
    }

    @Test fun ifBranchesOnVariable() = runTest {
        val g = FakeGestures()
        val m = Macro("m", "m", maxIterations = 1, actions = listOf(
            Action.SetVar("n", "5"),
            Action.If(Condition.Var("n", Op.GT, "3"), then = listOf(Action.Tap(1, 1)), otherwise = listOf(Action.Tap(9, 9))),
        ))
        MacroEngine(g, FakeVision()).run(m)
        assertEquals(listOf(1 to 1), g.taps)
    }

    @Test fun findImageThenTapFound() = runTest {
        val g = FakeGestures()
        val m = Macro("m", "m", maxIterations = 1, actions = listOf(
            Action.FindImage("btn", onFound = listOf(Action.TapFound())),
        ))
        MacroEngine(g, FakeVision(hit = Match(100, 200, 0.97f))).run(m)
        assertEquals(listOf(100 to 200), g.taps)
    }

    @Test fun stopEndsRun() = runTest {
        val g = FakeGestures()
        val m = Macro("m", "m", actions = listOf(Action.Tap(1, 1), Action.Stop, Action.Tap(2, 2)))
        MacroEngine(g, FakeVision()).run(m)
        assertEquals(1, g.taps.size)
    }

    @Test fun ocrConditionUsesText() = runTest {
        val g = FakeGestures()
        val m = Macro("m", "m", maxIterations = 1, actions = listOf(
            Action.If(Condition.TextContains(Region(0, 0, 10, 10), "ok"), then = listOf(Action.Tap(7, 7))),
        ))
        MacroEngine(g, FakeVision(text = "Tudo OK")).run(m)
        assertEquals(listOf(7 to 7), g.taps)
    }
}

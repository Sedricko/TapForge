package com.tapforge.engine

import com.tapforge.model.*
import com.tapforge.vision.Gray
import com.tapforge.vision.TemplateMatcher
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class TemplateMatcherTest {
    private fun noise(w: Int, h: Int, seed: Int): Gray { val r = Random(seed); return Gray(w, h, IntArray(w * h) { r.nextInt(256) }) }
    private fun plant(hay: Gray, n: Gray, x: Int, y: Int) {
        for (j in 0 until n.h) for (i in 0 until n.w) hay.d[(y + j) * hay.w + x + i] = n.d[j * n.w + i]
    }

    @Test fun findsPlantedTemplate() {
        val hay = noise(200, 150, 1)
        val needle = Gray(40, 30, IntArray(40 * 30) { Random(2).nextInt(256) xor (it * 31 % 256) })
        plant(hay, needle, 80, 60)
        val hit = TemplateMatcher.find(hay, needle, 0.95f)
        assertNotNull(hit); assertEquals(80 + 20, hit!!.x); assertEquals(60 + 15, hit.y)
    }

    @Test fun rejectsAbsentTemplate() {
        assertNull(TemplateMatcher.find(noise(200, 150, 3), noise(40, 30, 4), 0.95f))
    }

    @Test fun findAllReturnsEachCopy() {
        val hay = noise(300, 200, 5)
        val needle = noise(32, 32, 6)
        plant(hay, needle, 20, 30); plant(hay, needle, 200, 120)
        val hits = TemplateMatcher.findAll(hay, needle, 0.95f, 10).map { it.x to it.y }.toSet()
        assertEquals(setOf(36 to 46, 216 to 136), hits)
    }

    @Test fun findScaledHandlesSmallerTemplate() {
        // Blocos grandes sobrevivem ao redimensionamento; ruído puro não.
        val big = Gray(48, 48, IntArray(48 * 48) { i -> if ((i % 48) / 12 % 2 == (i / 48) / 12 % 2) 230 else 20 })
        val hay = Gray(200, 200, IntArray(200 * 200) { 128 })
        plant(hay, big.resize(43, 43), 60, 70)
        assertTrue(TemplateMatcher.findScaled(hay, big, 0.85f, 1).isNotEmpty())
    }

    @Test fun treeEditing() {
        val root = listOf(Action.Loop(count = 2, body = listOf(Action.Tap(1, 1))), Action.Stop)
        val p: ListPath = listOf(0 to 0)
        val added = updateList(root, p) { it + Action.Wait(5) }
        assertEquals(listOf(Action.Tap(1, 1), Action.Wait(5)), getList(added, p))
        assertEquals(5, flatten(added).size) // loop, bloco, tap, wait, stop
    }
}

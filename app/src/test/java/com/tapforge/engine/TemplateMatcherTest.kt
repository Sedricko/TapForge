package com.tapforge.engine

import com.tapforge.vision.Gray
import com.tapforge.vision.TemplateMatcher
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class TemplateMatcherTest {
    private fun noise(w: Int, h: Int, seed: Int): Gray { val r = Random(seed); return Gray(w, h, IntArray(w * h) { r.nextInt(256) }) }

    @Test fun findsPlantedTemplate() {
        val hay = noise(200, 150, 1)
        val needle = Gray(40, 30, IntArray(40 * 30) { Random(2).nextInt(256) xor (it * 31 % 256) })
        for (j in 0 until 30) for (i in 0 until 40) hay.d[(60 + j) * 200 + 80 + i] = needle.d[j * 40 + i]
        val hit = TemplateMatcher.find(hay, needle, 0.95f)
        assertNotNull(hit); assertEquals(80 + 20, hit!!.x); assertEquals(60 + 15, hit.y)
    }

    @Test fun rejectsAbsentTemplate() {
        assertNull(TemplateMatcher.find(noise(200, 150, 3), noise(40, 30, 4), 0.95f))
    }
}

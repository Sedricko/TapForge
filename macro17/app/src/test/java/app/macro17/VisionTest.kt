package app.macro17

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class VisionTest {
    private fun noise(w: Int, h: Int, seed: Int): Gray { val r = Random(seed); return Gray(w, h, IntArray(w * h) { r.nextInt(256) }) }
    private fun plant(hay: Gray, n: Gray, x: Int, y: Int) {
        for (j in 0 until n.h) for (i in 0 until n.w) hay.d[(y + j) * hay.w + x + i] = n.d[j * n.w + i]
    }

    @Test fun findsPlantedTemplate() {
        val hay = noise(200, 150, 1)
        val needle = noise(40, 30, 2)
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

    // --- correlação normalizada ---
    /** g' = a·g + b, com recorte para 0..255: a mesma imagem numa tela mais escura e com menos contraste. */
    private fun dim(n: Gray) = Gray(n.w, n.h, IntArray(n.d.size) { (n.d[it] * 5 / 10 + 40).coerceIn(0, 255) })

    @Test fun nccFindsTemplateDespiteBrightnessAndContrast() {
        val hay = noise(200, 150, 11)
        val needle = noise(30, 20, 12)
        plant(hay, dim(needle), 80, 60)
        // O SAD é enganado pela diferença de brilho (similaridade ~0,86); a correlação não.
        assertNull(TemplateMatcher.find(hay, needle, 0.9f))
        val hit = TemplateMatcher.findNcc(hay, needle, 0.9f)
        assertNotNull(hit); assertEquals(80 + 15, hit!!.x); assertEquals(60 + 10, hit.y)
        assertTrue(hit.score > 0.99f)
    }

    @Test fun nccRejectsAbsentAndRandomTemplates() {
        assertNull(TemplateMatcher.findNcc(noise(200, 150, 13), noise(30, 20, 14), 0.9f))
    }

    @Test fun nccPrefersTheTrueMatchAmongCandidates() {
        val hay = noise(260, 160, 15)
        val needle = noise(40, 30, 16)
        plant(hay, needle, 40, 48)                       // o certo (múltiplo de 4: ruído puro desalinhado na redução correlaciona mal)
        plant(hay, dim(noise(40, 30, 17)), 160, 90)      // um impostor com outro conteúdo
        val hit = TemplateMatcher.findNcc(hay, needle, 0.9f)
        assertEquals(40 + 20, hit!!.x); assertEquals(48 + 15, hit.y)
    }

    @Test fun flatTemplateFallsBackToDifferenceMatching() {
        val hay = Gray(120, 100, IntArray(120 * 100) { 30 })
        val needle = Gray(24, 16, IntArray(24 * 16) { 220 })   // retângulo liso: sem variância, a ZNCC não existe
        for (j in 0 until 16) for (i in 0 until 24) hay.d[(40 + j) * 120 + 60 + i] = 220
        val hit = TemplateMatcher.findNcc(hay, needle, 0.95f)
        assertNotNull(hit); assertEquals(60 + 12, hit!!.x); assertEquals(40 + 8, hit.y)
    }

    @Test fun nccScaledFindsAResizedTemplate() {
        val big = Gray(48, 48, IntArray(48 * 48) { i -> if ((i % 48) / 12 % 2 == (i / 48) / 12 % 2) 230 else 20 })
        val hay = Gray(200, 200, IntArray(200 * 200) { 128 })
        plant(hay, big.resize(43, 43), 60, 70)
        assertNotNull(TemplateMatcher.findNccScaled(hay, big, 0.85f))
    }
}

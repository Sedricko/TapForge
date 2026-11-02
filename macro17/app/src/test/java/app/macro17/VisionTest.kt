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

    // --- método do app de referência: diferença de pixels dentro da área, decidida pela % configurada ---
    private fun argbOf(g: Gray) = IntArray(g.d.size) { (0xFF shl 24) or (g.d[it] shl 16) or (g.d[it] shl 8) or g.d[it] }

    @Test fun sqdiffFindsExactCopyWithFullSimilarity() {
        val hay = noise(200, 150, 21)
        val needle = noise(30, 20, 22)
        plant(hay, needle, 80, 60)
        val hit = TemplateMatcher.findSqdiff(hay, needle, 0.95f, mono = true)
        assertNotNull(hit); assertEquals(80 + 15, hit!!.x); assertEquals(60 + 10, hit.y)
        assertTrue(hit.score > 0.999f)
    }

    @Test fun sqdiffSimilarityIsThePercentOfMatchingPixels() {
        // 10% dos pixels do modelo bem diferentes → semelhança alta mas abaixo de 100%, e a % configurada decide.
        // Fundo texturizado: num fundo liso haveria outras janelas idênticas ao modelo e a busca as acharia.
        val needle = noise(20, 20, 41)
        val hay = noise(60, 60, 42)
        plant(hay, needle, 20, 20)
        for (i in 0 until 40) { val k = (20 + i / 20) * 60 + 20 + i % 20; hay.d[k] = (hay.d[k] + 128) % 256 } // 2 linhas de 20 = 40 de 400 pixels (10%)
        val sim = TemplateMatcher.sqdiffSim(hay, needle, 20, 20, mono = true)
        assertTrue("semelhança $sim", sim in 0.80f..0.97f)                           // não é 100%, mas é alta
        assertNotNull(TemplateMatcher.findSqdiff(hay, needle, 0.75f, mono = true))   // configurado em 75%: aceita
        assertNull(TemplateMatcher.findSqdiff(hay, needle, 0.99f, mono = true))      // configurado em 99%: recusa
    }

    @Test fun sqdiffFollowsTheOriginalAndRejectsADimmedCopy() {
        val hay = noise(200, 150, 23)
        val needle = noise(30, 20, 24)
        plant(hay, dim(needle), 80, 60)
        assertNull(TemplateMatcher.findSqdiff(hay, needle, 0.95f, mono = true))       // o método original é sensível a brilho
        assertNotNull(TemplateMatcher.findNcc(hay, needle, 0.95f))                    // a alternativa tolerante acha
    }

    @Test fun sqdiffThresholdIsStrictlyGreaterAndHundredMeansIdentical() {
        assertTrue(TemplateMatcher.meets(0.96f, 0.95f))
        assertTrue(!TemplateMatcher.meets(0.95f, 0.95f))              // estritamente maior, como no original
        assertTrue(TemplateMatcher.meets(1.0f, 1.0f))                 // melhoria: 100% = idêntico
        assertTrue(!TemplateMatcher.meets(0.9f, 1.0f))
        val hay = noise(120, 90, 25)
        val needle = noise(24, 16, 26)
        plant(hay, needle, 40, 30)
        assertNotNull(TemplateMatcher.findSqdiff(hay, needle, 1.0f, mono = true))
        hay.d[30 * 120 + 44] = (hay.d[30 * 120 + 44] + 100) % 256     // um único pixel diferente: deixa de ser idêntico
        assertNull(TemplateMatcher.findSqdiff(hay, needle, 1.0f, mono = true))
    }

    @Test fun sqdiffColorModeSeesWhatGrayModeCannot() {
        // mesma luminosidade, cor diferente: o modelo é vermelho, a tela tem verde com o mesmo cinza
        val w = 120; val h = 90
        val p = Random(27)
        val needleArgb = IntArray(24 * 16) { val v = 40 + p.nextInt(180); (0xFF shl 24) or (v shl 16) }
        val hayArgb = IntArray(w * h) { val v = p.nextInt(256); (0xFF shl 24) or (v shl 16) or (v shl 8) or v }
        for (j in 0 until 16) for (i in 0 until 24) {
            val red = (needleArgb[j * 24 + i] shr 16) and 255
            val g = red * 77 / 151                                     // verde que dá o mesmo cinza do vermelho
            hayArgb[(30 + j) * w + 40 + i] = (0xFF shl 24) or (g shl 8)
        }
        val needle = Gray.fromArgb(needleArgb, 24, 16)
        val hay = Gray.fromArgb(hayArgb, w, h)
        assertNotNull(TemplateMatcher.findSqdiff(hay, needle, 0.95f, mono = true))    // em cinza, parece igual
        assertNull(TemplateMatcher.findSqdiff(hay, needle, 0.95f, mono = false))      // em cor, não é
    }

    @Test fun cropKeepsColorAlignedWithGray() {
        val g = Gray.fromArgb(IntArray(40 * 30) { (0xFF shl 24) or (it % 256) }, 40, 30)
        val c = g.crop(10, 5, 8, 6)
        assertEquals(8 * 6, c.rgb!!.size)
        assertEquals(g.rgb!![5 * 40 + 10], c.rgb!![0])
    }

    @Test fun sqdiffFindsAnAreaTheSameSizeAsTheModel() {
        // área do tamanho do modelo: é uma comparação direta, sem busca (modo "posição fixa" do original)
        val needle = noise(30, 20, 28)
        val same = Gray(30, 20, needle.d.copyOf())
        assertNotNull(TemplateMatcher.findSqdiff(same, needle, 0.99f, mono = true))
        assertNull(TemplateMatcher.findSqdiff(noise(30, 20, 29), needle, 0.9f, mono = true))
    }
}

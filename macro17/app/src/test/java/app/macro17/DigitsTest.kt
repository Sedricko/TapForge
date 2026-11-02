package app.macro17

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DigitsTest {
    // Uma amostra com todos os dígitos ensina o leitor: é o fluxo do usuário (recorte + "0123456789").
    private fun trained() = GlyphSet().also { assertNull(Digits.learn(TestFont.render("0123456789", 3, 220, 20), "0123456789", it)) }

    @Test fun readsOtherNumbersAtOtherScalesAndColors() {
        val set = trained()
        assertEquals(4081L, Digits.read(TestFont.render("4081", 3, 220, 20), set))
        assertEquals(9527L, Digits.read(TestFont.render("9527", 4, 220, 20), set))      // outro tamanho
        assertEquals(3141L, Digits.read(TestFont.render("3141", 5, 200, 40), set))      // menos contraste
        assertEquals(2086L, Digits.read(TestFont.render("2086", 2, 230, 10), set))      // menor
        assertEquals(7777L, Digits.read(TestFont.render("7777", 6, 180, 60), set))
        assertEquals(5L, Digits.read(TestFont.render("5", 3, 220, 20), set))            // um dígito só
    }

    @Test fun readsDarkTextOnLightBackground() {
        val set = trained()
        assertEquals(6038L, Digits.read(TestFont.render("6038", 3, 40, 230), set))
        assertEquals(9021L, Digits.read(TestFont.render("9021", 4, 60, 210), set))
    }

    @Test fun readsThroughNoise() {
        val set = trained()
        assertEquals(4081L, Digits.read(TestFont.render("4081", 3, 220, 20, noise = 25, seed = 3), set))
        assertEquals(9527L, Digits.read(TestFont.render("9527", 4, 200, 40, noise = 30, seed = 5), set))
    }

    @Test fun refusesTouchingDigitsInsteadOfGuessing() {
        assertNull(Digits.read(TestFont.render("4081", 3, 220, 20, gap = 0), trained()))
    }

    @Test fun refusesAnUnknownSymbol() {
        val w = 20; val h = 20
        val d = IntArray(w * h) { 20 }
        for (i in 3..16) { d[i * w + i] = 220; d[i * w + i + 1] = 220; d[i * w + 19 - i] = 220; d[i * w + 18 - i] = 220 } // um X grosso
        assertNull(Digits.read(Gray(w, h, d), trained()))
    }

    @Test fun emptyReaderReadsNothing() {
        assertNull(Digits.read(TestFont.render("123", 3, 220, 20), GlyphSet()))
    }

    @Test fun blankCropReadsNothing() {
        assertNull(Digits.read(Gray(40, 20, IntArray(800) { 30 }), trained()))
    }

    @Test fun learnExplainsAWrongCount() {
        val msg = Digits.learn(TestFont.render("12", 3, 220, 20), "123", GlyphSet())
        assertNotNull(msg); assertTrue(msg!!.contains("encontrei 2"))
    }

    @Test fun learnRejectsNonDigitsAndEmptyLabels() {
        assertNotNull(Digits.learn(TestFont.render("12", 3, 220, 20), "1a", GlyphSet()))
        assertNotNull(Digits.learn(TestFont.render("12", 3, 220, 20), "", GlyphSet()))
    }

    @Test fun keepsOnlyTheMostRecentSamplesPerDigit() {
        val set = GlyphSet()
        repeat(15) { assertNull(Digits.learn(TestFont.render("1", 3 + it % 3, 220, 20), "1", set)) }
        assertEquals(12, set.byDigit.getValue('1').size)
    }

    @Test fun summaryListsEveryDigit() {
        assertEquals("0:1  1:1  2:1  3:1  4:1  5:1  6:1  7:1  8:1  9:1", trained().summary())
    }
}

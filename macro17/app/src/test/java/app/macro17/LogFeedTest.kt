package app.macro17

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogFeedTest {
    private fun e(tag: String, text: String, t: Long = 0) = Entry(t, tag, text, LogRules.level(tag, text))

    @Test fun errorsWarningsAndInfoAreClassified() {
        assertEquals(Level.ERROR, LogRules.level("gesto", "ERRO dispatchGesture recusou"))
        assertEquals(Level.ERROR, LogRules.level("FATAL", "qualquer"))
        assertEquals(Level.WARN, LogRules.level("passo", "passo 2 imagem: não achou"))
        assertEquals(Level.WARN, LogRules.level("aviso", "Passo 2: imagem não achada. Melhor semelhança 40%"))
        assertEquals(Level.WARN, LogRules.level("passo", "passo 3 número: condição não satisfeita no prazo"))
        assertEquals(Level.INFO, LogRules.level("passo", "passo 1/3 TAP atraso=0ms"))
    }

    @Test fun categoriesSelectTheRightEntries() {
        val step = e("passo", "passo 1/3 TAP atraso=0ms")
        val found = e("passo", "passo 2 imagem: achou em 10,20 semelhança 97%")
        val gesture = e("gesto", "5,5>5,5 @50ms")
        val sys = e("serviço", "acessibilidade conectada")
        val err = e("run", "ERRO Throwable no executor")
        assertTrue(LogRules.matches(Cat.STEPS, step)); assertTrue(LogRules.matches(Cat.STEPS, found))
        assertFalse(LogRules.matches(Cat.STEPS, gesture))
        assertTrue(LogRules.matches(Cat.SEARCH, found)); assertFalse(LogRules.matches(Cat.SEARCH, step))
        assertTrue(LogRules.matches(Cat.GESTURES, gesture))
        assertTrue(LogRules.matches(Cat.SYSTEM, sys)); assertFalse(LogRules.matches(Cat.SYSTEM, gesture))
        assertTrue(LogRules.matches(Cat.ERRORS, err)); assertFalse(LogRules.matches(Cat.ERRORS, step))
        listOf(step, found, gesture, sys, err).forEach { assertTrue(LogRules.matches(Cat.ALL, it)) }
    }

    @Test fun tonesFollowSeverityThenKind() {
        assertEquals(Tone.ERROR, LogRules.tone(e("gesto", "ERRO x")))
        assertEquals(Tone.WARN, LogRules.tone(e("passo", "passo 1 texto: não achou")))
        assertEquals(Tone.VALUE, LogRules.tone(e("passo", "passo 1 texto: (3, 4)")))
        assertEquals(Tone.SYSTEM, LogRules.tone(e("tela", "MainActivity criada")))
        assertEquals(Tone.PLAIN, LogRules.tone(e("passo", "passo 1/2 TAP atraso=0ms")))
    }

    @Test fun snapshotIsNewestFirstAndFiltered() {
        val f = LogFeed()
        f.add(e("passo", "a", 1)); f.add(e("gesto", "b", 2)); f.add(e("passo", "c", 3))
        assertEquals(listOf("c", "b", "a"), f.snapshot(Cat.ALL).map { it.text })
        assertEquals(listOf("c", "a"), f.snapshot(Cat.STEPS).map { it.text })
        assertEquals(2, f.count(Cat.STEPS))
    }

    @Test fun ringKeepsOnlyTheNewest() {
        val f = LogFeed(cap = 3)
        repeat(10) { f.add(e("passo", "$it", it.toLong())) }
        assertEquals(listOf("9", "8", "7"), f.snapshot(Cat.ALL).map { it.text })
    }

    @Test fun snapshotLimitKeepsTheNewest() {
        val f = LogFeed()
        repeat(10) { f.add(e("passo", "$it")) }
        assertEquals(listOf("9", "8"), f.snapshot(Cat.ALL, limit = 2).map { it.text })
    }

    @Test fun clearEmptiesAndNotifiesListener() {
        val f = LogFeed()
        var calls = 0
        f.listener = { calls++ }
        f.add(e("passo", "x")); f.clear()
        assertEquals(0, f.snapshot(Cat.ALL).size)
        assertEquals(2, calls)
    }

    @Test fun concurrentAddsLoseNothingUpToCap() {
        val f = LogFeed(cap = 10_000)
        val ts = (0 until 4).map { t -> Thread { repeat(500) { f.add(e("passo", "t$t-$it")) } } }
        ts.forEach { it.start() }; ts.forEach { it.join() }
        assertEquals(2000, f.count(Cat.ALL))
    }
}

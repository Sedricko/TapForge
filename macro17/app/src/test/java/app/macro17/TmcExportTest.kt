package app.macro17

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

class TmcExportTest {
    private fun back(m: Macro, images: Map<String, String> = emptyMap(), files: Map<String, String> = emptyMap(), ids: Map<String, String> = emptyMap(), tids: Map<String, String> = emptyMap()): TmcImport.Result {
        val out = TmcExport.write(m, images, files)
        return TmcImport.parse(out.text, m.name, m.id, tids, ids)
    }

    @Test fun roundTripKeepsTapSwipeImageWaitGotoGroup() {
        val steps = listOf(
            Step(Type.TAP, x1 = 100, y1 = 200, x2 = 100, y2 = 200, ms = 30, delay = 290, name = "Toca"),   // 0,29 s: o import antigo truncava para 289
            Step(Type.SWIPE, x1 = 10, y1 = 20, x2 = 300, y2 = 400, ms = 800, rnd = 1500, rndMin = 500, jitter = 7),
            Step(Type.IMAGE, text = "t1", thr = 85, cycle = 300, ms = 4000, goto = 6, fail = 1, tap = false, x1 = 5, y1 = 6, x2 = 500, y2 = 600),
            Step(Type.WAIT, ms = 2500),
            Step(Type.GOTO, goto = 1),
            Step(Type.GROUP, grp = 2, rep = 3, name = "G"),
            Step(Type.TAP, x1 = 1, y1 = 2, x2 = 1, y2 = 2, ms = 10),
            Step(Type.TAP, x1 = 3, y1 = 4, x2 = 3, y2 = 4, ms = 10, dbl = true),
        )
        val m = Macro("id", "Exemplo", 4, steps)
        assertEquals(null, m.validate())
        val r = back(m, images = mapOf("t1" to "t1.png"), tids = mapOf("t1.png" to "novo"))
        val s = r.macro.steps
        assertEquals(4, r.macro.loops); assertEquals(8, s.size); assertEquals(r.notes.toString(), 1, r.notes.size) // só o aviso do Esperar
        assertEquals(listOf(Type.TAP, Type.SWIPE, Type.IMAGE, Type.TAP, Type.GOTO, Type.GROUP, Type.TAP, Type.TAP), s.map { it.type })
        assertEquals(290, s[0].delay); assertEquals(100, s[0].x1); assertEquals("Toca", s[0].name)
        assertEquals(500, s[1].rndMin); assertEquals(1500, s[1].rnd); assertEquals(7, s[1].jitter); assertEquals(400, s[1].y2)
        assertEquals("novo", s[2].text); assertEquals(6, s[2].goto); assertEquals(1, s[2].fail); assertEquals(85, s[2].thr); assertEquals(500, s[2].x2); assertTrue(!s[2].tap)
        assertTrue(s[3].skip); assertEquals(2500, s[3].delay)            // espera = item "pular" com o atraso
        assertEquals(1, s[4].goto)
        assertEquals(2, s[5].grp); assertEquals(3, s[5].rep); assertEquals("G", s[5].name)
        assertTrue(s[7].dbl)
        assertEquals(null, r.macro.validate())
    }

    @Test fun counterCasesResetCallKeyStatusAndUnsupportedTypes() {
        val steps = listOf(
            Step(Type.COUNTER, counter = 3, goto = 3, cases = listOf(Case(2, 4))),
            Step(Type.RESET, counter = 3),
            Step(Type.CALL, text = "outra", ret = false),
            Step(Type.KEY, x1 = 3),
            Step(Type.STATUS, text = "Oi, \"mundo\"", ms = 1200, snd = true, pop = true),
            Step(Type.NODE, text = "ok", delay = 100),
            Step(Type.NUMBER, delay = 200, x1 = 1, y1 = 1, x2 = 9, y2 = 9),
            Step(Type.STOP),
        )
        val m = Macro("id", "M", 1, steps)
        val out = TmcExport.write(m, emptyMap(), mapOf("outra" to "Outra"))
        assertEquals(2, out.notes.size)
        val s = TmcImport.parse(out.text, "M", "id", emptyMap(), mapOf("Outra" to "oid")).macro.steps
        assertEquals(Type.COUNTER, s[0].type); assertEquals(3, s[0].goto); assertEquals(listOf(Case(2, 4)), s[0].cases)
        assertEquals(Type.RESET, s[1].type); assertEquals(s[0].counter, s[1].counter)
        assertEquals("oid", s[2].text); assertTrue(!s[2].ret)
        assertEquals(3, s[3].x1)
        assertEquals("Oi, \"mundo\"", s[4].text); assertEquals(1200, s[4].ms); assertTrue(s[4].snd && s[4].pop && !s[4].vib)
        assertEquals(Type.TAP, s[5].type); assertTrue(s[5].skip); assertEquals(100, s[5].delay)
        assertEquals(Type.STOP, s[7].type)
    }

    @Test fun zipHasBaseLayoutAndPullsCalledMacros() {
        val b = Macro("b", "B/ruim", 1, listOf(Step(Type.TAP, x1 = 1, y1 = 1, x2 = 1, y2 = 1, ms = 5)))
        val a = Macro("a", "A", 1, listOf(Step(Type.CALL, text = "b"), Step(Type.IMAGE, text = "t", ms = 1000)))
        val out = ByteArrayOutputStream()
        val bundle = TmcExport.zip(out, listOf(a), { if (it == "b") b else null }, { if (it == "t") byteArrayOf(1, 2, 3) else null })
        assertEquals(2, bundle.macros); assertEquals(1, bundle.images)
        val names = ArrayList<String>()
        ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { z -> while (true) { names += (z.nextEntry ?: break).name } }
        assertTrue(names.toString(), names.containsAll(listOf("TouchMacroPro/A.tmc", "TouchMacroPro/B_ruim.tmc", "TouchMacroPro/image/A/t.png")))
    }

    private fun rows(text: String) = TmcImport.parseCsv(text).dropWhile { it.getOrNull(0) != "Title" || it.size < 20 }.drop(1)

    @Test fun groupChildrenGetTabAndMonoGroupGetsCaptureItem() {
        val steps = listOf(
            Step(Type.GROUP, grp = 1, name = "G"), Step(Type.TAP, x1 = 1, y1 = 2, x2 = 1, y2 = 2, ms = 10, name = "f1"),
            Step(Type.GROUP, grp = 1, mono = true, gms = 2000, name = "M"), Step(Type.TAP, x1 = 3, y1 = 4, x2 = 3, y2 = 4, ms = 10, name = "f2"),
            Step(Type.TAP, x1 = 5, y1 = 6, x2 = 5, y2 = 6, ms = 10, name = "solto"),
        )
        val out = TmcExport.write(Macro("id", "G", 1, steps), emptyMap(), emptyMap())
        val r = rows(out.text)
        assertEquals(6, r.size) // 5 itens + captura do grupo de imagens
        assertEquals("G", r[0][0]); assertEquals("1", r[0][13])
        assertEquals("\tf1", r[1][0]); assertEquals("11", r[1][13])
        assertEquals("M", r[2][0]); assertEquals("2", r[2][13])
        assertEquals("16", r[3][7]); assertEquals("12", r[3][13]); assertEquals("\t", r[3][0])
        assertEquals("\tf2", r[4][0]); assertEquals("12", r[4][13])
        assertEquals("solto", r[5][0]); assertEquals("", r[5][13])
        val s = TmcImport.parse(out.text, "G", "id", emptyMap(), emptyMap()).macro.steps
        assertEquals(listOf(Type.GROUP, Type.TAP, Type.GROUP, Type.TAP, Type.TAP), s.map { it.type })
        assertEquals(listOf(1, 0, 1, 0, 0), s.map { it.grp }); assertTrue(s[2].mono); assertEquals("f1", s[1].name)
    }

    @Test fun textImageAndChangeColumns() {
        val steps = listOf(
            Step(Type.TEXT, text = "oi", ms = 3000), Step(Type.IMAGE, text = "t1", ms = 3000), Step(Type.CHANGE, ms = 3000),
            Step(Type.TAP, x1 = 1, y1 = 1, x2 = 1, y2 = 1, ms = 10, rnd = 700, rndMin = 700),
        )
        val out = TmcExport.write(Macro("id", "T", 1, steps), mapOf("t1" to "t1.png"), emptyMap())
        val r = rows(out.text)
        assertEquals("17", r[0][7]); assertEquals("1", r[0][38]); assertEquals("3", r[0][11])
        assertEquals("6", r[1][7]); assertEquals("3", r[1][11])
        assertEquals("19", r[2][7]); assertEquals("3", r[2][11])
        assertEquals("0.7", r[3][3]); assertEquals("0", r[3][21]); assertEquals("", r[3][37]) // mín==máx: atraso fixo
        val s = TmcImport.parse(out.text, "T", "id", mapOf("t1.png" to "novo"), emptyMap()).macro.steps
        assertEquals(listOf(Type.TEXT, Type.IMAGE, Type.CHANGE, Type.TAP), s.map { it.type }); assertEquals(700, s[3].delay)
    }
}

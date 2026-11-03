package app.macro17

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TmcImportTest {
    private fun row(vararg kv: Pair<Int, String>): String {
        val c = Array(47) { "" }; kv.forEach { (i, v) -> c[i] = v }
        return c.joinToString(",") { if (it.contains(',') || it.contains('"')) "\"" + it.replace("\"", "\"\"") + "\"" else it }
    }

    private val header = (0 until 47).joinToString(",") { if (it == 0) "Title" else "c$it" }
    private fun file(vararg rows: String) = "Title,Touch Macro\nReplay,3\n" + ",\n".repeat(7) + header + "\n" + rows.joinToString("\n") + "\n"

    @Test fun csvHandlesQuotesAndCommas() {
        assertEquals(listOf(listOf("a", "b,c", "d\"e")), TmcImport.parseCsv("a,\"b,c\",\"d\"\"e\"\n"))
    }

    @Test fun tapSwipeAndJumpsAreConverted() {
        val tap = row(0 to "Tap", 1 to "100", 2 to "200", 3 to "1.5", 7 to "0", 10 to "5", 11 to "1", 24 to "7", 18 to (0x10000000 or 8).toString())
        val swipe = row(0 to "Swipe", 1 to "10", 2 to "20", 8 to "30", 9 to "40", 7 to "1", 10 to "1000", 11 to "1", 24 to "8")
        val r = TmcImport.parse(file(tap, swipe), "Minha", "id1", emptyMap(), emptyMap())
        assertEquals(3, r.macro.loops)
        assertEquals(2, r.macro.steps.size)
        val a = r.macro.steps[0]
        assertEquals(Type.TAP, a.type); assertEquals(100, a.x1); assertEquals(1500, a.delay); assertEquals(2, a.goto) // ID 8 = passo 2
        assertEquals(Type.SWIPE, r.macro.steps[1].type); assertEquals(40, r.macro.steps[1].y2)
        assertTrue(r.macro.validate() == null)
    }

    @Test fun stopKeyStatusAndCounter() {
        val r = TmcImport.parse(file(
            row(0 to "Parar", 7 to "12", 11 to "1"),
            row(0 to "Tecla", 7 to "15", 32 to "187", 11 to "1"),
            row(0 to "Alerta", 7 to "14", 31 to "3", 45 to "Oi", 10 to "5000", 11 to "1"),
            row(0 to "Cont", 7 to "11", 24 to "5", 44 to "2:0|4:1", 11 to "1"),
        ), "m", "id", emptyMap(), emptyMap())
        assertEquals(listOf(Type.STOP, Type.KEY, Type.STATUS, Type.COUNTER), r.macro.steps.map { it.type })
        assertEquals(3, r.macro.steps[1].x1)
        assertTrue(r.macro.steps[2].snd && r.macro.steps[2].vib)
        assertEquals(2, r.macro.steps[3].cases.size)
    }

    @Test fun unsupportedItemsBecomePlaceholdersAndAreReported() {
        val r = TmcImport.parse(file(row(0 to "Gesto", 7 to "3", 3 to "2", 11 to "1")), "m", "id", emptyMap(), emptyMap())
        assertEquals(Type.WAIT, r.macro.steps[0].type)
        assertTrue(r.notes.any { it.contains("gesto") })
    }
}

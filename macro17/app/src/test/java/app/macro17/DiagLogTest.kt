package app.macro17

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class DiagLogTest {
    private fun dir() = Files.createTempDirectory("diag").toFile().also { it.deleteOnExit() }

    @Test fun appendsInOrderAndReadsBack() {
        val l = DiagLog(File(dir(), "sub/d.log"))              // cria a subpasta sozinho
        l.append("um"); l.append("dois")
        assertEquals("um\ndois\n", l.readAll())
    }

    @Test fun rotatesKeepingTheNewestAndOneOlderFile() {
        val d = dir()
        val l = DiagLog(File(d, "d.log"), maxBytes = 50)
        repeat(30) { l.append("linha-$it-xxxxxxxxxx") }       // ~19 B cada: várias rotações
        val all = l.readAll()
        assertTrue(all.endsWith("linha-29-xxxxxxxxxx\n"))      // o mais recente nunca se perde
        assertFalse(all.contains("linha-0-"))                  // o mais antigo foi descartado
        assertTrue(File(d, "d.log.1").exists())
        assertTrue(all.length < 400)                           // limite respeitado: ~2 arquivos de maxBytes
    }

    @Test fun tailReturnsOnlyTheLastChars() {
        val l = DiagLog(File(dir(), "d.log"))
        l.append("abcdefghij")
        assertEquals("ghij\n", l.tail(5))
        assertEquals("abcdefghij\n", l.tail(1000))
    }

    @Test fun clearRemovesBothFiles() {
        val d = dir()
        val l = DiagLog(File(d, "d.log"), maxBytes = 10)
        repeat(5) { l.append("0123456789ab") }
        l.clear()
        assertEquals("", l.readAll())
        assertFalse(File(d, "d.log.1").exists())
    }

    @Test fun readOfAnEmptyLogIsEmptyNotAnError() {
        assertEquals("", DiagLog(File(dir(), "nunca.log")).readAll())
    }

    @Test fun concurrentWritersLoseNoLines() {
        val l = DiagLog(File(dir(), "d.log"), maxBytes = 10_000_000)
        val ts = (0 until 4).map { t -> Thread { repeat(200) { l.append("t$t-$it") } } }
        ts.forEach { it.start() }; ts.forEach { it.join() }
        assertEquals(800, l.readAll().lines().count { it.isNotEmpty() })
    }
}

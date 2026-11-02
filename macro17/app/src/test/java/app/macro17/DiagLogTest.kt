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

    private val raw = listOf(
        "--------- beginning of main",
        "11-02 21:17:06.956  4474  4474 D Macro17/app: processo iniciado pid=4474",
        "11-02 21:17:07.011  4474  4496 D libEGL  : loaded /vendor/lib64/egl/libGLES_adreno.so",
        "11-02 21:18:48.877  5017  5037 D OpenGLRenderer: endAllActiveAnimators on 0x7484 (TextView) with handle 0x7394",
        "11-02 21:19:34.202  5017  5242 D OpenCV/StaticHelper: Library opencv_java4 loaded",
        "11-02 21:19:34.204  5017  5242 I OpenCV/StaticHelper:     C++ standard:                11",
        "11-02 21:19:34.300  5017  5242 W OpenCV/StaticHelper: algo suspeito",
        "11-02 21:19:35.000  1234  1240 E AndroidRuntime: FATAL EXCEPTION: main",
        "11-02 21:19:36.000  5017  5017 D MeuTag: linha comum de outra origem",
    ).joinToString("\n")

    @Test fun logcatFilterDropsNoiseButKeepsWarningsAndErrors() {
        val out = filterLogcat(raw).lines()
        assertTrue(out.contains("--------- beginning of main"))                          // cabeçalho do buffer
        assertTrue(out.any { it.contains("Library opencv_java4 loaded") })                // prova de que o OpenCV carregou
        assertTrue(out.any { it.contains("algo suspeito") })                              // aviso do OpenCV fica
        assertTrue(out.any { it.contains("FATAL EXCEPTION") })                            // erro de qualquer origem fica
        assertTrue(out.any { it.contains("linha comum de outra origem") })                // o que não é ruído conhecido fica
        assertFalse(out.any { it.contains("Macro17/app") })                               // duplicada em "Eventos do app"
        assertFalse(out.any { it.contains("endAllActiveAnimators") })
        assertFalse(out.any { it.contains("C++ standard") })                              // configuração do OpenCV
        assertFalse(out.any { it.contains("libEGL") })
    }

    @Test fun logcatFilterOfEmptyInputIsEmpty() {
        assertEquals("", filterLogcat(""))
    }
}

package app.macro17

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupZipTest {
    private fun dir() = Files.createTempDirectory("bkp").toFile().also { it.deleteOnExit() }
    private val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 1, 2, 3, 4)
    private fun macroJson() = Macro("x", "Teste", 1, listOf(Step(Type.WAIT, ms = 100))).toJson().toString().toByteArray()
    private fun zip(vararg e: Pair<String, ByteArray>): ByteArray {
        val b = ByteArrayOutputStream()
        ZipOutputStream(b).use { z -> e.forEach { (n, d) -> z.putNextEntry(ZipEntry(n)); z.write(d); z.closeEntry() } }
        return b.toByteArray()
    }
    private fun id() = UUID.randomUUID().toString()

    @Test fun roundTripKeepsEverything() {
        val src = dir()
        val m = id(); val t = id(); val s = id()
        File(src, "macros").mkdirs(); File(src, "templates").mkdirs(); File(src, "samples").mkdirs()
        File(src, "macros/$m.json").writeBytes(macroJson())
        File(src, "templates/$t.png").writeBytes(png)
        File(src, "samples/$s.png").writeBytes(png)
        File(src, "glyphs.json").writeText("{}")
        val out = ByteArrayOutputStream()
        assertEquals(4, BackupZip.write(out, src))
        val dst = dir()
        val r = BackupZip.read(ByteArrayInputStream(out.toByteArray()), dst)
        assertEquals(4, r.restored); assertTrue(r.skipped.isEmpty())
        assertArrayEquals(png, File(dst, "templates/$t.png").readBytes())
        assertArrayEquals(macroJson(), File(dst, "macros/$m.json").readBytes())
        assertEquals("{}", File(dst, "glyphs.json").readText())
    }

    @Test fun zipSlipAndForeignNamesAreNeverWritten() {
        val dst = dir()
        val outside = File(dst.parentFile, "evil-${System.nanoTime()}.json")
        val r = BackupZip.read(ByteArrayInputStream(zip(
            "../${outside.name}" to macroJson(),
            "macros/../../${outside.name}" to macroJson(),
            "macros/nao-e-uuid.json" to macroJson(),
            "/etc/passwd" to byteArrayOf(1),
            "macros/${id()}.json.exe" to macroJson(),
        )), dst)
        assertEquals(0, r.restored)
        assertEquals(5, r.skipped.size)
        assertFalse(outside.exists())
    }

    @Test fun invalidContentIsSkippedNotRestored() {
        val dst = dir()
        val bad = id(); val badPng = id(); val badMacro = id()
        val r = BackupZip.read(ByteArrayInputStream(zip(
            "macros/$bad.json" to "isto não é json".toByteArray(),
            "templates/$badPng.png" to byteArrayOf(1, 2, 3, 4, 5, 6),
            // JSON válido, mas macro inválida (nome em branco)
            "macros/$badMacro.json" to Macro("x", " ", 1, emptyList()).toJson().toString().toByteArray(),
        )), dst)
        assertEquals(0, r.restored); assertEquals(3, r.skipped.size)
        assertFalse(File(dst, "macros/$bad.json").exists())
    }

    @Test fun oversizedEntryIsSkippedAndTheRestStillRestores() {
        val dst = dir()
        val big = id(); val good = id()
        val r = BackupZip.read(ByteArrayInputStream(zip(
            "templates/$big.png" to ByteArray((BackupZip.MAX_ENTRY + 1024).toInt()),
            "macros/$good.json" to macroJson(),
        )), dst)
        assertEquals(1, r.restored)
        assertTrue(r.skipped.single().contains("grande demais"))
        assertTrue(File(dst, "macros/$good.json").exists())
    }

    @Test fun restoreOverwritesTheSameIdAtomically() {
        val dst = dir(); val m = id()
        File(dst, "macros").mkdirs(); File(dst, "macros/$m.json").writeText("antigo")
        val r = BackupZip.read(ByteArrayInputStream(zip("macros/$m.json" to macroJson())), dst)
        assertEquals(1, r.restored)
        assertArrayEquals(macroJson(), File(dst, "macros/$m.json").readBytes())
        assertFalse(File(dst, "macros/$m.json.tmp").exists())          // não sobra arquivo temporário
    }

    @Test fun emptyAndGarbageZipRestoreNothing() {
        assertEquals(0, BackupZip.read(ByteArrayInputStream(zip()), dir()).restored)
        assertEquals(0, BackupZip.read(ByteArrayInputStream(ByteArray(0)), dir()).restored)
    }

    @Test fun writeSkipsFilesThatAreNotBackupNames() {
        val src = dir()
        File(src, "macros").mkdirs()
        File(src, "macros/lixo.txt").writeText("x")
        File(src, "macros/${id()}.tmp").writeText("x")
        assertEquals(0, BackupZip.write(ByteArrayOutputStream(), src))
    }
}

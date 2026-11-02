package app.macro17

import java.io.File
import java.io.FileOutputStream

/**
 * Registro em arquivo com rotação: ao passar de [maxBytes] o arquivo atual vira "<nome>.1" (o anterior some).
 * Fica em ~2 x maxBytes no total. Java puro, testável na JVM.
 */
class DiagLog(private val file: File, private val maxBytes: Long = 1_000_000L) {
    private val old = File(file.path + ".1")

    @Synchronized fun append(line: String) {
        file.parentFile?.mkdirs()
        if (file.length() > maxBytes) { old.delete(); file.renameTo(old) }
        FileOutputStream(file, true).use { it.write((line + "\n").toByteArray()) }
    }

    @Synchronized fun readAll(): String = listOf(old, file).filter { it.exists() }.joinToString("") { it.readText() }

    @Synchronized fun tail(maxChars: Int): String = readAll().takeLast(maxChars)

    @Synchronized fun clear() { old.delete(); file.delete() }
}

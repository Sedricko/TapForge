package app.macro17

import java.io.File
import java.io.FileOutputStream

/**
 * Tira do logcat o que só faz volume: as linhas do próprio app (já estão em "Eventos do app"), o relatório de configuração
 * do OpenCV, animações e reportes de compatibilidade; depois, o ruído do sistema por tag (ReportBuilder.filterNoise). Avisos e erros (W, E, F) de outras tags ficam sempre.
 * Formato threadtime: "MM-DD HH:MM:SS.mmm PID TID NIVEL TAG: texto".
 */
private val SPACES = Regex("\\s+")

fun filterLogcat(raw: String): String {
    val noise = listOf(
        " Macro17/", "OpenGLRenderer: endAllActiveAnimators", "CompatibilityChangeReporter", "GraphicsEnvironment",
        "NetworkSecurityConfig", "Late-enabling -Xcheck:jni", "libEGL  : loaded",
    )
    val first = raw.lineSequence().filter { line ->
        // Há espaços duplos (antes do PID e do TID): dividir por "um ou mais espaços", senão o nível cai no campo errado.
        val level = SPACES.split(line.trim(), 6).getOrNull(4)
        when {
            level == "W" || level == "E" || level == "F" -> true
            line.contains("OpenCV/StaticHelper") -> line.contains("loaded") || line.contains("is OK")
            else -> noise.none { line.contains(it) }
        }
    }.joinToString("\n")
    return ReportBuilder.filterNoise(first) // ruído do sistema por tag (StrictMode, VRI, Insets, BLAST...)
}

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

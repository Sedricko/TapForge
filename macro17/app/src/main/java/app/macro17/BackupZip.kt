package app.macro17

import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Backup em ZIP simples, como o do app base: macros, imagens (modelos e amostras) e, a mais, o leitor de números.
 * Restaurar só aceita nomes exatos (UUID.json, UUID.png, glyphs.json): isso fecha o zip-slip e impede que um ZIP
 * estranho grave fora das pastas do app. Cada macro é validada antes de entrar; imagem exige o cabeçalho PNG.
 * Java puro, testável na JVM.
 */
object BackupZip {
    const val MAX_ENTRY = 8L shl 20      // 8 MB por arquivo
    const val MAX_TOTAL = 256L shl 20    // 256 MB no total
    const val MAX_ENTRIES = 20_000

    private const val U = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
    private val ALLOWED = Regex("^(macros/$U\\.json|templates/$U\\.png|samples/$U\\.png|glyphs\\.json)$")
    private val PNG = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())

    class Result(val restored: Int, val skipped: List<String>)

    /** Grava o ZIP com tudo o que há em [root]; devolve quantos arquivos entraram. */
    fun write(out: OutputStream, root: File): Int {
        var n = 0
        ZipOutputStream(out).use { z ->
            for (dir in listOf("macros", "templates", "samples")) {
                File(root, dir).listFiles().orEmpty().sortedBy { it.name }.forEach { f ->
                    val name = "$dir/${f.name}"
                    if (f.isFile && ALLOWED.matches(name)) { put(z, name, f); n++ }
                }
            }
            File(root, "glyphs.json").takeIf { it.isFile }?.let { put(z, "glyphs.json", it); n++ }
        }
        return n
    }

    private fun put(z: ZipOutputStream, name: String, f: File) {
        z.putNextEntry(ZipEntry(name))
        f.inputStream().use { it.copyTo(z) }
        z.closeEntry()
    }

    /** Restaura de [input] para [root]; entradas inválidas são puladas e listadas, nunca gravadas. */
    fun read(input: InputStream, root: File): Result {
        var ok = 0; var total = 0L; var seen = 0
        val skipped = ArrayList<String>()
        val rootPath = root.canonicalPath + File.separator
        ZipInputStream(input).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                if (++seen > MAX_ENTRIES) throw IOException("backup com arquivos demais")
                val name = e.name
                if (e.isDirectory) continue
                if (!ALLOWED.matches(name)) { skipped += "$name: nome não permitido"; continue }
                val data = readLimited(z, MAX_ENTRY)
                if (data == null) { skipped += "$name: arquivo grande demais"; continue }
                total += data.size
                if (total > MAX_TOTAL) throw IOException("backup grande demais")
                val why = invalid(name, data)
                if (why != null) { skipped += "$name: $why"; continue }
                val dest = File(root, name)
                if (!dest.canonicalPath.startsWith(rootPath)) { skipped += "$name: fora da pasta do app"; continue }
                dest.parentFile?.mkdirs()
                val tmp = File(dest.path + ".tmp")
                tmp.writeBytes(data)
                if (!tmp.renameTo(dest)) { tmp.delete(); skipped += "$name: não foi possível gravar"; continue }
                ok++
            }
        }
        return Result(ok, skipped)
    }

    /** null = passou. Macro precisa ser JSON válido e passar em [Macro.validate]; imagem precisa começar como PNG. */
    private fun invalid(name: String, data: ByteArray): String? = try {
        when {
            name.startsWith("macros/") -> Macro.fromJson(JSONObject(String(data)), name.substringAfter('/').removeSuffix(".json")).validate()
            name.endsWith(".png") -> if (data.size > 4 && data.copyOfRange(0, 4).contentEquals(PNG)) null else "não é PNG"
            else -> { JSONObject(String(data)); null } // glyphs.json
        }
    } catch (e: Exception) { "conteúdo inválido (${e.javaClass.simpleName})" }

    /** Lê até [max] bytes; null se passar do limite (o resto do arquivo é descartado). */
    private fun readLimited(z: InputStream, max: Long): ByteArray? {
        val buf = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(16 * 1024)
        var size = 0L
        var over = false
        while (true) {
            val r = z.read(chunk)
            if (r < 0) break
            size += r
            if (size > max) { over = true; continue } // continua lendo para avançar no ZIP, sem guardar
            if (!over) buf.write(chunk, 0, r)
        }
        return if (over) null else buf.toByteArray()
    }
}

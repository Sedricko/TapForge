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

    /**
     * Grava o ZIP com o que há em [root]; devolve quantos arquivos entraram. Com [only] (ids de macro), leva só essas macros
     * e as imagens que elas citam; [samples] decide se as capturas de tela entram (podem ter dados pessoais).
     */
    fun write(out: OutputStream, root: File, only: Set<String>? = null, samples: Boolean = true): Int {
        var n = 0
        val cited = only?.let { ids -> ids.joinToString(" ") { File(root, "macros/$it.json").takeIf { f -> f.isFile }?.readText().orEmpty() } }
        ZipOutputStream(out).use { z ->
            for (dir in listOf("macros", "templates", "samples")) {
                File(root, dir).listFiles().orEmpty().sortedBy { it.name }.forEach { f ->
                    val name = "$dir/${f.name}"
                    val keep = when {
                        only == null -> dir != "samples" || samples
                        dir == "macros" -> f.nameWithoutExtension in only
                        dir == "templates" -> cited!!.contains(f.nameWithoutExtension)
                        else -> samples
                    }
                    if (keep && f.isFile && ALLOWED.matches(name)) { put(z, name, f); n++ }
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
        val staged = ArrayList<Pair<File, ByteArray>>() // nada é gravado até o ZIP inteiro ser lido: um ZIP truncado não deixa dados pela metade
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
                staged += dest to data
            }
        }
        val tmps = ArrayList<Pair<File, File>>()
        try {
            for ((dest, data) in staged) { dest.parentFile?.mkdirs(); val tmp = File(dest.path + ".tmp"); tmp.writeBytes(data); tmps += tmp to dest }
        } catch (e: IOException) { tmps.forEach { it.first.delete() }; throw e }
        for ((tmp, dest) in tmps) { if (tmp.renameTo(dest)) ok++ else { tmp.delete(); skipped += "${dest.name}: não foi possível gravar" } }
        return Result(ok, skipped)
    }

    /** null = passou. Macro precisa ser JSON válido e passar em [Macro.validate]; imagem precisa começar como PNG. */
    private fun invalid(name: String, data: ByteArray): String? = try {
        when {
            name.startsWith("macros/") -> Macro.fromJson(JSONObject(String(data)), name.substringAfter('/').removeSuffix(".json")).validate()
            name.endsWith(".png") -> when {
                data.size < 24 || !data.copyOfRange(0, 4).contentEquals(PNG) -> "não é PNG"
                else -> { // largura/altura no IHDR (big-endian): recusa imagens que estourariam a memória ao decodificar
                    fun be(o: Int) = ((data[o].toInt() and 255) shl 24) or ((data[o + 1].toInt() and 255) shl 16) or ((data[o + 2].toInt() and 255) shl 8) or (data[o + 3].toInt() and 255)
                    val w = be(16); val h = be(20)
                    if (w !in 1..8192 || h !in 1..8192 || w.toLong() * h > 16_000_000L) "imagem grande demais (${w}x$h)" else null
                }
            }
            else -> { JSONObject(String(data)); null } // glyphs.json
        }
    } catch (e: Exception) { "conteúdo inválido (${e.javaClass.simpleName})" } catch (e: StackOverflowError) { "conteúdo inválido (aninhamento)" }

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
            if (size > max + (64L shl 20)) throw java.io.IOException("entrada maior que o limite") // não gasta minutos lendo um zip-bomb
            if (size > max) { over = true; continue } // continua lendo (até o teto acima) para avançar no ZIP, sem guardar
            if (!over) buf.write(chunk, 0, r)
        }
        return if (over) null else buf.toByteArray()
    }
}

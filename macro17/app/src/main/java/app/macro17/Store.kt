package app.macro17

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream

class Store(ctx: Context) {
    private val dir = File(ctx.filesDir, "macros").apply { mkdirs() }
    private val tdir = File(ctx.filesDir, "templates").apply { mkdirs() }
    private val sdir = File(ctx.filesDir, "samples").apply { mkdirs() }
    private val gfile = File(ctx.filesDir, "glyphs.json")
    private val prefs = ctx.getSharedPreferences("macro17", Context.MODE_PRIVATE)

    var selected: String?
        get() = prefs.getString("selected", null)
        set(v) = prefs.edit().putString("selected", v).apply()

    // Opções da tela "Opções". Padrões: tela ligada e parada ao bloquear (gestos já seriam cancelados), bolha opaca.
    var keepOn: Boolean
        get() = prefs.getBoolean("keepOn", true)
        set(v) = prefs.edit().putBoolean("keepOn", v).apply()

    // Diagnóstico: grava eventos em arquivo (ligado por padrão); diagRoot inclui log do sistema via su, só se o usuário pedir.
    var diag: Boolean
        get() = prefs.getBoolean("diag", true)
        set(v) { prefs.edit().putBoolean("diag", v).apply(); Diag.enabled = v }
    var diagRoot: Boolean
        get() = prefs.getBoolean("diagRoot", false)
        set(v) = prefs.edit().putBoolean("diagRoot", v).apply()

    var stopOnLock: Boolean
        get() = prefs.getBoolean("stopOnLock", true)
        set(v) = prefs.edit().putBoolean("stopOnLock", v).apply()

    var opacity: Int // 30..100: abaixo de 30% a bolha some e não dá para pará-la
        get() = prefs.getInt("opacity", 100).coerceIn(30, 100)
        set(v) = prefs.edit().putInt("opacity", v.coerceIn(30, 100)).apply()

    // --- recortes de imagem dos passos IMAGE (um PNG por id) ---
    fun newTemplateId(): String = java.util.UUID.randomUUID().toString()

    fun saveTemplate(id: String, b: Bitmap) {
        require(ID.matches(id)) { "id inválido" }
        File(tdir, "$id.png").outputStream().use { check(b.compress(Bitmap.CompressFormat.PNG, 100, it)) { "falha ao gravar a imagem" } }
    }

    /** Miniatura (lado maior [px]) do recorte de um passo IMAGE, para a lista do editor; null se não houver. */
    fun templateThumb(id: String, px: Int): Bitmap? {
        if (!ID.matches(id)) return null
        val f = File(tdir, "$id.png").takeIf { it.exists() } ?: return null
        val full = BitmapFactory.decodeFile(f.path) ?: return null
        val k = px.toFloat() / maxOf(full.width, full.height)
        return if (k >= 1f) full else Bitmap.createScaledBitmap(full, maxOf(1, (full.width * k).toInt()), maxOf(1, (full.height * k).toInt()), true).also { full.recycle() }
    }

    /** Só UUID vira nome de arquivo; arquivo ausente ou corrompido devolve null. */
    fun template(id: String): Gray? {
        if (!ID.matches(id)) return null
        val f = File(tdir, "$id.png").takeIf { it.exists() } ?: return null
        val b = BitmapFactory.decodeFile(f.path) ?: return null
        return gray(b).also { b.recycle() }
    }

    fun gray(b: Bitmap): Gray {
        val px = IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }
        return Gray.fromArgb(px, b.width, b.height)
    }

    // --- leitor de números: amostras ainda sem rótulo e os símbolos já aprendidos ---
    fun saveSample(b: Bitmap): String {
        val id = newTemplateId()
        File(sdir, "$id.png").outputStream().use { check(b.compress(Bitmap.CompressFormat.PNG, 100, it)) { "falha ao gravar a amostra" } }
        return id
    }

    fun samples(): List<String> = sdir.listFiles { f -> f.extension == "png" }.orEmpty().map { it.nameWithoutExtension }.filter { ID.matches(it) }.sorted()

    fun sample(id: String): Bitmap? = if (ID.matches(id)) BitmapFactory.decodeFile(File(sdir, "$id.png").path) else null

    fun deleteSample(id: String) { if (ID.matches(id)) File(sdir, "$id.png").delete() }

    /** Símbolos aprendidos. Arquivo ausente ou corrompido vira um leitor vazio. */
    fun glyphs(): GlyphSet {
        val set = GlyphSet()
        runCatching {
            val o = JSONObject(gfile.readText())
            for (c in '0'..'9') {
                val a = o.optJSONArray(c.toString()) ?: continue
                for (i in 0 until a.length()) {
                    val g = a.getJSONObject(i)
                    val hex = g.getString("p")
                    if (hex.length != GLYPH_W * GLYPH_H * 2) continue
                    set.add(c, Glyph(IntArray(GLYPH_W * GLYPH_H) { hex.substring(it * 2, it * 2 + 2).toInt(16) }, g.getDouble("a").toFloat()))
                }
            }
        }
        return set
    }

    /** Grava em arquivo temporário e renomeia, como as macros. */
    fun saveGlyphs(set: GlyphSet) {
        val o = JSONObject()
        for ((c, list) in set.byDigit) o.put(c.toString(), JSONArray().also { a ->
            list.forEach { g -> a.put(JSONObject().put("a", g.aspect.toDouble()).put("p", g.px.joinToString("") { "%02x".format(it.coerceIn(0, 255)) })) }
        })
        val tmp = File(gfile.path + ".tmp")
        tmp.writeText(o.toString())
        if (!tmp.renameTo(gfile)) throw IOException("não foi possível gravar o leitor")
    }

    fun clearGlyphs() { gfile.delete() }

    /** Horário "HH:mm" do alarme de cada macro; vale só se o alarme ainda existir (ver Alarms.isScheduled). */
    fun scheduleTime(id: String): String? = prefs.getString("at_$id", null)

    fun setScheduleTime(id: String, t: String?) {
        prefs.edit().apply { if (t == null) remove("at_$id") else putString("at_$id", t) }.apply()
    }

    /** Macro aguardando o seletor de arquivo do SAF; sobrevive a Activity recriada. */
    var pendingExport: String?
        get() = prefs.getString("export", null)
        set(v) = prefs.edit().putString("export", v).apply()

    // Só UUID vira nome de arquivo: bloqueia path traversal.
    private fun file(id: String) = if (ID.matches(id)) File(dir, "$id.json") else null

    // Arquivo corrompido ou inválido é ignorado em vez de derrubar a lista.
    fun get(id: String): Macro? = runCatching {
        Macro.fromJson(JSONObject(file(id)!!.readText()), id).takeIf { it.validate() == null }
    }.getOrNull()

    fun list(): List<Macro> =
        dir.listFiles().orEmpty().filter { it.extension == "json" }.mapNotNull { get(it.nameWithoutExtension) }.sortedBy { it.name.lowercase() }

    /** Escreve em arquivo temporário e renomeia: queda no meio não corrompe a macro anterior. */
    fun save(m: Macro) {
        m.validate()?.let { throw IllegalArgumentException(it) }
        val f = file(m.id) ?: throw IllegalArgumentException("id inválido")
        val tmp = File(dir, "${m.id}.tmp")
        tmp.writeText(m.toJson().toString())
        if (!tmp.renameTo(f)) throw IOException("não foi possível gravar a macro")
    }

    fun delete(id: String) {
        file(id)?.delete()
        setScheduleTime(id, null)
        if (selected == id) selected = null
    }

    companion object {
        private val ID = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        const val MAX_IMPORT = 1 shl 20

        /** Lê no máximo [max] bytes; falha se o arquivo for maior (evita OOM com arquivo gigante). */
        fun readLimited(s: InputStream, max: Int = MAX_IMPORT): ByteArray {
            val buf = ByteArray(max + 1)
            var n = 0
            while (n < buf.size) {
                val r = s.read(buf, n, buf.size - n)
                if (r < 0) break
                n += r
            }
            if (n > max) throw IOException("arquivo maior que ${max / 1024} KB")
            return buf.copyOf(n)
        }
    }
}

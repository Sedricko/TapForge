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

    /** "Início Automático": pacote do app -> id da macro que roda ao abrir o app (como no base). */
    var autoOpen: Map<String, String>
        get() = runCatching { org.json.JSONObject(prefs.getString("autoOpen", "{}")!!).let { j -> j.keys().asSequence().associateWith { j.getString(it) } } }.getOrDefault(emptyMap())
        set(v) = prefs.edit().putString("autoOpen", org.json.JSONObject(v).toString()).apply()

    /** Posição vertical da lista flutuante (o título arrasta; o base guarda x,y). */
    var listY: Int get() = prefs.getInt("listY", 0); set(v) = prefs.edit().putInt("listY", v).apply()

    var selected: String?
        get() = prefs.getString("selected", null)
        set(v) = prefs.edit().putString("selected", v).apply()

    // Opções da tela "Opções". Padrões: tela ligada e parada ao bloquear (gestos já seriam cancelados), bolha opaca.
    /** "Tamanho da imagem a salvar" (px do recorte gravado em volta do toque). */
    var thumbPx: Int
        get() = prefs.getInt("thumbPx", 80)
        set(v) = prefs.edit().putInt("thumbPx", v).apply()
    /** "Mostrar Toques": ponto na tela onde a macro toca (padrão ligado, como no base). */
    var showTouches: Boolean
        get() = prefs.getBoolean("showTouches", true)
        set(v) = prefs.edit().putBoolean("showTouches", v).apply()
    /** Diálogo "Guia para gravar locais de toque." antes de gravar; some com "Não mostrar novamente". */
    var recGuide: Boolean
        get() = prefs.getBoolean("recGuide", true)
        set(v) = prefs.edit().putBoolean("recGuide", v).apply()
    /** Gravação de tela: 0 sem áudio, 1 microfone; bitrate em bps; quadros por segundo. */
    var recAudio: Int get() = prefs.getInt("recAudio", 0); set(v) = prefs.edit().putInt("recAudio", v).apply()
    var recBitrate: Int get() = prefs.getInt("recBitrate", 5_242_880); set(v) = prefs.edit().putInt("recBitrate", v).apply()
    var recFps: Int get() = prefs.getInt("recFps", 24); set(v) = prefs.edit().putInt("recFps", v).apply()
    var askedNotif: Boolean get() = prefs.getBoolean("askedNotif", false); set(v) = prefs.edit().putBoolean("askedNotif", v).apply()
    /** Modo de controle escolhido em Permissões (1 acessibilidade, 2 ADB, 3 root) e porta do adbd local. */
    var controlMode: Int get() = prefs.getInt("controlMode", 1); set(v) = prefs.edit().putInt("controlMode", v).apply()
    var adbPort: Int get() = prefs.getInt("adbPort", 7777); set(v) = prefs.edit().putInt("adbPort", v).apply()
    var adbKey: String? get() = prefs.getString("adbKey", null); set(v) = prefs.edit().putString("adbKey", v).apply()
    /** Shell do modo escolhido; null no modo acessibilidade. */
    fun shell(): Shell? = when (controlMode) {
        MODE_ROOT -> RootShell()
        MODE_ADB -> AdbShell(AdbClient(adbKey?.let { AdbProtocol.decodeKey(it) } ?: AdbProtocol.newKey().also { adbKey = AdbProtocol.encodeKey(it) }, adbPort))
        else -> null
    }
    var keepOn: Boolean
        get() = prefs.getBoolean("keepOn", false)
        set(v) = prefs.edit().putBoolean("keepOn", v).apply()

    // Diagnóstico: grava eventos em arquivo (ligado por padrão); diagRoot inclui log do sistema via su, só se o usuário pedir.
    var diag: Boolean
        get() = prefs.getBoolean("diag", true)
        set(v) { prefs.edit().putBoolean("diag", v).apply(); Diag.enabled = v }
    var diagRoot: Boolean
        get() = prefs.getBoolean("diagRoot", false)
        set(v) = prefs.edit().putBoolean("diagRoot", v).apply()

    // Opções do painel e das teclas, como na tela de configurações do app base. Padrão tudo desligado, menos a transparência.
    private fun flag(k: String) = prefs.getBoolean(k, false)
    private fun setFlag(k: String, v: Boolean) = prefs.edit().putBoolean(k, v).apply()
    var stopVolume: Boolean get() = flag("stopVolume"); set(v) = setFlag("stopVolume", v)
    var startVolume: Boolean get() = flag("startVolume"); set(v) = setFlag("startVolume", v)
    var hidePopup: Boolean get() = flag("hidePopup"); set(v) = setFlag("hidePopup", v)
    var simpleView: Boolean get() = flag("simpleView"); set(v) = setFlag("simpleView", v)
    var playMode: Boolean get() = flag("playMode"); set(v) = setFlag("playMode", v)
    var debugOverlay: Boolean get() = flag("debugOverlay"); set(v) = setFlag("debugOverlay", v)
    var endAlarm: Boolean get() = flag("endAlarm"); set(v) = setFlag("endAlarm", v)

    var stopOnLock: Boolean
        get() = prefs.getBoolean("stopOnLock", false) // o base não para a macro ao apagar a tela
        set(v) = prefs.edit().putBoolean("stopOnLock", v).apply()

    var opacity: Int // 50..100 (= transparência de 0 a 50%, o limite do base): mais transparente que isso a bolha some
        get() = prefs.getInt("opacity", 70).coerceIn(50, 100) // 30% de transparência por padrão, como no base (limite de 50%)
        set(v) = prefs.edit().putInt("opacity", v.coerceIn(50, 100)).apply()

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

    fun alarms(): List<Alarm> = runCatching {
        val arr = org.json.JSONArray(prefs.getString("alarms", "[]"))
        (0 until arr.length()).map { val o = arr.getJSONObject(it); Alarm(o.getString("id"), o.getString("m"), o.getInt("s"), o.getInt("u"), o.getBoolean("e")) }
    }.getOrDefault(emptyList())

    fun saveAlarms(l: List<Alarm>) {
        val arr = org.json.JSONArray()
        l.forEach { arr.put(org.json.JSONObject().put("id", it.id).put("m", it.macroId).put("s", it.start).put("u", it.uptime).put("e", it.enabled)) }
        prefs.edit().putString("alarms", arr.toString()).apply()
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
    private val hdir = File(ctx.filesDir, "history")
    /** Cópia datada a cada Salvar (como no base: até 10, a mais antiga sai). */
    fun saveHistory(m: Macro) {
        if (!ID.matches(m.id)) return
        val d = File(hdir, m.id).apply { mkdirs() }
        File(d, java.text.SimpleDateFormat("yyyyMMddHHmmss", java.util.Locale.US).format(java.util.Date()) + ".json").writeText(m.toJson().toString())
        d.listFiles().orEmpty().sortedByDescending { it.name }.drop(10).forEach { it.delete() }
    }
    /** Nomes (yyyyMMddHHmmss) das cópias, da mais recente para a mais antiga. */
    fun history(id: String): List<String> = if (!ID.matches(id)) emptyList() else File(hdir, id).listFiles().orEmpty().map { it.nameWithoutExtension }.sortedDescending()
    fun loadHistory(id: String, stamp: String): Macro? = runCatching {
        if (!ID.matches(id) || !Regex("\\d{14}").matches(stamp)) return null
        Macro.fromJson(JSONObject(File(File(hdir, id), "$stamp.json").readText()), id)
    }.getOrNull()

    fun save(m: Macro) {
        m.validate()?.let { throw IllegalArgumentException(it) }
        val f = file(m.id) ?: throw IllegalArgumentException("id inválido")
        Diag.d("dados", "macro salva: “${m.name}” (${m.steps.size} passos, repetições ${m.loops})")
        val tmp = File(dir, "${m.id}.tmp")
        tmp.writeText(m.toJson().toString())
        if (!tmp.renameTo(f)) throw IOException("não foi possível gravar a macro")
    }

    /** Nome já usado por outra macro (o base não aceita dois arquivos com o mesmo nome). */
    fun nameTaken(name: String, exceptId: String? = null) = list().any { it.id != exceptId && it.name.equals(name, ignoreCase = true) }

    fun delete(id: String) {
        Diag.d("dados", "macro excluída id=$id")
        file(id)?.delete()
        saveAlarms(alarms().filter { it.macroId != id })
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

package app.macro17

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream

class Store(ctx: Context) {
    private val dir = File(ctx.filesDir, "macros").apply { mkdirs() }
    private val prefs = ctx.getSharedPreferences("macro17", Context.MODE_PRIVATE)

    var selected: String?
        get() = prefs.getString("selected", null)
        set(v) = prefs.edit().putString("selected", v).apply()

    // Opções da tela "Opções". Padrões: tela ligada e parada ao bloquear (gestos já seriam cancelados), bolha opaca.
    var keepOn: Boolean
        get() = prefs.getBoolean("keepOn", true)
        set(v) = prefs.edit().putBoolean("keepOn", v).apply()

    var stopOnLock: Boolean
        get() = prefs.getBoolean("stopOnLock", true)
        set(v) = prefs.edit().putBoolean("stopOnLock", v).apply()

    var opacity: Int // 30..100: abaixo de 30% a bolha some e não dá para pará-la
        get() = prefs.getInt("opacity", 100).coerceIn(30, 100)
        set(v) = prefs.edit().putInt("opacity", v.coerceIn(30, 100)).apply()

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
        dir.listFiles().orEmpty().mapNotNull { get(it.nameWithoutExtension) }.sortedBy { it.name.lowercase() }

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

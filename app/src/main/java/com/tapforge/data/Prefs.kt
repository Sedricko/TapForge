package com.tapforge.data

import android.content.Context
import com.tapforge.model.Macro
import kotlinx.serialization.Serializable
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/** Ajustes do app. */
class Settings(c: Context) {
    private val p = c.getSharedPreferences("settings", 0)
    var volumeStop: Boolean get() = p.getBoolean("volumeStop", true); set(v) = p.edit().putBoolean("volumeStop", v).apply()
    var shakeStop: Boolean get() = p.getBoolean("shakeStop", false); set(v) = p.edit().putBoolean("shakeStop", v).apply()
    var showTargets: Boolean get() = p.getBoolean("showTargets", false); set(v) = p.edit().putBoolean("showTargets", v).apply()
    var panelAlpha: Int get() = p.getInt("panelAlpha", 95); set(v) = p.edit().putInt("panelAlpha", v).apply()
    var allowExternal: Boolean get() = p.getBoolean("allowExternal", false); set(v) = p.edit().putBoolean("allowExternal", v).apply()
    var blocked: Set<String> get() = p.getStringSet("blocked", emptySet())!!; set(v) = p.edit().putStringSet("blocked", v).apply()
    var tosAccepted: Boolean get() = p.getBoolean("tos", false); set(v) = p.edit().putBoolean("tos", v).apply()
    /** Segredo dos atalhos: só intents com ele (ou com [allowExternal]) podem iniciar macros. */
    val token: String get() = p.getString("token", null) ?: UUID.randomUUID().toString().also { p.edit().putString("token", it).apply() }
}

/** Registro das execuções: memória + arquivo, para a aba Registro. */
object RunLog {
    private const val MAX = 400
    private val lines = ArrayDeque<String>()
    private val listeners = mutableListOf<() -> Unit>()
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private var loaded = false

    private fun file(c: Context) = File(c.filesDir, "log.txt")

    @Synchronized fun add(c: Context, msg: String) {
        load(c)
        val line = "${fmt.format(Date())}  $msg"
        lines.addLast(line); while (lines.size > MAX) lines.removeFirst()
        runCatching {
            val f = file(c)
            if (f.length() > 200_000) f.writeText(lines.joinToString("\n", postfix = "\n")) else f.appendText(line + "\n")
        }
        listeners.toList().forEach { it() }
    }

    @Synchronized fun lines(c: Context): List<String> { load(c); return lines.toList() }

    @Synchronized fun clear(c: Context) { lines.clear(); file(c).delete(); listeners.toList().forEach { it() } }

    @Synchronized fun listen(l: () -> Unit) { listeners += l }
    @Synchronized fun unlisten(l: () -> Unit) { listeners -= l }

    private fun load(c: Context) {
        if (loaded) return
        loaded = true
        runCatching { file(c).readLines().takeLast(MAX).forEach { lines.addLast(it) } }
    }
}

@Serializable
data class Stat(val runs: Int = 0, val totalMs: Long = 0, val lastRun: Long = 0, val found: Int = 0)

/** Estatísticas por macro. */
object Stats {
    private fun p(c: Context) = c.getSharedPreferences("stats", 0)
    fun get(c: Context, id: String): Stat =
        p(c).getString(id, null)?.let { runCatching { MacroStore.json.decodeFromString(Stat.serializer(), it) }.getOrNull() } ?: Stat()
    fun update(c: Context, id: String, f: (Stat) -> Stat) =
        p(c).edit().putString(id, MacroStore.json.encodeToString(Stat.serializer(), f(get(c, id)))).apply()

    fun summary(c: Context, m: Macro): String {
        val s = get(c, m.id)
        if (s.runs == 0) return "Nunca executado"
        val min = s.totalMs / 60_000; val sec = s.totalMs / 1000 % 60
        val last = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(s.lastRun))
        return "${s.runs} execuções · ${min}min ${sec}s no total · ${s.found} imagens achadas · última $last"
    }
}

package com.tapforge.data

import android.content.Context
import android.util.Base64
import com.tapforge.model.Macro
import com.tapforge.service.Scheduler
import com.tapforge.vision.Templates
import kotlinx.serialization.json.*
import java.io.File
import java.util.UUID

/** Macros em JSON aberto: um arquivo por macro, exportável e versionável. */
object MacroStore {
    val json = Json { classDiscriminator = "type"; encodeDefaults = true; prettyPrint = true; ignoreUnknownKeys = true }
    private const val HISTORY_MAX = 15

    private fun dir(c: Context) = File(c.filesDir, "macros").apply { mkdirs() }
    private fun file(c: Context, id: String) = File(dir(c), "$id.json")
    private fun historyDir(c: Context, id: String) = File(c.filesDir, "history/$id")

    fun newId(): String = UUID.randomUUID().toString().take(8)

    fun encode(m: Macro): String = json.encodeToString(Macro.serializer(), m)
    fun decode(s: String): Macro = json.decodeFromString(Macro.serializer(), s)

    /** Salva; a versão anterior vai para o histórico (até [HISTORY_MAX]). */
    fun save(c: Context, m: Macro) {
        val f = file(c, m.id)
        if (f.exists()) {
            val h = historyDir(c, m.id).apply { mkdirs() }
            if (f.readText() != encode(m)) f.copyTo(File(h, "${System.currentTimeMillis()}.json"), overwrite = true)
            h.listFiles().orEmpty().sortedByDescending { it.name }.drop(HISTORY_MAX).forEach { it.delete() }
        }
        f.writeText(encode(m))
        Scheduler.reschedule(c)
    }

    fun load(c: Context, id: String): Macro? =
        file(c, id).takeIf { it.exists() }?.let { runCatching { decode(it.readText()) }.getOrNull() }

    /** Favoritos primeiro, depois por pasta e nome. */
    fun list(c: Context): List<Macro> = dir(c).listFiles { f -> f.extension == "json" }.orEmpty()
        .mapNotNull { runCatching { decode(it.readText()) }.getOrNull() }
        .sortedWith(compareBy({ !it.favorite }, { it.folder.lowercase() }, { it.name.lowercase() }))

    fun delete(c: Context, id: String) {
        file(c, id).delete(); historyDir(c, id).deleteRecursively()
        if (c.getSharedPreferences(PREFS, 0).getString("selected", null) == id) c.getSharedPreferences(PREFS, 0).edit().remove("selected").apply()
        Scheduler.reschedule(c)
    }

    /** Versões anteriores: (momento em ms, macro), mais recente primeiro. */
    fun history(c: Context, id: String): List<Pair<Long, Macro>> = historyDir(c, id).listFiles().orEmpty()
        .sortedByDescending { it.name }
        .mapNotNull { f -> runCatching { f.nameWithoutExtension.toLong() to decode(f.readText()) }.getOrNull() }

    private const val PREFS = "tapforge"
    fun select(c: Context, id: String) = c.getSharedPreferences(PREFS, 0).edit().putString("selected", id).apply()
    fun selected(c: Context): Macro? = c.getSharedPreferences(PREFS, 0).getString("selected", null)?.let { load(c, it) }

    /** Backup completo: macros e imagens recortadas num único JSON. */
    fun backup(c: Context): String = json.encodeToString(JsonObject.serializer(), buildJsonObject {
        put("tapforge_backup", 1)
        putJsonArray("macros") { list(c).forEach { add(json.encodeToJsonElement(Macro.serializer(), it)) } }
        putJsonObject("templates") {
            Templates.list(c).forEach { id -> put(id, Base64.encodeToString(Templates.file(c, id).readBytes(), Base64.NO_WRAP)) }
        }
    })

    /** Importa um backup ou um macro avulso. Retorna quantos macros entraram. */
    fun import(c: Context, text: String): Int {
        val el = json.parseToJsonElement(text).jsonObject
        if ("macros" !in el) { save(c, json.decodeFromJsonElement(Macro.serializer(), el)); return 1 }
        el["templates"]?.jsonObject?.forEach { (id, b64) -> Templates.write(c, id, Base64.decode(b64.jsonPrimitive.content, Base64.NO_WRAP)) }
        val macros = el["macros"]!!.jsonArray.map { json.decodeFromJsonElement(Macro.serializer(), it) }
        macros.forEach { save(c, it) }
        return macros.size
    }
}

package com.tapforge.data

import android.content.Context
import com.tapforge.model.Macro
import kotlinx.serialization.json.Json
import java.io.File

/** Macros em JSON aberto: um arquivo por macro, exportável e versionável. */
object MacroStore {
    val json = Json { classDiscriminator = "type"; encodeDefaults = true; prettyPrint = true; ignoreUnknownKeys = true }

    private fun dir(c: Context) = File(c.filesDir, "macros").apply { mkdirs() }

    fun save(c: Context, m: Macro) = File(dir(c), "${m.id}.json").writeText(json.encodeToString(Macro.serializer(), m))

    fun load(c: Context, id: String): Macro? =
        File(dir(c), "$id.json").takeIf { it.exists() }?.let { json.decodeFromString(Macro.serializer(), it.readText()) }

    fun list(c: Context): List<Macro> = dir(c).listFiles { f -> f.extension == "json" }.orEmpty()
        .mapNotNull { runCatching { json.decodeFromString(Macro.serializer(), it.readText()) }.getOrNull() }

    private const val PREFS = "tapforge"
    fun select(c: Context, id: String) = c.getSharedPreferences(PREFS, 0).edit().putString("selected", id).apply()
    fun selected(c: Context): Macro? = c.getSharedPreferences(PREFS, 0).getString("selected", null)?.let { load(c, it) }
}

package app.macro17

import android.graphics.BitmapFactory
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipInputStream

/** Restaurar um backup do app base: ZIP com TouchMacroPro/<nome>.tmc e as imagens PNG em TouchMacroPro/image/<nome>/ (Kotlin lê barra-asterisco como comentário aninhado, por isso sem curinga aqui). Gestos (.raw) e capturas não entram. */
object TmcZip {
    class Summary(val macros: Int, val images: Int, val notes: List<String>)

    private fun readEntry(z: ZipInputStream, max: Long): ByteArray {
        val out = java.io.ByteArrayOutputStream(); val buf = ByteArray(8192); var n = 0; var total = 0L
        while (z.read(buf).also { n = it } > 0) { total += n; if (total > max) throw IOException("arquivo grande demais no backup"); out.write(buf, 0, n) }
        return out.toByteArray()
    }

    /** Devolve null se o ZIP não tem nenhum .tmc (então não é backup do base). */
    fun import(input: InputStream, store: Store): Summary? {
        val tmc = LinkedHashMap<String, String>()               // nome da macro -> texto
        val png = HashMap<String, HashMap<String, ByteArray>>() // nome da macro -> (arquivo -> bytes)
        var total = 0L; var seen = 0
        ZipInputStream(input).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                if (++seen > BackupZip.MAX_ENTRIES) throw IOException("backup com arquivos demais")
                if (e.isDirectory) continue
                val parts = e.name.replace('\\', '/').split('/').filter { it.isNotEmpty() && it != ".." }
                val file = parts.lastOrNull() ?: continue
                when {
                    file.endsWith(".tmc") -> { val b = readEntry(z, BackupZip.MAX_ENTRY); total += b.size; tmc[file.removeSuffix(".tmc")] = String(b, Charsets.UTF_8) }
                    file.endsWith(".png") && parts.size >= 3 && parts[parts.size - 3] == "image" -> {
                        val b = readEntry(z, BackupZip.MAX_ENTRY); total += b.size
                        png.getOrPut(parts[parts.size - 2]) { HashMap() }[file] = b
                    }
                }
                if (total > BackupZip.MAX_TOTAL) throw IOException("backup grande demais")
            }
        }
        if (tmc.isEmpty()) return null
        // Como no base: importar de novo uma macro de mesmo nome a sobrescreve (e os CALL continuam apontando para ela).
        val existing = store.list().associateBy { it.name.lowercase() }
        val ids = tmc.keys.associateWith { existing[it.lowercase()]?.id ?: UUID.randomUUID().toString() }
        val notes = ArrayList<String>(); var images = 0; var done = 0
        for ((name, text) in tmc) {
            val imgIds = HashMap<String, String>()
            png[name]?.forEach { (fname, bytes) ->
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@forEach
                val tid = store.newTemplateId(); store.saveTemplate(tid, bmp); bmp.recycle(); imgIds[fname] = tid; images++
            }
            val r = TmcImport.parse(text, name, ids.getValue(name), imgIds, ids)
            notes += r.notes.map { "$name: $it" }
            if (r.macro.validate() == null) { store.save(r.macro); done++ } else notes += "$name: macro não importada (${r.macro.validate()})"
        }
        return Summary(done, images, notes)
    }
}

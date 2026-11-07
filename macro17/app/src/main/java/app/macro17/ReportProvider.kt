package app.macro17

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException

/**
 * Entrega ao seletor de compartilhamento o ZIP de "Enviar tudo". Só serve arquivos "macro17-diagnostico-AAAAMMDD-HHMMSS.zip"
 * da pasta de cache, somente leitura. Feito à mão (em vez do FileProvider do androidx) porque o app não depende de androidx.
 */
class ReportProvider : ContentProvider() {
    private fun fileFor(uri: Uri): File? {
        val n = uri.lastPathSegment ?: return null
        if (!NAME.matches(n)) return null // só o nome esperado: bloqueia path traversal
        val dir = context?.cacheDir ?: return null
        return File(dir, n).takeIf { it.isFile }
    }

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        if (mode != "r") throw SecurityException("somente leitura")
        val f = fileFor(uri) ?: throw FileNotFoundException(uri.toString())
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
        val f = fileFor(uri) ?: return null
        return MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)).apply { addRow(arrayOf<Any>(f.name, f.length())) }
    }

    override fun getType(uri: Uri): String = "application/zip"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    private companion object { val NAME = Regex("macro17-diagnostico-\\d{8}-\\d{6}\\.zip") }
}

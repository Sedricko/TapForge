package com.tapforge.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tapforge.engine.Match
import com.tapforge.engine.Vision
import com.tapforge.model.Region
import com.tapforge.service.ScreenCaptureService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/** Imagens recortadas da tela, em filesDir/templates/<id>.png. */
object Templates {
    private fun dir(c: Context) = File(c.filesDir, "templates").apply { mkdirs() }
    fun file(c: Context, id: String) = File(dir(c), "$id.png")
    fun list(c: Context): List<String> = dir(c).listFiles { f -> f.extension == "png" }.orEmpty().map { it.nameWithoutExtension }.sorted()
    fun save(c: Context, id: String, b: Bitmap) = file(c, id).outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    fun write(c: Context, id: String, bytes: ByteArray) = file(c, id).writeBytes(bytes)
    fun delete(c: Context, id: String) = file(c, id).delete()
}

/** Recorta [r] de [b], limitado às bordas; null se a região ficar vazia. */
fun Bitmap.cropTo(r: Region?): Bitmap? {
    if (r == null) return this
    val x = r.x.coerceIn(0, width - 1); val y = r.y.coerceIn(0, height - 1)
    val w = r.w.coerceAtMost(width - x); val h = r.h.coerceAtMost(height - y)
    return if (w <= 0 || h <= 0) null else Bitmap.createBitmap(this, x, y, w, h)
}

/** Visão sobre a captura de tela atual. */
class AndroidVision(private val ctx: Context) : Vision {
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private val cache = HashMap<String, Gray>()

    private fun grayOf(b: Bitmap): Gray {
        val px = IntArray(b.width * b.height)
        b.getPixels(px, 0, b.width, 0, 0, b.width, b.height)
        return Gray.fromArgb(px, b.width, b.height)
    }

    private fun template(id: String): Gray? = cache.getOrPut(id) {
        val f = Templates.file(ctx, id)
        val bmp = if (f.exists()) BitmapFactory.decodeFile(f.path) else null
        bmp?.let { grayOf(it) } ?: return null
    }

    private fun frame() = ScreenCaptureService.instance?.latestFrame()

    override val available get() = ScreenCaptureService.instance != null

    override suspend fun find(templateId: String, threshold: Float, region: Region?, max: Int, scaleTolerant: Boolean): List<Match> =
        withContext(Dispatchers.Default) {
            val needle = template(templateId) ?: return@withContext emptyList()
            val crop = frame()?.cropTo(region) ?: return@withContext emptyList()
            val hay = grayOf(crop)
            val ox = region?.x?.coerceAtLeast(0) ?: 0; val oy = region?.y?.coerceAtLeast(0) ?: 0
            val hits = when {
                scaleTolerant -> TemplateMatcher.findScaled(hay, needle, threshold, max)
                max > 1 -> TemplateMatcher.findAll(hay, needle, threshold, max)
                else -> listOfNotNull(TemplateMatcher.find(hay, needle, threshold))
            }
            hits.map { Match(it.x + ox, it.y + oy, it.score, it.w, it.h) }
        }

    override suspend fun readText(region: Region): String {
        val crop = frame()?.cropTo(region) ?: return ""
        return suspendCancellableCoroutine { cont ->
            recognizer.process(InputImage.fromBitmap(crop, 0))
                .addOnSuccessListener { if (cont.isActive) cont.resume(it.text) }
                .addOnFailureListener { if (cont.isActive) cont.resume("") }
        }
    }

    override suspend fun pixel(x: Int, y: Int): Int? {
        val f = frame() ?: return null
        return if (x in 0 until f.width && y in 0 until f.height) f.getPixel(x, y) else null
    }

    override suspend fun snapshot(region: Region?): IntArray? = withContext(Dispatchers.Default) {
        val crop = frame()?.cropTo(region) ?: return@withContext null
        grayOf(Bitmap.createScaledBitmap(crop, 32, 32, true)).d
    }
}

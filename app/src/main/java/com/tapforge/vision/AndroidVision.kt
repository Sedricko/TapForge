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

/** Visão sobre a captura de tela atual. Templates ficam em filesDir/templates/<id>.png. */
class AndroidVision(private val ctx: Context) : Vision {
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private val cache = HashMap<String, Gray>()

    private fun grayOf(b: Bitmap): Gray {
        val px = IntArray(b.width * b.height)
        b.getPixels(px, 0, b.width, 0, 0, b.width, b.height)
        return Gray.fromArgb(px, b.width, b.height)
    }

    private fun template(id: String): Gray? = cache.getOrPut(id) {
        val f = File(ctx.filesDir, "templates/$id.png")
        val bmp = if (f.exists()) BitmapFactory.decodeFile(f.path) else null
        bmp?.let { grayOf(it) } ?: return null
    }

    override suspend fun findImage(templateId: String, threshold: Float, region: Region?): Match? =
        withContext(Dispatchers.Default) {
            val frame = ScreenCaptureService.instance?.latestFrame() ?: return@withContext null
            val needle = template(templateId) ?: return@withContext null
            var hay = grayOf(frame)
            var ox = 0; var oy = 0
            if (region != null) { hay = hay.crop(region.x, region.y, region.w, region.h); ox = region.x; oy = region.y }
            TemplateMatcher.find(hay, needle, threshold)?.let { Match(it.x + ox, it.y + oy, it.score) }
        }

    override suspend fun readText(region: Region): String {
        val frame = ScreenCaptureService.instance?.latestFrame() ?: return ""
        val x = region.x.coerceIn(0, frame.width - 1); val y = region.y.coerceIn(0, frame.height - 1)
        val crop = Bitmap.createBitmap(frame, x, y, region.w.coerceAtMost(frame.width - x), region.h.coerceAtMost(frame.height - y))
        return suspendCancellableCoroutine { cont ->
            recognizer.process(InputImage.fromBitmap(crop, 0))
                .addOnSuccessListener { cont.resume(it.text) }
                .addOnFailureListener { cont.resume("") }
        }
    }
}

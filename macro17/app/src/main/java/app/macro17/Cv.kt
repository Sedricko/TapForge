package app.macro17

import org.opencv.android.OpenCVLoader
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import kotlin.math.max

/**
 * Casamento do app de referência: matchTemplate com TM_CCOEFF_NORMED sobre tons de cinza, varrendo a área toda
 * (o buscador em Kotlin é grosso-e-fino e pode perder acerto pequeno). Se a biblioteca nativa não carregar,
 * [ok] fica falso e quem chama usa o buscador em Kotlin.
 */
object Cv {
    val ok: Boolean by lazy { try { OpenCVLoader.initLocal() } catch (t: Throwable) { false } }

    private fun mat(g: Gray) = Mat(g.h, g.w, CvType.CV_8UC1).also { m ->
        m.put(0, 0, ByteArray(g.d.size) { g.d[it].toByte() })
    }

    /** Mesmo contrato de [TemplateMatcher.findNcc]: centro do melhor acerto se a correlação passar de [th]; [probe] guarda a melhor. */
    fun ncc(hay: Gray, needle: Gray, th: Float, probe: TemplateMatcher.Probe?): TemplateMatcher.Hit? {
        if (needle.w == 0 || needle.h == 0 || needle.w > hay.w || needle.h > hay.h) return null
        // Modelo liso: a correlação é indefinida (divisão por zero); o buscador em Kotlin já trata esse caso.
        if (needle.d.all { it == needle.d[0] }) return TemplateMatcher.findNcc(hay, needle, th, probe)
        val h = mat(hay); val n = mat(needle); val res = Mat()
        try {
            Imgproc.matchTemplate(h, n, res, Imgproc.TM_CCOEFF_NORMED)
            val mm = Core.minMaxLoc(res)
            val score = mm.maxVal.toFloat()
            if (score.isNaN()) return null
            probe?.let { it.best = max(it.best, score) }
            return if (score > th) TemplateMatcher.Hit(mm.maxLoc.x.toInt() + needle.w / 2, mm.maxLoc.y.toInt() + needle.h / 2, score, needle.w, needle.h) else null
        } finally { h.release(); n.release(); res.release() }
    }
}

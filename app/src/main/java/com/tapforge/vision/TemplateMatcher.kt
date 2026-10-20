package com.tapforge.vision

import kotlin.math.max
import kotlin.math.min

/** Imagem em tons de cinza (0..255). Independente de Android, testável na JVM. */
class Gray(val w: Int, val h: Int, val d: IntArray) {
    fun crop(x: Int, y: Int, cw: Int, ch: Int): Gray {
        val x0 = x.coerceIn(0, w); val y0 = y.coerceIn(0, h)
        val w2 = min(cw, w - x0); val h2 = min(ch, h - y0)
        val out = IntArray(max(0, w2) * max(0, h2))
        for (j in 0 until h2) System.arraycopy(d, (y0 + j) * w + x0, out, j * w2, w2)
        return Gray(max(0, w2), max(0, h2), out)
    }

    fun downscale(s: Int): Gray {
        if (s <= 1) return this
        val nw = w / s; val nh = h / s
        val out = IntArray(nw * nh)
        for (y in 0 until nh) for (x in 0 until nw) {
            var sum = 0
            for (j in 0 until s) for (i in 0 until s) sum += d[(y * s + j) * w + x * s + i]
            out[y * nw + x] = sum / (s * s)
        }
        return Gray(nw, nh, out)
    }

    companion object {
        /** Converte ARGB_8888 em cinza. */
        fun fromArgb(argb: IntArray, w: Int, h: Int) = Gray(w, h, IntArray(argb.size) {
            val p = argb[it]
            (((p shr 16) and 255) * 77 + ((p shr 8) and 255) * 151 + (p and 255) * 28) shr 8
        })
    }
}

object TemplateMatcher {
    /** Centro do template na imagem e similaridade 0..1 (1 = idêntico). */
    data class Hit(val x: Int, val y: Int, val score: Float)

    /** Busca grossa (reduzida) seguida de refinamento em resolução cheia. */
    fun find(hay: Gray, needle: Gray, threshold: Float): Hit? {
        if (needle.w == 0 || needle.h == 0 || needle.w > hay.w || needle.h > hay.h) return null
        val s = when { min(needle.w, needle.h) >= 24 -> 4; min(needle.w, needle.h) >= 12 -> 2; else -> 1 }
        val hs = hay.downscale(s); val ns = needle.downscale(s)
        if (ns.w == 0 || ns.h == 0 || ns.w > hs.w || ns.h > hs.h) return null
        val coarse = best(hs, ns, 0..hs.w - ns.w, 0..hs.h - ns.h)
        if (s > 1 && coarse.score < threshold - 0.15f) return null
        val cx = coarse.x * s; val cy = coarse.y * s
        val fine = best(
            hay, needle,
            max(0, cx - s - 1)..min(hay.w - needle.w, cx + s + 1),
            max(0, cy - s - 1)..min(hay.h - needle.h, cy + s + 1),
        )
        if (fine.score < threshold) return null
        return Hit(fine.x + needle.w / 2, fine.y + needle.h / 2, fine.score)
    }

    private class Best(val x: Int, val y: Int, val score: Float)

    private fun best(hay: Gray, n: Gray, xs: IntRange, ys: IntRange): Best {
        var bestSad = Long.MAX_VALUE; var bx = 0; var by = 0
        for (y in ys) for (x in xs) {
            var sad = 0L
            rows@ for (j in 0 until n.h) {
                val hb = (y + j) * hay.w + x; val nb = j * n.w
                for (i in 0 until n.w) {
                    val diff = hay.d[hb + i] - n.d[nb + i]
                    sad += if (diff < 0) -diff else diff
                }
                if (sad >= bestSad) break@rows // poda: já é pior que o melhor
            }
            if (sad < bestSad) { bestSad = sad; bx = x; by = y }
        }
        val score = if (bestSad == Long.MAX_VALUE) 0f else 1f - bestSad.toFloat() / (n.w * n.h * 255f)
        return Best(bx, by, score)
    }
}

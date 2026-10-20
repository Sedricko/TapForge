package com.tapforge.vision

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Imagem em tons de cinza (0..255). Independente de Android, testável na JVM. */
class Gray(val w: Int, val h: Int, val d: IntArray) {
    fun crop(x: Int, y: Int, cw: Int, ch: Int): Gray {
        val x0 = x.coerceIn(0, w); val y0 = y.coerceIn(0, h)
        val w2 = max(0, min(cw, w - x0)); val h2 = max(0, min(ch, h - y0))
        val out = IntArray(w2 * h2)
        for (j in 0 until h2) System.arraycopy(d, (y0 + j) * w + x0, out, j * w2, w2)
        return Gray(w2, h2, out)
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

    /** Redimensiona para nw×nh pela média de cada bloco (amostra o vizinho ao ampliar). */
    fun resize(nw: Int, nh: Int): Gray {
        if (nw <= 0 || nh <= 0 || w == 0 || h == 0) return Gray(0, 0, IntArray(0))
        val out = IntArray(nw * nh)
        for (y in 0 until nh) {
            val y0 = y * h / nh; val y1 = max(y0 + 1, (y + 1) * h / nh)
            for (x in 0 until nw) {
                val x0 = x * w / nw; val x1 = max(x0 + 1, (x + 1) * w / nw)
                var sum = 0
                for (j in y0 until y1) for (i in x0 until x1) sum += d[j * w + i]
                out[y * nw + x] = sum / ((y1 - y0) * (x1 - x0))
            }
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
    /** Centro do template na imagem, similaridade 0..1 (1 = idêntico) e tamanho achado. */
    data class Hit(val x: Int, val y: Int, val score: Float, val w: Int = 0, val h: Int = 0)

    private fun step(n: Gray) = when { min(n.w, n.h) >= 24 -> 4; min(n.w, n.h) >= 12 -> 2; else -> 1 }

    /** Busca grossa (reduzida) seguida de refinamento em resolução cheia. */
    fun find(hay: Gray, needle: Gray, threshold: Float): Hit? {
        if (needle.w == 0 || needle.h == 0 || needle.w > hay.w || needle.h > hay.h) return null
        val s = step(needle)
        val hs = hay.downscale(s); val ns = needle.downscale(s)
        if (ns.w == 0 || ns.h == 0 || ns.w > hs.w || ns.h > hs.h) return null
        val coarse = best(hs, ns, 0..hs.w - ns.w, 0..hs.h - ns.h)
        if (s > 1 && coarse.score < threshold - 0.15f) return null
        val fine = refine(hay, needle, coarse.x * s, coarse.y * s, s)
        if (fine.score < threshold) return null
        return Hit(fine.x + needle.w / 2, fine.y + needle.h / 2, fine.score, needle.w, needle.h)
    }

    /** Todas as ocorrências acima do limiar, sem sobreposição, melhores primeiro. */
    fun findAll(hay: Gray, needle: Gray, threshold: Float, max: Int): List<Hit> {
        if (needle.w == 0 || needle.h == 0 || needle.w > hay.w || needle.h > hay.h || max <= 0) return emptyList()
        val s = step(needle)
        val hs = hay.downscale(s); val ns = needle.downscale(s)
        if (ns.w == 0 || ns.h == 0 || ns.w > hs.w || ns.h > hs.h) return emptyList()
        val limit = ((1f - (threshold - 0.15f)) * ns.w * ns.h * 255f).toLong()
        val candidates = scan(hs, ns, limit).sortedBy { it.sad }
        val out = mutableListOf<Hit>()
        for (cand in candidates) {
            if (out.size >= max) break
            val f = refine(hay, needle, cand.x * s, cand.y * s, s)
            if (f.score < threshold) continue
            val cx = f.x + needle.w / 2; val cy = f.y + needle.h / 2
            if (out.any { abs(it.x - cx) < needle.w && abs(it.y - cy) < needle.h }) continue
            out += Hit(cx, cy, f.score, needle.w, needle.h)
        }
        return out
    }

    /** Tenta o template em escalas próximas (telas de densidade diferente). */
    fun findScaled(hay: Gray, needle: Gray, threshold: Float, max: Int): List<Hit> {
        for (f in floatArrayOf(1f, 0.9f, 1.1f, 0.8f, 1.25f, 0.67f, 1.5f)) {
            val n = if (f == 1f) needle else needle.resize((needle.w * f).toInt(), (needle.h * f).toInt())
            val hits = if (max == 1) listOfNotNull(find(hay, n, threshold)) else findAll(hay, n, threshold, max)
            if (hits.isNotEmpty()) return hits
        }
        return emptyList()
    }

    private class Best(val x: Int, val y: Int, val score: Float, val sad: Long = 0)

    private fun refine(hay: Gray, needle: Gray, cx: Int, cy: Int, s: Int) = best(
        hay, needle,
        max(0, cx - s - 1)..min(hay.w - needle.w, cx + s + 1),
        max(0, cy - s - 1)..min(hay.h - needle.h, cy + s + 1),
    )

    private fun sadAt(hay: Gray, n: Gray, x: Int, y: Int, limit: Long): Long {
        var sad = 0L
        for (j in 0 until n.h) {
            val hb = (y + j) * hay.w + x; val nb = j * n.w
            for (i in 0 until n.w) {
                val diff = hay.d[hb + i] - n.d[nb + i]
                sad += if (diff < 0) -diff else diff
            }
            if (sad > limit) return sad // poda: já passou do limite
        }
        return sad
    }

    private fun best(hay: Gray, n: Gray, xs: IntRange, ys: IntRange): Best {
        var bestSad = Long.MAX_VALUE; var bx = 0; var by = 0
        for (y in ys) for (x in xs) {
            val sad = sadAt(hay, n, x, y, bestSad)
            if (sad < bestSad) { bestSad = sad; bx = x; by = y }
        }
        val score = if (bestSad == Long.MAX_VALUE) 0f else 1f - bestSad.toFloat() / (n.w * n.h * 255f)
        return Best(bx, by, score)
    }

    private fun scan(hay: Gray, n: Gray, limit: Long): List<Best> {
        val out = mutableListOf<Best>()
        for (y in 0..hay.h - n.h) for (x in 0..hay.w - n.w) {
            val sad = sadAt(hay, n, x, y, limit)
            if (sad <= limit) out += Best(x, y, 0f, sad)
        }
        return out
    }
}

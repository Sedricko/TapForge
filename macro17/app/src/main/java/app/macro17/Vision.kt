package app.macro17

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

// Buscador reaproveitado do TapForge (mesmo repositório): SAD em tons de cinza, busca grossa e depois refino.
// shortcut: sensível a brilho e escala; tolerância a escala só em findScaled. Trocar por correlação normalizada se falhar em campo.
/** Imagem em tons de cinza (0..255). Independente de Android, testável na JVM. */
/** [rgb] (ARGB por pixel) é opcional: quando existe, o modo estrito também confere a cor. */
class Gray(val w: Int, val h: Int, val d: IntArray, val rgb: IntArray? = null) {
    fun crop(x: Int, y: Int, cw: Int, ch: Int): Gray {
        val x0 = x.coerceIn(0, w); val y0 = y.coerceIn(0, h)
        val w2 = max(0, min(cw, w - x0)); val h2 = max(0, min(ch, h - y0))
        val out = IntArray(w2 * h2)
        for (j in 0 until h2) System.arraycopy(d, (y0 + j) * w + x0, out, j * w2, w2)
        val rgbOut = rgb?.let { src -> IntArray(w2 * h2).also { o -> for (j in 0 until h2) System.arraycopy(src, (y0 + j) * w + x0, o, j * w2, w2) } }
        return Gray(w2, h2, out, rgbOut)
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
        }, argb)
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

    // ---- correlação normalizada (ZNCC) ----
    // Invariante a brilho e contraste (g' = a·g + b), ao contrário do SAD. Candidatos na escala reduzida, refino em escala cheia.

    private fun mean(g: Gray): Double { var s = 0L; for (v in g.d) s += v; return s.toDouble() / g.d.size }
    private fun sumSq(g: Gray, m: Double): Double { var s = 0.0; for (v in g.d) { val d = v - m; s += d * d }; return s }

    /** Sem variância a correlação não existe: recorte liso cai para a diferença absoluta. Desvio-padrão < 4. */
    private fun flat(n: Gray) = sumSq(n, mean(n)) / n.d.size < 16.0

    /** Somas acumuladas de g e g² (imagem integral): média e variância de qualquer janela em O(1). */
    private class Sums(val w1: Int, val s: LongArray, val q: LongArray) {
        fun rect(a: LongArray, x: Int, y: Int, w: Int, h: Int) = a[(y + h) * w1 + x + w] - a[y * w1 + x + w] - a[(y + h) * w1 + x] + a[y * w1 + x]
    }

    private fun integral(g: Gray): Sums {
        val w1 = g.w + 1
        val s = LongArray(w1 * (g.h + 1)); val q = LongArray(w1 * (g.h + 1))
        for (y in 1..g.h) {
            var rs = 0L; var rq = 0L
            for (x in 1..g.w) {
                val v = g.d[(y - 1) * g.w + x - 1].toLong()
                rs += v; rq += v * v
                s[y * w1 + x] = s[(y - 1) * w1 + x] + rs; q[y * w1 + x] = q[(y - 1) * w1 + x] + rq
            }
        }
        return Sums(w1, s, q)
    }

    /** ZNCC da janela de [hay] em (x,y) contra [n]; [sums] (se houver) dá as somas da janela sem percorrê-la. -1..1. */
    private fun zncc(hay: Gray, n: Gray, x: Int, y: Int, nm: Double, nSS: Double, sums: Sums?): Float {
        val cnt = n.w * n.h
        var sp = 0L; var hs = 0L; var hq = 0L
        for (j in 0 until n.h) {
            val hb = (y + j) * hay.w + x; val nb = j * n.w
            for (i in 0 until n.w) {
                val h = hay.d[hb + i]
                sp += h.toLong() * n.d[nb + i]
                if (sums == null) { hs += h; hq += h.toLong() * h }
            }
        }
        if (sums != null) { hs = sums.rect(sums.s, x, y, n.w, n.h); hq = sums.rect(sums.q, x, y, n.w, n.h) }
        val hm = hs.toDouble() / cnt
        val den = sqrt((hq - cnt * hm * hm) * nSS)
        return if (den < 1e-6) 0f else ((sp - cnt * hm * nm) / den).toFloat()
    }

    private class Cand(val x: Int, val y: Int, val score: Float)

    /** Melhores [k] posições na escala reduzida, sem vizinhas sobrepostas, com ZNCC ao menos [min]. */
    private fun coarseNcc(hs: Gray, ns: Gray, min: Float, k: Int): List<Cand> {
        val nm = mean(ns); val nSS = sumSq(ns, nm); val sums = integral(hs)
        val all = ArrayList<Cand>()
        for (y in 0..hs.h - ns.h) for (x in 0..hs.w - ns.w) {
            val sc = zncc(hs, ns, x, y, nm, nSS, sums)
            if (sc >= min) all += Cand(x, y, sc)
        }
        return nms(all, ns, k)
    }

    /** Melhores [k] candidatos, do mais parecido ao menos, sem vizinhos sobrepostos. */
    private fun nms(all: MutableList<Cand>, ns: Gray, k: Int): List<Cand> {
        all.sortByDescending { it.score }
        val out = ArrayList<Cand>()
        for (c in all) {
            if (out.size >= k) break
            if (out.none { abs(it.x - c.x) <= ns.w / 2 && abs(it.y - c.y) <= ns.h / 2 }) out += c
        }
        return out
    }

    /**
     * Limiar do app de referência (TM_CCOEFF_NORMED): configurado ≤ 99 vale max(A, 20)%; 100 vale 99%. Sucesso: score ESTRITAMENTE maior.
     * Confirmado no bytecode dele (constante de método 5 em matchTemplate; 99,0 no ramo A > 99).
     */
    fun baseThreshold(percent: Int): Float = if (percent <= 99) maxOf(percent, 20) / 100f else 0.99f

    /** Medidas da busca, para dizer ao usuário o quanto faltou quando não achou. */
    class Probe { var best = 0f }

    /**
     * Sucesso da diferença de pixels: semelhança ESTRITAMENTE maior que o limiar; 100% significa "pixel idêntico".
     */
    fun meets(sim: Float, threshold: Float) = sim > threshold || (threshold >= 1f && sim >= 0.9999f)

    // ---- alternativa: diferença de pixels (TM_SQDIFF_NORMED). NÃO é o método do app de referência (que usa correlação, ver baseThreshold);
    // é sensível a brilho, útil quando se quer a tela idêntica ao modelo. Semelhança = 1 − Σ(T−I)² / sqrt(ΣT²·ΣI²).

    /**
     * Semelhança exata da janela de [hay] em (x,y) com [needle]. Em cor soma os canais R, G, B e A (como o OpenCV
     * com imagens RGBA, que é o que o app de referência compara); [mono] compara só o cinza.
     */
    fun sqdiffSim(hay: Gray, needle: Gray, x: Int, y: Int, mono: Boolean): Float {
        val hr = hay.rgb; val nr = needle.rgb
        var num = 0L; var t2 = 0L; var i2 = 0L
        if (mono || hr == null || nr == null) {
            for (j in 0 until needle.h) {
                val hb = (y + j) * hay.w + x; val nb = j * needle.w
                for (i in 0 until needle.w) {
                    val t = needle.d[nb + i]; val v = hay.d[hb + i]; val d = t - v
                    num += d * d; t2 += t * t; i2 += v * v
                }
            }
        } else {
            for (j in 0 until needle.h) {
                val hb = (y + j) * hay.w + x; val nb = j * needle.w
                for (i in 0 until needle.w) {
                    val t = nr[nb + i]; val v = hr[hb + i]
                    val tr = (t shr 16) and 255; val tg = (t shr 8) and 255; val tb = t and 255; val ta = (t ushr 24) and 255
                    val vr = (v shr 16) and 255; val vg = (v shr 8) and 255; val vb = v and 255; val va = (v ushr 24) and 255
                    num += (tr - vr) * (tr - vr) + (tg - vg) * (tg - vg) + (tb - vb) * (tb - vb) + (ta - va) * (ta - va)
                    t2 += tr * tr + tg * tg + tb * tb + ta * ta
                    i2 += vr * vr + vg * vg + vb * vb + va * va
                }
            }
        }
        val den = sqrt(t2.toDouble() * i2.toDouble())
        val dist = if (den < 1e-9) (if (num == 0L) 0.0 else 1.0) else num / den
        return (1.0 - dist).toFloat()
    }

    /** Candidatos na escala reduzida: a soma dos quadrados é interrompida assim que passa do que a semelhança mínima admite. */
    private fun coarseSqdiff(hs: Gray, ns: Gray, minSim: Float, k: Int): List<Cand> {
        var t2 = 0L
        for (v in ns.d) t2 += v.toLong() * v
        val sums = integral(hs)
        val maxDist = 1.0 - minSim
        val all = ArrayList<Cand>()
        for (y in 0..hs.h - ns.h) for (x in 0..hs.w - ns.w) {
            val i2 = sums.rect(sums.q, x, y, ns.w, ns.h).toDouble()
            val den = sqrt(t2.toDouble() * i2)
            if (den < 1e-9) continue
            val limit = (maxDist * den).toLong() + 1
            var acc = 0L
            var j = 0
            while (j < ns.h && acc <= limit) {
                val hb = (y + j) * hs.w + x; val nb = j * ns.w
                for (i in 0 until ns.w) { val d = hs.d[hb + i] - ns.d[nb + i]; acc += d * d }
                j++
            }
            if (acc <= limit) all += Cand(x, y, (1.0 - acc / den).toFloat())
        }
        return nms(all, ns, k)
    }

    /** Procura [needle] em [hay] por diferença de pixels. Score = semelhança 0..1. */
    fun findSqdiff(hay: Gray, needle: Gray, threshold: Float, mono: Boolean, probe: Probe? = null): Hit? {
        if (needle.w == 0 || needle.h == 0 || needle.w > hay.w || needle.h > hay.h) return null
        val s = step(needle)
        val hs = hay.downscale(s); val ns = needle.downscale(s)
        if (ns.w == 0 || ns.h == 0 || ns.w > hs.w || ns.h > hs.h) return null
        var best: Hit? = null
        for (c in coarseSqdiff(hs, ns, maxOf(0.2f, threshold - 0.15f), 8)) {
            var bx = 0; var by = 0; var bs = -2f
            for (y in max(0, c.y * s - s - 1)..min(hay.h - needle.h, c.y * s + s + 1)) {
                for (x in max(0, c.x * s - s - 1)..min(hay.w - needle.w, c.x * s + s + 1)) {
                    val sc = sqdiffSim(hay, needle, x, y, mono)
                    if (sc > bs) { bs = sc; bx = x; by = y }
                }
            }
            probe?.let { it.best = maxOf(it.best, bs) }
            if (meets(bs, threshold) && (best == null || bs > best.score)) best = Hit(bx + needle.w / 2, by + needle.h / 2, bs, needle.w, needle.h)
        }
        return best
    }

    /** Tenta o modelo em escalas próximas com o buscador [find]. Mais lento: até 5 buscas. O modelo reduzido perde a cor (cinza). */
    fun scaled(needle: Gray, find: (Gray) -> Hit?): Hit? {
        for (f in floatArrayOf(1f, 0.9f, 1.1f, 0.8f, 1.25f)) {
            val n = if (f == 1f) needle else needle.resize((needle.w * f).toInt(), (needle.h * f).toInt())
            find(n)?.let { return it }
        }
        return null
    }

    // ---- alternativa tolerante a brilho: correlação normalizada (ZNCC) ----

    /** Método do app de referência: correlação normalizada (TM_CCOEFF_NORMED). Acha o recorte mesmo com brilho e contraste diferentes. Score = ZNCC. */
    fun findNcc(hay: Gray, needle: Gray, threshold: Float, probe: Probe? = null): Hit? {
        if (needle.w == 0 || needle.h == 0 || needle.w > hay.w || needle.h > hay.h) return null
        if (flat(needle)) return find(hay, needle, threshold)
        val s = step(needle)
        val hs = hay.downscale(s); val ns = needle.downscale(s)
        if (ns.w == 0 || ns.h == 0 || ns.w > hs.w || ns.h > hs.h || flat(ns)) return find(hay, needle, threshold)
        val nm = mean(needle); val nSS = sumSq(needle, nm)
        var best: Hit? = null
        for (c in coarseNcc(hs, ns, threshold - 0.15f, 5)) {
            var bx = 0; var by = 0; var bs = -2f
            for (y in max(0, c.y * s - s - 1)..min(hay.h - needle.h, c.y * s + s + 1)) {
                for (x in max(0, c.x * s - s - 1)..min(hay.w - needle.w, c.x * s + s + 1)) {
                    val sc = zncc(hay, needle, x, y, nm, nSS, null)
                    if (sc > bs) { bs = sc; bx = x; by = y }
                }
            }
            probe?.let { it.best = maxOf(it.best, bs) }
            if (bs > threshold && (best == null || bs > best.score)) best = Hit(bx + needle.w / 2, by + needle.h / 2, bs, needle.w, needle.h)
        }
        return best
    }

    fun findNccScaled(hay: Gray, needle: Gray, threshold: Float): Hit? = scaled(needle) { findNcc(hay, it, threshold) }

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

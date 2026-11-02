package app.macro17

import kotlin.math.abs

const val GLYPH_W = 12
const val GLYPH_H = 16
private const val MAX_PER_DIGIT = 12   // fontes e tamanhos diferentes convivem; passando disso, o mais antigo sai
private const val MIN_INK = 4          // menos tinta que isso é ruído
private const val MIN_H = 3
private const val MIN_SCORE = 0.85f    // confiança mínima do melhor modelo
private const val MIN_MARGIN = 0.05f   // distância para o segundo melhor dígito: abaixo disso é ambíguo e não se chuta

/** Símbolo normalizado: GLYPH_W×GLYPH_H com 255 = tinta, mais a proporção largura/altura do original. */
class Glyph(val px: IntArray, val aspect: Float)

/** Símbolos aprendidos por dígito. */
class GlyphSet(val byDigit: MutableMap<Char, MutableList<Glyph>> = HashMap()) {
    fun add(c: Char, g: Glyph) {
        val l = byDigit.getOrPut(c) { ArrayList() }
        if (l.size >= MAX_PER_DIGIT) l.removeAt(0)
        l.add(g)
    }

    fun count() = byDigit.values.sumOf { it.size }
    fun summary() = ('0'..'9').joinToString("  ") { "$it:${byDigit[it]?.size ?: 0}" }
}

/**
 * Leitor de números por amostra, sem OCR: o usuário mostra "este recorte é 4081" e o leitor aprende o formato de cada dígito
 * daquela fonte. Serve a telas com fonte fixa (jogos, contadores). Separa os símbolos por colunas vazias; dígitos colados não leem.
 */
object Digits {
    private fun otsu(g: Gray): Int {
        val hist = IntArray(256)
        for (v in g.d) hist[v.coerceIn(0, 255)]++
        val total = g.d.size
        var sum = 0L
        for (i in 0..255) sum += i.toLong() * hist[i]
        var sumB = 0L; var wB = 0; var best = -1.0; var thr = 127
        for (t in 0..255) {
            wB += hist[t]
            if (wB == 0) continue
            val wF = total - wB
            if (wF == 0) break
            sumB += t.toLong() * hist[t]
            val mB = sumB.toDouble() / wB
            val mF = (sum - sumB).toDouble() / wF
            val between = wB.toDouble() * wF * (mB - mF) * (mB - mF)
            if (between > best) { best = between; thr = t }
        }
        return thr
    }

    /** Separa os símbolos do recorte, da esquerda para a direita. A tinta é a classe com menos pixels (texto claro ou escuro). */
    fun segment(g: Gray): List<Glyph> {
        if (g.w == 0 || g.h == 0) return emptyList()
        val t = otsu(g)
        val dark = g.d.count { it <= t }
        val inkIsDark = dark <= g.d.size - dark
        val ink = BooleanArray(g.d.size) { if (inkIsDark) g.d[it] <= t else g.d[it] > t }
        val cols = BooleanArray(g.w) { x -> (0 until g.h).any { y -> ink[y * g.w + x] } }
        val boxes = ArrayList<IntArray>() // x0, x1 (exclusivo), y0, y1 (exclusivo)
        var x = 0
        while (x < g.w) {
            if (!cols[x]) { x++; continue }
            val x0 = x
            while (x < g.w && cols[x]) x++
            var y0 = g.h; var y1 = -1; var n = 0
            for (y in 0 until g.h) for (xx in x0 until x) if (ink[y * g.w + xx]) { n++; if (y < y0) y0 = y; if (y > y1) y1 = y }
            if (n >= MIN_INK && y1 - y0 + 1 >= MIN_H) boxes += intArrayOf(x0, x, y0, y1 + 1)
        }
        if (boxes.isEmpty()) return emptyList()
        val tallest = boxes.maxOf { it[3] - it[2] }
        // Pontos, riscos e sujeira são bem mais baixos que os dígitos.
        return boxes.filter { (it[3] - it[2]) * 2 >= tallest }.map { b ->
            val w = b[1] - b[0]; val h = b[3] - b[2]
            val cut = Gray(w, h, IntArray(w * h) { if (ink[(b[2] + it / w) * g.w + b[0] + it % w]) 255 else 0 })
            Glyph(cut.resize(GLYPH_W, GLYPH_H).d, w.toFloat() / h)
        }
    }

    private fun score(a: Glyph, b: Glyph): Float {
        var d = 0L
        for (i in a.px.indices) d += abs(a.px[i] - b.px[i])
        val shape = d.toFloat() / (a.px.size * 255f)
        val aspect = minOf(1f, abs(a.aspect - b.aspect) / maxOf(a.aspect, b.aspect))
        return 1f - (shape + 0.5f * aspect)
    }

    /** Aprende o recorte [g] como o número [label]. Devolve a mensagem de erro, ou null se aprendeu. */
    fun learn(g: Gray, label: String, set: GlyphSet): String? {
        if (label.isEmpty() || label.any { it !in '0'..'9' }) return "digite só os dígitos que aparecem no recorte"
        val segs = segment(g)
        if (segs.size != label.length) return "encontrei ${segs.size} símbolos no recorte, mas você digitou ${label.length}. Dígitos colados não separam; recorte só os separados."
        label.forEachIndexed { i, c -> set.add(c, segs[i]) }
        return null
    }

    /** Lê o número do recorte, ou null se algum símbolo for desconhecido ou ambíguo (nunca chuta). */
    fun read(g: Gray, set: GlyphSet): Long? {
        if (set.count() == 0) return null
        val segs = segment(g)
        if (segs.isEmpty() || segs.size > 18) return null
        val sb = StringBuilder()
        for (s in segs) {
            val per = set.byDigit.map { (c, ts) -> c to ts.maxOf { score(s, it) } }.sortedByDescending { it.second }
            val best = per[0]
            val second = per.getOrNull(1)?.second ?: 0f
            if (best.second < MIN_SCORE || best.second - second < MIN_MARGIN) return null
            sb.append(best.first)
        }
        return sb.toString().toLongOrNull()
    }
}

package app.macro17

import kotlin.random.Random

/** Fonte 5×7 para os testes: gera recortes de números sem imagem nem Android. */
object TestFont {
    private val rows = mapOf(
        '0' to listOf(".###.", "#...#", "#..##", "#.#.#", "##..#", "#...#", ".###."),
        '1' to listOf("..#..", ".##..", "..#..", "..#..", "..#..", "..#..", ".###."),
        '2' to listOf(".###.", "#...#", "....#", "...#.", "..#..", ".#...", "#####"),
        '3' to listOf("#####", "...#.", "..#..", "...#.", "....#", "#...#", ".###."),
        '4' to listOf("...#.", "..##.", ".#.#.", "#..#.", "#####", "...#.", "...#."),
        '5' to listOf("#####", "#....", "####.", "....#", "....#", "#...#", ".###."),
        '6' to listOf("..##.", ".#...", "#....", "####.", "#...#", "#...#", ".###."),
        '7' to listOf("#####", "....#", "...#.", "..#..", ".#...", ".#...", ".#..."),
        '8' to listOf(".###.", "#...#", "#...#", ".###.", "#...#", "#...#", ".###."),
        '9' to listOf(".###.", "#...#", "#...#", ".####", "....#", "...#.", ".##.."),
    )

    /** [gap] = colunas vazias entre dígitos (0 = colados); [noise] = ruído uniforme ±noise por pixel. */
    fun render(text: String, scale: Int, ink: Int, bg: Int, gap: Int = 1, margin: Int = 2, noise: Int = 0, seed: Int = 0): Gray {
        val cell = 5 + gap
        val w = text.length * cell * scale + 2 * margin
        val h = 7 * scale + 2 * margin
        val d = IntArray(w * h) { bg }
        text.forEachIndexed { k, ch ->
            rows.getValue(ch).forEachIndexed { ry, row ->
                row.forEachIndexed { rx, c ->
                    if (c == '#') for (sy in 0 until scale) for (sx in 0 until scale) d[(margin + ry * scale + sy) * w + margin + (k * cell + rx) * scale + sx] = ink
                }
            }
        }
        if (noise > 0) { val r = Random(seed); for (i in d.indices) d[i] = (d[i] + r.nextInt(-noise, noise + 1)).coerceIn(0, 255) }
        return Gray(w, h, d)
    }
}

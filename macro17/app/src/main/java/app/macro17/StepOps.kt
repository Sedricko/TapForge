package app.macro17

/** Operações de lista sobre passos, sem Android: alvos de salto (1-based, 0 = seguir) e tamanhos de grupo acompanham a edição. */

internal fun Step.mapTargets(f: (Int) -> Int) = copy(goto = f(goto), fail = f(fail), gfail = f(gfail), cases = cases.map { it.copy(to = f(it.to)) })

/** Índice do grupo mais interno que contém [i], ou -1. */
fun List<Step>.parentOf(i: Int): Int = indices.lastOrNull { k -> k < i && this[k].type == Type.GROUP && i <= k + this[k].grp } ?: -1

/** Nenhum grupo passa do fim da lista nem cruza outro. Só para o save() do editor: Macro.validate rejeitaria macros antigas. */
fun List<Step>.groupsConsistent(): Boolean {
    val ends = ArrayList<Int>()
    for (k in indices) {
        while (ends.isNotEmpty() && ends.last() < k) ends.removeAt(ends.lastIndex)
        if (this[k].type == Type.GROUP) {
            val e = k + this[k].grp
            if (e > lastIndex || (ends.isNotEmpty() && e > ends.last())) return false
            ends.add(e)
        }
    }
    return true
}

/** Remove todos os [del] (0-based) numa passada só; saltos para removidos viram "seguir", os demais descem. */
fun List<Step>.deleted(del: Set<Int>): List<Step> {
    if (del.isEmpty()) return this
    val before = IntArray(size + 1) // before[n] = removidos com índice < n
    for (n in 0 until size) before[n + 1] = before[n] + if (n in del) 1 else 0
    val f = { t: Int -> if (t <= 0 || t > size) t else if ((t - 1) in del) 0 else t - before[t - 1] }
    return mapIndexedNotNull { k, s ->
        if (k in del) return@mapIndexedNotNull null
        val g = if (s.type == Type.GROUP) s.copy(grp = s.grp - (before[minOf(k + s.grp, lastIndex) + 1] - before[k + 1])) else s
        g.mapTargets(f)
    }
}

/**
 * Duplica [idx] (0-based, ordenado) de uma vez, depois do último marcado. Saltos entre copiados apontam para as cópias;
 * grupos que contêm o ponto de inserção crescem; a cópia de um cabeçalho leva só os filhos copiados. Respeita [max] passos.
 */
fun List<Step>.duplicated(idx: List<Int>, max: Int): List<Step> {
    val pick = idx.take(maxOf(0, max - size))
    val k = pick.size
    if (k == 0) return this
    val at0 = idx.last() + 1
    val pos = pick.withIndex().associate { it.value to it.index }
    val shift = { t: Int -> if (t >= at0 + 1) t + k else t }
    val head = mapIndexed { n, s -> if (s.type == Type.GROUP && at0 > n && at0 <= n + s.grp) s.copy(grp = s.grp + k) else s }
    val copies = pick.map { i ->
        val s = this[i]
        val g = if (s.type == Type.GROUP) s.copy(grp = pick.count { it > i && it <= i + s.grp }) else s
        g.mapTargets { t -> pos[t - 1]?.let { at0 + it + 1 } ?: shift(t) }
    }
    return head.take(at0).map { it.mapTargets(shift) } + copies + head.drop(at0).map { it.mapTargets(shift) }
}

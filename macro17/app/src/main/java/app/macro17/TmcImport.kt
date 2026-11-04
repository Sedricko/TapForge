package app.macro17

/**
 * Importa macros do app base (arquivo .tmc: CSV de 47 colunas, ver docs/FUNCOES_TOUCHMACRO.md). Java/Kotlin puro, testável na JVM.
 * O que o Macro17 não tem vira um passo de espera do mesmo atraso (para os saltos continuarem apontando certo) e entra em [Result.notes]:
 * gesto gravado (.raw) e linhas de grupo (os filhos entram em sequência, sem o laço do grupo).
 */
object TmcImport {
    class Result(val macro: Macro, val notes: List<String>)

    /** CSV padrão: vírgula, aspas duplas, "" dentro de aspas escapa; linhas terminadas em \n (ou \r\n). */
    fun parseCsv(text: String): List<List<String>> {
        val rows = ArrayList<List<String>>(); var row = ArrayList<String>(); val cur = StringBuilder(); var q = false; var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                q && c == '"' && text.getOrNull(i + 1) == '"' -> { cur.append('"'); i++ }
                c == '"' -> q = !q
                !q && c == ',' -> { row.add(cur.toString()); cur.setLength(0) }
                !q && (c == '\n' || c == '\r') -> { if (c == '\r' && text.getOrNull(i + 1) == '\n') i++; row.add(cur.toString()); cur.setLength(0); rows.add(row); row = ArrayList() }
                else -> cur.append(c)
            }
            i++
        }
        if (cur.isNotEmpty() || row.isNotEmpty()) { row.add(cur.toString()); rows.add(row) }
        return rows
    }

    private const val ID_BIT = 0x10000000

    /**
     * [images]: nome do PNG no arquivo -> id do modelo já gravado no Macro17 (ausente = imagem não veio no backup).
     * [macroIds]: nome do arquivo de macro (sem .tmc) -> id da macro importada, para o item "Arquivo de macro".
     */
    fun parse(text: String, name: String, id: String, images: Map<String, String>, macroIds: Map<String, String>): Result {
        val rows = parseCsv(text)
        val notes = ArrayList<String>()
        var loops = 1
        rows.take(10).forEach { r -> if (r.getOrNull(0) == "Replay") loops = r.getOrNull(1)?.trim()?.toIntOrNull()?.coerceIn(0, MAX_LOOPS) ?: 1 }
        val header = rows.indexOfFirst { it.getOrNull(0) == "Title" && it.size > 20 }
        val items = if (header >= 0) rows.drop(header + 1) else emptyList()
        // Itens: ignora linhas de grupo (a0 com tipo e profundidade 0) e a captura interna de grupo de imagens (tipo 16).
        data class Row(val c: List<String>, val pos: Int, val depth: Int = 0, val grp: Int = 0)
        val flat = ArrayList<Row>()
        for (r0 in items) {
            if (r0.size < 16) continue
            val c = if (r0.size < 47) r0 + List(47 - r0.size) { "" } else r0
            val a0 = c[13].trim().toIntOrNull(16) ?: 0
            val isHeader = a0 != 0 && (a0 shr 4) == 0
            if (c[7].trim().toIntOrNull() == 16) continue
            flat += Row(c, flat.size, depth = if (isHeader) -1 else (a0 shr 4), grp = a0 and 0xF)
        }
        // Cabeçalho de grupo (depth -1): vira Type.GROUP com os filhos seguintes (depth 1).
        val childCount = IntArray(flat.size).also { cc -> flat.forEachIndexed { k, r -> if (r.depth == -1) { var j = k + 1; while (j < flat.size && flat[j].depth == 1) j++; cc[k] = j - k - 1 } } }
        val idToPos = HashMap<Int, Int>()
        flat.forEach { r -> r.c[24].trim().toIntOrNull()?.takeIf { it >= 0 }?.let { idToPos.putIfAbsent(it, r.pos + 1) } }
        fun target(v: String): Int {
            val n = v.trim().toIntOrNull() ?: return 0
            if (n == 0) return 0
            return if (n and ID_BIT != 0) idToPos[n and 0x0FFFFFFF] ?: 0 else n.coerceIn(0, flat.size)
        }
        // Contadores do base são os próprios itens (ID); aqui cada item contador usa o próximo número livre de 1..10.
        val counterSlot = HashMap<Int, Int>()
        fun slot(itemId: Int): Int = counterSlot.getOrPut(itemId) {
            if (counterSlot.size >= MAX_COUNTERS) notes.add("mais de $MAX_COUNTERS contadores: os excedentes compartilham o mesmo número")
            (counterSlot.size % MAX_COUNTERS) + 1
        }

        fun sec(s: String, max: Int = MAX_DELAY_MS) = ((s.trim().toDoubleOrNull() ?: 0.0) * 1000).toInt().coerceIn(0, max)
        fun f(c: List<String>, n: Int) = c[n].trim()
        val steps = flat.map { (c, pos, depth, grp) ->
            if (depth == -1) return@map Step(Type.GROUP, grp = childCount[pos], rep = (f(c, 14).toIntOrNull() ?: 1).coerceIn(0, MAX_LOOPS), mono = grp == 2, gms = if (grp == 2) sec(f(c, 16)) else 0, gfail = if (grp == 2) target(f(c, 17)) else 0, name = f(c, 0).trim('\t').take(60), off = (f(c, 15).toIntOrNull() ?: 0) > 0)
            val p = f(c, 7).toIntOrNull() ?: 0
            val cond = f(c, 11).toIntOrNull() ?: 1
            var delay = if (cond and 1 != 0) sec(f(c, 3)) else 0
            var rnd = 0; var rndMin = 0
            if ((f(c, 21).toIntOrNull() ?: 0) and 1 != 0) f(c, 37).split('~').takeIf { it.size == 2 }?.let { (a, b) -> rndMin = sec(a); rnd = sec(b); if (rnd < rndMin) rnd = rndMin; delay = 0 }
            val jitter = if (((f(c, 21).toIntOrNull() ?: 0) shr 1) and 1 != 0) (f(c, 22).toIntOrNull() ?: 0).coerceIn(0, 500) else 0
            val nm = f(c, 0).trim('\t').replace('\n', ' ').take(60)
            val x1 = f(c, 1).toIntOrNull() ?: 0; val y1 = f(c, 2).toIntOrNull() ?: 0
            var x2 = f(c, 8).toIntOrNull() ?: 0; var y2 = f(c, 9).toIntOrNull() ?: 0
            val ms = (f(c, 10).toIntOrNull() ?: 5).coerceIn(1, MAX_MS)
            val goto = target(f(c, 18)); val fail = target(f(c, 17))
            val area = f(c, 36).split(',', '-').mapNotNull { it.trim().toIntOrNull() }.takeIf { it.size == 4 && it[2] > it[0] && it[3] > it[1] }
            val custom = (f(c, 28).toIntOrNull() ?: 1) >= 3 && area != null
            val searchMs = sec(f(c, 16), MAX_MS).let { if (it in 1..99) 100 else it } // o Macro17 exige tempo 0 (sem limite) ou ≥ 100 ms
            val cycle = (f(c, 40).toIntOrNull() ?: -1).let { if (it < 0) 200 else it.coerceIn(50, MAX_MS) }
            val thr = (f(c, 19).toIntOrNull() ?: 100).coerceIn(20, 100)
            val devs = f(c, 41).split('|').map { it.trim().toIntOrNull() ?: 0 }
            val common = Step(Type.WAIT, delay = delay, rnd = rnd, rndMin = rndMin, jitter = jitter, name = nm, off = (f(c, 15).toIntOrNull() ?: 0) > 0)
            fun placeholder(why: String): Step { notes.add("Item ${pos + 1} “$nm”: $why"); return common.copy(ms = 1) }
            val built: Step = when (p) {
                0, 1, 2, 4, 5, 20 -> { // 20 = duplo toque legado
                    if (p == 2) { x2 = x1; y2 = y1 }
                    val type = when (p) { 1 -> Type.SWIPE; 4 -> Type.PINCH_IN; 5 -> Type.PINCH_OUT; else -> Type.TAP }
                    val imgName = f(c, 12); val tid = images[imgName]
                    val matchOn = (cond shr 1) and 1 != 0 && tid != null && f(c, 19).isNotEmpty()
                    common.copy(
                        type = type, x1 = x1, y1 = y1, x2 = x2, y2 = y2, ms = ms, skip = (f(c, 20).toIntOrNull() ?: 0) != 0,
                        dbl = type == Type.TAP && (f(c, 46) == "1" || p == 20), img = tid.orEmpty(), useImg = matchOn, fx = matchOn && (f(c, 28).toIntOrNull() ?: 1) == 0, // coluna 28 = 0: "Posição fixa"
                        
                        sms = if (matchOn) searchMs else 5000, cycle = if (matchOn) cycle else 200, thr = if (matchOn) thr else 90,
                        ax1 = if (matchOn && custom) area!![0] else 0, ay1 = if (matchOn && custom) area!![1] else 0,
                        ax2 = if (matchOn && custom) area!![2] else 0, ay2 = if (matchOn && custom) area!![3] else 0,
                        goto = if (matchOn && searchMs > 0) goto else 0, fail = if (matchOn && searchMs > 0) fail else 0,
                        ox = if (matchOn) devs.getOrElse(0) { 0 } else 0, oy = if (matchOn) devs.getOrElse(1) { 0 } else 0,
                    )
                }
                6 -> { // item de imagem (sem tocar por padrão)
                    val tid = images[f(c, 12)] ?: return@map placeholder("imagem não encontrada no backup")
                    common.copy(
                        type = Type.IMAGE, text = tid, dbl = f(c, 46) == "1", ms = searchMs, cycle = cycle, thr = thr, tap = (f(c, 20).toIntOrNull() ?: 1) == 0,
                        x1 = if (custom) area!![0] else 0, y1 = if (custom) area!![1] else 0, x2 = if (custom) area!![2] else 0, y2 = if (custom) area!![3] else 0,
                        goto = if (searchMs > 0) goto else 0, fail = if (searchMs > 0) fail else 0,
                        ox = devs.getOrElse(0) { 0 }, oy = devs.getOrElse(1) { 0 }, mono = (f(c, 28).toIntOrNull() ?: 1) in setOf(2, 4),
                    )
                }
                3 -> placeholder("gesto gravado (.raw) não é importado")
                10 -> common.copy(type = Type.GOTO, goto = goto)
                11 -> {
                    val cases = f(c, 44).split('|').mapNotNull { s -> s.split(':').takeIf { it.size == 2 }?.let { (a, b) -> a.trim().toIntOrNull()?.takeIf { it > 0 }?.let { n -> Case(n, target(b)) } } }.take(MAX_CASES)
                    common.copy(type = Type.COUNTER, counter = slot(f(c, 24).toIntOrNull() ?: pos), cases = cases, goto = target(f(c, 17)))
                }
                12 -> common.copy(type = Type.STOP)
                13 -> {
                    val file = f(c, 29).removeSuffix(".tmc")
                    val mid = macroIds[file] ?: return@map placeholder("macro “$file” não veio no backup")
                    common.copy(type = Type.CALL, text = mid, ret = f(c, 30) != "1")
                }
                14 -> {
                    val o = f(c, 31).toIntOrNull() ?: 1
                    common.copy(type = Type.STATUS, text = f(c, 45).ifBlank { "Alerta de status" }.take(200), ms = (f(c, 10).toIntOrNull() ?: 5000).coerceIn(1, MAX_MS), snd = o and 1 != 0, vib = o and 2 != 0)
                }
                15 -> {
                    val code = when (f(c, 32).toIntOrNull() ?: 4) { 4 -> 1; 3 -> 2; 187 -> 3; 120 -> KEY_SCREENSHOT; else -> 1 }
                    common.copy(type = Type.KEY, x1 = code, ms = (f(c, 10).toIntOrNull() ?: 0).coerceIn(0, MAX_MS))
                }
                17 -> {
                    val texts = f(c, 33).split('|', ',').map { it.trim() }.filter { it.isNotEmpty() }
                    if (texts.isEmpty() || f(c, 38) == "2") return@map placeholder("reconhecimento de texto numérico ou sem texto não é importado")
                    common.copy(
                        type = Type.TEXT, text = texts.joinToString(","), dbl = f(c, 46) == "1", ms = searchMs, cycle = maxOf(cycle, 200), minConf = (f(c, 42).toDoubleOrNull() ?: 0.0).toInt().coerceIn(0, 100),
                        x1 = if (custom) area!![0] else 0, y1 = if (custom) area!![1] else 0, x2 = if (custom) area!![2] else 0, y2 = if (custom) area!![3] else 0,
                        goto = if (searchMs > 0) goto else 0, fail = if (searchMs > 0) fail else 0, tap = (f(c, 20).toIntOrNull() ?: 1) == 0, // "Saltar" desliga o toque
                    )
                }
                18 -> common.copy(type = Type.RESET, counter = slot((f(c, 34).toIntOrNull() ?: 0) and 0x0FFFFFFF))
                19 -> common.copy(
                    type = Type.CHANGE, ms = searchMs, cycle = cycle, thr = thr.coerceAtLeast(1),
                    x1 = if (area != null) area[0] else 0, y1 = if (area != null) area[1] else 0, x2 = if (area != null) area[2] else 0, y2 = if (area != null) area[3] else 0,
                    goto = if (searchMs > 0) goto else 0, fail = if (searchMs > 0) fail else 0,
                )
                else -> placeholder("tipo $p não é importado")
            }
            if (built.validate(flat.size) != null) placeholder("valores fora dos limites: ${built.validate(flat.size)}") else built
        }
        return Result(Macro(id, name.take(60).ifBlank { "Importada" }, loops, steps.take(MAX_STEPS)), notes)
    }
}

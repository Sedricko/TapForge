package app.macro17

import org.json.JSONArray
import org.json.JSONObject

/** Ordem importa: o editor usa o índice do enum no menu de tipos. */
enum class Type { TAP, SWIPE, PINCH_IN, PINCH_OUT, WAIT, KEY, STATUS, GOTO, COUNTER, RESET, CALL, IMAGE, NODE, NUMBER, TEXT, STOP, CHANGE, GESTURE, GROUP }

const val MAX_STEPS = 5000

/** Pontos do gesto livre, guardados em Step.text como "x,y;x,y;...". */
fun gesturePoints(text: String): List<Pair<Int, Int>> = text.split(';').mapNotNull { p ->
    val q = p.split(','); val x = q.getOrNull(0)?.toIntOrNull(); val y = q.getOrNull(1)?.toIntOrNull()
    if (x != null && y != null) x to y else null
}
const val KEY_SCREENSHOT = 9   // GLOBAL_ACTION_TAKE_SCREENSHOT
const val MAX_MS = 60_000      // getMaxGestureDuration() do sistema é 60 s
const val MAX_DELAY_MS = 300_000 // atraso, espera e pesquisa: o base aceita até 300 s
const val MAX_COORD = 20_000
const val MAX_LOOPS = 1_000_000
const val MAX_CASES = 10       // como no app de referência
const val MAX_COUNTERS = 10

/** Caso de um contador: ao alcançar [n] execuções, salta para o passo [to] (1-based; 0 = seguir). */
data class Case(val n: Int, val to: Int)

/**
 * Passo de uma macro. Alvos de salto são números de passo 1-based; 0 significa "seguir para o próximo".
 * KEY usa x1 = GLOBAL_ACTION_*: 1 voltar, 2 início, 3 recentes, 4 notificações.
 */
data class Step(
    val type: Type,
    val x1: Int = 0, val y1: Int = 0, val x2: Int = 0, val y2: Int = 0,
    val ms: Int = 0,        // TAP: tempo pressionado; gestos: duração; WAIT: espera; STATUS: tempo da mensagem
    val delay: Int = 0,     // atraso ANTES da ação (como no app de referência)
    val rnd: Int = 0,       // > 0 liga a faixa aleatória [rndMin, rnd] e IGNORA o atraso fixo
    val rndMin: Int = 0,
    val jitter: Int = 0,    // raio em px; um único (dx,dy) sorteado desloca o gesto inteiro
    val skip: Boolean = false, // só o atraso roda; gestos, tecla, alerta e chamada são ignorados
    val goto: Int = 0,      // GOTO: destino; COUNTER: destino padrão
    val counter: Int = 1,   // COUNTER e RESET: 1..MAX_COUNTERS
    val cases: List<Case> = emptyList(),
    val text: String = "",  // STATUS: mensagem; CALL: id da macro chamada
    val ret: Boolean = true, // CALL: true empilha e volta ao chamador; false substitui a macro atual
    // IMAGE: text = id do recorte; x1..y2 = área de busca (tudo 0 = tela toda); ms = tempo máximo; goto = ao achar.
    val thr: Int = 90,       // semelhança mínima, em %
    val cycle: Int = 200,    // intervalo entre tentativas, em ms
    val fail: Int = 0,       // IMAGE: passo ao não achar no prazo (0 = seguir)
    val tap: Boolean = true, // IMAGE e NODE: tocar no centro do que achou
    // NODE: text = texto do elemento (ou "id:nome" para o id de recurso); ms = prazo; cycle = intervalo; goto/fail como no IMAGE.
    val exact: Boolean = false, // NODE: texto inteiro igual (sem diferenciar maiúsculas); false = contém
    val scales: Boolean = false, // IMAGE: tentar também escalas próximas (mais lento)
    // NUMBER: x1..y2 = área onde está o número (obrigatória); cmp: 0 ≥, 1 ≤, 2 =, 3 entre; lo = valor (ou mínimo); hi = máximo.
    val cmp: Int = 0,
    val lo: Int = 0,
    val hi: Int = 0,
    // IMAGE: method 0 = o do app de referência (correlação normalizada, tolera brilho e contraste); 1 = diferença de pixels (exige a tela idêntica).
    // mono (só no método 1) compara em cinza; confirm = quadros seguidos no mesmo lugar antes de agir (1 = como o original).
    val method: Int = 0,
    val mono: Boolean = false,
    val confirm: Int = 1,
    // Do manual oficial do base: clique duplo; deslocamento do toque em relação ao que foi achado (px, pode ser negativo);
    // alerta com vibração e som; confiança mínima do OCR (0 = sem filtro).
    val dbl: Boolean = false,
    val ox: Int = 0, val oy: Int = 0,
    val vib: Boolean = false, val snd: Boolean = false,
    val minConf: Int = 0,
    val img: String = "",   // id do recorte de 80 px ao redor do ponto gravado (miniatura e, com useImg, a imagem buscada)
    // Toque/deslizar com "Correspondência de imagem" (como o item do base): busca [img] antes do atraso e age RELATIVO ao achado.
    // sms = tempo máximo da busca (0 = sem limite); a área de busca é ax1..ay2 (tudo 0 = tela toda); thr, cycle, goto e fail valem aqui também.
    val useImg: Boolean = false,
    val sms: Int = 5000,
    val ax1: Int = 0, val ay1: Int = 0, val ax2: Int = 0, val ay2: Int = 0,
    val name: String = "", // nome do item (campo "Nome" do base); vazio = usa o nome do tipo
    val fx: Boolean = false, // gestos com imagem: procura só em volta da posição gravada ("Posição fixa" da Área de Pesquisa)
    val grp: Int = 0, // GROUP: quantos passos SEGUINTES pertencem ao grupo
    val rep: Int = 1, // GROUP: repetições do grupo (0 = infinito)
    val off: Boolean = false, // item desabilitado ("Alternar Pular" do base): o executor passa direto, sem atraso nem ação
) {
    /** [size] é o total de passos da macro, para validar os alvos de salto. */
    fun validate(size: Int): String? = when {
        listOf(x1, y1, x2, y2).any { it !in 0..MAX_COORD } -> "coordenada fora de 0..$MAX_COORD"
        listOf(delay, rnd, rndMin).any { it !in 0..MAX_DELAY_MS } || ms !in 0..(if (type in GESTURES || type == Type.GESTURE) MAX_MS else MAX_DELAY_MS) -> "tempo fora do limite (atraso até $MAX_DELAY_MS ms; duração de gesto até $MAX_MS ms)"
        rnd > 0 && rndMin > rnd -> "faixa aleatória: mínimo maior que o máximo"
        jitter !in 0..500 -> "jitter fora de 0..500 px"
        type == Type.KEY && x1 !in 1..4 && x1 != KEY_SCREENSHOT -> "tecla deve ser 1 a 4 ou $KEY_SCREENSHOT (captura de tela)"
        type in GESTURES && ms < 1 -> "duração mínima de 1 ms"
        (type == Type.PINCH_IN || type == Type.PINCH_OUT) && x1 == x2 && y1 == y2 -> "a pinça precisa de dois pontos diferentes"
        type == Type.STATUS && (text.isBlank() || text.length > 200) -> "mensagem vazia ou maior que 200 caracteres"
        (ox !in -MAX_COORD..MAX_COORD || oy !in -MAX_COORD..MAX_COORD) -> "deslocamento do toque fora de ±$MAX_COORD px"
        type == Type.TEXT && minConf !in 0..100 -> "confiança mínima deve ser de 0 a 100%"
        // Como no base: tempo máximo 0 = busca sem limite, e então não existe "falhou"; os saltos "se achar / se não achar" exigem tempo > 0.
        type in SEARCHES && ms == 0 && (goto != 0 || fail != 0) -> "com tempo máximo 0 (sem limite) não há “se achar / se não achar”: use um tempo maior que 0 ou zere os destinos"
        type in GESTURES && useImg && img.isBlank() -> "correspondência de imagem sem imagem: grave o item pelo painel"
        type in GESTURES && useImg && thr !in 20..100 -> "semelhança deve ser de 20 a 100%"
        type in GESTURES && useImg && (cycle !in 50..MAX_MS || sms !in 0..MAX_MS || (sms != 0 && sms < 100)) -> "intervalo de 50 ms a $MAX_MS e tempo da busca 0 (sem limite) ou de 100 ms a $MAX_MS"
        type in GESTURES && useImg && !(listOf(ax1, ay1, ax2, ay2).all { it == 0 } || (ax2 > ax1 && ay2 > ay1)) -> "área de busca inválida: use tudo 0 ou X/Y finais maiores que os iniciais"
        type in GESTURES && useImg && (goto !in 0..size || fail !in 0..size) -> "destinos fora de 0..$size"
        type in GESTURES && useImg && sms == 0 && (goto != 0 || fail != 0) -> "com tempo da busca 0 (sem limite) não há “se achar / se não achar”"
        name.length > 60 || name.contains('\n') -> "nome do item com mais de 60 caracteres ou com quebra de linha"
        type == Type.GOTO && goto !in 0..size -> "destino fora de 0..$size (0 = próximo)"
        type == Type.COUNTER && !validCounter() -> "contador deve ser 1 a $MAX_COUNTERS"
        type == Type.RESET && !validCounter() -> "contador deve ser 1 a $MAX_COUNTERS"
        type == Type.COUNTER && goto !in 0..size -> "destino padrão fora de 0..$size"
        type == Type.COUNTER && (cases.size > MAX_CASES || cases.any { it.n !in 1..MAX_LOOPS || it.to !in 0..size }) ->
            "casos: até $MAX_CASES, com limiar de 1 a $MAX_LOOPS e destino de 0 a $size"
        type == Type.CHANGE && (thr !in 1..100 || cycle !in 50..MAX_MS || (ms != 0 && ms < 100) || !(listOf(x1, y1, x2, y2).all { it == 0 } || (x2 > x1 && y2 > y1))) -> "mudança: sensibilidade 1 a 100%, intervalo 50 ms a $MAX_MS, tempo 0 ou ≥ 100 ms e área válida"
        type == Type.CHANGE && (goto !in 0..size || fail !in 0..size) -> "destinos fora de 0..$size"
        type == Type.GESTURE && (gesturePoints(text).size !in 2..400 || ms !in 1..MAX_MS || gesturePoints(text).any { it.first !in 0..MAX_COORD || it.second !in 0..MAX_COORD }) -> "gesto: 2 a 400 pontos dentro da tela e duração de 1 ms a ${MAX_MS / 1000} s"
        type == Type.GROUP && (grp !in 0..size || rep !in 0..MAX_LOOPS) -> "grupo: itens e repetições fora dos limites"
        type == Type.CALL && text.length > 64 -> "id de macro inválido" // vazio é permitido (o base cria "Arquivo de macro" sem arquivo); o executor ignora
        type == Type.IMAGE && text.isBlank() -> "passo sem imagem: capture pelo orbe"
        type == Type.IMAGE && thr !in 20..100 -> "semelhança deve ser de 20 a 100%"
        type == Type.IMAGE && confirm !in 1..5 -> "confirmações seguidas devem ser de 1 a 5"
        type == Type.IMAGE && method !in 0..1 -> "método deve ser 0 (correlação, como o app original) ou 1 (diferença de pixels)"
        type == Type.IMAGE && (cycle !in 50..MAX_MS || (ms != 0 && ms < 100)) -> "intervalo de 50 ms a $MAX_MS e tempo máximo de 0 (sem limite) ou pelo menos 100 ms"
        type == Type.IMAGE && !(listOf(x1, y1, x2, y2).all { it == 0 } || (x2 > x1 && y2 > y1)) -> "área de busca inválida: use tudo 0 ou X/Y finais maiores que os iniciais"
        type == Type.IMAGE && (goto !in 0..size || fail !in 0..size) -> "destinos fora de 0..$size"
        type == Type.NODE && (text.isBlank() || text.length > 200) -> "texto do elemento vazio ou maior que 200 caracteres"
        type == Type.NODE && (cycle !in 50..MAX_MS || (ms != 0 && ms < 100)) -> "intervalo de 50 ms a $MAX_MS e tempo máximo de 0 (sem limite) ou pelo menos 100 ms"
        type == Type.NODE && (goto !in 0..size || fail !in 0..size) -> "destinos fora de 0..$size"
        type == Type.TEXT && (text.isBlank() || text.length > 200) -> "texto vazio ou maior que 200 caracteres"
        type == Type.TEXT && !(x1 == 0 && y1 == 0 && x2 == 0 && y2 == 0) && !(x2 > x1 && y2 > y1) -> "área inválida: deixe tudo 0 (tela toda) ou X e Y finais maiores que os iniciais"
        type == Type.TEXT && (cycle !in 200..MAX_MS || (ms != 0 && ms < 100)) -> "intervalo de 200 ms a $MAX_MS (o reconhecimento é pesado) e tempo máximo de 0 (sem limite) ou pelo menos 100 ms"
        type == Type.TEXT && (goto !in 0..size || fail !in 0..size) -> "destinos fora de 0..$size"
        type == Type.NUMBER && !(x2 > x1 && y2 > y1) -> "área do número inválida: X e Y finais devem ser maiores que os iniciais"
        type == Type.NUMBER && cmp !in 0..3 -> "condição deve ser 0 (≥), 1 (≤), 2 (=) ou 3 (entre)"
        type == Type.NUMBER && (lo < 0 || hi < 0 || (cmp == 3 && hi < lo)) -> "valores não podem ser negativos; em 'entre' o máximo não pode ser menor que o mínimo"
        type == Type.NUMBER && (cycle !in 50..MAX_MS || (ms != 0 && ms < 100)) -> "intervalo de 50 ms a $MAX_MS e tempo máximo de 0 (sem limite) ou pelo menos 100 ms"
        type == Type.NUMBER && (goto !in 0..size || fail !in 0..size) -> "destinos fora de 0..$size"
        else -> null
    }

    private fun validCounter() = counter in 1..MAX_COUNTERS

    fun toJson() = JSONObject().put("t", type.name).put("x1", x1).put("y1", y1).put("x2", x2).put("y2", y2)
        .put("ms", ms).put("d", delay).put("r", rnd).put("rm", rndMin).put("j", jitter).put("sk", skip)
        .put("g", goto).put("c", counter).put("tx", text).put("rt", ret)
        .put("th", thr).put("cy", cycle).put("fl", fail).put("tp", tap).put("ex", exact).put("sl", scales).put("cm", cmp).put("lo", lo).put("hi", hi).put("mt", method).put("mo", mono).put("cf", confirm)
        .put("db", dbl).put("ox", ox).put("oy", oy).put("vb", vib).put("sn", snd).put("mc", minConf).put("im", img)
        .put("ui", useImg).put("sm", sms).put("a1", ax1).put("b1", ay1).put("a2", ax2).put("b2", ay2).put("nm", name).put("of", off).put("fx", fx).put("gp", grp).put("rp", rep)
        .put("cs", JSONArray().also { a -> cases.forEach { a.put(JSONObject().put("n", it.n).put("to", it.to)) } })

    companion object {
        val GESTURES = setOf(Type.TAP, Type.SWIPE, Type.PINCH_IN, Type.PINCH_OUT)
        val SEARCHES = setOf(Type.IMAGE, Type.NODE, Type.TEXT, Type.NUMBER, Type.CHANGE)

        // valueOf lança IllegalArgumentException para tipo desconhecido; o chamador trata.
        fun fromJson(o: JSONObject): Step {
            val cs = o.optJSONArray("cs")
            return Step(
                Type.valueOf(o.getString("t")), o.optInt("x1"), o.optInt("y1"), o.optInt("x2"), o.optInt("y2"),
                o.optInt("ms"), o.optInt("d"), o.optInt("r"), o.optInt("rm"), o.optInt("j"), o.optBoolean("sk"),
                o.optInt("g"), o.optInt("c", 1),
                List(minOf(cs?.length() ?: 0, MAX_CASES)) { Case(cs!!.getJSONObject(it).optInt("n"), cs.getJSONObject(it).optInt("to")) },
                o.optString("tx"), o.optBoolean("rt", true),
                o.optInt("th", 90), o.optInt("cy", 200), o.optInt("fl"), o.optBoolean("tp", true), o.optBoolean("ex"), o.optBoolean("sl"), o.optInt("cm"), o.optInt("lo"), o.optInt("hi"), o.optInt("mt"), o.optBoolean("mo"), o.optInt("cf", 1),
                o.optBoolean("db"), o.optInt("ox"), o.optInt("oy"), o.optBoolean("vb"), o.optBoolean("sn"), o.optInt("mc"), o.optString("im"),
                o.optBoolean("ui"), o.optInt("sm", 5000), o.optInt("a1"), o.optInt("b1"), o.optInt("a2"), o.optInt("b2"), o.optString("nm"), o.optBoolean("fx"), o.optInt("gp"), o.optInt("rp", 1), o.optBoolean("of"),
            )
        }
    }
}

data class Macro(val id: String, val name: String, val loops: Int, val steps: List<Step>) {
    fun validate(): String? {
        if (name.isBlank() || name.length > 60) return "nome vazio ou maior que 60 caracteres"
        if (loops !in 0..MAX_LOOPS) return "repetições fora de 0..$MAX_LOOPS (0 = infinito)"
        if (steps.size > MAX_STEPS) return "mais de $MAX_STEPS passos"
        steps.forEachIndexed { i, s -> s.validate(steps.size)?.let { return "passo ${i + 1}: $it" } }
        return null
    }

    fun toJson() = JSONObject().put("v", 2).put("name", name).put("loops", loops)
        .put("steps", JSONArray().also { a -> steps.forEach { a.put(it.toJson()) } })

    companion object {
        /** O id nunca vem do arquivo: evita colisão e nome de arquivo malicioso na importação. */
        fun fromJson(o: JSONObject, id: String): Macro {
            val a = o.getJSONArray("steps")
            require(a.length() <= MAX_STEPS) { "mais de $MAX_STEPS passos" }
            return Macro(id, o.getString("name"), o.optInt("loops", 1), List(a.length()) { Step.fromJson(a.getJSONObject(it)) })
        }
    }
}

// --- edição da lista: os alvos de salto acompanham os passos (renumeração) ---

private fun Step.mapTargets(f: (Int) -> Int) = copy(goto = f(goto), fail = f(fail), cases = cases.map { it.copy(to = f(it.to)) })

/** Remove o passo [i] (0-based). Saltos para ele viram "seguir"; os seguintes descem um número. */
fun List<Step>.afterDelete(i: Int): List<Step> {
    val gone = i + 1
    // Grupo que continha o passo removido encolhe um item.
    val shrunk = mapIndexed { k, x -> if (x.type == Type.GROUP && i > k && i <= k + x.grp) x.copy(grp = x.grp - 1) else x }
    return shrunk.filterIndexed { k, _ -> k != i }.map { s -> s.mapTargets { t -> if (t == gone) 0 else if (t > gone) t - 1 else t } }
}

/** Troca os passos [i] e [j] (0-based); os saltos continuam apontando para o mesmo passo. */
fun List<Step>.afterSwap(i: Int, j: Int): List<Step> {
    val a = i + 1
    val b = j + 1
    val swapped = toMutableList().also { it[i] = this[j]; it[j] = this[i] }
    return swapped.map { s -> s.mapTargets { t -> if (t == a) b else if (t == b) a else t } }
}

/**
 * Insere [s] na posição [at] (0-based); saltos dos passos EXISTENTES para dali em diante sobem um número.
 * [s] entra como veio: um passo novo no fim que salta para si mesmo (laço) tem de continuar válido.
 * Para uma cópia (duplicar) use [Step.shiftedFrom].
 */
fun List<Step>.afterInsert(at: Int, s: Step): List<Step> {
    val pos = at + 1
    val shift = { t: Int -> if (t >= pos) t + 1 else t }
    // Grupo que contém a posição de inserção cresce um item.
    val grown = mapIndexed { k, x -> if (x.type == Type.GROUP && at > k && at <= k + x.grp) x.copy(grp = x.grp + 1) else x }
    return grown.map { x -> x.mapTargets(shift) }.toMutableList().also { it.add(at, s) }
}

/** Cópia de um passo para ser inserida em [at] (0-based): seus alvos acompanham a renumeração das demais. */
fun Step.shiftedFrom(at: Int): Step = mapTargets { t -> if (t >= at + 1) t + 1 else t }

package app.macro17

import org.json.JSONArray
import org.json.JSONObject

/** Ordem importa: o editor usa o índice do enum no menu de tipos. */
enum class Type { TAP, SWIPE, PINCH_IN, PINCH_OUT, WAIT, KEY, STATUS, GOTO, COUNTER, RESET, CALL, IMAGE, NODE }

const val MAX_STEPS = 5000
const val MAX_MS = 60_000      // getMaxGestureDuration() do sistema é 60 s
const val MAX_COORD = 20_000
const val MAX_LOOPS = 1_000_000
const val MAX_CASES = 5        // o app de referência aceita 10; 5 cobre o uso e cabe na tela do editor
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
) {
    /** [size] é o total de passos da macro, para validar os alvos de salto. */
    fun validate(size: Int): String? = when {
        listOf(x1, y1, x2, y2).any { it !in 0..MAX_COORD } -> "coordenada fora de 0..$MAX_COORD"
        listOf(ms, delay, rnd, rndMin).any { it !in 0..MAX_MS } -> "tempo fora de 0..$MAX_MS ms"
        rnd > 0 && rndMin > rnd -> "faixa aleatória: mínimo maior que o máximo"
        jitter !in 0..500 -> "jitter fora de 0..500 px"
        type == Type.KEY && x1 !in 1..4 -> "tecla deve ser 1 a 4"
        type in GESTURES && ms < 1 -> "duração mínima de 1 ms"
        (type == Type.PINCH_IN || type == Type.PINCH_OUT) && x1 == x2 && y1 == y2 -> "a pinça precisa de dois pontos diferentes"
        type == Type.STATUS && (text.isBlank() || text.length > 200) -> "mensagem vazia ou maior que 200 caracteres"
        type == Type.GOTO && goto !in 1..size -> "escolha o passo de destino (1 a $size)"
        type == Type.COUNTER && !validCounter() -> "contador deve ser 1 a $MAX_COUNTERS"
        type == Type.RESET && !validCounter() -> "contador deve ser 1 a $MAX_COUNTERS"
        type == Type.COUNTER && goto !in 0..size -> "destino padrão fora de 0..$size"
        type == Type.COUNTER && (cases.size > MAX_CASES || cases.any { it.n !in 1..MAX_LOOPS || it.to !in 0..size }) ->
            "casos: até $MAX_CASES, com limiar de 1 a $MAX_LOOPS e destino de 0 a $size"
        type == Type.CALL && (text.isBlank() || text.length > 64) -> "escolha a macro a chamar"
        type == Type.IMAGE && text.isBlank() -> "passo sem imagem: capture pelo orbe"
        type == Type.IMAGE && thr !in 20..100 -> "semelhança deve ser de 20 a 100%"
        type == Type.IMAGE && (cycle !in 50..MAX_MS || ms < 100) -> "intervalo de 50 ms a $MAX_MS e tempo máximo de pelo menos 100 ms"
        type == Type.IMAGE && !(listOf(x1, y1, x2, y2).all { it == 0 } || (x2 > x1 && y2 > y1)) -> "área de busca inválida: use tudo 0 ou X/Y finais maiores que os iniciais"
        type == Type.IMAGE && (goto !in 0..size || fail !in 0..size) -> "destinos fora de 0..$size"
        type == Type.NODE && (text.isBlank() || text.length > 200) -> "texto do elemento vazio ou maior que 200 caracteres"
        type == Type.NODE && (cycle !in 50..MAX_MS || ms < 100) -> "intervalo de 50 ms a $MAX_MS e tempo máximo de pelo menos 100 ms"
        type == Type.NODE && (goto !in 0..size || fail !in 0..size) -> "destinos fora de 0..$size"
        else -> null
    }

    private fun validCounter() = counter in 1..MAX_COUNTERS

    fun toJson() = JSONObject().put("t", type.name).put("x1", x1).put("y1", y1).put("x2", x2).put("y2", y2)
        .put("ms", ms).put("d", delay).put("r", rnd).put("rm", rndMin).put("j", jitter).put("sk", skip)
        .put("g", goto).put("c", counter).put("tx", text).put("rt", ret)
        .put("th", thr).put("cy", cycle).put("fl", fail).put("tp", tap).put("ex", exact)
        .put("cs", JSONArray().also { a -> cases.forEach { a.put(JSONObject().put("n", it.n).put("to", it.to)) } })

    companion object {
        val GESTURES = setOf(Type.TAP, Type.SWIPE, Type.PINCH_IN, Type.PINCH_OUT)

        // valueOf lança IllegalArgumentException para tipo desconhecido; o chamador trata.
        fun fromJson(o: JSONObject): Step {
            val cs = o.optJSONArray("cs")
            return Step(
                Type.valueOf(o.getString("t")), o.optInt("x1"), o.optInt("y1"), o.optInt("x2"), o.optInt("y2"),
                o.optInt("ms"), o.optInt("d"), o.optInt("r"), o.optInt("rm"), o.optInt("j"), o.optBoolean("sk"),
                o.optInt("g"), o.optInt("c", 1),
                List(minOf(cs?.length() ?: 0, MAX_CASES)) { Case(cs!!.getJSONObject(it).optInt("n"), cs.getJSONObject(it).optInt("to")) },
                o.optString("tx"), o.optBoolean("rt", true),
                o.optInt("th", 90), o.optInt("cy", 200), o.optInt("fl"), o.optBoolean("tp", true), o.optBoolean("ex"),
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
    return filterIndexed { k, _ -> k != i }.map { s -> s.mapTargets { t -> if (t == gone) 0 else if (t > gone) t - 1 else t } }
}

/** Troca os passos [i] e [j] (0-based); os saltos continuam apontando para o mesmo passo. */
fun List<Step>.afterSwap(i: Int, j: Int): List<Step> {
    val a = i + 1
    val b = j + 1
    val swapped = toMutableList().also { it[i] = this[j]; it[j] = this[i] }
    return swapped.map { s -> s.mapTargets { t -> if (t == a) b else if (t == b) a else t } }
}

/** Insere [s] na posição [at] (0-based); saltos para passos dali em diante sobem um número. */
fun List<Step>.afterInsert(at: Int, s: Step): List<Step> {
    val pos = at + 1
    return toMutableList().also { it.add(at, s) }.map { x -> x.mapTargets { t -> if (t >= pos) t + 1 else t } }
}

package app.macro17

import org.json.JSONArray
import org.json.JSONObject

enum class Type { TAP, SWIPE, WAIT, KEY }

const val MAX_STEPS = 5000
const val MAX_MS = 60_000      // getMaxGestureDuration() do sistema é 60 s
const val MAX_COORD = 20_000
const val MAX_LOOPS = 1_000_000

/** KEY usa x1 = GLOBAL_ACTION_*: 1 voltar, 2 início, 3 recentes, 4 notificações. */
data class Step(
    val type: Type,
    val x1: Int = 0, val y1: Int = 0, val x2: Int = 0, val y2: Int = 0,
    val ms: Int = 0,      // TAP: tempo pressionado; SWIPE: duração; WAIT: espera
    val delay: Int = 0,   // pausa fixa depois do passo
    val rnd: Int = 0,     // pausa extra aleatória, 0..rnd ms
    val jitter: Int = 0,  // raio em px sorteado a cada execução
) {
    fun validate(): String? = when {
        listOf(x1, y1, x2, y2).any { it !in 0..MAX_COORD } -> "coordenada fora de 0..$MAX_COORD"
        listOf(ms, delay, rnd).any { it !in 0..MAX_MS } -> "tempo fora de 0..$MAX_MS ms"
        jitter !in 0..500 -> "jitter fora de 0..500 px"
        type == Type.KEY && x1 !in 1..4 -> "tecla deve ser 1 a 4"
        (type == Type.TAP || type == Type.SWIPE) && ms < 1 -> "duração mínima de 1 ms"
        else -> null
    }

    fun toJson() = JSONObject().put("t", type.name).put("x1", x1).put("y1", y1).put("x2", x2).put("y2", y2)
        .put("ms", ms).put("d", delay).put("r", rnd).put("j", jitter)

    companion object {
        // valueOf lança IllegalArgumentException para tipo desconhecido; o chamador trata.
        fun fromJson(o: JSONObject) = Step(
            Type.valueOf(o.getString("t")), o.optInt("x1"), o.optInt("y1"), o.optInt("x2"), o.optInt("y2"),
            o.optInt("ms"), o.optInt("d"), o.optInt("r"), o.optInt("j"),
        )
    }
}

data class Macro(val id: String, val name: String, val loops: Int, val steps: List<Step>) {
    fun validate(): String? {
        if (name.isBlank() || name.length > 60) return "nome vazio ou maior que 60 caracteres"
        if (loops !in 0..MAX_LOOPS) return "repetições fora de 0..$MAX_LOOPS (0 = infinito)"
        if (steps.size > MAX_STEPS) return "mais de $MAX_STEPS passos"
        steps.forEachIndexed { i, s -> s.validate()?.let { return "passo ${i + 1}: $it" } }
        return null
    }

    fun toJson() = JSONObject().put("v", 1).put("name", name).put("loops", loops)
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

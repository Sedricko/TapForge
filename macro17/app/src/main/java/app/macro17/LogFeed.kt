package app.macro17

/** Registro ao vivo: entradas em memória (últimas [LogFeed] cap), classificadas por categoria, nível e cor. Java/Kotlin puro. */
enum class Level { INFO, WARN, ERROR }
enum class Tone { PLAIN, WARN, ERROR, VALUE, SYSTEM }
enum class Cat(val label: String) { ALL("Tudo"), STEPS("Passos"), SEARCH("Busca"), GESTURES("Gestos"), SYSTEM("Sistema"), WARNINGS("Avisos"), ERRORS("Erros") }

data class Entry(val time: Long, val tag: String, val text: String, val level: Level)

object LogRules {
    private val systemTags = setOf("serviço", "sistema", "tela", "memória", "alarme", "app", "gravação")
    private val resultWords = listOf(" imagem:", " texto:", " elemento:", " número:")

    fun level(tag: String, text: String): Level = when {
        text.startsWith("ERRO") || tag == "FATAL" -> Level.ERROR
        text.contains("não achou") || text.contains("não achada") || text.contains("não satisfeita") -> Level.WARN
        else -> Level.INFO
    }

    fun matches(c: Cat, e: Entry): Boolean = when (c) {
        Cat.ALL -> true
        Cat.STEPS -> e.tag == "passo" || e.tag == "run" || e.tag == "alerta"
        Cat.SEARCH -> e.tag == "visão" || e.tag == "captura" || e.tag == "aviso" || (e.tag == "passo" && resultWords.any { e.text.contains(it) })
        Cat.GESTURES -> e.tag == "gesto"
        Cat.SYSTEM -> e.tag in systemTags
        Cat.WARNINGS -> e.level == Level.WARN
        Cat.ERRORS -> e.level == Level.ERROR
    }

    fun tone(e: Entry): Tone = when {
        e.level == Level.ERROR -> Tone.ERROR
        e.level == Level.WARN -> Tone.WARN
        e.tag == "passo" && resultWords.any { e.text.contains(it) } -> Tone.VALUE
        e.tag in systemTags -> Tone.SYSTEM
        else -> Tone.PLAIN
    }
}

/** Uma ou mais entradas seguidas idênticas (mesma tag e texto) viram uma linha com [count]; [entry] é a mais recente. */
data class Row(val entry: Entry, val count: Int)

/** [es] mais recente primeiro. Só junta vizinhas: a ordem dos fatos não muda, o ruído repetido some. */
fun collapse(es: List<Entry>): List<Row> {
    val out = ArrayList<Row>()
    for (e in es) {
        val last = out.lastOrNull()
        if (last != null && last.entry.tag == e.tag && last.entry.text == e.text) out[out.lastIndex] = last.copy(count = last.count + 1)
        else out += Row(e, 1)
    }
    return out
}

class LogFeed(private val cap: Int = 500) {
    private val items = ArrayDeque<Entry>()
    private val listeners = java.util.concurrent.CopyOnWriteArraySet<() -> Unit>()

    /** Chamados de qualquer thread; quem recebe agenda a atualização na sua própria thread. Guarde a mesma referência para remover. */
    fun addListener(l: () -> Unit) { listeners.add(l) }
    fun removeListener(l: () -> Unit) { listeners.remove(l) }
    private fun fire() = listeners.forEach { it() }

    fun add(e: Entry) {
        synchronized(this) { items.addLast(e); while (items.size > cap) items.removeFirst() }
        fire()
    }

    /** Mais recente primeiro, no máximo [limit]. */
    fun snapshot(c: Cat, limit: Int = 200): List<Entry> =
        synchronized(this) { items.filter { LogRules.matches(c, it) }.takeLast(limit).asReversed() }

    fun count(c: Cat): Int = synchronized(this) { items.count { LogRules.matches(c, it) } }

    fun clear() { synchronized(this) { items.clear() }; fire() }
}

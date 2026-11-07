package app.macro17

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Monta o texto do relatório a partir das linhas do arquivo de log ("MM-dd HH:mm:ss.SSS [thread] tag mensagem").
 * Kotlin puro (sem Android): testável na JVM. O arquivo cru continua inteiro no "Arquivo do log" e no ZIP de "Enviar tudo".
 *
 * Saída: resumo no topo, uma seção por execução de macro (repetições colapsadas, mensagens em linguagem clara),
 * uso do app (toques e telas, agrupado) e os demais eventos. O que não for execução nem uso do app vai para "outros".
 */
object ReportBuilder {
    data class Ev(val time: String, val thread: String, val tag: String, val msg: String) {
        val clock: String get() = if (time.length >= 14) time.substring(6, 14) else time
        val stamp: String get() = if (time.length >= 14) time.substring(0, 14) else time
        val isError: Boolean get() = msg.startsWith("ERRO") || tag == "FATAL"
    }

    class Parts(val summary: String, val runs: String, val usage: String, val other: String) {
        fun sections(): List<Pair<String, String>> = listOf(
            "Execuções" to runs, "Uso do app (toques e telas)" to usage, "Outros eventos do app" to other,
        )
    }

    private class Flag(var on: Boolean = false)

    private class Group(val n: Int, val total: Int, val type: String, val skip: Boolean, val delay: String, val first: String) {
        var last: String = first
        var count = 1
        val results = ArrayList<String>()
        val gestos = ArrayList<String>()
        val avisos = ArrayList<String>()
        val notes = ArrayList<String>()
        val slowMs = ArrayList<Int>()
        var slowArea = ""
    }

    private class Run(val name: String, val steps: Int, val loops: String, val startClock: String) {
        var kind = "sem fim registrado"
        var detail = "sem fim registrado (o app pode ter sido fechado ou o log foi cortado)"
        var ms = -1L
        var endClock = ""
        var visits = 0
        var warns = 0
        var jumps = 0
        var imgFails = 0
        val groups = ArrayList<Group>()
        val errors = ArrayList<String>()
        val notes = ArrayList<String>()
        val heaps = ArrayList<Int>()
    }

    private val LINE = Regex("""^(\d\d-\d\d \d\d:\d\d:\d\d\.\d{3}) \[([^\]]*)\] (.*)$""")
    private val TWO_WORD_TAGS = listOf("início automático")
    private val START = Regex("""^início "(.*)" passos=(\d+) repetições=(\d+)$""")
    private val FIM = Regex("""^fim: (.*?)(?: \((\d+) ms\))?$""", RegexOption.DOT_MATCHES_ALL)
    private val STEP = Regex("""^passo (\d+)/(\d+) (\S+)(.*)$""")
    private val RES = Regex("""^passo (\d+) (.*)$""")
    private val DELAY = Regex("""atraso=(\d+)ms""")
    private val SLOW = Regex("""^casamento lento: (\d+) ms \((\w+)\) área (\d+x\d+) modelo (\d+x\d+)$""")
    private val JUMP = Regex("""^Passo (\d+) → Passo (\d+)$""")
    private val GESTO = Regex("""^(\S+)>(\S+) @(\d+)ms$""")
    private val HEAP = Regex("""^execução: heap (\d+) MB""")
    private val FOUND = Regex("""achou em (\d+),(\d+) semelhança (\d+)%""")
    private val TOUCH = Regex("""^(\S+) \((\d+),(\d+)\) → (.*)$""")
    private val MASK = Regex("""\d+""")

    private val ENGINE_TAGS = setOf("run", "passo", "aviso", "visão", "gesto")
    private val USAGE_TAGS = setOf("toque", "tela", "tecla", "janela", "painel")

    private const val SLOW_HINT = "dica: buscas de imagem acima de 250 ms; use Posição Fixa no passo para acelerar (a busca fica limitada à área escolhida)."

    // ---------------------------------------------------------------- leitura

    fun parseEvents(text: String): List<Ev> {
        val out = ArrayList<Ev>()
        for (l in text.lineSequence()) {
            val m = LINE.matchEntire(l)
            if (m != null) {
                val rest = m.groupValues[3]
                var tag = rest.substringBefore(' ')
                for (t in TWO_WORD_TAGS) if (rest == t || rest.startsWith("$t ")) tag = t
                out.add(Ev(m.groupValues[1], m.groupValues[2], tag, rest.substring(tag.length).trimStart()))
            } else if (l.startsWith("    ") && out.isNotEmpty()) {
                val last = out.removeAt(out.lastIndex)
                out.add(last.copy(msg = last.msg + "\n" + l.substring(4)))
            }
        }
        return out
    }

    // ---------------------------------------------------------------- tradução

    /** Mensagem do registro em linguagem clara. O que não é conhecido volta igual. */
    fun translate(tag: String, msg: String): String {
        val j = JUMP.matchEntire(msg)
        if (j != null) {
            val a = j.groupValues[1]; val b = j.groupValues[2]
            return if (a == b) "Passo $a salta para si mesmo (repete até a condição mudar)" else "Passo $a salta para o Passo $b"
        }
        if (tag == "visão") {
            val s = SLOW.matchEntire(msg)
            if (s != null) return "busca de imagem levou ${s.groupValues[1]} ms em área ${s.groupValues[3]} (modelo ${s.groupValues[4]})"
        }
        if (tag == "captura" && msg.startsWith("lenta: ")) return "captura de tela lenta: " + msg.removePrefix("lenta: ")
        if (tag == "aviso") {
            if (msg.contains("imagem do item não encontrada no aparelho; age sem comparar"))
                return msg.replace("imagem do item não encontrada no aparelho; age sem comparar", "a imagem guardada deste passo não está mais no aparelho; o passo agiu sem conferir a imagem")
            return msg.replace("imagem não achada.", "imagem não encontrada na tela.")
        }
        if (tag == "gesto") {
            val g = GESTO.matchEntire(msg)
            if (g != null) return if (g.groupValues[1] == g.groupValues[2]) "toque em ${g.groupValues[1]} (${g.groupValues[3]} ms)" else "deslize de ${g.groupValues[1]} até ${g.groupValues[2]} (${g.groupValues[3]} ms)"
        }
        if (tag == "passo") {
            val r = RES.matchEntire(msg)
            if (r != null) return "passo ${r.groupValues[1]} " + translateResult(r.groupValues[2])
        }
        return msg
    }

    private fun translateResult(r: String): String =
        r.replace(FOUND) { "achou em ${it.groupValues[1]},${it.groupValues[2]} (semelhança ${it.groupValues[3]}%)" }
            .replace("não achou", "NÃO encontrou")

    // ---------------------------------------------------------------- ruído do logcat

    private val NOISE_TAGS = setOf(
        "StrictMode", "ViewRootImpl", "InsetsSourceConsumer", "InsetsController", "ImeTracker", "ImeFocusController",
        "InputMethodManager", "InputMethodManagerUtils", "BLASTBufferQueue", "BLASTBufferQueue_Java", "BufferQueueProducer",
        "InputTransport", "SurfaceComposerClient", "Surface", "HWUI", "perf_hint", "NativeCustomFrequencyManager",
        "WindowOnBackDispatcher", "ScrollView", "AbsListView", "BBA2", "Kumiho-Kumiho", "DecorView", "WindowManager",
    )
    private val SPACES = Regex("\\s+")

    /**
     * Tira do logcat (formato threadtime) as linhas de ruído do sistema pela tag. Fatais (F) e linhas com "macro17" ficam,
     * exceto a pilha do StrictMode (o StrictMode do próprio app gera milhares de linhas). Avisos e erros de outras tags ficam.
     */
    fun filterNoise(raw: String): String = raw.lineSequence().filter { line ->
        val p = SPACES.split(line.trim(), 6)
        if (p.size < 6 || p[4].length != 1) return@filter true // cabeçalho de buffer, continuação, etc.
        val tag = p[5].substringBefore(':').trim()
        when {
            tag == "StrictMode" -> false
            p[4] == "F" -> true
            line.contains("macro17") -> true
            else -> !(tag in NOISE_TAGS || tag.startsWith("VRI["))
        }
    }.joinToString("\n")

    // ---------------------------------------------------------------- análise

    fun analyze(text: String, appPackage: String = "app.macro17"): Parts {
        val evs = parseEvents(text)
        val runs = ArrayList<Run>()
        val usage = ArrayList<Ev>()
        val other = ArrayList<Ev>()
        var run: Run? = null
        var cur: Group? = null
        for (e in evs) {
            val r = run
            if (e.tag == "run" && e.msg.startsWith("início")) {
                val m = START.matchEntire(e.msg)
                if (m != null) {
                    val nr = Run(m.groupValues[1], m.groupValues[2].toIntOrNull() ?: 0, m.groupValues[3], e.clock)
                    runs.add(nr); run = nr; cur = null
                } else other.add(e)
                continue
            }
            if (r != null && e.tag == "run" && e.msg.startsWith("fim:")) {
                finish(r, e); run = null; cur = null
                continue
            }
            val owned = r != null && (e.tag in ENGINE_TAGS || e.isError || e.tag == "captura" || e.tag == "alerta" || (e.tag == "memória" && e.msg.startsWith("execução")))
            if (r == null || !owned) {
                if (e.tag in USAGE_TAGS) usage.add(e) else other.add(e)
                continue
            }
            if (e.isError) { r.errors.add(e.clock + " " + limitLines(e.msg, 12)); continue }
            when (e.tag) {
                "passo" -> cur = onPassoEvent(r, cur, e)
                "aviso" -> {
                    if (JUMP.matches(e.msg)) r.jumps++ else r.warns++
                    val t = translate("aviso", e.msg)
                    if (cur != null) cur.avisos.add(t) else r.notes.add(t)
                }
                "visão" -> {
                    val s = SLOW.matchEntire(e.msg)
                    if (s != null && cur != null) { cur.slowMs.add(s.groupValues[1].toInt()); cur.slowArea = "área ${s.groupValues[3]} (modelo ${s.groupValues[4]})" }
                    else if (cur != null) cur.notes.add(translate("visão", e.msg)) else r.notes.add(translate("visão", e.msg))
                }
                "gesto" -> if (cur != null) cur.gestos.add(translate("gesto", e.msg)) else r.notes.add(translate("gesto", e.msg))
                "memória" -> HEAP.find(e.msg)?.let { r.heaps.add(it.groupValues[1].toInt()) }
                else -> {
                    val t = e.clock + " " + translate(e.tag, e.msg)
                    if (cur != null) cur.notes.add(t) else r.notes.add(t)
                }
            }
            r.endClock = e.clock
        }
        val hint = Flag()
        val runsText = if (runs.isEmpty()) "Nenhuma execução de macro registrada neste log." else {
            val sb = StringBuilder()
            runs.forEachIndexed { i, r -> renderRun(i + 1, r, sb, hint) }
            sb.toString().trimEnd()
        }
        return Parts(summary(evs, runs), runsText, usageText(usage, appPackage), otherText(other))
    }

    private fun onPassoEvent(r: Run, cur: Group?, e: Ev): Group? {
        val s = STEP.matchEntire(e.msg)
        if (s != null) {
            val n = s.groupValues[1].toInt(); val type = s.groupValues[3]
            r.visits++
            if (cur != null && cur.n == n && cur.type == type) { cur.count++; cur.last = e.clock; return cur }
            val rest = s.groupValues[4]
            val g = Group(n, s.groupValues[2].toInt(), type, rest.contains("(pular)"), DELAY.find(rest)?.groupValues?.get(1) ?: "", e.clock)
            r.groups.add(g)
            return g
        }
        val rs = RES.matchEntire(e.msg)
        if (rs != null && cur != null) {
            val raw = rs.groupValues[2]
            if (raw.startsWith("imagem") && raw.endsWith("não achou")) r.imgFails++
            cur.results.add(translateResult(raw))
        } else if (cur != null) cur.notes.add(e.msg) else r.notes.add(e.clock + " " + e.msg)
        return cur
    }

    private fun finish(r: Run, e: Ev) {
        val m = FIM.matchEntire(e.msg)
        val text = m?.groupValues?.get(1) ?: e.msg.removePrefix("fim:").trim()
        r.ms = m?.groupValues?.get(2)?.toLongOrNull() ?: -1L
        r.endClock = e.clock
        r.kind = when {
            text.startsWith("Macro concluída") -> "concluída"
            text.startsWith("Macro parada") -> "parada"
            text.startsWith("Erro") -> "erro"
            else -> text
        }
        r.detail = if (r.kind == "erro") text else r.kind
    }

    // ---------------------------------------------------------------- texto

    fun duration(ms: Long): String {
        if (ms < 0) return "tempo desconhecido"
        val s = ms / 1000
        return when {
            ms < 10_000 -> "${ms / 1000},${(ms % 1000) / 100} s"
            s < 60 -> "$s s"
            s < 3600 -> "${s / 60} min ${s % 60} s"
            else -> "${s / 3600} h ${(s % 3600) / 60} min"
        }
    }

    private fun limitLines(msg: String, max: Int): String {
        val ls = msg.lines()
        val shown = ls.take(max).joinToString("\n      ")
        return if (ls.size > max) shown + "\n      ... (+${ls.size - max} linhas; veja o arquivo do log)" else shown
    }

    private fun repeatedStep(r: Run): String {
        val perStep = LinkedHashMap<Int, Int>()
        for (g in r.groups) perStep[g.n] = (perStep[g.n] ?: 0) + g.count
        val top = perStep.entries.maxByOrNull { it.value } ?: return "nenhum"
        return if (top.value > 1) "passo ${top.key} (${top.value} vezes)" else "nenhum"
    }

    private fun summary(evs: List<Ev>, runs: List<Run>): String {
        val sb = StringBuilder()
        val errs = evs.count { it.isError }
        if (runs.isEmpty()) sb.append("Execuções de macro: nenhuma neste log.\n")
        else {
            val parts = ArrayList<String>()
            val ok = runs.count { it.kind == "concluída" }; val stopped = runs.count { it.kind == "parada" }
            val bad = runs.count { it.kind == "erro" }; val open = runs.size - ok - stopped - bad
            if (ok > 0) parts.add("$ok concluída(s)")
            if (stopped > 0) parts.add("$stopped parada(s)")
            if (bad > 0) parts.add("$bad com erro")
            if (open > 0) parts.add("$open sem fim registrado")
            sb.append("Execuções de macro: ${runs.size} (${parts.joinToString(", ")})\n")
        }
        sb.append(if (errs > 0) "Erros registrados: $errs (veja as linhas ERRO)\n" else "Erros registrados: nenhum\n")
        runs.forEachIndexed { i, r ->
            sb.append("\n${i + 1}. “${r.name}” · ${if (r.kind == "erro") r.detail else r.kind} · ${duration(r.ms)} · início ${r.startClock}\n")
            sb.append("   ${r.visits} execuções de passo (macro com ${r.steps} passos, ${r.loops} repetição(ões)) · ${r.warns} aviso(s) · ${r.jumps} salto(s) · ")
            sb.append("${r.imgFails} busca(s) de imagem sem sucesso\n")
            sb.append("   passo mais repetido: ${repeatedStep(r)}")
            if (r.errors.isNotEmpty()) sb.append(" · ${r.errors.size} erro(s) durante a execução")
            if (r.heaps.isNotEmpty()) sb.append("\n   memória do app durante a execução: ${r.heaps.min()} a ${r.heaps.max()} MB")
            sb.append('\n')
        }
        return sb.toString().trimEnd()
    }

    private fun tally(items: List<String>, max: Int = 3): String {
        val groups = LinkedHashMap<String, MutableList<String>>()
        for (s in items) groups.getOrPut(MASK.replace(s, "#")) { ArrayList() }.add(s)
        val parts = groups.values.take(max).map { v ->
            when {
                v.size == 1 -> v[0]
                v.toSet().size == 1 -> v[0] + " (×" + v.size + ")"
                else -> v[0] + " (×" + v.size + "; valores variam, exemplo do primeiro)"
            }
        }
        val extra = groups.size - max
        return parts.joinToString(" | ") + if (extra > 0) " | +$extra outras variações" else ""
    }

    private fun renderRun(idx: Int, r: Run, sb: StringBuilder, hint: Flag) {
        sb.append("--- Execução $idx: “${r.name}” · ${if (r.kind == "erro") r.detail else r.kind} · ${duration(r.ms)} ---\n")
        sb.append("início ${r.startClock} · ${r.steps} passos · ${r.loops} repetição(ões)\n")
        for (n in r.notes.distinct().take(10)) sb.append("  obs: ").append(n).append('\n')
        // shortcut: laços que alternam entre dois passos (8, 9, 8, 9...) não colapsam; só limito o tamanho e aponto o arquivo cru.
        if (r.groups.size > 400) {
            r.groups.take(150).forEach { renderGroup(it, sb, hint) }
            sb.append("  ... ${r.groups.size - 300} trechos omitidos (veja o arquivo do log) ...\n")
            r.groups.takeLast(150).forEach { renderGroup(it, sb, hint) }
        } else r.groups.forEach { renderGroup(it, sb, hint) }
        for (e in r.errors) sb.append("  ERRO ").append(e).append('\n')
        sb.append("fim ${r.endClock.ifEmpty { "?" }}: ${r.detail}")
        if (r.ms >= 0) sb.append(" (${duration(r.ms)})")
        sb.append("\n\n")
    }

    private fun renderGroup(g: Group, sb: StringBuilder, hint: Flag) {
        val skip = if (g.skip) " (pulado)" else ""
        val delay = if (g.delay.isNotEmpty()) " · atraso ${g.delay} ms" else ""
        if (g.count == 1) sb.append("  ${g.first}  passo ${g.n}/${g.total} ${g.type}$skip$delay\n")
        else sb.append("  passo ${g.n}/${g.total} ${g.type}$skip repetiu ${g.count} vezes, de ${g.first} a ${g.last}$delay\n")
        if (g.results.isNotEmpty()) sb.append("      resultado: ").append(tally(g.results)).append('\n')
        if (g.gestos.isNotEmpty()) sb.append("      gesto: ").append(tally(g.gestos)).append('\n')
        if (g.slowMs.isNotEmpty()) {
            val lo = g.slowMs.min(); val hi = g.slowMs.max()
            if (g.slowMs.size == 1) sb.append("      busca de imagem levou $lo ms em ${g.slowArea}\n")
            else sb.append("      ${g.slowMs.size} buscas de imagem lentas: de $lo a $hi ms (média ${g.slowMs.sum() / g.slowMs.size} ms) em ${g.slowArea}\n")
            if (!hint.on) { hint.on = true; sb.append("      ").append(SLOW_HINT).append('\n') }
        }
        if (g.avisos.isNotEmpty()) sb.append("      aviso: ").append(tally(g.avisos)).append('\n')
        for (n in g.notes.distinct().take(5)) sb.append("      obs: ").append(n).append('\n')
    }

    private fun usageText(usage: List<Ev>, appPackage: String): String {
        if (usage.isEmpty()) return "nenhum toque ou mudança de tela registrado."
        val sb = StringBuilder()
        val touches = usage.filter { it.tag == "toque" }
        val screens = ArrayList<Pair<String, String>>() // hora, tela (só quando muda)
        for (e in usage) if (e.tag == "tela" && e.msg.endsWith(" em primeiro plano")) {
            val name = e.msg.removeSuffix(" em primeiro plano")
            if (screens.lastOrNull()?.second != name) screens.add(e.clock to name)
        }
        val windows = usage.count { it.tag == "janela" }
        val panel = usage.filter { it.tag == "painel" }
        sb.append("${touches.size} toque(s), ${screens.size} mudança(s) de tela, $windows troca(s) de janela, ${panel.size} evento(s) do painel flutuante.\n")
        if (screens.isNotEmpty()) {
            sb.append("\nTelas, em ordem:\n")
            val shown = if (screens.size > 60) screens.takeLast(60) else screens
            if (screens.size > 60) sb.append("  ... (${screens.size - 60} anteriores omitidas)\n")
            shown.forEach { sb.append("  ").append(it.first).append("  ").append(it.second).append('\n') }
        }
        if (touches.isNotEmpty()) {
            sb.append("\nToques por tela:\n")
            val per = LinkedHashMap<String, MutableList<String>>()
            for (e in touches) {
                val m = TOUCH.matchEntire(e.msg)
                val scr = m?.groupValues?.get(1) ?: "?"
                per.getOrPut(scr) { ArrayList() }.add(m?.groupValues?.get(4) ?: e.msg)
            }
            for ((scr, ts) in per) {
                val top = ts.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(3).joinToString(", ") { "${it.key} ×${it.value}" }
                sb.append("  $scr: ${ts.size} toque(s); mais tocados: $top\n")
            }
        }
        val others = LinkedHashMap<String, Int>()
        for (e in usage) if (e.tag == "janela") {
            val pkg = e.msg.substringBefore('/')
            if (pkg != appPackage && pkg.isNotEmpty()) others[pkg] = (others[pkg] ?: 0) + 1
        }
        if (others.isNotEmpty()) {
            sb.append("\nJanelas de outros apps:\n")
            others.entries.sortedByDescending { it.value }.take(15).forEach { sb.append("  ${it.key} ×${it.value}\n") }
            if (others.size > 15) sb.append("  ... +${others.size - 15} apps\n")
        }
        if (panel.isNotEmpty()) {
            sb.append("\nPainel flutuante:\n")
            val rows = collapseText(panel.map { it.clock to it.msg })
            rows.take(40).forEach { sb.append("  ").append(it).append('\n') }
            if (rows.size > 40) sb.append("  ... +${rows.size - 40} linhas\n")
        }
        val keys = usage.filter { it.tag == "tecla" }
        if (keys.isNotEmpty()) sb.append("\nTeclas: ").append(tally(keys.map { it.msg }, 5)).append('\n')
        return sb.toString().trimEnd()
    }

    /** Junta linhas vizinhas iguais: "HH:MM:SS texto (×N)". */
    private fun collapseText(rows: List<Pair<String, String>>): List<String> {
        val out = ArrayList<String>()
        var i = 0
        while (i < rows.size) {
            var j = i
            while (j + 1 < rows.size && rows[j + 1].second == rows[i].second) j++
            val n = j - i + 1
            out.add(rows[i].first + "  " + rows[i].second + if (n > 1) "  (×$n, até ${rows[j].first})" else "")
            i = j + 1
        }
        return out
    }

    private fun otherText(other: List<Ev>): String {
        if (other.isEmpty()) return "nenhum."
        val lines = collapseText(other.map { it.stamp to (translate(it.tag, it.msg).let { t -> "[${it.tag}] " + t.replace("\n", "\n    ") }) })
        val shown = if (lines.size > 400) lines.takeLast(400) else lines
        return (if (lines.size > 400) "(${lines.size - 400} linhas mais antigas omitidas; veja o arquivo do log)\n" else "") + shown.joinToString("\n")
    }

    // ---------------------------------------------------------------- ZIP

    /** Grava o ZIP do "Enviar tudo". [macros] = (nome do arquivo, conteúdo JSON). Sem imagens, de propósito (privacidade). */
    fun writeZip(out: File, relatorio: String, registro: String, macros: List<Pair<String, String>>, info: String) {
        ZipOutputStream(out.outputStream().buffered()).use { z ->
            fun add(name: String, text: String) {
                z.putNextEntry(ZipEntry(name)); z.write(text.toByteArray(Charsets.UTF_8)); z.closeEntry()
            }
            add("relatorio.txt", relatorio)
            add("registro-completo.txt", registro)
            add("info.txt", info)
            for ((n, c) in macros) add("macros/$n", c)
        }
    }
}

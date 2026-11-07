package app.macro17

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Exporta macros no formato do app base: ZIP com TouchMacroPro/<nome>.tmc (CSV de 47 colunas) e as imagens PNG em
 * TouchMacroPro/image/<nome>/. É o inverso exato de [TmcImport] (mesmas colunas, mesmo cabeçalho "Title", linha "Replay",
 * ids de item na coluna 24 e saltos como 0x10000000 or id), então TmcImport.parse(TmcExport.write(m)) devolve a macro.
 * NÃO VERIFICADO: que o app base consegue importar o arquivo (não há o app base aqui para testar); o layout segue o
 * leitor/escritor decompilado dele e o que o próprio TmcImport lê. Kotlin puro, testável na JVM.
 *
 * Sem equivalente no base, cada um vira UM item de espera (pular + atraso), para os números dos saltos continuarem certos, e entra em [Out.notes]:
 * Achar por texto (NODE), Ler número (NUMBER), gesto livre (GESTURE), grupo dentro de grupo, imagem sem arquivo, CALL de macro ausente.
 * Perdas de ida e volta (por não existirem no base): faixa/atraso de ESPERAR viram atraso (ms somado), contadores compartilhados
 * entre vários itens viram um contador por item, miniatura (img) de toque sem correspondência, método/escalas/confirmação de imagem.
 */
object TmcExport {
    class Out(val text: String, val notes: List<String>)

    private const val ID_BIT = 0x10000000
    private val HEADER = (0 until 47).joinToString(",") { if (it == 0) "Title" else "col$it" }

    private fun sec(ms: Int): String = (ms / 1000).toString() + (if (ms % 1000 == 0) "" else "." + (ms % 1000).toString().padStart(3, '0').trimEnd('0'))
    private fun csv(c: List<String>) = c.joinToString(",") { if (it.any { ch -> ch == ',' || ch == '"' || ch == '\n' || ch == '\r' }) "\"" + it.replace("\"", "\"\"") + "\"" else it }

    /** Ids de recorte que a macro usa de fato como imagem de busca (os que o .tmc leva). */
    fun imageIds(m: Macro): Set<String> = m.steps.mapNotNull { s ->
        when { s.type == Type.IMAGE -> s.text; s.type in setOf(Type.TAP, Type.SWIPE, Type.PINCH_IN, Type.PINCH_OUT) && s.useImg -> s.img; else -> null }
    }.filter { it.isNotEmpty() }.toSet()

    /** [images]: id do recorte -> nome do PNG. [macroFiles]: id da macro -> nome do arquivo (sem .tmc), para o item CALL. */
    fun write(m: Macro, images: Map<String, String>, macroFiles: Map<String, String>): Out {
        val notes = ArrayList<String>()
        val n = m.steps.size
        fun tgt(g: Int) = if (g <= 0 || g > n) "0" else (ID_BIT or (g - 1)).toString()
        val depth = IntArray(n)
        m.steps.forEachIndexed { k, s -> if (s.type == Type.GROUP) for (j in k + 1..minOf(k + s.grp, n - 1)) depth[j] = 1 }
        // Contador do base = o próprio item; o primeiro COUNTER de cada número é o que o RESET aponta.
        val counterItem = HashMap<Int, Int>()
        m.steps.forEachIndexed { k, s -> if (s.type == Type.COUNTER) { if (counterItem.containsKey(s.counter)) notes.add("Item ${k + 1}: o contador ${s.counter} é compartilhado; no base cada item é um contador próprio") else counterItem[s.counter] = k } }
        val sb = StringBuilder()
        sb.append("Title,Touch Macro\nReplay,${m.loops}\n").append(",\n".repeat(7)).append(HEADER).append('\n')
        m.steps.forEachIndexed { k, s ->
            val c = MutableList(47) { "" }
            fun set(i: Int, v: Any) { c[i] = v.toString() }
            for (i in listOf(1, 2, 3, 4, 8, 9, 10, 14, 15, 16, 17, 18, 20, 21, 22, 38, 42, 46)) c[i] = "0"
            c[6] = "N"; c[11] = "1"; c[19] = "100"; c[28] = "1"; c[30] = "0"; c[31] = "1"; c[40] = "200"; c[41] = "0|0"; c[24] = k.toString()
            set(0, s.name.replace('\n', ' ').take(60)); set(15, if (s.off) 1 else 0)
            if (depth[k] == 1) c[13] = "11"
            fun placeholder(why: String) {
                notes.add("Item ${k + 1} “${s.name}”: $why"); c.fill(""); // recomeça como item de espera
                for (i in listOf(1, 2, 3, 4, 8, 9, 10, 14, 15, 16, 17, 18, 20, 21, 22, 38, 42, 46)) c[i] = "0"
                c[6] = "N"; c[11] = "1"; c[19] = "100"; c[28] = "1"; c[30] = "0"; c[31] = "1"; c[40] = "200"; c[41] = "0|0"; c[24] = k.toString()
                set(0, s.name.replace('\n', ' ').take(60)); set(15, if (s.off) 1 else 0); if (depth[k] == 1) c[13] = "11"
                set(7, 0); set(10, 5); set(20, 1); set(3, sec(s.delay.coerceIn(0, MAX_DELAY_MS)))
            }
            fun area(): String = if (s.x2 > s.x1 && s.y2 > s.y1) "${s.x1},${s.y1},${s.x2},${s.y2}" else ""
            fun delay(extra: Int = 0) {
                if (s.rnd > 0) { set(21, (c[21].toInt() or 1)); set(37, sec((s.rndMin + extra).coerceAtMost(MAX_DELAY_MS)) + "~" + sec((s.rnd + extra).coerceAtMost(MAX_DELAY_MS))) }
                else set(3, sec((s.delay + extra).coerceAtMost(MAX_DELAY_MS)))
                if (s.jitter > 0) { set(21, (c[21].toInt() or 2)); set(22, s.jitter) }
            }
            when (s.type) {
                Type.GROUP -> {
                    if (depth[k] == 1) return@forEachIndexed placeholder("grupo dentro de grupo não existe no base").also { sb.append(csv(c)).append('\n') }
                    c[13] = if (s.mono) "2" else "1"; set(14, s.rep); set(7, 0)
                    if (s.mono) { set(16, sec(s.gms)); set(17, tgt(s.gfail)) }
                }
                Type.TAP, Type.SWIPE, Type.PINCH_IN, Type.PINCH_OUT -> {
                    val file = if (s.useImg) images[s.img] else null
                    if (s.useImg && file == null) notes.add("Item ${k + 1}: imagem de correspondência sem arquivo; exportado como toque simples")
                    set(7, when (s.type) { Type.SWIPE -> 1; Type.PINCH_IN -> 4; Type.PINCH_OUT -> 5; else -> 0 })
                    set(1, s.x1); set(2, s.y1); set(8, s.x2); set(9, s.y2); set(10, s.ms.coerceAtLeast(1)); set(20, if (s.skip) 1 else 0)
                    if (s.type == Type.TAP && s.dbl) set(46, 1)
                    delay()
                    if (file != null) {
                        set(11, 3); set(12, file); set(19, s.thr); set(16, sec(s.sms)); set(40, s.cycle); set(41, "${s.ox}|${s.oy}")
                        val custom = s.ax2 > s.ax1 && s.ay2 > s.ay1
                        set(28, if (s.fx) 0 else if (custom) 3 else 1); if (custom && !s.fx) set(36, "${s.ax1},${s.ay1},${s.ax2},${s.ay2}")
                        set(18, tgt(s.goto)); set(17, tgt(s.fail))
                    }
                }
                Type.WAIT -> { // item de toque "pular": só o atraso roda; o tempo da espera soma ao atraso
                    set(7, 0); set(10, 5); set(20, 1); delay(s.ms)
                }
                Type.IMAGE -> {
                    val file = images[s.text] ?: return@forEachIndexed placeholder("imagem sem arquivo; vira espera").also { sb.append(csv(c)).append('\n') }
                    set(7, 6); set(12, file); set(46, if (s.dbl) 1 else 0); set(16, sec(s.ms)); set(40, s.cycle); set(19, s.thr); set(20, if (s.tap) 0 else 1)
                    val custom = s.x2 > s.x1 && s.y2 > s.y1
                    set(28, if (s.mono) (if (custom) 4 else 2) else if (custom) 3 else 1); if (custom) set(36, area())
                    set(41, "${s.ox}|${s.oy}"); set(18, tgt(s.goto)); set(17, tgt(s.fail)); delay()
                }
                Type.GOTO -> { set(7, 10); set(18, tgt(s.goto)); delay() }
                Type.COUNTER -> {
                    set(7, 11); set(17, tgt(s.goto)); set(44, s.cases.joinToString("|") { "${it.n}:${tgt(it.to)}" }); delay()
                }
                Type.RESET -> {
                    set(7, 18); delay()
                    val item = counterItem[s.counter]
                    if (item == null) { notes.add("Item ${k + 1}: zerar o contador ${s.counter}, que nenhum item usa"); set(34, 900_000 + s.counter) } else set(34, item)
                }
                Type.STOP -> { set(7, 12); delay() }
                Type.CALL -> {
                    val f = macroFiles[s.text] ?: return@forEachIndexed placeholder("macro chamada não incluída; vira espera").also { sb.append(csv(c)).append('\n') }
                    set(7, 13); set(29, "$f.tmc"); set(30, if (s.ret) 0 else 1); delay()
                }
                Type.STATUS -> {
                    set(7, 14); set(45, s.text); set(10, s.ms.coerceAtLeast(1)); set(31, (if (s.snd) 1 else 0) or (if (s.vib) 2 else 0) or (if (s.pop) 4 else 0))
                    if (s.snu.isNotBlank()) set(43, "0|" + s.snu); delay()
                }
                Type.KEY -> {
                    val code = when (s.x1) { 1 -> 4; 2 -> 3; 3 -> 187; KEY_SCREENSHOT -> 120; else -> { notes.add("Item ${k + 1}: tecla ${s.x1} sem equivalente; exportada como Voltar"); 4 } }
                    set(7, 15); set(32, code); set(10, s.ms); delay()
                }
                Type.TEXT -> {
                    set(7, 17); set(33, s.text); set(46, if (s.dbl) 1 else 0); set(16, sec(s.ms)); set(40, s.cycle); set(42, s.minConf); set(20, if (s.tap) 0 else 1)
                    if (s.x2 > s.x1 && s.y2 > s.y1) { set(28, 3); set(36, area()) }
                    set(18, tgt(s.goto)); set(17, tgt(s.fail)); delay()
                }
                Type.CHANGE -> {
                    set(7, 19); set(16, sec(s.ms)); set(40, s.cycle); set(19, s.thr); if (s.x2 > s.x1 && s.y2 > s.y1) set(36, area())
                    set(18, tgt(s.goto)); set(17, tgt(s.fail)); delay()
                }
                Type.NODE -> placeholder("“Achar por texto” não existe no base; vira espera")
                Type.NUMBER -> placeholder("“Ler número” não existe no base; vira espera")
                Type.GESTURE -> placeholder("gesto livre não existe no base; vira espera")
            }
            sb.append(csv(c)).append('\n')
        }
        return Out(sb.toString(), notes)
    }

    /** Nome de arquivo seguro e único (sem diferenciar maiúsculas, como o import). */
    fun fileName(name: String, used: MutableSet<String>): String {
        val base = name.replace(Regex("[\\\\/:*?\"<>|\\p{Cc}]"), "_").trim().trim('.').ifEmpty { "macro" }
        var f = base; var i = 2
        while (!used.add(f.lowercase())) f = "$base ($i)".also { i++ }
        return f
    }

    class Bundle(val macros: Int, val images: Int, val notes: List<String>)

    /**
     * Grava o ZIP do base. Junta as macros chamadas por CALL (recursivamente, via [byId]) para o item não ficar sem destino.
     * [template] devolve os bytes PNG de um recorte (ou null se não existir).
     */
    fun zip(out: OutputStream, macros: List<Macro>, byId: (String) -> Macro?, template: (String) -> ByteArray?): Bundle {
        val all = LinkedHashMap<String, Macro>()
        val queue = ArrayDeque(macros)
        while (queue.isNotEmpty()) {
            val m = queue.removeFirst()
            if (all.putIfAbsent(m.id, m) != null) continue
            m.steps.filter { it.type == Type.CALL }.mapNotNull { byId(it.text) }.forEach { queue.addLast(it) }
        }
        val used = HashSet<String>()
        val files = all.mapValues { fileName(it.value.name, used) }
        val notes = ArrayList<String>(); var imgs = 0
        ZipOutputStream(out).use { z ->
            for ((id, m) in all) {
                val dir = files.getValue(id)
                val names = HashMap<String, String>()
                for (t in imageIds(m)) { val b = template(t) ?: continue; names[t] = "$t.png"; z.putNextEntry(ZipEntry("TouchMacroPro/image/$dir/$t.png")); z.write(b); z.closeEntry(); imgs++ }
                val r = write(m, names, files)
                notes += r.notes.map { "${m.name}: $it" }
                z.putNextEntry(ZipEntry("TouchMacroPro/$dir.tmc")); z.write(r.text.toByteArray(Charsets.UTF_8)); z.closeEntry()
            }
        }
        return Bundle(all.size, imgs, notes)
    }
}

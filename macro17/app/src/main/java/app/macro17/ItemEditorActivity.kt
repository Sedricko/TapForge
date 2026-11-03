package app.macro17

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject

/**
 * Tela de edição de um item de toque/deslizar no formato do app base (res/xml/pref_touch_pos_config.xml): nome, posição,
 * duração, atraso, correspondência de imagem (área, taxa, velocidade, tempo, mover se falhar/achar), ação de toque
 * (pular, clique duplo, desvio) e variação aleatória. Cada mudança refaz a tela, para as linhas dependentes mudarem de estado.
 * Entrada: extras "step" (JSON), "total" (passos depois da edição) e "index" (-1 = item novo). Saída: "step" e "index".
 */
@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
class ItemEditorActivity : Activity() {
    private lateinit var s: Step
    private var total = 1
    private var index = -1
    private var scrollY = 0
    private lateinit var store: Store
    private var scroller: android.widget.ScrollView? = null

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        store = Store(this)
        s = Step.fromJson(JSONObject(intent.getStringExtra("step") ?: "{}"))
        total = intent.getIntExtra("total", 1); index = intent.getIntExtra("index", -1)
        render()
    }

    override fun onBackPressed() = leave()

    private fun render() {
        scroller?.let { scrollY = it.scrollY }
        val p = Prefs(this)
        build(p)
        setContentView(p.page("Editor de Macro", Ic.BACK, { leave() }))
        scroller = p.scrollView
        p.scrollView.post { p.scrollView.scrollTo(0, scrollY) }
    }

    /** Volta salvando se o item é válido; senão mostra o motivo e deixa corrigir ou descartar. */
    private fun leave() {
        val err = s.validate(total)
        if (err == null) {
            setResult(RESULT_OK, Intent().putExtra("step", s.toJson().toString()).putExtra("index", index))
            finish()
        } else AlertDialog.Builder(this).setTitle("Item inválido").setMessage(err)
            .setPositiveButton("Corrigir", null).setNegativeButton("Descartar") { _, _ -> finish() }.show()
    }

    // --- formatação --------------------------------------------------------------------------

    private fun secs(ms: Int) = "%.1f Sec".format(java.util.Locale.US, ms / 1000.0)
    private fun gotoLabel(n: Int) = if (n == 0) "Seguir para o próximo" else "Passo $n"
    private val speeds = listOf("Muito rápido" to 50, "Rápido" to 200, "1 Seg" to 1000, "2 Seg" to 2000, "3 Seg" to 3000)

    private fun kind() = when { s.type == Type.SWIPE -> "Deslizar"; s.ms >= 500 -> "Pressionar"; else -> "Toque" }

    // --- tela --------------------------------------------------------------------------------

    private val simple = setOf(Type.WAIT, Type.KEY, Type.STATUS, Type.GOTO, Type.RESET, Type.CALL, Type.STOP, Type.COUNTER, Type.TEXT, Type.NODE, Type.IMAGE, Type.NUMBER, Type.CHANGE, Type.GESTURE)
    private val keyNames = mapOf(1 to "Voltar", 2 to "Início", 3 to "Recentes", 4 to "Notificações", KEY_SCREENSHOT to "Captura de tela")
    private val titleOf = mapOf(
        Type.WAIT to "Esperar", Type.KEY to "Evento de Teclado", Type.STATUS to "Alertas de status", Type.GOTO to "Ir Para",
        Type.RESET to "Reiniciar Contador de Execuções", Type.CALL to "Arquivo de Macro", Type.STOP to "Parar Macro",
        Type.COUNTER to "Contador de Execuções", Type.TEXT to "Reconhecimento de Texto", Type.NODE to "Achar por texto", Type.IMAGE to "Pesquisa de Imagem", Type.CHANGE to "Detecção de Mudança de Imagem", Type.GESTURE to "Gesto", Type.NUMBER to "Ler número",
    )

    /** Itens que não são toque: mesma estrutura (nome, categoria do tipo, valores, atraso), só com as linhas de cada tipo. */
    private fun buildSimple(p: Prefs) {
        val title = titleOf.getValue(s.type)
        p.valueRow("Nome", s.name.ifBlank { title }) { textDialog("Nome", s.name) { v -> s = s.copy(name = v.replace(',', '_').trim()); render() } }
        p.category(title, Ic.GEAR)
        when (s.type) {
            Type.WAIT -> p.valueRow("Tempo de Espera", secs(s.ms)) { secDialog("Tempo de Espera (segundos)", null, s.ms) { v -> s = s.copy(ms = v); render() } }
            Type.KEY -> p.valueRow("Tecla", keyNames[s.x1] ?: "Voltar") {
                val codes = keyNames.keys.toList()
                AlertDialog.Builder(this).setTitle("Tecla").setSingleChoiceItems(codes.map { keyNames.getValue(it) }.toTypedArray(), codes.indexOf(s.x1)) { d, w ->
                    s = s.copy(x1 = codes[w]); d.dismiss(); render()
                }.setNegativeButton("Cancelar", null).show()
            }
            Type.STATUS -> {
                p.valueRow("Mensagem", s.text.ifBlank { "(vazia)" }) { textDialog("Mensagem", s.text) { v -> s = s.copy(text = v.trim()); render() } }
                p.valueRow("Duração", secs(s.ms)) { secDialog("Duração (segundos)", null, s.ms) { v -> s = s.copy(ms = v); render() } }
                p.switchRow("Som", "Toca o som de alarme do aparelho.", s.snd) { v -> s = s.copy(snd = v); render() }
                p.switchRow("Vibração", "Vibra junto com o alerta.", s.vib) { v -> s = s.copy(vib = v); render() }
            }
            Type.GOTO -> p.valueRow("Ir para o passo", if (s.goto == 0) "Seguir para o próximo" else "Passo ${s.goto}") { gotoDialog("Ir para o passo", s.goto) { v -> s = s.copy(goto = v); render() } }
            Type.RESET -> p.valueRow("Contador", "Contador ${s.counter}") {
                AlertDialog.Builder(this).setTitle("Contador").setSingleChoiceItems(Array(MAX_COUNTERS) { "Contador ${it + 1}" }, (s.counter - 1).coerceIn(0, MAX_COUNTERS - 1)) { d, w ->
                    s = s.copy(counter = w + 1); d.dismiss(); render()
                }.setNegativeButton("Cancelar", null).show()
            }
            Type.COUNTER -> {
                p.valueRow("Contador", "Contador ${s.counter}") {
                    AlertDialog.Builder(this).setTitle("Contador").setSingleChoiceItems(Array(MAX_COUNTERS) { "Contador ${it + 1}" }, (s.counter - 1).coerceIn(0, MAX_COUNTERS - 1)) { d, w ->
                        s = s.copy(counter = w + 1); d.dismiss(); render()
                    }.setNegativeButton("Cancelar", null).show()
                }
                s.cases.forEachIndexed { k, c ->
                    p.valueRow("Caso ${k + 1}", "≥ ${c.n} execuções → ${if (c.to == 0) "seguir" else "passo ${c.to}"}") {
                        val d = fieldsBox("Caso ${k + 1}", listOf("Execuções (limiar)", "Ir para o passo (0 = seguir)"), listOf(c.n, c.to))
                        d.first.setNeutralButton("Remover") { _, _ -> s = s.copy(cases = s.cases.filterIndexed { j, _ -> j != k }); render() }
                            .setPositiveButton("OK") { _, _ ->
                                val v = d.second.map { it.text.toString().toIntOrNull() }
                                if (v.any { it == null }) toast("Número inválido")
                                else { s = s.copy(cases = s.cases.mapIndexed { j, x -> if (j == k) Case(v[0]!!, v[1]!!) else x }); render() }
                            }.setNegativeButton("Cancelar", null).show()
                    }
                }
                if (s.cases.size < MAX_CASES) p.valueRow("Adicionar", "Novo caso (até $MAX_CASES)") {
                    val d = fieldsBox("Novo caso", listOf("Execuções (limiar)", "Ir para o passo (0 = seguir)"), listOf((s.cases.maxOfOrNull { it.n } ?: 0) + 1, 0))
                    d.first.setPositiveButton("OK") { _, _ ->
                        val v = d.second.map { it.text.toString().toIntOrNull() }
                        if (v.any { it == null }) toast("Número inválido") else { s = s.copy(cases = s.cases + Case(v[0]!!, v[1]!!)); render() }
                    }.setNegativeButton("Cancelar", null).show()
                }
                p.valueRow("Outro Caso", gotoLabel(s.goto)) { gotoDialog("Outro Caso", s.goto) { v -> s = s.copy(goto = v); render() } }
            }
            Type.TEXT, Type.NODE -> {
                val ocr = s.type == Type.TEXT
                p.valueRow(if (ocr) "Texto" else "Texto ou id", s.text.ifBlank { "(vazio)" }) { textDialog(if (ocr) "Texto (vários separados por vírgula)" else "Texto ou id:recurso", s.text) { v -> s = s.copy(text = v.trim()); render() } }
                p.switchRow("Texto exato", "Só vale se o texto for igual, e não apenas contido.", s.exact) { v -> s = s.copy(exact = v); render() }
                if (ocr) {
                    val whole = s.x2 <= s.x1
                    p.valueRow("Área de Pesquisa", if (whole) "Procura o texto em toda a tela." else "Procura na área ${s.x1},${s.y1},${s.x2},${s.y2}.") {
                        AlertDialog.Builder(this).setTitle("Área de Pesquisa").setSingleChoiceItems(arrayOf("Tela inteira", "Área personalizada"), if (whole) 0 else 1) { d, w ->
                            s = if (w == 0) s.copy(x1 = 0, y1 = 0, x2 = 0, y2 = 0) else if (whole) s.copy(x1 = 0, y1 = 0, x2 = 500, y2 = 500) else s
                            d.dismiss(); render()
                        }.setNegativeButton("Cancelar", null).show()
                    }
                    p.valueRow("Área Personalizada", "${s.x1},${s.y1},${s.x2},${s.y2}", !whole) {
                        fieldsDialog("Área Personalizada", null, listOf("X inicial", "Y inicial", "X final", "Y final"), listOf(s.x1, s.y1, s.x2, s.y2), signed = false) { v -> s = s.copy(x1 = v[0], y1 = v[1], x2 = v[2], y2 = v[3]); render() }
                    }
                    p.valueRow("Confiança mínima (%)", "${s.minConf}%") { numDialog("Confiança mínima (%)", "De 0 a 100. Leituras abaixo disso são descartadas.", s.minConf) { v -> s = s.copy(minConf = v); render() } }
                }
                p.valueRow("Tempo de Pesquisa", if (s.ms == 0) "Infinito" else secs(s.ms)) {
                    secDialog("Tempo de Pesquisa (segundos)", "0 = pesquisa infinita (sem 'se achar / se não achar').", s.ms) { v -> s = if (v == 0) s.copy(ms = 0, goto = 0, fail = 0) else s.copy(ms = v); render() }
                }
                p.valueRow("Velocidade de Pesquisa", speeds.firstOrNull { it.second == s.cycle }?.first ?: "${s.cycle} ms") {
                    AlertDialog.Builder(this).setTitle("Velocidade de Pesquisa").setSingleChoiceItems(speeds.map { it.first }.toTypedArray(), speeds.indexOfFirst { it.second == s.cycle }) { d, w -> s = s.copy(cycle = speeds[w].second); d.dismiss(); render() }
                        .setNegativeButton("Cancelar", null).show()
                }
                p.switchRow("Tocar ao achar", "Toca no centro do que foi encontrado.", s.tap) { v -> s = s.copy(tap = v); render() }
                p.valueRow("Desvio de Coordenadas", "X${"%+d".format(s.ox)}, Y${"%+d".format(s.oy)} px", s.tap) {
                    fieldsDialog("Desvio de Coordenadas (px)", null, listOf("X (negativo = esquerda)", "Y (negativo = acima)"), listOf(s.ox, s.oy), signed = true) { v -> s = s.copy(ox = v[0], oy = v[1]); render() }
                }
                val jumps = s.ms > 0
                p.valueRow("Mover em Caso de Falha", gotoLabel(s.fail), jumps) { gotoDialog("Mover em Caso de Falha", s.fail) { v -> s = s.copy(fail = v); render() } }
                p.valueRow("Mover em Caso de Sucesso", gotoLabel(s.goto), jumps) { gotoDialog("Mover em Caso de Sucesso", s.goto) { v -> s = s.copy(goto = v); render() } }
            }
            Type.IMAGE -> {
                p.valueRow("Imagem", if (s.text.isBlank()) "Sem imagem: capture pelo painel." else "Imagem capturada (não editável aqui).")
                val whole = listOf(s.x1, s.y1, s.x2, s.y2).all { it == 0 }
                p.valueRow("Área de Pesquisa", if (whole) "Procura imagens em toda a tela." else "Procura imagens na área ${s.x1},${s.y1},${s.x2},${s.y2}.") {
                    AlertDialog.Builder(this).setTitle("Área de Pesquisa").setSingleChoiceItems(arrayOf("Tela inteira", "Área personalizada"), if (whole) 0 else 1) { d, w ->
                        s = if (w == 0) s.copy(x1 = 0, y1 = 0, x2 = 0, y2 = 0) else if (whole) s.copy(x1 = 0, y1 = 0, x2 = 500, y2 = 500) else s
                        d.dismiss(); render()
                    }.setNegativeButton("Cancelar", null).show()
                }
                p.valueRow("Área Personalizada", "${s.x1},${s.y1},${s.x2},${s.y2}", !whole) {
                    fieldsDialog("Área Personalizada", null, listOf("X inicial", "Y inicial", "X final", "Y final"), listOf(s.x1, s.y1, s.x2, s.y2), signed = false) { v -> s = s.copy(x1 = v[0], y1 = v[1], x2 = v[2], y2 = v[3]); render() }
                }
                p.valueRow("Taxa de correspondência de imagem (%)", "${s.thr}%") { numDialog("Taxa de correspondência de imagem (%)", "De 20 a 100.", s.thr) { v -> s = s.copy(thr = v); render() } }
                p.switchRow("Comparar em cinza", "Ignora a cor e compara só a luminosidade.", s.mono) { v -> s = s.copy(mono = v); render() }
                p.switchRow("Várias escalas", "Procura também a imagem maior ou menor.", s.scales) { v -> s = s.copy(scales = v); render() }
                p.valueRow("Confirmações seguidas", "${s.confirm}") { numDialog("Confirmações seguidas", "De 1 a 5 quadros seguidos com a imagem.", s.confirm) { v -> s = s.copy(confirm = v); render() } }
                p.valueRow("Velocidade de Pesquisa de Imagem", speeds.firstOrNull { it.second == s.cycle }?.first ?: "${s.cycle} ms") {
                    AlertDialog.Builder(this).setTitle("Velocidade de Pesquisa de Imagem").setSingleChoiceItems(speeds.map { it.first }.toTypedArray(), speeds.indexOfFirst { it.second == s.cycle }) { d, w -> s = s.copy(cycle = speeds[w].second); d.dismiss(); render() }
                        .setNegativeButton("Cancelar", null).show()
                }
                p.valueRow("Tempo de Pesquisa", if (s.ms == 0) "Infinito" else secs(s.ms)) {
                    secDialog("Tempo de Pesquisa (segundos)", "0 = pesquisa infinita (sem 'se achar / se não achar').", s.ms) { v -> s = if (v == 0) s.copy(ms = 0, goto = 0, fail = 0) else s.copy(ms = v); render() }
                }
                p.switchRow("Tocar ao achar", "Toca no centro da imagem encontrada.", s.tap) { v -> s = s.copy(tap = v); render() }
                p.valueRow("Desvio de Coordenadas", "X${"%+d".format(s.ox)}, Y${"%+d".format(s.oy)} px", s.tap) {
                    fieldsDialog("Desvio de Coordenadas (px)", null, listOf("X (negativo = esquerda)", "Y (negativo = acima)"), listOf(s.ox, s.oy), signed = true) { v -> s = s.copy(ox = v[0], oy = v[1]); render() }
                }
                val jumps = s.ms > 0
                p.valueRow("Mover em Caso de Falha", gotoLabel(s.fail), jumps) { gotoDialog("Mover em Caso de Falha", s.fail) { v -> s = s.copy(fail = v); render() } }
                p.valueRow("Mover em Caso de Sucesso", gotoLabel(s.goto), jumps) { gotoDialog("Mover em Caso de Sucesso", s.goto) { v -> s = s.copy(goto = v); render() } }
            }
            Type.GESTURE -> {
                p.valueRow("Duração do Gesto", secs(s.ms)) { secDialog("Duração do Gesto (segundos)", "De 0,001 a 60 s.", s.ms) { v -> s = s.copy(ms = v); render() } }
                p.valueRow("Traço", "${gesturePoints(s.text).size} pontos") {}
                p.note("O traço é desenhado com o dedo na gravação (tipo Gesto no painel).")
            }
            Type.CHANGE -> {
                p.note("Captura uma imagem na primeira execução e verifica continuamente se ela mudou da imagem capturada antes.")
                val whole = s.x2 <= s.x1
                p.valueRow("Área de Pesquisa", if (whole) "Compara a tela inteira." else "Compara a área ${s.x1},${s.y1},${s.x2},${s.y2}.") {
                    AlertDialog.Builder(this).setTitle("Área de Pesquisa").setSingleChoiceItems(arrayOf("Tela inteira", "Área personalizada"), if (whole) 0 else 1) { d, w ->
                        s = if (w == 0) s.copy(x1 = 0, y1 = 0, x2 = 0, y2 = 0) else if (whole) s.copy(x1 = 0, y1 = 0, x2 = 500, y2 = 500) else s
                        d.dismiss(); render()
                    }.setNegativeButton("Cancelar", null).show()
                }
                p.valueRow("Área Personalizada", "${s.x1},${s.y1},${s.x2},${s.y2}", !whole) {
                    fieldsDialog("Área Personalizada", null, listOf("X inicial", "Y inicial", "X final", "Y final"), listOf(s.x1, s.y1, s.x2, s.y2), signed = false) { v -> s = s.copy(x1 = v[0], y1 = v[1], x2 = v[2], y2 = v[3]); render() }
                }
                p.valueRow("Semelhança para considerar igual (%)", "${s.thr}%") { numDialog("Semelhança (%)", "Abaixo disso a imagem é considerada mudada. De 1 a 100.", s.thr) { v -> s = s.copy(thr = v); render() } }
                p.valueRow("Velocidade de Pesquisa", speeds.firstOrNull { it.second == s.cycle }?.first ?: "${s.cycle} ms") {
                    AlertDialog.Builder(this).setTitle("Velocidade de Pesquisa").setSingleChoiceItems(speeds.map { it.first }.toTypedArray(), speeds.indexOfFirst { it.second == s.cycle }) { d, w -> s = s.copy(cycle = speeds[w].second); d.dismiss(); render() }
                        .setNegativeButton("Cancelar", null).show()
                }
                p.valueRow("Tempo de Pesquisa", if (s.ms == 0) "Infinito" else secs(s.ms)) {
                    secDialog("Tempo de Pesquisa (segundos)", "0 = espera infinita.", s.ms) { v -> s = if (v == 0) s.copy(ms = 0, goto = 0, fail = 0) else s.copy(ms = v); render() }
                }
                val jumps = s.ms > 0
                p.valueRow("Mover em Caso de Falha", gotoLabel(s.fail), jumps) { gotoDialog("Mover em Caso de Falha", s.fail) { v -> s = s.copy(fail = v); render() } }
                p.valueRow("Mover em Caso de Sucesso", gotoLabel(s.goto), jumps) { gotoDialog("Mover em Caso de Sucesso", s.goto) { v -> s = s.copy(goto = v); render() } }
            }
            Type.NUMBER -> {
                p.valueRow("Área do número", "${s.x1},${s.y1},${s.x2},${s.y2}") {
                    fieldsDialog("Área do número", null, listOf("X inicial", "Y inicial", "X final", "Y final"), listOf(s.x1, s.y1, s.x2, s.y2), signed = false) { v -> s = s.copy(x1 = v[0], y1 = v[1], x2 = v[2], y2 = v[3]); render() }
                }
                val ops = arrayOf("Maior ou igual (≥)", "Menor ou igual (≤)", "Igual (=)", "Entre")
                p.valueRow("Condição", ops[s.cmp.coerceIn(0, 3)]) {
                    AlertDialog.Builder(this).setTitle("Condição").setSingleChoiceItems(ops, s.cmp.coerceIn(0, 3)) { d, w -> s = s.copy(cmp = w); d.dismiss(); render() }.setNegativeButton("Cancelar", null).show()
                }
                p.valueRow(if (s.cmp == 3) "Valor mínimo" else "Valor", "${s.lo}") { numDialog("Valor", null, s.lo) { v -> s = s.copy(lo = v); render() } }
                p.valueRow("Valor máximo", "${s.hi}", s.cmp == 3) { numDialog("Valor máximo", null, s.hi) { v -> s = s.copy(hi = v); render() } }
                p.valueRow("Tempo de Pesquisa", if (s.ms == 0) "Infinito" else secs(s.ms)) {
                    secDialog("Tempo de Pesquisa (segundos)", "0 = pesquisa infinita.", s.ms) { v -> s = if (v == 0) s.copy(ms = 0, goto = 0, fail = 0) else s.copy(ms = v); render() }
                }
                p.valueRow("Velocidade de Pesquisa", speeds.firstOrNull { it.second == s.cycle }?.first ?: "${s.cycle} ms") {
                    AlertDialog.Builder(this).setTitle("Velocidade de Pesquisa").setSingleChoiceItems(speeds.map { it.first }.toTypedArray(), speeds.indexOfFirst { it.second == s.cycle }) { d, w -> s = s.copy(cycle = speeds[w].second); d.dismiss(); render() }
                        .setNegativeButton("Cancelar", null).show()
                }
                val jumps = s.ms > 0
                p.valueRow("Mover em Caso de Falha", gotoLabel(s.fail), jumps) { gotoDialog("Mover em Caso de Falha", s.fail) { v -> s = s.copy(fail = v); render() } }
                p.valueRow("Mover em Caso de Sucesso", gotoLabel(s.goto), jumps) { gotoDialog("Mover em Caso de Sucesso", s.goto) { v -> s = s.copy(goto = v); render() } }
            }
            Type.STOP -> p.note("Encerra a reprodução da macro neste ponto.")
            else -> { // CALL
                val target = store.get(s.text)
                p.valueRow("Arquivo de Macro", target?.name ?: "Nenhum arquivo alocado.") {
                    val others = store.list().filter { it.id != intent.getStringExtra("self") }
                    if (others.isEmpty()) toast("Nenhum arquivo de macro")
                    else AlertDialog.Builder(this).setTitle("Arquivos de Macro").setItems(others.map { it.name }.toTypedArray()) { _, w -> s = s.copy(text = others[w].id); render() }
                        .setNegativeButton("Cancelar", null).show()
                }
                p.switchRow("Voltar ao terminar", "Desligado: o arquivo escolhido substitui esta macro.", s.ret) { v -> s = s.copy(ret = v); render() }
            }
        }
        if (s.type != Type.WAIT) {
            p.category("Atraso", Ic.WAIT)
            val delayOn = s.delay > 0
            p.switchRow("Atraso", "O item será processado após o tempo de atraso.", delayOn) { on -> s = s.copy(delay = if (on) 1000 else 0, rnd = 0, rndMin = 0); render() }
            p.valueRow("Tempo de Atraso", secs(s.delay), delayOn) { secDialog("Tempo de Atraso (segundos)", null, s.delay) { v -> s = s.copy(delay = v); render() } }
        }
    }

    private fun build(p: Prefs) {
        if (s.type in simple) return buildSimple(p)
        val swipe = s.type == Type.SWIPE
        p.valueRow("Nome", s.name.ifBlank { kind() }) { textDialog("Nome", s.name) { v -> s = s.copy(name = v.replace(',', '_').trim()); render() } }

        p.category(kind(), if (swipe) Ic.SWIPE else Ic.TAP)
        p.valueRow("Posição do Toque", if (swipe) "${s.x1},${s.y1}  →  ${s.x2},${s.y2}" else "${s.x1},${s.y1}") { positionDialog() }
        p.valueRow("Duração do Gesto", "${s.ms}ms") {
            numDialog("Duração do Gesto (ms)", null, s.ms) { v -> s = s.copy(ms = v); render() }
        }

        p.category("Atraso", Ic.WAIT)
        val delayOn = s.delay > 0 || s.rnd > 0
        p.switchRow("Atraso", "Tocar/Deslizar será processado após o tempo de atraso.", delayOn) { on ->
            s = if (on) s.copy(delay = if (s.delay > 0) s.delay else 1000) else s.copy(delay = 0, rnd = 0, rndMin = 0); render()
        }
        p.valueRow("Tempo de Atraso", secs(s.delay), delayOn) {
            secDialog("Tempo de Atraso (segundos)", null, s.delay) { v -> s = s.copy(delay = v); render() }
        }

        p.category("Correspondência de Imagem", Ic.IMAGE_SEARCH)
        val on = s.useImg
        p.switchRow("Correspondência de Imagem", "Tocar/Deslizar será processado se a imagem corresponder.", on) { v ->
            if (v && s.img.isEmpty()) { toast("Este item não tem imagem: grave-o pelo painel (REC)."); render() }
            else { s = s.copy(useImg = v); render() }
        }
        val whole = listOf(s.ax1, s.ay1, s.ax2, s.ay2).all { it == 0 }
        p.valueRow("Área de Pesquisa", if (whole) "Procura imagens em toda a tela." else "Procura imagens na área personalizada.", on) {
            AlertDialog.Builder(this).setTitle("Área de Pesquisa")
                .setSingleChoiceItems(arrayOf("Tela inteira", "Área personalizada"), if (whole) 0 else 1) { d, w ->
                    s = if (w == 0) s.copy(ax1 = 0, ay1 = 0, ax2 = 0, ay2 = 0) else if (whole) s.copy(ax1 = 0, ay1 = 0, ax2 = 500, ay2 = 500) else s
                    d.dismiss(); render()
                }.setNegativeButton("Cancelar", null).show()
        }
        p.valueRow("Área Personalizada", "${s.ax1},${s.ay1},${s.ax2},${s.ay2}", on && !whole) {
            fieldsDialog("Área Personalizada", null, listOf("X inicial", "Y inicial", "X final", "Y final"), listOf(s.ax1, s.ay1, s.ax2, s.ay2), signed = false) { v ->
                s = s.copy(ax1 = v[0], ay1 = v[1], ax2 = v[2], ay2 = v[3]); render()
            }
        }
        p.valueRow("Taxa de correspondência de imagem (%)", "${s.thr}%", on) {
            numDialog("Taxa de correspondência de imagem (%)", "De 20 a 100. Quanto maior, mais parecida com a gravada a imagem precisa ser.", s.thr) { v -> s = s.copy(thr = v); render() }
        }
        p.valueRow("Velocidade de Pesquisa de Imagem", speeds.firstOrNull { it.second == s.cycle }?.first ?: "${s.cycle} ms", on) {
            AlertDialog.Builder(this).setTitle("Velocidade de Pesquisa de Imagem")
                .setMessage("Defina uma velocidade mais rápida para detectar imagens em mudança rápida.")
                .setSingleChoiceItems(speeds.map { it.first }.toTypedArray(), speeds.indexOfFirst { it.second == s.cycle }) { d, w -> s = s.copy(cycle = speeds[w].second); d.dismiss(); render() }
                .setNegativeButton("Cancelar", null).show()
        }
        p.valueRow("Tempo de Pesquisa", if (s.sms == 0) "Infinito" else secs(s.sms), on) {
            secDialog(
                "Tempo de Pesquisa (segundos)",
                "Definir o tempo de pesquisa como 0 significa pesquisa infinita. Maior que 0, permite configurar para onde ir conforme o resultado da pesquisa. " +
                    "Um tempo entre 0 e 1 segundo compara as imagens instantaneamente, usando quadros capturados anteriormente.",
                s.sms,
            ) { v -> s = if (v == 0) s.copy(sms = 0, goto = 0, fail = 0) else s.copy(sms = v); render() }
        }
        val jumps = on && s.sms > 0
        p.valueRow("Mover em Caso de Falha", gotoLabel(s.fail), jumps) { gotoDialog("Mover em Caso de Falha", s.fail) { v -> s = s.copy(fail = v); render() } }
        p.valueRow("Mover em Caso de Sucesso", gotoLabel(s.goto), jumps) { gotoDialog("Mover em Caso de Sucesso", s.goto) { v -> s = s.copy(goto = v); render() } }

        p.category("Ação de Toque", Ic.TAP)
        p.switchRow("Pular", "Tocar/Deslizar/Pressionar não funcionará. Usado para fins de atraso e comparação de imagens.", s.skip) { v -> s = s.copy(skip = v); render() }
        p.switchRow("Clique Duplo", "Clica duas vezes no mesmo local.", s.dbl, enabled = !s.skip && s.type == Type.TAP) { v -> s = s.copy(dbl = v); render() }
        p.valueRow("Desvio de Coordenadas", "X${"%+d".format(s.ox)}, Y${"%+d".format(s.oy)} px", on && !s.skip) {
            fieldsDialog(
                "Desvio de Coordenadas (px)",
                "Esta função permite tocar num local diferente da posição da imagem. Defina o desvio de coordenadas para onde tocar a partir da posição da imagem encontrada pela pesquisa.",
                listOf("X (negativo = esquerda)", "Y (negativo = acima)"), listOf(s.ox, s.oy), signed = true,
            ) { v -> s = s.copy(ox = v[0], oy = v[1]); render() }
        }

        p.category("Evasão de Detecção", Ic.EYE_OFF)
        p.valueRow("Coordenadas Aleatórias (Coordenadas ± Valor de Pixel Aleatório)", "${s.jitter} pixels") {
            numDialog("Coordenadas Aleatórias (pixels)", "De 0 a 500. Cada gesto é deslocado por um valor sorteado dentro dessa margem.", s.jitter) { v -> s = s.copy(jitter = v); render() }
        }
        p.valueRow("Atraso Aleatório", if (s.rnd > 0) "${secs(s.rndMin)} a ${secs(s.rnd)}" else "Desligado", delayOn) {
            fieldsDialog(
                "Atraso Aleatório (segundos)",
                "A ação será executada após o atraso aleatório especificado. O atraso acima será ignorado. Máximo 0 desliga.",
                listOf("Mínimo (s)", "Máximo (s)"), listOf(s.rndMin, s.rnd), signed = false, decimal = true,
            ) { v -> s = if (v[1] == 0) s.copy(rnd = 0, rndMin = 0) else s.copy(rndMin = v[0], rnd = v[1]); render() }
        }
    }

    // --- diálogos ----------------------------------------------------------------------------

    private fun box(hint: String, init: String, type: Int) = EditText(this).apply { this.hint = hint; inputType = type; setText(init); setSelectAllOnFocus(true); setSingleLine() }

    private fun column(msg: String?, vararg views: android.view.View) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(12), dp(24), dp(4))
        msg?.let { addView(TextView(this@ItemEditorActivity).apply { text = it; textSize = 14f; setPadding(0, 0, 0, dp(8)) }) }
        views.forEach { addView(it, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)) }
    }

    private fun textDialog(title: String, init: String, ok: (String) -> Unit) {
        val et = box(title, init, InputType.TYPE_CLASS_TEXT)
        AlertDialog.Builder(this).setTitle(title).setView(column(null, et)).setPositiveButton("OK") { _, _ -> ok(et.text.toString()) }.setNegativeButton("Cancelar", null).show()
    }

    private fun numDialog(title: String, msg: String?, init: Int, ok: (Int) -> Unit) {
        val et = box(title, init.toString(), InputType.TYPE_CLASS_NUMBER)
        AlertDialog.Builder(this).setTitle(title).setView(column(msg, et))
            .setPositiveButton("OK") { _, _ -> et.text.toString().toIntOrNull()?.let(ok) ?: toast("Número inválido") }.setNegativeButton("Cancelar", null).show()
    }

    /** Segundos com casas decimais na tela, milissegundos no modelo. */
    private fun secDialog(title: String, msg: String?, initMs: Int, ok: (Int) -> Unit) {
        val et = box(title, (initMs / 1000.0).toString(), InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        AlertDialog.Builder(this).setTitle(title).setView(column(msg, et))
            .setPositiveButton("OK") { _, _ -> et.text.toString().replace(',', '.').toDoubleOrNull()?.let { ok(Math.round(it * 1000).toInt()) } ?: toast("Número inválido") }
            .setNegativeButton("Cancelar", null).show()
    }

    /** Vários números de uma vez. Com [decimal], os valores aparecem em segundos e voltam em milissegundos. */
    private fun fieldsDialog(title: String, msg: String?, labels: List<String>, init: List<Int>, signed: Boolean, decimal: Boolean = false, ok: (List<Int>) -> Unit) {
        val kind = InputType.TYPE_CLASS_NUMBER or (if (signed) InputType.TYPE_NUMBER_FLAG_SIGNED else 0) or (if (decimal) InputType.TYPE_NUMBER_FLAG_DECIMAL else 0)
        val ets = labels.mapIndexed { i, l -> box(l, if (decimal) (init[i] / 1000.0).toString() else init[i].toString(), kind) }
        AlertDialog.Builder(this).setTitle(title).setView(column(msg, *ets.toTypedArray()))
            .setPositiveButton("OK") { _, _ ->
                val v = ets.map { e -> e.text.toString().replace(',', '.').toDoubleOrNull()?.let { if (decimal) Math.round(it * 1000).toInt() else it.toInt() } }
                if (v.any { it == null }) toast("Número inválido") else ok(v.map { it!! })
            }.setNegativeButton("Cancelar", null).show()
    }

    /** Diálogo com vários campos numéricos com sinal; devolve o construtor e os campos para o chamador ligar os botões. */
    private fun fieldsBox(title: String, labels: List<String>, init: List<Int>): Pair<AlertDialog.Builder, List<EditText>> {
        val ets = labels.mapIndexed { k, l -> box(l, init[k].toString(), InputType.TYPE_CLASS_NUMBER) }
        return AlertDialog.Builder(this).setTitle(title).setView(column(null, *ets.toTypedArray())) to ets
    }

    private fun positionDialog() {
        if (s.type == Type.SWIPE) fieldsDialog("Posição do Toque", null, listOf("X inicial", "Y inicial", "X final", "Y final"), listOf(s.x1, s.y1, s.x2, s.y2), signed = false) { v ->
            s = s.copy(x1 = v[0], y1 = v[1], x2 = v[2], y2 = v[3]); render()
        } else fieldsDialog("Posição do Toque", null, listOf("X", "Y"), listOf(s.x1, s.y1), signed = false) { v -> s = s.copy(x1 = v[0], y1 = v[1], x2 = v[0], y2 = v[1]); render() }
    }

    /** 0 = seguir para o próximo; 1..total = ir para esse passo. */
    private fun gotoDialog(title: String, current: Int, ok: (Int) -> Unit) {
        val items = Array(total + 1) { if (it == 0) "Seguir para o próximo" else "Passo $it" }
        AlertDialog.Builder(this).setTitle(title).setSingleChoiceItems(items, current.coerceIn(0, total)) { d, w -> ok(w); d.dismiss() }.setNegativeButton("Cancelar", null).show()
    }

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_LONG).show()
}

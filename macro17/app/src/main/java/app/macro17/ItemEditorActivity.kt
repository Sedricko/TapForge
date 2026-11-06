package app.macro17

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
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
    private var names: Array<String> = emptyArray() // nomes dos passos para "N. nome" nas listas de destino
    private var scrollY = 0
    private lateinit var store: Store
    private var scroller: android.widget.ScrollView? = null

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        store = Store(this)
        s = Step.fromJson(JSONObject(intent.getStringExtra("step") ?: "{}"))
        total = intent.getIntExtra("total", 1); index = intent.getIntExtra("index", -1)
        names = intent.getStringArrayExtra("names") ?: emptyArray()
        if (s.type == Type.GROUP && !s.mono) { setContentView(android.view.View(this).apply { setBackgroundColor(android.graphics.Color.WHITE) }); groupDialog() } // grupo comum: só o diálogo "Grupo"
        else render()
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
    /** Resumo "N Segundos" / "Infinito" do base (Tempo de Pesquisa, duração do alerta e do grupo de imagem). */
    private fun segs(ms: Int) = if (ms == 0) "Infinito" else "${if (ms % 1000 == 0) "${ms / 1000}" else "${ms / 1000.0}"} Segundos"
    private fun areaSum(a: Int, b: Int, c: Int, d: Int) = "X:$a Y:$b W:${c - a} H:${d - b}"
    private fun devSum() = "X:${s.ox} Y:${s.oy}"
    private val rotNames = arrayOf("Retrato 0°", "Paisagem 90°", "Retrato 180°", "Paisagem 270°")
    private fun gotoLabel(n: Int) = if (n == 0) "0. Próximo" else "$n. ${names.getOrNull(n - 1).orEmpty()}".trim()
    private val speeds = listOf("Muito rápido" to 50, "Rápido" to 200, "1 Seg" to 1000, "2 Seg" to 2000, "3 Seg" to 3000) // shortcut: o base grava 0 em "Muito rápido"; aqui 50 ms para o laço não girar sem pausa
    private val searchMsg = "Definir o tempo de pesquisa como 0 significa pesquisa infinita. Definindo-o maior que 0 permite configurar para onde mover com base nos resultados da pesquisa. " +
        "Um tempo de pesquisa entre 0 e 1 segundo significa comparar imagens instantaneamente e usar imagens capturadas anteriormente."
    private val speedMsg = "Defina uma velocidade mais rápida para detectar imagens que mudam rapidamente."

    private val twoPoint = setOf(Type.SWIPE, Type.PINCH_IN, Type.PINCH_OUT)
    private fun kind() = when { s.type == Type.SWIPE -> "Deslizar"; s.type == Type.PINCH_OUT -> "Aumentar Zoom"; s.type == Type.PINCH_IN -> "Diminuir Zoom"; s.ms >= 500 -> "Pressionar"; else -> "Tocar" }

    /** Linha com ícone de 24dp na coluna da esquerda (o "Nome" de Gesto/Tecla/Texto/Grupo de imagem e o "Adicionar" do contador, como no base). */
    private fun iconRow(p: Prefs, icon: Int, title: String, summary: String, enabled: Boolean = true, click: (() -> Unit)? = null) {
        val r = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            if (enabled && click != null) { foreground = ripple(0, 0x1F00897B); setOnClickListener { click() } }
            alpha = if (enabled) 1f else 0.45f
            addView(FrameLayout(this@ItemEditorActivity).apply {
                addView(ImageView(this@ItemEditorActivity).apply { setImageResource(icon) }, FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER_VERTICAL))
            }, LinearLayout.LayoutParams(dp(56), WRAP_CONTENT))
            addView(LinearLayout(this@ItemEditorActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(label(title, 16f, B.TEXT))
                if (summary.isNotEmpty()) addView(label(summary, 14f, B.TEXT2))
            }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        }
        p.column.addView(r, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
    }

    private fun nameRow(p: Prefs, title: String, icon: Int? = null) {
        val click = { textDialog("Nome", s.name) { v -> s = s.copy(name = v.replace(',', '_').trim()); render() } }
        val shown = s.name.ifBlank { title }
        if (icon != null) iconRow(p, icon, "Nome", shown, true, click) else p.valueRow("Nome", shown) { click() }
    }

    /** Lista de velocidades sem botões: tocar escolhe e fecha, como as ListPreference do base. */
    private fun speedRow(p: Prefs, title: String, list: List<Pair<String, Int>>, msg: String?, enabled: Boolean = true) {
        p.valueRow(title, list.firstOrNull { it.second == s.cycle }?.first ?: "${s.cycle} ms", enabled) {
            AlertDialog.Builder(this).setTitle(title).apply { if (msg != null) setMessage(msg) }
                .setSingleChoiceItems(list.map { it.first }.toTypedArray(), list.indexOfFirst { it.second == s.cycle }) { d, w -> s = s.copy(cycle = list[w].second); d.dismiss(); render() }.show()
        }
    }

    /** "Tempo de Pesquisa": 0 = infinito; ao pôr 0 só o destino de falha é zerado (o de sucesso continua valendo, como no base). */
    private fun timeRow(p: Prefs, ms: Int, enabled: Boolean = true, set: (Int) -> Step) {
        p.valueRow("Tempo de Pesquisa", segs(ms), enabled) { secDialog("Tempo de Pesquisa (segundos)", searchMsg, ms) { v -> s = set(v); render() } }
    }
    private fun timeRow(p: Prefs, enabled: Boolean = true) = timeRow(p, s.ms, enabled) { v -> if (v == 0) s.copy(ms = 0, fail = 0) else s.copy(ms = v) }

    /** "Mover em Caso de Falha" só com tempo ≠ 0; "Mover em Caso de Sucesso" sempre (busca infinita e depois salta). */
    private fun moveRows(p: Prefs, ms: Int, enabled: Boolean = true) {
        p.valueRow("Mover em Caso de Falha", gotoLabel(s.fail), enabled && ms > 0) { gotoDialog("Mover em Caso de Falha", s.fail) { v -> s = s.copy(fail = v); render() } }
        p.valueRow("Mover em Caso de Sucesso", gotoLabel(s.goto), enabled) { gotoDialog("Mover em Caso de Sucesso", s.goto) { v -> s = s.copy(goto = v); render() } }
    }

    private fun areaPick(init: IntArray, set: (IntArray) -> Unit) = pick(PositionPickerActivity.AREA, init, ok = set)

    // --- tela --------------------------------------------------------------------------------

    private val simple = setOf(Type.WAIT, Type.KEY, Type.STATUS, Type.GOTO, Type.RESET, Type.CALL, Type.STOP, Type.COUNTER, Type.TEXT, Type.NODE, Type.IMAGE, Type.NUMBER, Type.CHANGE, Type.GESTURE, Type.GROUP)
    private val keyNames = mapOf(1 to "Tecla Voltar", 2 to "Tecla Home", 3 to "Tecla Recentes", 4 to "Tecla Voltar", KEY_SCREENSHOT to "Tecla de Captura de Tela")
    private val keyChoices = listOf(2, 1, 3, KEY_SCREENSHOT) // ordem do base: Home, Voltar, Recentes, Captura (a 4 de macros antigas aparece como Voltar)
    private val titleOf = mapOf(
        Type.WAIT to "Esperar", Type.KEY to "Evento de Teclado", Type.STATUS to "Alertas de status", Type.GOTO to "Ir para",
        Type.RESET to "Redefinir Contador de Execuções", Type.CALL to "Arquivo de Macro", Type.STOP to "Parar Macro",
        Type.COUNTER to "Contador de Execuções", Type.TEXT to "Reconhecimento de Texto", Type.NODE to "Achar por texto", Type.IMAGE to "Pesquisa de Imagem", Type.CHANGE to "Detecção de Mudança de Imagem", Type.GESTURE to "Gesto", Type.GROUP to "Grupo", Type.NUMBER to "Ler número",
    )

    /** Itens que não são toque: mesma estrutura (nome, categoria do tipo, valores, atraso), só com as linhas de cada tipo. */
    private fun buildSimple(p: Prefs) {
        val title = titleOf.getValue(s.type)
        val catIcon = mapOf(Type.GOTO to R.drawable.bp_action_goto, Type.COUNTER to R.drawable.bp_action_repeat_count, Type.RESET to R.drawable.bp_action_repeat_counter_zero, Type.CALL to R.drawable.bp_file_replace_outline)[s.type]
        val catNote = mapOf(Type.COUNTER to "Conta as execuções da macro e aciona ações específicas quando a contagem de execuções excede um número definido.",
            Type.RESET to "Selecione o contador de execuções para redefinir.", Type.CALL to "Abrir e reproduzir novo arquivo")[s.type]
        if (catIcon != null) { p.category(title, iconRes = catIcon); catNote?.let { p.note(it) } }
        if (s.type == Type.TEXT || s.type == Type.NUMBER) return buildOcr(p)
        if (s.type == Type.GESTURE) return buildGesture(p)
        if (s.type == Type.IMAGE) return buildImage(p)
        if (s.type == Type.GROUP) return buildImageGroup(p)
        if (s.type == Type.KEY) nameRow(p, title, R.drawable.bp_outline_keyboard_24)
        else if (s.type != Type.STATUS) nameRow(p, title)
        when (s.type) {
            Type.WAIT -> p.valueRow("Tempo de Espera", secs(s.ms)) { secDialog("Tempo de Espera (segundos)", null, s.ms) { v -> s = s.copy(ms = v); render() } }
            Type.KEY -> p.valueRow("Evento de Teclado", keyNames[s.x1] ?: "Tecla Voltar") {
                AlertDialog.Builder(this).setTitle("Evento de Teclado").setSingleChoiceItems(keyChoices.map { keyNames.getValue(it) }.toTypedArray(), keyChoices.indexOf(if (s.x1 == 4) 1 else s.x1)) { d, w ->
                    s = s.copy(x1 = keyChoices[w]); d.dismiss(); render()
                }.show()
            }
            Type.STATUS -> {
                p.category("Alertas de status", iconRes = R.drawable.bp_outline_notifications_active)
                p.note("Receba notificações sobre o progresso e o status de execução da macro.")
                p.valueRow("Nome", s.name.ifBlank { "Alerta de status" }) { textDialog("Nome", s.name) { v -> s = s.copy(name = v.replace(',', '_').trim()); render() } }
                // inteiro em segundos; 0 = Infinito (resumo "N Segundos")
                p.valueRow("Duração do alerta", segs(s.ms)) { numDialog("Duração do alerta (segundos)", null, s.ms / 1000) { v -> s = s.copy(ms = v * 1000); render() } }
                p.category("Som e vibração", iconRes = R.drawable.bp_music_cast_24px)
                p.switchRow("Vibração", "", s.vib) { v -> s = s.copy(vib = v); render() }
                p.switchRow("Som", "", s.snd) { v -> s = s.copy(snd = v); render() }
                p.valueRow("Selecionar Som", if (s.snu.isBlank()) "Padrão" else soundTitle(s.snu), s.snd) { soundDialog() }
                p.category("Caixa de diálogo", iconRes = R.drawable.bp_dialogs_24px)
                p.switchRow("Mostrar pop-up", "", s.pop) { v -> s = s.copy(pop = v); render() }
                p.valueRow("Texto da mensagem", s.text.ifBlank { "Alerta de status" }) { textDialog("Texto da mensagem", s.text) { v -> s = s.copy(text = v.trim()); render() } }
            }
            Type.GOTO -> p.valueRow("Ir para o item selecionado", gotoLabel(s.goto)) { gotoDialog("Ir para o item selecionado", s.goto) { v -> s = s.copy(goto = v); render() } }
            Type.RESET -> p.valueRow("Contador de Execuções", "Contador ${s.counter}") {
                AlertDialog.Builder(this).setTitle("Contador de Execuções").setSingleChoiceItems(Array(MAX_COUNTERS) { "Contador ${it + 1}" }, (s.counter - 1).coerceIn(0, MAX_COUNTERS - 1)) { d, w ->
                    s = s.copy(counter = w + 1); d.dismiss(); render()
                }.show()
            }
            Type.COUNTER -> {
                // Um bloco por caso (ícone de rota), "Adicionar" e "Outro Caso", como no base; o contador usado fica numa linha no fim.
                s.cases.forEachIndexed { k, c ->
                    p.category("Caso ${k + 1}", iconRes = if (k < 2) R.drawable.bp_ic_route_24dp_case else R.drawable.bp_ic_delete_24px)
                    p.valueRow("Contador de Execuções", "N ≥ ${c.n}") { numDialog("Contador de Execuções", null, c.n) { v -> s = s.copy(cases = s.cases.mapIndexed { j, x -> if (j == k) x.copy(n = v) else x }); render() } }
                    p.valueRow("Ir para o item selecionado", gotoLabel(c.to)) { gotoDialog("Ir para o item selecionado", c.to) { v -> s = s.copy(cases = s.cases.mapIndexed { j, x -> if (j == k) x.copy(to = v) else x }); render() } }
                    if (k >= 2) p.valueRow("Remover caso", "Excluir o caso ${k + 1}") { s = s.copy(cases = s.cases.filterIndexed { j, _ -> j != k }); render() }
                }
                if (s.cases.size < MAX_CASES) iconRow(p, R.drawable.bp_icn_add_plus, "Adicionar", "") { s = s.copy(cases = s.cases + Case((s.cases.maxOfOrNull { it.n } ?: 0) + 1, 0)); render() }
                p.category("Outro Caso", iconRes = R.drawable.bp_ic_route_24dp_default)
                p.valueRow("Ir para o item selecionado", gotoLabel(s.goto)) { gotoDialog("Ir para o item selecionado", s.goto) { v -> s = s.copy(goto = v); render() } }
                p.valueRow("Contador usado", "Contador ${s.counter}") {
                    AlertDialog.Builder(this).setTitle("Contador usado").setSingleChoiceItems(Array(MAX_COUNTERS) { "Contador ${it + 1}" }, (s.counter - 1).coerceIn(0, MAX_COUNTERS - 1)) { d, w ->
                        s = s.copy(counter = w + 1); d.dismiss(); render()
                    }.show()
                }
            }
            Type.NODE -> {
                p.valueRow("Texto ou id", s.text.ifBlank { "(vazio)" }) { textDialog("Texto ou id:recurso", s.text) { v -> s = s.copy(text = v.trim()); render() } }
                p.switchRow("Texto exato", "Só vale se o texto for igual, e não apenas contido.", s.exact) { v -> s = s.copy(exact = v); render() }
                timeRow(p)
                speedRow(p, "Velocidade de Pesquisa", speeds, null)
                p.switchRow("Tocar ao achar", "Toca no centro do que foi encontrado.", s.tap) { v -> s = s.copy(tap = v); render() }
                p.valueRow("Desvio de Coordenadas", devSum(), s.tap) {
                    devDialog(null)
                }
                moveRows(p, s.ms)
            }
            Type.CHANGE -> {
                // pref_image_change_detection_config: Nome, Área Personalizada, Diferença de Imagens, Velocidade, Tempo, Falha, Sucesso.
                val whole = s.x2 <= s.x1
                p.valueRow("Área Personalizada", if (whole) "X:0 Y:0 W:0 H:0 (tela cheia)" else areaSum(s.x1, s.y1, s.x2, s.y2)) {
                    areaPick(intArrayOf(s.x1, s.y1, s.x2, s.y2)) { v -> s = s.copy(x1 = v[0], y1 = v[1], x2 = v[2], y2 = v[3]) }
                }
                // o base guarda a diferença; o modelo guarda a semelhança exigida (100 - diferença)
                p.valueRow("Diferença de Imagens", "${100 - s.thr}%") { numDialog("Diferença de Imagens (%)", "A imagem é considerada mudada quando a diferença passa deste valor. De 0 a 99.", 100 - s.thr) { v -> s = s.copy(thr = (100 - v).coerceIn(1, 100)); render() } }
                speedRow(p, "Velocidade de Pesquisa de Imagem", speeds, speedMsg)
                timeRow(p)
                moveRows(p, s.ms)
            }
            Type.STOP -> p.note("Encerra a reprodução da macro neste ponto.")
            else -> { // CALL: ordem do base (Nome, voltar ao arquivo anterior, arquivo)
                val target = store.get(s.text)
                p.switchRow("Voltar ao arquivo anterior", "Retorna ao arquivo anterior quando esta macro estiver completa.", s.ret) { v -> s = s.copy(ret = v); render() }
                p.valueRow("Arquivo de Macro", target?.name ?: "Nenhum arquivo alocado.") {
                    val others = store.list().filter { it.id != intent.getStringExtra("self") }
                    if (others.isEmpty()) toast("Nenhum arquivo de macro")
                    else AlertDialog.Builder(this).setTitle("Arquivo de Macro").setItems(others.map { it.name }.toTypedArray()) { _, w -> s = s.copy(text = others[w].id); render() }.show()
                }
            }
        }
        if (s.type == Type.GOTO || s.type == Type.KEY) delayBlock(p)
    }

    /** Gesto (pref_gesture_config): Nome, "Duração do Gesto" só leitura, categoria Atraso. */
    private fun buildGesture(p: Prefs) {
        nameRow(p, "Gesto", R.drawable.bp_ic_gesture)
        p.valueRow("Duração do Gesto", "%.1f Segundos".format(java.util.Locale.US, s.ms / 1000.0))
        delayBlock(p)
    }

    /** Pesquisa de Imagem: a estrutura do item Tocar com imagem do base (Correspondência de Imagem + Ação de Toque), com os extras do Macro17 no fim da categoria. */
    private fun buildImage(p: Prefs) {
        nameRow(p, "Pesquisa de Imagem")
        p.category("Correspondência de Imagem", iconRes = R.drawable.bp_outline_image_search_24)
        p.valueRow("Imagem", if (s.text.isBlank()) "Sem imagem: capture pelo painel." else "Imagem capturada (não editável aqui).")
        val whole = listOf(s.x1, s.y1, s.x2, s.y2).all { it == 0 }
        val names4 = arrayOf("Tela Cheia", "Tela Cheia (Escala de Cinza)", "Área Personalizada", "Área Personalizada (Escala de Cinza)")
        val info4 = arrayOf("Procura imagens na tela cheia.", "Procura imagens na tela cheia. A velocidade de pesquisa de imagem é mais rápida.", "Pesquisa de imagem em área personalizada.", "Pesquisa de imagem em área personalizada. A velocidade de pesquisa de imagem é mais rápida.")
        val idx = (if (whole) 0 else 2) + (if (s.mono) 1 else 0)
        p.valueRow("Área de Pesquisa", names4[idx] + "\n" + info4[idx]) {
            val adapter = object : android.widget.ArrayAdapter<String>(this, android.R.layout.simple_list_item_single_choice, names4.toList()) {
                override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View =
                    (super.getView(position, convertView, parent) as android.widget.CheckedTextView).also { it.text = names4[position] + "\n" + info4[position] }
            }
            AlertDialog.Builder(this).setTitle("Área de Pesquisa").setSingleChoiceItems(adapter, idx) { d, w ->
                val gray = w % 2 == 1
                s = if (w < 2) s.copy(x1 = 0, y1 = 0, x2 = 0, y2 = 0, mono = gray) else s.copy(mono = gray, x1 = if (whole) 0 else s.x1, y1 = if (whole) 0 else s.y1, x2 = if (whole) 500 else s.x2, y2 = if (whole) 500 else s.y2)
                d.dismiss(); render()
            }.show()
        }
        if (!whole) p.valueRow("Área Personalizada", areaSum(s.x1, s.y1, s.x2, s.y2)) {
            areaPick(intArrayOf(s.x1, s.y1, s.x2, s.y2)) { v -> s = s.copy(x1 = v[0], y1 = v[1], x2 = v[2], y2 = v[3]) }
        }
        p.valueRow("Taxa de correspondência de imagem (%)", "${s.thr}%") { numDialog("Taxa de correspondência de imagem (%)", null, s.thr) { v -> s = s.copy(thr = v.coerceIn(0, 100)); render() } }
        speedRow(p, "Velocidade de Pesquisa de Imagem", speeds, speedMsg)
        timeRow(p)
        moveRows(p, s.ms)
        p.switchRow("Várias escalas", "Procura também a imagem maior ou menor.", s.scales) { v -> s = s.copy(scales = v); render() }
        p.valueRow("Confirmações seguidas", "${s.confirm}") { numDialog("Confirmações seguidas", "De 1 a 5 quadros seguidos com a imagem.", s.confirm) { v -> s = s.copy(confirm = v); render() } }
        p.category("Ação de Toque", iconRes = R.drawable.bp_click_traffic)
        p.switchRow("Pular", "Tocar/Deslizar/Pressionar não funcionará. Usado para atraso e comparação de imagens.", !s.tap) { v -> s = s.copy(tap = !v); render() }
        p.valueRow("Desvio de Coordenadas", devSum()) {
            devDialog(s.text)
        }
    }

    /**
     * Reconhecimento de Texto e de Números (pref_text_recognition_config): os dois interruptores são exclusivos; ligar o de números
     * converte o item em Ler número (e vice-versa), pois o modelo tem um tipo para cada.
     */
    private fun buildOcr(p: Prefs) {
        val num = s.type == Type.NUMBER
        nameRow(p, if (num) "Ler número" else "Reconhecimento de Texto", R.drawable.bp_format_text)
        if (!num) delayBlock(p)
        p.category("Reconhecimento de Texto", iconRes = R.drawable.bp_format_text)
        p.switchRow("Reconhece o texto na tela e encontra o mesmo texto.", "", !num) { on ->
            if (on && num) s = s.copy(type = Type.TEXT, cycle = maxOf(s.cycle, 200), x1 = 0, y1 = 0, x2 = 0, y2 = 0)
            render()
        }
        p.valueRow("Texto", s.text.ifBlank { "(vazio)" }, !num) { textDialog("Texto", s.text, "Use uma vírgula como separador para procurar várias palavras simultaneamente. ex.) Maçã, Abacaxi") { v -> s = s.copy(text = v.trim()); render() } }
        p.valueRow("Idioma", "English", false)
        if (!num) p.switchRow("Texto exato", "Só vale se o texto for igual, e não apenas contido.", s.exact) { v -> s = s.copy(exact = v); render() }
        p.category("Reconhecimento de Números", iconRes = R.drawable.bp_icn_number_24px)
        p.switchRow("Reconhece números na tela e compara se estão dentro de um intervalo numérico.", "", num) { on ->
            if (on && !num) {
                val dm = resources.displayMetrics
                val whole = s.x2 <= s.x1
                s = s.copy(type = Type.NUMBER, cmp = 3, x1 = if (whole) 0 else s.x1, y1 = if (whole) 0 else s.y1, x2 = if (whole) dm.widthPixels else s.x2, y2 = if (whole) dm.heightPixels else s.y2)
            }
            render()
        }
        val ops = arrayOf("Maior ou igual (≥)", "Menor ou igual (≤)", "Igual (=)", "Entre")
        p.valueRow("Intervalo", if (s.cmp == 3) "${s.lo} ~ ${s.hi}" else "${ops[s.cmp.coerceIn(0, 3)]} ${s.lo}", num) {
            fieldsDialog("Intervalo", null, listOf("Número Mínimo", "Número Máximo"), listOf(s.lo, s.hi), signed = false, helper = null) { v -> s = s.copy(cmp = 3, lo = v[0], hi = maxOf(v[0], v[1])); render() }
        }
        if (num) p.valueRow("Condição", ops[s.cmp.coerceIn(0, 3)]) { // extra do Macro17: ≥, ≤, = além de "Entre"
            AlertDialog.Builder(this).setTitle("Condição").setSingleChoiceItems(ops, s.cmp.coerceIn(0, 3)) { d, w -> s = s.copy(cmp = w); d.dismiss(); render() }.show()
        }
        p.category("Opção de Busca", iconRes = R.drawable.bp_outline_image_search_24)
        speedRow(p, "Velocidade de Pesquisa de Imagem", if (num) speeds else speeds.filter { it.second >= 200 }, null) // o reconhecimento de texto exige ciclo de pelo menos 200 ms
        timeRow(p)
        moveRows(p, s.ms)
        val whole = s.x2 <= s.x1
        if (!num) {
            p.valueRow("Área de Pesquisa", if (whole) "Tela Cheia\nProcura imagens na tela cheia." else "Área Personalizada\nPesquisa de imagem em área personalizada.") {
                AlertDialog.Builder(this).setTitle("Área de Pesquisa").setSingleChoiceItems(arrayOf("Tela Cheia", "Área Personalizada"), if (whole) 0 else 1) { d, w ->
                    s = if (w == 0) s.copy(x1 = 0, y1 = 0, x2 = 0, y2 = 0) else if (whole) s.copy(x1 = 0, y1 = 0, x2 = 500, y2 = 500) else s
                    d.dismiss(); render()
                }.show()
            }
        }
        if (num || !whole) p.valueRow("Área Personalizada", areaSum(s.x1, s.y1, s.x2, s.y2)) {
            areaPick(intArrayOf(s.x1, s.y1, s.x2, s.y2)) { v -> s = s.copy(x1 = v[0], y1 = v[1], x2 = v[2], y2 = v[3]) }
        }
        if (!num) {
            p.valueRow("Limiar de Pontuação de Confiança", "${s.minConf}") { numDialog("Limiar de Pontuação de Confiança", "Caracteres com pontuações de confiança abaixo do limiar serão excluídos. De 0 a 90.", s.minConf) { v -> s = s.copy(minConf = v.coerceIn(0, 90)); render() } }
            p.category("Ação de Toque", iconRes = R.drawable.bp_click_traffic)
            p.switchRow("Pular", "Tocar/Deslizar/Pressionar não funcionará. Usado para atraso e comparação de imagens.", !s.tap) { v -> s = s.copy(tap = !v); render() }
            p.valueRow("Desvio de Coordenadas", devSum()) {
                devDialog(null)
            }
        }
    }

    /** Grupo de Pesquisa de Imagem (pref_image_group_config): Nome, duração e script de expiração. O grupo comum só tem o diálogo "Grupo" ([groupDialog]). */
    private fun buildImageGroup(p: Prefs) {
        nameRow(p, "Grupo de Pesquisa de Imagem", R.drawable.bp_ic_loop_image)
        iconRow(p, R.drawable.bp_outline_image_search_24, "Duração do Grupo de Pesquisa de Imagens", segs(s.gms)) {
            secDialog("Duração do Grupo de Pesquisa de Imagens (segundos)", "0 significa infinito. Ao expirar, o grupo é abandonado.", s.gms) { v -> s = s.copy(gms = v); render() }
        }
        iconRow(p, R.drawable.bp_arrow_right_default, "Script quando a duração da pesquisa expira", gotoLabel(s.gfail), s.gms > 0) {
            gotoDialog("Script quando a duração da pesquisa expira", s.gfail) { v -> s = s.copy(gfail = v); render() }
        }
    }

    /** Diálogo "Grupo" do base: Nome e "Repetir reprodução" (0 = infinito). OK salva e volta; Cancelar descarta. */
    private fun groupDialog() {
        val nm = box("Nome", s.name.ifBlank { "Grupo" }, InputType.TYPE_CLASS_TEXT)
        val rp = box("Repetir reprodução", s.rep.toString(), InputType.TYPE_CLASS_NUMBER)
        AlertDialog.Builder(this).setTitle("Grupo").setView(column(null, nm, rp)).setCancelable(false)
            .setPositiveButton("OK") { _, _ ->
                val r = rp.text.toString().toIntOrNull()
                if (r == null) { toast("Número inválido"); groupDialog() } else { s = s.copy(name = nm.text.toString().replace(',', '_').trim(), rep = r); leave() }
            }
            .setNegativeButton("Cancelar") { _, _ -> finish() }.show()
    }

    /** "Atraso" do base: só em Toque/Deslizar, Ir para, Tecla, Gesto e Reconhecimento de Texto; em Tecla é só o tempo, sem categoria nem interruptor. */
    private fun delayBlock(p: Prefs) {
        val delayOn = s.delay > 0
        if (s.type != Type.KEY) p.category("Atraso", iconRes = if (s.type == Type.GOTO) R.drawable.bp_ic_timer else R.drawable.bp_alarm)
        if (s.type != Type.KEY) p.switchRow(if (s.type == Type.GOTO) "Realizar ação após o tempo de atraso." else "Atraso", if (s.type == Type.GOTO) "" else "Tocar/Deslizar será processado após o tempo de atraso.", delayOn) { on -> s = s.copy(delay = if (on) 1000 else 0, rnd = 0, rndMin = 0); render() }
        p.valueRow("Tempo de Atraso", secs(s.delay), s.type == Type.KEY || s.type == Type.GESTURE || delayOn) { secDialog("Tempo de Atraso (segundos)", null, s.delay) { v -> s = s.copy(delay = v); render() } }
    }

    private fun build(p: Prefs) {
        if (s.type in simple) return buildSimple(p)
        val swipe = s.type in twoPoint
        nameRow(p, kind())

        p.category(kind(), iconRes = R.drawable.bp_ic_gesture_swipe)
        p.valueRow("Posição do Toque", (if (swipe) "(${s.x1},${s.y1}) (${s.x2},${s.y2})" else "(${s.x1},${s.y1})") + " " + rotNames[s.rot.coerceIn(0, 3)]) { positionDialog() }
        // em segundos, como no base; oculta em Tocar (Pressionar = Toque com 0,5 s ou mais: aqui o Toque e o Pressionar são o mesmo tipo)
        if (s.type != Type.TAP || s.ms >= 500) p.valueRow("Duração do Gesto", secs(s.ms)) {
            secDialog("Duração do Gesto (segundos)", "De 0,1 a 30 s.", s.ms) { v -> s = s.copy(ms = v.coerceIn(100, 30_000)); render() }
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
        val areaNames = arrayOf("Posição Fixa", "Tela Cheia", "Tela Cheia (Escala de Cinza)", "Área Personalizada", "Área Personalizada (Escala de Cinza)")
        val areaDesc = arrayOf("Compara imagens em uma posição fixa.", "Procura imagens na tela cheia.", "Procura imagens na tela cheia. A velocidade de pesquisa de imagem é mais rápida.", "Pesquisa de imagem em área personalizada.", "Pesquisa de imagem em área personalizada. A velocidade de pesquisa de imagem é mais rápida.")
        val areaIdx = if (s.fx) 0 else if (whole) (if (s.mono) 2 else 1) else (if (s.mono) 4 else 3)
        p.valueRow("Área de Pesquisa", areaNames[areaIdx] + "\n" + areaDesc[areaIdx], on) {
            AlertDialog.Builder(this).setTitle("Área de Pesquisa")
                .setSingleChoiceItems(areaNames, areaIdx) { d, w ->
                    s = when (w) {
                        0 -> s.copy(fx = true, ax1 = 0, ay1 = 0, ax2 = 0, ay2 = 0)
                        1, 2 -> s.copy(fx = false, ax1 = 0, ay1 = 0, ax2 = 0, ay2 = 0, mono = w == 2)
                        else -> s.copy(fx = false, mono = w == 4, ax1 = if (whole) 0 else s.ax1, ay1 = if (whole) 0 else s.ay1, ax2 = if (whole) 500 else s.ax2, ay2 = if (whole) 500 else s.ay2)
                    }
                    d.dismiss(); render()
                }.show()
        }
        if (!whole && !s.fx) p.valueRow("Área Personalizada", areaSum(s.ax1, s.ay1, s.ax2, s.ay2), on) {
            areaPick(intArrayOf(s.ax1, s.ay1, s.ax2, s.ay2)) { v -> s = s.copy(ax1 = v[0], ay1 = v[1], ax2 = v[2], ay2 = v[3]) }
        }
        p.valueRow("Taxa de correspondência de imagem (%)", "${s.thr}%", on) {
            numDialog("Taxa de correspondência de imagem (%)", null, s.thr) { v -> s = s.copy(thr = v.coerceIn(0, 100)); render() }
        }
        speedRow(p, "Velocidade de Pesquisa de Imagem", speeds, speedMsg, on)
        timeRow(p, s.sms, on) { v -> if (v == 0) s.copy(sms = 0, fail = 0) else s.copy(sms = v) }
        moveRows(p, s.sms, on)

        p.category("Ação de Toque", iconRes = R.drawable.bp_click_traffic)
        p.switchRow("Pular", "Tocar/Deslizar/Pressionar não funcionará. Usado para atraso e comparação de imagens.", s.skip) { v -> s = s.copy(skip = v); render() }
        if (s.type == Type.TAP && s.ms < 500) p.switchRow("Clique Duplo", "Clica no mesmo lugar duas vezes.", s.dbl) { v -> s = s.copy(dbl = v); render() }
        if (on) p.valueRow("Desvio de Coordenadas", devSum()) {
            devDialog(s.img.ifBlank { null })
        }

        p.category("Evasão de Detecção", iconRes = R.drawable.bp_icn_hide_source_24px)
        p.valueRow("Coordenadas Aleatórias (Coordenadas ± Valor de Pixel Aleatório)", "${s.jitter} pixels") {
            // pref_pixel_range_slider_dialog: texto + controle deslizante de 0 a 100 px
            val vt = TextView(this).apply { text = "${s.jitter.coerceIn(0, 100)} px"; textSize = 16f; gravity = android.view.Gravity.END }
            val sb = android.widget.SeekBar(this).apply {
                max = 100; progress = s.jitter.coerceIn(0, 100)
                setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(b: android.widget.SeekBar, v: Int, u: Boolean) { vt.text = "$v px" }
                    override fun onStartTrackingTouch(b: android.widget.SeekBar) {}
                    override fun onStopTrackingTouch(b: android.widget.SeekBar) {}
                })
            }
            AlertDialog.Builder(this).setTitle("Coordenadas Aleatórias").setView(column("Raio para clicar aleatoriamente. Defina como 0 para desativar.", sb, vt))
                .setPositiveButton("OK") { _, _ -> s = s.copy(jitter = sb.progress); render() }.setNegativeButton("Cancelar", null).show()
        }
        p.valueRow("Atraso Aleatório", if (s.rnd > 0) "${secs(s.rndMin)} a ${secs(s.rnd)}" else "Desligado", delayOn) {
            fieldsDialog(
                "Atraso Aleatório (segundos)",
                "A ação será realizada após o atraso aleatório especificado. O atraso acima será ignorado.",
                listOf("Atraso Mínimo", "Atraso Máximo"), listOf(s.rndMin, s.rnd), signed = false, decimal = true,
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

    private fun textDialog(title: String, init: String, msg: String? = null, ok: (String) -> Unit) {
        val et = box(title, init, InputType.TYPE_CLASS_TEXT)
        AlertDialog.Builder(this).setTitle(title).setView(column(msg, et)).setPositiveButton("OK") { _, _ -> ok(et.text.toString()) }.setNegativeButton("Cancelar", null).show()
    }

    /** Campo no formato do base (TextInputLayout): dica, texto de ajuda embaixo e contador n/10. */
    private fun mfield(hint: String, init: String, type: Int, helper: String?, counter: Boolean = true): Pair<android.view.View, EditText> {
        val et = box(hint, init, type).apply { filters = arrayOf(android.text.InputFilter.LengthFilter(10)) }
        val cnt = TextView(this).apply { text = "${init.length}/10"; textSize = 12f; setTextColor(0xFF757575.toInt()) }
        et.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(e: android.text.Editable?) { cnt.text = "${e?.length ?: 0}/10" }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        val foot = LinearLayout(this).apply {
            addView(TextView(this@ItemEditorActivity).apply { text = helper.orEmpty(); textSize = 12f; setTextColor(0xFF757575.toInt()) }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            if (counter) addView(cnt)
        }
        return LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; addView(et); addView(foot); setPadding(0, 0, 0, dp(8)) } to et
    }

    private fun numDialog(title: String, msg: String?, init: Int, ok: (Int) -> Unit) {
        val (v, et) = mfield(title, init.toString(), InputType.TYPE_CLASS_NUMBER, null)
        AlertDialog.Builder(this).setTitle(title).setView(column(msg, v))
            .setPositiveButton("OK") { _, _ -> et.text.toString().toIntOrNull()?.let(ok) ?: toast("Número inválido") }.setNegativeButton("Cancelar", null).show()
    }

    /** Segundos com casas decimais na tela, milissegundos no modelo (hint "Segundos" e faixa 0.0~300.0, como no base). */
    private fun secDialog(title: String, msg: String?, initMs: Int, ok: (Int) -> Unit) {
        val infinite = msg?.contains("infinit") == true // tempo de pesquisa: "0 significa infinito", sem contador
        val (v, et) = mfield(if (infinite) "0 significa infinito" else "Segundos", (initMs / 1000.0).toString(), InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL, if (infinite) null else "0.0~${MAX_DELAY_MS / 1000.0}", counter = !infinite)
        AlertDialog.Builder(this).setTitle(title).setView(column(msg, v))
            .setPositiveButton("OK") { _, _ -> et.text.toString().replace(',', '.').toDoubleOrNull()?.let { ok(Math.round(it * 1000).toInt()) } ?: toast("Número inválido") }
            .setNegativeButton("Cancelar", null).show()
    }

    /** Vários números de uma vez. Com [decimal], os valores aparecem em segundos e voltam em milissegundos. */
    private fun fieldsDialog(title: String, msg: String?, labels: List<String>, init: List<Int>, signed: Boolean, decimal: Boolean = false, helper: String? = if (decimal) "Segundos" else "Pixel", ok: (List<Int>) -> Unit) {
        val kind = InputType.TYPE_CLASS_NUMBER or (if (signed) InputType.TYPE_NUMBER_FLAG_SIGNED else 0) or (if (decimal) InputType.TYPE_NUMBER_FLAG_DECIMAL else 0)
        val fs = labels.mapIndexed { i, l -> mfield(l, if (decimal) (init[i] / 1000.0).toString() else init[i].toString(), kind, helper) }
        val dlg = AlertDialog.Builder(this).setTitle(title).setView(column(msg, *fs.map { it.first }.toTypedArray()))
            .setPositiveButton("OK") { _, _ ->
                val v = fs.map { e -> e.second.text.toString().replace(',', '.').toDoubleOrNull()?.let { if (decimal) Math.round(it * 1000).toInt() else it.toInt() } }
                if (v.any { it == null }) toast("Número inválido") else ok(v.map { it!! })
            }.setNegativeButton("Cancelar", null)
        // Atraso aleatório: botão "Definir como Desabilitado" (zera mínimo e máximo), como no base.
        if (decimal && labels.size == 2) dlg.setNeutralButton("Definir como Desabilitado") { _, _ -> ok(listOf(0, 0)) }
        dlg.show()
    }

    /** Diálogo com vários campos numéricos com sinal; devolve o construtor e os campos para o chamador ligar os botões. */
    private fun fieldsBox(title: String, labels: List<String>, init: List<Int>): Pair<AlertDialog.Builder, List<EditText>> {
        val fs = labels.mapIndexed { k, l -> mfield(l, init[k].toString(), InputType.TYPE_CLASS_NUMBER, null) }
        return AlertDialog.Builder(this).setTitle(title).setView(column(null, *fs.map { it.first }.toTypedArray())) to fs.map { it.second }
    }

    // --- som do alerta ("Selecionar Som" do base): lista de sons do sistema com marca e botão de ouvir ----------

    private fun soundTitle(uri: String): String = runCatching { android.media.RingtoneManager.getRingtone(this, android.net.Uri.parse(uri)).getTitle(this) }.getOrDefault("Padrão")

    private fun soundDialog() {
        val rm = android.media.RingtoneManager(this).apply { setType(android.media.RingtoneManager.TYPE_ALL) }
        val cur = rm.cursor
        val names = ArrayList<String>(); val uris = ArrayList<String>()
        while (cur.moveToNext()) { names.add(cur.getString(android.media.RingtoneManager.TITLE_COLUMN_INDEX)); uris.add(rm.getRingtoneUri(cur.position).toString()) }
        var playing: android.media.Ringtone? = null
        var chosen = uris.indexOf(s.snu)
        val list = android.widget.ListView(this)
        list.adapter = object : android.widget.BaseAdapter() {
            override fun getCount() = names.size
            override fun getItem(i: Int) = names[i]
            override fun getItemId(i: Int) = i.toLong()
            override fun getView(i: Int, v: android.view.View?, p: android.view.ViewGroup) = LinearLayout(this@ItemEditorActivity).apply {
                gravity = android.view.Gravity.CENTER_VERTICAL; setPadding(0, dp(4), 0, dp(4))
                addView(android.widget.CheckBox(this@ItemEditorActivity).apply { isChecked = i == chosen; isClickable = false; isFocusable = false })
                addView(TextView(this@ItemEditorActivity).apply { text = names[i]; textSize = 18f; setTextColor(0xFF607D8B.toInt()); setSingleLine() }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                addView(android.widget.ImageButton(this@ItemEditorActivity).apply {
                    setImageResource(R.drawable.bp_volume_high); setBackgroundColor(0); contentDescription = "Ouvir"
                    setOnClickListener { playing?.stop(); playing = android.media.RingtoneManager.getRingtone(this@ItemEditorActivity, android.net.Uri.parse(uris[i])).also { it.play() } }
                }, LinearLayout.LayoutParams(dp(40), dp(40)))
            }
        }
        list.setOnItemClickListener { _, _, i, _ -> chosen = i; (list.adapter as android.widget.BaseAdapter).notifyDataSetChanged() }
        AlertDialog.Builder(this).setTitle("Selecionar Som").setView(list)
            .setPositiveButton("OK") { _, _ -> if (chosen >= 0) { s = s.copy(snu = uris[chosen]); render() } }
            .setNegativeButton("Cancelar", null).setOnDismissListener { playing?.stop() }.show()
    }

    /** "Desvio de Coordenadas" do base: explicação e Não usar / Configurar (abre o seletor). */
    private fun devDialog(tpl: String?) {
        AlertDialog.Builder(this).setTitle("Desvio de Coordenadas")
            .setMessage("Esta função permite tocar em um local diferente da posição da imagem. Defina o desvio das coordenadas para onde tocar a partir da posição da imagem encontrada pela função de pesquisa.")
            .setNegativeButton("Não usar") { _, _ -> s = s.copy(ox = 0, oy = 0); render() }
            .setPositiveButton("Configurar") { _, _ -> pick(PositionPickerActivity.DEV, intArrayOf(s.ox, s.oy), tpl) { v -> s = s.copy(ox = v[0], oy = v[1]) } }.show()
    }

    private fun positionDialog() {
        val sw = s.type in twoPoint
        pick(PositionPickerActivity.POS, intArrayOf(s.x1, s.y1, s.x2, s.y2), swipe = sw) { v ->
            s = if (sw) s.copy(x1 = v[0], y1 = v[1], x2 = v[2], y2 = v[3]) else s.copy(x1 = v[0], y1 = v[1], x2 = v[0], y2 = v[1])
        }
    }

    private var pickCb: ((IntArray) -> Unit)? = null
    /** Abre o seletor em tela cheia do base (posição, área ou desvio) e aplica o resultado. */
    private fun pick(mode: Int, init: IntArray, tpl: String? = null, swipe: Boolean = false, ok: (IntArray) -> Unit) {
        pickCb = ok
        startActivityForResult(Intent(this, PositionPickerActivity::class.java).putExtra("mode", mode).putExtra("v", init).putExtra("tpl", tpl).putExtra("swipe", swipe), 77)
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req == 77 && res == RESULT_OK) data?.getIntArrayExtra("v")?.let { pickCb?.invoke(it); render() }
    }

    /** 0 = seguir para o próximo; 1..total = ir para esse passo. */
    private fun gotoDialog(title: String, current: Int, ok: (Int) -> Unit) {
        // Como no base: "1. nome" … "N. nome" e, por último, "0. Próximo".
        val items = Array(total + 1) { gotoLabel(if (it < total) it + 1 else 0) }
        AlertDialog.Builder(this).setTitle(title).setSingleChoiceItems(items, if (current.coerceIn(0, total) == 0) total else current - 1) { d, w -> ok(if (w < total) w + 1 else 0); d.dismiss() }.show()
    }

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_LONG).show()
}

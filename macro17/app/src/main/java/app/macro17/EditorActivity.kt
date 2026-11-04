package app.macro17

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.InsetDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.ImageView
import android.graphics.Bitmap
import android.content.res.ColorStateList
import android.text.InputType
import android.view.WindowInsets
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.util.UUID

@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
class EditorActivity : Activity() {
    private lateinit var store: Store
    private lateinit var id: String
    private lateinit var name: EditText
    private lateinit var loops: EditText
    private lateinit var list: ListView
    private lateinit var emptyHint: TextView
    private var steps: List<Step> = emptyList()

    // Campos numéricos do diálogo, em um IntArray: índices abaixo. Casos do contador: 11.. em pares (limiar, destino).
    private val X1 = 0; private val Y1 = 1; private val X2 = 2; private val Y2 = 3; private val MS = 4
    private val DELAY = 5; private val RND = 6; private val JIT = 7; private val RMIN = 8; private val GOTO = 9; private val CNT = 10; private val CASE0 = 11
    private val THR = CASE0 + MAX_CASES * 2; private val CYC = THR + 1; private val FAIL = THR + 2
    private val OX = FAIL + 20; private val OY = FAIL + 21; private val MINC = FAIL + 22
    private val CMP = FAIL + 1; private val LO = FAIL + 2; private val HI = FAIL + 3; private val CONF = FAIL + 4; private val METH = FAIL + 5

    private val labels = mapOf(
        X1 to "X", Y1 to "Y", X2 to "X final", Y2 to "Y final", MS to "Duração (ms)",
        DELAY to "Atraso antes da ação (ms)", RND to "Atraso aleatório: máximo (ms, 0 = desligado)", RMIN to "Atraso aleatório: mínimo (ms)",
        JIT to "Jitter (px)", GOTO to "Ir para o passo (0 = seguir)", CNT to "Contador (1 a $MAX_COUNTERS)",
        OX to "Deslocamento do toque X (px; negativo = esquerda)", OY to "Deslocamento do toque Y (px; negativo = acima)", MINC to "Confiança mínima do reconhecimento (0 a 100%; 0 = sem filtro)",
        THR to "Semelhança mínima (20 a 100%): % de pixels que precisam bater com o modelo", CYC to "Intervalo entre tentativas (ms)", FAIL to "Se não achar, ir para o passo (0 = seguir)",
        METH to "Método (0 = correlação, como o app original; 1 = diferença de pixels)", CONF to "Confirmações seguidas, no mesmo lugar (1 a 5; 1 = como o original)", CMP to "Condição (0 ≥, 1 ≤, 2 =, 3 entre)", LO to "Valor (ou o mínimo, se “entre”)", HI to "Máximo (só para “entre”)",
    )
    private val common = intArrayOf(DELAY, RMIN, RND)
    private val fields = mapOf(
        Type.TAP to intArrayOf(X1, Y1, MS, JIT) + common,
        Type.SWIPE to intArrayOf(X1, Y1, X2, Y2, MS, JIT) + common,
        Type.PINCH_IN to intArrayOf(X1, Y1, X2, Y2, MS, JIT) + common,
        Type.PINCH_OUT to intArrayOf(X1, Y1, X2, Y2, MS, JIT) + common,
        Type.WAIT to intArrayOf(MS) + common,
        Type.KEY to intArrayOf(X1) + common,
        Type.STATUS to intArrayOf(MS) + common,
        Type.GOTO to intArrayOf(GOTO) + common,
        Type.COUNTER to intArrayOf(CNT, GOTO) + IntArray(MAX_CASES * 2) { CASE0 + it } + common,
        Type.RESET to intArrayOf(CNT) + common,
        Type.CALL to common,
        Type.IMAGE to intArrayOf(X1, Y1, X2, Y2, MS, CYC, THR, METH, CONF, GOTO, FAIL, OX, OY) + common,
        Type.NODE to intArrayOf(MS, CYC, GOTO, FAIL, OX, OY) + common,
        Type.TEXT to intArrayOf(X1, Y1, X2, Y2, MS, CYC, MINC, GOTO, FAIL, OX, OY) + common,
        Type.NUMBER to intArrayOf(X1, Y1, X2, Y2, MS, CYC, CMP, LO, HI, GOTO, FAIL) + common,
        Type.STOP to common,
        Type.CHANGE to common,
        Type.GESTURE to common,
        Type.GROUP to common,
    )
    private val optional = setOf(DELAY, RND, JIT, RMIN, GOTO, FAIL, CMP, LO, HI, OX, OY, MINC) + (CASE0 until CASE0 + MAX_CASES * 2) // em branco vale 0

    private val titles = mapOf(
        Type.TAP to "Tap", Type.SWIPE to "Swipe", Type.PINCH_IN to "Pinça para dentro", Type.PINCH_OUT to "Pinça para fora",
        Type.WAIT to "Esperar", Type.KEY to "Evento de Teclado", Type.STATUS to "Alertas de status", Type.GOTO to "GOTO",
        Type.COUNTER to "Contador de Execuções", Type.RESET to "Reiniciar Contador de Execuções", Type.CALL to "Arquivo de Macro", Type.IMAGE to "Image",
        Type.NODE to "Achar por texto", Type.TEXT to "Reconhecimento de Texto", Type.NUMBER to "Ler número", Type.STOP to "Parar Macro",
        Type.CHANGE to "Detecção de Mudança de Imagem", Type.GESTURE to "Gesture", Type.GROUP to "Grupo",
    )
    private val icons = mapOf(
        Type.TAP to Ic.TAP, Type.SWIPE to Ic.SWIPE, Type.PINCH_IN to Ic.SWIPE, Type.PINCH_OUT to Ic.SWIPE, Type.WAIT to Ic.WAIT,
        Type.KEY to Ic.KEY, Type.STATUS to Ic.WARN, Type.GOTO to Ic.CHEVRON, Type.COUNTER to Ic.ADD, Type.RESET to Ic.BACK, Type.CALL to Ic.PLAY, Type.IMAGE to Ic.SEARCH, Type.NODE to Ic.SEARCH, Type.TEXT to Ic.SEARCH, Type.NUMBER to Ic.NUM, Type.STOP to Ic.STOP, Type.CHANGE to Ic.IMAGE_SEARCH, Type.GESTURE to Ic.GESTURE, Type.GROUP to Ic.REPEAT,
    )
    private val tones = mapOf(
        Type.TAP to C.BLUE, Type.SWIPE to C.PURPLE, Type.PINCH_IN to C.PURPLE, Type.PINCH_OUT to C.PURPLE, Type.WAIT to C.ORANGE,
        Type.KEY to C.TEAL, Type.STATUS to C.TEAL, Type.GOTO to C.ORANGE, Type.COUNTER to C.BLUE, Type.RESET to C.RED, Type.CALL to C.PURPLE, Type.IMAGE to C.ORANGE, Type.NODE to C.TEAL, Type.TEXT to C.TEAL, Type.NUMBER to C.PURPLE, Type.STOP to C.RED, Type.CHANGE to C.ORANGE, Type.GESTURE to C.PURPLE, Type.GROUP to C.TEAL,
    )

    private val REQ_ITEM = 21
    private val REQ_ADD = 22
    private val selected = HashSet<Int>()
    private val thumbs = HashMap<String, Bitmap?>()
    private lateinit var existingId: String

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        store = Store(this)
        val existing = intent.getStringExtra("id")?.let { store.get(it) }
        id = existing?.id ?: UUID.randomUUID().toString()
        existingId = id
        steps = existing?.steps.orEmpty()

        // Como no editor do app base: barra verde, barra de ícones, nome com repetições, lista com seleção e botão + flutuante.
        name = EditText(this).apply {
            background = null; setTextColor(0xDE000000.toInt()); textSize = 20f; setSingleLine()
            isFocusable = false; isCursorVisible = false; keyListener = null // o nome muda na tela Arquivo (Renomear), como no base
            setText(existing?.name ?: "Nova macro"); setPadding(dp(16), dp(6), dp(16), dp(6))
        }
        loops = EditText(this).apply {
            background = null; setTextColor(B.TEXT); textSize = 16f; inputType = InputType.TYPE_CLASS_NUMBER; setSingleLine()
            setText((existing?.loops ?: 1).toString()); setPadding(dp(4), 0, dp(4), dp(8)); minWidth = dp(48); contentDescription = "Repetições (0 = infinito)"
            isFocusable = false; isCursorVisible = false; keyListener = null
            setOnClickListener { loopsDialog() }
        }
        emptyHint = label("Sem itens. Toque em + ou grave pelo painel.", 14f, B.TEXT2).apply { setPadding(dp(16), dp(16), dp(16), 0) }
        list = ListView(this).apply {
            divider = ColorDrawable(0x1F000000); dividerHeight = 1
            // Como no base: tocar em "Parar macro" não abre nada; toque longo só no "Arquivo de macro" (abre o arquivo alvo).
            setOnItemClickListener { _, _, i, _ -> if (steps[i].type != Type.STOP) openItem(steps[i].type, steps[i], i) }
            setOnItemLongClickListener { _, _, i, _ -> val t = steps[i]; if (t.type == Type.CALL && store.get(t.text) != null) { startActivity(android.content.Intent(this@EditorActivity, EditorActivity::class.java).putExtra("id", t.text)); true } else false }
            clipToPadding = false; setPadding(0, 0, 0, dp(96))
            adapter = object : BaseAdapter() {
                override fun getCount() = steps.size
                override fun getItem(i: Int) = steps[i]
                override fun getItemId(i: Int) = i.toLong()
                override fun getView(i: Int, v: View?, p: ViewGroup) = stepRow(i, steps[i])
            }
        }
        val more = ImageView(this).apply {
            setImageDrawable(Icon(Ic.MORE, Color.WHITE)); setPadding(dp(10), dp(10), dp(10), dp(10)); foreground = ripple(24, 0x33FFFFFF)
            contentDescription = "Mais opções"; layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)); setOnClickListener { overflow(this) }
        }
        fun tool(ic: Ic, desc: String, click: () -> Unit) = ImageView(this).apply {
            setImageDrawable(Icon(ic, B.TEXT)); setPadding(dp(14), dp(14), dp(14), dp(14)); foreground = ripple(24, 0x1F000000)
            contentDescription = desc; layoutParams = LinearLayout.LayoutParams(0, dp(56), 1f); setOnClickListener { click() }
        }
        val tools = LinearLayout(this).apply {
            setBackgroundColor(Color.WHITE); elevation = dp(2).toFloat()
            addView(tool(Ic.SELECT, "Selecionar tudo") { toggleAll() })
            addView(tool(Ic.UP, "Subir os selecionados") { moveSelected(up = true) })
            addView(tool(Ic.DOWN, "Descer os selecionados") { moveSelected(up = false) })
            addView(tool(Ic.COPY, "Duplicar os selecionados") { duplicateSelected() })
            addView(tool(Ic.TRASH, "Excluir os selecionados") { deleteSelected() })
            addView(tool(Ic.SAVE, "Salvar") { save() })
        }
        val head = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; setBackgroundColor(Color.WHITE); setPadding(0, dp(4), dp(16), dp(4))
            addView(name, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            // Como no base: "Repetir reprodução N" alinhado à direita, na mesma linha do nome
            addView(label("Repetir reprodução", 14f, B.TEXT2))
            loops.setPadding(dp(6), 0, 0, 0); loops.minWidth = 0; loops.textSize = 14f
            addView(loops)
        }
        val fab = ImageView(this).apply {
            setImageDrawable(Icon(Ic.ADD, Color.WHITE)); setPadding(dp(16), dp(16), dp(16), dp(16))
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(B.PINK) }
            foreground = RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), null, GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.WHITE) })
            elevation = dp(6).toFloat(); contentDescription = "Adicionar item"; setOnClickListener { pickType() }
            layoutParams = FrameLayout.LayoutParams(dp(56), dp(56), Gravity.BOTTOM or Gravity.END).also { it.setMargins(0, 0, dp(16), dp(16)) }
            setOnApplyWindowInsetsListener { v, i -> (v.layoutParams as FrameLayout.LayoutParams).bottomMargin = dp(16) + i.getInsets(WindowInsets.Type.navigationBars()).bottom; v.requestLayout(); i }
        }
        setContentView(FrameLayout(this).apply {
            setBackgroundColor(B.BG)
            addView(LinearLayout(this@EditorActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(appBar("Editor de Macro", Ic.BACK, { onBackPressed() }, more))
                addView(tools); addView(head); addView(emptyHint)
                addView(list, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
            }, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            addView(fab)
        })
        saved = snapshot()
        refresh()
    }

    private fun refresh() {
        selected.retainAll(steps.indices.toSet())
        emptyHint.visibility = if (steps.isEmpty()) View.VISIBLE else View.GONE
        (list.adapter as BaseAdapter).notifyDataSetChanged()
    }

    /** Toque e deslizar têm a tela própria no formato do base; os demais tipos seguem no diálogo até migrarem. */
    private fun openItem(type: Type, step: Step, index: Int) {
        if (type == Type.TAP || type == Type.SWIPE || type in setOf(Type.WAIT, Type.KEY, Type.STATUS, Type.GOTO, Type.RESET, Type.CALL, Type.STOP, Type.COUNTER, Type.TEXT, Type.NODE, Type.IMAGE, Type.NUMBER, Type.CHANGE, Type.GESTURE, Type.GROUP)) {
            startActivityForResult(
                android.content.Intent(this, ItemEditorActivity::class.java)
                    .putExtra("step", step.toJson().toString()).putExtra("total", if (index >= 0) steps.size else steps.size + 1).putExtra("index", index).putExtra("self", id), REQ_ITEM,
            )
        } else if (index >= 0) editStep(type, step, steps.size) { replace(index, it) }
        else editStep(type, step, steps.size + 1) { steps = steps.afterInsert(steps.size, it); refresh() }
    }

    override fun onActivityResult(req: Int, res: Int, data: android.content.Intent?) {
        super.onActivityResult(req, res, data)
        if (req == REQ_ADD && res == RESULT_OK) { data?.getStringExtra("type")?.let { addOfType(Type.valueOf(it)) }; return }
        if (req != REQ_ITEM || res != RESULT_OK || data == null) return
        val st = runCatching { Step.fromJson(org.json.JSONObject(data.getStringExtra("step") ?: return)) }.getOrNull() ?: return
        val i = data.getIntExtra("index", -1)
        if (i in steps.indices) replace(i, st) else { steps = steps.afterInsert(steps.size, st); refresh() }
    }

    private fun replace(i: Int, s: Step) { steps = steps.toMutableList().also { it[i] = s }; refresh() }

    // --- seleção e operações em bloco -------------------------------------------------------

    private fun marked(): List<Int> = selected.filter { it in steps.indices }.sorted()

    private fun needSelection(): List<Int>? = marked().takeIf { it.isNotEmpty() } ?: run { toast("Nenhum item selecionado."); null }

    private fun toggleAll() {
        if (selected.size == steps.size) selected.clear() else { selected.clear(); selected.addAll(steps.indices) }
        refresh()
    }

    /** Move cada item marcado uma posição; um bloco encostado na borda ou em outro marcado fica onde está. */
    private fun moveSelected(up: Boolean) {
        val idx = needSelection() ?: return
        // Como no base: se o primeiro item do sentido do movimento já está na borda, nenhum se move.
        if (if (up) idx.first() == 0 else idx.last() == steps.lastIndex) return
        val cur = idx.toMutableSet()
        for (i in if (up) idx else idx.reversed()) {
            val j = if (up) i - 1 else i + 1
            if (j in steps.indices && j !in cur) { steps = steps.afterSwap(i, j); cur.remove(i); cur.add(j) }
        }
        selected.clear(); selected.addAll(cur)
        refresh()
    }

    private fun duplicateSelected() {
        val idx = needSelection() ?: return
        // Como no base: as cópias entram juntas, depois do último marcado, e a seleção continua nos originais.
        var at = idx.last() + 1
        for (i in idx) if (steps.size < MAX_STEPS) { steps = steps.afterInsert(at, steps[i].shiftedFrom(at)); at++ }
        refresh()
    }

    private fun deleteSelected() {
        val idx = needSelection() ?: return
        for (i in idx.reversed()) steps = steps.afterDelete(i) // sem confirmação, como no base
        selected.clear(); refresh()
    }

    private fun overflow(anchor: View) {
        // Menu do base (menu_edit_macro_root): Grupo de Macro, Alternar Pular, Editar Itens Selecionados, Recarregar; "Desagrupar" é extra.
        val pm = android.widget.PopupMenu(this, anchor)
        val acts = listOf<Pair<String, () -> Unit>>(
            "Grupo de Macro" to { groupSelected() }, "Alternar Pular" to { toggleSkip() }, "Editar Itens Selecionados" to { batchEdit() },
            "Recarregar" to { reload() }, "Desagrupar" to { ungroupSelected() },
        )
        acts.forEachIndexed { k, a -> pm.menu.add(0, k, k, a.first) }
        pm.setOnMenuItemClickListener { acts[it.itemId].second(); true }
        pm.show()
    }

    /** Agrupa os itens marcados (têm de ser seguidos e sem outro grupo): entra um cabeçalho de grupo antes do primeiro. */
    private fun groupSelected() {
        val idx = needSelection()?.sorted() ?: return
        if (idx.last() - idx.first() + 1 != idx.size) return toast("Marque itens seguidos para agrupar.")
        if (idx.any { steps[it].type == Type.GROUP }) return toast("Um grupo não pode conter outro grupo.")
        if (steps.indices.any { k -> steps[k].type == Type.GROUP && idx.any { it > k && it <= k + steps[k].grp } }) return toast("Estes itens já estão em um grupo.")
        steps = steps.afterInsert(idx.first(), Step(Type.GROUP, grp = idx.size, name = "Grupo"))
        selected.clear(); refresh()
    }

    /** Desfaz o grupo marcado: some o cabeçalho e os itens continuam na lista. */
    private fun ungroupSelected() {
        val idx = needSelection() ?: return
        val heads = idx.filter { steps[it].type == Type.GROUP }.sortedDescending()
        if (heads.isEmpty()) return toast("Marque o cabeçalho de um grupo.")
        for (h in heads) steps = steps.afterDelete(h)
        selected.clear(); refresh()
    }

    private fun inGroup(i: Int) = steps.indices.any { k -> k < i && steps[k].type == Type.GROUP && i <= k + steps[k].grp }

    private fun toggleSkip() {
        val idx = needSelection() ?: return
        steps = steps.mapIndexed { i, s -> if (i in idx) s.copy(off = !s.off) else s }
        refresh()
    }

    /** "Editar Itens Selecionados" do base: escolha do que editar (rádio) e o valor, aplicado só aos itens a que se aplica. */
    private fun batchEdit() {
        val idx = needSelection() ?: return
        val opts = arrayOf("Tempo de atraso", "Pesquisa de imagem", "Pesquisa de imagem/Taxa de correspondência", "Pesquisa de imagem/Velocidade de pesquisa",
            "Pesquisa de imagem/Duração da pesquisa", "Pesquisa de imagem/Mover", "Ação")
        var pick = 0
        AlertDialog.Builder(this).setTitle("Selecione o que editar.").setSingleChoiceItems(opts, 0) { _, w -> pick = w }
            .setPositiveButton("Alterar tudo") { _, _ ->
                fun img(f: (Step) -> Step): (Step) -> Step = { if (it.type in Step.GESTURES) f(it) else it }
                when (pick) {
                    0 -> askNumber("Tempo de Atraso (ms)") { v -> applyBatch(idx) { it.copy(delay = v, rnd = 0, rndMin = 0) } }
                    1 -> askChoice("Pesquisa de imagem", arrayOf("Ligada", "Desligada")) { c -> applyBatch(idx, img { it.copy(useImg = c == 0 && it.img.isNotEmpty()) }) }
                    2 -> askNumber("Taxa de correspondência (20 a 100)", 95) { v -> applyBatch(idx, img { it.copy(thr = v) }) }
                    3 -> askNumber("Velocidade de pesquisa (ms)", 1000) { v -> applyBatch(idx, img { it.copy(cycle = v) }) }
                    4 -> askNumber("Duração da pesquisa (ms, 0 = infinito)", 5000) { v -> applyBatch(idx, img { it.copy(sms = v) }) }
                    5 -> askNumber("Mover em caso de sucesso (passo, 0 = próximo)", 0) { v -> applyBatch(idx, img { it.copy(goto = v) }) }
                    else -> askChoice("Ação", arrayOf("Pular a ação", "Executar a ação")) { c -> applyBatch(idx, img { it.copy(skip = c == 0) }) }
                }
            }.setNegativeButton("Cancelar", null).show()
    }

    private fun askChoice(title: String, items: Array<String>, then: (Int) -> Unit) {
        var pick = 0
        AlertDialog.Builder(this).setTitle(title).setSingleChoiceItems(items, 0) { _, w -> pick = w }
            .setPositiveButton("Alterar tudo") { _, _ -> then(pick) }.setNegativeButton("Cancelar", null).show()
    }

    /** Aplica [f] aos marcados; se algum ficar inválido, nada muda e o motivo aparece. */
    private fun applyBatch(idx: List<Int>, f: (Step) -> Step) {
        val next = steps.mapIndexed { i, s -> if (i in idx) f(s) else s }
        next.forEachIndexed { i, s -> s.validate(next.size)?.let { return toast("Item ${i + 1}: $it") } }
        val changed = idx.count { next[it] != steps[it] }
        steps = next; refresh()
        toast(if (changed == 0) "Nenhum item se aplica a esta opção." else "$changed ${if (changed == 1) "item alterado" else "itens alterados"}.")
    }

    private fun askNumber(title: String, initial: Int = 1000, then: (Int) -> Unit) {
        val et = EditText(this).apply { inputType = InputType.TYPE_CLASS_NUMBER; setText(initial.toString()); setPadding(dp(24), dp(16), dp(24), dp(8)) }
        AlertDialog.Builder(this).setTitle(title).setView(et)
            .setPositiveButton("Alterar tudo") { _, _ -> et.text.toString().toIntOrNull()?.let(then) ?: toast("Número inválido") }
            .setNegativeButton("Cancelar", null).show()
    }

    /** Descarta as mudanças e relê a macro salva. */
    private fun reload() {
        AlertDialog.Builder(this).setMessage("Descartar as alterações e recarregar a macro salva?")
            .setPositiveButton("Recarregar") { _, _ ->
                val m = store.get(existingId)
                steps = m?.steps.orEmpty(); name.setText(m?.name ?: "Nova macro"); loops.setText((m?.loops ?: 1).toString())
                selected.clear(); refresh()
            }.setNegativeButton("Cancelar", null).show()
    }

    // --- linha da lista ----------------------------------------------------------------------

    /** Formato do base: "5.0s", "1m 5.0s" (ponto decimal em qualquer idioma). */
    private fun secs(ms: Int): String {
        val t = ms / 1000.0
        return if (t >= 60) "%dm %.1fs".format(java.util.Locale.US, (t / 60).toInt(), t % 60) else "%.1fs".format(java.util.Locale.US, t)
    }

    /** Vetores do próprio base para cada tipo (list_icon / lista de itens). */
    private val typeRes = mapOf(
        Type.TAP to R.drawable.bp_touch_app, Type.SWIPE to R.drawable.bp_touch_app, Type.PINCH_IN to R.drawable.bp_touch_app, Type.PINCH_OUT to R.drawable.bp_touch_app,
        Type.IMAGE to R.drawable.bp_touch_app, Type.NODE to R.drawable.bp_format_text, Type.NUMBER to R.drawable.bp_format_text, Type.TEXT to R.drawable.bp_format_text,
        Type.WAIT to R.drawable.bp_ic_timer, Type.KEY to R.drawable.bp_outline_keyboard_24, Type.STATUS to R.drawable.bp_outline_notifications_active,
        Type.GOTO to R.drawable.bp_action_goto, Type.COUNTER to R.drawable.bp_action_repeat_count, Type.RESET to R.drawable.bp_action_repeat_counter_zero,
        Type.CALL to R.drawable.bp_file_replace_outline, Type.STOP to R.drawable.bp_stop_macro, Type.CHANGE to R.drawable.bp_image_compare,
        Type.GESTURE to R.drawable.bp_ic_gesture_swipe, Type.GROUP to R.drawable.bp_ic_loop,
    )

    private fun resIcon(res: Int, sizeDp: Int, tint: Int? = null) = ImageView(this).apply {
        setImageResource(res); scaleType = ImageView.ScaleType.FIT_CENTER; tint?.let { setColorFilter(it) }
        layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
    }

    /** Valor da linha (ícone de 14 + texto de 14sp), como os chips do base. */
    private fun chip(res: Int, text: String) = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).also { it.marginEnd = dp(6) }
        addView(resIcon(res, 14, 0xFF000000.toInt()))
        addView(label(text, 14f, 0xDE000000.toInt()).apply { setPadding(dp(2), 0, 0, 0) })
    }

    private fun limit(ms: Int) = if (ms == 0) "Infinito" else secs(ms)

    /** Informação curta do item (tv_string_info do base). */
    private fun info(s: Step): String = when (s.type) {
        Type.KEY -> mapOf(1 to "Voltar", 2 to "Início", 3 to "Recentes", 4 to "Notificações", KEY_SCREENSHOT to "Captura de tela")[s.x1] ?: ""
        Type.STATUS -> s.text
        Type.CALL -> store.get(s.text)?.name ?: "Nenhum arquivo alocado."
        Type.COUNTER -> s.cases.joinToString(" ") { "${it.n}:${it.to}" }
        Type.RESET -> "Contador ${s.counter}"
        Type.TEXT, Type.NODE -> s.text.replace(",", ", ")
        Type.NUMBER -> "${s.lo}"
        Type.GROUP -> if (s.rep == 0) "Infinito" else "${s.rep} vezes"
        Type.GESTURE -> "${gesturePoints(s.text).size} pontos"
        else -> ""
    }

    private fun stepRow(i: Int, s: Step): View {
        val search = s.type in Step.SEARCHES || s.useImg
        val gestureLike = s.type in Step.GESTURES
        val thumbId = if (s.type == Type.IMAGE) s.text else s.img
        val thumb: View = (if (thumbId.isNotEmpty()) thumbs.getOrPut(thumbId) { store.templateThumb(thumbId, 120) } else null)?.let { bmp ->
            ImageView(this).apply { setImageBitmap(bmp); scaleType = ImageView.ScaleType.FIT_CENTER; layoutParams = LinearLayout.LayoutParams(dp(32), dp(32)).also { it.setMargins(0, dp(6), 0, dp(6)) } }
        } ?: resIcon(if (s.type == Type.GROUP && s.mono) R.drawable.bp_ic_loop_image else typeRes.getValue(s.type), 32, 0xFF607D8B.toInt()).apply { (layoutParams as LinearLayout.LayoutParams).setMargins(0, dp(6), 0, dp(6)) }
        // Setas de salto só quando existe condição (busca) ou o item é um "Ir para": azul = sucesso, vermelha = falha.
        val jumps = if (s.type == Type.GOTO) listOfNotNull(s.goto.takeIf { it > 0 }?.let { it to 0xFF2121FF.toInt() })
            else if (search) listOfNotNull(s.goto.takeIf { it > 0 }?.let { it to 0xFF2121FF.toInt() }, s.fail.takeIf { it > 0 }?.let { it to 0xFFF44336.toInt() }) else emptyList()
        val delayText = if (s.rnd > 0) "${secs(s.rndMin)}~${secs(s.rnd)}" else secs(s.delay)
        val infoText = info(s)
        return LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(dp(6) + if (inGroup(i)) dp(24) else 0, dp(2), dp(6), dp(2))
            addView(CheckBox(this@EditorActivity).apply {
                isChecked = i in selected; buttonTintList = ColorStateList.valueOf(0xFFD81B60.toInt())
                setOnCheckedChangeListener { _, on -> if (on) selected.add(i) else selected.remove(i) }
                layoutParams = LinearLayout.LayoutParams(dp(42), WRAP_CONTENT)
            })
            addView(thumb)
            addView(LinearLayout(this@EditorActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).also { it.setMargins(dp(16), dp(4), dp(16), dp(4)) }
                addView(label("${i + 1}. ${s.name.ifBlank { if (s.type == Type.TAP && s.ms >= 500) "Press" else titles.getValue(s.type) }}", 18f, 0xFF607D8B.toInt()).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
                addView(LinearLayout(this@EditorActivity).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    // Cada valor só aparece quando está ligado: busca (tempo ou Infinito), atraso, ação pulada e a informação do tipo.
                    if (search) addView(chip(R.drawable.bp_outline_image_search_24, limit(if (s.type in Step.GESTURES) s.sms else s.ms)))
                    if (s.delay > 0 || s.rnd > 0) addView(chip(R.drawable.bp_ic_timer, delayText))
                    if (gestureLike && s.skip) addView(resIcon(R.drawable.bp_ic_do_not_touch_24px, 14, 0xFF000000.toInt()))
                    if (infoText.isNotEmpty()) addView(label(infoText, 14f, 0xDE000000.toInt()).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
                })
            })
            if (s.off) addView(resIcon(R.drawable.bp_remove_circle, 24, 0xFFD81B60.toInt()).apply { (layoutParams as LinearLayout.LayoutParams).setMargins(dp(3), 0, dp(3), 0) })
            jumps.forEach { (to, color) ->
                addView(LinearLayout(this@EditorActivity).apply {
                    orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).also { it.setMargins(dp(3), 0, dp(3), 0) }
                    addView(resIcon(R.drawable.bp_arrow_right, 16, color))
                    addView(label(to.toString(), 16f, color, bold = true))
                })
            }
        }
    }

    private fun detail(s: Step): String {
        val jit = if (s.jitter > 0) " · ±${s.jitter} px" else ""
        val skip = if (s.skip) " · pulado" else ""
        return when (s.type) {
            Type.TAP -> "(${s.x1}, ${s.y1}) · ${s.ms} ms$jit"
            Type.SWIPE -> "(${s.x1}, ${s.y1}) → (${s.x2}, ${s.y2}) · ${s.ms} ms$jit"
            Type.PINCH_IN -> "(${s.x1}, ${s.y1}) e (${s.x2}, ${s.y2}) ao centro · ${s.ms} ms$jit"
            Type.PINCH_OUT -> "do centro a (${s.x1}, ${s.y1}) e (${s.x2}, ${s.y2}) · ${s.ms} ms$jit"
            Type.WAIT -> "${s.ms} ms"
            Type.KEY -> arrayOf("?", "Voltar", "Início", "Recentes", "Notificações")[s.x1.coerceIn(0, 4)]
            Type.STATUS -> "“${s.text}” · ${s.ms} ms"
            Type.GOTO -> "→ passo ${s.goto}"
            Type.COUNTER -> "contador ${s.counter}" + s.cases.joinToString("") { " · ≥${it.n}→${if (it.to == 0) "seguir" else it.to}" } +
                " · senão " + if (s.goto == 0) "seguir" else "→ ${s.goto}"
            Type.RESET -> "contador ${s.counter} = 0"
            Type.NUMBER -> "área (${s.x1}, ${s.y1})–(${s.x2}, ${s.y2}) · valor " +
                when (s.cmp) { 0 -> "≥ ${s.lo}"; 1 -> "≤ ${s.lo}"; 2 -> "= ${s.lo}"; else -> "entre ${s.lo} e ${s.hi}" } +
                " · até ${s.ms} ms · vale → " + (if (s.goto == 0) "seguir" else s.goto) + " · não → " + (if (s.fail == 0) "seguir" else s.fail)
            Type.NODE, Type.TEXT -> "“${s.text}”" + (if (s.type == Type.TEXT) (if (s.x2 > s.x1) " · área (${s.x1}, ${s.y1})–(${s.x2}, ${s.y2})" else " · tela toda") else "") + (if (s.exact) " (igual)" else "") + " · até ${s.ms} ms · achou → " + (if (s.goto == 0) "seguir" else s.goto) +
                " · não → " + (if (s.fail == 0) "seguir" else s.fail) + if (s.tap) " · toca" else ""
            Type.IMAGE -> "≥ ${s.thr}%" + (if (s.method == 1) " pixels" else "") + (if (s.mono) " cinza" else "") + (if (s.confirm > 1) " ×${s.confirm}" else "") + (if (s.scales) " (escalas)" else "") + " · até ${s.ms} ms · " + (if (s.x2 > s.x1) "área (${s.x1}, ${s.y1})–(${s.x2}, ${s.y2}) · " else "tela toda · ") +
                "achou → " + (if (s.goto == 0) "seguir" else s.goto) + " · não → " + (if (s.fail == 0) "seguir" else s.fail) + if (s.tap) " · toca" else ""
            Type.STOP -> "encerra a reprodução"
            Type.GROUP -> "${s.grp} itens · " + (if (s.rep == 0) "infinito" else "${s.rep} vezes")
            Type.GESTURE -> "${gesturePoints(s.text).size} pontos · ${s.ms} ms"
            Type.CHANGE -> (if (s.x2 > s.x1) "área (${s.x1}, ${s.y1})–(${s.x2}, ${s.y2})" else "tela toda") + " · muda abaixo de ${s.thr}% · até ${s.ms} ms"
            Type.CALL -> (store.get(s.text)?.name ?: "macro removida") + if (s.ret) " · volta ao terminar" else " · substitui esta"
        } + skip
    }

    private fun pickType() {
        if (steps.size >= MAX_STEPS) return toast("Limite de $MAX_STEPS passos")
        startActivityForResult(android.content.Intent(this, AddItemsActivity::class.java), REQ_ADD)
    }

    private fun addOfType(type: Type) {
        // Como no base, estes itens do catálogo entram direto na lista, com os padrões dele; o toque na linha abre a edição.
        val direct = when (type) {
            Type.KEY -> Step(type, x1 = 1, delay = 5000)
            Type.STATUS -> Step(type, ms = 5000, snd = true, text = "Alerta de status")
            Type.GOTO -> Step(type, goto = 0)
            Type.RESET, Type.COUNTER -> Step(type, counter = 1)
            Type.CALL, Type.STOP -> Step(type)
            else -> null
        }
        if (direct != null) { steps = steps.afterInsert(steps.size, direct); refresh(); list.setSelection(steps.size - 1); return }
        run {
            // Padrões que já passam na validação; a tela mostra um centro razoável.
            val base = when (type) {
                Type.TAP -> Step(type, 500, 1000, ms = 50)
                Type.SWIPE -> Step(type, 500, 1500, 500, 500, ms = 300)
                Type.PINCH_IN -> Step(type, 300, 900, 700, 1500, ms = 400)
                Type.PINCH_OUT -> Step(type, 300, 900, 700, 1500, ms = 400)
                Type.WAIT -> Step(type, ms = 1000)
                Type.KEY -> Step(type, x1 = 1)
                Type.STATUS -> Step(type, ms = 1500, text = "Passo concluído")
                Type.GOTO -> Step(type, goto = 1)
                Type.COUNTER, Type.RESET -> Step(type, counter = 1)
                Type.CALL, Type.IMAGE -> Step(type)
                Type.NODE -> Step(type, ms = 3000)
                Type.TEXT -> Step(type, ms = 5000, cycle = 500)
                Type.NUMBER -> Step(type, 100, 100, 400, 180, ms = 3000, cycle = 300)
                Type.STOP -> Step(type)
                Type.CHANGE -> Step(type, ms = 5000, cycle = 500, thr = 95)
                Type.GROUP -> Step(type)
                Type.GESTURE -> Step(type, ms = 500, text = "300,900;700,900")
            }
            openItem(type, base, -1)
        }
    }

    /** [total] é o número de passos depois da edição, para validar os destinos de salto. */
    private fun editStep(type: Type, s: Step, total: Int, done: (Step) -> Unit) {
        val v = IntArray(MINC + 1)
        v[X1] = s.x1; v[Y1] = s.y1; v[X2] = s.x2; v[Y2] = s.y2; v[MS] = s.ms; v[DELAY] = s.delay; v[RND] = s.rnd
        v[JIT] = s.jitter; v[RMIN] = s.rndMin; v[GOTO] = s.goto; v[CNT] = s.counter; v[THR] = s.thr; v[CYC] = s.cycle; v[FAIL] = s.fail; v[CMP] = s.cmp; v[LO] = s.lo; v[HI] = s.hi; v[CONF] = s.confirm; v[METH] = s.method; v[OX] = s.ox; v[OY] = s.oy; v[MINC] = s.minConf
        s.cases.forEachIndexed { k, c -> v[CASE0 + 2 * k] = c.n; v[CASE0 + 2 * k + 1] = c.to }

        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(20), dp(20), dp(4)) }
        col.addView(label(titles.getValue(type), 20f, bold = true).apply { setPadding(0, 0, 0, dp(8)) })

        var text = s.text
        val textLabel = if (type == Type.TEXT) "Texto na tela (vários separados por vírgula ou |)" else if (type == Type.NODE) "Texto do elemento (ou id:nome do recurso)" else "Mensagem"
        val textEdit = if (type == Type.STATUS || (type == Type.NODE || type == Type.TEXT)) field(s.text, textLabel, numeric = false).also { col.addView(labeled(textLabel, it)) } else null
        if (type == Type.CALL) {
            val btn = textAction(store.get(s.text)?.name ?: "Escolher macro…") {}
            btn.setOnClickListener { pickMacro { m -> text = m.id; btn.text = m.name } }
            col.addView(labeled("Macro chamada", btn))
        }

        val idx = fields.getValue(type)
        val edits = idx.map { k ->
            val lbl = when {
                k == X1 && type == Type.KEY -> "Tecla (1 voltar, 2 início, 3 recentes, 4 notificações, 9 captura de tela)"
                k == MS && type == Type.WAIT -> "Espera (ms)"
                k == MS && type == Type.STATUS -> "Tempo da mensagem (ms)"
                k == MS && (type == Type.IMAGE || (type == Type.NODE || type == Type.TEXT) || type == Type.NUMBER) -> "Tentar por até (ms; 0 = sem limite, sem “se achar / não”)"
                k == GOTO && type == Type.NUMBER -> "Se a condição valer, ir para o passo (0 = seguir)"
                k == FAIL && type == Type.NUMBER -> "Se não valer no prazo, ir para o passo (0 = seguir)"
                type == Type.NUMBER && k in X1..Y2 -> "Área do número: " + labels.getValue(k)
                k == GOTO && (type == Type.IMAGE || (type == Type.NODE || type == Type.TEXT)) -> "Se achar, ir para o passo (0 = seguir)"
                (type == Type.IMAGE || type == Type.TEXT) && k in X1..Y2 -> "Área de busca: " + labels.getValue(k) + " (tudo 0 = tela toda)"
                k >= CASE0 -> if ((k - CASE0) % 2 == 0) "Caso ${(k - CASE0) / 2 + 1}: ao chegar a (0 = vazio)" else "Caso ${(k - CASE0) / 2 + 1}: ir para o passo (0 = seguir)"
                k == GOTO && type == Type.COUNTER -> "Destino padrão (0 = seguir)"
                else -> labels.getValue(k)
            }
            field(if (v[k] == 0 && (k in optional || ((type == Type.IMAGE || type == Type.TEXT) && k in X1..Y2))) "" else v[k].toString(), lbl, numeric = true).also { f -> if (k == OX || k == OY) f.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED; col.addView(labeled(lbl, f)) }
        }
        val skipSw = if (type in Step.GESTURES || type == Type.KEY || type == Type.STATUS || type == Type.CALL)
            Switch(this).apply { isChecked = s.skip; contentDescription = "Pular a ação" }.also { col.addView(labeled("Pular a ação (só roda o atraso)", it)) } else null
        val retSw = if (type == Type.CALL)
            Switch(this).apply { isChecked = s.ret; contentDescription = "Voltar ao terminar" }.also { col.addView(labeled("Voltar a esta macro ao terminar", it)) } else null

        val exactSw = if ((type == Type.NODE || type == Type.TEXT))
            Switch(this).apply { isChecked = s.exact; contentDescription = "Texto inteiro igual" }.also { col.addView(labeled("Texto inteiro igual (desligado = contém)", it)) } else null
        val monoSw = if (type == Type.IMAGE)
            Switch(this).apply { isChecked = s.mono; contentDescription = "Comparar em cinza" }.also { col.addView(labeled("Comparar em cinza (só no método 1)", it)) } else null
        val scalesSw = if (type == Type.IMAGE)
            Switch(this).apply { isChecked = s.scales; contentDescription = "Tolerar variação de escala" }.also { col.addView(labeled("Tolerar variação de escala (mais lento)", it)) } else null
        val tapSw = if (type == Type.IMAGE || (type == Type.NODE || type == Type.TEXT))
            Switch(this).apply { isChecked = s.tap; contentDescription = "Tocar no centro ao achar" }.also { col.addView(labeled("Tocar no centro do que achou", it)) } else null

        val dblSw = if (type == Type.TAP || type == Type.IMAGE || type == Type.NODE || type == Type.TEXT)
            Switch(this).apply { isChecked = s.dbl; contentDescription = "Clique duplo" }.also { col.addView(labeled("Clique duplo (dois toques seguidos no mesmo ponto)", it)) } else null
        val vibSw = if (type == Type.STATUS) Switch(this).apply { isChecked = s.vib; contentDescription = "Vibrar" }.also { col.addView(labeled("Vibrar ao exibir", it)) } else null
        val sndSw = if (type == Type.STATUS) Switch(this).apply { isChecked = s.snd; contentDescription = "Tocar som" }.also { col.addView(labeled("Tocar o som de notificação", it)) } else null

        val dlg = AlertDialog.Builder(this).setView(ScrollView(this).apply { addView(col) })
            .setPositiveButton("OK", null).setNegativeButton("Cancelar", null).create()
        dlg.window?.setBackgroundDrawable(rounded(0xF2161C2B.toInt(), 24, C.EDGE)) // mesmo vidro escuro do painel
        dlg.show()
        // Listener próprio: o padrão fecha o diálogo mesmo com valor inválido.
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val nv = v.copyOf()
            for ((n, k) in idx.withIndex()) {
                val raw = edits[n].text.toString().trim()
                nv[k] = if (raw.isEmpty() && (k in optional || ((type == Type.IMAGE || type == Type.TEXT) && k in X1..Y2))) 0 else raw.toIntOrNull() ?: run { edits[n].error = "número inteiro"; return@setOnClickListener }
            }
            val cases = (0 until MAX_CASES).map { Case(nv[CASE0 + 2 * it], nv[CASE0 + 2 * it + 1]) }.filter { it.n > 0 }
            val ns = Step(
                type, nv[X1], nv[Y1], nv[X2], nv[Y2], nv[MS], nv[DELAY], nv[RND], nv[RMIN], nv[JIT],
                skipSw?.isChecked ?: false, nv[GOTO], if (nv[CNT] == 0) 1 else nv[CNT], if (type == Type.COUNTER) cases else emptyList(),
                if (type == Type.STATUS || (type == Type.NODE || type == Type.TEXT)) textEdit!!.text.toString().trim() else if (type == Type.CALL || type == Type.IMAGE) text else "", retSw?.isChecked ?: true,
                nv[THR], nv[CYC], nv[FAIL], tapSw?.isChecked ?: true, exactSw?.isChecked ?: false, scalesSw?.isChecked ?: false, nv[CMP], nv[LO], nv[HI], nv[METH], monoSw?.isChecked ?: false, nv[CONF],
                dbl = dblSw?.isChecked ?: false, ox = nv[OX], oy = nv[OY], vib = vibSw?.isChecked ?: false, snd = sndSw?.isChecked ?: false, minConf = nv[MINC],
                name = s.name, img = s.img, useImg = s.useImg, sms = s.sms, ax1 = s.ax1, ay1 = s.ay1, ax2 = s.ax2, ay2 = s.ay2, // o bloco de imagem não tem campos neste diálogo: preserva
            )
            val err = ns.validate(total)
            if (err != null) { toast(err); return@setOnClickListener }
            done(ns); dlg.dismiss()
        }
    }

    /** Lista as outras macros para o passo "Chamar macro". */
    private fun pickMacro(chosen: (Macro) -> Unit) {
        val others = store.list().filter { it.id != id }
        if (others.isEmpty()) return toast("Não há outra macro salva para chamar")
        AlertDialog.Builder(this).setItems(others.map { it.name }.toTypedArray()) { _, w -> chosen(others[w]) }.show()
    }

    /** Estado salvo por último, para o Voltar saber se há mudanças. */
    private var saved: String = ""
    private fun snapshot() = Macro(id, name.text.toString().trim(), loops.text.toString().toIntOrNull() ?: 1, steps).toJson().toString()

    override fun onBackPressed() {
        if (saved.isEmpty() || snapshot() == saved) return super.onBackPressed()
        AlertDialog.Builder(this).setMessage("Deseja sair da edição?")
            .setPositiveButton("OK") { _, _ -> finish() }.setNegativeButton("Cancelar", null).show()
    }

    private fun loopsDialog() {
        val et = EditText(this).apply { inputType = InputType.TYPE_CLASS_NUMBER; setText(loops.text); setPadding(dp(24), dp(16), dp(24), dp(8)) }
        AlertDialog.Builder(this).setTitle("Repetir reprodução").setMessage("0 = infinito").setView(et)
            .setPositiveButton("OK") { _, _ -> et.text.toString().toIntOrNull()?.takeIf { it in 0..MAX_LOOPS }?.let { loops.setText(it.toString()) } ?: toast("Número inválido") }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun save() {
        val n = loops.text.toString().toIntOrNull() ?: return toast("Repetições deve ser um número inteiro")
        val m = Macro(id, name.text.toString().trim(), n, steps)
        m.validate()?.let { return toast(it) }
        try { store.save(m); saved = snapshot(); toast("Salvo.") } catch (e: Exception) { toast("Falha ao salvar: ${e.message}") }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
}

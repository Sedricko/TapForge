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
    )
    private val optional = setOf(DELAY, RND, JIT, RMIN, GOTO, FAIL, CMP, LO, HI, OX, OY, MINC) + (CASE0 until CASE0 + MAX_CASES * 2) // em branco vale 0

    private val titles = mapOf(
        Type.TAP to "Toque", Type.SWIPE to "Deslizar", Type.PINCH_IN to "Pinça para dentro", Type.PINCH_OUT to "Pinça para fora",
        Type.WAIT to "Esperar", Type.KEY to "Tecla", Type.STATUS to "Alerta de status", Type.GOTO to "Ir para o passo",
        Type.COUNTER to "Contador de execução", Type.RESET to "Zerar contador", Type.CALL to "Chamar macro", Type.IMAGE to "Buscar imagem", Type.NODE to "Achar por texto", Type.TEXT to "Ler texto da tela (OCR)", Type.NUMBER to "Ler número",
    )
    private val icons = mapOf(
        Type.TAP to Ic.TAP, Type.SWIPE to Ic.SWIPE, Type.PINCH_IN to Ic.SWIPE, Type.PINCH_OUT to Ic.SWIPE, Type.WAIT to Ic.WAIT,
        Type.KEY to Ic.KEY, Type.STATUS to Ic.WARN, Type.GOTO to Ic.CHEVRON, Type.COUNTER to Ic.ADD, Type.RESET to Ic.BACK, Type.CALL to Ic.PLAY, Type.IMAGE to Ic.SEARCH, Type.NODE to Ic.SEARCH, Type.TEXT to Ic.SEARCH, Type.NUMBER to Ic.NUM,
    )
    private val tones = mapOf(
        Type.TAP to C.BLUE, Type.SWIPE to C.PURPLE, Type.PINCH_IN to C.PURPLE, Type.PINCH_OUT to C.PURPLE, Type.WAIT to C.ORANGE,
        Type.KEY to C.TEAL, Type.STATUS to C.TEAL, Type.GOTO to C.ORANGE, Type.COUNTER to C.BLUE, Type.RESET to C.RED, Type.CALL to C.PURPLE, Type.IMAGE to C.ORANGE, Type.NODE to C.TEAL, Type.TEXT to C.TEAL, Type.NUMBER to C.PURPLE,
    )

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
            background = null; setTextColor(B.TEXT); setHintTextColor(B.TEXT2); textSize = 22f; hint = "Nome"; setSingleLine()
            setText(existing?.name ?: "Nova macro"); setPadding(dp(16), dp(8), dp(16), 0)
        }
        loops = EditText(this).apply {
            background = null; setTextColor(B.TEXT); textSize = 16f; inputType = InputType.TYPE_CLASS_NUMBER; setSingleLine()
            setText((existing?.loops ?: 1).toString()); setPadding(dp(4), 0, dp(4), dp(8)); minWidth = dp(48); contentDescription = "Repetições (0 = infinito)"
        }
        emptyHint = label("Sem itens. Toque em + ou grave pelo painel.", 14f, B.TEXT2).apply { setPadding(dp(16), dp(16), dp(16), 0) }
        list = ListView(this).apply {
            divider = ColorDrawable(0x1F000000); dividerHeight = 1
            setOnItemClickListener { _, _, i, _ -> editStep(steps[i].type, steps[i], steps.size) { replace(i, it) } }
            setOnItemLongClickListener { _, _, i, _ -> rowMenu(i); true }
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
            contentDescription = "Mais opções"; layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)); setOnClickListener { overflow() }
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
            orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.WHITE)
            addView(name)
            addView(LinearLayout(this@EditorActivity).apply {
                gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), 0, 0, 0)
                addView(glyph(Ic.REPEAT, B.TEXT, 18)); addView(loops)
            })
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
                addView(appBar("Editor de Macro", Ic.BACK, { finish() }, more))
                addView(tools); addView(head); addView(emptyHint)
                addView(list, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
            }, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            addView(fab)
        })
        refresh()
    }

    private fun refresh() {
        selected.retainAll(steps.indices.toSet())
        emptyHint.visibility = if (steps.isEmpty()) View.VISIBLE else View.GONE
        (list.adapter as BaseAdapter).notifyDataSetChanged()
    }

    private fun replace(i: Int, s: Step) { steps = steps.toMutableList().also { it[i] = s }; refresh() }

    // --- seleção e operações em bloco -------------------------------------------------------

    private fun marked(): List<Int> = selected.filter { it in steps.indices }.sorted()

    private fun needSelection(): List<Int>? = marked().takeIf { it.isNotEmpty() } ?: run { toast("Marque os itens primeiro"); null }

    private fun toggleAll() {
        if (selected.size == steps.size) selected.clear() else { selected.clear(); selected.addAll(steps.indices) }
        refresh()
    }

    /** Move cada item marcado uma posição; um bloco encostado na borda ou em outro marcado fica onde está. */
    private fun moveSelected(up: Boolean) {
        val idx = needSelection() ?: return
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
        for (i in idx.reversed()) if (steps.size < MAX_STEPS) steps = steps.afterInsert(i + 1, steps[i].shiftedFrom(i + 1))
        selected.clear(); refresh()
    }

    private fun deleteSelected() {
        val idx = needSelection() ?: return
        AlertDialog.Builder(this).setMessage("Excluir ${idx.size} ${if (idx.size == 1) "item" else "itens"}?")
            .setPositiveButton("Excluir") { _, _ -> for (i in idx.reversed()) steps = steps.afterDelete(i); selected.clear(); refresh() }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun overflow() {
        val acts = listOf<Pair<String, () -> Unit>>(
            "Alternar Pular" to { toggleSkip() },
            "Editar Itens Selecionados" to { batchEdit() },
            "Recarregar" to { reload() },
        )
        AlertDialog.Builder(this).setItems(acts.map { it.first }.toTypedArray()) { _, w -> acts[w].second() }.show()
    }

    private fun toggleSkip() {
        val idx = needSelection() ?: return
        steps = steps.mapIndexed { i, s -> if (i in idx) s.copy(skip = !s.skip) else s }
        refresh()
    }

    private fun batchEdit() {
        val idx = needSelection() ?: return
        val opts = arrayOf("Tempo de atraso (ms)", "Duração (ms)", "Pular: ligar", "Pular: desligar")
        AlertDialog.Builder(this).setTitle("Selecione o que editar.").setItems(opts) { _, w ->
            when (w) {
                0 -> askNumber("Tempo de atraso (ms)") { v -> applyBatch(idx) { it.copy(delay = v, rnd = 0, rndMin = 0) } }
                1 -> askNumber("Duração (ms)") { v -> applyBatch(idx) { it.copy(ms = v) } }
                2 -> applyBatch(idx) { it.copy(skip = true) }
                else -> applyBatch(idx) { it.copy(skip = false) }
            }
        }.setNegativeButton("Cancelar", null).show()
    }

    /** Aplica [f] aos marcados; se algum ficar inválido, nada muda e o motivo aparece. */
    private fun applyBatch(idx: List<Int>, f: (Step) -> Step) {
        val next = steps.mapIndexed { i, s -> if (i in idx) f(s) else s }
        next.forEachIndexed { i, s -> s.validate(next.size)?.let { return toast("Item ${i + 1}: $it") } }
        steps = next; refresh()
    }

    private fun askNumber(title: String, then: (Int) -> Unit) {
        val et = EditText(this).apply { inputType = InputType.TYPE_CLASS_NUMBER; setText("1000"); setPadding(dp(24), dp(16), dp(24), dp(8)) }
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

    private fun secs(ms: Int) = "%.1fs".format(ms / 1000.0)

    private fun chip(ic: Ic, text: String) = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).also { it.marginEnd = dp(14) }
        addView(glyph(ic, B.TEXT, 16)); addView(label(text, 13f, B.TEXT).apply { setPadding(dp(4), 0, 0, 0) })
    }

    private fun stepRow(i: Int, s: Step): View {
        val vision = s.type == Type.IMAGE || s.type == Type.TEXT || s.type == Type.NODE || s.type == Type.NUMBER
        val thumb: View = (if (s.type == Type.IMAGE) thumbs.getOrPut(s.text) { store.templateThumb(s.text, 120) } else null)?.let { bmp ->
            ImageView(this).apply { setImageBitmap(bmp); scaleType = ImageView.ScaleType.FIT_CENTER; layoutParams = LinearLayout.LayoutParams(dp(52), dp(52)) }
        } ?: badge(icons.getValue(s.type), tones.getValue(s.type), 44).apply { layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)).also { it.setMargins(dp(4), dp(4), dp(4), dp(4)) } }
        val to = if (s.goto > 0) s.goto else i + 2
        val arrow = if (vision) 0xFF2D2DDC.toInt() else 0xFFF44336.toInt()
        val delayText = if (s.rnd > 0) "${secs(s.rndMin)}–${secs(s.rnd)}" else secs(s.delay)
        return LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(8), dp(12), dp(8))
            alpha = if (s.skip) 0.45f else 1f
            addView(CheckBox(this@EditorActivity).apply {
                isChecked = i in selected; buttonTintList = ColorStateList.valueOf(B.TEXT2)
                setOnCheckedChangeListener { _, on -> if (on) selected.add(i) else selected.remove(i) }
            })
            addView(thumb)
            addView(LinearLayout(this@EditorActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), 0, dp(8), 0)
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
                addView(label("${i + 1}. ${titles.getValue(s.type)}" + if (s.skip) " (pular)" else "", 19f, 0xFF607D8B.toInt()))
                addView(LinearLayout(this@EditorActivity).apply {
                    setPadding(0, dp(4), 0, 0)
                    addView(chip(if (vision) Ic.IMAGE_SEARCH else icons.getValue(s.type), secs(s.ms)))
                    addView(chip(Ic.WAIT, delayText))
                })
                addView(label(detail(s), 12f, B.TEXT2).apply { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END; setPadding(0, dp(2), 0, 0) })
            })
            addView(LinearLayout(this@EditorActivity).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
                addView(glyph(Ic.JUMP, arrow, 22))
                addView(label(if (to > steps.size) "fim" else to.toString(), 17f, arrow, bold = true))
            })
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
            Type.CALL -> (store.get(s.text)?.name ?: "macro removida") + if (s.ret) " · volta ao terminar" else " · substitui esta"
        } + skip
    }

    private fun pickType() {
        if (steps.size >= MAX_STEPS) return toast("Limite de $MAX_STEPS passos")
        val offered = Type.entries.filter { it != Type.IMAGE } // IMAGE nasce da captura pelo orbe
        AlertDialog.Builder(this).setItems(offered.map { titles.getValue(it) }.toTypedArray()) { _, w ->
            val type = offered[w]
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
            }
            editStep(type, base, steps.size + 1) { steps = steps.afterInsert(steps.size, it); refresh() }
        }.show()
    }

    private fun rowMenu(i: Int) {
        selected.clear()
        AlertDialog.Builder(this).setItems(arrayOf("Duplicar", "Mover para cima", "Mover para baixo", "Excluir")) { _, w ->
            when (w) {
                0 -> if (steps.size < MAX_STEPS) steps = steps.afterInsert(i + 1, steps[i].shiftedFrom(i + 1))
                1 -> if (i > 0) steps = steps.afterSwap(i, i - 1)
                2 -> if (i < steps.lastIndex) steps = steps.afterSwap(i, i + 1)
                3 -> steps = steps.afterDelete(i)
            }
            refresh()
        }.show()
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

    private fun save() {
        val n = loops.text.toString().toIntOrNull() ?: return toast("Repetições deve ser um número inteiro")
        val m = Macro(id, name.text.toString().trim(), n, steps)
        m.validate()?.let { return toast(it) }
        try { store.save(m); finish() } catch (e: Exception) { toast("Falha ao salvar: ${e.message}") }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
}

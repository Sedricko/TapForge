package app.macro17

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.InsetDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.BaseAdapter
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
    private lateinit var header: TextView
    private lateinit var emptyHint: TextView
    private var steps: List<Step> = emptyList()

    // Campos numéricos do diálogo, em um IntArray: índices abaixo. Casos do contador: 11.. em pares (limiar, destino).
    private val X1 = 0; private val Y1 = 1; private val X2 = 2; private val Y2 = 3; private val MS = 4
    private val DELAY = 5; private val RND = 6; private val JIT = 7; private val RMIN = 8; private val GOTO = 9; private val CNT = 10; private val CASE0 = 11

    private val labels = mapOf(
        X1 to "X", Y1 to "Y", X2 to "X final", Y2 to "Y final", MS to "Duração (ms)",
        DELAY to "Atraso antes da ação (ms)", RND to "Atraso aleatório: máximo (ms, 0 = desligado)", RMIN to "Atraso aleatório: mínimo (ms)",
        JIT to "Jitter (px)", GOTO to "Ir para o passo (0 = seguir)", CNT to "Contador (1 a $MAX_COUNTERS)",
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
    )
    private val optional = setOf(DELAY, RND, JIT, RMIN, GOTO) + (CASE0 until CASE0 + MAX_CASES * 2) // em branco vale 0

    private val titles = mapOf(
        Type.TAP to "Toque", Type.SWIPE to "Deslizar", Type.PINCH_IN to "Pinça para dentro", Type.PINCH_OUT to "Pinça para fora",
        Type.WAIT to "Esperar", Type.KEY to "Tecla", Type.STATUS to "Alerta de status", Type.GOTO to "Ir para o passo",
        Type.COUNTER to "Contador de execução", Type.RESET to "Zerar contador", Type.CALL to "Chamar macro",
    )
    private val icons = mapOf(
        Type.TAP to Ic.TAP, Type.SWIPE to Ic.SWIPE, Type.PINCH_IN to Ic.SWIPE, Type.PINCH_OUT to Ic.SWIPE, Type.WAIT to Ic.WAIT,
        Type.KEY to Ic.KEY, Type.STATUS to Ic.WARN, Type.GOTO to Ic.CHEVRON, Type.COUNTER to Ic.ADD, Type.RESET to Ic.BACK, Type.CALL to Ic.PLAY,
    )
    private val tones = mapOf(
        Type.TAP to C.BLUE, Type.SWIPE to C.PURPLE, Type.PINCH_IN to C.PURPLE, Type.PINCH_OUT to C.PURPLE, Type.WAIT to C.ORANGE,
        Type.KEY to C.TEAL, Type.STATUS to C.TEAL, Type.GOTO to C.ORANGE, Type.COUNTER to C.BLUE, Type.RESET to C.RED, Type.CALL to C.PURPLE,
    )

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        store = Store(this)
        val existing = intent.getStringExtra("id")?.let { store.get(it) }
        id = existing?.id ?: UUID.randomUUID().toString()
        steps = existing?.steps.orEmpty()

        name = field(existing?.name ?: "Nova macro", "Nome", numeric = false)
        loops = field((existing?.loops ?: 1).toString(), "0 = ∞", numeric = true)
        header = label("", 14f, bold = true)
        emptyHint = label("Sem passos. Toque em + ou grave pela bolha.", 13f, C.TEXT2).apply { setPadding(dp(2), dp(12), dp(2), 0) }
        list = ListView(this).apply {
            divider = InsetDrawable(ColorDrawable(0x1AFFFFFF), dp(48), 0, 0, 0) // linha começa depois do ícone
            dividerHeight = 1
            isVerticalScrollBarEnabled = true
            setOnItemClickListener { _, _, i, _ -> editStep(steps[i].type, steps[i], steps.size) { replace(i, it) } }
            setOnItemLongClickListener { _, _, i, _ -> rowMenu(i); true }
            adapter = object : BaseAdapter() {
                override fun getCount() = steps.size
                override fun getItem(i: Int) = steps[i]
                override fun getItemId(i: Int) = i.toLong()
                override fun getView(i: Int, v: View?, p: ViewGroup) = stepRow(i, steps[i])
            }
        }
        val bar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(circleBtn(Ic.BACK, Color.TRANSPARENT, 40, "Voltar") { finish() })
            addView(label("Editar macro", 16f, bold = true).apply { setPadding(dp(4), 0, 0, 0); layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f) })
            addView(circleBtn(Ic.ADD, Color.TRANSPARENT, 40, "Adicionar passo") { pickType() })
            addView(circleBtn(Ic.OK, C.BLUE, 40, "Salvar") { save() })
        }
        val fieldsRow = LinearLayout(this).apply {
            addView(labeled("Nome", name, weight = 2f))
            addView(labeled("Repetições", loops, weight = 1f))
        }
        val stepsTile = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = tileBg()
            setPadding(dp(12), dp(10), dp(8), dp(8))
            addView(header)
            addView(emptyHint)
            addView(list, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f).also { it.topMargin = dp(6) })
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f).also { it.topMargin = dp(8) }
        }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glass()
            elevation = dp(8).toFloat()
            setPadding(dp(16), dp(12), dp(16), dp(16))
            addView(bar); addView(fieldsRow); addView(stepsTile)
        }
        setContentView(LinearLayout(this).apply {
            addView(panel, LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            padForSystemBars(dp(12))
        })
        refresh()
    }

    private fun refresh() {
        header.text = "Passos · ${steps.size}"
        emptyHint.visibility = if (steps.isEmpty()) View.VISIBLE else View.GONE
        (list.adapter as BaseAdapter).notifyDataSetChanged()
    }

    private fun replace(i: Int, s: Step) { steps = steps.toMutableList().also { it[i] = s }; refresh() }

    private fun stepRow(i: Int, s: Step): View = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(4), dp(10), dp(4), dp(10))
        addView(badge(icons.getValue(s.type), tones.getValue(s.type), 36))
        addView(LinearLayout(this@EditorActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, dp(8), 0)
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            addView(label("${i + 1}. ${titles.getValue(s.type)}", 15f, bold = true))
            addView(label(detail(s), 13f, C.TEXT2))
        })
        val before = if (s.rnd > 0) "${s.rndMin}–${s.rnd} ms" else if (s.delay > 0) "${s.delay} ms" else ""
        if (before.isNotEmpty()) addView(label("antes:\n$before", 12f, C.TEXT2).apply { gravity = Gravity.END })
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
            Type.CALL -> (store.get(s.text)?.name ?: "macro removida") + if (s.ret) " · volta ao terminar" else " · substitui esta"
        } + skip
    }

    private fun pickType() {
        if (steps.size >= MAX_STEPS) return toast("Limite de $MAX_STEPS passos")
        AlertDialog.Builder(this).setItems(titles.values.toTypedArray()) { _, w ->
            val type = Type.entries[w]
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
                Type.CALL -> Step(type)
            }
            editStep(type, base, steps.size + 1) { steps = steps.afterInsert(steps.size, it); refresh() }
        }.showGlass(this)
    }

    private fun rowMenu(i: Int) {
        AlertDialog.Builder(this).setItems(arrayOf("Duplicar", "Mover para cima", "Mover para baixo", "Excluir")) { _, w ->
            when (w) {
                0 -> if (steps.size < MAX_STEPS) steps = steps.afterInsert(i + 1, steps[i])
                1 -> if (i > 0) steps = steps.afterSwap(i, i - 1)
                2 -> if (i < steps.lastIndex) steps = steps.afterSwap(i, i + 1)
                3 -> steps = steps.afterDelete(i)
            }
            refresh()
        }.showGlass(this)
    }

    /** [total] é o número de passos depois da edição, para validar os destinos de salto. */
    private fun editStep(type: Type, s: Step, total: Int, done: (Step) -> Unit) {
        val v = IntArray(CASE0 + MAX_CASES * 2)
        v[X1] = s.x1; v[Y1] = s.y1; v[X2] = s.x2; v[Y2] = s.y2; v[MS] = s.ms; v[DELAY] = s.delay; v[RND] = s.rnd
        v[JIT] = s.jitter; v[RMIN] = s.rndMin; v[GOTO] = s.goto; v[CNT] = s.counter
        s.cases.forEachIndexed { k, c -> v[CASE0 + 2 * k] = c.n; v[CASE0 + 2 * k + 1] = c.to }

        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(20), dp(20), dp(4)) }
        col.addView(label(titles.getValue(type), 20f, bold = true).apply { setPadding(0, 0, 0, dp(8)) })

        var text = s.text
        val textEdit = if (type == Type.STATUS) field(s.text, "Mensagem", numeric = false).also { col.addView(labeled("Mensagem", it)) } else null
        if (type == Type.CALL) {
            val btn = textAction(store.get(s.text)?.name ?: "Escolher macro…") {}
            btn.setOnClickListener { pickMacro { m -> text = m.id; btn.text = m.name } }
            col.addView(labeled("Macro chamada", btn))
        }

        val idx = fields.getValue(type)
        val edits = idx.map { k ->
            val lbl = when {
                k == X1 && type == Type.KEY -> "Tecla (1 voltar, 2 início, 3 recentes, 4 notificações)"
                k == MS && type == Type.WAIT -> "Espera (ms)"
                k == MS && type == Type.STATUS -> "Tempo da mensagem (ms)"
                k >= CASE0 -> if ((k - CASE0) % 2 == 0) "Caso ${(k - CASE0) / 2 + 1}: ao chegar a (0 = vazio)" else "Caso ${(k - CASE0) / 2 + 1}: ir para o passo (0 = seguir)"
                k == GOTO && type == Type.COUNTER -> "Destino padrão (0 = seguir)"
                else -> labels.getValue(k)
            }
            field(if (v[k] == 0 && k in optional) "" else v[k].toString(), lbl, numeric = true).also { col.addView(labeled(lbl, it)) }
        }
        val skipSw = if (type in Step.GESTURES || type == Type.KEY || type == Type.STATUS || type == Type.CALL)
            Switch(this).apply { isChecked = s.skip; contentDescription = "Pular a ação" }.also { col.addView(labeled("Pular a ação (só roda o atraso)", it)) } else null
        val retSw = if (type == Type.CALL)
            Switch(this).apply { isChecked = s.ret; contentDescription = "Voltar ao terminar" }.also { col.addView(labeled("Voltar a esta macro ao terminar", it)) } else null

        val dlg = AlertDialog.Builder(this).setView(ScrollView(this).apply { addView(col) })
            .setPositiveButton("OK", null).setNegativeButton("Cancelar", null).create()
        dlg.window?.setBackgroundDrawable(rounded(0xF2161C2B.toInt(), 24, C.EDGE)) // mesmo vidro escuro do painel
        dlg.show()
        // Listener próprio: o padrão fecha o diálogo mesmo com valor inválido.
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val nv = v.copyOf()
            for ((n, k) in idx.withIndex()) {
                val raw = edits[n].text.toString().trim()
                nv[k] = if (raw.isEmpty() && k in optional) 0 else raw.toIntOrNull() ?: run { edits[n].error = "número inteiro"; return@setOnClickListener }
            }
            val cases = (0 until MAX_CASES).map { Case(nv[CASE0 + 2 * it], nv[CASE0 + 2 * it + 1]) }.filter { it.n > 0 }
            val ns = Step(
                type, nv[X1], nv[Y1], nv[X2], nv[Y2], nv[MS], nv[DELAY], nv[RND], nv[RMIN], nv[JIT],
                skipSw?.isChecked ?: false, nv[GOTO], if (nv[CNT] == 0) 1 else nv[CNT], if (type == Type.COUNTER) cases else emptyList(),
                if (type == Type.STATUS) textEdit!!.text.toString().trim() else if (type == Type.CALL) text else "", retSw?.isChecked ?: true,
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
        AlertDialog.Builder(this).setItems(others.map { it.name }.toTypedArray()) { _, w -> chosen(others[w]) }.showGlass(this)
    }

    private fun save() {
        val n = loops.text.toString().toIntOrNull() ?: return toast("Repetições deve ser um número inteiro")
        val m = Macro(id, name.text.toString().trim(), n, steps)
        m.validate()?.let { return toast(it) }
        try { store.save(m); finish() } catch (e: Exception) { toast("Falha ao salvar: ${e.message}") }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
}

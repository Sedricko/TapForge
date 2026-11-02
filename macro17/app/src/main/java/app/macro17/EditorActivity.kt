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
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
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
    private val steps = ArrayList<Step>()

    // Ordem dos campos no diálogo: índice em IntArray [x1,y1,x2,y2,ms,delay,rnd,jitter].
    private val labels = arrayOf("X", "Y", "X final", "Y final", "Duração / espera (ms)", "Pausa depois (ms)", "Pausa extra aleatória (ms)", "Jitter (px)")
    private val fields = mapOf(
        Type.TAP to intArrayOf(0, 1, 4, 7, 5, 6),
        Type.SWIPE to intArrayOf(0, 1, 2, 3, 4, 7, 5, 6),
        Type.WAIT to intArrayOf(4),
        Type.KEY to intArrayOf(0, 5, 6),
    )
    private val titles = mapOf(Type.TAP to "Toque", Type.SWIPE to "Deslizar", Type.WAIT to "Esperar", Type.KEY to "Tecla")
    private val icons = mapOf(Type.TAP to Ic.TAP, Type.SWIPE to Ic.SWIPE, Type.WAIT to Ic.WAIT, Type.KEY to Ic.KEY)
    private val tones = mapOf(Type.TAP to C.BLUE, Type.SWIPE to C.PURPLE, Type.WAIT to C.ORANGE, Type.KEY to C.TEAL)

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        store = Store(this)
        val existing = intent.getStringExtra("id")?.let { store.get(it) }
        id = existing?.id ?: UUID.randomUUID().toString()
        steps.addAll(existing?.steps.orEmpty())

        name = field(existing?.name ?: "Nova macro", "Nome", numeric = false)
        loops = field((existing?.loops ?: 1).toString(), "0 = ∞", numeric = true)
        header = label("", 14f, bold = true)
        emptyHint = label("Sem passos. Toque em + ou grave pela bolha.", 13f, C.TEXT2).apply { setPadding(dp(2), dp(12), dp(2), 0) }
        list = ListView(this).apply {
            divider = InsetDrawable(ColorDrawable(0x1AFFFFFF), dp(48), 0, 0, 0) // linha começa depois do ícone
            dividerHeight = 1
            isVerticalScrollBarEnabled = true
            setOnItemClickListener { _, _, i, _ -> editStep(steps[i].type, steps[i]) { steps[i] = it; refresh() } }
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
        val pause = (if (s.delay > 0) "+${s.delay} ms" else "") + (if (s.rnd > 0) "\n+0–${s.rnd} ms" else "")
        if (pause.isNotEmpty()) addView(label(pause, 12f, C.TEXT2).apply { gravity = Gravity.END })
    }

    private fun detail(s: Step): String {
        val jit = if (s.jitter > 0) " · ±${s.jitter} px" else ""
        return when (s.type) {
            Type.TAP -> "(${s.x1}, ${s.y1}) · ${s.ms} ms$jit"
            Type.SWIPE -> "(${s.x1}, ${s.y1}) → (${s.x2}, ${s.y2}) · ${s.ms} ms$jit"
            Type.WAIT -> "${s.ms} ms"
            Type.KEY -> arrayOf("?", "Voltar", "Início", "Recentes", "Notificações")[s.x1.coerceIn(0, 4)]
        }
    }

    private fun pickType() {
        val t = arrayOf("Toque", "Deslizar", "Esperar", "Tecla (1 voltar, 2 início, 3 recentes, 4 notificações)")
        AlertDialog.Builder(this).setItems(t) { _, w ->
            val type = Type.entries[w]
            // Padrões que já passam na validação; a tela mostra um centro razoável.
            val base = when (type) {
                Type.TAP -> Step(type, 500, 1000, ms = 50)
                Type.SWIPE -> Step(type, 500, 1500, 500, 500, ms = 300)
                Type.WAIT -> Step(type, ms = 1000)
                Type.KEY -> Step(type, x1 = 1)
            }
            editStep(type, base) { steps.add(it); refresh() }
        }.showGlass(this)
    }

    private fun rowMenu(i: Int) {
        AlertDialog.Builder(this).setItems(arrayOf("Duplicar", "Mover para cima", "Mover para baixo", "Excluir")) { _, w ->
            when (w) {
                0 -> if (steps.size < MAX_STEPS) steps.add(i + 1, steps[i])
                1 -> if (i > 0) steps.add(i - 1, steps.removeAt(i))
                2 -> if (i < steps.lastIndex) steps.add(i + 1, steps.removeAt(i))
                3 -> steps.removeAt(i)
            }
            refresh()
        }.showGlass(this)
    }

    private fun editStep(type: Type, s: Step, done: (Step) -> Unit) {
        val v = intArrayOf(s.x1, s.y1, s.x2, s.y2, s.ms, s.delay, s.rnd, s.jitter)
        val idx = fields.getValue(type)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(20), dp(20), dp(4)) }
        col.addView(label(titles.getValue(type), 20f, bold = true).apply { setPadding(0, 0, 0, dp(8)) })
        val edits = idx.map { k -> field(v[k].toString(), labels[k], numeric = true).also { col.addView(labeled(labels[k], it)) } }
        val dlg = AlertDialog.Builder(this).setView(ScrollView(this).apply { addView(col) })
            .setPositiveButton("OK", null).setNegativeButton("Cancelar", null).create()
        dlg.window?.setBackgroundDrawable(rounded(0xF2161C2B.toInt(), 24, C.EDGE)) // mesmo vidro escuro do painel
        dlg.show()
        // Listener próprio: o padrão fecha o diálogo mesmo com valor inválido.
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val nv = v.copyOf()
            for ((n, k) in idx.withIndex()) {
                nv[k] = edits[n].text.toString().toIntOrNull() ?: run { edits[n].error = "número inteiro"; return@setOnClickListener }
            }
            val ns = Step(type, nv[0], nv[1], nv[2], nv[3], nv[4], nv[5], nv[6], nv[7])
            val err = ns.validate()
            if (err != null) { edits[0].error = err; return@setOnClickListener }
            done(ns); dlg.dismiss()
        }
    }

    private fun save() {
        val n = loops.text.toString().toIntOrNull() ?: return toast("Repetições deve ser um número inteiro")
        val m = Macro(id, name.text.toString().trim(), n, steps.toList())
        m.validate()?.let { return toast(it) }
        try { store.save(m); finish() } catch (e: Exception) { toast("Falha ao salvar: ${e.message}") }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
}

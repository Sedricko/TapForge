package app.macro17

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.ArrayAdapter
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

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        store = Store(this)
        val existing = intent.getStringExtra("id")?.let { store.get(it) }
        id = existing?.id ?: UUID.randomUUID().toString()
        steps.addAll(existing?.steps.orEmpty())

        name = field(existing?.name ?: "Nova macro", "Nome", numeric = false)
        loops = field((existing?.loops ?: 1).toString(), "Repetições", numeric = true)
        list = ListView(this).apply {
            divider = ColorDrawable(C.LINE); dividerHeight = 1
            clipToPadding = false; setPadding(0, 0, 0, dp(88)) // vão para o FAB
            setOnItemClickListener { _, _, i, _ -> editStep(steps[i].type, steps[i]) { steps[i] = it; refresh() } }
            setOnItemLongClickListener { _, _, i, _ -> rowMenu(i); true }
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(labeled("Nome", name))
            addView(labeled("Repetições (0 = infinito)", loops))
            addView(label("Passos", 16f, bold = true).apply { setPadding(dp(2), dp(12), 0, 0) })
            addView(label("Toque para editar; segure para duplicar, mover ou excluir.", 12f, C.GRAY).apply { setPadding(dp(2), 0, 0, dp(4)) })
            addView(list, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
            padForSystemBars(dp(12), top = false)
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(appBar("Editar macro", back = true, action = Ic.OK to { save() }))
            addView(content, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        }
        setContentView(FrameLayout(this).apply {
            setBackgroundColor(C.BG)
            addView(column, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            addView(fab("Adicionar passo") { pickType() })
        })
        refresh()
    }

    private fun refresh() {
        list.adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, steps.mapIndexed { i, s -> "${i + 1}. ${describe(s)}" }) {
            override fun getView(p: Int, v: View?, g: ViewGroup): View = (super.getView(p, v, g) as TextView).apply {
                textSize = 14f; typeface = Typeface.MONOSPACE; setTextColor(Color.BLACK)
                setPadding(dp(8), dp(12), dp(8), dp(12))
            }
        }
    }

    private fun describe(s: Step): String {
        val jit = if (s.jitter > 0) " ±${s.jitter}px" else ""
        val pause = (if (s.delay > 0) " +${s.delay}ms" else "") + (if (s.rnd > 0) " +rnd${s.rnd}" else "")
        return when (s.type) {
            Type.TAP -> "Toque (${s.x1},${s.y1}) ${s.ms}ms$jit"
            Type.SWIPE -> "Deslizar (${s.x1},${s.y1})→(${s.x2},${s.y2}) ${s.ms}ms$jit"
            Type.WAIT -> "Esperar ${s.ms}ms"
            Type.KEY -> "Tecla " + arrayOf("?", "Voltar", "Início", "Recentes", "Notificações")[s.x1.coerceIn(0, 4)]
        } + pause
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
        }.show()
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
        }.show()
    }

    private fun editStep(type: Type, s: Step, done: (Step) -> Unit) {
        val v = intArrayOf(s.x1, s.y1, s.x2, s.y2, s.ms, s.delay, s.rnd, s.jitter)
        val idx = fields.getValue(type)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(20), dp(20), dp(4)) }
        col.addView(label(titles.getValue(type), 20f, bold = true).apply { setPadding(0, 0, 0, dp(8)) })
        val edits = idx.map { k -> field(v[k].toString(), labels[k], numeric = true).also { col.addView(labeled(labels[k], it)) } }
        val dlg = AlertDialog.Builder(this).setView(ScrollView(this).apply { addView(col) })
            .setPositiveButton("OK", null).setNegativeButton("Cancelar", null).create()
        dlg.window?.setBackgroundDrawable(rounded(Color.WHITE, 24)) // card de 24dp como no app de referência
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

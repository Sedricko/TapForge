package app.macro17

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
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

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        store = Store(this)
        val given = intent.getStringExtra("id")
        val existing = given?.let { store.get(it) }
        id = existing?.id ?: UUID.randomUUID().toString()
        steps.addAll(existing?.steps.orEmpty())

        name = EditText(this).apply { hint = "Nome"; setText(existing?.name ?: "Nova macro"); setSingleLine() }
        loops = EditText(this).apply { hint = "Repetições (0 = infinito)"; inputType = InputType.TYPE_CLASS_NUMBER; setText((existing?.loops ?: 1).toString()) }
        list = ListView(this).apply {
            setOnItemClickListener { _, _, i, _ -> editStep(steps[i].type, steps[i]) { steps[i] = it; refresh() } }
            setOnItemLongClickListener { _, _, i, _ -> rowMenu(i); true }
        }
        val row = LinearLayout(this).apply {
            addView(button("+ Passo") { pickType() })
            addView(button("Salvar") { save() })
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(name); addView(loops)
            addView(TextView(this@EditorActivity).apply { text = "Toque num passo para editar; segure para mais opções." })
            addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(row)
            padForSystemBars(dp(16))
        })
        refresh()
    }

    private fun refresh() {
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, steps.mapIndexed { i, s -> "${i + 1}. ${describe(s)}" })
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
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0) }
        val edits = idx.map { k ->
            EditText(this).apply { hint = labels[k]; inputType = InputType.TYPE_CLASS_NUMBER; setText(v[k].toString()) }
                .also { box.addView(TextView(this).apply { text = labels[k] }); box.addView(it) }
        }
        val dlg = AlertDialog.Builder(this).setView(android.widget.ScrollView(this).apply { addView(box) })
            .setPositiveButton("OK", null).setNegativeButton("Cancelar", null).create()
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

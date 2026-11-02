package app.macro17

import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
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
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.util.UUID

// startActivityForResult é a API de plataforma; a alternativa (Activity Result API) exige AndroidX.
@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
class MainActivity : Activity() {
    private lateinit var store: Store
    private lateinit var search: EditText
    private lateinit var list: ListView
    private lateinit var statusBox: FrameLayout
    private lateinit var selBox: FrameLayout
    private var macros = emptyList<Macro>()
    private var shown = emptyList<Macro>()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        store = Store(this)
        val ctx = this

        search = EditText(this).apply {
            hint = "Buscar macros"; setHintTextColor(0x991B2233.toInt()); setTextColor(C.INK); textSize = 15f
            background = null; setSingleLine()
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) { applyFilter() }
                override fun beforeTextChanged(s: CharSequence?, st: Int, cn: Int, af: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, bf: Int, cn: Int) {}
            })
        }
        val emptyText = label("Nenhuma macro. Toque em + ou grave pela bolha.", 13f, C.TEXT2).apply { setPadding(dp(4), dp(16), dp(4), 0) }
        list = ListView(this).apply {
            divider = null; dividerHeight = 0
            emptyView = emptyText
            setOnItemClickListener { _, _, i, _ -> menu(shown[i]) }
            adapter = object : BaseAdapter() {
                override fun getCount() = shown.size
                override fun getItem(i: Int) = shown[i]
                override fun getItemId(i: Int) = i.toLong()
                override fun getView(i: Int, v: View?, p: ViewGroup) = macroRow(shown[i])
            }
        }
        statusBox = FrameLayout(this)
        selBox = FrameLayout(this)

        // Linha 1: grade 2×2 de ações + status do serviço (o "calendário" do conceito).
        val quick = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = tileBg()
            setPadding(dp(6), dp(6), dp(6), dp(6))
            addView(LinearLayout(ctx).apply {
                addView(squareBtn(Ic.PLAY, C.BLUE, 44, "Executar macro da bolha") { playSelected() })
                addView(squareBtn(Ic.STOP, C.RED, 44, "Parar macro") { MacroService.instance?.stopMacro() })
            })
            addView(LinearLayout(ctx).apply {
                addView(squareBtn(Ic.IMPORT, C.ORANGE, 44, "Importar macro") { importFile() })
                addView(squareBtn(Ic.GEAR, C.PURPLE, 44, "Ajustes de acessibilidade") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) })
            })
            layoutParams = LinearLayout.LayoutParams(dp(120), WRAP_CONTENT)
        }
        val statusTile = LinearLayout(this).apply {
            background = tileBg()
            setPadding(dp(12), dp(10), dp(12), dp(10))
            addView(statusBox, LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            layoutParams = LinearLayout.LayoutParams(0, MATCH_PARENT, 1f).also { it.marginStart = dp(8) }
        }
        val row1 = LinearLayout(this).apply {
            addView(quick); addView(statusTile)
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).also { it.topMargin = dp(8) }
        }

        // Linha 2: macro da bolha (o "celular" do conceito) + lista com rolagem (o "All Apps").
        val selTile = LinearLayout(this).apply {
            background = tileBg()
            setPadding(dp(8), dp(10), dp(8), dp(8))
            addView(selBox, LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            layoutParams = LinearLayout.LayoutParams(dp(120), MATCH_PARENT)
        }
        val listTile = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = tileBg()
            setPadding(dp(12), dp(10), dp(8), dp(8))
            addView(LinearLayout(ctx).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(label("Todas as macros", 14f, bold = true).apply { layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f) })
                addView(glyph(Ic.CHEVRON, C.TEXT, 22))
            })
            addView(list, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f).also { it.topMargin = dp(6) })
            addView(emptyText)
            layoutParams = LinearLayout.LayoutParams(0, MATCH_PARENT, 1f).also { it.marginStart = dp(8) }
        }
        val row2 = LinearLayout(this).apply {
            addView(selTile); addView(listTile)
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f).also { it.topMargin = dp(8) }
        }

        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), 0, 0, 0)
            addView(label("Macros", 16f, bold = true).apply { layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f) })
            addView(circleBtn(Ic.GEAR, Color.TRANSPARENT, 40, "Opções") { startActivity(Intent(this@MainActivity, OptionsActivity::class.java)) })
            addView(circleBtn(Ic.ADD, Color.TRANSPARENT, 40, "Nova macro") { edit("new") })
        }
        val footer = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(12), 0, 0)
            addView(label("M", 13f, Color.WHITE, bold = true).apply {
                gravity = Gravity.CENTER; background = rounded(C.ACCENT, 14); layoutParams = LinearLayout.LayoutParams(dp(28), dp(28))
            })
            addView(label("Macro17", 13f).apply { setPadding(dp(10), 0, 0, 0) })
        }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glass()
            elevation = dp(8).toFloat()
            setPadding(dp(16), dp(12), dp(16), dp(14))
            addView(header); addView(row1); addView(row2); addView(footer)
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f)
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(searchPill(search), LinearLayout.LayoutParams(MATCH_PARENT, dp(44)).also { it.bottomMargin = dp(12) })
            addView(panel)
            padForSystemBars(dp(12))
        })
    }

    override fun onResume() { super.onResume(); refresh() }

    private fun refresh() {
        macros = store.list()
        applyFilter()
        fillStatus()
        fillSelected()
    }

    private fun applyFilter() {
        val q = search.text.toString().trim()
        shown = if (q.isEmpty()) macros else macros.filter { it.name.contains(q, ignoreCase = true) }
        (list.adapter as BaseAdapter).notifyDataSetChanged()
    }

    private fun fillStatus() {
        val on = MacroService.instance != null
        val tone = if (on) C.ACCENT else C.RED
        statusBox.removeAllViews()
        statusBox.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(LinearLayout(this@MainActivity).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(label("Serviço", 14f, bold = true).apply { layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f) })
                addView(label(if (on) "ativo" else "desligado", 12f, tone))
            })
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f)
                addView(glyph(if (on) Ic.OK else Ic.WARN, tone, 32))
                addView(label(if (on) "Bolha na tela" else "Ative “Macro17” em Acessibilidade", 13f, C.TEXT2).apply {
                    gravity = Gravity.CENTER; setPadding(0, dp(4), 0, 0)
                })
                if (!on) addView(textAction("Ativar agora") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) })
            })
        })
    }

    private fun fillSelected() {
        val m = macros.find { it.id == store.selected }
        selBox.removeAllViews()
        selBox.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            if (m == null) {
                addView(label("Nenhuma macro na bolha", 13f, C.TEXT2, bold = true).apply { gravity = Gravity.CENTER; setPadding(0, dp(20), 0, dp(4)) })
                addView(label("Toque numa macro e escolha Selecionar.", 11f, C.TEXT2).apply { gravity = Gravity.CENTER })
            } else {
                addView(monogram(m.name, 56))
                addView(label(m.name, 13f, bold = true).apply {
                    gravity = Gravity.CENTER; maxLines = 2; ellipsize = TextUtils.TruncateAt.END; setPadding(0, dp(8), 0, 0)
                })
                addView(label("${m.steps.size} passos", 11f, C.TEXT2).apply { gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(8)) })
                addView(LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    background = rounded(0x1A000000, 12)
                    setPadding(dp(8), dp(4), dp(8), dp(4))
                    addView(miniAction("Executar", Ic.PLAY) { play(m.id) })
                    addView(miniAction("Editar", Ic.EDIT) { edit(m.id) })
                    addView(miniAction("Agendar", Ic.WAIT) { schedule(m) })
                })
            }
        })
    }

    private fun miniAction(text: String, ic: Ic, click: () -> Unit): View = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(4), dp(8), dp(4), dp(8))
        foreground = ripple(8)
        addView(glyph(ic, C.TEXT, 18))
        addView(label(text, 12f).apply { setPadding(dp(8), 0, 0, 0) })
        setOnClickListener { click() }
    }

    private fun macroRow(m: Macro): View = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(6), dp(6), dp(6), dp(6))
        foreground = ripple(10)
        val selected = m.id == store.selected
        if (selected) background = rounded(0x1FFFFFFF, 10)
        addView(monogram(m.name, 32))
        addView(label(m.name, 14f).apply {
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END; setPadding(dp(10), 0, dp(4), 0)
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        })
        if (selected) addView(glyph(Ic.OK, C.ACCENT, 20))
    }

    private fun play(id: String) { MacroService.instance?.runMacro(id) ?: toast("Ative o serviço de acessibilidade primeiro") }

    private fun playSelected() { store.selected?.let { play(it) } ?: toast("Selecione uma macro primeiro") }

    private fun edit(id: String) = startActivity(Intent(this, EditorActivity::class.java).putExtra("id", id))

    private fun menu(m: Macro) {
        val items = arrayOf("Selecionar para a bolha", "Executar agora", "Editar", "Agendar…", "Exportar…", "Excluir")
        AlertDialog.Builder(this).setTitle(m.name).setItems(items) { _, w ->
            when (w) {
                0 -> { store.selected = m.id; refresh() }
                1 -> play(m.id)
                2 -> edit(m.id)
                3 -> schedule(m)
                4 -> { store.pendingExport = m.id; startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json").putExtra(Intent.EXTRA_TITLE, "${m.name}.json"), EXPORT) }
                5 -> AlertDialog.Builder(this).setMessage("Excluir “${m.name}”?")
                    .setPositiveButton("Excluir") { _, _ -> store.delete(m.id); refresh() }.setNegativeButton("Cancelar", null).showGlass(this)
            }
        }.showGlass(this)
    }

    private fun schedule(m: Macro) {
        TimePickerDialog(this, { _, h, min ->
            val exact = Alarms.schedule(this, m.id, h, min)
            toast("Agendada para %02d:%02d".format(h, min) + if (exact) "" else " (aproximada: permita “Alarmes e lembretes” nas configurações do app)")
        }, 8, 0, true).show()
    }

    private fun importFile() =
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), IMPORT)

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        val uri: Uri = data?.data ?: return
        if (res != RESULT_OK) return
        try {
            when (req) {
                EXPORT -> {
                    val m = store.pendingExport?.let { store.get(it) } ?: throw IllegalStateException("macro não encontrada")
                    contentResolver.openOutputStream(uri, "wt")!!.use { it.write(m.toJson().toString(2).toByteArray()) }
                    toast("Exportada")
                }
                IMPORT -> {
                    val raw = contentResolver.openInputStream(uri)!!.use { Store.readLimited(it) }
                    val m = Macro.fromJson(JSONObject(String(raw)), UUID.randomUUID().toString())
                    m.validate()?.let { throw IllegalArgumentException(it) }
                    store.save(m); refresh(); toast("Importada: ${m.name}")
                }
            }
        } catch (e: Exception) { // JSONException, IllegalArgumentException, IOException, null do provider
            toast("Falhou: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    private companion object { const val EXPORT = 1; const val IMPORT = 2 }
}

package app.macro17

import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import android.widget.Toast
import org.json.JSONObject
import java.util.UUID

// startActivityForResult é a API de plataforma; a alternativa (Activity Result API) exige AndroidX.
@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
class MainActivity : Activity() {
    private lateinit var store: Store
    private lateinit var search: EditText
    private lateinit var searchBox: View
    private lateinit var statusRow: FrameLayout
    private lateinit var chips: LinearLayout
    private lateinit var grid: LinearLayout
    private var macros = emptyList<Macro>()
    private var filter = 0 // 0 todas, 1 agendadas

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        store = Store(this)

        search = field("", "Buscar macros", numeric = false).apply {
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) { fillGrid() }
                override fun beforeTextChanged(s: CharSequence?, st: Int, cn: Int, af: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, bf: Int, cn: Int) {}
            })
        }
        searchBox = LinearLayout(this).apply {
            background = tileBg()
            setPadding(dp(14), dp(2), dp(14), dp(2))
            visibility = View.GONE
            addView(search, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).also { it.topMargin = dp(8) }
        }
        val top = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), 0, 0, 0)
            addView(label("Macros", 22f, bold = true).apply { layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f) })
            addView(circleBtn(Ic.SEARCH, Color.TRANSPARENT, 40, "Buscar") { toggleSearch() })
            addView(circleBtn(Ic.IMPORT, Color.TRANSPARENT, 40, "Importar macro") { importFile() })
            addView(circleBtn(Ic.GEAR, Color.TRANSPARENT, 40, "Opções") { startActivity(Intent(this@MainActivity, OptionsActivity::class.java)) })
        }
        statusRow = FrameLayout(this)
        chips = LinearLayout(this).apply { setPadding(dp(2), dp(10), 0, dp(10)) }
        grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(grid) }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(top); addView(statusRow); addView(searchBox); addView(chips)
            addView(scroll, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
            padForSystemBars(dp(12))
        })
    }

    override fun onResume() { super.onResume(); refresh() }

    private fun toggleSearch() {
        val show = searchBox.visibility != View.VISIBLE
        searchBox.visibility = if (show) View.VISIBLE else View.GONE
        if (show) {
            search.requestFocus()
            getSystemService(InputMethodManager::class.java).showSoftInput(search, 0)
        } else search.setText("")
    }

    /** Horário do alarme, só se ele ainda existir (reinício apaga os alarmes). */
    private fun scheduled(id: String) = store.scheduleTime(id)?.takeIf { Alarms.isScheduled(this, id) }

    private fun refresh() {
        macros = store.list()
        fillStatus()
        fillChips()
        fillGrid()
    }

    private fun fillStatus() {
        val on = MacroService.instance != null
        statusRow.removeAllViews()
        statusRow.addView(
            if (on) LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(6), dp(4), 0, 0)
                addView(View(this@MainActivity).apply {
                    background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(C.ACCENT) }
                    layoutParams = LinearLayout.LayoutParams(dp(8), dp(8)).also { it.setMargins(0, 0, dp(8), 0) }
                })
                addView(label("Serviço ativo · orbe na tela", 12f, C.TEXT2))
            } else LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                background = tileBg()
                setPadding(dp(14), dp(10), dp(8), dp(10))
                addView(glyph(Ic.WARN, C.RED, 28))
                addView(LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(12), 0, dp(4), 0)
                    layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
                    addView(label("Serviço desligado", 14f, bold = true))
                    addView(label("Ative “Macro17” em Acessibilidade. Se estiver cinza: Informações do app > ⋮ > Permitir configurações restritas.", 12f, C.TEXT2))
                })
                addView(textAction("Ativar") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) })
            }
        )
    }

    private fun fillChips() {
        chips.removeAllViews()
        chips.addView(chipBtn("Todas · ${macros.size}", filter == 0) { filter = 0; refresh() })
        chips.addView(chipBtn("Agendadas · ${macros.count { scheduled(it.id) != null }}", filter == 1) { filter = 1; refresh() })
    }

    private fun fillGrid() {
        val q = search.text.toString().trim()
        var list = macros
        if (filter == 1) list = list.filter { scheduled(it.id) != null }
        if (q.isNotEmpty()) list = list.filter { it.name.contains(q, ignoreCase = true) }
        val cells = list.map { card(it) } + if (filter == 0 && q.isEmpty()) listOf(newCard()) else emptyList()
        grid.removeAllViews()
        if (cells.isEmpty()) grid.addView(label(if (q.isNotEmpty()) "Nada encontrado." else "Nenhuma macro agendada.", 14f, C.TEXT2).apply { setPadding(dp(6), dp(16), 0, 0) })
        cells.chunked(2).forEach { pair ->
            grid.addView(LinearLayout(this).apply {
                pair.forEach { addView(it) }
                if (pair.size == 1) addView(Space(this@MainActivity), LinearLayout.LayoutParams(0, 1, 1f))
            })
        }
    }

    private fun cardParams() = LinearLayout.LayoutParams(0, MATCH_PARENT, 1f).also { it.setMargins(dp(4), dp(4), dp(4), dp(4)) }

    private fun card(m: Macro): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(12), dp(12), dp(12), dp(12))
        val sel = m.id == store.selected
        background = GradientDrawable().apply {
            setColor(C.TILE); cornerRadius = dp(20).toFloat(); setStroke(dp(if (sel) 2 else 1), if (sel) C.ACCENT else C.EDGE)
        }
        foreground = ripple(20)
        minimumHeight = dp(128)
        layoutParams = cardParams()
        addView(LinearLayout(this@MainActivity).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(monogram(m.name, 44))
            addView(View(this@MainActivity), LinearLayout.LayoutParams(0, 1, 1f))
            addView(circleBtn(Ic.PLAY, C.BLUE, 32, "Executar ${m.name}") { play(m.id) })
        })
        addView(View(this@MainActivity), LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        addView(label(m.name, 14f, bold = true).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END; setPadding(0, dp(8), 0, 0) })
        val loops = when (m.loops) { 0 -> "∞"; else -> "${m.loops}×" }
        addView(label("${m.steps.size} passos · $loops" + (scheduled(m.id)?.let { " · $it" } ?: ""), 11.5f, C.TEXT2))
        setOnClickListener { menu(m) }
    }

    private fun newCard(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        background = GradientDrawable().apply {
            setColor(Color.TRANSPARENT); cornerRadius = dp(20).toFloat(); setStroke(dp(1), C.EDGE, dp(6).toFloat(), dp(4).toFloat())
        }
        foreground = ripple(20)
        minimumHeight = dp(128)
        layoutParams = cardParams()
        addView(glyph(Ic.ADD, C.TEXT2, 32))
        addView(label("Nova macro", 13f, C.TEXT2))
        setOnClickListener { edit("new") }
    }

    private fun play(id: String) { MacroService.instance?.runMacro(id) ?: toast("Ative o serviço de acessibilidade primeiro") }

    private fun edit(id: String) = startActivity(Intent(this, EditorActivity::class.java).putExtra("id", id))

    private fun menu(m: Macro) {
        val acts = buildList<Pair<String, () -> Unit>> {
            add("Selecionar para o orbe" to { store.selected = m.id; refresh() })
            add("Executar agora" to { play(m.id) })
            add("Editar" to { edit(m.id) })
            add("Agendar…" to { schedule(m) })
            if (scheduled(m.id) != null) add("Cancelar agendamento" to { Alarms.cancel(this@MainActivity, m.id); store.setScheduleTime(m.id, null); refresh() })
            add("Exportar…" to { exportMacro(m) })
            add("Excluir" to { confirmDelete(m) })
        }
        AlertDialog.Builder(this).setTitle(m.name).setItems(acts.map { it.first }.toTypedArray()) { _, w -> acts[w].second() }.showGlass(this)
    }

    private fun confirmDelete(m: Macro) {
        AlertDialog.Builder(this).setMessage("Excluir “${m.name}”?")
            .setPositiveButton("Excluir") { _, _ -> Alarms.cancel(this, m.id); store.delete(m.id); refresh() }
            .setNegativeButton("Cancelar", null).showGlass(this)
    }

    private fun schedule(m: Macro) {
        TimePickerDialog(this, { _, h, min ->
            val exact = Alarms.schedule(this, m.id, h, min)
            store.setScheduleTime(m.id, "%02d:%02d".format(h, min))
            refresh()
            toast("Agendada para %02d:%02d".format(h, min) + if (exact) "" else " (aproximada: permita “Alarmes e lembretes” nas configurações do app)")
        }, 8, 0, true).show()
    }

    private fun exportMacro(m: Macro) {
        store.pendingExport = m.id
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json").putExtra(Intent.EXTRA_TITLE, "${m.name}.json"), EXPORT)
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

package app.macro17

import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import org.json.JSONObject
import java.util.UUID

// startActivityForResult é a API de plataforma; a alternativa (Activity Result API) exige AndroidX.
@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
class MainActivity : Activity() {
    private lateinit var store: Store
    private lateinit var content: LinearLayout
    private lateinit var drawer: Drawer
    private var macros = emptyList<Macro>()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        store = Store(this)
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(4), dp(4), dp(4), dp(4)) }
        val scroll = ScrollView(this).apply {
            addView(content)
            setOnApplyWindowInsetsListener { v, i -> v.setPadding(0, 0, 0, i.getInsets(WindowInsets.Type.navigationBars()).bottom); i }
        }
        // Ações ainda sem tela própria ficam fora do menu em vez de fingir que existem.
        drawer = Drawer(this, listOf(
            Triple(Ic.HOME, "Home") {},
            Triple(Ic.GEAR, "Configurações") { startActivity(Intent(this, OptionsActivity::class.java)) },
            Triple(Ic.INFO, "Registro ao vivo") { MacroService.instance?.toggleLog() ?: toast("Ative o serviço de acessibilidade primeiro") },
            Triple(Ic.INFO, "Diagnóstico e logs") { startActivity(Intent(this, DiagActivity::class.java)) },
        ))
        val root = FrameLayout(this).apply {
            setBackgroundColor(B.BG)
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(appBar("Macro17", Ic.MENU, { drawer.open() }))
                addView(scroll, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
            }, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        }
        drawer.attach(root)
        setContentView(root)
    }

    override fun onResume() { super.onResume(); refresh() }

    override fun onBackPressed() { if (drawer.isOpen) drawer.close() else super.onBackPressed() }

    /** Horário do alarme, só se ele ainda existir (reinício apaga os alarmes). */
    private fun scheduled(id: String) = store.scheduleTime(id)?.takeIf { Alarms.isScheduled(this, id) }

    /** Macro atual; sem seleção válida, assume a primeira (o orbe lê a mesma seleção). */
    private fun current(): Macro? {
        val m = store.selected?.let { store.get(it) } ?: macros.firstOrNull()
        if (m != null && store.selected != m.id) store.selected = m.id
        return m
    }

    private fun refresh() {
        macros = store.list()
        val cur = current()
        content.removeAllViews()

        content.addView(card().apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            if (MacroService.instance != null) {
                addView(glyph(Ic.INFO, B.PRIMARY, 24))
                addView(label("Permissão de Acessibilidade Concedida", 15f, B.TEXT2, bold = true).apply { setPadding(dp(8), 0, 0, 0) })
            } else {
                addView(glyph(Ic.WARN, B.RED, 24))
                addView(LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(8), 0, 0, 0)
                    addView(label("A permissão de acessibilidade deve ser concedida.", 15f, B.TEXT2, bold = true))
                    addView(label("Toque aqui. Se “Macro17” estiver cinza: Informações do app > ⋮ > Permitir configurações restritas.", 12f, B.TEXT2))
                })
                setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            }
        })

        content.addView(card().apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            if (cur == null) {
                addView(label("Nenhuma macro", 16f, B.TEXT2, bold = true))
                addView(label("Toque em ARQUIVO para criar ou importar.", 14f, B.TEXT2))
            } else {
                addView(label(cur.name, 16f, B.TEXT2, bold = true).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END })
                addView(label("Repetir reprodução:" + if (cur.loops == 0) "∞" else cur.loops.toString(), 15f, B.TEXT2, bold = true).apply { setPadding(0, dp(8), 0, 0) })
                scheduled(cur.id)?.let { addView(label("Agendada: $it", 13f, B.TEXT2)) }
                foreground = ripple(4, 0x1F00897B)
                setOnClickListener { menu(cur) }
            }
        })

        content.addView(bigButton(Ic.EDIT, "Editor de macro") { edit(cur?.id ?: "new") })
        content.addView(bigButton(Ic.FOLDER, "Arquivo") { fileDialog() })
        content.addView(bigButton(Ic.TAP, "Iniciar") { cur?.let { play(it.id) } ?: toast("Crie ou importe uma macro primeiro") })
    }

    /** Lista de macros (toque seleciona) + criar/importar. Substitui a grade: o base trabalha com uma macro atual. */
    private fun fileDialog() {
        val acts = buildList<Pair<String, () -> Unit>> {
            macros.forEach { m -> add((if (m.id == store.selected) "✓ " else "") + m.name to { store.selected = m.id; refresh() }) }
            add("Nova macro" to { edit("new") })
            add("Importar…" to { importFile() })
        }
        AlertDialog.Builder(this).setTitle("Arquivo").setItems(acts.map { it.first }.toTypedArray()) { _, w -> acts[w].second() }.show()
    }

    private fun play(id: String) { MacroService.instance?.runMacro(id) ?: toast("Ative o serviço de acessibilidade primeiro") }

    private fun edit(id: String) = startActivity(Intent(this, EditorActivity::class.java).putExtra("id", id))

    private fun menu(m: Macro) {
        val acts = buildList<Pair<String, () -> Unit>> {
            add("Executar agora" to { play(m.id) })
            add("Editar" to { edit(m.id) })
            add("Agendar…" to { schedule(m) })
            if (scheduled(m.id) != null) add("Cancelar agendamento" to { Alarms.cancel(this@MainActivity, m.id); store.setScheduleTime(m.id, null); refresh() })
            add("Exportar…" to { exportMacro(m) })
            add("Excluir" to { confirmDelete(m) })
        }
        AlertDialog.Builder(this).setTitle(m.name).setItems(acts.map { it.first }.toTypedArray()) { _, w -> acts[w].second() }.show()
    }

    private fun confirmDelete(m: Macro) {
        AlertDialog.Builder(this).setMessage("Excluir “${m.name}”?")
            .setPositiveButton("Excluir") { _, _ -> Alarms.cancel(this, m.id); store.delete(m.id); refresh() }
            .setNegativeButton("Cancelar", null).show()
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

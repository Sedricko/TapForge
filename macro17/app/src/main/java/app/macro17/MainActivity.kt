package app.macro17

import android.app.Activity
import android.app.AlertDialog
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
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply {
            addView(content)
            setOnApplyWindowInsetsListener { v, i -> v.setPadding(0, 0, 0, i.getInsets(WindowInsets.Type.navigationBars()).bottom); i }
        }
        drawer = mainDrawer(0)
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
            gravity = Gravity.CENTER
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            val ok = MacroService.instance != null
            addView(glyph(Ic.INFO, if (ok) 0xFF00574B.toInt() else B.RED, 24))
            addView(label(if (ok) "Permissão de Acessibilidade Concedida" else "A acessibilidade não está ativada.", 14f, if (ok) 0xFF00574B.toInt() else B.RED, bold = true).apply { setPadding(dp(8), 0, 0, 0) })
            if (!ok) setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })

        content.addView(card().apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            foreground = ripple(2, 0x1F000000)
            setOnClickListener { fileDialog() }
            if (cur == null) {
                addView(label("Por favor, selecione um arquivo.", 14f, 0xFF212121.toInt(), bold = true))
            } else {
                addView(label(cur.name, 14f, 0xFF212121.toInt(), bold = true).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END })
                addView(label("Repetir reprodução:" + if (cur.loops == 0) "Infinito" else cur.loops.toString(), 14f, 0xFF212121.toInt(), bold = true).apply { setPadding(0, dp(4), 0, 0) })
            }
        })

        content.addView(bigButton(Ic.EDIT, "Editor de macro") { edit(cur?.id ?: "new") })
        content.addView(bigButton(Ic.FOLDER, "Arquivo") { fileDialog() })
        if (store.debugOverlay) content.addView(bigButton(Ic.INFO, "Arquivo de Depuração") { startActivity(Intent(this, DiagActivity::class.java)) })
        // Como no app base: INICIAR abre os controles flutuantes para montar (gravar) e rodar; não exige macro pronta.
        content.addView(bigButton(Ic.TAP, if (MacroService.instance?.panelOpen() == true) "Parar" else "Iniciar") { start() })
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

    private fun start() {
        val svc = MacroService.instance
        if (svc == null) {
            toast("Ative o serviço de acessibilidade do Macro17 primeiro")
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }
        // Como no base: o botão alterna INICIAR/PARAR e o app continua na tela; o painel aparece por cima.
        if (svc.panelOpen()) svc.hideControls() else svc.showControls()
        content.postDelayed({ refresh() }, 150) // o painel abre/fecha na fila da thread principal; relê o estado depois
    }

    private fun play(id: String) { MacroService.instance?.runMacro(id) ?: toast("Ative o serviço de acessibilidade primeiro") }

    private fun edit(id: String) = startActivity(Intent(this, EditorActivity::class.java).putExtra("id", id))

    private fun menu(m: Macro) {
        val acts = buildList<Pair<String, () -> Unit>> {
            add("Executar agora" to { play(m.id) })
            add("Editar" to { edit(m.id) })
            add("Exportar…" to { exportMacro(m) })
            add("Excluir" to { confirmDelete(m) })
        }
        AlertDialog.Builder(this).setTitle(m.name).setItems(acts.map { it.first }.toTypedArray()) { _, w -> acts[w].second() }.show()
    }

    private fun confirmDelete(m: Macro) {
        AlertDialog.Builder(this).setMessage("Excluir “${m.name}”?")
            .setPositiveButton("Excluir") { _, _ -> store.delete(m.id); Alarms.rearm(this); refresh() }
            .setNegativeButton("Cancelar", null).show()
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

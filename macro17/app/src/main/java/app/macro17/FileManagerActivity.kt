package app.macro17

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.Menu
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowInsets
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.ScrollView
import android.widget.Toast
import org.json.JSONObject
import java.util.UUID

/** Tela "Arquivo" do base: lista de macros com rádio e menu ⋮ (Criar, Excluir, Copiar para, Copiar (Mesclar), Renomear, Editar, Exportar). */
class FileManagerActivity : Activity() {
    private lateinit var store: Store
    private lateinit var list: LinearLayout
    private var macros = emptyList<Macro>()
    private var marked: String? = null

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        store = Store(this)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(10), 0, 0) }
        val more = glyph(Ic.MORE, Color.WHITE, 40).apply {
            setPadding(dp(12), dp(12), dp(12), dp(12)); foreground = ripple(24, 0x33FFFFFF); contentDescription = "Mais opções"
            setOnClickListener { menu(this) }
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(56))
        }
        setContentView(FrameLayout(this).apply {
            setBackgroundColor(Color.WHITE)
            addView(LinearLayout(this@FileManagerActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(appBar("Arquivo", Ic.BACK, { finish() }, more))
                addView(ScrollView(this@FileManagerActivity).apply {
                    addView(list)
                    setOnApplyWindowInsetsListener { v, i -> v.setPadding(0, 0, 0, i.getInsets(WindowInsets.Type.navigationBars()).bottom); i }
                }, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
            }, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        })
    }

    override fun onResume() { super.onResume(); render() }

    private fun render() {
        macros = store.list()
        if (marked !in macros.map { it.id }) marked = null
        list.removeAllViews()
        macros.forEach { m ->
            list.addView(RadioButton(this).apply {
                text = m.name; textSize = 16f; setTextColor(B.TEXT); isChecked = m.id == marked
                layoutDirection = android.view.View.LAYOUT_DIRECTION_RTL // rádio à direita, como no base
                textAlignment = android.view.View.TEXT_ALIGNMENT_VIEW_END
                gravity = Gravity.CENTER_VERTICAL
                buttonTintList = android.content.res.ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(0xFF00574B.toInt(), 0x8A000000.toInt()))
                setPadding(dp(16), dp(12), dp(16), dp(12))
                setOnClickListener { marked = m.id; render() }
            }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            list.addView(android.view.View(this).apply { setBackgroundColor(0x1E000000) }, LinearLayout.LayoutParams(MATCH_PARENT, 1))
        }
    }

    private fun menu(anchor: android.view.View) {
        // Menu do base (menu_file_manager): 7 itens, sem "Importar" (importar é Copiar (Mesclar) no base).
        val acts = listOf<Pair<String, () -> Unit>>(
            "Criar" to { createDlg() }, "Excluir" to { needMark { deleteDlg(it) } }, "Copiar para" to { needMark { copyDlg(it) } },
            "Copiar (Mesclar)" to { needMark { mergeDlg(it) } }, "Renomear" to { needMark { renameDlg(it) } },
            "Editar" to { needMark { startActivity(Intent(this, EditorActivity::class.java).putExtra("id", it.id)) } },
            "Exportar" to { needMark { exportM(it) } },
        )
        val pm = android.widget.PopupMenu(this, anchor)
        acts.forEachIndexed { k, a -> pm.menu.add(0, k, k, a.first) }
        pm.setOnMenuItemClickListener { acts[it.itemId].second(); true }
        pm.show()
    }

    private fun needMark(go: (Macro) -> Unit) {
        val m = macros.firstOrNull { it.id == marked }
        if (m == null) AlertDialog.Builder(this).setMessage("Por favor, selecione um arquivo.").setPositiveButton("OK", null).show() else go(m)
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    private fun nameDialog(title: String, ok: String, initial: String, go: (String) -> Unit) {
        val et = EditText(this).apply { setText(initial); setSingleLine(); setSelection(initial.length) }
        AlertDialog.Builder(this).setTitle(title).setView(FrameLayout(this).apply { setPadding(dp(16), dp(5), dp(16), dp(5)); addView(et) })
            .setPositiveButton(ok) { _, _ ->
                val n = et.text.toString().trim()
                if (n.isEmpty()) return@setPositiveButton
                if (n.length > 60) return@setPositiveButton toast("Nome com mais de 60 caracteres")
                go(n)
            }.setNegativeButton("Cancelar", null).show()
    }

    private fun createDlg() = nameDialog("Criar Novo Arquivo", "Criar", "") { n ->
        if (store.nameTaken(n)) return@nameDialog toast("Arquivo com o mesmo nome já existe")
        val m = Macro(UUID.randomUUID().toString(), n, 1, emptyList())
        store.save(m); store.selected = m.id; marked = m.id; render()
    }

    private fun deleteDlg(m: Macro) = AlertDialog.Builder(this).setMessage("Deseja excluir?")
        .setPositiveButton("Excluir") { _, _ ->
            store.delete(m.id); Alarms.rearm(this); marked = null
            if (store.selected == null) store.selected = store.list().firstOrNull()?.id
            render()
        }.setNegativeButton("Cancelar", null).show()

    private fun copyDlg(m: Macro) = nameDialog("Novo nome (copiar arquivo)", "Criar", m.name) { n ->
        if (store.nameTaken(n)) return@nameDialog toast("Arquivo com o mesmo nome já existe")
        val c = m.copy(id = UUID.randomUUID().toString(), name = n)
        store.save(c); store.selected = c.id; marked = null; render()
    }

    private fun renameDlg(m: Macro) = nameDialog("Novo Nome (Renomear)", "Mudar", m.name) { n ->
        if (store.nameTaken(n, m.id)) return@nameDialog toast("Arquivo com o mesmo nome já existe")
        store.save(m.copy(name = n)); render()
    }

    /** Anexa os itens de [src] ao fim do arquivo escolhido, com os saltos deslocados; o escolhido vira o atual e abre no editor. */
    private fun mergeDlg(src: Macro) {
        val others = macros.filter { it.id != src.id }
        if (others.isEmpty()) return toast("Não há outro arquivo")
        var pick = 0
        AlertDialog.Builder(this).setTitle("Selecione o arquivo para copiar")
            .setSingleChoiceItems(others.map { it.name }.toTypedArray(), 0) { _, w -> pick = w }
            .setPositiveButton("OK") { _, _ ->
                val dst = others[pick]
                val off = dst.steps.size
                fun sh(v: Int) = if (v > 0) v + off else v
                val moved = src.steps.map { s -> s.copy(goto = sh(s.goto), fail = sh(s.fail), cases = s.cases.map { it.copy(to = sh(it.to)) }) }
                val merged = dst.copy(steps = dst.steps + moved)
                merged.validate()?.let { return@setPositiveButton toast("Não foi possível mesclar: $it") }
                store.save(merged); store.selected = merged.id
                startActivity(Intent(this, EditorActivity::class.java).putExtra("id", merged.id))
            }.setNegativeButton("Cancelar", null).show()
    }

    private var exportSamples = false

    private fun exportM(m: Macro) = AlertDialog.Builder(this).setTitle("Backup da Captura de Tela")
        .setMessage("Você quer incluir os dados da captura de tela no backup? As capturas de tela podem conter informações pessoais sensíveis.")
        .setPositiveButton("Sim") { _, _ -> exportSamples = true; exportPick(m) }
        .setNegativeButton("Não") { _, _ -> exportSamples = false; exportPick(m) }.show()

    private fun exportPick(m: Macro) {
        store.pendingExport = m.id
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/zip").putExtra(Intent.EXTRA_TITLE, "${m.name}.zip"), EXPORT)
    }

    private fun importJson() =
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), IMPORT)

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        val uri = data?.data ?: return
        if (res != RESULT_OK) return
        try {
            when (req) {
                EXPORT -> {
                    val m = store.pendingExport?.let { store.get(it) } ?: throw IllegalStateException("macro não encontrada")
                    // ZIP no formato do Backup (macro + imagens citadas): a tela Backup e Restauração lê de volta com as imagens.
                    contentResolver.openOutputStream(uri, "wt")!!.use { BackupZip.write(it, filesDir, setOf(m.id), samples = exportSamples) }
                    toast("Exportada com as imagens")
                }
                IMPORT -> {
                    val raw = contentResolver.openInputStream(uri)!!.use { Store.readLimited(it) }
                    val m = Macro.fromJson(JSONObject(String(raw)), UUID.randomUUID().toString())
                    m.validate()?.let { throw IllegalArgumentException(it) }
                    store.save(m); render(); toast("Importada: ${m.name}")
                }
            }
        } catch (e: Exception) {
            toast("Falhou: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private companion object { const val EXPORT = 1; const val IMPORT = 2 }
}

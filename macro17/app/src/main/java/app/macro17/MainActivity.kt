package app.macro17

import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.ArrayAdapter
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
    private lateinit var status: TextView
    private lateinit var list: ListView
    private var macros = emptyList<Macro>()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        store = Store(this)
        status = TextView(this).apply { textSize = 16f; setPadding(0, 0, 0, dp(8)) }
        list = ListView(this).apply {
            emptyView = TextView(this@MainActivity).apply { text = "Nenhuma macro. Use “Nova” ou grave pela bolha."; visibility = android.view.View.GONE }
            setOnItemClickListener { _, _, i, _ -> menu(macros[i]) }
        }
        val row = LinearLayout(this).apply {
            addView(button("Acessibilidade") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) })
            addView(button("Nova") { edit("new") })
            addView(button("Importar") { importFile() })
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(status); addView(row)
            addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(list.emptyView)
            padForSystemBars(dp(16))
        })
    }

    override fun onResume() { super.onResume(); refresh() }

    private fun refresh() {
        val on = MacroService.instance != null
        status.text = if (on) "Serviço ativo: bolha na tela." else
            "Serviço desligado. Ative “Macro17” em Acessibilidade. Se estiver cinza (app instalado fora da loja), abra Informações do app > ⋮ > Permitir configurações restritas."
        macros = store.list()
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, macros.map {
            (if (it.id == store.selected) "★ " else "") + "${it.name}  (${it.steps.size} passos, ${if (it.loops == 0) "∞" else "${it.loops}×"})"
        })
    }

    private fun edit(id: String) = startActivity(Intent(this, EditorActivity::class.java).putExtra("id", id))

    private fun menu(m: Macro) {
        val items = arrayOf("Selecionar para a bolha", "Executar agora", "Editar", "Agendar…", "Exportar…", "Excluir")
        AlertDialog.Builder(this).setTitle(m.name).setItems(items) { _, w ->
            when (w) {
                0 -> { store.selected = m.id; refresh() }
                1 -> MacroService.instance?.runMacro(m.id) ?: toast("Ative o serviço de acessibilidade primeiro")
                2 -> edit(m.id)
                3 -> schedule(m)
                4 -> { store.pendingExport = m.id; startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json").putExtra(Intent.EXTRA_TITLE, "${m.name}.json"), EXPORT) }
                5 -> AlertDialog.Builder(this).setMessage("Excluir “${m.name}”?")
                    .setPositiveButton("Excluir") { _, _ -> store.delete(m.id); refresh() }.setNegativeButton("Cancelar", null).show()
            }
        }.show()
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

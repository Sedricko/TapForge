package app.macro17

import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.BaseAdapter
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
    private lateinit var status: TextView
    private lateinit var dot: View
    private lateinit var list: ListView
    private var macros = emptyList<Macro>()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        store = Store(this)

        dot = View(this).apply { layoutParams = LinearLayout.LayoutParams(dp(12), dp(12)).also { it.setMargins(0, 0, dp(10), 0) } }
        status = label("", 14f, bold = true).apply { layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f) }
        val banner = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = rounded(Color.WHITE, 4, C.LINE)
            elevation = dp(2).toFloat()
            addView(dot); addView(status)
        }
        val actions = LinearLayout(this).apply {
            setPadding(0, dp(10), 0, dp(6))
            addView(outlineBtn("Acessibilidade") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) })
            addView(outlineBtn("Importar") { importFile() })
        }
        val empty = label("Nenhuma macro. Toque em + ou grave pela bolha.", 14f, C.GRAY).apply { setPadding(dp(8), dp(24), dp(8), 0) }
        list = ListView(this).apply {
            divider = ColorDrawable(C.LINE); dividerHeight = 1
            clipToPadding = false; setPadding(0, 0, 0, dp(88)) // vão para o FAB não cobrir a última linha
            emptyView = empty
            setOnItemClickListener { _, _, i, _ -> menu(macros[i]) }
            adapter = object : BaseAdapter() {
                override fun getCount() = macros.size
                override fun getItem(i: Int) = macros[i]
                override fun getItemId(i: Int) = i.toLong()
                override fun getView(i: Int, v: View?, p: ViewGroup) = row(macros[i])
            }
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(banner, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            addView(actions)
            addView(list, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
            addView(empty)
            padForSystemBars(dp(12), top = false)
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(appBar("Macro17", back = false))
            addView(content, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        }
        setContentView(FrameLayout(this).apply {
            setBackgroundColor(C.BG)
            addView(column, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            addView(fab("Nova macro") { edit("new") })
        })
    }

    override fun onResume() { super.onResume(); refresh() }

    private fun row(m: Macro): View = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(4), dp(12), dp(8), dp(12))
        addView(label(if (m.id == store.selected) "★" else "", 18f, C.PRIMARY).apply {
            gravity = Gravity.CENTER; layoutParams = LinearLayout.LayoutParams(dp(32), WRAP_CONTENT)
        })
        addView(LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            addView(label(m.name, 16f))
            addView(label("${m.steps.size} passos · " + if (m.loops == 0) "repete sem parar" else "${m.loops}×", 13f, C.GRAY))
        })
    }

    private fun refresh() {
        val on = MacroService.instance != null
        dot.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(if (on) C.PRIMARY else C.RED) }
        status.text = if (on) "Serviço de acessibilidade ativo. A bolha está na tela." else
            "Serviço desligado. Ative “Macro17” em Acessibilidade. Se estiver cinza (app instalado fora da loja), abra Informações do app > ⋮ > Permitir configurações restritas."
        macros = store.list()
        (list.adapter as BaseAdapter).notifyDataSetChanged()
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

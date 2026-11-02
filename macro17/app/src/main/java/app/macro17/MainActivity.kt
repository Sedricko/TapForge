package app.macro17

import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
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
    private lateinit var body: LinearLayout
    private var macros = emptyList<Macro>()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        store = Store(this)
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(16)) }
        val scroll = ScrollView(this).apply {
            clipToPadding = false
            addView(body)
            setOnApplyWindowInsetsListener { v, i ->
                val s = i.getInsets(WindowInsets.Type.systemBars())
                v.setPadding(s.left, 0, s.right, dp(96) + s.bottom) // vão para o botão flutuante não cobrir o último card
                i
            }
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(appBar("Macro17", back = false))
            addView(scroll, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        }
        setContentView(FrameLayout(this).apply {
            setBackgroundColor(C.BG)
            addView(column, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            addView(fab("Nova macro") { edit("new") })
        })
    }

    override fun onResume() { super.onResume(); refresh() }

    // Poucas dezenas de macros: reconstruir a coluna é mais simples e mais robusto que um adapter com cabeçalhos.
    private fun refresh() {
        macros = store.list()
        body.removeAllViews()
        body.addView(statusCard())
        body.addView(LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(20), 0, 0)
            addView(label("MINHAS MACROS · ${macros.size}", 12f, C.GRAY, bold = true).apply {
                letterSpacing = 0.08f
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            })
            addView(textAction("Importar") { importFile() })
        })
        if (macros.isEmpty()) body.addView(emptyState()) else macros.forEach { body.addView(macroCard(it)) }
    }

    private fun statusCard(): View {
        val on = MacroService.instance != null
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = card()
            elevation = dp(1).toFloat()
            setPadding(dp(16), dp(16), dp(12), dp(8))
            addView(LinearLayout(this@MainActivity).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(badge(if (on) Ic.OK else Ic.WARN, if (on) C.TINT else C.TINT_RED, if (on) C.PRIMARY else C.RED, 40))
                addView(LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(14), 0, dp(4), 0)
                    layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
                    addView(label(if (on) "Serviço ativo" else "Serviço desligado", 16f, bold = true))
                    addView(label(
                        if (on) "A bolha está na tela. Selecione uma macro para ela executar."
                        else "Ative “Macro17” em Acessibilidade. Se a opção estiver cinza (app instalado fora da loja): Informações do app > ⋮ > Permitir configurações restritas.",
                        13f, C.GRAY,
                    ))
                })
            })
            addView(textAction(if (on) "Ajustes de acessibilidade" else "Ativar agora") {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }.apply { layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).also { it.gravity = Gravity.END } })
        }
    }

    private fun macroCard(m: Macro): View = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(14), dp(12), dp(14), dp(12))
        background = card()
        foreground = ripple(12)
        elevation = dp(1).toFloat()
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).also { it.topMargin = dp(8) }
        addView(badge(Ic.PLAY, C.TINT, C.PRIMARY, 40))
        addView(LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, dp(8), 0)
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            addView(label(m.name, 16f, bold = true))
            addView(label("${m.steps.size} passos · " + when (m.loops) { 0 -> "repete sem parar"; 1 -> "executa 1 vez"; else -> "repete ${m.loops}×" }, 13f, C.GRAY))
        })
        if (m.id == store.selected) addView(chip("NA BOLHA"))
        setOnClickListener { menu(m) }
    }

    private fun emptyState(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(24), dp(48), dp(24), 0)
        addView(badge(Ic.REC, C.TINT, C.PRIMARY, 72).apply { layoutParams = LinearLayout.LayoutParams(dp(72), dp(72)) })
        addView(label("Nenhuma macro ainda", 18f, bold = true).apply { setPadding(0, dp(16), 0, dp(4)) })
        addView(label("Toque em Nova macro ou grave pela bolha flutuante.", 14f, C.GRAY).apply { gravity = Gravity.CENTER })
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

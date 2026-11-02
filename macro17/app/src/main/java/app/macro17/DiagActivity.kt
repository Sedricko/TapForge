package app.macro17

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/**
 * Registro de eventos do app e relatório para quem corrige: salvar em arquivo (SAF), copiar ou compartilhar.
 * O relatório completo é montado numa thread própria porque lê arquivo e roda `logcat`.
 */
@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
class DiagActivity : Activity() {
    private lateinit var store: Store
    private lateinit var view: TextView
    private lateinit var scroll: ScrollView
    @Volatile private var pending: String? = null

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        store = Store(this)
        view = TextView(this).apply { typeface = Typeface.MONOSPACE; textSize = 10.5f; setTextColor(C.TEXT2); setTextIsSelectable(true) }
        scroll = ScrollView(this).apply { addView(view) }

        val bar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(circleBtn(Ic.BACK, Color.TRANSPARENT, 40, "Voltar") { finish() })
            addView(label("Diagnóstico e logs", 16f, bold = true).apply { setPadding(dp(4), 0, 0, 0) })
        }
        val actions = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(LinearLayout(this@DiagActivity).apply {
                setPadding(0, dp(8), 0, dp(8))
                addView(pillBtn("Painel ao vivo", C.BLUE) { MacroService.instance?.toggleLog() ?: Toast.makeText(this@DiagActivity, "Ative o serviço de acessibilidade primeiro", Toast.LENGTH_LONG).show() })
                addView(pillBtn("Atualizar", C.DARK) { refresh() })
                addView(pillBtn("Salvar relatório", C.TEAL) { saveReport() })
                addView(pillBtn("Compartilhar", C.PURPLE) { shareReport() })
                addView(pillBtn("Copiar", C.ORANGE) { copyReport() })
                addView(pillBtn("Limpar", C.RED) { Diag.clear(); refresh() })
            })
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(bar)
            addView(toggle("Registrar eventos", "Passos, gestos, capturas, serviço, telas, alarmes e falhas fatais ficam num arquivo local (rotação de ~2 MB).", store.diag) { store.diag = it })
            addView(toggle("Incluir log do sistema (root)", "Ao gerar o relatório, roda “su” para juntar logcat completo, dmesg e estado da acessibilidade. O gerenciador de root pode pedir confirmação.", store.diagRoot) { store.diagRoot = it })
            addView(label("Sem root: adb shell pm grant app.macro17 android.permission.READ_LOGS libera o logcat do sistema.", 11f, C.TEXT2).apply { setPadding(dp(4), dp(8), dp(4), 0) })
            addView(actions)
            addView(scroll, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
            padForSystemBars(dp(12))
        })
    }

    override fun onResume() { super.onResume(); refresh() }

    private fun refresh() {
        view.text = Diag.tail(40_000).ifEmpty { "Nenhum evento registrado ainda." }
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun toggle(title: String, desc: String, value: Boolean, change: (Boolean) -> Unit): View = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        background = tileBg()
        setPadding(dp(14), dp(10), dp(10), dp(10))
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).also { it.topMargin = dp(8) }
        addView(LinearLayout(this@DiagActivity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            addView(label(title, 15f, bold = true))
            addView(label(desc, 12f, C.TEXT2))
        })
        addView(Switch(this@DiagActivity).apply {
            isChecked = value; contentDescription = title
            setOnCheckedChangeListener { _, on -> change(on) }
        })
    }

    /** Monta o relatório fora da thread principal e entrega o texto a [then] na principal. */
    private fun build(then: (String) -> Unit) {
        Toast.makeText(this, "Gerando relatório…", Toast.LENGTH_SHORT).show()
        Thread({
            val r = try { Diag.report(this) } catch (t: Throwable) { "falha ao gerar o relatório: ${t.javaClass.simpleName}: ${t.message}" }
            runOnUiThread { if (!isDestroyed) then(r) }
        }, "macro17-report").start()
    }

    private fun saveReport() = build { r ->
        pending = r
        startActivityForResult(
            Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("text/plain")
                .putExtra(Intent.EXTRA_TITLE, "macro17-relatorio-${System.currentTimeMillis() / 1000}.txt"), SAVE,
        )
    }

    // EXTRA_TEXT/área de transferência passam pelo Binder (~1 MB): manda só o final, que é o mais recente.
    private fun shareReport() = build { r ->
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, "Relatório Macro17").putExtra(Intent.EXTRA_TEXT, r.takeLast(150_000)), "Enviar relatório"))
    }

    private fun copyReport() = build { r ->
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Relatório Macro17", r.takeLast(150_000)))
        Toast.makeText(this, "Copiado (últimos 150 mil caracteres)", Toast.LENGTH_SHORT).show()
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        val uri: Uri = data?.data ?: return
        val text = pending ?: return
        if (req != SAVE || res != RESULT_OK) return
        try {
            contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) }
            Toast.makeText(this, "Relatório salvo", Toast.LENGTH_LONG).show()
        } catch (e: Exception) { Toast.makeText(this, "Falhou: ${e.message ?: e.javaClass.simpleName}", Toast.LENGTH_LONG).show() }
        pending = null
    }

    private companion object { const val SAVE = 7 }
}

package app.macro17

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast

/** Engrenagem do painel: janela de 4 linhas (Arquivo de Macro, Repetir reprodução, Tamanho da imagem a salvar, Monitoramento contínuo), como no base. */
class PanelSettingsActivity : Activity() {
    private lateinit var store: Store
    private lateinit var col: LinearLayout

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        store = Store(this)
        col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(8), dp(8), dp(8)) }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(B.BG)
            addView(appBar("Configurações", Ic.BACK, { finish() })) // overlay_settings_activity do base: seta de voltar + título
            addView(col)
        })
        render()
    }

    // Aberta pelo painel (fora do app): não pode ficar por cima de outras telas depois de fechada.
    override fun onStop() { super.onStop(); if (!isChangingConfigurations) finish() }

    private fun row(title: String, value: String, enabled: Boolean = true, click: (() -> Unit)? = null) {
        col.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(10), dp(10), dp(10), dp(10)); alpha = if (enabled) 1f else 0.45f
            addView(label(title, 18f, Color.BLACK))
            addView(label(value, 14f, B.TEXT2))
            if (enabled && click != null) { foreground = ripple(0, 0x1F000000); setOnClickListener { click() } }
        }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).also { it.topMargin = dp(10) })
    }

    private fun render() {
        col.removeAllViews()
        val m = store.selected?.let { store.get(it) }
        row("Arquivo de Macro", m?.name ?: "Por favor, selecione um arquivo.") {
            val ms = store.list()
            if (ms.isEmpty()) Toast.makeText(this, "Nenhum arquivo de macro", Toast.LENGTH_SHORT).show()
            else AlertDialog.Builder(this).setTitle("Arquivos de Macro").setItems(ms.map { it.name }.toTypedArray()) { _, w -> store.selected = ms[w].id; render() }.setNegativeButton("Cancelar", null).show()
        }
        row("Repetir reprodução", if (m == null) "—" else if (m.loops == 0) "Infinito" else "${m.loops}", m != null) {
            val et = EditText(this).apply { inputType = InputType.TYPE_CLASS_NUMBER; setText(m!!.loops.toString()); setPadding(dp(24), dp(16), dp(24), dp(8)) }
            AlertDialog.Builder(this).setTitle("Repetir reprodução").setMessage("0 = infinito").setView(et)
                .setPositiveButton("OK") { _, _ ->
                    val n = et.text.toString().toIntOrNull()?.takeIf { it in 0..MAX_LOOPS }
                    if (n == null) Toast.makeText(this, "Número inválido", Toast.LENGTH_SHORT).show() else { store.save(m!!.copy(loops = n)); render() }
                }.setNegativeButton("Cancelar", null).show()
        }
        val sizes = intArrayOf(5, 10, 20, 40, 80, 120, 160)
        row("Tamanho da imagem a salvar", "${store.thumbPx} Pixel") {
            AlertDialog.Builder(this).setTitle("Tamanho da imagem a salvar").setSingleChoiceItems(sizes.map { "$it Pixel" }.toTypedArray(), sizes.indexOf(store.thumbPx)) { d, w -> store.thumbPx = sizes[w]; d.dismiss(); render() }
                .setNegativeButton("Cancelar", null).show()
        }
        row("Monitoramento contínuo da tela", "Mantém a captura de tela em ociosidade para reduzir o atraso de inicialização. (Nenhum dado transmitido externamente)", enabled = false)
    }
}

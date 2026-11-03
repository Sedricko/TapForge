package app.macro17

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
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

/**
 * Permissões, como no base: aviso no topo e três cartões (Acessibilidade, ADB, Root). Só a acessibilidade existe no Macro17;
 * ADB e Root abrem uma explicação em vez de fingir que funcionam.
 */
class PermissionsActivity : Activity() {
    private lateinit var content: LinearLayout
    private lateinit var drawer: Drawer

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, 0) }
        drawer = mainDrawer(1)
        val scroll = ScrollView(this).apply {
            addView(content)
            setOnApplyWindowInsetsListener { v, i -> v.setPadding(0, 0, 0, i.getInsets(WindowInsets.Type.navigationBars()).bottom); i }
        }
        setContentView(FrameLayout(this).apply {
            setBackgroundColor(B.BG)
            addView(LinearLayout(this@PermissionsActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(appBar("Permissões", Ic.MENU, { drawer.open() }))
                addView(scroll, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
            }, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            drawer.attach(this)
        })
    }

    override fun onResume() { super.onResume(); refresh() }

    override fun onBackPressed() { if (drawer.isOpen) drawer.close() else super.onBackPressed() }

    private fun option(title: String, body: String, click: () -> Unit): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        background = rounded(Color.WHITE, 2); elevation = dp(2).toFloat()
        foreground = ripple(4, 0x1F00897B)
        setPadding(dp(16), dp(15), dp(16), dp(15))
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).also { it.setMargins(dp(5), dp(4), dp(5), dp(4)) }
        addView(label(title, 14f, 0xFF212121.toInt(), bold = true).apply { setPadding(0, 0, 0, dp(10)) })
        addView(label(body, 14f, 0xFF212121.toInt()).apply { gravity = Gravity.CENTER; setPadding(0, dp(10), 0, 0) })
        setOnClickListener { click() }
    }

    private fun refresh() {
        content.removeAllViews()
        val granted = MacroService.instance != null
        content.addView(card().apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            setPadding(dp(12), dp(18), dp(12), dp(18))
            addView(glyph(Ic.INFO, B.RED, 24))
            addView(label("Por favor, selecione uma permissão.", 14f, B.RED, bold = true).apply { setPadding(dp(10), 0, 0, 0) })
        })
        content.addView(option("Acessibilidade", if (granted) "A permissão de acessibilidade está concedida." else "A permissão de acessibilidade deve ser concedida.") {
            // Como no base: escolhe o modo de controle (só existe o de acessibilidade) e volta à Home; sem a permissão, abre o Acordo.
            if (granted) finish() else startActivity(Intent(this, AccessibilityAgreementActivity::class.java))
        })
        content.addView(option("Android Debug Bridge (ADB)", "Precisa ser configurado usando um computador.") {
            info("ADB", "O modo ADB injeta toques com privilégio de shell. O Macro17 não o usa: os gestos saem pelo serviço de acessibilidade.")
        })
        content.addView(option("Root", "Disponível em smartphones com root.") {
            info("Root", "O modo root não é usado para os toques. Com root, a tela Monitor e logs pode juntar o log completo do sistema no relatório (Opções do monitor).")
        })
    }

    private fun info(title: String, text: String) { AlertDialog.Builder(this).setTitle(title).setMessage(text).setPositiveButton("OK", null).show() }
}

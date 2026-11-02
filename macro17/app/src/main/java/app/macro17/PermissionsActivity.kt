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
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(4), dp(4), dp(4), dp(4)) }
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
        background = rounded(Color.WHITE, 4); elevation = dp(2).toFloat()
        foreground = ripple(4, 0x1F00897B)
        setPadding(dp(16), dp(18), dp(16), dp(18))
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).also { it.setMargins(dp(4), dp(4), dp(4), dp(4)) }
        addView(label(title, 15f, B.TEXT2, bold = true))
        addView(label(body, 15f, B.TEXT).apply { gravity = Gravity.CENTER; setPadding(0, dp(10), 0, 0) })
        setOnClickListener { click() }
    }

    private fun refresh() {
        content.removeAllViews()
        val granted = MacroService.instance != null
        content.addView(card().apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            addView(glyph(Ic.INFO, if (granted) B.PRIMARY else B.RED, 24))
            addView(label(if (granted) "Permissão de Acessibilidade Concedida" else "Por favor, selecione uma permissão.", 15f, B.TEXT2, bold = true).apply { setPadding(dp(10), 0, 0, 0) })
        })
        content.addView(option("Acessibilidade", if (granted) "A permissão de acessibilidade está concedida." else "A permissão de acessibilidade deve ser concedida.") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
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

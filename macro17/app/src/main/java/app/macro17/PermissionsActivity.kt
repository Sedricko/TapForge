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
@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
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
        addView(label(body, 14f, 0xFF212121.toInt()).apply { gravity = Gravity.CENTER })
        setOnClickListener { click() }
    }

    private fun refresh() {
        content.removeAllViews()
        content.addView(card().apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            setPadding(dp(12), dp(18), dp(12), dp(18))
            addView(glyph(Ic.INFO, 0xFFF44336.toInt(), 25))
            addView(label("Por favor, selecione uma permissão.", 14f, 0xDE000000.toInt(), bold = true).apply { setPadding(dp(6), dp(6), dp(6), dp(6)); minWidth = dp(225); gravity = Gravity.CENTER })
        })
        content.addView(option("Acessibilidade", "A permissão de acessibilidade deve ser concedida.") {
            // Como no base: escolhe o modo de controle (só existe o de acessibilidade) e volta à Home; o Acordo abre pelo cartão da Home.
            Store(this).controlMode = MODE_ACCESS
            finish()
        })
        content.addView(option("Android Debug Bridge (ADB)", "Precisa ser configurado usando um computador.") { startActivityForResult(Intent(this, AdbSetupActivity::class.java), 1) })
        content.addView(option("Root", "Disponível em smartphones com root.") { rootDialog() })
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) { super.onActivityResult(req, res, data); if (res == RESULT_OK) finish() } // ADB configurado: volta à Home

    private fun rootDialog() = AlertDialog.Builder(this).setTitle("Root")
        .setMessage("Os toques e a captura passam a sair por `su`. O aparelho precisa de root; o gerenciador de root vai pedir permissão ao testar.")
        .setPositiveButton("Testar e usar") { _, _ -> testShell(MODE_ROOT, "Root") }.setNegativeButton("Cancelar", null).show()

    /** Escolhe o modo e testa o shell fora da thread principal; se falhar, volta para a acessibilidade. */
    private fun testShell(mode: Int, name: String) {
        val st = Store(this); val before = st.controlMode; st.controlMode = mode
        android.widget.Toast.makeText(this, "Testando $name…", android.widget.Toast.LENGTH_SHORT).show()
        Thread({
            val msg = try {
                val out = String(st.shell()!!.run(if (mode == MODE_ADB) "echo macro17" else "id")).trim()
                Diag.d("controle", "$name ok: ${out.take(60)}")
                "$name pronto."
            } catch (e: Exception) { st.controlMode = if (before == mode) MODE_ACCESS else before; Diag.e("controle", "$name falhou", e); (if (mode == MODE_ROOT) "Falha ao obter permissão de root." else "$name indisponível: ${e.message ?: e.javaClass.simpleName}") }
            runOnUiThread { android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_LONG).show(); if (st.controlMode == mode) finish() else refresh() }
        }, "macro17-shell-test").start()
    }
}

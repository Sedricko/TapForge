package app.macro17

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.ImageView
import android.widget.LinearLayout

/** Sobre o Aplicativo no formato do base: logo, nome e versão a 24sp e linhas de 60dp com divisor e seta. */
class AboutActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val pi = packageManager.getPackageInfo(packageName, 0)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            addView(ImageView(this@AboutActivity).apply { setImageDrawable(packageManager.getApplicationIcon(packageName)) },
                LinearLayout.LayoutParams(dp(96), dp(96)).also { it.topMargin = dp(30) })
            addView(label("Macro17", 24f, Color.BLACK, bold = true), LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).also { it.topMargin = dp(40) })
            addView(label(pi.versionName ?: "", 24f, Color.BLACK), LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).also { it.topMargin = dp(5); it.bottomMargin = dp(20) })
        }
        fun row(text: String, body: String) {
            col.addView(View(this).apply { setBackgroundColor(0x1E000000) }, LinearLayout.LayoutParams(MATCH_PARENT, 1))
            col.addView(LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                foreground = ripple(0, 0x1F000000)
                addView(label(text, 16f, Color.BLACK).apply { setPadding(dp(10), 0, 0, 0) }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                addView(glyph(Ic.CHEVRON, 0x1E000000, 24).apply { (this as ImageView).setColorFilter(0x66000000) }, LinearLayout.LayoutParams(dp(24), dp(24)).also { it.marginEnd = dp(10) })
                setOnClickListener { AlertDialog.Builder(this@AboutActivity).setTitle(text).setMessage(body).setPositiveButton("OK", null).show() }
            }, LinearLayout.LayoutParams(MATCH_PARENT, dp(60)))
        }
        row("O que é", "Automação de toques por acessibilidade: gravar, editar e repetir macros, com busca por imagem, texto e número na tela.\n\nSem internet: o app não tem a permissão INTERNET. Nada sai do aparelho a menos que você compartilhe um relatório.")
        row("Licenças de terceiros", "OpenCV 4.9.0 (Apache-2.0): casamento de imagem.\nML Kit Text Recognition 16.0.1 (termos do Google ML Kit): reconhecimento de texto no aparelho.\nKotlin stdlib 2.1.0 (Apache-2.0).\norg.json (JSON License).\nÍcones Material (Apache-2.0).")
        col.addView(View(this).apply { setBackgroundColor(0x1E000000) }, LinearLayout.LayoutParams(MATCH_PARENT, 1))
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            addView(appBar("Sobre o Aplicativo", Ic.BACK, { finish() }))
            addView(android.widget.ScrollView(this@AboutActivity).apply {
                addView(col)
                setOnApplyWindowInsetsListener { v, i -> v.setPadding(0, 0, 0, i.getInsets(android.view.WindowInsets.Type.navigationBars()).bottom); i }
            }, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        })
    }
}

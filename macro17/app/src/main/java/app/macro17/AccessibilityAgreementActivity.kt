package app.macro17

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.Toast

/** Divulgação de Acessibilidade do base: texto, "Concordo..." e Ir para Configurações / Cancelar (janela de diálogo). */
class AccessibilityAgreementActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setFinishOnTouchOutside(false)
        val agree = CheckBox(this).apply { text = "Concordo com o uso da Acessibilidade."; textSize = 16f; setTextColor(Color.BLACK) }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            addView(label("O Macro17 é um programa de automação de toque. Este aplicativo requer permissão de Acessibilidade para realizar toques e gestos.", 16f, Color.BLACK),
                LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            addView(agree, LinearLayout.LayoutParams(MATCH_PARENT, dp(48)).also { it.setMargins(dp(6), dp(12), dp(6), dp(6)) })
            addView(Button(this@AccessibilityAgreementActivity).apply {
                text = "Ir para Configurações"
                setOnClickListener {
                    if (!agree.isChecked) Toast.makeText(context, "Você deve concordar com o uso da Acessibilidade.", Toast.LENGTH_LONG).show()
                    else { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); finish() }
                }
            }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).also { it.topMargin = dp(20) })
            addView(Button(this@AccessibilityAgreementActivity).apply { text = "Cancelar"; setOnClickListener { finish() } }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        })
    }
}

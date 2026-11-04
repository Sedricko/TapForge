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
        val agree = CheckBox(this).apply { text = "Eu concordo com o uso da Acessibilidade."; textSize = 16f; setTextColor(Color.BLACK) }
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
            // Apps instalados por APK ficam acinzentados em Acessibilidade (configurações restritas do Android): o ⋮ das informações do app libera.
            addView(label("Se o Macro17 estiver acinzentado em Acessibilidade: Informações do app › ⋮ › Permitir configurações restritas.", 13f, 0xFF616161.toInt()).apply { setPadding(0, dp(12), 0, 0) })
            addView(Button(this@AccessibilityAgreementActivity).apply {
                text = "Abrir informações do app"
                setOnClickListener { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:$packageName"))) }
            }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        })
    }
}

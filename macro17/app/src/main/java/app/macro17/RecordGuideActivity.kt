package app.macro17

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout

/** "Guia para gravar locais de toque.": o que cada botão do painel de gravação faz (diálogo do base, dialog_record_instruction). */
class RecordGuideActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setFinishOnTouchOutside(false)
        title = "Guia para gravar locais de toque."
        fun row(res: Int, text: String) = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(ImageView(this@RecordGuideActivity).apply { setImageResource(res); setColorFilter(0xFF00574B.toInt()) }, LinearLayout.LayoutParams(dp(28), dp(28)).also { it.marginEnd = dp(8) })
            addView(label(text, 15f, Color.BLACK))
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).also { it.bottomMargin = dp(8) }
        }
        val again = CheckBox(this).apply { text = "Não mostrar novamente"; setTextColor(0xFF00574B.toInt()) }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(16), dp(24), dp(8))
            addView(label("Guia para gravar locais de toque.", 18f, Color.BLACK, bold = true).apply { setPadding(0, 0, 0, dp(12)) })
            addView(row(R.drawable.bp_ic_target, "Mova o ícone para o local que deseja tocar."))
            addView(row(R.drawable.bp_playlist_add, "Guarda a localização selecionada."))
            addView(row(R.drawable.bp_ic_tap, "Muda o método de toque."))
            addView(again)
            addView(label("OK", 15f, 0xFFD81B60.toInt(), bold = true).apply {
                gravity = Gravity.CENTER; setPadding(dp(16), dp(12), dp(16), dp(12))
                setOnClickListener {
                    if (again.isChecked) Store(this@RecordGuideActivity).recGuide = false
                    finish(); MacroService.instance?.beginRecording()
                }
            }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).also { it.gravity = Gravity.END })
        })
    }
}

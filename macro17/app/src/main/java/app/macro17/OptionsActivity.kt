package app.macro17

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Switch

/** Opções gravadas em SharedPreferences (Store). O serviço lê a cada execução; a opacidade ele aplica na hora. */
class OptionsActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val store = Store(this)

        val bar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(circleBtn(Ic.BACK, Color.TRANSPARENT, 40, "Voltar") { finish() })
            addView(label("Opções", 16f, bold = true).apply { setPadding(dp(4), 0, 0, 0) })
        }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glass()
            elevation = dp(8).toFloat()
            setPadding(dp(16), dp(12), dp(16), dp(16))
            addView(bar)
            addView(toggle("Manter a tela ligada", "Durante a execução de uma macro.", store.keepOn) { store.keepOn = it })
            addView(toggle("Parar ao bloquear a tela", "Encerra a macro quando a tela apaga.", store.stopOnLock) { store.stopOnLock = it })
            addView(opacityTile(store))
            addView(LinearLayout(this@OptionsActivity).apply {
                gravity = Gravity.CENTER_VERTICAL
                background = tileBg()
                foreground = ripple(16)
                setPadding(dp(14), dp(12), dp(10), dp(12))
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).also { it.topMargin = dp(8) }
                addView(LinearLayout(this@OptionsActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
                    addView(label("Monitor e logs", 15f, bold = true))
                    addView(label("Registro de eventos, relatório do sistema e exportação para enviar a quem corrige.", 12f, C.TEXT2))
                })
                addView(glyph(Ic.CHEVRON, C.TEXT, 22))
                setOnClickListener { startActivity(Intent(this@OptionsActivity, DiagActivity::class.java)) }
            })
            addView(LinearLayout(this@OptionsActivity).apply {
                gravity = Gravity.CENTER_VERTICAL
                background = tileBg()
                foreground = ripple(16)
                setPadding(dp(14), dp(12), dp(10), dp(12))
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).also { it.topMargin = dp(8) }
                addView(LinearLayout(this@OptionsActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
                    addView(label("Leitor de números", 15f, bold = true))
                    addView(label("Ensine os dígitos de um jogo ou contador para o passo “Ler número”.", 12f, C.TEXT2))
                })
                addView(glyph(Ic.CHEVRON, C.TEXT, 22))
                setOnClickListener { startActivity(Intent(this@OptionsActivity, ReaderActivity::class.java)) }
            })
        }
        setContentView(LinearLayout(this).apply {
            addView(panel, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            padForSystemBars(dp(12))
        })
    }

    private fun toggle(title: String, desc: String, value: Boolean, change: (Boolean) -> Unit): View = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        background = tileBg()
        setPadding(dp(14), dp(12), dp(10), dp(12))
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).also { it.topMargin = dp(8) }
        addView(LinearLayout(this@OptionsActivity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            addView(label(title, 15f, bold = true))
            addView(label(desc, 12f, C.TEXT2))
        })
        addView(Switch(this@OptionsActivity).apply {
            isChecked = value
            contentDescription = title
            setOnCheckedChangeListener { _, on -> change(on) }
        })
    }

    private fun opacityTile(store: Store): View {
        val value = label("${store.opacity}%", 13f, C.TEXT2)
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = tileBg()
            setPadding(dp(14), dp(12), dp(14), dp(8))
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).also { it.topMargin = dp(8) }
            addView(LinearLayout(this@OptionsActivity).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(label("Opacidade da bolha", 15f, bold = true).apply { layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f) })
                addView(value)
            })
            addView(SeekBar(this@OptionsActivity).apply {
                max = 70                       // 30..100: abaixo de 30% a bolha some
                progress = store.opacity - 30
                contentDescription = "Opacidade da bolha"
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {
                        store.opacity = 30 + p
                        value.text = "${store.opacity}%"
                        MacroService.instance?.applyOptions()
                    }
                    override fun onStartTrackingTouch(s: SeekBar) {}
                    override fun onStopTrackingTouch(s: SeekBar) {}
                })
            })
        }
    }
}

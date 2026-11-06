package app.macro17

import android.graphics.Color
import android.graphics.PixelFormat
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView

/**
 * Edição do item por cima do app (toque na linha da lista flutuante), como no base: janela de 280 dp, cartões cinza com borda
 * (overlay_macro_editor, _gesture_editor, _image_change_detection_editor, _text_recognition_editor) e os botões Eliminar, Cancelar, Guardar.
 * Os tipos 10 a 16 e 18 do base (Ir para, contadores, arquivo, alerta, tecla, grupo, redefinir) não abrem aqui: só no editor completo.
 */
class ItemOverlay(private val svc: MacroService, private val wm: WindowManager) {
    companion object {
        fun editable(t: Type) = t in setOf(Type.TAP, Type.SWIPE, Type.PINCH_IN, Type.PINCH_OUT, Type.GESTURE, Type.IMAGE, Type.TEXT, Type.NUMBER, Type.CHANGE)
        private val AREAS = arrayOf("Posição Fixa", "Tela Cheia", "Tela Cheia (Escala de Cinza)", "Área Personalizada", "Área Personalizada (Escala de Cinza)")
    }

    private var win: View? = null
    private val pink = 0xFFD81B60.toInt()
    private fun dp(v: Int) = svc.dp(v)
    val isOpen get() = win != null
    fun dismiss() { win?.let { runCatching { wm.removeView(it) } }; win = null }

    fun show(st: Step, total: Int, onSave: (Step) -> Unit, onDelete: () -> Unit) {
        dismiss()
        var s = st
        val body = ScrollView(svc).apply { isFillViewport = true }
        val title = when (s.type) { Type.SWIPE -> "Deslizar"; Type.GESTURE -> "Gesto"; Type.IMAGE -> "Pesquisa de Imagem"; Type.TEXT -> "Reconhecimento de Texto"; Type.NUMBER -> "Ler número"
            Type.CHANGE -> "Detecção de Mudança de Imagem"; Type.PINCH_IN, Type.PINCH_OUT -> "Pinça"; else -> if (s.ms >= 500) "Pressionar" else "Toque" }

        fun card(pad: Int, build: LinearLayout.() -> Unit) = LinearLayout(svc).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundResource(R.drawable.bp_bg_outline_fill_gray); setPadding(dp(pad), dp(pad), dp(pad), dp(pad))
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).also { it.topMargin = dp(8) }; build()
        }
        fun field(init: String, decimal: Boolean, change: (String) -> Unit) = EditText(svc).apply {
            textSize = 12f; setTextColor(Color.BLACK); gravity = Gravity.END or Gravity.CENTER_VERTICAL; setBackgroundResource(R.drawable.bp_bg_outline_fill_gray)
            setPadding(dp(16), dp(6), dp(16), dp(6)); minWidth = dp(90); setSingleLine(); setText(init)
            inputType = InputType.TYPE_CLASS_NUMBER or (if (decimal) InputType.TYPE_NUMBER_FLAG_DECIMAL else 0)
            addTextChangedListener(object : android.text.TextWatcher {
                override fun afterTextChanged(e: android.text.Editable?) { change(e?.toString().orEmpty()) }
                override fun beforeTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) {}
                override fun onTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) {}
            })
        }
        fun accent(text: String, click: () -> Unit) = Button(svc, null, android.R.attr.borderlessButtonStyle).apply {
            this.text = "Mudar"; textSize = 12f; isAllCaps = false; setTextColor(pink); setBackgroundResource(R.drawable.bp_bg_outline_fill_gray) // rótulo único do base (name_change), h 30dp
            setPadding(dp(12), dp(4), dp(12), dp(4)); minHeight = dp(30); minimumHeight = dp(30); minWidth = 0; minimumWidth = 0; setOnClickListener { click() }
        }
        fun sw(on: Boolean, change: (Boolean) -> Unit) = Switch(svc).apply {
            thumbTintList = android.content.res.ColorStateList.valueOf(pink); trackTintList = android.content.res.ColorStateList.valueOf(0xFFF8BBD0.toInt())
            isChecked = on; setOnCheckedChangeListener { _, v -> change(v) }
        }
        fun row(left: String, right: View, color: Int = 0xFF616161.toInt()) = LinearLayout(svc).apply {
            gravity = Gravity.CENTER_VERTICAL; layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).also { it.topMargin = dp(8) }
            addView(svc.label(left, 14f, color).apply { maxWidth = dp(150) }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)); addView(right)
        }
        fun head(text: String, right: View?) = LinearLayout(svc).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(svc.label(text, 14f, Color.BLACK, bold = true), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)); right?.let { addView(it) }
        }
        fun divider() = View(svc).apply { setBackgroundColor(0xFFE0E0E0.toInt()); layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(1)).also { it.topMargin = dp(12) } }
        fun secs(ms: Int) = (ms / 1000.0).toString()
        fun ms(txt: String) = txt.replace(',', '.').toDoubleOrNull()?.let { Math.round(it * 1000).toInt() }
        lateinit var render: () -> Unit

        fun areaPick(x1: Int, y1: Int, x2: Int, y2: Int, got: (IntArray) -> Unit) = svc.requestPick(PositionPickerActivity.AREA, intArrayOf(x1, y1, x2, y2), null, false, got)

        fun delayCard() = card(16) {
            addView(head("Tempo de Atraso", sw(s.delay > 0) { on -> s = s.copy(delay = if (on) 1000 else 0, rnd = 0, rndMin = 0); render() }))
            if (s.delay > 0) addView(row("Tempo de Atraso", field(secs(s.delay), true) { t -> ms(t)?.let { s = s.copy(delay = it.coerceIn(0, MAX_DELAY_MS)) } }))
        }
        fun rateBar(value: Int, change: (Int) -> Unit, label: String) = LinearLayout(svc).apply {
            orientation = LinearLayout.VERTICAL
            val pct = svc.label("$value%", 14f, Color.BLACK)
            addView(LinearLayout(svc).apply { addView(svc.label(label, 14f, Color.BLACK), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)); addView(pct) })
            addView(SeekBar(svc).apply {
                max = 100; progress = value; progressTintList = android.content.res.ColorStateList.valueOf(pink); thumbTintList = android.content.res.ColorStateList.valueOf(pink)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(b: SeekBar, v: Int, u: Boolean) { pct.text = "$v%"; if (u) change(v) }
                    override fun onStartTrackingTouch(b: SeekBar) {}
                    override fun onStopTrackingTouch(b: SeekBar) {}
                })
            }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).also { it.topMargin = dp(8) })
        }

        render = {
            val col = LinearLayout(svc).apply { orientation = LinearLayout.VERTICAL }
            when (s.type) {
                Type.TAP, Type.SWIPE, Type.PINCH_IN, Type.PINCH_OUT -> {
                    val swipe = s.type != Type.TAP
                    col.addView(card(12) {
                        addView(head("Posição do Toque", null))
                        addView(row(if (swipe) "${s.x1},${s.y1} → ${s.x2},${s.y2}" else "${s.x1},${s.y1}", accent("Alterar Posição") {
                            svc.requestPick(PositionPickerActivity.POS, intArrayOf(s.x1, s.y1, s.x2, s.y2), null, swipe) { v ->
                                s = if (swipe) s.copy(x1 = v[0], y1 = v[1], x2 = v[2], y2 = v[3]) else s.copy(x1 = v[0], y1 = v[1], x2 = v[0], y2 = v[1]); render()
                            }
                        }))
                    })
                    col.addView(delayCard())
                    col.addView(card(16) {
                        addView(head("Correspondência de Imagem", sw(s.useImg && s.img.isNotBlank()) { on -> s = s.copy(useImg = on && s.img.isNotBlank()); render() }))
                        if (s.useImg) {
                            addView(row("Tempo de Pesquisa", field(secs(s.sms), true) { t -> ms(t)?.let { s = s.copy(sms = it.coerceIn(0, MAX_DELAY_MS)) } }))
                            addView(divider())
                            addView(rateBar(s.thr, { v -> s = s.copy(thr = v.coerceIn(20, 100)) }, "Taxa de correspondência de imagem (%)"))
                            addView(divider())
                            val whole = listOf(s.ax1, s.ay1, s.ax2, s.ay2).all { it == 0 }
                            val idx = if (s.fx) 0 else if (whole) (if (s.mono) 2 else 1) else (if (s.mono) 4 else 3)
                            addView(head("Área de Pesquisa", null).apply { (layoutParams as? LinearLayout.LayoutParams)?.topMargin = dp(12) })
                            addView(row(AREAS[idx], accent("Área de Pesquisa") {
                                svc.chooseOverlay("Área de Pesquisa", AREAS, idx) { w ->
                                    s = when (w) {
                                        0 -> s.copy(fx = true, ax1 = 0, ay1 = 0, ax2 = 0, ay2 = 0)
                                        1, 2 -> s.copy(fx = false, ax1 = 0, ay1 = 0, ax2 = 0, ay2 = 0, mono = w == 2)
                                        else -> s.copy(fx = false, mono = w == 4, ax1 = if (whole) 0 else s.ax1, ay1 = if (whole) 0 else s.ay1, ax2 = if (whole) 500 else s.ax2, ay2 = if (whole) 500 else s.ay2)
                                    }
                                    render()
                                }
                            }))
                            if (idx >= 3) addView(row("${s.ax1},${s.ay1},${s.ax2},${s.ay2}", accent("Área Personalizada") { areaPick(s.ax1, s.ay1, s.ax2, s.ay2) { v -> s = s.copy(ax1 = v[0], ay1 = v[1], ax2 = v[2], ay2 = v[3]); render() } }))
                        }
                    })
                }
                Type.GESTURE -> col.addView(delayCard())
                Type.CHANGE -> col.addView(card(12) {
                    addView(rateBar(100 - s.thr, { v -> s = s.copy(thr = (100 - v).coerceIn(1, 100)) }, "Diferença de Imagens"))
                    addView(head("Área de Pesquisa", null).apply { (layoutParams as? LinearLayout.LayoutParams)?.topMargin = dp(16) })
                    addView(row("${s.x1},${s.y1},${s.x2},${s.y2}", accent("Área Personalizada") { areaPick(s.x1, s.y1, s.x2, s.y2) { v -> s = s.copy(x1 = v[0], y1 = v[1], x2 = v[2], y2 = v[3]); render() } }))
                })
                else -> { // Texto, Número e Imagem: atraso, reconhecimento e opção de pesquisa (área e tempo)
                    col.addView(delayCard())
                    if (s.type == Type.TEXT) col.addView(card(16) {
                        addView(head("Reconhecimento de Texto", sw(true) { render() }.apply { isEnabled = false }))
                        addView(field(s.text, false) { t -> s = s.copy(text = t.trim()) }.apply { inputType = InputType.TYPE_CLASS_TEXT; gravity = Gravity.START; layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).also { it.topMargin = dp(8) } })
                    })
                    if (s.type == Type.NUMBER) col.addView(card(16) {
                        addView(head("Reconhecimento de Números", sw(true) { render() }.apply { isEnabled = false }))
                        addView(row("Intervalo", LinearLayout(svc).apply {
                            addView(field(s.lo.toString(), false) { t -> t.toIntOrNull()?.let { s = s.copy(lo = it) } })
                            addView(field(s.hi.toString(), false) { t -> t.toIntOrNull()?.let { s = s.copy(hi = it) } })
                        }))
                    })
                    col.addView(card(16) {
                        addView(head(if (s.type == Type.IMAGE) "Pesquisa de Imagem" else "Opção de Pesquisa", null))
                        val whole = s.x2 <= s.x1
                        addView(row(if (whole) "Tela Cheia" else "Área Personalizada", accent("Área de Pesquisa") {
                            svc.chooseOverlay("Área de Pesquisa", arrayOf("Tela Cheia", "Área Personalizada"), if (whole) 0 else 1) { w ->
                                s = if (w == 0) s.copy(x1 = 0, y1 = 0, x2 = 0, y2 = 0) else if (whole) s.copy(x1 = 0, y1 = 0, x2 = 500, y2 = 500) else s; render()
                            }
                        }))
                        if (!whole) addView(row("${s.x1},${s.y1},${s.x2},${s.y2}", accent("Área Personalizada") { areaPick(s.x1, s.y1, s.x2, s.y2) { v -> s = s.copy(x1 = v[0], y1 = v[1], x2 = v[2], y2 = v[3]); render() } }))
                        addView(divider())
                        addView(row("Tempo de Pesquisa", field(secs(s.ms), true) { t -> ms(t)?.let { s = s.copy(ms = it.coerceIn(0, MAX_DELAY_MS)) } }, Color.BLACK))
                        if (s.type == Type.IMAGE) { addView(divider()); addView(rateBar(s.thr, { v -> s = s.copy(thr = v.coerceIn(20, 100)) }, "Taxa de correspondência de imagem (%)")) }
                    })
                }
            }
            body.removeAllViews(); body.addView(col)
        }
        render()

        fun btn(text: String, click: () -> Unit) = Button(svc, null, android.R.attr.borderlessButtonStyle).apply {
            this.text = text; isAllCaps = false; setTextColor(0xFFD32F2F.toInt()); setTypeface(typeface, android.graphics.Typeface.BOLD)
            setBackgroundResource(R.drawable.bp_bg_button_outline_pink); minHeight = 0; minimumHeight = 0; setPadding(dp(4), 0, dp(4), 0); setOnClickListener { click() }
        }
        val buttons = LinearLayout(svc).apply {
            weightSum = 3f; layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).also { it.topMargin = dp(24) }
            addView(btn("Excluir") { dismiss(); onDelete() }, LinearLayout.LayoutParams(0, dp(36), 1f).also { it.marginEnd = dp(8) })
            addView(btn("Cancelar") { dismiss() }, LinearLayout.LayoutParams(0, dp(36), 1f).also { it.marginEnd = dp(8) })
            addView(btn("Salvar") {
                s.validate(total)?.let { return@btn svc.toast(it) }
                dismiss(); onSave(s)
            }, LinearLayout.LayoutParams(0, dp(36), 1f))
        }
        val card = LinearLayout(svc).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(20), dp(20), dp(20)); elevation = dp(8).toFloat()
            background = svc.rounded(Color.WHITE, 24)
            addView(LinearLayout(svc).apply {
                gravity = Gravity.CENTER_VERTICAL
                // ícone de 32dp (imageClick): miniatura do item quando há, senão o toque, como no base
                val thumb = if (s.img.isNotBlank()) svc.thumb(s.img, dp(32)) else if (s.type == Type.IMAGE && s.text.isNotBlank()) svc.thumb(s.text, dp(32)) else null
                if (thumb != null) addView(ImageView(svc).apply { setImageBitmap(thumb) }, LinearLayout.LayoutParams(dp(32), dp(32)))
                else if (s.type in setOf(Type.TAP, Type.SWIPE, Type.PINCH_IN, Type.PINCH_OUT)) addView(ImageView(svc).apply { setImageResource(R.drawable.bp_ic_tap) }, LinearLayout.LayoutParams(dp(32), dp(32)))
                addView(svc.label(st.name.ifBlank { title }, 20f, Color.BLACK, bold = true).apply { setPadding(dp(8), 0, 0, 0) }) // título = nome do item
            })
            addView(body, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).also { it.topMargin = dp(16) })
            addView(buttons)
        }
        val lp = WindowManager.LayoutParams(dp(280), WRAP_CONTENT, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.CENTER; softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        runCatching { wm.addView(card, lp) }.onSuccess { win = card }.onFailure { Diag.e("lista", "não foi possível abrir a edição do item", it) }
    }
}

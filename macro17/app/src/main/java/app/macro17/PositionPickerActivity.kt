package app.macro17

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File

/**
 * Seletores em tela cheia do base (activity_touch_position_change, activity_custom_area_selection, activity_deviation_selection):
 * fundo da última captura escurecido a 50%, borda tracejada, barra flutuante de vidro com alça, painel inferior com as coordenadas
 * e marcadores redondos arrastáveis (início = alvo, fim = seta). Devolve os valores em "v" (IntArray) no resultado.
 */
class PositionPickerActivity : Activity() {
    companion object { const val POS = 0; const val AREA = 1; const val DEV = 2 }

    private var mode = POS
    private var sw = 1; private var sh = 1
    private val v = IntArray(4)
    private lateinit var root: FrameLayout
    private lateinit var coords: TextView
    private lateinit var line: DashedLine
    private lateinit var bg: ImageView
    private var m1: View? = null; private var m2: View? = null
    private var rect: View? = null; private var handle: View? = null
    private var tpl: Bitmap? = null
    private var swipe = false

    private class DashedLine(c: android.content.Context) : View(c) {
        var a: Pair<Float, Float>? = null; var b: Pair<Float, Float>? = null
        private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 4f; pathEffect = DashPathEffect(floatArrayOf(24f, 16f), 0f) }
        override fun onDraw(c: Canvas) { val s = a ?: return; val e = b ?: return; c.drawLine(s.first, s.second, e.first, e.second, p) }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        mode = intent.getIntExtra("mode", POS); swipe = intent.getBooleanExtra("swipe", false)
        intent.getIntArrayExtra("v")?.copyInto(v, 0, 0, minOf(4, intent.getIntArrayExtra("v")!!.size))
        window.insetsController?.let { it.hide(WindowInsets.Type.systemBars()); it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE }
        val bnd = windowManager.currentWindowMetrics.bounds; sw = bnd.width(); sh = bnd.height()
        root = FrameLayout(this).apply { setBackgroundColor(0xFFF0F0F0.toInt()) }
        bg = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_XY }
        root.addView(bg, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        if (mode == DEV) intent.getStringExtra("tpl")?.let { tpl = Store(this).templateThumb(it, 240) }
        loadShot()
        root.addView(View(this).apply { setBackgroundColor(0x7F000000) }, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        root.addView(View(this).apply { setBackgroundResource(R.drawable.bp_bg_dashed_border) }, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT).also { it.setMargins(dp(16), dp(16), dp(16), dp(16)) })
        if (mode == DEV) tpl?.let { t ->
            root.addView(ImageView(this).apply { setImageBitmap(t) }, FrameLayout.LayoutParams(t.width, t.height, Gravity.CENTER))
        }
        line = DashedLine(this); root.addView(line, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        when (mode) {
            AREA -> buildArea()
            DEV -> { m1 = marker(R.drawable.bp_ic_target); m2 = marker(R.drawable.bp_ic_navigation) }
            else -> { m1 = marker(R.drawable.bp_ic_target); if (swipe) m2 = marker(R.drawable.bp_ic_navigation) }
        }
        root.addView(toolbar(), FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL).also { it.topMargin = dp(60) })
        coords = TextView(this).apply { textSize = 14f; setTextColor(Color.WHITE); setTypeface(typeface, android.graphics.Typeface.BOLD) }
        root.addView(LinearLayout(this).apply {
            setBackgroundResource(R.drawable.bp_bg_glass_panel); setPadding(dp(24), dp(16), dp(24), dp(16)); addView(coords)
        }, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).also { it.bottomMargin = dp(40) })
        setContentView(root)
        root.post { place(); refresh() }
    }

    private fun loadShot() {
        val f = File(cacheDir, "last_shot.jpg")
        bg.setImageBitmap(if (f.exists()) BitmapFactory.decodeFile(f.path) else null)
    }

    private fun toolbar(): View = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL; setBackgroundResource(R.drawable.bp_bg_glass_panel); setPadding(dp(8), dp(8), dp(8), dp(8))
        val grip = ImageView(this@PositionPickerActivity).apply { setImageResource(R.drawable.bp_ic_move_resize_variant); alpha = 0.6f }
        addView(grip, LinearLayout.LayoutParams(dp(24), dp(24)).also { it.setMargins(dp(4), 0, dp(4), 0) })
        var ox = 0f; var oy = 0f
        grip.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { ox = e.rawX - this.translationX; oy = e.rawY - this.translationY }
                MotionEvent.ACTION_MOVE -> { translationX = e.rawX - ox; translationY = e.rawY - oy }
            }
            true
        }
        fun div() = addView(View(context).apply { setBackgroundColor(0x4DFFFFFF) }, LinearLayout.LayoutParams(dp(1), dp(20)).also { it.setMargins(dp(4), 0, dp(4), 0) })
        fun btn(text: String, click: () -> Unit) = addView(Button(context, null, android.R.attr.borderlessButtonStyle).apply {
            this.text = text; isAllCaps = false; setTextColor(Color.WHITE); minWidth = 0; minimumWidth = 0; setPadding(dp(12), 0, dp(12), 0); setOnClickListener { click() }
        })
        div()
        if (mode != DEV) { btn("Editar") { editNumbers() }; div() }
        if (mode == AREA) { btn("Imagem") { chooseImage() }; div() }
        btn("Guardar") { setResult(RESULT_OK, Intent().putExtra("v", result())); finish() }
    }

    private fun marker(icon: Int): View = FrameLayout(this).apply {
        setBackgroundResource(R.drawable.bp_round_button); setPadding(0, 0, 0, 0)
        addView(ImageView(this@PositionPickerActivity).apply { setImageResource(icon); setColorFilter(0xFFFAFAFA.toInt()) }, FrameLayout.LayoutParams(dp(40), dp(40)))
        this@PositionPickerActivity.root.addView(this, FrameLayout.LayoutParams(dp(40), dp(40)))
        setOnTouchListener { view, e ->
            if (e.actionMasked == MotionEvent.ACTION_MOVE || e.actionMasked == MotionEvent.ACTION_DOWN) {
                val cx = (view.x + e.x).coerceIn(0f, root.width.toFloat()); val cy = (view.y + e.y).coerceIn(0f, root.height.toFloat())
                if (e.actionMasked == MotionEvent.ACTION_MOVE) { move(view, cx, cy) }
            }
            true
        }
        elevation = dp(1).toFloat()
    }

    private fun sx(vx: Float) = (vx * sw / root.width).toInt()
    private fun sy(vy: Float) = (vy * sh / root.height).toInt()
    private fun vxOf(x: Int) = x * root.width / sw.toFloat()
    private fun vyOf(y: Int) = y * root.height / sh.toFloat()

    private fun move(view: View, cx: Float, cy: Float) {
        when {
            mode == AREA && view === handle -> { // alça de mover: leva o retângulo junto
                val w = v[2] - v[0]; val h = v[3] - v[1]
                v[0] = (sx(cx) - 0).coerceIn(0, maxOf(0, sw - w)); v[1] = sy(cy).coerceIn(0, maxOf(0, sh - h)); v[2] = v[0] + w; v[3] = v[1] + h
            }
            mode == AREA -> { v[2] = maxOf(v[0] + 20, sx(cx)); v[3] = maxOf(v[1] + 20, sy(cy)) } // marcador de canto: redimensiona
            mode == DEV -> { // início fixo no centro da imagem; o fim define o desvio
                val c0x = sw / 2; val c0y = sh / 2
                if (view === m2) { v[0] = sx(cx) - c0x; v[1] = sy(cy) - c0y }
            }
            view === m1 -> { v[0] = sx(cx); v[1] = sy(cy) }
            else -> { v[2] = sx(cx); v[3] = sy(cy) }
        }
        place(); refresh()
    }

    /** Posiciona os marcadores e o retângulo a partir dos valores (v) em pixels da tela. */
    private fun place() {
        fun put(view: View?, x: Float, y: Float) { view?.x = x - dp(20); view?.y = y - dp(20) }
        when (mode) {
            AREA -> {
                val r = rect ?: return
                val lp = r.layoutParams as FrameLayout.LayoutParams
                lp.leftMargin = vxOf(v[0]).toInt(); lp.topMargin = vyOf(v[1]).toInt(); lp.width = (vxOf(v[2]) - vxOf(v[0])).toInt(); lp.height = (vyOf(v[3]) - vyOf(v[1])).toInt()
                r.layoutParams = lp
                put(handle, vxOf(v[0]), vyOf(v[1])); put(m1, vxOf(v[2]), vyOf(v[3]))
            }
            DEV -> {
                val cx = root.width / 2f; val cy = root.height / 2f
                put(m1, cx, cy); put(m2, cx + vxOf(v[0]) , cy + vyOf(v[1]))
                line.a = cx to cy; line.b = (cx + vxOf(v[0])) to (cy + vyOf(v[1])); line.invalidate()
            }
            else -> {
                put(m1, vxOf(v[0]), vyOf(v[1]))
                if (swipe) { put(m2, vxOf(v[2]), vyOf(v[3])); line.a = vxOf(v[0]) to vyOf(v[1]); line.b = vxOf(v[2]) to vyOf(v[3]); line.invalidate() }
            }
        }
    }

    private fun refresh() {
        coords.text = when (mode) {
            DEV -> "Desvio (X: %+d, Y: %+d)".format(v[0], v[1])
            AREA -> "Início (X: ${v[0]}, Y: ${v[1]})   |   Fim (X: ${v[2]}, Y: ${v[3]})"
            else -> if (swipe) "Início (X: ${v[0]}, Y: ${v[1]})   |   Fim (X: ${v[2]}, Y: ${v[3]})" else "Posição (X: ${v[0]}, Y: ${v[1]})"
        }
    }

    private fun buildArea() {
        if (v[2] <= v[0] || v[3] <= v[1]) { v[0] = sw / 4; v[1] = sh / 4; v[2] = sw * 3 / 4; v[3] = sh * 3 / 4 }
        rect = View(this).apply {
            background = GradientDrawable().apply { setColor(0x22D81B60); setStroke(dp(2), 0xFFD81B60.toInt()) }
        }.also { root.addView(it, FrameLayout.LayoutParams(dp(20), dp(20))) }
        // alça rosa de mover (Mudar Posição) no canto superior esquerdo; marcador redondo no canto inferior direito redimensiona
        handle = FrameLayout(this).apply {
            setBackgroundColor(0xFFD81B60.toInt())
            addView(ImageView(this@PositionPickerActivity).apply { setImageResource(R.drawable.bp_ic_move_resize_variant); setColorFilter(0xFFFAFAFA.toInt()); contentDescription = "Mudar Posição" }, FrameLayout.LayoutParams(dp(40), dp(40)))
            setOnTouchListener { view, e -> if (e.actionMasked == MotionEvent.ACTION_MOVE) move(view, view.x + e.x, view.y + e.y); true }
        }.also { root.addView(it, FrameLayout.LayoutParams(dp(40), dp(40))) }
        m1 = marker(R.drawable.bp_ic_navigation)
    }

    private fun result(): IntArray = if (mode == DEV) intArrayOf(v[0], v[1]) else v.copyOf()

    private fun editNumbers() {
        val labels = if (mode == AREA || swipe) listOf("X inicial", "Y inicial", "X final", "Y final") else listOf("X", "Y")
        val n = labels.size
        val ets = labels.mapIndexed { i, l -> android.widget.EditText(this).apply { hint = l; setText(v[i].toString()); inputType = android.text.InputType.TYPE_CLASS_NUMBER } }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0); ets.forEach { addView(it) } }
        AlertDialog.Builder(this).setTitle(if (mode == AREA) "Área Personalizada" else "Posição do Toque").setView(box)
            .setPositiveButton("OK") { _, _ -> for (i in 0 until n) ets[i].text.toString().toIntOrNull()?.let { v[i] = it.coerceIn(0, maxOf(sw, sh)) }; place(); refresh() }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun chooseImage() {
        val store = Store(this); val ids = store.samples()
        val names = (listOf("Última captura") + ids.mapIndexed { i, _ -> "Amostra ${i + 1}" }).toTypedArray()
        AlertDialog.Builder(this).setTitle("Imagem de fundo").setItems(names) { _, w ->
            if (w == 0) loadShot() else bg.setImageBitmap(store.sample(ids[w - 1]))
        }.show()
    }
}

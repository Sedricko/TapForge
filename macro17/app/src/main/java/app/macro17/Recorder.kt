package app.macro17

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.GradientDrawable
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import kotlin.math.PI
import kotlin.math.atan2

/**
 * Gravação por marcadores, como no app base: pontos arrastáveis sobre o app (que continua usável por baixo) e o botão "+"
 * do painel acrescenta o item na posição atual. Tocar = 1 ponto; Deslizar = ponto inicial e seta final com uma linha;
 * Imagem = caixa de área com uma alça no canto para redimensionar. Aqui só ficam as janelas e as posições; quem grava é o serviço.
 */
class Recorder(private val ctx: Context, private val wm: WindowManager) {
    enum class Mode(val label: String, val ic: Ic, val res: Int) { // res = vetor do base de cada tipo (ic_tap, ic_tap_hold, ic_gesture_swipe, ic_gesture, ic_image)
        TAP("Tocar", Ic.TAP, R.drawable.bp_ic_tap), PRESS("Pressionar", Ic.TAP, R.drawable.bp_ic_tap_hold), SWIPE("Deslizar", Ic.SWIPE, R.drawable.bp_ic_gesture_swipe),
        GESTURE("Gesto", Ic.GESTURE, R.drawable.bp_ic_gesture), IMAGE("Imagem", Ic.IMAGE, R.drawable.bp_ic_image);
        fun next() = values()[(ordinal + 1) % values().size]
    }

    var mode = Mode.TAP; private set
    private class Win(val view: View, val lp: WindowManager.LayoutParams)
    private val wins = ArrayList<Win>()
    private val dot = ctx.dp(40) // marcador de 40dp, como no base
    private val baseAlpha get() = if (Store(ctx).opacity < 100) 0.7f else 1f // base: 0.7 só com transparência > 0
    private val screen get() = wm.maximumWindowMetrics.bounds // relido a cada uso: a tela pode girar
    private val pts = ArrayList<Pair<Int, Int>>(); private var drawStart = 0L; private var drawEnd = 0L
    private var p1: Win? = null; private var p2: Win? = null; private var box: Win? = null; private var handle: Win? = null

    private fun params(w: Int, h: Int, touchable: Boolean) = WindowManager.LayoutParams(
        w, h, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            (if (touchable) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE),
        PixelFormat.TRANSLUCENT,
    ).apply { gravity = Gravity.TOP or Gravity.START }

    private fun add(v: View, lp: WindowManager.LayoutParams): Win { wm.addView(v, lp); return Win(v, lp).also { wins += it } }

    /** Marcador do base: 40dp, oval #D81B60 com traço de 2dp (round_button), ícone esticado sem padding. */
    private fun marker(res: Int, tint: Int, x: Int, y: Int, inset: Int = 0, onMove: () -> Unit): Win {
        val v = ImageView(ctx).apply {
            setImageResource(res); scaleType = ImageView.ScaleType.FIT_XY; setColorFilter(tint)
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFFD81B60.toInt()); setStroke(ctx.dp(2), 0xFFD81B60.toInt()) }
            setPadding(inset, inset, inset, inset)
            alpha = baseAlpha
        }
        val lp = params(dot, dot, true).apply { this.x = x - dot / 2; this.y = y - dot / 2 }
        val w = add(v, lp)
        drag(w, onMove)
        return w
    }

    /** Arrasta a janela inteira pelo próprio corpo; os limites mantêm o marcador visível na tela. */
    private fun drag(w: Win, onMove: () -> Unit) {
        var dx = 0f; var dy = 0f; var sx = 0; var sy = 0
        w.view.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { dx = e.rawX; dy = e.rawY; sx = w.lp.x; sy = w.lp.y }
                MotionEvent.ACTION_MOVE -> {
                    w.lp.x = (sx + (e.rawX - dx).toInt()).coerceIn(0, maxOf(0, screen.width() - w.lp.width.coerceAtLeast(dot)))
                    w.lp.y = (sy + (e.rawY - dy).toInt()).coerceIn(0, maxOf(0, screen.height() - w.lp.height.coerceAtLeast(dot)))
                    runCatching { wm.updateViewLayout(w.view, w.lp) }
                    onMove()
                }
            }
            true
        }
    }

    private fun center(w: Win) = (w.lp.x + w.lp.width / 2) to (w.lp.y + w.lp.height / 2)

    /** Linha entre o ponto inicial e a seta final; janela sem toque, só desenha. */
    private inner class LineView : View(ctx) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x1E000000; strokeWidth = 40f }
        override fun onDraw(c: Canvas) {
            val a = p1 ?: return; val b = p2 ?: return
            val (x1, y1) = center(a); val (x2, y2) = center(b)
            c.drawLine(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat(), paint)
        }
    }

    private inner class BoxView : View(ctx) {
        private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFD81B60.toInt(); style = Paint.Style.STROKE; strokeWidth = 8f }
        private val fill = Paint().apply { color = 0x7F7F7F7F }
        override fun onDraw(c: Canvas) {
            c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill)
            c.drawRect(edge.strokeWidth / 2, edge.strokeWidth / 2, width - edge.strokeWidth / 2, height - edge.strokeWidth / 2, edge)
        }
    }

    // Posições lembradas ao trocar de tipo (o base só alterna a visibilidade); hide() as descarta.
    private var sp1: Pair<Int, Int>? = null; private var sp2: Pair<Int, Int>? = null; private var sbox: Rect? = null

    /** A seta do fim gira para apontar na direção do traço, como no base. */
    private fun rotateArrow() {
        val a = p1 ?: return; val b = p2 ?: return
        val (x1, y1) = center(a); val (x2, y2) = center(b)
        b.view.rotation = (180 - atan2((x2 - x1).toDouble(), (y2 - y1).toDouble()) * 180 / PI).toFloat()
    }

    private fun clear() { wins.forEach { runCatching { wm.removeView(it.view) } }; wins.clear(); p1 = null; p2 = null; box = null; handle = null }

    fun show(m: Mode) {
        clear()
        mode = m
        val cx = sp1?.first ?: (screen.width() / 2); val cy = sp1?.second ?: (screen.height() / 2)
        when (m) {
            Mode.TAP, Mode.PRESS -> p1 = marker(R.drawable.bp_ic_target, 0xFFFAFAFA.toInt(), cx, cy) {}
            Mode.SWIPE -> {
                var line: LineView? = null
                p1 = marker(R.drawable.bp_ic_target, 0xFFFAFAFA.toInt(), cx, cy) { line?.invalidate(); rotateArrow() }
                val e = sp2 ?: ((cx + ctx.dp(40)) to cy) // o fim nasce 40dp à direita do início
                p2 = marker(R.drawable.bp_ic_navigation, Color.WHITE, e.first, e.second, ctx.dp(8)) { // seta de 24dp (tamanho natural) dentro do círculo de 40dp
                     line?.invalidate(); rotateArrow() }
                line = LineView().also { add(it, params(-1, -1, false)) } // a linha vem depois: fica acima dos marcadores
                rotateArrow(); line?.invalidate()
            }
            Mode.GESTURE -> {
                // Camada de desenho: o dedo traça o gesto (fundo escuro leve, traço cinza); o + do painel grava o traço.
                pts.clear(); drawStart = 0L
                val v = object : View(ctx) {
                    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF7F7F7F.toInt(); strokeWidth = 30f; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
                    override fun onDraw(c: Canvas) {
                        c.drawColor(0x4F0F0F0F)
                        if (pts.size > 1) c.drawPath(android.graphics.Path().apply { moveTo(pts[0].first.toFloat(), pts[0].second.toFloat()); for (k in 1 until pts.size) lineTo(pts[k].first.toFloat(), pts[k].second.toFloat()) }, paint)
                    }
                    override fun onTouchEvent(e: MotionEvent): Boolean {
                        when (e.actionMasked) {
                            MotionEvent.ACTION_DOWN -> { pts.clear(); drawStart = e.eventTime }
                            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {}
                            else -> return true
                        }
                        pts.add(e.rawX.toInt() to e.rawY.toInt()); drawEnd = e.eventTime
                        if (pts.size > 400) pts.removeAt(1)
                        invalidate(); return true
                    }
                }
                add(v, params(-1, -1, true))
            }
            Mode.IMAGE -> {
                val side = ctx.dp(140); val hs = ctx.dp(40) // alça de 40dp, por fora do canto inferior direito da caixa
                val r0 = sbox
                val b = add(BoxView().also { it.alpha = baseAlpha }, params(r0?.width() ?: side, r0?.height() ?: side, true).apply { x = r0?.left ?: (cx - side / 2); y = r0?.top ?: (cy - side / 2) })
                box = b
                val hv = ImageView(ctx).apply {
                    setImageResource(R.drawable.bp_ic_move_resize_variant); scaleType = ImageView.ScaleType.FIT_XY; setColorFilter(0xFFFAFAFA.toInt())
                    setBackgroundColor(0xFFD81B60.toInt()); alpha = baseAlpha
                }
                val h = add(hv, params(hs, hs, true))
                handle = h
                fun place() { h.lp.x = b.lp.x + b.lp.width; h.lp.y = b.lp.y + b.lp.height; runCatching { wm.updateViewLayout(h.view, h.lp) } }
                drag(b) { place() } // arrastar o corpo move a caixa e leva a alça junto
                // A alça redimensiona: o canto da caixa acompanha a alça, com o mínimo de 38 px do base.
                var sx = 0f; var sy = 0f; var sw = 0; var sh = 0
                h.view.setOnTouchListener { _, e ->
                    when (e.actionMasked) {
                        MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; sw = b.lp.width; sh = b.lp.height }
                        MotionEvent.ACTION_MOVE -> {
                            b.lp.width = (sw + (e.rawX - sx).toInt()).coerceIn(38, maxOf(38, screen.width() - b.lp.x - hs))
                            b.lp.height = (sh + (e.rawY - sy).toInt()).coerceIn(38, maxOf(38, screen.height() - b.lp.y - hs))
                            runCatching { wm.updateViewLayout(b.view, b.lp) }
                            place()
                        }
                    }
                    true
                }
                place()
            }
        }
    }

    fun hide() { clear(); sp1 = null; sp2 = null; sbox = null }

    /** Esconde os marcadores um instante (captura de tela) sem perder as posições. */
    fun setVisible(v: Boolean) = wins.forEach { it.view.visibility = if (v) View.VISIBLE else View.INVISIBLE }

    fun cycle() {
        p1?.let { sp1 = center(it) }; p2?.let { sp2 = center(it) }; box?.let { b -> sbox = Rect(b.lp.x, b.lp.y, b.lp.x + b.lp.width, b.lp.y + b.lp.height) }
        show(mode.next())
    }

    /** Item de toque ou deslize na posição atual dos marcadores; null no modo Imagem (ver [region]). */
    fun step(delay: Int): Step? {
        if (mode == Mode.GESTURE) return if (pts.size >= 2) Step(Type.GESTURE, ms = (drawEnd - drawStart).toInt().coerceIn(100, 30_000), text = pts.joinToString(";") { "${it.first.coerceIn(0, MAX_COORD)},${it.second.coerceIn(0, MAX_COORD)}" }, delay = delay) else null
        val a = p1 ?: return null
        val (x1, y1) = center(a)
        return when (mode) {
            Mode.TAP -> Step(Type.TAP, x1, y1, ms = 5, delay = delay)
            Mode.PRESS -> Step(Type.TAP, x1, y1, ms = 1000, delay = delay, name = "Press")    // pressionar = toque longo, 1 s como no base
            Mode.SWIPE -> p2?.let { val (x2, y2) = center(it); Step(Type.SWIPE, x1, y1, x2, y2, ms = 1000, delay = delay) }
            Mode.GESTURE -> null
            Mode.IMAGE -> null
        }
    }

    /** Centro do marcador principal, em pixels da tela. */
    fun point(): Pair<Int, Int>? = if (mode == Mode.GESTURE) pts.firstOrNull() else p1?.let { center(it) }

    /** Área da caixa em pixels da tela (modo Imagem). */
    fun region(): Rect? = box?.let { Rect(it.lp.x, it.lp.y, it.lp.x + it.lp.width, it.lp.y + it.lp.height) }
}

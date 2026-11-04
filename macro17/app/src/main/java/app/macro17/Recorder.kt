package app.macro17

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView

/**
 * Gravação por marcadores, como no app base: pontos arrastáveis sobre o app (que continua usável por baixo) e o botão "+"
 * do painel acrescenta o item na posição atual. Tocar = 1 ponto; Deslizar = ponto inicial e seta final com uma linha;
 * Imagem = caixa de área com uma alça no canto para redimensionar. Aqui só ficam as janelas e as posições; quem grava é o serviço.
 */
class Recorder(private val ctx: Context, private val wm: WindowManager) {
    enum class Mode(val label: String, val ic: Ic) {
        TAP("Tocar", Ic.TAP), PRESS("Pressionar", Ic.TAP), SWIPE("Deslizar", Ic.SWIPE), GESTURE("Gesto", Ic.GESTURE), IMAGE("Imagem", Ic.IMAGE);
        fun next() = values()[(ordinal + 1) % values().size]
    }

    var mode = Mode.TAP; private set
    private class Win(val view: View, val lp: WindowManager.LayoutParams)
    private val wins = ArrayList<Win>()
    private val dot = ctx.dp(40) // marcador de 40dp, como no base
    private val screen get() = wm.maximumWindowMetrics.bounds // relido a cada uso: a tela pode girar
    private val pts = ArrayList<Pair<Int, Int>>(); private var drawStart = 0L; private var drawEnd = 0L
    private var p1: Win? = null; private var p2: Win? = null; private var box: Win? = null; private var handle: Win? = null

    private fun params(w: Int, h: Int, touchable: Boolean) = WindowManager.LayoutParams(
        w, h, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            (if (touchable) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE),
        PixelFormat.TRANSLUCENT,
    ).apply { gravity = Gravity.TOP or Gravity.START }

    private fun add(v: View, lp: WindowManager.LayoutParams): Win { wm.addView(v, lp); return Win(v, lp).also { wins += it } }

    private fun marker(ic: Ic, x: Int, y: Int, onMove: () -> Unit): Win {
        val v = ImageView(ctx).apply {
            setImageDrawable(Icon(ic, 0xFFF5F5F5.toInt()))
            setPadding(ctx.dp(6), ctx.dp(6), ctx.dp(6), ctx.dp(6))
            background = ctx.rounded(0xFFD81B60.toInt(), 20, Color.WHITE)
            alpha = 0.7f
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
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x1E000000; strokeWidth = 40f; strokeCap = Paint.Cap.ROUND }
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

    fun show(m: Mode) {
        hide()
        mode = m
        val cx = screen.width() / 2; val cy = screen.height() / 2
        when (m) {
            Mode.TAP, Mode.PRESS -> p1 = marker(Ic.TARGET, cx, cy) {}
            Mode.SWIPE -> {
                val line = LineView().also { add(it, params(-1, -1, false)) }
                p1 = marker(Ic.TARGET, cx, cy) { line.invalidate() }
                p2 = marker(Ic.ARROW, cx + ctx.dp(40), cy) { line.invalidate() } // o fim nasce 40dp à direita do início
                line.invalidate()
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
                val side = ctx.dp(140)
                val b = add(BoxView(), params(side, side, true).apply { x = cx - side / 2; y = cy - side / 2 })
                box = b
                val h = marker(Ic.MOVE, 0, 0) {}
                handle = h
                fun place() { h.lp.x = b.lp.x + b.lp.width; h.lp.y = b.lp.y + b.lp.height; runCatching { wm.updateViewLayout(h.view, h.lp) } }
                drag(b) { place() } // arrastar o corpo move a caixa e leva a alça junto
                // A alça redimensiona: o canto da caixa acompanha a alça, com o mínimo de RegionPicker.MIN px.
                var sx = 0f; var sy = 0f; var sw = 0; var sh = 0
                h.view.setOnTouchListener { _, e ->
                    when (e.actionMasked) {
                        MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; sw = b.lp.width; sh = b.lp.height }
                        MotionEvent.ACTION_MOVE -> {
                            b.lp.width = (sw + (e.rawX - sx).toInt()).coerceIn(RegionPicker.MIN * 2, maxOf(RegionPicker.MIN * 2, screen.width() - b.lp.x - dot))
                            b.lp.height = (sh + (e.rawY - sy).toInt()).coerceIn(RegionPicker.MIN * 2, maxOf(RegionPicker.MIN * 2, screen.height() - b.lp.y - dot))
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

    fun hide() {
        wins.forEach { runCatching { wm.removeView(it.view) } }
        wins.clear(); p1 = null; p2 = null; box = null; handle = null
    }

    /** Esconde os marcadores um instante (captura de tela) sem perder as posições. */
    fun setVisible(v: Boolean) = wins.forEach { it.view.visibility = if (v) View.VISIBLE else View.INVISIBLE }

    fun cycle() = show(mode.next())

    /** Item de toque ou deslize na posição atual dos marcadores; null no modo Imagem (ver [region]). */
    fun step(delay: Int): Step? {
        if (mode == Mode.GESTURE) return if (pts.size >= 2) Step(Type.GESTURE, ms = (drawEnd - drawStart).toInt().coerceIn(100, 30_000), text = pts.joinToString(";") { "${it.first.coerceIn(0, MAX_COORD)},${it.second.coerceIn(0, MAX_COORD)}" }, delay = delay) else null
        val a = p1 ?: return null
        val (x1, y1) = center(a)
        return when (mode) {
            Mode.TAP -> Step(Type.TAP, x1, y1, ms = 5, delay = delay)
            Mode.PRESS -> Step(Type.TAP, x1, y1, ms = 1000, delay = delay)    // pressionar = toque longo, 1 s como no base
            Mode.SWIPE -> p2?.let { val (x2, y2) = center(it); Step(Type.SWIPE, x1, y1, x2, y2, ms = 1000, delay = delay) }
            Mode.GESTURE -> null
            Mode.IMAGE -> null
        }
    }

    /** Centro do marcador principal, em pixels da tela. */
    fun point(): Pair<Int, Int>? = p1?.let { center(it) }

    /** Área da caixa em pixels da tela (modo Imagem). */
    fun region(): Rect? = box?.let { Rect(it.lp.x, it.lp.y, it.lp.x + it.lp.width, it.lp.y + it.lp.height) }
}

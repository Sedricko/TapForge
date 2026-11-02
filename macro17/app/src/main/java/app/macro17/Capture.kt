package app.macro17

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View

/** Seleção por arrasto sobre um quadro congelado da tela; o quadro é desenhado esticado na view. */
class RegionPicker(ctx: Context, private val shot: Bitmap) : View(ctx) {
    private var sx = 0f; private var sy = 0f; private var ex = 0f; private var ey = 0f
    private var has = false
    private val dim = Paint().apply { color = 0x99000000.toInt() }
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = C.ACCENT; style = Paint.Style.STROKE; strokeWidth = 4f }
    private val dst = Rect()

    /** Retângulo em pixels do quadro (não da view); null se não há seleção ou ela é menor que [MIN]. */
    fun region(): Rect? {
        if (!has || width == 0 || height == 0) return null
        val kx = shot.width.toFloat() / width
        val ky = shot.height.toFloat() / height
        val r = Rect(
            (minOf(sx, ex) * kx).toInt().coerceIn(0, shot.width), (minOf(sy, ey) * ky).toInt().coerceIn(0, shot.height),
            (maxOf(sx, ex) * kx).toInt().coerceIn(0, shot.width), (maxOf(sy, ey) * ky).toInt().coerceIn(0, shot.height),
        )
        return r.takeIf { it.width() >= MIN && it.height() >= MIN }
    }

    override fun onDraw(c: Canvas) {
        dst.set(0, 0, width, height)
        c.drawBitmap(shot, null, dst, null)
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dim)
        if (has) {
            val l = minOf(sx, ex); val t = minOf(sy, ey); val r = maxOf(sx, ex); val b = maxOf(sy, ey)
            c.save(); c.clipRect(l, t, r, b); c.drawBitmap(shot, null, dst, null); c.restore() // o recorte sem o escurecido
            c.drawRect(l, t, r, b, edge)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { sx = e.x; sy = e.y; ex = e.x; ey = e.y; has = true }
            MotionEvent.ACTION_MOVE -> { ex = e.x; ey = e.y }
        }
        invalidate()
        return true
    }

    companion object { const val MIN = 16 }
}

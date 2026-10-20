package com.tapforge.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.SystemClock
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.WindowManager.LayoutParams as LP
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.tapforge.R
import com.tapforge.data.MacroStore
import com.tapforge.data.Settings
import com.tapforge.engine.Match
import com.tapforge.model.Action
import com.tapforge.model.Point
import com.tapforge.model.Stroke
import kotlinx.coroutines.Job
import kotlin.math.abs
import kotlin.math.hypot

private fun overlayParams(w: Int, h: Int, flags: Int) = LP(w, h, LP.TYPE_ACCESSIBILITY_OVERLAY, flags, PixelFormat.TRANSLUCENT)
    .apply { gravity = Gravity.TOP or Gravity.START }

/** Torna uma view arrastável; toques sem arrasto seguem como clique. */
@SuppressLint("ClickableViewAccessibility")
private fun View.dragging(wm: WindowManager, root: View, lp: LP) {
    var sx = 0f; var sy = 0f; var ox = 0; var oy = 0; var moved = false
    setOnTouchListener { v, ev ->
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> { sx = ev.rawX; sy = ev.rawY; ox = lp.x; oy = lp.y; moved = false }
            MotionEvent.ACTION_MOVE -> {
                if (abs(ev.rawX - sx) > 10 || abs(ev.rawY - sy) > 10) moved = true
                if (moved) { lp.x = ox + (ev.rawX - sx).toInt(); lp.y = oy + (ev.rawY - sy).toInt(); wm.updateViewLayout(root, lp) }
            }
            MotionEvent.ACTION_UP -> if (!moved) v.performClick()
        }
        true
    }
}

/** Painel flutuante: executar, pausar, passo a passo, trocar macro, marcar na tela e gravar. */
class Panel(private val svc: ClickerAccessibilityService) {
    private val ctx: Context = ContextThemeWrapper(svc, R.style.Theme_TapForge)
    private val wm = svc.getSystemService(WindowManager::class.java)
    private val root: View = LayoutInflater.from(ctx).inflate(R.layout.overlay_panel, null)
    private val bubble: View = LayoutInflater.from(ctx).inflate(R.layout.overlay_bubble, null)
    private val lp = overlayParams(LP.WRAP_CONTENT, LP.WRAP_CONTENT, LP.FLAG_NOT_FOCUSABLE or LP.FLAG_LAYOUT_NO_LIMITS).apply { x = 24; y = 260 }
    private val play = root.findViewById<MaterialButton>(R.id.play)
    private val pause = root.findViewById<MaterialButton>(R.id.pause)
    private val step = root.findViewById<MaterialButton>(R.id.step)
    private val rec = root.findViewById<MaterialButton>(R.id.rec)
    private val name = root.findViewById<TextView>(R.id.name)
    private val status = root.findViewById<TextView>(R.id.status)
    private val addRow = root.findViewById<View>(R.id.add_row)
    private var collapsed = false
    private var shown = false

    init {
        root.findViewById<View>(R.id.drag).dragging(wm, root, lp)
        bubble.dragging(wm, bubble, lp)
        bubble.setOnClickListener { setCollapsed(false) }
        play.setOnClickListener { svc.toggle(); update() }
        pause.setOnClickListener { if (svc.paused) svc.resume() else svc.pause() }
        step.setOnClickListener { svc.step() }
        name.setOnClickListener { nextMacro() }
        root.findViewById<View>(R.id.add).setOnClickListener { addRow.visibility = if (addRow.visibility == View.VISIBLE) View.GONE else View.VISIBLE }
        rec.setOnClickListener { svc.toggleRecording() }
        root.findViewById<View>(R.id.collapse).setOnClickListener { setCollapsed(true) }
        mapOf(R.id.chip_tap to ClickerAccessibilityService.PickMode.TAP, R.id.chip_swipe to ClickerAccessibilityService.PickMode.SWIPE,
            R.id.chip_image to ClickerAccessibilityService.PickMode.IMAGE, R.id.chip_text to ClickerAccessibilityService.PickMode.TEXT,
            R.id.chip_color to ClickerAccessibilityService.PickMode.COLOR).forEach { (id, mode) ->
            root.findViewById<Chip>(id).setOnClickListener { addRow.visibility = View.GONE; svc.pick(mode) }
        }
    }

    private fun current() = if (collapsed) bubble else root

    fun show() {
        if (shown) return
        current().alpha = Settings(svc).panelAlpha / 100f
        wm.addView(current(), lp); shown = true
        update()
    }

    fun remove() { if (shown) runCatching { wm.removeView(current()) }; shown = false }

    fun setVisible(v: Boolean) = current().let { it.post { it.visibility = if (v) View.VISIBLE else View.GONE } }

    /** Recoloca o painel acima de outras janelas do serviço (ex.: gravador). */
    fun bringToFront() { remove(); show() }

    private fun setCollapsed(c: Boolean) { remove(); collapsed = c; show() }

    private fun nextMacro() {
        if (svc.isRunning) return
        val all = MacroStore.list(svc)
        if (all.isEmpty()) { svc.toast("Nenhum macro: crie um no app"); return }
        val cur = MacroStore.selected(svc)
        val next = all[(all.indexOfFirst { it.id == cur?.id } + 1) % all.size]
        MacroStore.select(svc, next.id); update()
    }

    fun status(text: String) = root.post { status.text = text }

    fun update() = root.post {
        val running = svc.isRunning
        val m = svc.running ?: MacroStore.selected(svc)
        name.text = m?.name ?: "Nenhum macro"
        play.setIconResource(if (running) R.drawable.ic_stop else R.drawable.ic_play)
        play.contentDescription = if (running) "Parar" else "Executar"
        pause.visibility = if (running) View.VISIBLE else View.GONE
        step.visibility = if (running && svc.paused) View.VISIBLE else View.GONE
        pause.setIconResource(if (svc.paused) R.drawable.ic_play else R.drawable.ic_pause)
        pause.contentDescription = if (svc.paused) "Continuar" else "Pausar"
        rec.setIconResource(if (svc.isRecording) R.drawable.ic_stop else R.drawable.ic_record)
        rec.contentDescription = if (svc.isRecording) "Terminar gravação" else "Gravar toques"
        status.text = when {
            svc.isRecording -> "● gravando"
            running && svc.paused -> "pausado · ${svc.iteration} rep."
            running -> "rodando · ${svc.iteration} rep."
            else -> "toque no nome para trocar"
        }
    }
}

/** Tela de seleção: um ponto, uma linha (deslizar) ou um retângulo (região). */
@SuppressLint("ViewConstructor")
class PickerOverlay(
    private val svc: ClickerAccessibilityService, private val kind: Kind,
    private val done: (Point?, Point?) -> Unit,
) : View(svc) {
    enum class Kind { POINT, LINE, RECT }

    private val wm = svc.getSystemService(WindowManager::class.java)
    private val dim = Paint().apply { color = Color.argb(70, 0, 0, 0) }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 140, 50); strokeWidth = 6f; style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 44f; setShadowLayer(6f, 0f, 0f, Color.BLACK) }
    private var a: Point? = null; private var b: Point? = null
    private var finished = false

    fun show() = wm.addView(this, overlayParams(LP.MATCH_PARENT, LP.MATCH_PARENT, LP.FLAG_NOT_FOCUSABLE or LP.FLAG_LAYOUT_NO_LIMITS or LP.FLAG_LAYOUT_IN_SCREEN))

    private fun close(p: Point?, q: Point?) {
        if (finished) return
        finished = true
        runCatching { wm.removeView(this) }
        done(p, q)
    }

    private val hint get() = when (kind) {
        Kind.POINT -> "Toque no ponto desejado"
        Kind.LINE -> "Arraste do início ao fim do deslize"
        Kind.RECT -> "Arraste para marcar a região"
    }

    override fun onDraw(c: Canvas) {
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dim)
        c.drawText(hint, 40f, 160f, text)
        c.drawText("✕ Cancelar", width - 280f, 160f, text)
        val p = a ?: return; val q = b ?: p
        when (kind) {
            Kind.POINT -> c.drawCircle(p.x.toFloat(), p.y.toFloat(), 30f, stroke)
            Kind.LINE -> c.drawLine(p.x.toFloat(), p.y.toFloat(), q.x.toFloat(), q.y.toFloat(), stroke)
            Kind.RECT -> c.drawRect(minOf(p.x, q.x).toFloat(), minOf(p.y, q.y).toFloat(), maxOf(p.x, q.x).toFloat(), maxOf(p.y, q.y).toFloat(), stroke)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val pt = Point(e.rawX.toInt(), e.rawY.toInt())
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (e.x > width - 320 && e.y < 220) { close(null, null); return true }
                a = pt; b = pt
            }
            MotionEvent.ACTION_MOVE -> b = pt
            MotionEvent.ACTION_UP -> { b = pt; close(a, if (kind == Kind.POINT) a else b) }
        }
        invalidate()
        return true
    }
}

/**
 * Gravação: uma camada transparente recebe os toques, guarda cada gesto e o repete
 * no app de baixo (a camada fica intocável durante a repetição).
 */
@SuppressLint("ViewConstructor")
class RecorderOverlay(
    private val svc: ClickerAccessibilityService,
    private val replay: (List<Stroke>) -> Job,
) : View(svc) {
    private val wm = svc.getSystemService(WindowManager::class.java)
    private val lp = overlayParams(LP.MATCH_PARENT, LP.MATCH_PARENT, LP.FLAG_NOT_FOCUSABLE or LP.FLAG_LAYOUT_NO_LIMITS or LP.FLAG_LAYOUT_IN_SCREEN)
    private val border = Paint().apply { color = Color.rgb(220, 40, 40); strokeWidth = 10f; style = Paint.Style.STROKE }
    private val actions = mutableListOf<Action>()
    private class Track(val start: Long, val points: MutableList<Point> = mutableListOf(), var end: Long = 0)
    private val tracks = mutableMapOf<Int, Track>()
    private val done = mutableListOf<Track>()
    private var gestureStart = 0L
    private var lastEnd = 0L

    fun show() = wm.addView(this, lp)
    fun remove() { runCatching { wm.removeView(this) } }
    fun finish(): List<Action> = actions.toList()

    override fun onDraw(c: Canvas) = c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), border)

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val t = SystemClock.uptimeMillis()
        val i = e.actionIndex
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { gestureStart = t; done.clear(); tracks.clear(); begin(e, i, t) }
            MotionEvent.ACTION_POINTER_DOWN -> begin(e, i, t)
            MotionEvent.ACTION_MOVE -> for (k in 0 until e.pointerCount) {
                val tr = tracks[e.getPointerId(k)] ?: continue
                val p = Point(e.getRawXCompat(k), e.getRawYCompat(k))
                val last = tr.points.last()
                if (hypot((p.x - last.x).toDouble(), (p.y - last.y).toDouble()) > 6 && tr.points.size < 60) tr.points += p
            }
            MotionEvent.ACTION_POINTER_UP -> end(e.getPointerId(i), t)
            MotionEvent.ACTION_UP -> { end(e.getPointerId(i), t); commit(t) }
            MotionEvent.ACTION_CANCEL -> tracks.clear()
        }
        return true
    }

    // rawX por ponteiro só existe a partir do Android 10; antes, soma o deslocamento da view.
    private fun MotionEvent.getRawXCompat(k: Int) = if (android.os.Build.VERSION.SDK_INT >= 29) getRawX(k).toInt() else (getX(k) + rawX - x).toInt()
    private fun MotionEvent.getRawYCompat(k: Int) = if (android.os.Build.VERSION.SDK_INT >= 29) getRawY(k).toInt() else (getY(k) + rawY - y).toInt()

    private fun begin(e: MotionEvent, i: Int, t: Long) {
        tracks[e.getPointerId(i)] = Track(t).apply { points += Point(e.getRawXCompat(i), e.getRawYCompat(i)) }
    }

    private fun end(id: Int, t: Long) { tracks.remove(id)?.let { it.end = t; done += it } }

    private fun commit(t: Long) {
        if (done.isEmpty()) return
        val strokes = done.map { Stroke(it.points.toList(), it.start - gestureStart, (it.end - it.start).coerceAtLeast(1)) }
        if (lastEnd > 0) (gestureStart - lastEnd).takeIf { it > 30 }?.let { actions += Action.Wait(it) }
        lastEnd = t
        val s = strokes.singleOrNull()
        actions += when {
            s != null && s.points.size == 1 -> Action.Tap(s.points[0].x, s.points[0].y, s.durationMs)
            s != null && s.points.size == 2 -> Action.Swipe(s.points[0].x, s.points[0].y, s.points[1].x, s.points[1].y, s.durationMs)
            else -> Action.Gesture(strokes)
        }
        // Repete o gesto no app de baixo com a camada temporariamente intocável.
        lp.flags = lp.flags or LP.FLAG_NOT_TOUCHABLE; wm.updateViewLayout(this, lp)
        replay(strokes).invokeOnCompletion {
            post { lp.flags = lp.flags and LP.FLAG_NOT_TOUCHABLE.inv(); runCatching { wm.updateViewLayout(this, lp) } }
        }
        done.clear()
    }
}

/** Mostra rapidamente onde o macro tocou e onde achou imagens. */
@SuppressLint("ViewConstructor")
class TargetsOverlay(svc: ClickerAccessibilityService) : View(svc) {
    private val wm = svc.getSystemService(WindowManager::class.java)
    private val touchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(200, 255, 140, 50); style = Paint.Style.STROKE; strokeWidth = 6f }
    private val foundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(220, 60, 200, 90); style = Paint.Style.STROKE; strokeWidth = 5f }
    private class Mark(val x: Int, val y: Int, val w: Int, val h: Int, val at: Long, val found: Boolean)
    private val marks = ArrayDeque<Mark>()

    fun show() = wm.addView(this, overlayParams(LP.MATCH_PARENT, LP.MATCH_PARENT,
        LP.FLAG_NOT_FOCUSABLE or LP.FLAG_NOT_TOUCHABLE or LP.FLAG_LAYOUT_NO_LIMITS or LP.FLAG_LAYOUT_IN_SCREEN))
    fun remove() { post { runCatching { wm.removeView(this) } } }

    fun touch(x: Int, y: Int) = add(Mark(x, y, 0, 0, SystemClock.uptimeMillis(), false))
    fun found(m: Match) = add(Mark(m.x, m.y, m.w, m.h, SystemClock.uptimeMillis(), true))

    private fun add(m: Mark) = post { marks.addLast(m); while (marks.size > 20) marks.removeFirst(); invalidate() }

    override fun onDraw(c: Canvas) {
        val now = SystemClock.uptimeMillis()
        while (marks.isNotEmpty() && now - marks.first().at > 600) marks.removeFirst()
        for (m in marks) {
            if (m.found) c.drawRect((m.x - m.w / 2).toFloat(), (m.y - m.h / 2).toFloat(), (m.x + m.w / 2).toFloat(), (m.y + m.h / 2).toFloat(), foundPaint)
            else c.drawCircle(m.x.toFloat(), m.y.toFloat(), 28f, touchPaint)
        }
        if (marks.isNotEmpty()) postInvalidateDelayed(100)
    }
}

package com.tapforge.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.LinearLayout
import com.tapforge.data.MacroStore
import com.tapforge.engine.EngineEvent
import com.tapforge.engine.GestureDispatcher
import com.tapforge.engine.MacroEngine
import com.tapforge.model.GlobalKind
import com.tapforge.vision.AndroidVision
import kotlinx.coroutines.*
import kotlin.coroutines.resume

/**
 * Executa gestos e hospeda o painel flutuante. O painel usa TYPE_ACCESSIBILITY_OVERLAY,
 * então não precisa da permissão "Exibir sobre outros apps".
 */
class ClickerAccessibilityService : AccessibilityService(), GestureDispatcher {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private var panel: View? = null
    private lateinit var playBtn: Button

    override fun onServiceConnected() { showPanel() }
    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}
    override fun onInterrupt() { stopMacro() }

    override fun onDestroy() {
        stopMacro(); scope.cancel()
        panel?.let { getSystemService(WindowManager::class.java).removeView(it) }
        super.onDestroy()
    }

    // ---------- GestureDispatcher ----------
    override suspend fun tap(x: Int, y: Int, holdMs: Long): Boolean =
        stroke(Path().apply { moveTo(x.toFloat(), y.toFloat()) }, holdMs.coerceAtLeast(1))

    override suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long): Boolean =
        stroke(Path().apply { moveTo(x1.toFloat(), y1.toFloat()); lineTo(x2.toFloat(), y2.toFloat()) }, durationMs.coerceAtLeast(1))

    private suspend fun stroke(p: Path, ms: Long): Boolean = suspendCancellableCoroutine { cont ->
        val g = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p, 0, ms)).build()
        val ok = dispatchGesture(g, object : GestureResultCallback() {
            override fun onCompleted(d: GestureDescription?) { if (cont.isActive) cont.resume(true) }
            override fun onCancelled(d: GestureDescription?) { if (cont.isActive) cont.resume(false) }
        }, null)
        if (!ok && cont.isActive) cont.resume(false)
    }

    override fun global(kind: GlobalKind): Boolean = performGlobalAction(
        when (kind) {
            GlobalKind.BACK -> GLOBAL_ACTION_BACK
            GlobalKind.HOME -> GLOBAL_ACTION_HOME
            GlobalKind.RECENTS -> GLOBAL_ACTION_RECENTS
            GlobalKind.LOCK_SCREEN -> GLOBAL_ACTION_LOCK_SCREEN // Android 9+
        }
    )

    // ---------- Execução ----------
    private fun toggle() { if (job?.isActive == true) stopMacro() else startMacro() }

    private fun startMacro() {
        val macro = MacroStore.selected(this) ?: return
        val engine = MacroEngine(this, AndroidVision(applicationContext))
        job = scope.launch {
            engine.run(macro) { e -> if (e is EngineEvent.Finished || e is EngineEvent.Failed) setPlayLabel("▶") }
        }
        setPlayLabel("■")
    }

    private fun stopMacro() { job?.cancel(); job = null; if (::playBtn.isInitialized) setPlayLabel("▶") }
    private fun setPlayLabel(t: String) { playBtn.post { playBtn.text = t } }

    // ---------- Painel flutuante arrastável ----------
    private fun showPanel() {
        if (panel != null) return
        val wm = getSystemService(WindowManager::class.java)
        playBtn = Button(this).apply { text = "▶"; setOnClickListener { toggle() } }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; addView(playBtn) }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 40; y = 300 }

        var sx = 0f; var sy = 0f; var ox = 0; var oy = 0; var moved = false
        // No botão (não na linha): ele cobre o painel e consumiria o toque; arrastar não vira clique.
        playBtn.setOnTouchListener { _, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> { sx = ev.rawX; sy = ev.rawY; ox = lp.x; oy = lp.y; moved = false }
                MotionEvent.ACTION_MOVE -> {
                    if (Math.abs(ev.rawX - sx) > 8 || Math.abs(ev.rawY - sy) > 8) moved = true
                    lp.x = ox + (ev.rawX - sx).toInt(); lp.y = oy + (ev.rawY - sy).toInt()
                    wm.updateViewLayout(row, lp)
                }
            }
            moved
        }
        wm.addView(row, lp); panel = row
    }
}

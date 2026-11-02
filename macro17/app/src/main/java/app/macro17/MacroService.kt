package app.macro17

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.hypot

class MacroService : AccessibilityService() {
    companion object {
        @Volatile var instance: MacroService? = null
    }

    private val ui = Handler(Looper.getMainLooper())
    private lateinit var wm: WindowManager
    private lateinit var store: Store
    private lateinit var engine: Engine
    private var bubble: View? = null
    private var recView: View? = null
    private val rec = ArrayList<Step>()
    private lateinit var recBtn: ImageView
    private var lock: BroadcastReceiver? = null

    override fun onServiceConnected() {
        wm = getSystemService(WindowManager::class.java)
        store = Store(this)
        // Ao terminar (qualquer motivo) a tela volta a poder apagar.
        engine = Engine(this) { toast(it); ui.post { awake(false) } }
        instance = this
        showBubble()
        lock = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) { if (store.stopOnLock) engine.stop() }
        }
        val off = IntentFilter(Intent.ACTION_SCREEN_OFF)
        // A versão com flags só existe na API 33; nas 31 e 32 o registro simples já basta para broadcast do sistema.
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(lock, off, Context.RECEIVER_NOT_EXPORTED) else registerReceiver(lock, off)
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) = Unit
    override fun onInterrupt() = engine.stop()

    override fun onUnbind(intent: Intent?): Boolean {
        engine.stop()
        stopRecording(save = false)
        lock?.let { runCatching { unregisterReceiver(it) } }
        lock = null
        bubble?.let { runCatching { wm.removeView(it) } }
        bubble = null
        instance = null
        return super.onUnbind(intent)
    }

    fun stopMacro() = engine.stop()

    fun toast(msg: String) { ui.post { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() } }

    /** Chamável de qualquer thread (alarme, app). */
    fun runMacro(id: String) {
        val m = store.get(id)
        when {
            m == null -> toast("Macro não encontrada")
            recView != null -> toast("Termine a gravação primeiro")
            engine.running -> toast("Já existe uma macro em execução")
            else -> {
                // awake(true) entra na fila antes de a thread da macro poder postar o awake(false) do fim.
                ui.post { awake(true) }
                if (engine.start(m)) toast("Executando: ${m.name}") else ui.post { awake(false) }
            }
        }
    }

    /** Opções que valem na hora (a opacidade); as demais são lidas a cada execução. */
    fun applyOptions() { ui.post { bubble?.alpha = store.opacity / 100f } }

    // A janela da bolha é a única que o serviço mantém: o flag mantém a tela ligada enquanto ela existe.
    private fun awake(on: Boolean) {
        val v = bubble ?: return
        val lp = v.layoutParams as WindowManager.LayoutParams
        val keep = on && store.keepOn
        lp.flags = if (keep) lp.flags or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON else lp.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON.inv()
        wm.updateViewLayout(v, lp)
    }

    // --- bolha ---------------------------------------------------------------------------

    private fun showBubble() {
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            // TYPE_ACCESSIBILITY_OVERLAY dispensa a permissão "exibir sobre outros apps".
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 0; y = 300 }

        val handle = circleBtn(Ic.MOVE, C.EDGE, 44, "Mover") {}.apply {
            var dx = 0f; var dy = 0f; var sx = 0; var sy = 0
            setOnTouchListener { _, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { dx = e.rawX; dy = e.rawY; sx = lp.x; sy = lp.y }
                    MotionEvent.ACTION_MOVE -> {
                        lp.x = sx + (e.rawX - dx).toInt(); lp.y = sy + (e.rawY - dy).toInt()
                        bubble?.let { wm.updateViewLayout(it, lp) }
                    }
                }
                true
            }
        }
        recBtn = circleBtn(Ic.REC, C.EDGE, 44, "Gravar", C.RED) { if (recView == null) startRecording() else stopRecording(save = true) }
        val v = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
            background = glass(28)
            addView(handle)
            addView(circleBtn(Ic.PLAY, C.BLUE, 44, "Executar macro selecionada") {
                store.selected?.let { runMacro(it) } ?: toast("Selecione uma macro no app")
            })
            addView(circleBtn(Ic.STOP, C.EDGE, 44, "Parar macro") { engine.stop() })
            addView(recBtn)
        }
        bubble = v
        v.alpha = store.opacity / 100f
        wm.addView(v, lp)
    }

    // --- gravação ------------------------------------------------------------------------
    // Captura sem repassar o toque ao app de baixo (injeção de volta reentraria no overlay).

    private fun startRecording() {
        if (engine.running) return toast("Pare a macro antes de gravar")
        rec.clear()
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var downX = 0f; var downY = 0f; var downT = 0L; var lastUp = 0L; var moved = 0f
        val v = object : View(this) {
            override fun onTouchEvent(e: MotionEvent): Boolean {
                when (e.actionMasked) { // só o primeiro dedo
                    MotionEvent.ACTION_DOWN -> { downX = e.rawX; downY = e.rawY; downT = e.eventTime; moved = 0f }
                    MotionEvent.ACTION_MOVE -> moved = maxOf(moved, hypot(e.rawX - downX, e.rawY - downY))
                    MotionEvent.ACTION_UP -> {
                        // post: não remover a janela dentro do próprio dispatch de toque.
                        if (rec.size >= MAX_STEPS) { ui.post { stopRecording(save = true) }; return true }
                        val dur = (e.eventTime - downT).toInt().coerceIn(1, MAX_MS)
                        if (rec.isNotEmpty()) rec[rec.lastIndex] = rec.last().copy(delay = (downT - lastUp).toInt().coerceIn(0, MAX_MS))
                        lastUp = e.eventTime
                        rec += if (moved > slop) Step(Type.SWIPE, downX.toInt(), downY.toInt(), e.rawX.toInt(), e.rawY.toInt(), dur)
                        else Step(Type.TAP, downX.toInt(), downY.toInt(), ms = dur)
                    }
                }
                return true
            }
        }.apply { setBackgroundColor(0x33FF0000) }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT,
        ).apply { layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS }
        wm.addView(v, lp)
        recView = v
        // Reanexa a bolha: janelas do mesmo tipo empilham pela ordem de adição.
        bubble?.let { b -> val p = b.layoutParams; wm.removeView(b); wm.addView(b, p) }
        recBtn.restyle(Ic.STOP, C.RED)
        toast("Gravando: toque nos pontos. Os toques não chegam ao app.")
    }

    private fun stopRecording(save: Boolean) {
        val v = recView ?: return
        runCatching { wm.removeView(v) }
        recView = null
        recBtn.restyle(Ic.REC, C.EDGE, C.RED)
        if (!save) return
        if (rec.isEmpty()) return toast("Nada gravado")
        val m = Macro(UUID.randomUUID().toString(), "Gravação " + SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault()).format(Date()), 1, rec.toList())
        val err = m.validate()
        if (err != null) return toast("Gravação inválida: $err")
        runCatching { store.save(m) }
            .onSuccess { store.selected = m.id; toast("Salva: ${m.name} (${m.steps.size} passos)") }
            .onFailure { toast("Falha ao salvar: ${it.message}") }
    }
}

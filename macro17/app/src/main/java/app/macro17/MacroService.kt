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
import android.widget.FrameLayout
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

    // Orbe que abre em leque (opção D). Fechado, a janela é só o orbe: uma janela de overlay captura todo o toque na sua área.
    private var orbOpen = false
    private lateinit var orbBtn: View
    private var fan = emptyList<View>()

    private fun <T : View> T.placed(size: Int, x: Int, y: Int): T {
        layoutParams = FrameLayout.LayoutParams(dp(size), dp(size)).also { it.leftMargin = dp(x); it.topMargin = dp(y) }
        return this
    }

    private fun setOrb(open: Boolean) {
        val f = bubble ?: return
        if (open == orbOpen) return
        orbOpen = open
        (orbBtn.layoutParams as FrameLayout.LayoutParams).topMargin = dp(if (open) 44 else 0)
        orbBtn.requestLayout()
        fan.forEach { it.visibility = if (open) View.VISIBLE else View.GONE }
        // Aberto, o leque ocupa 44dp acima do orbe; sobe a janela na mesma medida para o orbe não pular.
        val lp = f.layoutParams as WindowManager.LayoutParams
        lp.y += dp(if (open) -44 else 44)
        wm.updateViewLayout(f, lp)
    }

    private fun showBubble() {
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            // TYPE_ACCESSIBILITY_OVERLAY dispensa a permissão "exibir sobre outros apps".
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 0; y = 360 }

        val play = circleBtn(Ic.PLAY, C.BLUE, 38, "Executar macro selecionada") {
            store.selected?.let { runMacro(it) } ?: toast("Selecione uma macro no app")
            setOrb(false)
        }.placed(38, 57, 15)
        val stop = circleBtn(Ic.STOP, C.DARK, 38, "Parar macro") { engine.stop(); setOrb(false) }.placed(38, 77, 49)
        recBtn = circleBtn(Ic.REC, C.DARK, 38, "Gravar", C.RED) { if (recView == null) startRecording() else stopRecording(save = true) }.placed(38, 57, 83)
        fan = listOf(play, stop, recBtn)
        fan.forEach { it.visibility = View.GONE }
        orbBtn = circleBtn(Ic.TAP, C.BLUE, 48, "Controle de macros") { setOrb(!orbOpen) }.placed(48, 0, 0)

        // Arrastar pelo próprio orbe; toque curto abre/fecha. Consumir só o arrasto deixa o clique (e o TalkBack) funcionando.
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var dx = 0f; var dy = 0f; var sx = 0; var sy = 0; var drag = false
        orbBtn.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { dx = e.rawX; dy = e.rawY; sx = lp.x; sy = lp.y; drag = false; false }
                MotionEvent.ACTION_MOVE -> {
                    if (!drag && hypot(e.rawX - dx, e.rawY - dy) > slop) drag = true
                    if (drag) {
                        lp.x = sx + (e.rawX - dx).toInt(); lp.y = sy + (e.rawY - dy).toInt()
                        bubble?.let { wm.updateViewLayout(it, lp) }
                    }
                    drag
                }
                MotionEvent.ACTION_UP -> { if (drag) v.isPressed = false; drag }
                else -> false
            }
        }

        val frame = FrameLayout(this).apply { fan.forEach { addView(it) }; addView(orbBtn) }
        bubble = frame
        frame.alpha = store.opacity / 100f
        wm.addView(frame, lp)
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
        recBtn.restyle(Ic.REC, C.DARK, C.RED)
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
